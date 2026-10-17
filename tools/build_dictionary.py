#!/usr/bin/env python3
import csv
import hashlib
from pathlib import Path
import subprocess
ROOT = Path(__file__).resolve().parents[1]

def build():
    out=ROOT/'vendor/dictionary'; out.mkdir(parents=True,exist_ok=True)
    cli=ROOT/'vendor/libchewing/target/release/chewing-cli'
    for name in ['word','tsi','alt']:
        subprocess.run([str(cli),'init','--csv',str(ROOT/'vendor/libchewing/data/dict/chewing'/f'{name}.csv'),str(out/f'{name}.dat')],check=True,capture_output=True)
    allowed=set()
    with (ROOT/'vendor/libchewing/data/dict/chewing/word.csv').open() as source:
        for row in csv.reader(line for line in source if not line.startswith('#')):
            if len(row)<3: continue
            if any(0x3400<=ord(c)<=0x9fff or 0x20000<=ord(c)<=0x323af for c in row[0]):
                allowed.add(row[2].strip())
    (out/'readings.txt').write_text('\n'.join(sorted(allowed))+'\n',encoding='utf-8')
    lines=['version=chewing-0.12.0-c44e81a']
    for name in ['word.dat','tsi.dat','alt.dat','readings.txt']:
        lines.append('sha256.'+name+'='+hashlib.sha256((out/name).read_bytes()).hexdigest())
    (out/'dictionary.manifest').write_text('\n'.join(lines)+'\n',encoding='utf-8')
    print('Dictionary generated; allowed readings:',len(allowed))
if __name__=='__main__': build()
