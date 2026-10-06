#!/usr/bin/env python3
"""Check the native distribution against the fixed Wurst upstream inventory."""

import argparse
import json
from pathlib import Path
import subprocess
import sys
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parents[1]
BASE = "71e3af0f2747937086ef889e34359507d95cc9f8"


def require(condition, message):
    if not condition:
        raise ValueError(message)


def verify(jar_path):
    paths = subprocess.check_output(
        ["git", "ls-tree", "-r", "--name-only", BASE], cwd=ROOT, text=True
    ).splitlines()
    features = [
        path for path in paths
        if (path.startswith("src/main/java/net/wurstclient/hacks/")
            and path.endswith("Hack.java"))
        or (path.startswith("src/main/java/net/wurstclient/commands/")
            and path.endswith("Cmd.java"))
    ]
    require(len(features) == 208, "Fixed upstream inventory unexpectedly differs")
    for path in features:
        require((ROOT / path).is_file(), f"Missing upstream feature source: {path}")
    require(jar_path.is_file(), f"Distribution not built: {jar_path}")
    with zipfile.ZipFile(jar_path) as jar:
        entries = set(jar.namelist())
        require("META-INF/neoforge.mods.toml" in entries, "Missing native NeoForge metadata")
        metadata = tomllib.loads(jar.read("META-INF/neoforge.mods.toml").decode())
        require(metadata["mods"][0]["modId"] == "wurst", "Wrong mod identity")
        require("${" not in jar.read("META-INF/neoforge.mods.toml").decode(),
                "Unexpanded mod metadata")
        require(metadata["license"] == "GPL-3.0-or-later", "Upstream license changed")
        require(metadata["mixins"][0]["config"] == "wurst.mixins.json", "Mixins not registered")
        require("META-INF/accesstransformer.cfg" in entries, "Missing access transformer")
        require(not any(name.startswith("net/wurstclient/smoke/") for name in entries),
                "Test-only smoke code in production distribution")
        require(not any(name.startswith("net/wurstclient/clienttest/") for name in entries),
                "Test-only clienttest code in production distribution")
        require("fabric.mod.json" not in entries, "Fabric metadata in native distribution")
        require("wurst.accesswidener" not in entries, "Fabric access widener in native distribution")
        require(any(name.startswith("LICENSE") for name in entries), "GPL license missing")
        require("net/wurstclient/mixin/xray/indigo/AltModelBlockRendererImplMixin.class"
                not in entries, "Fabric-only Indigo hook in native distribution")
        mixins = json.loads(jar.read("wurst.mixins.json"))
        require(mixins["required"] and mixins["injectors"]["defaultRequire"] == 1,
                "Required mixin checks weakened")
        for name in mixins["client"]:
            require("net/wurstclient/mixin/" + name.replace(".", "/") + ".class" in entries,
                    f"Registered mixin missing: {name}")
        for path in features:
            entry = path.removeprefix("src/main/java/").removesuffix(".java") + ".class"
            require(entry in entries, f"Feature absent from native distribution: {entry}")
        for path in paths:
            if path.startswith("src/main/resources/assets/"):
                require(path.removeprefix("src/main/resources/") in entries,
                        f"Missing upstream asset: {path}")
        for name in entries:
            if name.endswith(".class"):
                bytecode = jar.read(name)
                require(b"net/fabricmc/" not in bytecode, f"Fabric class dependency: {name}")
    source_jar = jar_path.with_name(jar_path.stem + "-sources.jar")
    require(source_jar.is_file(), f"Corresponding sources jar missing: {source_jar.name}")
    with zipfile.ZipFile(source_jar) as sources:
        for path in features:
            require(path.removeprefix("src/main/java/") in sources.namelist(),
                    f"Corresponding feature source missing: {path}")
        require(any(name.startswith("LICENSE") for name in sources.namelist()),
                "Corresponding sources license missing")
    print(f"PASS: 156 hack sources, 52 command sources, all packaged features/assets, "
          f"native metadata and GPL source distribution: {jar_path.name}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path)
    args = parser.parse_args()
    jars = [path for path in (ROOT / "build/libs").glob("*.jar")
            if not path.name.endswith("-sources.jar")]
    if args.jar:
        jar_path = args.jar.resolve()
    else:
        require(len(jars) == 1, "Expected exactly one native distribution in build/libs")
        jar_path = jars[0]
    verify(jar_path)


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, zipfile.BadZipFile) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        sys.exit(1)
