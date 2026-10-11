"""Build the bundled search resource from UNECE's official 2025-1 production ZIP.

Usage: python3 scripts/build_unlocode_resource.py /path/to/official.zip
Source: https://unlocode.unece.org/publications/ (CC BY 4.0).
"""

import csv
import gzip
import io
import sys
import zipfile
from pathlib import Path

CURATED_ALIASES = {
    "INNSA": "JNPT | JNPA | Jawaharlal Nehru Port | Nava Sheva",
    "THLCH": "Laem Chabang Port",
    "INBOM": "Bombay | Mumbai Port",
}


def clean(value):
    return " ".join(value.replace("\t", " ").replace("\n", " ").split())


zip_path = Path(sys.argv[1])
destination = Path(__file__).resolve().parents[1] / "src/main/resources/reference/unlocode-2025-1.tsv.gz"
destination.parent.mkdir(parents=True, exist_ok=True)
records = {}
with zipfile.ZipFile(zip_path) as archive:
    parts = sorted(name for name in archive.namelist() if "CodeListPart" in name and name.endswith(".csv"))
    if len(parts) != 3:
        raise ValueError("Expected all three official CodeListPart CSV files")
    for part in parts:
        with archive.open(part) as source:
            for row in csv.reader(io.TextIOWrapper(source, encoding="utf-8-sig", errors="replace")):
                if len(row) < 7 or row[0] == "X" or len(row[1]) != 2 or len(row[2]) != 3 or not row[3].strip():
                    continue
                country, local_code = row[1].upper(), row[2].upper()
                if not country.isalpha() or not local_code.isalnum():
                    continue
                code = country + local_code
                name = clean(row[3])
                alternate = clean(row[4])
                functions = clean(row[6])
                aliases = alternate if alternate.casefold() != name.casefold() else ""
                if code in CURATED_ALIASES:
                    aliases = " | ".join(filter(None, [aliases, CURATED_ALIASES[code]]))
                records[code] = (code, country, name, aliases, functions)

with gzip.open(destination, "wt", encoding="utf-8", newline="") as output:
    for record in sorted(records.values()):
        output.write("\t".join(record) + "\n")
print(f"Wrote {len(records)} locations to {destination}")
