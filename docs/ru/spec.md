# Внедрение Offcraft в существующий проект

[English](../../spec.md)

[Обзор проекта](README.md) · [Документация](index.md)

## 1. Граница интеграции

Offcraft управляет **настоящим клиентом Minecraft Java Edition 1.21.1**. Установите мод под нужный загрузчик в экземпляр игры, а существующее приложение подключите к процессу клиента. Не встраивайте классы Minecraft в приложение, не запускайте мод через `java -jar` и не подменяйте его диагностическим standalone-стендом.

Выберите один способ:

| Существующий проект | Подключение | Кто запускает процесс |
| --- | --- | --- |
| Приложение, automation runner, игровой агент, собственный backend | JSONL через stdin/stdout Java; любой язык с subprocess pipes | Ваше приложение |
| Агент или desktop-приложение с MCP | MCP stdio через `mcp/index.mjs` | MCP-хост запускает адаптер, адаптер запускает Java |

Встроенного HTTP, WebSocket, TCP listener, подключения к уже запущенной игре или общей многопользовательской сессии нет. Оба варианта используют локальные pipes. Для удалённого развёртывания запускайте владельца процесса на машине Minecraft и организуйте собственный авторизованный доступ; не открывайте произвольные игровые команды и файловые пути публично.

## 2. Требования и установка

1. **Windows x64**, **Java 21**, графическая desktop-сессия и OpenGL-драйвер. Окно скрыто, но Minecraft не полностью headless; Windows-служба без графической сессии не является подтверждённым вариантом развёртывания.
2. Подготовьте отдельный экземпляр Minecraft **1.21.1**. Сделайте резервные копии миров и конфигурации; не запускайте несколько клиентов в одном каталоге экземпляра одновременно.
3. Установите подходящий загрузчик. Закреплённые development-конфигурации: Fabric Loader **0.16.14**, Forge **52.1.0**, NeoForge **21.1.259**.
4. Положите ровно один подходящий JAR в `<game-directory>/mods/`:
   - `offcraft-1.0.0-mc1.21.1-fabric.jar`
   - `offcraft-1.0.0-mc1.21.1-forge.jar`
   - `offcraft-1.0.0-mc1.21.1-neoforge.jar`
5. Не устанавливайте source JAR и `offcraft-1.0.0-standalone-windows-x64.jar`. Отдельный common-мод, Fabric API и Architectury API не нужны.
6. Получите настоящий **Java executable и полный массив JVM/main/game-аргументов** из launch-конфигурации экземпляра. Включите classpath/module path, libraries, assets, natives, main class, game directory и параметры профиля. Offcraft не собирает их и не авторизует аккаунт. Обычный лаунчер может не предоставлять приложению stdin; контроллер должен запускать настоящий Java-процесс, не лаунчер или `.bat`-обёртку.
7. Для **Forge и NeoForge** задайте `earlyWindowControl=false` в `<game-directory>/config/fml.toml` **до** запуска Java, сохранив остальные настройки. Это не свойство `-D`.
8. Для **Forge** добавьте `-Dterminal.ansi=false` и `-Dterminal.jline=false` к JVM-опциям перед main class.
9. Для MCP и Node.js-примера ниже установите **Node.js 22+** и получите каталог `mcp/` Offcraft из закреплённой ревизии репозитория. Сохраняйте `adapter.mjs`, `schemas.mjs` и `index.mjs` вместе. npm-зависимостей нет. При распространении сохраняйте [MIT notice](../../LICENSE).

Launch-конфигурация зависит от машины и экземпляра, это не универсальный рецепт classpath. Исполняемый **Fabric development**-пример находится в [mcp.md](mcp.md). Из корня репозитория Offcraft сначала выполните `.\fabric\gradlew.bat -p .\fabric runClient`: явно заданный каталог проекта обеспечивает создание Fabric launch-файлов. После удаления `fabric/build/` восстановите их. Не используйте Fabric Knot-аргументы для Forge/NeoForge.

## 3. Конфигурация существующего проекта

