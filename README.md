# Offcraft

[Русский](docs/ru/README.md)

Offcraft is a mod for **Minecraft 1.21.1** that lets an external controller or MCP client operate a real game client. The game window stays hidden, while its image can be captured as PNG.

It is neither a server bot nor a fully headless mode: the client still needs a graphical session and a working OpenGL driver.

## Features

- Create and load local worlds, switch between them, and connect to servers.
- Move, jump, sneak, attack, use items, and rotate the camera through normal game mechanics.
- Operate inventories, containers, the interface, and text input.
- Read player state, items, and the crosshair target.
- Save fresh game frames as PNG and reduce background rendering without stopping game ticks.
- Connect external tools through an MCP adapter.

## Requirements and compatibility

| Component | Supported configuration |
| --- | --- |
| OS | Windows x64 |
| Minecraft | 1.21.1 |
| Java | 21 |
| Fabric Loader | 0.16.14 |
| Forge | 52.1.0 |
| NeoForge | 21.1.259 |
| MCP adapter, if used | Node.js 22+ |

Each loader uses a separate JAR. No additional Fabric API, Architectury API, or separate common mod is required.

## Installation and control

1. Prepare a Minecraft 1.21.1 client with your chosen loader and Java 21.
2. Install **only the JAR for your loader** in `mods/`. The mod is not a standalone application launched with `java -jar`. See the [build guide](docs/development.md) to build a JAR from source.
3. For Forge and NeoForge, set `earlyWindowControl=false` in the game directory's `config/fml.toml` **before launching**, preserving all other settings. Otherwise the loader's early window remains visible.
4. Launch the client through a controller with access to the Java process's stdin, or through the [MCP adapter](docs/mcp.md). A regular graphical launcher may not provide the required input stream. Forge-specific launch settings are covered in the [launch guide](docs/development.md).

Control uses JSON Lines commands. The controller must wait for client readiness and completion of the previous mutating command; an acceptance acknowledgement does not mean execution. Request formats and examples are in the [command reference](docs/protocol.md).

## Limitations

- Offcraft does not authenticate a Microsoft account or bypass server restrictions. Development launches use a test profile.
- Actions follow vanilla mechanics: collisions, item-use delays, and server rules still apply.
- Observations describe client state, not separate server confirmation.
- The game window is hidden, but the launch terminal is not necessarily hidden; Minecraft audio is not disabled.
- Sodium 0.6.9 and Iris 1.8.8 were checked with Fabric. Compatibility with third-party Forge/NeoForge renderer mods or arbitrary shaderpacks is not claimed.
- Previous checks used development clients; they do not establish installation in a regular authenticated launcher or operation as a Windows service.

## Documentation

- [Build and launch](docs/development.md)
- [JSONL commands, GUI, and inventory](docs/protocol.md)
- [MCP connection](docs/mcp.md)
- [All technical documentation](docs/README.md): architecture, verification, diagnostic harness, and Git

Instructions for agents working in this repository: [AGENTS.md](AGENTS.md).

## Publication and licensing

Offcraft source code and documentation are licensed under the [MIT License](LICENSE). Keep its copyright and permission notice when distributing copies or substantial portions. Bundled dependencies retain their own licenses; MIT does not relicense Minecraft or third-party components.

Built JARs are written to `dist/`; generated files and local game data are excluded from source control. See [GitHub publication preparation](docs/git.md#preparing-the-first-github-publication) before uploading the repository.
