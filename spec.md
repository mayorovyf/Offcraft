# Integrating Offcraft into an existing project

[Русский — инструкция подключения](docs/ru/spec.md)

[Project overview](README.md) · [Documentation](docs/README.md)

## 1. Integration boundary

Offcraft operates a **real Minecraft Java Edition 1.21.1 client**. Install its loader-specific mod in the game instance; connect your existing application to that client's process. Do not embed Minecraft classes into your application, run the mod with `java -jar`, or install the standalone diagnostic harness instead of the mod.

Choose one connection:

| Existing project | Connection | Process owner |
| --- | --- | --- |
| Application, automation runner, game agent, custom backend | JSONL over Java stdin/stdout; any language capable of subprocess pipes | Your application |
| MCP-capable agent or desktop application | MCP stdio through `mcp/index.mjs` | MCP host starts the adapter; adapter starts Java |

There is no built-in HTTP, WebSocket, TCP listener, remote attach, or shared multi-user game session. Both connections use local process pipes. For remote deployment, run the process owner on the machine with Minecraft and arrange your own authenticated access; do not expose arbitrary game commands or file paths publicly.

## 2. Prerequisites and installation

1. Use **Windows x64**, **Java 21**, a graphical desktop session, and an OpenGL driver. The window is hidden, but Minecraft is not fully headless; a Windows service without a graphical session is not an established deployment configuration.
2. Prepare a dedicated Minecraft **1.21.1** instance. Back up existing worlds and configuration; do not point concurrent clients at the same instance.
3. Install the matching loader. Pinned development configurations: Fabric Loader **0.16.14**, Forge **52.1.0**, NeoForge **21.1.259**.
4. Place exactly one matching mod JAR in `<game-directory>/mods/`:
   - `offcraft-1.0.0-mc1.21.1-fabric.jar`
   - `offcraft-1.0.0-mc1.21.1-forge.jar`
   - `offcraft-1.0.0-mc1.21.1-neoforge.jar`
5. Do not install source JARs or `offcraft-1.0.0-standalone-windows-x64.jar`. No separate common mod, Fabric API, or Architectury API is required.
6. Obtain the actual **Java executable and complete JVM/main/game argument array** from the instance's launch configuration. Include its classpath or module path, libraries, assets, natives, main class, game directory and profile arguments. Offcraft does not assemble these or authenticate accounts. A normal launcher may not expose stdin to your program; the controller must launch the actual Java process, not the launcher or a `.bat` wrapper.
7. For **Forge and NeoForge**, set `earlyWindowControl=false` in `<game-directory>/config/fml.toml` **before** Java starts, preserving other settings. This is not a `-D` property.
8. For **Forge**, also include `-Dterminal.ansi=false` and `-Dterminal.jline=false` among JVM options before the main class.
9. For MCP or the Node.js SDK, install **Node.js 22+** and obtain Offcraft's complete `mcp/` directory from a pinned revision. Keep its modules together, including `recording.mjs` and `sdk.mjs`. No npm dependencies are required. Preserve the [MIT notice](LICENSE) when redistributing.

A launch configuration is machine/instance-specific, not a portable classpath recipe. For an executable **Fabric development** configuration, use [docs/mcp.md](docs/mcp.md). From the Offcraft repository root, first run `.\fabric\gradlew.bat -p .\fabric runClient`; the explicit project directory creates the Fabric launch files. After removal of `fabric/build/`, regenerate them. Never use Fabric Knot arguments for Forge/NeoForge.

## 3. Add configuration to the existing project

Keep these fields in your application's private local configuration:

| Field | Meaning |
| --- | --- |
| `java` | Absolute path to the Java 21 executable |
| `args` | Complete launch argument array, one string per JVM/main/game argument; not a shell command |
| `gameDir` | Absolute writable instance directory, used as subprocess cwd; align it with the launch's game-directory argument |
| `world` | Existing local world folder name for the example: Latin letters, digits, `_`, `-` |
| `offcraftHome` | Absolute Offcraft checkout/installation directory containing `mcp/` |
| `captureDir` | Application-selected writable directory for PNGs |

Save `java`, `args`, and `gameDir` in `launch.offcraft.json` in your application. Populate them from the **real launch configuration**, not a guessed main class. Keep account tokens/profile exports out of source control, logs, issue reports, and generated MCP configs. Keep `world` and `captureDir` as your application's settings.

## 4. Direct JSONL integration

