# Location and FX reference data

## UN/LOCODE

The bundled `backend/src/main/resources/reference/unlocode-2025-1.tsv.gz`
contains 116,067 active locations from the UNECE 2025-1 production release.
The import runs on application startup when that source version is absent.
Set `reference.unlocode.import-on-startup=false` to disable startup import.
Existing enquiries keep their historical text; new ocean and air enquiries
require canonical POL/POD codes. Existing quotations keep their shipment
snapshots. To rebuild the resource from the official ZIP:

```sh
cd backend
python3 scripts/build_unlocode_resource.py /path/to/official-production.zip
```

Source and license: https://unlocode.unece.org/publications/

`GET /api/reference/locations?q=JNPT&mode=OCEAN` returns matching codes.
The search can return several candidates; the employee must select one.

## FX

ECB observations come from the free daily EXR series at
https://data-api.ecb.europa.eu/service/data/EXR/ . The reference rate is
units of the foreign currency for one EUR. A cross rate from A to B is
`B-per-EUR / A-per-EUR`, using observations from the same date. EUR is 1.
Reference rates are information rates, not transaction rates. A rate older
than seven calendar days is rejected. The service tries to refresh after ECB
publication on weekdays in the Europe/Berlin time zone and on demand when
the cache is due.

The configured `fx.buffer-percent` defaults to zero. It applies only to
cross-currency charges. For each line, the effective rate is the reference
or authorized manual rate multiplied by `(1 + buffer/100)`. The original
unit price is converted to the quotation currency and rounded to two decimals
using HALF_UP; line amount is quantity times that converted unit price,
rounded to two decimals. The original unit price and currency, reference
and effective rates, observation date, source, and buffer are persisted.
Finalization records `fx_frozen_at` and locks the quotation from edits.

Only an ADMIN can insert an audited fallback using
`POST /api/fx/manual-rates` with `fromCurrency`, `toCurrency`, `rate`,
`effectiveDate`, and `reason`. Rates are tenant scoped. The backend returns
an error if neither a current ECB rate nor an authorized manual rate exists.

Java must trust the ECB HTTPS certificate chain. Configure the deployment
JVM trust store if its CA bundle is incomplete; TLS validation is never
disabled by the application.
For older JVMs missing the current Sectigo Public Server Authentication Root E46,
the ECB client retries only the fixed ECB endpoint with that public root bundled
at `backend/src/main/resources/fx/sectigo-public-server-authentication-root-e46.pem`.
The root was exported from the macOS system root store and has SHA-256 fingerprint
`C9:0F:26:F0:FB:1B:40:18:B2:22:27:51:9B:5C:A2:B5:3E:2C:A5:B3:BE:5C:F1:8E:FE:1B:EF:47:38:0C:53:83`.
Normal certificate chain and hostname validation remain enabled. Keep the JVM CA
store current; this fallback may need review when ECB rotates its certificate chain.
