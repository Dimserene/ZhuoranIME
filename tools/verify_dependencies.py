#!/usr/bin/env python3
"""Verify checked-in dependency evidence and downloaded archives; never grant approvals."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import sys


def validate(lock, root, release):
    errors = []
    def fail(message):
        errors.append(message)
    def check_file(path, digest, label):
        if not isinstance(path, str) or not path:
            fail(label + ' path missing'); return
        candidate = (root / path).resolve()
        if not candidate.is_relative_to(root.resolve()):
            fail(label + ' path escapes root'); return
        if not isinstance(digest, str) or not re.fullmatch('[0-9a-f]{64}', digest):
            fail(label + ' invalid sha256'); return
        try:
            actual = hashlib.sha256(candidate.read_bytes()).hexdigest()
            if actual != digest:
                fail(label + ' checksum mismatch')
        except OSError:
            fail(label + ' path unavailable')
    for name in ['engineCommit', 'dataCommit']:
        if not isinstance(lock.get(name), str) or not re.fullmatch('[0-9a-f]{40}', lock[name]):
            fail(name + ' must be a full commit')
    for prefix in ['source', 'data']:
        check_file(lock.get(prefix + 'Archive'), lock.get(prefix + 'Sha256'), prefix + 'Sha256')
    for name in ['licenses', 'reviewEvidence']:
        records = lock.get(name)
        if not isinstance(records, list) or not records:
            fail(name + ' missing'); continue
        for record in records:
            if not isinstance(record, dict):
                fail(name + ' invalid entry'); continue
            if name == 'licenses' and not record.get('spdx'):
                fail('licenses SPDX missing')
            check_file(record.get('path'), record.get('sha256'), name)
    if release and lock.get('redistributionApproved') is not True:
        fail('redistributionApproved must be explicitly true')
    return errors


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--lock', required=True, type=Path)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--release', action='store_true')
    args = parser.parse_args()
    try:
        lock = json.loads(args.lock.read_text(encoding='utf-8'))
        if not isinstance(lock, dict):
            raise ValueError('lock must be an object')
        errors = validate(lock, args.root, args.release)
    except (OSError, ValueError, TypeError) as exc:
        errors = ['Invalid lock: ' + str(exc)]
    if errors:
        print('\n'.join(errors), file=sys.stderr)
        return 1
    print('PASS: dependency archives and review evidence verified')
    return 0

if __name__ == '__main__':
    sys.exit(main())
