UN/LOCODE production release 2025-1
====================================

Source: https://unlocode.unece.org/publications/
Official ZIP: https://opensource.unicc.org/un/unece/uncefact/vocab-locode/-/jobs/artifacts/2025-1/download?job=package-release
License: UN/CEFACT CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/)

`unlocode-2025-1.tsv.gz` is derived from the three official CSV code-list parts.
The build script excludes rows marked `X` (deleted), keeps the latest row per
code, and adds a small set of explicitly curated search aliases. Codes, names,
countries and transport functions otherwise come from the official release.
Run `python3 scripts/build_unlocode_resource.py <official-zip>` to reproduce it.
