# Qurst branding checkpoint — 2026-10-06

User request: create the public `Qu4rk/Qurst` repository, rename BosonQ to Qurst, use the two supplied Qurst logos, apply a purple theme, redesign the README, then prepare the next Quarkloop contract. This is a separately authorized maintenance change after Slice 02b; it is not part of that slice's frozen contract or approval.

## Change

- Gradle project/archive name and NeoForge display name: Qurst.
- README: supplied logo in a dark violet composition, verified feature status, build/use commands, limitations and upstream attribution. PNG published with editable SVG and exact original logo copies.
- Client: supplied transparent logo in HUD/Options; Qurst Options title/narration; purple chat prefix and default ClickGUI/HUD text/background palette. Existing custom settings are preserved.
- Compatibility: retain `wurst` mod ID, resource namespace, feature names, package names and saved-data directory. Original upstream assets, source inventory, copyright notices and GPL license remain packaged.
- Repository: Qurst issue forms and native build/package CI. Original upstream GitHub publishing/maintenance/funding files are preserved under `docs/upstream-github/`.

## Verification

Fresh local commands on Java 25, Minecraft 26.3 / NeoForge 26.3.0.51-beta:

- `./scripts/gradle-java25 build --console=plain`: PASS (5 seconds). A fresh `clean build` also passed after the archive rename; this removed stale generated Wurst jar outputs that made the default verifier correctly reject an ambiguous `build/libs/`.
- `python3 scripts/verify_neoforge_port.py` and its explicit Qurst jar variant: PASS; retained feature classes/assets, native metadata, license and sources jar.
- `./scripts/gradle-java25 runClientKeybindsTest runClientTranslationsTest runClientSmoke --console=plain`: PASS (32 seconds); emitted `WURST_KEYBINDS_PASS`, `WURST_TRANSLATIONS_PASS`, `WURST_SMOKE_PASS`.
- README skill audit: PASS. Visually inspected the full rendered README at 900-pixel content width and 360-pixel mobile viewport, including the supplied-art hero, status, commands and licensing. The hero is about 120 KB.
- `git diff --check`: PASS.

The native tests establish the previously approved behavior; they do not establish full runtime feature parity or exhaustive visual parity. The new workflow runs build/package checks; graphical client tests remain local. Existing upstream deprecation and absent optional-renderer diagnostics remain unchanged.
