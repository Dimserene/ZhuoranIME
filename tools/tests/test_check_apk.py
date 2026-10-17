import importlib.util
from pathlib import Path
import unittest
import sys
sys.path.insert(0,str(Path(__file__).parents[1]))
spec=importlib.util.spec_from_file_location('check_apk',Path(__file__).parents[1]/'check_apk.py')
class ApkIntegrityTest(unittest.TestCase):
 def test_missing_abi_is_rejected(self):
  from check_apk import validate_entries
  with self.assertRaises(ValueError):validate_entries({})
 def test_bad_elf_alignment_is_rejected(self):
  from check_apk import validate_elf
  import struct
  elf=bytearray(120);elf[:6]=b'\x7fELF\x02\x01';struct.pack_into('<H',elf,18,62);struct.pack_into('<Q',elf,32,64);struct.pack_into('<HH',elf,54,56,1);struct.pack_into('<I',elf,64,1);struct.pack_into('<Q',elf,112,4096)
  with self.assertRaises(ValueError):validate_elf(elf)
 def test_corrupt_dictionary_is_rejected(self):
  from check_apk import validate_dictionary
  with self.assertRaises(ValueError):validate_dictionary({'word.dat':b'bad'},'version=test\nsha256.word.dat='+'0'*64)
 def test_noncongruent_load_is_rejected(self):
  from check_apk import validate_elf
  import struct
  elf=bytearray(120);elf[:6]=b'\x7fELF\x02\x01';struct.pack_into('<H',elf,18,62);struct.pack_into('<Q',elf,32,64);struct.pack_into('<HH',elf,54,56,1);struct.pack_into('<I',elf,64,1);struct.pack_into('<Q',elf,72,1);struct.pack_into('<Q',elf,112,16384)
  with self.assertRaises(ValueError):validate_elf(elf)
 def test_wrong_machine_is_rejected(self):
  from check_apk import validate_elf
  import struct
  elf=bytearray(120);elf[:6]=b'\x7fELF\x02\x01';struct.pack_into('<H',elf,18,62);struct.pack_into('<Q',elf,32,64);struct.pack_into('<HH',elf,54,56,1);struct.pack_into('<I',elf,64,1);struct.pack_into('<Q',elf,112,16384)
  with self.assertRaises(ValueError):validate_elf(elf,183)
 def test_missing_dictionary_is_rejected(self):
  from check_apk import validate_dictionary
  with self.assertRaisesRegex(ValueError,'Missing dictionary'):
   validate_dictionary({},'version=test')
