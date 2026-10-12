# Offcraft

Control a real Minecraft client through **JSONL or MCP**. Offcraft is a client-side mod for **Minecraft 1.21.1**, available for **Fabric, Forge, and NeoForge**.

- Create and load worlds, connect to servers, and control movement and camera.
- Interact with inventories and GUIs through vanilla mechanics.
- Observe player state and capture fresh PNG frames.
- Reduce background rendering without stopping game ticks.

**Important:** the game window is intentionally hidden. An external controller must launch the actual Java client with stdin/stdout pipes and keep stdin open; closing it shuts down the client. This is a developer automation tool, not a normal visible-play mod or fully headless Minecraft.

## Requirements

Minecraft **1.21.1**, Java **21**, Windows **x64**, and a graphical session with OpenGL. Install only the JAR matching your loader. No server-side installation, Fabric API, or Architectury API is required. The optional MCP adapter requires **Node.js 22+**.

For **Forge/NeoForge**, set `earlyWindowControl=false` in `config/fml.toml` before launch. Forge also requires JVM flags `-Dterminal.ansi=false -Dterminal.jline=false` for piped operation.

**Beta:** production-launcher installation, multiplayer, other operating systems, and third-party rendering mods remain unverified. Follow server rules for automation.

[Setup guide](https://github.com/mayorovyf/Offcraft/blob/main/docs/development.md) · [MCP](https://github.com/mayorovyf/Offcraft/blob/main/docs/mcp.md) · [Source code](https://github.com/mayorovyf/Offcraft)
