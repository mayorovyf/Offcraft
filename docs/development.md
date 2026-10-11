# Building and running

[Русский](ru/development.md)

[Documentation](README.md) · [Project overview](../README.md)

JDK 21 is required. Fabric retains Gradle 8.14.3 / Fabric Loom 1.10.5 / Loader 0.16.14. Forge and NeoForge use Architectury Loom 1.17.493 and Gradle 9.5.0; the wrappers download the pinned Gradle version. Yarn is 1.21.1+build.3; NeoForge additionally uses `yarn-mappings-patch-neoforge:1.21+build.4`. Fabric API is not required. The root Maven harness is independent of the mod builds.

```powershell
cd C:\Projects\MinecraftMods\Offcraft\fabric
.\gradlew.bat --no-daemon --console=plain build
.\gradlew.bat --no-daemon --console=plain runClient
```

Each loader directory is an independent Gradle project; run the same `build` and `runClient` commands inside `forge/` or `neoforge/`. Finished artifacts from every project go to the root `dist/` directory; see the naming rules below. These are mods, not executable Java JARs; install only the JAR for your loader in the Minecraft 1.21.1 client's `mods/` directory. The pinned versions are Forge **52.1.0** and NeoForge **21.1.259**. The controller needs access to the Java process's stdin; a graphical launcher may not provide it. `runClient` uses a development profile and does not authenticate a Microsoft account.

## Versioning

Offcraft uses Semantic Versioning: `MAJOR.MINOR.PATCH`. The current version is `1.0.0`; Minecraft, loader, dependency and JSONL protocol versions are separate identifiers.

- **MAJOR**: incompatible public changes to JSONL/MCP commands, response semantics or supported usage that require consumer migration.
- **MINOR**: backward-compatible features or commands.
- **PATCH**: backward-compatible fixes and maintenance without a new public feature.
- Development builds use the target release version plus `-dev.N`, with a positive, increasing counter: `1.1.0-dev.1`, `1.1.0-dev.2`, then `1.1.0`. No `v` prefix in build metadata or filenames; Git tags add it: `v1.1.0-dev.1` / `v1.1.0`.

Use the same Offcraft version for all three loader builds, the standalone harness and MCP in a coordinated release. Before building/tagging, update [Fabric build.gradle](../fabric/build.gradle), [Forge build.gradle](../forge/build.gradle), `mod_version` in [NeoForge gradle.properties](../neoforge/gradle.properties), [pom.xml](../pom.xml), [mcp/package.json](../mcp/package.json) and MCP `serverInfo.version` in [adapter.mjs](../mcp/adapter.mjs). A tag does not change these values. Versions currently have explicit declarations in those files, not a shared source or automatic synchronization. See [Git release rules](git.md#tags-and-builds).

## Common artifact directory and names

The root `dist/` directory contains final distributable artifacts, not compilation caches or development JARs. Build commands remain loader-local (`build`) or root Maven (`package`); no additional copy command is required. The directory is ignored by Git.

| Artifact | Filename in `dist/` at version `1.0.0` |
| --- | --- |
| Fabric mod | `offcraft-1.0.0-mc1.21.1-fabric.jar` |
| Forge mod | `offcraft-1.0.0-mc1.21.1-forge.jar` |
| NeoForge mod | `offcraft-1.0.0-mc1.21.1-neoforge.jar` |
| Mod sources | Same loader filename with `-sources` before `.jar` |
| Executable diagnostic harness | `offcraft-1.0.0-standalone-windows-x64.jar` |

Mod template: `offcraft-<version>-mc<minecraft>-<loader>[-sources].jar`. Harness template: `offcraft-<version>-standalone-windows-x64.jar`; it is not a Minecraft mod. MCP runs from `mcp/index.mjs` and has no JAR build. Loader dependency versions remain in metadata/build configuration, not filenames.

Different versions and loaders coexist in `dist/`; rebuilding the same version replaces only that artifact. Module `clean` tasks remove their own `build/` or `target/`, not the shared `dist/` or other loaders' artifacts. Old artifacts are not deleted automatically; deliberately remove selected obsolete files when needed. Intermediate/unremapped JARs stay in module build directories and must not be installed instead of the `dist/` mod JARs.

## Forge and NeoForge early window

**Before launching an installed client**, set the following in `<game-directory>/config/fml.toml`:

```toml
earlyWindowControl=false
```

Preserve the file's other settings. Both loaders create their own early window before discovering ordinary mods; Offcraft cannot hide it retroactively. Without this setting, the entire launch therefore cannot be considered hidden. Development `runClient` prepares the file automatically before the game JVM starts; a direct Java/MCP launch requires the same advance configuration in its own `--cwd`/game-directory. This is not a JVM `-D` option. The shared mixin then creates the main game window hidden and disables fullscreen.

For a Forge launch with pipes, also pass the JVM options `-Dterminal.ansi=false -Dterminal.jline=false`: otherwise, Forge's TerminalConsoleAppender may leave an ANSI reset before the next JSONL line or interfere with stdin. Development `runClient` sets them automatically. For an installed Java/MCP launch, add them to `--args-json` before the main class. This does not disable error messages, only formatting and the interactive terminal frontend.

## Sodium and Iris

```powershell
cd C:\Projects\MinecraftMods\Offcraft\fabric
.\gradlew.bat --no-daemon --console=plain -Prenderer=sodium runClient
.\gradlew.bat --no-daemon --console=plain -Prenderer=iris runClient
```

These renderer profiles apply only to **Fabric**: without the option, the profile is `vanilla`; pinned versions are Sodium 0.6.9 for 1.21.1 and Iris 1.8.8 + Sodium 0.6.9. These are runtime dependencies of the development launch, not parts of the Offcraft JAR. Fabric mods cannot be transferred to Forge/NeoForge. For an installed client, use renderer mods specifically for your loader; place the shaderpack in `shaderpacks/` and enable it through Iris. Compatibility with Forge/NeoForge renderer mods is not claimed here.

## Where versions are pinned

- Fabric: [build.gradle](../fabric/build.gradle), [Gradle wrapper](../fabric/gradle/wrapper/gradle-wrapper.properties).
- Forge: [build.gradle](../forge/build.gradle), [Gradle wrapper](../forge/gradle/wrapper/gradle-wrapper.properties).
- NeoForge: [gradle.properties](../neoforge/gradle.properties), [build.gradle](../neoforge/build.gradle), [Gradle wrapper](../neoforge/gradle/wrapper/gradle-wrapper.properties).
- Standalone: [pom.xml](../pom.xml); instructions in [standalone.md](standalone.md).

The build commands above run from the respective loader's directory. The MCP example, test commands, and commands in standalone.md run from the repository root unless explicitly stated otherwise.

Verification scenarios: [verification.md](verification.md). Connecting a controller: [protocol.md](protocol.md) and [mcp.md](mcp.md).
