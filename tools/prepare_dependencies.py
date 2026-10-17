#!/usr/bin/env python3
"""Materialize immutable reviewed sources, then verify their bytes."""
import hashlib
import json
from pathlib import Path
import subprocess
import urllib.request
import os
from verify_dependencies import validate

ROOT = Path(__file__).resolve().parents[1]

def git(*args, cwd=ROOT):
    return subprocess.check_output(['git', *args], cwd=cwd, text=True).strip()

def checkout(path, url, commit):
    if not (path / '.git').exists():
        path.mkdir(parents=True, exist_ok=True)
        git('init',str(path))
        git('remote','add','origin',url,cwd=path)
    has_head = subprocess.run(['git','rev-parse','--verify','HEAD'],cwd=path,capture_output=True).returncode == 0
    if not has_head or git('rev-parse','HEAD',cwd=path) != commit:
        git('fetch','--depth','1','origin',commit,cwd=path)
        git('checkout','--detach',commit,cwd=path)
    if git('rev-parse','HEAD',cwd=path) != commit:
        raise ValueError('Wrong source commit')
    if git('status','--porcelain','--untracked-files=no',cwd=path):
        raise ValueError('Modified dependency source')

def main():
    lock=json.loads((ROOT/'third_party/chewing.lock.json').read_text())
    source=ROOT/lock['sourceArchive']; source.parent.mkdir(parents=True,exist_ok=True)
    if not source.exists():
        temp=source.with_suffix('.download')
        with urllib.request.urlopen(lock['sourceUrl'],timeout=120) as r, temp.open('wb') as out:
            import shutil
            shutil.copyfileobj(r,out)
        if hashlib.sha256(temp.read_bytes()).hexdigest()!=lock['sourceSha256']:
            temp.unlink(); raise ValueError('Source checksum mismatch')
        os.replace(temp,source)
    engine=ROOT/'vendor/libchewing'
    checkout(engine,'https://github.com/chewing/libchewing.git',lock['engineCommit'])
    data=engine/'data'
    # A populated git submodule can be a .git file, and must not be replaced.
    if subprocess.run(['git','rev-parse','--show-toplevel'],cwd=data,capture_output=True,text=True).stdout.strip()!=str(data):
        if (data/'.git').exists(): raise ValueError('Unexpected dependency layout')
        checkout(data,'https://github.com/chewing/libchewing-data.git',lock['dataCommit'])
    else:
        if git('rev-parse','HEAD',cwd=data)!=lock['dataCommit'] or git('status','--porcelain',cwd=data):
            raise ValueError('Wrong or modified data source')
    archive=ROOT/lock['dataArchive']
    subprocess.run(['git','archive','--format=tar','--output='+str(archive),'HEAD'],cwd=data,check=True)
    errors=validate(lock,ROOT,True)
    if errors: raise ValueError('; '.join(errors))
    print('PASS: immutable sources materialized and verified')

if __name__=='__main__': main()
