# Offcraft 1.0.0 — Minecraft 1.21.1

Первая подготовленная загрузка на Modrinth, канал **Beta**. Выберите отдельный файл Fabric, Forge или NeoForge под клиент; не устанавливайте несколько вариантов одновременно.

## Включено

- Настоящий клиент Minecraft со скрытым окном и управлением через JSONL-запросы с корреляцией ID.
- Создание, загрузка и переключение локальных миров, подключение к серверу, штатное отключение и завершение.
- Ванильный ввод в игровых тиках, камера, GUI/текст, наблюдения игрока и инвентаря, клики контейнеров.
- Свежий PNG после завершения кадра; сниженный фоновый рендеринг без остановки тиков.
- MCP-адаптер Node.js без npm-зависимостей в репозитории; Node.js 22+ нужен только для MCP.
- Общая реализация внутри каждого loader JAR; отдельный common-мод, Fabric API и Architectury API не требуются.

## Требования и ограничения

Minecraft **1.21.1**, Java **21**, Windows **x64**, графическая сессия и OpenGL. Закреплённые development-конфигурации: Fabric Loader **0.16.14**, Forge **52.1.0**, NeoForge **21.1.259**. Контроллеру нужны pipes настоящего Java-процесса; это не полностью headless-режим.

Forge/NeoForge требуют `earlyWindowControl=false` в `config/fml.toml` каталога игры до запуска. Forge также нужны JVM-флаги `-Dterminal.ansi=false -Dterminal.jline=false` для работы через pipes.

Для трёх загрузчиков есть сборки и записанные проверки реальных development-клиентов. Установка через production-лаунчер, multiplayer, другие ОС и сторонние renderer-моды не проверены. Диагностический standalone JAR не является Minecraft-модом и исключён.

[Запуск](https://github.com/mayorovyf/Offcraft/blob/main/docs/ru/development.md) · [MCP](https://github.com/mayorovyf/Offcraft/blob/main/docs/ru/mcp.md) · [Проверки](https://github.com/mayorovyf/Offcraft/blob/main/docs/ru/verification.md)
