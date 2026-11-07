#!/usr/bin/env python3
"""Check that both English default and Simplified Chinese Android resources cover the same keys."""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
root=Path(__file__).resolve().parents[1]
def load(locale):
    p=root/"app/src/main/res"/locale/"strings.xml"
    strings={}
    for s in ET.parse(p).getroot():
        if s.tag=="string":
            name=s.attrib.get("name")
            if not name or name in strings:
                raise ValueError(f"Missing or duplicate name in {p}: {name}")
            strings[name]="".join(s.itertext())
    return strings
english, chinese=load("values"),load("values-zh")
assert english.keys()==chinese.keys(), f"Missing locale keys: en-only={english.keys()-chinese.keys()} zh-only={chinese.keys()-english.keys()}"
assert all(english.values()) and all(chinese.values()),"Empty translation"
assert not any(re.search(r"[\u3400-\u9fff]",v) for v in english.values()),"Untranslated Chinese in English locale"
source_files=(root/"app/src/main/java").rglob("*.java")
referenced=set()
for p in source_files:
    referenced.update(re.findall(r"R\.string\.(ui_[a-z0-9_]+)",p.read_text()))
assert referenced<=english.keys(),f"Missing string references: {referenced-english.keys()}"
print(f"Locale OK: {len(english)} en/zh resources, {len(referenced)} keys referenced")