### Process and response contract

- Spawn Java with shell execution disabled and **all three streams piped**. Continuously drain stdout and stderr. On Windows use the process API's console-hiding option if desired; the mod hides the game window, not necessarily the launch terminal.
- Use UTF-8 newline-delimited JSON. Each request has a fresh nonempty string `id` (at most 128 characters), `command`, and command-specific fields.
- Minecraft logs share stdout with responses. Parse each complete line; process only objects with `protocol:1` and a recognized `type`. Treat non-protocol lines as logs, not errors in the JSONL transport. Do not append log prefixes to protocol input.
- Wait for `event:ready` before sending commands. It means the transport is available, **not** that the world is ready. Inspect `status.screen`; startup/onboarding screens may require GUI interaction.
- Correlate `accepted`, `completed`, and `error` by ID. `accepted` never resolves an operation. Only terminal `completed` or `error` does; `error` has top-level `code` and `message`. Some invalid requests have `id:null` and cannot resolve an ordinary pending request.
- Serialize mutating operations in your application's scheduler. While one long action runs, `status`, `help`, `observe`, `inventory`, `item`, `messages`, `worlds`, `cancel`, and `quit` remain available. Other commands get `busy`; the client has no gameplay job queue. Its stdin queue is bounded to 32 lines: do not flood it.
- Do not retry uncertain mutating commands automatically. The protocol has no replay/idempotency history; an active duplicate ID causes a `duplicate_active_id` event, not another terminal reply.
- Reject all unresolved operations on process/stream failure. Drain output through subprocess `close`, not only `exit`, so the final `quit` reply is not lost.

### Node.js example using the existing bridge

Reuse `GameBridge` rather than adding a second JSONL parser. Its `submit()` returns `{id, command, promise}`; it generates IDs, waits for `ready`, filters logs, and resolves only terminal responses. **It does not validate arguments or serialize gameplay**: the wrapper below uses the existing validator, and the caller awaits mutating commands in order.

