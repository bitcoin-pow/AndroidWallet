# Source publication privacy

Publish the Git-selected source files, not a ZIP of the entire development folder.
The working folder contains local signing material, SDK configuration, IDE state,
and generated caches/logs that must remain private. These are excluded by the
repository's `.gitignore`. The whole `.idea/` and `.kotlin/` directories are excluded.

## Repeat the review

With Python 3 and Git installed, run:

```sh
python3 tools/audit-source-privacy.py
```

The tool enumerates candidate files using repository ignore rules and, when Git
has been initialized, also checks tracked files even if newly ignored. It scans
raw contents and nested ZIP/JAR/AAR entries, checks the local username, home path,
account display name and configured Git author identity, common credential/key
markers, first-party email addresses, and PNG/WebP identifying metadata.

For additional names or identifiers, keep a private text file **outside** the
project, one identifier per line, and pass `--identifiers-file` with that file's
path. Do not store actual private keys or recovery phrases in that file.

The report and publication file list are written under `.privacy-audit/`, which
is excluded from Git. Reports show categories and redacted locations, not matched
values. A nonzero exit code means findings or scan errors need review; it does
not automatically mean a credential has leaked.

Third-party archives contain their authors' copyright names, license notices,
example paths, certificate stores, and public test fixtures. Preserve these
upstream files and notices. A short personal name may also match unrelated public
authors or examples. Review the location and provenance before changing a library.
The wallet's test recovery phrase and addresses are public test vectors; other
test seeds are deterministic synthetic byte arrays. They are not a saved wallet.

String matching cannot recognize every possible personal identifier or arbitrary
secret. This check does not audit Git history, GitHub account attribution, device
storage, or external backups. There was no `.git` history in the supplied project
at the time of the initial review. When creating commits, choose an appropriate
public author name and private/noreply email; commit metadata is separate from
the files scanned here.

## Clean export

The reviewed source ZIP generated during the publication review contains only
the selected source/dependency files. Its ZIP entries use fixed timestamps and
generic permissions; local owner IDs, filesystem extended attributes, and local
absolute paths are not copied. Extract it into a separate directory before
initializing a new public repository. Do not commit the large ZIP itself; commit
the extracted files, including `vendor/`.

Any later edit or dependency update needs a fresh review. Never force-add ignored
signing keys, caches, local configuration, or wallet files. Ignoring a file does
not remove it from existing Git history if it was committed previously.
