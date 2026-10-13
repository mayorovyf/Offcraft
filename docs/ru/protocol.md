# JSONL: команды и ответы

[English](../protocol.md)

[Документация](index.md) · [Обзор проекта](README.md)

Клиент на любом загрузчике принимает **только JSON Lines**: один JSON-объект на строку. Корневой standalone-стенд сохраняет текстовый интерфейс. Каждый запрос содержит непустые строковые `id` (до 128 символов) и `command`. Контроллер использует уникальные ID. Ответы с `protocol:1` выводятся чистыми JSON-строками; обычные логи Minecraft остаются в stdout/stderr и не являются протоколом. MCP-адаптер одинаков для всех загрузчиков.

После события `{"protocol":1,"type":"event","event":"ready",...}` отправить:

```json
{"id":"world-1","command":"new","world":"my-world"}
```

Дождаться **`completed` с тем же ID**, затем выполнить действия последовательно:

```json
{"id":"walk-1","command":"act","ticks":20,"buttons":["forward"],"capture":"output/after-walk.png"}
{"id":"look-1","command":"look","yaw":90,"pitch":15}
{"id":"slot-1","command":"act","ticks":1,"slot":3}
{"id":"status-1","command":"status"}
{"id":"quit-1","command":"quit"}
```

В следующем процессе `{"id":"world-2","command":"load","world":"my-world"}` открывает сохранённый мир. По умолчанию `new`: seed `"0"`, creative, peaceful и разрешённые команды; существующий каталог не перезаписывается. Настройки создания: `seed` — десятичная **строка** signed 64-bit (−9223372036854775808…9223372036854775807), `game_mode` — `survival|creative|adventure|spectator`, `difficulty` — `peaceful|easy|normal|hard`. У `switch_world` эти поля допустимы только с `create:true`; при загрузке отвергаются. Имена каталогов: латинские буквы, цифры, `_`, `-`. Создание и загрузка разрешены только вне мира.

| `command` | Поля запроса | Действие |
| --- | --- | --- |
| `new` / `load` | `world`: строка | Создать / загрузить локальный мир; завершение только после готовности игрока и мира |
| `worlds` | — | До 256 каталогов сохранений с level.dat/level.dat_old без открытия/изменения; `truncated` сообщает о лимите, `can_load` — о поддерживаемом имени |
| `connect` | `address`: строка `host[:port]` | Подключиться из меню; завершение после готовности сетевого мира |
| `disconnect` | — | Штатно выйти в меню; локальный сервер сохраняет мир |
| `switch_world` | `world`; необязательный `create`: boolean | Сохранить/закрыть текущий мир и загрузить другой; `create:true` создаёт новый |
| `cancel` | необязательный `target`: ID активного `act` | Отменить действие и отпустить ввод, оставив клиент и мир работающими |
| `act` | `ticks`: целое 1…12000; необязательные `buttons`, `slot`, `capture` | Удерживать игровые кнопки указанное число клиентских игровых тиков, отпустить и при необходимости сохранить свежий кадр |
| `press` | `action`: `drop` / `drop_stack` / `swap_hands` | Одно vanilla-нажатие за один клиентский тик; ввод отпускается до terminal response |
| `look` | `yaw`, `pitch`: конечные числа; pitch −90…90 | Повернуть камеру игрока |
| `render` | необязательный `path`: строка | Сохранить следующий отрисованный кадр; по умолчанию `output/frame-0001.png` и далее |
| `status` | — | Состояние мира, PID, GLFW visibility/focus, пауза, время мира, кадры, тики, размер, экран, активный запрос; позиция/углы/слот при наличии игрока |
| `observe` | — | Игрок, здоровье, голод, эффекты, руки, GUI и цель под прицелом |
| `inventory` | — | Текущий handler, `sync_id`, revision, слоты, cursor stack и координаты GUI |
| `item` | `sync_id`, `slot`: ID текущего handler-слота | Подробности одного стека; устаревший handler отвергается |
| `messages` | необязательные `after`: safe integer ≥0; `limit`: 1…256 | Новые сообщения чата/системы/action bar из ограниченного журнала процесса |
| `gui` | `action`: `inventory` / `close` | Открыть инвентарь / штатно закрыть текущий экран |
| `key` | `key`: имя клавиши / GLFW-код; необязательный `modifiers` | Нажатие и отпускание клавиши GUI; вне GUI только inventory/chat/escape |
| `click` | `x`, `y`; необязательные `button` 0…2, `modifiers` | Нажатие и отпускание мыши в координатах scaled GUI |
| `text` | `text`: строка | Ввести Unicode-текст в текущий экран через vanilla `charTyped` |
| `click_slot` | `sync_id`, `slot`, `button`, `action` | Обычная операция screen handler с vanilla-предсказанием и сетевым пакетом |
| `render_policy` | `mode`: `normal` / `reduced`; необязательный `fps` 1…60 | Управлять частотой фоновой отрисовки без остановки тиков |
| `help` | — | Список команд, кнопок и диапазонов |
| `quit` | — | Отпустить кнопки, отменить активный запрос, сохранить мир и остановить клиент |

