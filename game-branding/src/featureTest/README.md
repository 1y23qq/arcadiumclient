Build this development fixture with `gradlew.bat remapFeatureTestJar`. The task is separate from the branding JAR and installer components.

Run Minecraft with `-Darcadium.featureTest=true`, username `ArcadiumTest`, access token `0`, and a fresh game directory. The fixture creates `arcadium-feature-qa`, writes PNG evidence and `feature-qa/result.json`, then restores changed module preferences. `-Darcadium.featureTest.exit=true` exits after success.

The checks use OpenGL and cover separate HUD scales, inventory hover/click alignment, username masking before text rendering, unchanged identity and armor equipment, focused search input, and complete module settings after navigation.

Set `-Darcadium.rendering042=true` for the separate 1.8.9 rendering fixture. It checks Compact Chat counters, badge and nametag bounds, Height Limit HUD color boundaries and custom formatting, Health units, and Unfocused FPS. Screenshots and `rendering042-result.json` are written inside the isolated game directory.

Inventory, chest and crafting captures also verify the Arcadium watermark stays at the physical bottom-right corner while their scale changes. The pause-menu capture checks its centered position above the existing buttons. The bitmap must load only once.

Set `-Darcadium.featureTest.containerCoverage=true` for the separate 12-phase container suite. It exercises Creative inventory tabs, Furnace, Anvil, Horse inventory and Unicode Small/Medium/Large/Auto with live tooltip, cursor and item-pick checks. Creative captures include tab hover and item hover before and after a real tab click.

Optional account popup QA uses `-Darcadium.featureTest.accounts=true`, `-Darcadium.launcherExecutable=<absolute launcher path>`, and `-Darcadium.launcherDataDir=<game directory>/launcher-data`. It creates two offline fixture accounts. The fixture rejects helper storage outside the test game directory. It never signs in, switches accounts, or removes accounts.

Set `-Darcadium.featureTest.028=true` to select the separate 0.2.8 fixture. It checks live HUD providers, Ping ranges and zero visibility, TPS rounding and high readings, native biome and potion colors, multiple item counter transitions, Tab and container opacity endpoints, and native folder/ZIP resource pack discovery and sharing preparation. It also creates torch-lit and enclosed ground scenes for Light Level and checks depth state while numbers are drawn. PNG evidence and `result.json` are written to `feature-qa-028/`. Use a fresh offline test game directory.
