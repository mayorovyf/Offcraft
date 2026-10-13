# Verifying changes

[Русский](ru/verification.md)

[Documentation](README.md) · [Project overview](../README.md)

## Choosing verification

- Documentation only: check local links, examples, completeness of the migration, and the absence of unrelated project names/rules; there is no need to launch Minecraft.
- Shared game code or mixins: build the affected loaders using [development.md](development.md), launch a real client, and exercise the changed command through JSONL. A change to common affects all three loaders; checking one does not establish that the others work.
- MCP/SDK: run `node --test mcp/*.test.mjs` from the root, then exercise the real stdio adapter/SDK with Java. A test transport does not prove Minecraft compatibility.
- Standalone: build the Maven project, launch the JAR, and run `status`, two `render` commands, and `quit`; instructions are in [standalone.md](standalone.md).

## Game smoke scenario

1. Wait for `ready`, then check `status`: visibility/focus, dimensions, and the current screen. `ready` does not mean the world is ready.
2. Create a separate verification world or load an explicitly selected world; wait for `completed` with a ready player and world. Do not overwrite user worlds.
3. Run the affected command and check its result and visible effect. For input, check actual ticks and button release; for GUI operations, check handler/screen state.
4. If capture is affected, save and inspect a PNG; compare freshness, dimensions, and tick metadata.
5. For a long-running command, check status availability, targeted cancellation, and input release. For lifecycle changes, check quit/EOF, normal saving, and process termination.
6. If reduced mode is affected, compare increases in game ticks and render counters. Counters are not a CPU/GPU benchmark.
7. For bounded additions: use observed GUI widget bounds to click a real control; verify drop/drop_stack/swap by inventory changes; check sprint observation and release. Read enchanted/potion/book items and reject stale sync_id. Exercise chat/system/action-bar classification, pagination, eviction gaps and text truncation. Check creation seed/mode/difficulty in saved world data, not only command acceptance.
8. Capture through offcraft_capture and inspect the actual image. SDK checks include AbortSignal, EOF cleanup/save/reload, quit and startup failure. Recording checks include opt-in, exclusive creation, byte/queue limits and failure reporting; no raw logs/images in the protocol journal.

Record the specific commands executed, results, loader, and limitations. Do not present a build, adapter tests, or previous observations as verification of current game behavior. Delete temporary worlds/mods/scripts only if they were created for verification; preserve necessary evidence in ignored output directories.

## Bounded control and observation additions (2026-10-13)

- Java 21.0.12.1 and Node.js 24.21.0. `node --test mcp/*.test.mjs`: 34 passed, 0 failed. Tests cover capture path/safety/errors, SDK readiness/cancellation/lifecycle, precision-safe seeds, and bounded recorder failures/limits.
- Final `build` succeeded for all three Gradle projects; updated remapped mod/source JARs are in `dist/`. Root Maven/standalone behavior was unchanged and was not rechecked. Checked 96 local documentation links and parsed 50 JSON example lines; no missing local targets or malformed JSON.
- Real Minecraft 1.21.1 development clients: Fabric 0.16.14, Forge 52.1.0, NeoForge 21.1.259. All exercised the observed onboarding Continue widget, survival/easy creation, forward+sprint, one-tick drop (8→7), swap hands and full-stack drop (7→0), enchanted sword/potion/written-book details, stale item sync rejection, chat/system/action-bar reads and pagination/future-cursor rejection.
- Each client served status/messages/worlds/item during a 200-tick action, cancelled its exact ID with released keys, returned a fresh 640×360 PNG through offcraft_capture, switched to a newly created creative/hard world and reloaded the survival world, then quit with exit code 0. Captured frames were visually inspected. Hidden/unfocused status was observed, not continuous Win32 visibility sampling.
- Saved level.dat data on each loader retained seeds `9223372036854775807` and `-9223372036854775808`, game types survival/creative and difficulty easy/hard. Recorded accepted requests each had exactly one terminal response: Fabric 894, Forge 79, NeoForge 79.
- Fabric additionally exercised >256 messages: retained 256, gap=true, correct last cursor; a server-rendered >4096-unit message was truncated and flagged. GUI/item/world observation remains request-driven. No steady-state CPU/GPU benchmark was performed. One explicit PNG write/readback per loader reported 101.5852 ms Fabric, 103.3894 ms Forge, 76.9045 ms NeoForge; these individual samples are not throughput guarantees.
- Real SDK/Fabric: ready, create, AbortSignal cancellation of forward+sprint, EOF during another action with release evidence and normal saving, process exit 0; a second SDK session reloaded the saved world and quit with exit 0. Opt-in journals flushed protocol records, not image blocks/raw logs; disabled recording remained null.
- Evidence: ignored `output/bounded-features-second/fabric/`, `output/bounded-features-third/forge/`, `output/bounded-features-first/neoforge/`, and `output/sdk-bounded-1791867953103/`. Temporary worlds/mod copies and launch/smoke scripts were removed after checks; responses, journals, summaries and PNGs were retained. Forge/NeoForge used development JARs in isolated mod directories (Forge classpath roots were removed to avoid duplicate module discovery). These checks do not establish authenticated production-launcher installation, multiplayer, Sodium/Iris compatibility, or frequent-capture performance for the new features.


