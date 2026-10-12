# Modrinth publication kit

[Русский](ru/modrinth.md)

[Documentation](README.md) · [Project overview](../README.md)

This kit prepares **Offcraft 1.0.0 for Minecraft 1.21.1**. It does not create a Modrinth project, reserve its slug, upload files, commit changes, tag, or publish a release. English is the primary listing language; a complete Russian alternative is included.

## Project fields

| Field | Value |
| --- | --- |
| Name / slug | Offcraft / `offcraft` |
| Type | Mod |
| Summary | Control a real Minecraft client through JSONL or MCP, with a hidden window and fresh PNG captures. |
| Featured category | Utility (`utility`) |
| Environment | Client-only (`client_only` on each version); no dedicated-server installation |
| Legacy sides | Client: required; server: unsupported |
| License | MIT |
| Source | https://github.com/mayorovyf/Offcraft |
| Issues | https://github.com/mayorovyf/Offcraft/issues |
| Documentation | https://github.com/mayorovyf/Offcraft/blob/main/docs/README.md |
| License URL | https://github.com/mayorovyf/Offcraft/blob/main/LICENSE |
| Discord / donations | Leave blank; none supplied |
| Initial status | Draft; submit for moderation only after files and description are reviewed |

Russian summary: «Управление настоящим клиентом Minecraft через JSONL или MCP: скрытое окно и свежие PNG-кадры.»

A GET of `https://api.modrinth.com/v2/project/offcraft` returned HTTP 404 on 2026-10-11. This is not a reservation or proof that the slug can be claimed at publication time.

## Copy-ready material

