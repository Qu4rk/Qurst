# Wurst 26.3 native NeoForge port

Requested and authorized on 2026-10-06: full Wurst port for Minecraft Java 26.3; use Quarkloop, install relevant skills, split the project into slices, and start implementation.

## Fixed source and target

- Upstream: https://github.com/Wurst-Imperium/Wurst7, branch 26.3, commit `71e3af0f2747937086ef889e34359507d95cc9f8`, Wurst 7.56.
- Target: native NeoForge 26.3.0.51-beta, Java 25, ModDevGradle 2.0.147.
- Preserve upstream copyright, GPLv3 license, module behavior, settings schemas, assets, commands, and user controls. No publishing or Git commits are authorized.
- Source inventory: 156 *Hack.java files, 52 *Cmd.java files, 74 mixin source files. These are source counts, not a claim of tested runtime parity.
- Keep Fabric client tests as migration references; they do not count as NeoForge test evidence.

## Ordered slices

| Slice | Observable outcome | Required proof |
|---|---|---|
| 01 Native bootstrap | All Wurst features retained in a native NeoForge artifact; client starts to title screen | source inventory, compile/package/license checks, client launch |
| 02 GUI and persistence | ClickGUI/Navigator/options/keybinds/friends/profiles work and survive restart | native client tests for controls, serialization and restart |
| 03 Movement and input | Movement/camera/timer/player input features preserve upstream behavior | controlled singleplayer scenarios; port relevant upstream regression tests |
| 04 Rendering | HUD/ESP/X-Ray/Fullbright/weather/shaders render correctly | real render captures and upstream expected images where applicable |
| 05 Combat and packets | Combat/aim/projectile/damage and packet features behave as upstream | controlled local server and deterministic packet/event assertions |
| 06 World and inventory | Building/mining/farming/fishing/container/item commands operate correctly | seeded test world, inventory and block state assertions |
| 07 Other features and compatibility | Chat/session/serverfinder/utilities and supported NeoForge renderer mods work | local connection lifecycle tests and pinned compatibility profiles |
| 08 Full parity and packaging | Every inventory entry has passing evidence or a documented upstream limitation; installable release candidate | complete inventory audit, packaged-jar launch, regression and integration review |

Each slice receives its own frozen PLAN, builder evidence, and fresh independent Standards/Spec review. One working-tree writer at a time. Slice 01 retains all module implementations; later slices verify behavior and repair specific NeoForge patch interactions. No stubbed or silently removed feature counts as parity. Fabric-only Indigo compatibility has no native NeoForge target: preserve its source, exclude it from the native artifact, and explicitly account for it in the inventory. Sodium compatibility requires separate native testing.

## Environment

- Fresh dedicated clone in `/Volumes/MicroSD/Projects/BosonQ`, branch `codex/neoforge-26.3`.
- Java absent initially; SHA-256-verified Temurin 25 downloaded to `/Users/eliasliasides/.cache/bosonq/java25`.
- Installed minecraft-modding and minecraft-testing skills. Their general licensing examples do not override Wurst's license; legacy 1.21 examples do not override exact 26.3 APIs.
- ModDevGradle and lifecycle APIs checked against official NeoForge MDK and NeoForge/FML source on 2026-10-06.

## Verification status

Slice 01 native build, packaging and title-screen bootstrap checks pass. Independent review verdicts are recorded under the active `.quarkloop/runs/` directory. The original Fabric compilation/style baseline passes. The native artifact verifier was demonstrated to reject the Fabric distribution for missing NeoForge metadata. Native build and runtime evidence are recorded under the active `.quarkloop/runs/` directory. All feature behavior is NOT VERIFIED until its corresponding slice supplies evidence.

## Upstream source changes in slice 01

- Build, metadata and access transformer configuration now target NeoForge.
- WurstInitializer uses native client lifecycle registration.
- Fabric screen helper calls use vanilla widget enumeration through ScreenWidgets.
- Analytics and X-Ray mod detection read native loader metadata.
- Analytics/client login callbacks use native events.
- Better Block Entities detection uses the loading mod list during mixin selection.
- Early telemetry/session callbacks tolerate the later native lifecycle initialization. Re-enabling telemetry from an existing user profile needs explicit behavior verification in slice 07.
- Translation resource-pack identity filtering accepts the upstream Fabric/vanilla identities plus the native NeoForge identity `neoforge:mod/wurst` (observed at runtime). Slice 02a proves bundled English/German loading, the Wurst Options toggle and restart persistence.
- AntiWaterPush targets the native fluid predicate; Flight augments NeoForge's gliding check. Their in-world behavior remains a slice 03 requirement.
- Fabric Indigo's optional hook and Fabric gametests remain in the source tree as references. The native artifact has no Fabric dependency.

## Bootstrap proof and known diagnostics

`build` and `scripts/verify_neoforge_port.py` pass. `runClientSmoke` reached the title screen with 156 hacks, 52 commands, 18 other features and the Alt Manager button, printed `WURST_SMOKE_PASS`, and exited normally (12 seconds with assets cached). The test follows the first-run accessibility screen's Continue path and uses an isolated game directory.

Nine javac deprecation warnings in unchanged upstream code come from NeoForge's enchantment and abilities patches; address them when verifying the affected movement/inventory features. Optional Sodium mixins warn that their targets are absent in the vanilla renderer test profile. Vanilla command ambiguity and Apple OpenGL shader-interface diagnostics also appear; they do not prevent this bootstrap test from passing. NeoForge itself supplies the Sponge Mixin runtime under the `net.fabricmc` Maven group; the port has no Fabric API or Fabric Loader dependency.

## Tests still to migrate

The pinned upstream has 32 Fabric gametest Java files, including screenshot comparisons, movement regressions, inventory commands and entity filters. These are preserved under `src/gametest`. They are excluded from the native build and currently provide no NeoForge regression evidence. The isolated `src/smoke` test mod supplies only bootstrap/title screen verification. The isolated `src/clienttest` test mod proves translations/options/restart behavior across two client launches in `run/translations-test/`. The isolated `src/keybindtest` test mod proves keybind editing, profile save/load and restart input dispatch across two client launches in `run/keybinds-test/`, including an unselected-key save guard in the editor. Port the relevant upstream tests alongside each runtime behavior slice.

## Maintainer workflow

Use `scripts/gradle-java25` to select Java 25 on this machine, or set JAVA_HOME and use `./gradlew`. The active contract and ownership are under `.quarkloop/`. Only one builder may edit the working tree. Each completed slice requires fresh independent Standards and Spec review. Do not publish this development build as a completed full port.
