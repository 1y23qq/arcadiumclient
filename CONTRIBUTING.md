# Contributing to Arcadium Client

Read [docs/BUILD.md](docs/BUILD.md) for build requirements and
[CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) before contributing.

Clone `https://github.com/0aurorq3/arcadiumclient` and create a branch for your
change. The launcher is a Rust workspace. `client-ui`, `client-runtime`,
`forge-compat` and `game-branding` contain the Minecraft components.

Build client components from source before compiling the launcher. Run the tests
relevant to your change and format edited Rust files with `cargo fmt`. Use an
isolated game and launcher data directory for first-run tests. The runtime QA
script accepts explicit cache reference and native-mod inputs; never use an
active account or existing world as a test fixture.

Use [Arcadium Discord](https://discord.gg/QbNfaCHjUz) for bugs and
feature requests. Include the client and operating-system versions, reproduction steps and
expected behavior. Remove account credentials and access tokens from any logs.

Pull requests should explain the resulting behavior and how it was verified.
Update documentation and regenerate component hashes when required. Generated
client JARs, Minecraft binaries, account files, cookies, tokens and signing keys
must not be committed. Keep existing copyright notices and third-party license
files. Run `python scripts/check_source_distribution.py` before publishing source.

Creating an installer does not publish a release. Release publication requires
the maintainer's approval.
