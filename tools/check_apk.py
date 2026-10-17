#!/usr/bin/env python3
"""Check packaged ABIs, immutable dictionary contents and 16 KiB ELF/ZIP alignment."""
import argparse,hashlib,json,os,re,struct,subprocess,zipfile
from pathlib import Path
ABIS={'arm64-v8a','x86_64'}
FILES=['word.dat','tsi.dat','alt.dat','readings.txt']
def validate_elf(data,machine=None):
 if len(data)<64 or data[:6]!=b'\x7fELF\x02\x01':raise ValueError('64-bit little-endian ELF required')
 actual_machine=struct.unpack_from('<H',data,18)[0]
 if actual_machine not in (62,183) or (machine is not None and actual_machine!=machine):raise ValueError('Wrong ELF architecture')
 offset=struct.unpack_from('<Q',data,32)[0];size,count=struct.unpack_from('<HH',data,54)
 if size!=56 or count==0:raise ValueError('Invalid program header table')
 loads=0
 for i in range(count):
  at=offset+i*size
  if at+56>len(data):raise ValueError('Truncated ELF')
  if struct.unpack_from('<I',data,at)[0]==1:
   loads+=1;align=struct.unpack_from('<Q',data,at+48)[0]
   if align<16384 or align&(align-1):raise ValueError('ELF LOAD alignment below 16 KiB')
   file_offset,vaddr,paddr,file_size,mem_size=struct.unpack_from('<QQQQQ',data,at+8)
   if file_offset%align!=vaddr%align or file_size>mem_size or file_offset+file_size>len(data):raise ValueError('Invalid ELF LOAD mapping')
 if not loads:raise ValueError('ELF has no LOAD segments')
def validate_dictionary(entries,manifest):
 props=dict(line.split('=',1) for line in manifest.splitlines() if '=' in line)
 for name in FILES:
  if name not in entries:raise ValueError('Missing dictionary '+name)
  if hashlib.sha256(entries[name]).hexdigest()!=props.get('sha256.'+name):raise ValueError('Dictionary checksum mismatch: '+name)
 if len(entries['word.dat'])<100000 or len(entries['tsi.dat'])<1000000:raise ValueError('Full dictionaries required')
def validate_entries(entries):
 for abi in ABIS:
  name=f'lib/{abi}/libzhuoran_chewing.so'
  if name not in entries:raise ValueError('Missing ABI: '+abi)
  validate_elf(entries[name],183 if abi=='arm64-v8a' else 62)
 manifest='assets/chewing/dictionary.manifest'
 if manifest not in entries:raise ValueError('Missing dictionary manifest')
 validate_dictionary({name:entries['assets/chewing/'+name] for name in FILES if 'assets/chewing/'+name in entries},entries[manifest].decode())
 if 'assets/third_party/NOTICES.md' not in entries:raise ValueError('Missing notices')
def main():
 p=argparse.ArgumentParser();p.add_argument('apk',type=Path);p.add_argument('--lock',default='third_party/chewing.lock.json',type=Path);p.add_argument('--aapt',required=True);args=p.parse_args()
 with zipfile.ZipFile(args.apk) as z:
  entries={n:z.read(n) for n in z.namelist()};validate_entries(entries)
  with args.apk.open('rb') as stream:
   for info in z.infolist():
    if info.filename.startswith('lib/') and info.filename.endswith('.so'):
     if info.compress_type!=zipfile.ZIP_STORED:raise ValueError('Native library must be uncompressed')
     stream.seek(info.header_offset+26);namesize,extrasize=struct.unpack('<HH',stream.read(4));data_offset=info.header_offset+30+namesize+extrasize
     if data_offset%16384:raise ValueError('ZIP native alignment below 16 KiB')
 root=args.lock.resolve().parents[1]
 if entries['assets/third_party/chewing.lock.json']!=args.lock.read_bytes():raise ValueError('Packaged lock mismatch')
 for record in json.loads(args.lock.read_text())['licenses']:
  packaged=entries.get('assets/'+record['path'])
  if packaged is None or hashlib.sha256(packaged).hexdigest()!=record['sha256']:raise ValueError('Missing or changed license: '+record['path'])
 if entries['assets/chewing/dictionary.manifest']!=(root/'vendor/dictionary/dictionary.manifest').read_bytes():raise ValueError('Unpinned dictionary manifest')
 badging=subprocess.check_output([args.aapt,'dump','badging',str(args.apk)],text=True)
 if not re.search(r"package: name='tw.zhuoran.ime' versionCode='13' versionName='0.13.0'",badging):raise ValueError('Wrong application/version')
 if 'uses-permission: name=\'android.permission.INTERNET\'' in badging:raise ValueError('Unexpected network permission')
 print('PASS: version 0.13.0, two ABIs, pinned full dictionary, notices, offline permission, 16 KiB ELF/ZIP alignment')
 print('SHA256 '+hashlib.sha256(args.apk.read_bytes()).hexdigest())
if __name__=='__main__':main()