Храните следующие поля в приватной локальной конфигурации приложения:

| Поле | Значение |
| --- | --- |
| `java` | Абсолютный путь к Java 21 executable |
| `args` | Полный массив аргументов запуска: отдельная строка для каждого JVM/main/game-аргумента, не shell-команда |
| `gameDir` | Абсолютный writable-каталог экземпляра, subprocess cwd; согласуйте с game-directory аргументом запуска |
| `world` | Имя папки существующего мира для примера: латинские буквы, цифры, `_`, `-` |
| `offcraftHome` | Абсолютный путь к checkout/установке Offcraft с `mcp/` |
| `captureDir` | Выбранный приложением writable-каталог PNG |

Сохраните `java`, `args`, `gameDir` в `launch.offcraft.json` вашего приложения. Возьмите значения из **реальной launch-конфигурации**, не угадывайте main class. Не помещайте account tokens/profile exports в Git, логи, issues или сгенерированные MCP-конфиги общего доступа. `world` и `captureDir` остаются настройками вашего приложения.

## 4. Прямое подключение JSONL

### Процесс и ответы

- Запускайте Java без shell, с **тремя потоками через pipes**. Постоянно вычитывайте stdout и stderr. При необходимости используйте Windows-опцию скрытия консоли в process API; мод скрывает игровое окно, не обязательно терминал запуска.
- Передавайте UTF-8 JSON по одному объекту в строке. Запрос содержит новый непустой строковый `id` до 128 символов, `command` и поля команды.
- В stdout также идут логи Minecraft. Разбирайте полные строки; как протокол обрабатывайте только объекты с `protocol:1` и известным `type`. Остальные строки — логи, не ошибка JSONL-транспорта. Не добавляйте логовые префиксы к протокольному вводу.
- Дождитесь `event:ready`. Это доступность транспорта, **не** готовность мира. Проверьте `status.screen`; стартовые/onboarding-экраны могут требовать GUI-команд.
- Сопоставляйте `accepted`, `completed`, `error` по ID. `accepted` не завершает операцию. Только терминальный `completed` или `error`; у `error` поля `code` и `message` находятся наверху. Некорректный запрос может получить `id:null` и не разрешить обычный pending-запрос.
- Последовательно исполняйте изменяющие операции в scheduler приложения. Пока идёт длительное действие, доступны `status`, `help`, `observe`, `inventory`, `cancel`, `quit`. Остальные команды получают `busy`; игровой очереди задач нет. Очередь stdin ограничена 32 строками: не засыпайте клиент командами.
- Не повторяйте автоматически изменяющую команду с неизвестным исходом. Replay/idempotency history нет; дублирование активного ID даёт событие `duplicate_active_id`, не второй терминальный ответ.
- При сбое процесса/потока завершите pending-операции ошибкой. Вычитывайте вывод до subprocess `close`, не только `exit`, чтобы не потерять финальный ответ `quit`.

### Node.js: повторное использование существующего bridge

Используйте `GameBridge`, не создавайте второй JSONL-parser. `submit()` возвращает `{id, command, promise}`; bridge генерирует ID, ждёт `ready`, фильтрует логи и разрешает promise только терминальным ответом. **Аргументы он не валидирует и игровой scheduler не заменяет**: обёртка ниже использует существующий validator, а вызывающий код ждёт изменяющие команды последовательно.

Сохраните код как `offcraft-session.mjs` в вашем приложении. Задайте `OFFCRAFT_HOME`, `OFFCRAFT_WORLD`, `OFFCRAFT_CAPTURE_DIR`; подготовьте `launch.offcraft.json` по разделу выше. Пример загружает **существующий** мир, двигается, снимает PNG и выходит. Он не создаёт и не перезаписывает папку мира.

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

