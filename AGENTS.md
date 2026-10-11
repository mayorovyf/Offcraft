# AGENTS.md

[Русский](docs/ru/AGENTS.md)

## Always keep in mind

- Offcraft controls a real Minecraft 1.21.1 client through JSONL; the target environment is Windows x64 and Java 21. A hidden window still requires a graphical session and OpenGL.
- `common/` is the single implementation of game features. `fabric/`, `forge/`, and `neoforge/` are separate Gradle projects containing loader bootstrap/metadata; each JAR includes common. Do not duplicate game logic across loaders or introduce runtime Architectury API without an explicit need.
- Root `pom.xml` and `src/` form a separate diagnostic LWJGL harness, not a mod build. `mcp/` is a dependency-free Node.js 22+ stdio adapter that launches the real client.

## Change invariants

- Minecraft, GLFW/OpenGL, game input, and capture run on the render thread. The stdin thread only passes lines to a bounded queue.
- Actions are measured in game ticks and use vanilla mechanics. Do not replace input with teleportation or container operations with direct stack writes.
- Completion, interruption, cancellation, quit, EOF, and shutdown must release controlled buttons and queued presses.
- `accepted` means acceptance, not success. Preserve ID correlation and a single terminal `completed/error` response for an accepted request; a duplicate active ID must not produce a second terminal response.
- Only one long-running mutating command is active at a time; observations, status, and cancellation available during it must not wait behind the action.
- PNG capture must represent a fresh, finished frame; the capture response follows file writing. Reduced rendering must not stop game or network ticks.
- Create the window hidden rather than hiding it after display. Do not restore fullscreen, cursor capture, or automatic pausing due to lost focus.
- Client observations, packet sending, and local prediction are not server confirmation. A development profile is not an authenticated Microsoft account.

## Working rules

- Read the affected code and the relevant document below first. Follow existing conventions and limit changes to the task.
- Changes to common affect all three loaders. Command changes require consistent updates to the implementation, MCP schemas, affected contract tests, and JSONL reference.
- Do not overwrite others' changes, worlds, or configuration. Do not automatically stash, reset, clean, switch over unfinished changes, or force-push.
- Read [docs/git.md](docs/git.md) before Git operations. Branch rules are policy, not evidence that `dev`, a remote, or branch protection exists. Commit, merge, tag, and publish only with authorization within the task.
- Exercise the actual changed execution path. A build and test transport do not replace a real-client smoke run; documentation-only work needs content and link checks.
- Report only checks actually performed, with concrete results and limitations. Historical entries in docs are not a new PASS.
- Update the relevant documentation after changes. Keep primary documents in English and Russian copies in `docs/ru/`; update both together. Keep README introductory and detailed procedures/contracts in docs rather than expanding this file.

## Read when relevant

| Task | Document |
| --- | --- |
| Common code, mixins, threads, input, rendering, capture | [Architecture](docs/architecture.md) |
| Build/launch, versions, loaders, Forge/NeoForge early window, Sodium/Iris | [Development](docs/development.md) |
| Commands, errors, loading/connections, GUI, and slots | [JSONL protocol](docs/protocol.md) |
| MCP, schemas, IDs, cancellation, stdio, and process shutdown | [MCP](docs/mcp.md) |
| Choosing checks, smoke scenarios, and evidence limits | [Verification](docs/verification.md) |
| Root Maven/LWJGL project | [Standalone](docs/standalone.md) |
| Branches, commits, integration, tags, and publication | [Git](docs/git.md) |

Full index: [docs/README.md](docs/README.md).
