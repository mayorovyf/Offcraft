# Сборка и запуск

[English](../development.md)

[Документация](index.md) · [Обзор проекта](README.md)

Требуется JDK 21. Fabric сохраняет Gradle 8.14.3 / Fabric Loom 1.10.5 / Loader 0.16.14. Forge и NeoForge используют Architectury Loom 1.17.493 и Gradle 9.5.0; wrappers скачивают зафиксированную версию Gradle. Yarn — 1.21.1+build.3; для NeoForge добавлен `yarn-mappings-patch-neoforge:1.21+build.4`. Fabric API не нужен. Корневой Maven-стенд не зависит от модовых сборок.

```powershell
cd C:\Projects\MinecraftMods\Offcraft\fabric
.\gradlew.bat --no-daemon --console=plain build
.\gradlew.bat --no-daemon --console=plain runClient
```

Каждый каталог загрузчика — самостоятельный Gradle-проект; те же команды `build` и `runClient` выполняются внутри `forge/` или `neoforge/`. Готовые артефакты всех проектов попадают в корневой каталог `dist/`; правила имён приведены ниже. Это моды, не исполняемые Java JAR; устанавливать только JAR своего загрузчика в `mods/` клиента Minecraft 1.21.1. Зафиксированы Forge **52.1.0**, NeoForge **21.1.259**. Контроллеру нужен stdin процесса Java; графический лаунчер может его не предоставлять. `runClient` использует development-профиль, не авторизует Microsoft-аккаунт.

## Версионирование

Offcraft использует Semantic Versioning: `MAJOR.MINOR.PATCH`. Текущая версия — `1.0.0`; версии Minecraft, загрузчиков, зависимостей и JSONL-протокола — отдельные идентификаторы.

- **MAJOR**: несовместимые публичные изменения команд JSONL/MCP, семантики ответов или поддерживаемого использования, требующие миграции потребителей.
- **MINOR**: обратно совместимые возможности или команды.
- **PATCH**: обратно совместимые исправления и обслуживание без новой публичной возможности.
- Development-сборки используют целевую версию выпуска и `-dev.N` с положительным возрастающим счётчиком: `1.1.0-dev.1`, `1.1.0-dev.2`, затем `1.1.0`. В метаданных сборки и именах файлов нет префикса `v`; Git-теги добавляют его: `v1.1.0-dev.1` / `v1.1.0`.

