# Offcraft

**Control a real Minecraft client through JSONL or MCP, with a hidden game window and fresh PNG captures.**

Offcraft is a client-side mod for **Minecraft Java Edition 1.21.1**, available as separate **Fabric, Forge, and NeoForge** builds. It lets an external program or MCP client operate the game through normal Minecraft input and interface mechanics.

> **Read before installing:** Offcraft intentionally creates the game window hidden. It is a developer/controller integration tool, not a normal visible-play mod. A controller must launch the actual Java client with access to stdin/stdout. Installing the JAR in a graphical launcher alone does not provide a controller, and closed stdin triggers orderly client shutdown.

## What it does

- Create and load local worlds, save and switch worlds, and connect to servers using the launched client's profile.
- Move, jump, sneak, attack, use items, select hotbar slots, and rotate the camera.
- Operate inventories, containers, GUI keys, mouse clicks, and Unicode text input through vanilla mechanics.
- Observe player state, inventory contents, interface state, and the crosshair target.
- Capture a fresh, completed game frame as PNG.
- Reduce background rendering while game and network ticks continue.
- Connect MCP clients through a dependency-free Node.js stdio adapter included in the source repository.

Actions are measured in game ticks, not milliseconds. Completion reports client-side execution; it is not an independent guarantee of server confirmation. Offcraft does not replace movement with teleportation, write container stacks directly, authenticate Microsoft accounts, or bypass server restrictions.

## Requirements

- **Minecraft 1.21.1**, **Java 21**, **Windows x64**.
- A graphical desktop session and working OpenGL driver, even with the window hidden. This is **not fully headless Minecraft** and not a server bot.
- One matching loader: Fabric Loader **0.16.14**, Forge **52.1.0**, or NeoForge **21.1.259** are the pinned development configurations.
- **Node.js 22+** only if using the optional MCP adapter.

Install **only one** Offcraft JAR, matching the instance's loader. Fabric API, Architectury API, Sodium, and Iris are not required. Do not install Offcraft on a dedicated server. Other operating systems, third-party rendering mods, production-launcher installation, and multiplayer compatibility are not established by the current verification.

## Installation and launch

1. Prepare a separate Minecraft 1.21.1 client instance with Java 21 and the appropriate loader.
2. Put the matching `offcraft-1.0.0-mc1.21.1-<loader>.jar` in that instance's `mods/` directory. Source JARs are for reference, not installation.
3. **Forge / NeoForge:** before launch, set `earlyWindowControl=false` in the instance's `config/fml.toml`, preserving its other settings. Their early loader window exists before mod discovery and cannot be hidden retroactively by Offcraft.
4. **Forge:** add JVM arguments `-Dterminal.ansi=false -Dterminal.jline=false` to preserve piped JSONL/stdin operation.
5. Launch the actual Java client from your controller with stdin/stdout pipes and the loader's real JVM/main/game arguments. A `.bat` file or graphical launcher is not a substitute for the Java process. Keep stdin open while operating the client.
6. Wait for the protocol `ready` event, then inspect `status`. `ready` means commands are accepted, not that a world is loaded. Resolve startup screens through GUI commands if necessary.

The [build and launch guide](https://github.com/mayorovyf/Offcraft/blob/main/docs/development.md) covers development launches and loader configuration. The [MCP guide](https://github.com/mayorovyf/Offcraft/blob/main/docs/mcp.md) includes an executable Fabric development example and the requirements for installed clients.

## JSONL example

Send one JSON object per line. Send the next world/action command only after the previous ID receives terminal `completed` or `error`:

```json
{"id":"world-1","command":"new","world":"controller-demo"}
{"id":"walk-1","command":"act","ticks":20,"buttons":["forward"],"capture":"output/after-walk.png"}
{"id":"status-1","command":"status"}
{"id":"quit-1","command":"quit"}
```

`accepted` is not success. Protocol responses carry `protocol:1`; ordinary Minecraft logs also appear on stdout/stderr and must be distinguished from responses. Relative PNG paths resolve from the game process's working directory. EOF and `quit` release controlled input, save normally, and stop the client.

## Compatibility and release status

Version **1.0.0** is offered on the **Beta** channel. All three loader builds include the same shared game implementation. Recorded real development-client checks cover world loading, input, creative inventory persistence, capture, cancellation, and shutdown. These checks do not establish compatibility with every launcher, server, driver, or modpack; see the [verification record](https://github.com/mayorovyf/Offcraft/blob/main/docs/verification.md) for evidence and limits.

Use separate test worlds and backups. Follow each server's rules for automation.

## Links and license

- [Source code](https://github.com/mayorovyf/Offcraft)
- [Documentation](https://github.com/mayorovyf/Offcraft/blob/main/docs/README.md)
- [JSONL command reference](https://github.com/mayorovyf/Offcraft/blob/main/docs/protocol.md)
- [Report an issue](https://github.com/mayorovyf/Offcraft/issues)
- [Русская документация](https://github.com/mayorovyf/Offcraft/blob/main/docs/ru/index.md)

Offcraft source and documentation use the **MIT License**. Minecraft and third-party components retain their own licenses. The standalone LWJGL diagnostic harness is not a Minecraft mod and is not part of these Modrinth versions.
