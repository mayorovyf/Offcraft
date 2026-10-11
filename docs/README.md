# Offcraft technical documentation

[Русский](ru/index.md)

[User overview](../README.md) · [Agent instructions](../AGENTS.md)

Commands target Windows / PowerShell unless explicitly marked as Bash in the Git guide. Relative source paths are relative to the repository root.

| Document | When to read |
| --- | --- |
| [architecture.md](architecture.md) | Before changing common code, mixins, threads, input, capture, or loader boundaries |
| [development.md](development.md) | For builds, launch, version updates, early-window settings, and Fabric renderer profiles |
| [protocol.md](protocol.md) | When working with JSONL commands, errors, world state, GUI, and inventory |
| [mcp.md](mcp.md) | When connecting MCP or changing schemas, transport, ID correlation, or cancellation |
| [verification.md](verification.md) | To choose checks and smoke scenarios, and understand historical evidence limits |
| [standalone.md](standalone.md) | For the root Maven/LWJGL harness, separate from Minecraft mods |
| [git.md](git.md) | Before branch, commit, integration, tag, or publication operations |

Detailed contracts and procedures live here. The root README remains a user overview; AGENTS.md contains only permanent context and mandatory invariants.

Primary documents are in English. Russian counterparts, including the user overview and agent instructions, are in [docs/ru](ru/index.md); update both languages together.