`buttons` — массив: `forward`, `back`, `left`, `right`, `jump`, `sneak`, `attack`, `use`, `sprint`. Кнопки можно сочетать; противоположные направления обрабатывает vanilla. `slot` — номер хотбара **1…9**. Пустые/отсутствующие кнопки позволяют ждать или выбрать слот. `press` ставит одно логическое drop/swap-нажатие, а не удержание. `drop_stack` включает Control только во время своего vanilla-тика ввода; стеки напрямую не меняются. Спринт учитывает обычные ограничения голода, движения и столкновений.

## GUI, инвентарь и наблюдение

`gui/inventory` открывает обычный инвентарь, `gui/close` штатно закрывает экран/контейнер. `key` принимает `INVENTORY`, `CHAT`, `ESCAPE`, `ENTER`, `TAB`, `BACKSPACE`, `LEFT`, `RIGHT`, `UP`, `DOWN`, `HOME`, `END`, `DELETE`, `SPACE` или GLFW-код 32…348. `modifiers` — GLFW-маска: Shift=1, Control=2, Alt=4, Super=8. Клавиша/мышь отпускается после callback; модификаторы не остаются активными. Игровое удержание выполняется через `act`.

`click` использует **scaled GUI pixels**, не framebuffer pixels; размеры и `scale_factor` возвращаются в `gui`. Слоты содержат handler ID `id`, индекс inventory `index`, описание стека, доступность и координаты. `click_x/click_y` доступны только при видимом matching handler. `selected_hotbar_slot` в наблюдении/инвентаре — **0…8**, в `act.slot` — **1…9**.

```json
{"id":"open","command":"gui","action":"inventory"}
{"id":"items","command":"inventory"}
{"id":"move","command":"click_slot","sync_id":0,"slot":36,"button":0,"action":"quick_move"}
{"id":"close","command":"gui","action":"close"}
{"id":"chat","command":"key","key":"CHAT"}
{"id":"type","command":"text","text":"Привет Offcraft"}
{"id":"escape","command":"key","key":"ESCAPE"}
```

Изменяющие команды отправлять после терминального ответа предыдущей. Брать актуальные `sync_id` и `slot.id` из `inventory`, не переносить player inventory индексы на сундук. `click_slot.action`: `pickup`, `quick_move`, `swap`, `throw`, `pickup_all`; `button`: 0/1, для `swap` — hotbar 0…8 либо offhand 40. `slot:-999` только для `pickup` (сброс cursor stack вне контейнера). Устаревший `sync_id` отвергается. Обычные handler используют vanilla `ClientPlayerInteractionManager.clickSlot`; открытый creative-инвентарь — vanilla-обработчик кликов своего экрана, который преобразует слоты каталога/hotbar/инвентаря в creative-операции и пакеты. Offcraft не записывает стеки напрямую. `sent:true, server_confirmed:false` описывает переданную vanilla-операцию и локальное предсказание, **не** серверное подтверждение; creative-операции только с каталогом/курсором могут не отправлять пакет. Последующий `inventory` также описывает локальное состояние.

`observe`: `player` (или null), здоровье/поглощение/броня, `food`, насыщение, эффекты с ID и длительностью в тиках, предметы в руках, координаты/скорость, игровые флаги и GUI. `target.type`: `block`, `entity`, `miss`, `unavailable`; блок содержит позицию/грань/свойства, сущность — ID/тип/позицию/здоровье. Это последнее клиентское состояние, не запрос серверной истины; цель обновляется при отрисовке.

`gui.buttons` перечисляет стандартные `ClickableWidget`: `label`, `bounds:{x,y,width,height}`, `active`, `visible`, `focused`. Координаты — scaled GUI pixels. Перед `click` прочитайте актуальный экран; постоянных ID виджетов нет. Нарисованные вручную элементы сторонних модов могут отсутствовать.

`item` возвращает обычные поля стека, `state:"local"`, идентификатор/revision handler, slot, nullable `custom_name`, массивы `enchantments`/`stored_enchantments` вида `{id,level}`. Необязательный `potion` содержит ID, цвета и эффекты; `book` — kind/pages и для написанной книги title, author, generation, resolved. Страницы учитывают настройку фильтрации текста клиента. Подробности читаются только для указанного слота; полного дампа компонентов/NBT нет.

`messages` по умолчанию: `after:0,limit:256`. Записи `{seq,kind,text,truncated}` имеют монотонный номер в пределах процесса; kind: `chat`, `system`, `action_bar`. Записываются только сообщения, доставленные vanilla HUD, не серверная история. Ёмкость 256, текст до 4096 UTF-16 units на запись. Поля ответа: `next` — номер последней возвращённой записи для следующего запроса, `oldest_seq`, `latest_seq`, `gap` — старые записи вытеснены, `has_more`, `capacity`, `max_text_length`. В пустом журнале номера нулевые. Будущий курсор отвергается. Смена мира журнал не очищает; новый процесс очищает. Фонового опроса, анализа текста и потока сообщений нет.