For a higher-level validated client, use `launchClient` from `mcp/sdk.mjs`; its API, AbortSignal semantics and optional bounded session recording are documented in [MCP and SDK](docs/mcp.md#javascript-sdk). The low-level bridge example below remains useful when your application already owns the child process.

Save this as `offcraft-session.mjs` in your application. Set `OFFCRAFT_HOME`, `OFFCRAFT_WORLD`, and `OFFCRAFT_CAPTURE_DIR`; prepare `launch.offcraft.json` as above. The example loads an **existing** world, walks, captures, and exits. It never creates or overwrites a world directory.

```js
import { spawn } from 'node:child_process';
import { readFile, mkdir } from 'node:fs/promises';
import { resolve, join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { randomUUID } from 'node:crypto';

const home = process.env.OFFCRAFT_HOME;
const world = process.env.OFFCRAFT_WORLD;
const captureDir = process.env.OFFCRAFT_CAPTURE_DIR;
if (!home || !world || !captureDir) {
  throw new Error('Set OFFCRAFT_HOME, OFFCRAFT_WORLD, OFFCRAFT_CAPTURE_DIR');
}
const config = JSON.parse(await readFile('launch.offcraft.json', 'utf8'));
if (typeof config.java !== 'string' || !config.java ||
    typeof config.gameDir !== 'string' || !config.gameDir ||
    !Array.isArray(config.args) || config.args.some(a => typeof a !== 'string')) {
  throw new Error('Invalid launch.offcraft.json');
}
const { GameBridge } = await import(pathToFileURL(resolve(home, 'mcp/adapter.mjs')));
const { validateCommandArguments } = await import(
  pathToFileURL(resolve(home, 'mcp/schemas.mjs'))
);
const outputDir = resolve(captureDir);
await mkdir(outputDir, { recursive: true });
const child = spawn(config.java, config.args, {
  cwd: config.gameDir, shell: false, windowsHide: true,
  stdio: ['pipe', 'pipe', 'pipe'],
});
const bridge = new GameBridge(child);
async function call(args) {
  const errors = validateCommandArguments(args);
  if (errors.length) throw new Error(JSON.stringify(errors));
  const response = await bridge.submit(args).promise;
  if (response.type === 'error') {
    throw Object.assign(new Error(response.message), { response });
  }
  return response.data;
}
try {
  let status = await call({ command: 'status' });
  if (status.screen === 'AccessibilityOnboardingScreen') {
    await call({ command: 'key', key: 'ESCAPE' });
    status = await call({ command: 'status' });
  }
  if (status.state !== 'menu') throw new Error('Expected an unloaded instance');
  await call({ command: 'load', world });
  const result = await call({
    command: 'act', ticks: 20, buttons: ['forward'],
    capture: join(outputDir, `${randomUUID()}.png`),
  });
  console.log(JSON.stringify(result));
  await call({ command: 'quit' });
} finally {
  bridge.endInput(); // EOF also saves and releases controlled input.
  const { code, signal } = await bridge.closed;
  if (code !== 0) throw new Error(`Minecraft exited: code=${code}, signal=${signal}`);
}
```

Run from your application's directory: `node offcraft-session.mjs`. Integrate `call()` into your existing service/scheduler rather than spawning a new client per game action. The sample waits for graceful exit without a deadline; a production host should apply its own startup/operation/shutdown deadlines and supervise hangs. A timeout does not prove the command failed or cancel it. For an active action, retain the handle from `bridge.submit()` and send `cancel` with that handle's game ID; loading/connection cannot be cancelled this way. Use `quit` or EOF to stop the session. Forced termination is a last resort and may interrupt saving.

For other languages, implement the same line framing, pending-ID map, readiness gate, terminal-response handling, and process supervision. The [JSONL reference](docs/protocol.md) is the complete command contract, not this example.

## 5. MCP integration

The adapter is a **stdio MCP server**, protocol **2024-11-05**. It launches a new game client; it does not attach to an already-running instance. Do not also start Java yourself for the same instance.

Many hosts use a `mcpServers` object with `command` and `args`. Generate its entry from your existing private launch file, so the nested argument array is encoded correctly. This PowerShell example runs from the application directory and prints a config fragment; merge it into the host's existing configuration instead of overwriting other servers:

```powershell
$launch = Get-Content -Raw -Encoding UTF8 .\launch.offcraft.json | ConvertFrom-Json
$offcraft = (Resolve-Path $env:OFFCRAFT_HOME).Path
$node = (Get-Command node).Source
$entry = @{
  command = $node
  args = @(
    (Join-Path $offcraft 'mcp/index.mjs'),
    '--client', $launch.java,
    '--args-json', (ConvertTo-Json -Compress -InputObject @($launch.args)),
    '--cwd', $launch.gameDir
  )
}
@{ mcpServers = @{ offcraft = $entry } } | ConvertTo-Json -Depth 8
```

`--cwd` sets the **game process** directory; the absolute adapter path avoids dependence on the host's cwd. Host config formats differ: use the command/argument values in the host's documented stdio-server configuration. This fragment is not a remote URL, an API key, or an `npm install` command. It can contain sensitive launch arguments; store it privately.

### Handshake and calls

Send each JSON-RPC message as a single line. Wait for the response to each request below before proceeding; send `notifications/initialized` only after the initialize response:

```json
{"jsonrpc":"2.0","id":"init-1","method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"existing-project","version":"1.0.0"}}}
{"jsonrpc":"2.0","method":"notifications/initialized"}
{"jsonrpc":"2.0","id":"list-1","method":"tools/list","params":{}}
{"jsonrpc":"2.0","id":"status-1","method":"tools/call","params":{"name":"offcraft_command","arguments":{"command":"status"}}}
```

- `tools/list` returns `offcraft_command` (JSONL command schemas) and `offcraft_capture` (`{path?}` for an explicit fresh image). Initialization/discovery does not wait for game readiness; execution does.
- The adapter generates game IDs. Do **not** pass a game `id` in tool arguments. Use unique JSON-RPC IDs throughout the MCP session.
- For successful JSON-RPC `tools/call`, inspect `result.isError`, then parse `result.content[0].text` as JSON. Game errors are actual terminal envelopes. Transport failures have `source:adapter`, `type:error`, `code`, and `message`, not invented `completed` data. JSON-RPC validation/method errors instead appear in the response's top-level `error`.
- MCP stdout contains only JSON-RPC; drain stderr for Minecraft logs/events. `offcraft_command` leaves PNGs as local files. `offcraft_capture` additionally returns a bounded PNG image block after successful render completion; read failures are explicit adapter errors retaining `game_response`. See [image limits](docs/mcp.md#explicit-images).
- To abandon a call, send `notifications/cancelled` with the MCP request ID. The cancelled call receives **no response**. The adapter attempts game `cancel` for the exact game ID; only active `act` is cancellable. Cancelling a load/connect call does not stop world loading/connection.

```json
{"jsonrpc":"2.0","method":"notifications/cancelled","params":{"requestId":"walk-1","reason":"User stopped the action"}}
```

This notification applies only if a call with ID `walk-1` exists. To stop the entire client, call `offcraft_command` with `{"command":"quit"}` and then close the transport. MCP EOF/SIGINT/SIGTERM ends game stdin for orderly shutdown; it does not force-kill Java. A direct tool call `{"command":"cancel"}` returns the actual game's cancellation response instead of abandoning its own MCP reply.

## 6. Application workflow and safety

1. Start one session for an isolated game directory; keep stdin open.
2. Wait for readiness/status and handle startup GUI. Do not automatically close arbitrary screens or accept server resource packs.
3. Load an existing world, explicitly create a new uniquely named world, or connect from the menu. Wait for terminal success and playable world state.
4. Queue your own mutating jobs sequentially. Use observations/status for feedback while an action is running; do not infer server success from local prediction.
5. Obtain current `sync_id` and slot IDs from `inventory` before a container action. GUI clicks use scaled GUI coordinates, not raw PNG pixels. `act.slot` is 1–9; observed selected slot is 0–8.
6. Use unique absolute PNG paths under an application-controlled directory. Completion with `capture` occurs after writing the file. Existing paths can be overwritten; constrain user-supplied paths. Read the returned `data.capture.path` only after terminal success.
7. On cancellation, verify release evidence; an action may have run partially. One tick-based action is not a millisecond deadline or a promise to break/place a block.
8. On application shutdown, stop new jobs, request `quit` or end stdin, drain pipes, and wait for process close. Preserve unresolved/failure state in your own job system; do not report success merely because the process exited.

Offcraft has no network authentication layer because it has no built-in network endpoint. The controller has the Java process's filesystem and game privileges. Restrict who can submit commands, cap your application queue, protect profile credentials, and use separate test worlds. Follow server rules for automation. Development profiles are not authenticated Microsoft accounts.

## 7. Acceptance check in the target project

- Matching loader/mod starts; receive `ready`, then `status` reports `visible:false` and the actual screen. A hidden GLFW status alone does not prove no early loader/console window appeared.
- Load a disposable copied/test world; wait for `completed` with world readiness, not just `accepted`.
- Exercise `observe`, `inventory`, one small `act`, and a unique PNG capture. Inspect the actual PNG and returned dimensions; check input release and terminal ID correlation.
- While a long `act` runs, obtain status and cancel the exact action. Confirm no second terminal response and no held/queued controlled input.
- Exercise `quit` and, in a separate session, EOF shutdown; observe process close and saved-world reload.
- For MCP, verify initialize → initialized → tools/list → tools/call, clean stdout, stderr drainage, `isError` handling, and cancelled-call behavior.

Record the actual Java/loader versions, launch mode, commands, file paths, results and limitations. Existing [verification records](docs/verification.md) are not a PASS for your installation. Production-launcher installation, multiplayer, other operating systems, services, arbitrary renderer combinations, and your application's integration are not established merely by a build or adapter tests.

## 8. Troubleshooting

| Symptom | Check |
| --- | --- |
| Hidden client exits immediately | stdin must remain open; read stderr and process exit; verify the real Java target |
| No `ready` | Wrong JAR/loader, classpath, assets/natives, startup overlay, Java version, graphical session/OpenGL |
| `ready`, but action cannot run | Inspect status: onboarding/menu/loading/pause is not a playable world |
| `busy` | Wait for the preceding mutating command's terminal response; do not retry blindly |
| Forge shows early window | Set `earlyWindowControl=false` in the actual instance before launch |
| Forge logs contaminate protocol framing | Apply both terminal JVM flags; still filter non-protocol log lines |
| MCP connects but tools fail | Finish handshake; inspect JSON-RPC error or tool `isError`; check child stderr and launch args |
| Capture is not found | Use returned path and process cwd; offcraft_capture returns an MCP image block, offcraft_command/render returns file metadata |
| Launch argument file missing after cleanup | Regenerate development launch files with the appropriate loader's `runClient` |

Detailed contracts: [build/launch](docs/development.md), [JSONL](docs/protocol.md), [MCP](docs/mcp.md), [architecture](docs/architecture.md).
