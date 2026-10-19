# macOS build worker

`worker.py` and `macos.yml` are the generic worker and workflow mirrored to the public build transport repository. Application sources and compiler caches are encrypted with the private build key before they leave the private build directory. The workflow runs only through `workflow_dispatch`.

The compiler cache is separated by architecture and authenticated against the Rust compiler, deployment target and optimization profiles. Workspace crate outputs are excluded so Arcadium is rebuilt for every input snapshot. Cache restoration never replaces existing files, and a missing or invalid cache falls back to a clean build. Unchanged dependency outputs reuse the existing encrypted archive without another compression pass. Tests still execute on each build.

Intel compilation uses up to four CPU jobs. Apple Silicon stays at two jobs to fit the runner's memory. Packaging, signatures and compatibility checks use the same build options with or without a cache.

After changing these files, copy the worker to the transport checkout's `worker.py` and the workflow to `.github/workflows/macos.yml`. Commit only those generic files and encrypted payload parts in the public transport repository. Keep the application and `scripts/private_build_cache.py` in the encrypted source snapshot.

Cache restoration has been verified on both architectures. The Intel 0.3.3 job took 45m48s, compared with 1h16m45s for 0.3.2, including tests and packaging.