Запускайте из каталога приложения: `node offcraft-session.mjs`. Встройте `call()` в существующий service/scheduler вместо запуска клиента на каждое действие. Пример ждёт штатного выхода без deadline; production-хосту нужны собственные startup/operation/shutdown deadlines и наблюдение зависаний. Timeout не доказывает неуспех и не отменяет команду. Для активного действия сохраните handle `bridge.submit()` и отправьте `cancel` с game ID этого handle; загрузка/подключение так не отменяются. `quit` или EOF останавливают сессию. Force-kill — крайняя мера, может прервать сохранение.

В другом языке реализуйте те же границы строк, pending-ID map, readiness gate, терминальные ответы и supervision процессов. Полный контракт — [справочник JSONL](protocol.md), не этот пример.

## 5. Подключение MCP

Адаптер — **stdio MCP server**, протокол **2024-11-05**. Он запускает новый игровой клиент, не подключается к уже работающему. Не запускайте Java самостоятельно для того же экземпляра одновременно с MCP.

Многие хосты используют объект `mcpServers` с `command` и `args`. Сгенерируйте запись из приватного launch-файла, чтобы правильно закодировать вложенный массив аргументов. PowerShell-пример запускается из каталога приложения и печатает config fragment; объедините его с существующей конфигурацией хоста, не перезаписывайте другие серверы:

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

`--cwd` задаёт каталог **игрового процесса**; абсолютный путь адаптера исключает зависимость от cwd хоста. Форматы хостов отличаются: перенесите command/args в документированную конфигурацию stdio-server вашего хоста. Это не remote URL, API key и не `npm install`. Фрагмент может содержать чувствительные launch-аргументы; храните его приватно.

### Handshake и вызовы

Каждое JSON-RPC сообщение — одна строка. Ждите ответ каждого запроса ниже перед продолжением; `notifications/initialized` отправляйте только после initialize-ответа:

```json
{"jsonrpc":"2.0","id":"init-1","method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"existing-project","version":"1.0.0"}}}
{"jsonrpc":"2.0","method":"notifications/initialized"}
{"jsonrpc":"2.0","id":"list-1","method":"tools/list","params":{}}
{"jsonrpc":"2.0","id":"status-1","method":"tools/call","params":{"name":"offcraft_command","arguments":{"command":"status"}}}
```

- `tools/list` возвращает **один tool**, `offcraft_command`, со схемой 20 команд. Initialize/discovery не ждут игровой `ready`; исполнение tool ждёт.
- Game ID генерирует адаптер. **Не** передавайте `id` в tool arguments. JSON-RPC IDs должны быть уникальны всю MCP-сессию.
- При успешном JSON-RPC `tools/call` проверьте `result.isError`, затем разберите JSON `result.content[0].text`. Game errors — настоящие terminal envelopes. Transport failures содержат `source:adapter`, `type:error`, `code`, `message`, не выдуманный `completed`. Ошибки JSON-RPC метода/валидации находятся в верхнем `error` ответа.
- stdout MCP содержит только JSON-RPC. Постоянно вычитывайте stderr адаптера: там Minecraft logs/events. PNG остаётся локальным файлом; MCP не возвращает image content block и не загружает его автоматически.
- Для отказа от вызова отправьте `notifications/cancelled` с MCP request ID. Отменённый вызов **не получает ответа**. Адаптер пытается выполнить game `cancel` для точного game ID; отменяется только активный `act`. Отказ от load/connect-вызова не останавливает загрузку/подключение.

```json
{"jsonrpc":"2.0","method":"notifications/cancelled","params":{"requestId":"walk-1","reason":"User stopped the action"}}
```

Уведомление применимо только при существующем вызове с ID `walk-1`. Для остановки клиента вызовите `offcraft_command` с `{"command":"quit"}`, затем закройте transport. MCP EOF/SIGINT/SIGTERM завершает game stdin для штатного выхода, не force-kill Java. Прямой tool call `{"command":"cancel"}` возвращает настоящий ответ отмены игры, не отказывается от собственного MCP-ответа.

## 6. Рабочий цикл и безопасность

