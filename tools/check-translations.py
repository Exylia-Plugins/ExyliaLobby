#!/usr/bin/env python3
"""Checks lang/<code>/ files against the English source: same keys, same tokens in every string.

English messages are not shipped (the records hold them), so they come from a dump directory:
  check-translations.py <resources-dir> <english-messages-dump-dir> [code...]
"""
import re, sys
from pathlib import Path
import yaml

TOKEN = re.compile(r"%[A-Za-z0-9_.]+%|\{[a-z_]+(?::[^}]*)?\}|<[^<>\s][^<>]*>|&[0-9a-fk-or]|\[sound:[^\]]*\]|\[[A-Z_]+\||<nl>")
TEXT_KEYS = {"name", "lore", "title"}

def tokens(s): return sorted(TOKEN.findall(s))

def walk(a, b, path, text, errs):
    if isinstance(a, dict):
        if not isinstance(b, dict) or set(a) != set(b):
            errs.append(f"{path}: keys differ {sorted(set(a) ^ set(b)) if isinstance(b, dict) else 'type'}"); return
        for k in a: walk(a[k], b[k], f"{path}.{k}", text or k in TEXT_KEYS, errs)
    elif isinstance(a, list):
        if not isinstance(b, list) or len(a) != len(b):
            errs.append(f"{path}: list length differs"); return
        for i, (x, y) in enumerate(zip(a, b)): walk(x, y, f"{path}[{i}]", text, errs)
    elif isinstance(a, str):
        if not isinstance(b, str): errs.append(f"{path}: not a string"); return
        if text:
            if tokens(a) != tokens(b): errs.append(f"{path}: tokens {tokens(a)} != {tokens(b)}")
        elif a != b: errs.append(f"{path}: {a!r} != {b!r} (not a text key)")
    elif a != b:
        errs.append(f"{path}: {a!r} != {b!r}")

def main():
    res, dump = Path(sys.argv[1]), Path(sys.argv[2])
    codes = sys.argv[3:] or ["es", "fr", "pt", "ru", "tr"]
    bad = 0
    sources = {p.relative_to(res / "lang/en"): p for p in (res / "lang/en").rglob("*.yml")}
    sources.update({p.relative_to(dump): p for p in dump.rglob("*.yml")})
    for code in codes:
        for rel, src in sorted(sources.items()):
            tgt = res / "lang" / code / rel
            if not tgt.exists(): print(f"{code}/{rel}: MISSING"); bad += 1; continue
            errs = []
            walk(yaml.safe_load(src.read_text()) or {}, yaml.safe_load(tgt.read_text()) or {}, "", src.is_relative_to(dump), errs)
            if code != "ru" and re.search(r"[Ѐ-ӿ]", tgt.read_text()): errs.append("cyrillic outside ru")
            for e in errs: print(f"{code}/{rel}: {e}")
            bad += len(errs)
    print("OK" if not bad else f"{bad} problems"); sys.exit(1 if bad else 0)
main()
