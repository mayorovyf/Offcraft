# Комплект публикации Modrinth

[English](../modrinth.md)

[Документация](index.md) · [Обзор проекта](README.md)

Комплект готовит **Offcraft 1.0.0 для Minecraft 1.21.1**. Он не создаёт проект Modrinth, не резервирует slug, не загружает файлы, не делает commit/tag и не публикует релиз. Основное описание — английское; полная русская альтернатива также подготовлена.

## Поля проекта

| Поле | Значение |
| --- | --- |
| Имя / slug | Offcraft / `offcraft` |
| Тип | Mod |
| Английское краткое описание | Control a real Minecraft client through JSONL or MCP, with a hidden window and fresh PNG captures. |
| Категория | Utility (`utility`) |
| Среда | Client-only (`client_only` для каждой версии); не устанавливать на выделенный сервер |
| Старые side-поля | Client: required; server: unsupported |
| Лицензия | MIT |
| Исходники | https://github.com/mayorovyf/Offcraft |
| Issues | https://github.com/mayorovyf/Offcraft/issues |
| Документация | https://github.com/mayorovyf/Offcraft/blob/main/docs/README.md |
| Лицензия URL | https://github.com/mayorovyf/Offcraft/blob/main/LICENSE |
| Discord / donations | Оставить пустыми; данных нет |
| Начальный статус | Draft; отправлять на модерацию после проверки описания и файлов |

Русское краткое описание: «Управление настоящим клиентом Minecraft через JSONL или MCP: скрытое окно и свежие PNG-кадры.»

GET `https://api.modrinth.com/v2/project/offcraft` вернул HTTP 404 2026-10-11. Это не резервирование и не гарантия доступности slug при публикации.

## Готовые материалы

- [Английское описание](../modrinth/description.en.md); [русское описание](modrinth/description.ru.md).
- [Английский changelog](../modrinth/changelog.en.md); [русский changelog](modrinth/changelog.ru.md).
- [Поля проекта](../modrinth/project.json) с полным английским body.
- [Три версии](../modrinth/versions.json): загрузчик, версия игры, changelog и соответствие файлов.
- [Gallery заголовки и подписи](../modrinth/gallery.json).
- [Иконка](../modrinth/assets/icon.png): PNG 512×512, 234 282 байта (меньше лимита Modrinth 512 КиБ), сгенерированный artwork, не игровой скриншот.
- [Векторная аватарка](../modrinth/assets/offcraft-avatar.svg): редактируемая SVG-реконструкция рисунка PNG, не попиксельная копия и не встроенный растр. PNG оставлен без изменений.
- [Главный gallery-кадр](../modrinth/assets/gallery-world.png): неизменённый PNG 640×360 из настоящего скрытого Fabric development-клиента, получен 2026-10-11.

Сохраните предупреждение в начале описания: окно намеренно скрыто, контроллеру нужны pipes настоящего Java-процесса, EOF завершает клиент. Не называйте Offcraft полностью headless, серверным модом, средством авторизации или универсально совместимым с лаунчерами/модпаками.

## Загрузка версий

Создайте **три отдельные версии** с номером `1.0.0`, каналом **Beta**. Для каждой укажите **только Minecraft 1.21.1** и **только её загрузчик**. Не объединяйте варианты загрузчиков в одну взаимозаменяемую версию.

| Загрузчик | Основной файл в `dist/` | Необязательные исходники |
| --- | --- | --- |
| Fabric | `offcraft-1.0.0-mc1.21.1-fabric.jar` | `offcraft-1.0.0-mc1.21.1-fabric-sources.jar` |
| Forge | `offcraft-1.0.0-mc1.21.1-forge.jar` | `offcraft-1.0.0-mc1.21.1-forge-sources.jar` |
| NeoForge | `offcraft-1.0.0-mc1.21.1-neoforge.jar` | `offcraft-1.0.0-mc1.21.1-neoforge-sources.jar` |

Source-вложения отмечайте **sources-jar**, не основными файлами установки. Modrinth project dependencies не нужны: Fabric API и Architectury API не требуются; Node.js — внешний необязательный runtime MCP, не модовая зависимость. Загрузчик выбирается в экземпляре игры; не придумывайте для него dependency project.

Закреплённые development-конфигурации: Fabric Loader 0.16.14, Forge 52.1.0, NeoForge 21.1.259; Java 21, Windows x64, графическая сессия/OpenGL. До запуска Forge/NeoForge задайте `earlyWindowControl=false` в `config/fml.toml`; Forge нужны `-Dterminal.ansi=false -Dterminal.jline=false` для pipes. Не удаляйте эти требования из описания.