## Shared artifact directory verification (2026-10-11)

- All three Gradle `build` commands and Maven `package` succeeded with version `1.0.0`; the root `dist/` contains three remapped mod JARs, three remapped source JARs and the standalone executable under the names in [development.md](development.md#common-artifact-directory-and-names).
- Inspected loader metadata: Offcraft `1.0.0`, Minecraft `1.21.1`; verified shared classes/mixin configuration in each mod JAR and shared Java sources in each source JAR. The standalone JAR contains its main class, LWJGL and Windows x64 native library; its Maven metadata is `1.0.0`.
- Launched `dist/offcraft-1.0.0-standalone-windows-x64.jar`, exercised status → two render requests → status → quit: `visible=false`, `frames=2`, exit code 0. Both PNGs were inspected. Evidence: ignored `output/dist-verification-1791738944548/`.
- This packaging check did not relaunch Minecraft; earlier game smoke evidence is recorded separately below. Local file links in the changed build/versioning documents were checked.

## Review-fix verification (2026-10-11)

- Fabric, Forge 52.1.0 and NeoForge 21.1.259 builds succeeded. Real development clients on all three loaders created isolated worlds, moved creative hotbar items with `click_slot`, disconnected/reloaded and retained the moved stacks; offhand items were preserved. Fabric also exercised catalogue pickup and `slot:-999` cursor drop.
- An invalid envelope reusing an active action ID produced `duplicate_active_id`; the action stayed available to status/cancellation and emitted exactly one terminal response on each loader.
- Inventory open → wait → close → immediate 20-tick attack destroyed the target block in 12/12 Fabric, 4/4 Forge and 4/4 NeoForge trials, with all controlled keys released. PNGs were captured; all clients quit with exit code 0 and reported hidden/unfocused windows.
- Node.js 24.21.0: 14/14 adapter contract tests passed, including both readline error paths. With a real Fabric child, injected stdout read failure produced `source:adapter/client_stdout_failed`; injected MCP input failure closed game stdin without an uncaught exception. Both client processes exited with code 0.
- Evidence: ignored `output/fix-review-1791736233021/`. Temporary worlds, loader mod copies and launch-export files were removed. Forge/NeoForge smoke used development JARs in isolated mod directories after split class/resource launches failed loader discovery; this is not production-launcher or multiplayer verification. Third-party renderers were not checked.

## Previously recorded results

The results below were carried over from the previous README without a new run. These are historical observations for the stated configurations, not a guarantee of the current state or of any loaders, drivers, or third-party mods. Evidence paths refer to ignored local directories and may be absent in another working copy. None of the observations in this section were rerun as part of this documentation migration.

### Historical verification on this machine

- The Fabric mod build and launch of real Minecraft 1.21.1 / Loader 0.16.14 succeeded; all required mixin injections applied.
- A local normal world named `offcraft-verification` was created. Without focus, `paused=false`, `screen=none`; world time increased from 354 to 1751 while commands were being executed.
- Two PNGs in `fabric/run/output/`: 640×360, a forest world and HUD; the view changed after `look 90 15`. Both captures were inspected, and their PNG headers and differing content were checked.
- Writing to a directory path produced an error; the next PNG, with spaces in its filename, was saved, and the counter increased only for the successful write.
- Before the JSONL transition, `quit` and EOF terminated the development client with exit code 0 and saved chunks; the later protocol checks are recorded below.
- Win32 `EnumWindows` / `IsWindowVisible` and foreground checks on the running first client: 1761 samples, 0 visible process windows, 0 cases of taking foreground focus. This was discrete sampling, not proof that no brief event occurred between samples.
- The hidden-mode build before the JSONL transition was checked in a separate Java process with the `--fullscreen` argument: the window remained hidden at 640×360, the saved world loaded, two new frames after rotating the camera differed, and EOF terminated the process with exit code 0. Win32 observation covered startup, loading, and exit: 957 samples, 0 visible windows, 0 cases of taking foreground focus; client PID 29356 was included in the observation.

### Historical action and JSONL checks

- A real client created a separate verification world. `new.completed` arrived only with `state=in_world`, `world=true`, `paused=false`, `screen=none`.
- In survival on a level surface, ordinary `forward` for 20 ticks changed Z from 0.5 to 4.55758; the result contained `elapsedTicks=20`, `released=true`. Checks covered `back`, `left`, `right`, jumping (Y increased from 120 to 121.2522), sneaking with toggle-sneak enabled, and selecting slot 9 in one tick.
- Actually holding `attack` for 40 ticks destroyed a dirt block in survival. `use` for one tick placed a stone block; the stack decreased from 16 to 15. The cursor was not captured.
- `act` with a capture wrote a new 640×360 PNG on the tick when movement ended; the capture differed from the original and was inspected. A frame after item use was also inspected.
- Checks covered invalid argument types/ranges, an unknown button, malformed JSON, preserving the ID on an envelope error, `busy`, `status` availability during an action, interruption by opening the inventory, and a PNG path error. All eight buttons were released after interruption and after the write error.
- A combination of held buttons was cancelled through `quit`; the terminal `quit` response arrived with `state=stopped`, and the process terminated with exit code 0.
- The final JSONL build loaded the same world again: `load.completed` arrived without a false active ID and only in the ready state. Movement for 20 ticks reproduced the distance of 4.05758 blocks, and a PNG was saved on the tick when the action ended.
- In the final build, opening the inventory interrupted the action on tick 7 with `released=true`. EOF cancelled the simultaneous holding of five buttons on tick 6: all `pressed=false`, all `queuedPresses=0`, and the process terminated with exit code 0. Exactly one terminal response per ID was verified for 35 accepted requests.
- Captures and JSON evidence were saved in ignored `fabric/run/output/priority1-*`. The temporary verification mod used to prepare the surface and independently observe state was removed; it is absent from the finished JAR.

A remote session, a Windows service, and a normal launcher with an authenticated account were not checked. Multiplayer and Sodium/Iris checks are recorded below.

### Historical shared-implementation checks across three loaders

- The final Fabric, Forge 52.1.0, and NeoForge 21.1.259 `build` runs succeeded. Each remapped JAR includes common; Forge contains `pack.mcmeta`, without which the development client switched to `LoadingErrorScreen`.
- After common was moved, a real Fabric client created a world, performed movement, `observe`, and a 640×360 capture, then shut down normally. The final Fabric + Sodium 0.6.9 configuration was checked again through MCP with `--fullscreen`: a hidden window, world loading, a capture, concurrent status, cancellation, and EOF; no redirect conflict occurred.
- Real Forge and NeoForge clients: world creation, inventory/close, chat and Russian text, `act` for 10 ticks with jumping and a fresh capture, `observe`, cancellation of a long action with input release, disconnect and reload, and quit with exit code 0. `visible=false`, `focused=false`; each run showed +44 world ticks and 5 reduced-mode renders over 2.2 s. These are counter observations, not a benchmark.
- All three loaders were checked through the real stdio MCP adapter: status during a 200-tick act, targeted cancellation, a 640×360 PNG, and EOF with exit code 0. Forge and Fabric + Sodium were also launched with `--fullscreen`. MCP contract check: 12/12 tests passed.
- Forge, NeoForge, and final Sodium frames were inspected. Evidence and captures were saved in ignored `fabric/run/output/multiloader-*`; temporary client directories, worlds, the Sodium JAR, and the launch-export init-script were removed.
- These were development launches of real Minecraft through Loom, not verification of installing a remapped JAR in a normal authenticated launcher. JAR metadata and production mapping selectors were checked separately; compatibility with third-party Forge/NeoForge renderer mods was not checked. The Win32 polling checks above relate to earlier Fabric runs, not the newer multiloader check.

### Historical extended-control checks

- A real client: inventory/close, logical keys, mouse pickup, Shift-click, Russian text, Ctrl+A, and Backspace. A server chest was read as containing 7 diamonds; `quick_move` transferred the stack to the player, and a stale `sync_id` was rejected.
- `observe`: health 17/20, hunger 20, apples in hand, a speed effect; chest, cow, and miss targets. Cancelling a 200-tick action on tick 6 released input and kept the network world running.
- Connection to a temporary loopback 1.21.1 server; a closed port produced `connection_failed`. Offline-mode was used only locally, after authorized acceptance of the EULA. Two worlds were created; checks covered switching by creating a world, returning by loading, disconnect, quit, and loading after EOF.
- The final vanilla build with `--fullscreen` remained hidden at 640×360; a gold block placed with a vanilla command persisted after disconnect and reload. After the window fix, Sodium + MCP was checked again separately: no redirect conflict, a PNG was saved, and quit terminated with exit code 0.
- Reduced over 3.5 s: +70 world ticks, +70 client ticks, 7 renders, and 98 skips. Normal: 101 renders in approximately 1 s. These are counter observations, not a CPU/GPU benchmark or a frequency guarantee under overload.
- A false `interrupted` error after a completed `act/use` that opened a container was fixed; a repeat MCP run produced `completed`, an open chest, and a fresh PNG.
- MCP: 12 contract tests and a real stdio process. Status succeeded during a long act; `notifications/cancelled` cancelled the exact game request, without a response to the cancelled MCP call. Game errors were not hidden; EOF released input and saved the world. Quit/EOF terminated the adapter with exit code 0.
- Frames and JSON evidence from the extended checks: ignored `fabric/run/output/features-*`.
- The temporary server was stopped normally; its worlds, the separate client directory, downloaded renderer mods, and shaderpack were removed after evidence was saved.

### Historical Sodium and Iris observations

Real processes with Sodium and with Iris + enabled **MakeUp-UltraFast 9.5g** were checked: world loading, differing 640×360 PNGs after rotating the camera, `visible=false`, `focused=false`, `paused=false`, and normal exit. Iris created a shader pipeline on NVIDIA RTX 3060 Laptop; shader frames were inspected. With `--fullscreen`, the client remained hidden at 640×360. In Iris reduced mode over 2.2 s: +45 world ticks, 4 renders. The redirect conflict with the Sodium NVIDIA workaround was resolved without disabling the workaround or mixin checks. This verifies the stated versions/driver, not all shaderpacks or mod combinations.

### Historical harness checks

- Maven package: successful; the executable JAR includes Windows x64 native libraries.
- Driver: Intel Iris Xe Graphics, OpenGL 3.3.0, Build 31.0.101.3729.
- After launch and after both renders, Win32 `EnumWindows` / `IsWindowVisible`: 0 visible process windows. The foreground window belonged to another process.
- Two images in `output/`: 640×360, different triangle positions and markers in the upper-left corner; the images were inspected.
- When launched from the Offcraft directory, the first request took approximately 8.8 ms for rendering/readback and 154.1 ms total; the second took 3.0 / 63.4 ms. These are two observations, not a benchmark.
- `status` after two requests: `frames=2`, `visible=false`.
- `quit` and EOF: both terminated the process with exit code 0.

The harness checks do not apply to Minecraft; historical game-client results are recorded above.
