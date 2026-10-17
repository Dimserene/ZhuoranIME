import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'tools/verify_dependencies.py'

class DependencyTests(unittest.TestCase):
    def fixture(self, root):
        for name in ['source.tar', 'data.tar', 'LICENSE.txt', 'evidence.md']:
            (root / name).write_text(name, encoding='utf-8')
        digest = lambda name: hashlib.sha256((root / name).read_bytes()).hexdigest()
        return dict(engineCommit='05ae6bcb9309c466a1b32d69c146bc583be04747',
                    dataCommit='c44e81aef24b06f1509f19e1be54c99812d0c43f',
                    sourceArchive='source.tar', sourceSha256=digest('source.tar'),
                    dataArchive='data.tar', dataSha256=digest('data.tar'),
                    licenses=[dict(path='LICENSE.txt', sha256=digest('LICENSE.txt'), spdx='LGPL-2.1-or-later')],
                    redistributionApproved=True,
                    reviewEvidence=[dict(path='evidence.md', sha256=digest('evidence.md'))])

    def run_check(self, root, lock):
        file = root / 'lock.json'
        file.write_text(json.dumps(lock), encoding='utf-8')
        return subprocess.run([sys.executable, str(SCRIPT), '--lock', str(file), '--root', str(root), '--release'],
                              text=True, capture_output=True)

    def test_releaseRejectsUnreviewedDictionary(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d); lock = self.fixture(root); lock['redistributionApproved'] = False
            r = self.run_check(root, lock)
            self.assertEqual(1, r.returncode)
            self.assertIn('redistributionApproved', r.stderr)

    def test_checksumMismatchIsRejected(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d); lock = self.fixture(root); (root / 'data.tar').write_text('corrupt')
            r = self.run_check(root, lock)
            self.assertEqual(1, r.returncode)
            self.assertIn('dataSha256', r.stderr)

    def test_completeEvidenceAndChecksumsPass(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d); r = self.run_check(root, self.fixture(root))
            self.assertEqual(0, r.returncode, r.stderr)

    def test_missingLicenseOrReviewEvidenceRejected(self):
        for field in ['licenses', 'reviewEvidence']:
            with self.subTest(field=field), tempfile.TemporaryDirectory() as d:
                root = Path(d); lock = self.fixture(root); lock[field] = []
                r = self.run_check(root, lock)
                self.assertEqual(1, r.returncode)
                self.assertIn(field, r.stderr)

    def test_evidenceCannotEscapeRepository(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d); lock = self.fixture(root); lock['licenses'][0]['path'] = '../outside'
            r = self.run_check(root, lock)
            self.assertEqual(1, r.returncode)
            self.assertIn('path', r.stderr)

if __name__ == '__main__':
    unittest.main()