- [English project description](modrinth/description.en.md); [Russian description](ru/modrinth/description.ru.md).
- [English changelog](modrinth/changelog.en.md); [Russian changelog](ru/modrinth/changelog.ru.md).
- [Project fields](modrinth/project.json), including the complete English body.
- [Three version records](modrinth/versions.json), including loader, game version, changelog, and file mapping.
- [Gallery titles and captions](modrinth/gallery.json).
- [Project icon](modrinth/assets/icon.png): square 512×512 PNG, 234,282 bytes (below Modrinth's 512 KiB limit), generated artwork, not a gameplay screenshot.
- [Vector avatar](modrinth/assets/offcraft-avatar.svg): editable SVG reconstruction of the PNG artwork, not a pixel-exact conversion or an embedded raster image. The PNG remains unchanged.
- [Featured gallery image](modrinth/assets/gallery-world.png): unedited 640×360 PNG from a real hidden Fabric development client, captured on 2026-10-11.

Keep the installation warning near the top of the description: the window is intentionally hidden, a controller needs pipes to the actual Java process, and EOF shuts down the client. Do not describe Offcraft as fully headless, a server mod, an account authenticator, or universally compatible with launchers/modpacks.

## Version uploads

Create **three separate versions**, all numbered `1.0.0`, on the **Beta** channel. Select **only Minecraft 1.21.1** and **only the matching loader** for each version. Do not upload all loader files as one interchangeable version.

| Loader | Primary installation file in `dist/` | Optional attachment |
| --- | --- | --- |
| Fabric | `offcraft-1.0.0-mc1.21.1-fabric.jar` | `offcraft-1.0.0-mc1.21.1-fabric-sources.jar` |
| Forge | `offcraft-1.0.0-mc1.21.1-forge.jar` | `offcraft-1.0.0-mc1.21.1-forge-sources.jar` |
| NeoForge | `offcraft-1.0.0-mc1.21.1-neoforge.jar` | `offcraft-1.0.0-mc1.21.1-neoforge-sources.jar` |

Mark source attachments **sources-jar**, not primary installation files. No Modrinth project dependencies are required: Fabric API and Architectury API are not needed; Node.js is an optional external MCP runtime, not a mod dependency. The loader itself is chosen in the instance, not invented as a Modrinth dependency project.

Pinned development configurations: Fabric Loader 0.16.14, Forge 52.1.0, NeoForge 21.1.259; Java 21, Windows x64, graphical session/OpenGL. Forge/NeoForge need `earlyWindowControl=false` in `config/fml.toml` before launch; Forge needs `-Dterminal.ansi=false -Dterminal.jline=false` with pipes. Preserve these requirements in the listing.

Beta is deliberate: real development-client checks exist, but installation of these remapped JARs through a production launcher, multiplayer, other OSes, and third-party rendering mods were not verified. The artifact's own version remains `1.0.0`; the release channel does not rename it.

## Local handoff bundle

`dist/offcraft-1.0.0-modrinth-kit.zip` contains exactly six mod/source JARs, `LICENSE`, the `mcp/` adapter and its contract tests/package metadata, English/Russian descriptions and changelogs, project/version/gallery JSON, icon, gallery PNG, this guide in both languages, manifest, and checksums. Upload the individual JARs to Modrinth, **not the handoff ZIP**. MCP is a repository/ZIP companion, not an installable mod JAR.

`dist/modrinth-1.0.0-manifest.json` records source commit `aa1f6403e5eacfbc5f7c5638b07999edf3b0a7fe`, artifact sizes and SHA-1/SHA-256/SHA-512, observed checks, and limits. `dist/SHA256SUMS.txt` covers all seven built JARs, including the diagnostic harness. The bundle's checksum file covers only its six included JARs; `dist/offcraft-1.0.0-modrinth-kit.zip.sha256` checks the complete ZIP.

The standalone `offcraft-1.0.0-standalone-windows-x64.jar` is built separately but excluded from the Modrinth kit and versions: it renders a diagnostic triangle and is not a Minecraft mod. No worlds, local game config, account caches, logs, credentials, Minecraft binaries, or third-party native libraries are included in the handoff ZIP.

## Manual or API publication

1. Create a draft mod project with the fields above and the icon. Paste the English description; the Russian file is an optional alternative, not a second mandatory body.
2. Create the three versions, attach the matching mod and optionally its sources, and paste the changelog. Select Beta, client-only, Minecraft 1.21.1, and the correct loader.
3. Upload the featured gameplay PNG with its supplied caption. The icon may be used as optional gallery artwork; label it artwork, never a screenshot.
4. Review requirements, files, dependencies, and links, then submit for moderation using the authenticated owner's account. No Modrinth credential is required for this preparation step.

The JSON files are handoff data, not an uploader. `project.json` matches documented v2 project fields and intentionally requests a draft. For API version creation, use the actual project ID returned by creation, omit each record's local `files` mapping from the `data` JSON, and attach the mapped JARs as multipart fields `mod` and `sources`. `file_parts`, `primary_file`, and `file_types` already describe those fields. The actual project ID cannot exist before project creation and is deliberately not fabricated. If sources are not uploaded, omit `sources` from `file_parts` and `file_types` too. Read the current API contract before an upload; environment fields and legacy side fields have changed over time.

Official references: [create project](https://docs.modrinth.com/api/operations/createproject/), [create version](https://docs.modrinth.com/api/operations/createversion/), [category list](https://api.modrinth.com/v2/tag/category).

## Preparation evidence (2026-10-11)

- Fabric, Forge, and NeoForge Gradle `build` succeeded, each with 7 tasks up-to-date. Maven `package` succeeded; shade reported overlapping manifest/native checksum resources, not a failed build.
- All seven JARs were inspected for the MIT notice; loader metadata, shared classes/mixins, and source attachments were checked. SHA-1/SHA-256/SHA-512 were computed from actual bytes.
- `node --test mcp/adapter.test.mjs`: 14 passed, 0 failed.
- Fresh real Fabric development-client smoke: ready/status, new world, look, 20-tick movement, PNG, quit; subsequent sessions loaded the saved world and captured it after normal rendering/160 empty action ticks. Hidden/unfocused status was observed; all three sessions exited 0. The final gameplay PNG was visually inspected. Evidence: ignored `output/modrinth-1791741044617/`.
- Fresh smoke used development classes, not the remapped release JAR. Forge/NeoForge runtime results are earlier [recorded evidence](verification.md#review-fix-verification-2026-10-11), not new runs for this kit.

No account, production-launcher, multiplayer, or third-party renderer certification is implied. The Modrinth project is not published by this task.
