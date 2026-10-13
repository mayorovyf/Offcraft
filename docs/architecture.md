# Architecture and invariants

[Русский](ru/architecture.md)

[Documentation](README.md) · [Project overview](../README.md)

A Minecraft mod for Windows x64, Java 21, and **Minecraft 1.21.1**, with separate Fabric, Forge, and NeoForge builds:

- `common/` — the shared implementation of JSONL, game actions, GUI, observation, framebuffer capture, and client mixins; package `offcraft.client`.
- `fabric/`, `forge/`, `neoforge/` — bootstrap code and metadata for the respective loader. Each JAR includes common; no additional common mod is installed. The Architectury API is not required at runtime.
- The root Maven project is a diagnostic LWJGL 3.3.6 harness with a triangle, a hidden GLFW window, and a 640×360 framebuffer.

`mcp/` is a separate stdio MCP adapter for Node.js 22+ that launches the real client and translates MCP requests into the game's JSONL protocol. The client's native window object exists but is not shown; a graphical session and a working OpenGL driver are still required.

## Code map

| File / directory | Responsibility |
| --- | --- |
| `common/src/main/java/offcraft/client/OffcraftClient.java` | stdin queue, command dispatch, active request, loading/connection, shutdown, and captures |
| `CommandProtocol.java` in the same package | Strict JSONL parser and protocol 1 responses, independent of Minecraft's stdout logger |
| `PlayerAction.java` | Vanilla key bindings, tick preparation/completion, input release, and action results |
| `GameUi.java` | On-demand player/GUI/slot observations, curated item components, vanilla callbacks and clickSlot |
| `RenderPolicy.java` | Background rendering without stopping the game loop |
| `MessageJournal.java` | 256-entry process-lifetime HUD message ring; 4096 UTF-16 units per entry, cursor/gap reads |
| `WorldCatalog.java` | Creation-only seed/mode/difficulty validation and bounded read-only save discovery |
| `common/src/main/java/offcraft/client/mixin/` | Frame/tick hooks, hidden window, prevention of mouse capture, accessors, and GUI modifiers |
| `common/src/main/resources/offcraft.mixins.json` | Required client mixins, JAVA_21, defaultRequire=1 |
| `fabric/src/main/java/offcraft/fabric/` | ClientModInitializer → OffcraftClient.initialize |
| `forge/src/main/java/offcraft/forge/` | Discovery entrypoint without client classes; a CLIENT subscriber calls initialize |
| `neoforge/src/main/java/offcraft/neoforge/` | CLIENT-only entrypoint calls initialize |
| `mcp/adapter.mjs`, `mcp/index.mjs` | GameBridge, MCP endpoint, process launch, and CLI |
| `mcp/sdk.mjs`, `mcp/recording.mjs` | JavaScript client API and optional bounded protocol recording outside the game process |
| `mcp/schemas.mjs`, `mcp/adapter.test.mjs` | Command schemas, validation, and contract tests |
| `src/main/java/offcraft/Offcraft.java` | Standalone OpenGL harness with a text interface |

## Module boundaries

Each loader compiles `../common/src/main/java` and includes `../common/src/main/resources`. Game features are implemented in common, not duplicated in the loaders' bootstrap code. The root Maven project does not build the mods; the shared code is not a separate Gradle project. MCP launches the real executable without a shell and does not replace the game process.

New observations are request-driven; no world scans, navigation, macro interpreter or video stream run in the mod. Only displayed messages append to the bounded journal. `press` uses one vanilla drop/swap key tick; full-stack dropping changes the Control query only for that prepared input tick. Input release includes sprint/drop/swap bindings. The Node.js layer reads explicit completed captures for MCP images, shares the existing bridge with the SDK, and records protocol only when enabled. PNG writing remains synchronous on the render thread; frequent captures require measurement, not a background-rendering performance claim.


## Hidden mode

- A mixin intercepts `glfwDefaultWindowHints`, performs the original reset, then sets `GLFW_VISIBLE`, `GLFW_FOCUSED`, and `GLFW_FOCUS_ON_SHOW` to false and disables fullscreen. This redirect does not compete with Sodium's interception of `glfwCreateWindow` and is permitted by Forge Mixin's constructor injections. The window is not hidden after creation; the Forge/NeoForge early window is disabled separately using the [early-window setting](development.md#forge-and-neoforge-early-window).
- The client's initial internal state is unfocused. Automatic pausing on focus loss is disabled; the game is prevented from capturing or moving the cursor.
- A daemon thread reads stdin, and a bounded queue holds 32 lines. Commands execute on the render thread; game buttons are set before input processing on each tick, and ticks are counted after they finish. Completion, interruption, `quit`, EOF, and shutdown clear all controlled buttons and queued presses. Toggle-sneak and physical key rebinding do not change the controller's logical actions.
- Closing a screen clears vanilla's GUI-only attack suppression before controlled input resumes; ordinary attack cooldowns remain intact, without an extra neutral action tick or cursor capture.
- The default is `reduced`: the client loop is limited to 30 iterations/s, and expensive world rendering to 2 frames/s; game and network ticks continue. `render` and the completion of actions/GUI operations require a fresh frame. Menus and the loading overlay are not skipped. `normal` restores continuous rendering with the saved FPS limit, but not below 20; `fps` configures only reduced rendering, whose actual ceiling is the loop frequency. This is not the standalone harness's full demand-rendering mode.
- The game framebuffer's color texture is read through Minecraft's `NativeImage`, with rows flipped. NativeImage is reused until the size changes; the texture binding is restored after reading. PNG files are written synchronously on the render thread, delaying the frame while the write is in progress.
- The default development window size is 640×360; Minecraft's saved size settings may override it. Captures use the actual framebuffer size.
- The launch terminal remains visible. Minecraft audio is not disabled. To launch without a new console, the controller must start Java with redirected streams and the appropriate Windows process options.

## Contracts that must be preserved

- GLFW/OpenGL operations and Minecraft changes run on the client's render thread; the stdin thread only places lines in the queue.
- Action duration is measured in client game ticks. Input remains vanilla; movement is not replaced with teleportation, nor are stack operations replaced with direct writes.
- Completion, interruption, cancellation, EOF, and shutdown release held buttons and queued presses.
- `accepted` does not mean success. A long-running command's terminal response is tied to actual world readiness, action completion, or the PNG write.
- A fresh capture is taken after rendering; reduced mode does not stop ticks or network servicing.
- Client observation and local prediction are not presented as server confirmation.

Detailed formats and errors: [JSONL](protocol.md). MCP changes must stay synchronized with the [schemas and adapter](mcp.md).
