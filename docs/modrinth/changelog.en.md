# Offcraft 1.0.0 — Minecraft 1.21.1

Initial Modrinth upload, offered on the **Beta** channel. Choose the separate Fabric, Forge, or NeoForge file for your client; do not install multiple variants.

## Included

- Hidden-window real Minecraft client controlled by correlated JSONL requests.
- Local world creation/loading/switching, server connection, and orderly disconnect/shutdown.
- Tick-based vanilla input, camera control, GUI/text operations, player/inventory observations, and container clicks.
- Fresh PNG capture after frame completion, plus reduced background rendering without stopping ticks.
- Dependency-free Node.js MCP adapter in the source repository; Node.js 22+ is required only for MCP.
- Shared implementation bundled into each loader JAR; no separate common mod, Fabric API, or Architectury API dependency.

## Requirements and limits

Minecraft **1.21.1**, Java **21**, Windows **x64**, a graphical session, and OpenGL. Pinned development configurations: Fabric Loader **0.16.14**, Forge **52.1.0**, NeoForge **21.1.259**. The controller needs pipes to the actual Java process; this is not a fully headless mode.

Forge/NeoForge require `earlyWindowControl=false` in the game directory's `config/fml.toml` before launch. Forge also needs JVM flags `-Dterminal.ansi=false -Dterminal.jline=false` for piped operation.

Builds and recorded real development-client checks exist for all three loaders. Production-launcher installation, multiplayer, other operating systems, and third-party renderer compatibility are not verified. The standalone diagnostic JAR is not a Minecraft mod and is excluded.

[Launch instructions](https://github.com/mayorovyf/Offcraft/blob/main/docs/development.md) · [MCP](https://github.com/mayorovyf/Offcraft/blob/main/docs/mcp.md) · [Verification](https://github.com/mayorovyf/Offcraft/blob/main/docs/verification.md)