Beta выбрана намеренно: реальные development-клиенты проверялись, но установка этих remapped JAR через production-лаунчер, multiplayer, другие ОС и сторонние render-моды не проверены. Версия артефакта остаётся `1.0.0`; канал не переименовывает её.

## Локальный архив передачи

`dist/offcraft-1.0.0-modrinth-kit.zip` содержит ровно шесть mod/source JAR, `LICENSE`, адаптер `mcp/` с контрактными тестами/package metadata, описания и changelog на двух языках, project/version/gallery JSON, иконку, игровой PNG, это руководство на двух языках, manifest и суммы. На Modrinth загружайте отдельные JAR, **не архив комплекта**. MCP — companion из репозитория/ZIP, не устанавливаемый модовый JAR.

`dist/modrinth-1.0.0-manifest.json` фиксирует исходный commit `aa1f6403e5eacfbc5f7c5638b07999edf3b0a7fe`, размеры, SHA-1/SHA-256/SHA-512, наблюдаемые проверки и ограничения. `dist/SHA256SUMS.txt` покрывает семь собранных JAR, включая диагностический стенд. Файл сумм внутри комплекта покрывает только шесть включённых JAR; `dist/offcraft-1.0.0-modrinth-kit.zip.sha256` проверяет весь ZIP.

Standalone `offcraft-1.0.0-standalone-windows-x64.jar` собран отдельно, но исключён из комплекта и версий Modrinth: он рисует диагностический треугольник и не является Minecraft-модом. В ZIP нет миров, локальной игровой конфигурации, account caches, логов, credentials, бинарников Minecraft и сторонних native-библиотек.

## Ручная или API-публикация

1. Создайте draft mod project с полями и иконкой. Вставьте английское описание; русское — альтернатива, не обязательный второй body.
2. Создайте три версии, добавьте соответствующий мод и при желании sources, вставьте changelog. Укажите Beta, client-only, Minecraft 1.21.1 и правильный загрузчик.
3. Загрузите игровой PNG с подписью как главный gallery-кадр. Иконку можно добавить как отдельный artwork, но не называть скриншотом.
4. Проверьте требования, файлы, зависимости и ссылки; отправьте на модерацию из аккаунта владельца. Для подготовки Modrinth credential не нужен.

JSON — данные передачи, не uploader. `project.json` использует документированные v2 project-поля и намеренно задаёт draft. Для создания API-версии возьмите настоящий project ID из ответа создания проекта, исключите локальный `files` из `data` JSON и приложите указанные JAR как multipart-поля `mod` и `sources`. `file_parts`, `primary_file`, `file_types` уже задают эти поля. Реального project ID до создания не существует; он намеренно не выдуман. Без sources уберите `sources` из `file_parts` и `file_types`. Перед загрузкой перечитайте текущий API-контракт: environment и старые side-поля менялись.

Официальные источники: [create project](https://docs.modrinth.com/api/operations/createproject/), [create version](https://docs.modrinth.com/api/operations/createversion/), [категории](https://api.modrinth.com/v2/tag/category).

## Проверки подготовки (2026-10-11)

- Gradle `build` Fabric, Forge, NeoForge успешны, по 7 up-to-date задач. Maven `package` успешен; shade предупредил о пересекающихся manifest/native checksum resources, это не ошибка сборки.
- Семь JAR проверены на MIT notice; проверены loader metadata, общие классы/mixins и source-вложения. SHA-1/SHA-256/SHA-512 вычислены из реальных байтов.
- `node --test mcp/adapter.test.mjs`: 14 passed, 0 failed.
- Свежий smoke настоящего Fabric development-клиента: ready/status, новый мир, look, движение 20 тиков, PNG, quit; следующие сессии загрузили сохранённый мир и сняли его после normal rendering/160 тиков пустого действия. Наблюдался скрытый/нефокусированный статус; три сессии завершились с кодом 0. Итоговый PNG просмотрен. Доказательства: игнорируемый `output/modrinth-1791741044617/`.
- Свежий smoke использовал development-классы, не remapped release JAR. Runtime Forge/NeoForge — ранее [записанные результаты](verification.md), не новые запуски для комплекта.

Подтверждения аккаунтов, production-лаунчеров, multiplayer или сторонних render-модов не подразумеваются. Проект Modrinth этим заданием не опубликован.
