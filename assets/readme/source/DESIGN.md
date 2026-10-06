# Qurst README direction

Audience: players and contributors following the native Wurst port.
Value: Wurst Client on native NeoForge, with Qurst's purple identity.
Proof: current bootstrap, translations and keybind/restart checks; retained sources are distinct from verified runtime parity.
First successful action: Java 25 build, package verification, development client launch.
Theme: supplied purple hexagonal Q and block wordmark; calm, compact technical documentation.

Palette: dark violet #100b1c, pale lavender #eee8fa, purple #a73bd4, accent #c8a5ff, muted #9985b3.
Type: supplied wordmark plus system sans serif; 34-unit description, 28-unit supporting target labels on a 1200-unit canvas.
Shape: one 20-unit corner radius, 20-unit spacing grid. The supplied logo supplies the hexagonal motif.

Both logo PNGs are exact copies of the user's supplied originals. The editable SVG positions a viewport over the supplied transparent artwork. Published `../hero.png` is a deterministic SVG composition using that existing art; no ImageGen or replacement artwork was used. Essential instructions and proof remain in Markdown for mobile and accessibility.

To rebuild: run `node assets/readme/source/render-hero.cjs` with `sharp` available, or set `QURST_SHARP_MODULE` to the installed sharp module's absolute path. The repository does not require this renderer to build the mod.