```json
{"id":"survival","command":"new","world":"survival-check","seed":"42","game_mode":"survival","difficulty":"normal"}
{"id":"sprint","command":"act","ticks":20,"buttons":["forward","sprint"]}
{"id":"swap","command":"press","action":"swap_hands"}
{"id":"detail","command":"item","sync_id":0,"slot":36}
{"id":"chat-read","command":"messages","after":0,"limit":32}
```

`cancel` без target отменяет активный `act`; с target требует точного ID. Действие получает `error/cancelled` с фактическим числом тиков и `released:true`; `cancel` завершается отдельно. Загрузка/соединение этой командой не отменяются; `quit` останавливает клиент целиком.

## Принятие, завершение и ошибки

- Каждый корректный конверт получает `accepted` с `id`, `command` и текущим `data`; это **не** подтверждение выполненного действия. Затем приходит ровно один терминальный ответ `completed` либо `error` для этого запроса.
- Ошибки аргументов/неизвестной команды: `invalid_command`; ошибки выполнения начатой команды: `command_failed`; занятость: `busy`; прерывание незаконченного действия экраном, паузой или потерей мира: `interrupted`; отмена/остановка: `cancelled`; ошибка PNG: `capture_failed`. Ошибка JSON/конверта: `invalid_request`, без `accepted`; usable ID сохраняется, иначе `id:null`.
- Одновременно выполняется одна длительная/изменяющая команда. Доступны `status`, `help`, `observe`, `inventory`, `item`, `messages`, `worlds`, `cancel`, `quit`; остальные получают `busy`. Повтор активного ID даёт `duplicate_active_id`, не второй terminal response, включая некорректный конверт с usable ID активного запроса. Истории ID/replay нет.
- `new`/`load`/`switch_world`: события `loading`, затем `in_world`; `connect`: `connecting`, затем `in_world`. Завершение только когда игрок присутствует, мир не на паузе, экран/overlay закрыты и игровые тики разрешены. Требующая взаимодействия ошибка загрузки даёт `load_requires_attention`, отмена загрузки — `load_cancelled`, отказ соединения — `connection_failed`; ложного успеха нет. `connect` использует профиль запущенного клиента; development-профиль не заменяет Microsoft-авторизацию. Серверные resource packs автоматически не принимаются.
- `act` и `press`: `completed.data` содержит `elapsedTicks`, `durationTicks`, `finished`, `start`/`end`, `maxY`, `sneakObserved`, `sprintObserved`, `keys` с `pressed`/`queuedPresses`, `released`. У `press` также есть `action`. Доказательство освобождения включает sprint/drop/swap. Ошибка начатого действия содержит частичный результат; отмена не выдаётся за выполнение всех тиков. `cancel` отменяет только `act`, не однотиковый `press`.
- При `capture` завершение приходит **после** записи PNG. `data.capture` содержит `path`, `frame`, `width`, `height`, `tick`, `render_sequence`, `total_ms`. `action_finished_tick` — тик окончания ввода; тик снимка не меньше него. Снимок использует уже завершённый игровой кадр и конечное состояние интерполяции, не старый framebuffer.
- Путь допускает пробелы в JSON-строке. Относительные пути считаются от рабочего каталога (`fabric/run/` у `runClient`). Счётчик успешных PNG начинается с нуля в новом процессе, совпадающие файлы перезаписываются. Ошибка записи не увеличивает счётчик.
- `quit.completed` отправляется после освобождения ресурсов клиента. EOF также отменяет активное действие, отпускает кнопки и штатно сохраняет мир; для него ID нет, выводится событие `stopped`.

Пример двух ответов на действие; дополнительные поля `data` здесь опущены:

```json
{"protocol":1,"type":"accepted","id":"walk-1","command":"act","data":{"state":"in_world"}}
{"protocol":1,"type":"completed","id":"walk-1","command":"act","data":{"elapsedTicks":20,"durationTicks":20,"finished":true,"released":true}}
```

**Время действий — игровые тики, не миллисекунды.** Удержание атаки/использования следует vanilla-механике: начальное нажатие, затем breaking progress или cooldown/удержание предмета. Действие не обещает разрушить любой блок или использовать недоступный предмет. Серверные ограничения, столкновения, инерция и игровые cooldown остаются действующими; движение не подменяется телепортацией. Завершение означает выполненный клиентский ввод, не отдельное подтверждение всех эффектов сервером.

## Готовность протокола и мира

Событие `ready` означает, что клиент принимает запросы; оно не гарантирует главный экран меню или готовый мир. Например, клиент может находиться на `AccessibilityOnboardingScreen`. Проверяйте `status.screen`; готовность мира подтверждается терминальным ответом загрузки/соединения и игровым состоянием, а не событием `ready`.

Запуск клиента: [development.md](development.md). MCP-транспорт: [mcp.md](mcp.md).
