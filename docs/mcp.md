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

The MCP client launches this command with pipes, sends `initialize`, then `notifications/initialized`. The only tool, **`offcraft_command`**, accepts `{command, ...command_fields}`; the adapter generates the game request `id`. `tools/list` returns schemas for 20 commands. After an actual `ready`, the adapter forwards requests and waits for `completed/error`, not `accepted`. The tool result is the game's JSON envelope in `content[0].text`; `isError:true` marks an error. Transport errors have `source:adapter` and are not presented as game successes.

stdout contains only JSON-RPC; Minecraft logs/events go to stderr. Read-only commands are not blocked behind a long-running action. `notifications/cancelled` targets its own game request ID; a cancelled MCP call receives no response. EOF/SIGINT/SIGTERM closes the game's stdin for normal saving/shutdown, without forcibly killing the process. The adapter does not authenticate an account or bypass server restrictions.

Read errors are handled on the readline interfaces: game stdout failure rejects pending/subsequent tools with `source:adapter`, `code:client_stdout_failed`; MCP input failure logs once, suppresses further replies and closes game stdin for orderly shutdown.

Contract tests: `node --test mcp/adapter.test.mjs`.

## Contract changes

The schemas in `mcp/schemas.mjs` are used both for `tools/list` and argument validation. When changing a game command, update its implementation in common, the MCP schema, and the [JSONL reference](protocol.md); affected adapter contract tests must reflect actual behavior. `GameBridge` links independent MCP and game IDs; `accepted` never completes a tool call.

Check not only the adapter with a test transport, but also a real Java client with pipes: [verification.md](verification.md).
