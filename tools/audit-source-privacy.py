#!/usr/bin/env python3
"""Read-only review of Git publication candidates, including nested ZIP/JAR/AARs.

Requires Python 3 and Git. Reports categories/locations, never matched values.
This heuristic review is not a proof that arbitrary secrets cannot be present.
"""
import argparse
import getpass
import hashlib
import io
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import tempfile
import zipfile
import zlib

ROOT = Path(__file__).resolve().parents[1]
MAX_ENTRY = 128 * 1024 * 1024
MAX_TOTAL = 8 * 1024 * 1024 * 1024


def git(*args, **kwargs):
    return subprocess.run(['git', *args], cwd=ROOT, check=True,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE, **kwargs).stdout


def candidates():
    # Honor only repository ignore rules; global ignores must not hide findings.
    with tempfile.TemporaryDirectory(prefix='btcw-privacy-') as temp:
        git('init', '-q', temp)
        data = git(f'--git-dir={temp}/.git', f'--work-tree={ROOT}',
                   'ls-files', '--others', '--exclude-per-directory=.gitignore', '-z')
    files = set(data.split(b'\0')) - {b''}
    if (ROOT / '.git').exists():
        # Include already tracked files even when a new ignore rule excludes them.
        files.update(set(git('ls-files', '-z').split(b'\0')) - {b''})
    return sorted(Path(os.fsdecode(p)) for p in files)


