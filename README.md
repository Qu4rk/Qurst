# Wurst Client — native NeoForge 26.3 port

Development port of [Wurst Client 7.56](https://github.com/Wurst-Imperium/Wurst7/tree/26.3), pinned to commit `71e3af0f2747937086ef889e34359507d95cc9f8`.

Target: Minecraft Java **26.3**, NeoForge **26.3.0.51-beta**, Java **25**. This is a native NeoForge mod.

**Status:** slice 01 native build and title-screen bootstrap checks pass; slice 02a bundled-translation loading, Wurst Options toggle and restart persistence checks pass; slice 02b keybind editing, profile save/load and restart dispatch checks pass; the independent reviews are recorded under `.quarkloop/runs/`. The full upstream feature set is retained; runtime feature parity is being verified in the later slices. See [the roadmap](docs/NEOFORGE_PORT.md) and [the complete source inventory](docs/upstream-inventory.json).

## Build and run

Install a Java 25 JDK and set `JAVA_HOME`, then run:

```sh
./gradlew build --console=plain
python3 scripts/verify_neoforge_port.py
./gradlew runClientKeybindsTest --console=plain
./gradlew runClientTranslationsTest --console=plain
./gradlew runClientSmoke --console=plain
./gradlew runClient --console=plain
```

On the development Mac, `./scripts/gradle-java25` can be used in place of `./gradlew`; it selects the verified local Java 25 installation or a system JDK. It does not install Java.

The client runs in the project's `run/` directory. The automated bootstrap test uses `run/smoke/`, checks all 156 hacks, 52 commands, 18 other features and the title screen, then exits. The translations test uses `run/translations-test/` across two client launches: the first sets Minecraft to German and enables Wurst translations through the real Wurst Options Translations toggle; the second proves the persisted non-default setting still resolves German after restart. The keybind test uses `run/keybinds-test/` across two client launches: the first edits bindings and saves profile `slice02b.json` through the real manager/editor/capture/profiles screens; the second proves the restored bindings dispatch through the real native input handlers. Test-only code is outside the production artifact. Development runs use the upstream analytics debug path.

Build outputs are under `build/libs/`. The package verifier checks native metadata, every hack and command class, original assets, GPL notices and the corresponding sources jar. It rejects missing features and Fabric dependencies.

The original Fabric client tests are preserved in `src/gametest` as migration references. They do not currently run on NeoForge. See the roadmap for behavior testing and optional renderer compatibility.

## License and attribution

Original code: Wurst-Imperium and contributors, copyright 2014–2026. This fork preserves the upstream GPLv3 license and GPL-3.0-or-later metadata. See [LICENSE.txt](LICENSE.txt). Native port changes are identified in source comments and [the port notes](docs/NEOFORGE_PORT.md). Corresponding sources are built alongside the binary.

[Original upstream README](docs/UPSTREAM_README.md).
