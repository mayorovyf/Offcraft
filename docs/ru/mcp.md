# MCP-адаптер

[English](../mcp.md)

[Документация](index.md) · [Обзор проекта](README.md)

Node.js **22+**, без npm-зависимостей. MCP `2024-11-05`, stdio: один JSON-RPC объект на строку. Entry point `mcp/index.mjs`; `--client` — настоящий executable, `--args-json` — JSON-массив аргументов, `--cwd` — игровой каталог. Shell не используется; `.bat` и графический лаунчер не заменяют Java-процесс со stdin.

Для установленного клиента любого загрузчика использовать Java executable и реальные JVM/main/game-аргументы его запуска; для Forge/NeoForge заранее отключить раннее окно в game-directory. Следующий development-пример — **только Fabric**: сначала выполнить `runClient` хотя бы один раз для создания Loom launch.cfg и argFile; затем из корня проекта. У Forge/NeoForge свои launch target и JVM/module-аргументы, Fabric Knot не использовать:

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

MCP-клиент запускает эту команду с pipes, выполняет `initialize`, затем `notifications/initialized`. Единственный tool **`offcraft_command`** принимает `{command, ...поля_команды}`; игровой `id` генерирует адаптер. `tools/list` возвращает схемы 20 команд. После фактического `ready` адаптер передаёт запросы и ждёт `completed/error`, не `accepted`. Результат tool — игровой конверт JSON в `content[0].text`; `isError:true` отмечает ошибку. Транспортные ошибки имеют `source:adapter`, не выдаются за игровой успех.

stdout содержит только JSON-RPC, Minecraft-логи/события идут в stderr. Read-only команды не блокируются за длительным действием. `notifications/cancelled` адресует собственный игровой ID; отменённый MCP-вызов не получает ответ. EOF/SIGINT/SIGTERM закрывает игровой stdin для штатного сохранения/остановки, без принудительного kill. Адаптер не авторизует аккаунт и не обходит ограничения сервера.

Ошибки чтения обрабатываются на readline-интерфейсах: ошибка игрового stdout завершает ожидающие/последующие tool-вызовы с `source:adapter`, `code:client_stdout_failed`; ошибка MCP stdin логируется один раз, прекращает ответы и закрывает игровой stdin для штатной остановки.

Контрактные тесты: `node --test mcp/adapter.test.mjs`.

## Изменения контракта

Схемы `mcp/schemas.mjs` одновременно используются для `tools/list` и валидации аргументов. При изменении игровой команды обновляйте её реализацию в common, схему MCP и [справочник JSONL](protocol.md); затронутые контрактные тесты адаптера должны соответствовать реальному поведению. `GameBridge` связывает независимые MCP и игровые ID; `accepted` никогда не завершает tool-вызов.

Проверяйте не только адаптер с тестовым транспортом, но и настоящий Java-клиент с pipes: [verification.md](verification.md).