def recover_entry(blob, entry):
    """Inspect payloads in upstream fixtures with inconsistent ZIP headers.

    This does not repair the file or certify CRC integrity; recovery is reported.
    """
    offset = blob.find(b'PK\x03\x04', max(0, entry.header_offset - 8),
                       max(64, entry.header_offset + 64))
    if offset < 0 or offset + 30 > len(blob):
        raise ValueError('Cannot locate local ZIP header')
    header = struct.unpack_from('<4s5H3I2H', blob, offset)
    start = offset + 30 + header[-2] + header[-1]
    payload = blob[start:start + entry.compress_size]
    if entry.compress_type == zipfile.ZIP_STORED:
        result = payload
    elif entry.compress_type == zipfile.ZIP_DEFLATED:
        decoder = zlib.decompressobj(-15)
        result = decoder.decompress(payload, MAX_ENTRY + 1)
        if not decoder.eof:
            raise ValueError('Truncated or oversized compressed entry')
    else:
        raise ValueError('Unsupported recovery compression')
    if len(result) > MAX_ENTRY:
        raise ValueError('Recovered entry exceeds audit limit')
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--identifiers-file', type=Path,
                        help='Private file outside this checkout, one personal identifier per line')
    args = parser.parse_args()
    identifiers = {getpass.getuser(), str(Path.home())}
    try:
        import pwd
        identifiers.add(pwd.getpwuid(os.getuid()).pw_gecos.split(',')[0])
    except (ImportError, KeyError):
        pass
    for key in ('user.name', 'user.email'):
        try:
            identifiers.add(git('config', '--get', key).decode().strip())
        except subprocess.CalledProcessError:
            pass
    if args.identifiers_file:
        identifiers.update(args.identifiers_file.read_text().splitlines())
    identifiers = {s.strip() for s in identifiers if len(s.strip()) >= 3}
    needles = {}
    for value in identifiers:
        for encoding, letter in (('utf-8', rb'[A-Za-z0-9_]'),
                                 ('utf-16le', rb'[A-Za-z0-9_]\x00'),
                                 ('utf-16be', rb'\x00[A-Za-z0-9_]')):
            needle = value.lower().encode(encoding)
            # Short display names otherwise match ordinary words in binaries.
            needles[needle] = re.compile(b'(?<!' + letter + b')' + re.escape(needle)
                                         + b'(?!' + letter + b')')
    findings, errors, seen_archives = [], [], set()
    counts = {'candidate_files': 0, 'archive_entries': 0, 'unique_archives': 0,
              'bytes_inspected': 0, 'first_party_images': 0}
    patterns = {
        'home-directory path': re.compile(rb'(?:/Users/|/home/|[A-Z]:\\Users\\)[\w.-]+', re.I),
        'private-key marker': re.compile(rb'-----BEGIN (?:RSA |EC |OPENSSH |ENCRYPTED )?PRIVATE KEY-----'),
        'credential token': re.compile(rb'(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,}|AKIA[A-Z0-9]{16}|AIza[A-Za-z0-9_-]{35}|xox[baprs]-[A-Za-z0-9-]{15,})'),
    }
    email = re.compile(rb'[A-Za-z0-9_.+%-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}')

    def redact(location):
        for identifier in sorted(identifiers, key=len, reverse=True):
            location = re.sub(re.escape(identifier), '[REDACTED]', location, flags=re.I)
        return location

    def record(category, location, vendor):
        findings.append({'category': category, 'location': redact(location),
                         'scope': 'third-party' if vendor else 'project'})

    def inspect(data, location, vendor, depth=0):
        counts['bytes_inspected'] += len(data)
        if counts['bytes_inspected'] > MAX_TOTAL:
            raise ValueError('Expanded scan exceeds total size limit')
        lower = data.lower()
        encoded_location = location.lower().encode()
        if any((n in lower and pattern.search(lower)) or
               (n in encoded_location and pattern.search(encoded_location))
               for n, pattern in needles.items()):
            record('personal identifier', location, vendor)
        # Remove NULs to recognize ASCII tokens/paths in UTF-16 strings as well.
        searchable = data.replace(b'\x00', b'') if b'\x00' in data else data
        for category, pattern in patterns.items():
            if pattern.search(searchable):
                record(category, location, vendor)
        if not vendor and email.search(searchable):
            record('email address', location, vendor)
        if re.search(r'(?i)(?:\.(?:jks|keystore|p12|pfx)|(?:^|/)\.env(?:\..*)?|wallet-seed-v1\.bin|wallet-transactions-v1\.json|local\.properties)$', location):
            record('private/config filename', location, vendor)

        if not vendor and data.startswith(b'\x89PNG\r\n\x1a\n'):
            counts['first_party_images'] += 1
            pos = 8
            while pos + 12 <= len(data):
                size = struct.unpack('>I', data[pos:pos + 4])[0]
                tag = data[pos + 4:pos + 8]
                if pos + 12 + size > len(data):
                    raise ValueError('Malformed PNG')
                if tag in (b'tEXt', b'zTXt', b'iTXt', b'eXIf', b'tIME'):
                    record('image metadata', location, vendor)
                pos += 12 + size
        if not vendor and data[:4] == b'RIFF' and data[8:12] == b'WEBP':
            counts['first_party_images'] += 1
            pos = 12
            while pos + 8 <= len(data):
                size = struct.unpack('<I', data[pos + 4:pos + 8])[0]
                if pos + 8 + size + (size & 1) > len(data):
                    raise ValueError('Malformed WebP')
                if data[pos:pos + 4] in (b'EXIF', b'XMP '):
                    record('image metadata', location, vendor)
                pos += 8 + size + (size & 1)

        if data.startswith((b'PK\x03\x04', b'PK\x05\x06')):
            digest = hashlib.sha256(data).digest()
            if digest in seen_archives:
                return
            if depth >= 8:
                raise ValueError('Archive nesting exceeds audit limit')
            seen_archives.add(digest)
            counts['unique_archives'] += 1
            with zipfile.ZipFile(io.BytesIO(data)) as archive:
                if archive.comment:
                    inspect(archive.comment, location + '!archive-comment', vendor, depth + 1)
                for entry in archive.infolist():
                    if entry.is_dir():
                        continue
                    if entry.file_size > MAX_ENTRY:
                        raise ValueError('Archive entry exceeds audit size limit')
                    counts['archive_entries'] += 1
                    entry_location = location + '!' + entry.filename
                    try:
                        try:
                            payload = archive.read(entry)
                        except (ValueError, zipfile.BadZipFile):
                            payload = recover_entry(data, entry)
                            record('malformed archive entry recovered', entry_location, vendor)
                        inspect(payload, entry_location, vendor, depth + 1)
                    except (OSError, ValueError, zipfile.BadZipFile, RuntimeError, zlib.error) as exc:
                        errors.append({'location': redact(entry_location),
                                       'error_type': type(exc).__name__})
                    if entry.comment:
                        inspect(entry.comment, location + '!' + entry.filename + '!comment', vendor, depth + 1)

    files = candidates()
    for relative in files:
        counts['candidate_files'] += 1
        try:
            path = ROOT / relative
            if path.is_symlink():
                record('symlink requires review', str(relative), False)
                continue
            if path.stat().st_size > MAX_ENTRY:
                raise ValueError('File exceeds audit size limit')
            inspect(path.read_bytes(), relative.as_posix(), relative.parts[0] == 'vendor')
        except (OSError, ValueError, zipfile.BadZipFile, RuntimeError) as exc:
            errors.append({'location': redact(str(relative)), 'error_type': type(exc).__name__})
    output = ROOT / '.privacy-audit'
    output.mkdir(exist_ok=True)
    (output / 'findings.json').write_text(json.dumps(
        {'counts': counts, 'findings': findings, 'errors': errors}, indent=2) + '\n')
    (output / 'publish-files.txt').write_text(''.join(str(p) + '\n' for p in files))
    print(json.dumps(counts, indent=2))
    for scope in ('project', 'third-party'):
        categories = {}
        for item in findings:
            if item['scope'] == scope:
                categories[item['category']] = categories.get(item['category'], 0) + 1
        print(scope + ' review findings: ' + json.dumps(categories))
    print('Scan errors:', len(errors))
    print('Redacted details: .privacy-audit/findings.json (excluded from Git)')
    return 1 if errors or findings else 0


if __name__ == '__main__':
    raise SystemExit(main())
