# MCP adapter

[Русский](ru/mcp.md)

[Documentation](README.md) · [Project overview](../README.md)

Node.js **22+**, with no npm dependencies. MCP `2024-11-05`, stdio: one JSON-RPC object per line. Entry point: `mcp/index.mjs`; `--client` is the actual executable, `--args-json` is a JSON array of arguments, and `--cwd` is the game directory. No shell is used; a `.bat` file or graphical launcher is not a substitute for a Java process with stdin.

For an installed client using any loader, use the Java executable and the actual JVM/main/game arguments from its launch configuration; for Forge/NeoForge, disable the early window in the game directory beforehand. The following development example is **Fabric only**: first run `runClient` at least once to create Loom's launch.cfg and argFile, then run this from the project root. Forge/NeoForge have their own launch targets and JVM/module arguments; do not use Fabric Knot for them:

```powershell
$fabric = (Resolve-Path .\fabric).Path.Replace('\', '/')
$gameArgs = @(
  "-Dfabric.dli.config=$fabric/.gradle/loom-cache/launch.cfg",
  '-Dfabric.dli.env=client',
  '-Dfabric.dli.main=net.fabricmc.loader.impl.launch.knot.KnotClient',
  "@$fabric/build/loom-cache/argFiles/runClient",
  '-Xmx2G', '-Dfile.encoding=UTF-8',
  'net.fabricmc.devlaunchinjector.Main', '--width', '640', '--height', '360'
)
node .\mcp\index.mjs --client java --args-json (ConvertTo-Json -Compress -InputObject $gameArgs) --cwd "$fabric/run"
```

The MCP client launches this command with pipes, sends `initialize`, then `notifications/initialized`. `tools/list` exposes **`offcraft_command`** for `{command,...command_fields}` and **`offcraft_capture`** for `{path?}`. The adapter generates game IDs and waits for `completed/error`, never `accepted`. Command results remain JSON text in `content[0].text`; capture additionally returns `content[1]` with `type:"image",mimeType:"image/png",data:<base64>`. `isError:true` marks errors. Transport failures have `source:adapter`, not invented game success.

stdout contains only JSON-RPC; Minecraft logs/events go to stderr. Read-only commands are not blocked behind a long-running action. `notifications/cancelled` targets its own game request ID; a cancelled MCP call receives no response. EOF/SIGINT/SIGTERM closes the game's stdin for normal saving/shutdown, without forcibly killing the process. The adapter does not authenticate an account or bypass server restrictions.

Read errors are handled on the readline interfaces: game stdout failure rejects pending/subsequent tools with `source:adapter`, `code:client_stdout_failed`; MCP input failure logs once, suppresses further replies and closes game stdin for orderly shutdown.

Contract tests: `node --test mcp/*.test.mjs`.

## Explicit images

`offcraft_capture` submits one ordinary `render`, waits for its fresh-frame/file completion, then reads only the returned capture path. There is no video stream or capture after every action. Files must be regular PNGs with a valid signature and at most 16 MiB; reads are bounded even if the file changes. A game error returns no image. A read error returns `source:adapter,code:capture_read_failed` and preserves the actual game envelope as `game_response`; a successful file write is not misreported as a failed game command. Paths are local and can overwrite PNGs, just like `render`; constrain caller-controlled paths. Base64 and PNG writing have a cost; capture remains explicitly requested.

## JavaScript SDK

Import `launchClient` from `mcp/sdk.mjs`; no dependencies are needed. Options: `{client,args?,cwd?,record?,recordMaxBytes?,log?,observers?}`. `client` and `args` are the actual Java launch, not a launcher shell command. The SDK reuses `GameBridge`, validates shared schemas, and does not queue/plan gameplay. Await mutating calls in order.

```js
import { launchClient } from './mcp/sdk.mjs';

// Supply the real executable, full argument array, and isolated game directory.
const client = launchClient({ client: java, args: gameArgs, cwd: gameDir });
try {
  await client.ready();
  const status = await client.command({ command: 'status' });
  if (status.data.screen === 'AccessibilityOnboardingScreen') {
    await client.command({ command: 'key', key: 'ESCAPE' });
  }
  const loaded = await client.command({ command: 'load', world: 'my-world' });
  if (loaded.type === 'error') throw new Error(loaded.message);
  const handle = client.submit({ command: 'act', ticks: 200, buttons: ['forward', 'sprint'] });
  await client.cancel(handle.id);
  const partial = await handle.promise; // Actual error/cancelled envelope with release evidence.
  await client.quit();
} finally {
  await client.close();
}
```

`ready()` resolves the actual ready event or rejects startup/transport failure. `command(args,{signal}?)` returns the terminal completed/error **envelope**; game errors are not thrown, while validation (`CommandValidationError.errors`), transport and abort failures reject. `submit` returns `{id,command,promise,cancel()}`. AbortSignal or handle cancellation detaches locally with `AbortError`; before readiness nothing is sent, after dispatch an exact-target `cancel` is attempted. Only an active `act` can be cancelled in-game; abandoning other operations does not undo them. `cancel(target?)` returns the real game cancellation response. `quit()` waits for its terminal response, child pipe closure and recorder flush; repeated calls share one request. `close()` ends stdin and waits without killing. `closed` resolves `{code,signal}`. Hosts supply their own deadlines; a timeout is not proof of game cancellation. Optional observers receive `(direction,message)` snapshots and cannot change correlation.

## Optional session recording

CLI flags: `--record PATH --record-max-bytes N`; SDK options: `record`, `recordMaxBytes`. Default is **off**, with no file opened. When enabled, the default budget is 10 MiB, at most 256 queued/in-progress records. Parent directories must exist; exclusive creation refuses an existing file before Java starts. Paths are relative to the controller's cwd, not `--cwd`.

Each JSONL entry is `{time:<ISO timestamp>,direction:"request"|"response"|"event",message:<game protocol object>}`. Requests, accepted/terminal responses and events are recorded; raw Minecraft logs and base64 images are not. PNGs remain separate explicit captures. The journal may contain chat/book text and submitted commands: store it privately.

Budget/queue exhaustion logs `byte_limit`/`queue_limit` and stops recording only; the game continues. I/O failure is logged and makes `closed`/`close`/`quit` reject with `recording_failed`. SDK `recording` is null or `{path,maxBytes,bytes,stopped,error?}`. Shutdown flushes queued records. No rotation, replay or background game scans are added.


## Contract changes

The schemas in `mcp/schemas.mjs` are used both for `tools/list` and argument validation. When changing a game command, update its implementation in common, the MCP schema, and the [JSONL reference](protocol.md); affected adapter contract tests must reflect actual behavior. `GameBridge` links independent MCP and game IDs; `accepted` never completes a tool call.

Check not only the adapter with a test transport, but also a real Java client with pipes: [verification.md](verification.md).