1. Запустите одну сессию в изолированном game directory; не закрывайте stdin.
2. Дождитесь readiness/status и обработайте стартовый GUI. Не закрывайте произвольные экраны автоматически и не принимайте server resource packs автоматически.
3. Загрузите существующий мир, явно создайте новый с уникальным именем или подключитесь из меню. Ждите terminal success и готового игрового состояния.
4. Последовательно исполняйте изменяющие задачи собственной очереди. Используйте observations/status во время действия; local prediction не означает server success.
5. До операций контейнера получите текущие `sync_id` и slot IDs из `inventory`. GUI-клики используют scaled GUI pixels, не PNG-пиксели. `act.slot` — 1–9; observed selected slot — 0–8.
6. Используйте уникальные абсолютные PNG-пути в каталоге приложения. Capture-completion приходит после записи файла. Совпадающие пути перезаписываются; ограничивайте пользовательские пути. Читайте возвращённый `data.capture.path` только после terminal success.
7. При отмене проверьте evidence освобождения ввода; действие могло выполниться частично. Тиковое действие не является миллисекундным deadline и не гарантирует разрушение/установку блока.
8. При закрытии приложения остановите новые задачи, отправьте `quit` или завершите stdin, вычитайте pipes и дождитесь process close. Сохраните неопределённое/ошибочное состояние в своей job-системе; не объявляйте успех только по факту завершения процесса.

У Offcraft нет network authentication layer, потому что нет встроенного network endpoint. Контроллер обладает файловыми и игровыми правами Java-процесса. Ограничьте отправителей команд, размер очереди приложения и доступ к profile credentials; используйте отдельные тестовые миры. Соблюдайте серверные правила автоматизации. Development-профили не заменяют авторизованные Microsoft-аккаунты.

## 7. Проверка в целевом проекте

- Подходящие loader/mod запускаются; получены `ready`, затем `status` с `visible:false` и реальным screen. Скрытый GLFW-status сам по себе не доказывает отсутствие раннего loader/console window.
- Загружен одноразовый скопированный/тестовый мир; получен `completed` готового мира, не только `accepted`.
- Выполнены `observe`, `inventory`, небольшое `act`, уникальный PNG. Просмотрены реальный PNG и dimensions; проверены input release и terminal ID correlation.
- Во время длинного `act` получен status, отменено точное действие. Проверены отсутствие второго terminal response и отсутствие удержанного/очередного управляемого ввода.
- Выполнены `quit` и в отдельной сессии EOF; наблюдены process close и повторная загрузка сохранённого мира.
- Для MCP проверены initialize → initialized → tools/list → tools/call, чистый stdout, вычитывание stderr, `isError`, поведение cancelled-call.

Запишите реальные Java/loader versions, launch mode, команды, пути файлов, результаты и ограничения. Существующий [журнал проверок](verification.md) не является PASS для вашей установки. Production-launcher, multiplayer, другие ОС, службы, произвольные renderer-комбинации и интеграция вашего приложения не подтверждаются одной сборкой или adapter-тестами.

## 8. Диагностика

| Симптом | Проверить |
| --- | --- |
| Скрытый клиент сразу выходит | stdin должен быть открыт; stderr/exit; настоящий Java target |
| Нет `ready` | JAR/loader, classpath, assets/natives, startup overlay, Java, графическая сессия/OpenGL |
| `ready` есть, действие не работает | status: onboarding/menu/loading/pause не является готовым миром |
| `busy` | Ждать terminal response предыдущей изменяющей команды; не повторять вслепую |
| Раннее окно Forge | `earlyWindowControl=false` в настоящем экземпляре до запуска |
| Forge logs нарушают framing | Обе terminal JVM-опции; всё равно фильтровать не-протокольные строки |
| MCP подключён, tool не работает | Handshake; JSON-RPC error или `isError`; child stderr и launch args |
| PNG не найден | Возвращённый path и process cwd; MCP не передаёт PNG bytes |
| После очистки нет argument file | Восстановить development-launch через `runClient` нужного загрузчика |

Полные контракты: [сборка/запуск](development.md), [JSONL](protocol.md), [MCP](mcp.md), [архитектура](architecture.md).
