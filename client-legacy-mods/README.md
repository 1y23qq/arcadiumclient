# Minecraft 1.7.10 dependency ports

These modified GPL, LGPL and MIT dependencies retain their original authors, copyright headers and licenses. `ports.json` records pinned upstream commits, original source archive hashes, build tasks and license hashes. Arcadium changes adapt the audited providers to Ornithe Gen2 1.7.10; each original license remains in its project directory.

Build Arcadium Core for 1.7.10 and publish its modules to Maven Local first, then use `scripts/build_client.py` to build all listed ports with the checked-in JDK 25 Gradle wrapper. Minecraft code, downloaded dependency binaries, build caches and local account data are excluded. Minecraft-owned texture overrides are resolved from the installed game instead of being redistributed.

PolyPlus, CrashPatch, ChatTweaks and PolySprint are excluded. Argentum remains an unmodified external dependency for Minecraft 1.8.9 only. SmartSearch keeps its existing audited cross-version download.
