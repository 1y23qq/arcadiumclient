# Private macOS builds on a public runner repository

The runner repository contains this worker, its workflow and AES-256-GCM input
parts. Application source, build logs and outputs are encrypted. The build key
is the existing `ARCADIUM_BUILD_KEY` Actions secret. Keep the key out of command
arguments, Git, normal build environment variables and tool output.

The source and result formats use the existing v1 additional authenticated data,
so the saved local key can be reused. `scripts/encrypted_build.py` reads its
256-bit hexadecimal key from standard input. It never prints the key.

Before preparing a source archive, commit the complete version change, client
JARs and distribution hashes in the private development repository. The worker
checks and packages these JARs directly without rebuilding or changing them.

Create an uncompressed archive with `git archive --format=tar HEAD`. Pipe the
locally protected key to:

```
python scripts/encrypted_build.py source --archive SOURCE.tar --output PUBLIC_CHECKOUT/payload
```

Copy `worker.py` to the public checkout root and `workflow.yml` to
`.github/workflows/macos.yml`. Its tracked files may contain only:

- `worker.py`, `.github/workflows/macos.yml`, `.gitignore` and a short README.
- `payload/manifest.json` and its listed `payload/part-NNN.enc` parts.

Remove obsolete encrypted parts from Git when the input becomes smaller. Do not
commit TAR archives, application source, output binaries or local key files to
the runner repository. Dispatch `macos.yml` with `mode=package` and
`platform=both`, or select `arm64` or `intel`. The account owner handles dispatch
and restores the normal GitHub account afterward. This worker publishes no
release and makes no billing or repository visibility changes.

For a fast native-helper diagnosis, dispatch `mode=helpers`. Add the already
verified `.app.tar.gz` and its platform manifest to the private input archive at
`build/ci-native-baseline/PLATFORM/`. This mode checks their SHA-256 and file list,
validates the CPU and code signature, and runs the helper diagnosis using the
existing app without compiling Rust. It compares the original two-process
account test with fixture snapshots and a single-process test. Logs show only
known fixture labels and whitelisted account error messages. Results remain
encrypted. Do not put baseline archives in the public Git checkout directly.

Keychain diagnostics also compare 250 ms and one-second delays after a write,
read-only retries and a fresh helper process. A small Swift probe reads only the
temporary fixture's Keychain reference and reports numeric status and fixture
labels. An optional diagnostic-only DYLD shim records the actual helper's
SecItemAdd, SecItemUpdate and SecItemCopyMatching status codes, scoped to that
fixture. Neither probe outputs account IDs, passwords, access tokens, refresh
tokens or raw Keychain data, and neither ships in the application bundle.

Both architectures run native Rust library and account-store tests, the macOS
update apply/rollback test, the native app build, existing launcher startup and
window/icon checks, and headless account/Discord helper smoke checks. Account
smoke data contains fake tokens only, uses a unique temporary profile, verifies
the credential-free account list and removes its own Keychain entry afterward.
The Discord smoke check uses an empty game list and exercises the READY/GO
handoff without opening a launcher window.

All private commands redirect output to a private log. The result artifact has
one-day retention and contains only an encrypted ZIP, including installers,
their manifests, checksums, private diagnostic logs and the native helper result.
Use the saved key on standard input to decrypt an artifact:

```
python scripts/encrypted_build.py result --input PLATFORM.enc --output PRIVATE_RESULT.zip
```

The ZIP has `status.json` and `worker.log`. A successful worker returns exit code
0. Each Mac package directory has a DMG, an `.app.tar.gz` update payload, its
platform manifest and checksums. Verify all final Windows and Mac assets with
`scripts/prepare_public_release.py` before a separately authorized public release.
