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

MCP-клиент запускает команду с pipes, выполняет `initialize`, затем `notifications/initialized`. `tools/list` содержит **`offcraft_command`** для `{command,...поля_команды}` и **`offcraft_capture`** для `{path?}`. Адаптер генерирует игровые ID и ждёт `completed/error`, не `accepted`. Результат команды — JSON в `content[0].text`; захват дополнительно возвращает `content[1]` с `type:"image",mimeType:"image/png",data:<base64>`. Ошибки отмечены `isError:true`. Транспортные ошибки имеют `source:adapter`, не выдаются за игровой успех.

stdout содержит только JSON-RPC, Minecraft-логи/события идут в stderr. Read-only команды не блокируются за длительным действием. `notifications/cancelled` адресует собственный игровой ID; отменённый MCP-вызов не получает ответ. EOF/SIGINT/SIGTERM закрывает игровой stdin для штатного сохранения/остановки, без принудительного kill. Адаптер не авторизует аккаунт и не обходит ограничения сервера.

Ошибки чтения обрабатываются на readline-интерфейсах: ошибка игрового stdout завершает ожидающие/последующие tool-вызовы с `source:adapter`, `code:client_stdout_failed`; ошибка MCP stdin логируется один раз, прекращает ответы и закрывает игровой stdin для штатной остановки.

Контрактные тесты: `node --test mcp/*.test.mjs`.

## Изображение по запросу

`offcraft_capture` выполняет обычный `render`, ждёт свежего кадра и записи, затем читает только возвращённый capture path. Видеопотока и автоматического захвата после каждого действия нет. Файл должен быть обычным PNG с правильной сигнатурой, не больше 16 MiB; чтение ограничено даже при изменении файла. Игровая ошибка не возвращает изображение. Ошибка чтения имеет `source:adapter,code:capture_read_failed` и сохраняет настоящий игровой ответ в `game_response`; успешная запись не объявляется неуспехом игровой команды. Пути локальные, PNG может перезаписываться как у `render`; ограничивайте пользовательские пути. Base64 и запись PNG имеют стоимость; захват остаётся явным.

## JavaScript SDK

Импортируйте `launchClient` из `mcp/sdk.mjs`; зависимостей нет. Опции: `{client,args?,cwd?,record?,recordMaxBytes?,log?,observers?}`. `client`/`args` — настоящий запуск Java, не shell-команда лаунчера. SDK использует существующий `GameBridge`, валидирует общие схемы, не планирует игру и не добавляет очередь действий. Изменяющие вызовы выполняйте последовательно.

```js
import { launchClient } from './mcp/sdk.mjs';

// Настоящий executable, полный массив аргументов и отдельный игровой каталог.
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
  const partial = await handle.promise; // Настоящий error/cancelled с доказательством отпускания.
  await client.quit();
} finally {
  await client.close();
}
```

`ready()` возвращает настоящий ready event или отвергается при сбое запуска/транспорта. `command(args,{signal}?)` возвращает terminal completed/error **конверт**; игровая ошибка не бросается, а validation (`CommandValidationError.errors`), transport и abort отвергают promise. `submit` возвращает `{id,command,promise,cancel()}`. AbortSignal/cancel handle отсоединяет локальный запрос с `AbortError`: до ready ничего не отправляется, после отправки выполняется попытка exact-target `cancel`. В игре отменяется только активный `act`; отказ от других операций их не откатывает. `cancel(target?)` возвращает настоящий ответ игры. `quit()` ждёт terminal response, закрытия pipes и flush журнала; повторные вызовы используют один запрос. `close()` закрывает stdin и ждёт без kill. `closed` возвращает `{code,signal}`. Deadlines задаёт хост; timeout не доказывает отмену. Необязательные observers получают снимки `(direction,message)`, не влияя на correlation.

## Необязательная запись сессии

CLI: `--record PATH --record-max-bytes N`; SDK: `record`, `recordMaxBytes`. По умолчанию **выключено**, файл не открывается. При включении лимит по умолчанию 10 MiB, до 256 ожидающих/записываемых записей. Родительский каталог должен существовать; exclusive create отвергает существующий файл до запуска Java. Путь относительно cwd контроллера, не `--cwd`.

Запись JSONL: `{time:<ISO timestamp>,direction:"request"|"response"|"event",message:<игровой протокол>}`. Сохраняются запросы, accepted/terminal responses и события, но не сырые Minecraft-логи и base64 изображений. PNG остаются отдельными явными захватами. Журнал может содержать чат, книги и команды: храните его приватно.

При исчерпании байтов/очереди логируется `byte_limit`/`queue_limit`, останавливается только запись; игра продолжается. Ошибка I/O логируется и отвергает `closed`/`close`/`quit` с `recording_failed`. SDK `recording`: null либо `{path,maxBytes,bytes,stopped,error?}`. При завершении очередь записывается. Ротации, replay и фонового сканирования игры нет.


## Изменения контракта

Схемы `mcp/schemas.mjs` одновременно используются для `tools/list` и валидации аргументов. При изменении игровой команды обновляйте её реализацию в common, схему MCP и [справочник JSONL](protocol.md); затронутые контрактные тесты адаптера должны соответствовать реальному поведению. `GameBridge` связывает независимые MCP и игровые ID; `accepted` никогда не завершает tool-вызов.

Проверяйте не только адаптер с тестовым транспортом, но и настоящий Java-клиент с pipes: [verification.md](verification.md).