В согласованном выпуске использовать одну версию Offcraft для трёх загрузчиков, standalone-стенда и MCP. До сборки/тега обновить [Fabric build.gradle](../../fabric/build.gradle), [Forge build.gradle](../../forge/build.gradle), `mod_version` в [NeoForge gradle.properties](../../neoforge/gradle.properties), [pom.xml](../../pom.xml), [mcp/package.json](../../mcp/package.json) и MCP `serverInfo.version` в [adapter.mjs](../../mcp/adapter.mjs). Тег эти значения не меняет. Сейчас версии явно объявлены в перечисленных файлах; единого источника или автоматической синхронизации нет. См. [правила выпуска Git](git.md#теги-и-сборки).

## Общий каталог и имена артефактов

Корневой `dist/` содержит готовые распространяемые артефакты, не кеши компиляции или development-JAR. Команды остаются прежними: `build` внутри загрузчика либо Maven `package` из корня; дополнительное копирование не требуется. Каталог исключён из Git.

| Артефакт | Имя файла в `dist/` для версии `1.0.0` |
| --- | --- |
| Fabric-мод | `offcraft-1.0.0-mc1.21.1-fabric.jar` |
| Forge-мод | `offcraft-1.0.0-mc1.21.1-forge.jar` |
| NeoForge-мод | `offcraft-1.0.0-mc1.21.1-neoforge.jar` |
| Исходники модов | То же имя загрузчика с `-sources` перед `.jar` |
| Исполняемый диагностический стенд | `offcraft-1.0.0-standalone-windows-x64.jar` |

Шаблон модов: `offcraft-<version>-mc<minecraft>-<loader>[-sources].jar`. Шаблон стенда: `offcraft-<version>-standalone-windows-x64.jar`; это не Minecraft-мод. MCP запускается из `mcp/index.mjs` и не собирается в JAR. Версии зависимостей загрузчиков остаются в метаданных/настройках сборки, не в имени файла.

Разные версии и загрузчики сосуществуют в `dist/`; повторная сборка той же версии заменяет только свой артефакт. Модульные `clean` удаляют собственный `build/` или `target/`, не общий `dist/` или файлы других загрузчиков. Старые артефакты автоматически не удаляются; при необходимости удалять выбранные устаревшие файлы осознанно. Промежуточные/unremapped JAR остаются в каталогах сборки модулей; их нельзя устанавливать вместо модовых JAR из `dist/`.

Для локальной очистки можно удалить `target/` и `build/` каждого загрузчика: там только генерируемые промежуточные результаты. Сборка и клиент в это время не должны работать. Сохраняйте `dist/` для публикации, `.gradle/` для кешей зависимостей/запуска, `run/` с мирами и конфигурацией, `output/` с доказательствами проверок. После удаления `build/` повторно выполните `runClient` перед development-запуском MCP: нужно восстановить сгенерированный файл аргументов. Исходники, Gradle wrappers, документация и изображения публикации не являются остатками сборки.

## Раннее окно Forge / NeoForge

**До запуска установленного клиента** установить в `<game-directory>/config/fml.toml`:

```toml
earlyWindowControl=false
```

Остальные параметры файла сохранять. Оба загрузчика создают собственное раннее окно до обнаружения обычных модов; Offcraft не может скрыть его задним числом. Поэтому без этой настройки весь запуск не считается скрытым. Development `runClient` подготавливает файл автоматически до старта игрового JVM; прямой запуск Java/MCP требует такой же предварительной настройки своего `--cwd`/game-directory. Это не JVM `-D`-параметр. Shared mixin затем создаёт основное игровое окно скрытым и отключает fullscreen.

При Forge-запуске с pipes также передать JVM-параметры `-Dterminal.ansi=false -Dterminal.jline=false`: Forge TerminalConsoleAppender иначе может оставить ANSI reset перед следующей JSONL-строкой или вмешаться в stdin. Development `runClient` выставляет их автоматически. Для установленного Java/MCP-запуска добавить их в `--args-json` до main class. Это не отключает сообщения об ошибках, только оформление/интерактивный terminal frontend.

## Sodium и Iris

```powershell
cd C:\Projects\MinecraftMods\Offcraft\fabric
.\gradlew.bat --no-daemon --console=plain -Prenderer=sodium runClient
.\gradlew.bat --no-daemon --console=plain -Prenderer=iris runClient
```

Эти renderer-профили относятся только к **Fabric**: без параметра — `vanilla`; фиксируются Sodium 0.6.9 для 1.21.1, Iris 1.8.8 + Sodium 0.6.9. Это runtime-зависимости development-запуска, не части Offcraft JAR. Fabric-моды нельзя переносить в Forge/NeoForge. В установленном клиенте брать renderer-моды именно для своего загрузчика; shaderpack положить в `shaderpacks/` и включить средствами Iris. Совместимость renderer-модов Forge/NeoForge здесь не заявляется.

## Где закреплены версии

- Fabric: [build.gradle](../../fabric/build.gradle), [Gradle wrapper](../../fabric/gradle/wrapper/gradle-wrapper.properties).
- Forge: [build.gradle](../../forge/build.gradle), [Gradle wrapper](../../forge/gradle/wrapper/gradle-wrapper.properties).
- NeoForge: [gradle.properties](../../neoforge/gradle.properties), [build.gradle](../../neoforge/build.gradle), [Gradle wrapper](../../neoforge/gradle/wrapper/gradle-wrapper.properties).
- Standalone: [pom.xml](../../pom.xml); инструкции в [standalone.md](standalone.md).

Команды сборки выше выполняются из каталога соответствующего загрузчика. MCP-пример, команды тестов и команды из standalone.md выполняются из корня, если явно не указано другое.

Сценарии проверки: [verification.md](verification.md). Подключение контроллера: [protocol.md](protocol.md) и [mcp.md](mcp.md).
