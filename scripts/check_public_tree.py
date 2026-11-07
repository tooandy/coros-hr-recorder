#!/usr/bin/env python3
"""Check files staged for public release, not historical/private git objects."""
from pathlib import Path
import re
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[1]
files=subprocess.check_output(["git","ls-files","--cached"],cwd=ROOT,text=True).splitlines()
if not files:
    print("No staged files: run after git add in the public clean repository.",file=sys.stderr)
    sys.exit(2)
blocked_filenames=re.compile(r"(^|/)(?:\.env(?:\..*)?|local\.properties|id_rsa|.+\.(?:apk|aab|csv|fit|jks|keystore|p12|pem))$",re.I)
patterns=[
    re.compile("/"+"Users/"+r"[^/\s]+/"),
    re.compile("/"+"Volumes/"),
    re.compile(r"(?:192\.168|10\.\d+|172\.(?:1[6-9]|2\d|3[01]))\.\d+\.\d+"),
    re.compile(r"[\w.+-]+@qq\.com",re.I),
    re.compile(r"(?:ghp_|github_pat_|sk-[A-Za-z0-9]{24})[A-Za-z0-9_]+"),
    re.compile(r"AKIA[0-9A-Z]{16}"),
]
bad=[]
for relative in files:
    p=ROOT/relative
    if blocked_filenames.search(relative):bad.append((relative,"prohibited filename"));continue
    if not p.is_file():continue
    if p.suffix.lower() in (".png",".jar"):continue
    try:text=p.read_text(encoding="utf-8")
    except UnicodeDecodeError:continue
    for rule in patterns:
        if rule.search(text):bad.append((relative,rule.pattern));break
if bad:
    print("Public source scan FAILED:",bad,file=sys.stderr)
    sys.exit(1)
print(f"Public source scan OK: {len(files)} staged files; no known private paths or credentials")
