# JSONL: commands and responses

[Русский](ru/protocol.md)

[Documentation](README.md) · [Project overview](../README.md)

The client on any loader accepts **only JSON Lines**: one JSON object per line. The root standalone harness retains its text interface. Each request contains nonempty string fields `id` (up to 128 characters) and `command`. The controller uses unique IDs. Responses with `protocol:1` are emitted as clean JSON lines; ordinary Minecraft logs remain on stdout/stderr and are not part of the protocol. The MCP adapter is the same for all loaders.

After the event `{"protocol":1,"type":"event","event":"ready",...}`, send:

```json
{"id":"world-1","command":"new","world":"my-world"}
```

Wait for **`completed` with the same ID**, then perform actions sequentially:

```json
{"id":"walk-1","command":"act","ticks":20,"buttons":["forward"],"capture":"output/after-walk.png"}
{"id":"look-1","command":"look","yaw":90,"pitch":15}
{"id":"slot-1","command":"act","ticks":1,"slot":3}
{"id":"status-1","command":"status"}
{"id":"quit-1","command":"quit"}
```

In the next process, `{"id":"world-2","command":"load","world":"my-world"}` opens the saved world. `new` creates a normal world with seed 0, creative mode, peaceful difficulty, and commands enabled; an existing directory is not overwritten. Directory names use Latin letters, digits, `_`, and `-`. Creation and loading are allowed only when not in a world.

| `command` | Request fields | Action |
| --- | --- | --- |
| `new` / `load` | `world`: string | Create / load a local world; completion only after the player and world are ready |
| `connect` | `address`: string `host[:port]` | Connect from the menu; completion after the network world is ready |
| `disconnect` | — | Exit normally to the menu; the local server saves the world |
| `switch_world` | `world`; optional `create`: boolean | Save/close the current world and load another; `create:true` creates a new one |
| `cancel` | optional `target`: ID of the active `act` | Cancel the action and release input, leaving the client and world running |
| `act` | `ticks`: integer 1…12000; optional `buttons`, `slot`, `capture` | Hold game buttons for the specified number of client game ticks, release them, and optionally save a fresh frame |
| `look` | `yaw`, `pitch`: finite numbers; pitch −90…90 | Rotate the player's camera |
| `render` | optional `path`: string | Save the next rendered frame; defaults to `output/frame-0001.png` and subsequent numbers |
| `status` | — | World state, PID, GLFW visibility/focus, pause state, world time, frames, ticks, size, screen, active request; position/angles/slot when a player is present |
| `observe` | — | Player, health, hunger, effects, hands, GUI, and crosshair target |
| `inventory` | — | Current handler, `sync_id`, revision, slots, cursor stack, and GUI coordinates |
| `gui` | `action`: `inventory` / `close` | Open the inventory / close the current screen normally |
| `key` | `key`: key name / GLFW code; optional `modifiers` | Press and release a GUI key; outside a GUI, only inventory/chat/escape |
| `click` | `x`, `y`; optional `button` 0…2, `modifiers` | Press and release the mouse at scaled GUI coordinates |
| `text` | `text`: string | Enter Unicode text into the current screen through vanilla `charTyped` |
| `click_slot` | `sync_id`, `slot`, `button`, `action` | Normal screen handler operation with vanilla prediction and a network packet |
| `render_policy` | `mode`: `normal` / `reduced`; optional `fps` 1…60 | Control background rendering frequency without stopping ticks |
| `help` | — | List commands, buttons, and ranges |
| `quit` | — | Release buttons, cancel the active request, save the world, and stop the client |

`buttons` is an array: `forward`, `back`, `left`, `right`, `jump`, `sneak`, `attack`, `use`. Buttons can be combined; opposite directions are handled by normal game logic. `slot` is a hotbar number **1…9**. An empty array or omitted buttons allow waiting for ticks or selecting a slot without moving. Combination example: `{"id":"jump-1","command":"act","ticks":10,"buttons":["forward","jump","sneak"]}`.

## GUI, inventory, and observation

`gui/inventory` opens the normal inventory; `gui/close` closes the screen/container normally. `key` accepts `INVENTORY`, `CHAT`, `ESCAPE`, `ENTER`, `TAB`, `BACKSPACE`, `LEFT`, `RIGHT`, `UP`, `DOWN`, `HOME`, `END`, `DELETE`, `SPACE`, or a GLFW code 32…348. `modifiers` is a GLFW mask: Shift=1, Control=2, Alt=4, Super=8. The key/mouse button is released after the callback; modifiers do not remain active. Use `act` to hold game input.

`click` uses **scaled GUI pixels**, not framebuffer pixels; dimensions and `scale_factor` are returned in `gui`. Slots contain the handler ID `id`, inventory index `index`, stack description, availability, and coordinates. `click_x/click_y` are available only when the matching handler is visible. `selected_hotbar_slot` in observation/inventory is **0…8**, while `act.slot` is **1…9**.

```json
{"id":"open","command":"gui","action":"inventory"}
{"id":"items","command":"inventory"}
{"id":"move","command":"click_slot","sync_id":0,"slot":36,"button":0,"action":"quick_move"}
{"id":"close","command":"gui","action":"close"}
{"id":"chat","command":"key","key":"CHAT"}
{"id":"type","command":"text","text":"Привет Offcraft"}
{"id":"escape","command":"key","key":"ESCAPE"}
```

Send state-changing commands after the previous command's terminal response. Obtain current `sync_id` and `slot.id` values from `inventory`; do not apply player inventory indices to a chest. `click_slot.action`: `pickup`, `quick_move`, `swap`, `throw`, `pickup_all`; `button`: 0/1, or for `swap`, hotbar 0…8 or offhand 40. `slot:-999` is only for `pickup` (dropping the cursor stack outside the container). A stale `sync_id` is rejected. Ordinary handlers use vanilla `ClientPlayerInteractionManager.clickSlot`; an open creative inventory uses its vanilla screen click handler, which translates catalogue/hotbar/inventory slots into creative operations and packets. Offcraft does not write stacks directly. `sent:true, server_confirmed:false` describes the dispatched vanilla operation and local prediction, **not** server confirmation; catalogue/cursor-only creative operations need not send a packet. A subsequent `inventory` also describes local state.

`observe`: `player` (or null), health/absorption/armor, `food`, saturation, effects with IDs and durations in ticks, items in hands, coordinates/velocity, game flags, and GUI. `target.type`: `block`, `entity`, `miss`, `unavailable`; a block contains position/face/properties, and an entity contains ID/type/position/health. This is the latest client state, not a query for server truth; the target updates during rendering.

`cancel` without a target cancels the active `act`; with a target, it requires the exact ID. The action receives `error/cancelled` with the actual tick count and `released:true`; `cancel` completes separately. This command does not cancel loading/connection; `quit` stops the entire client.

## Acceptance, completion, and errors

- Every valid envelope receives `accepted` with `id`, `command`, and current `data`; this is **not** confirmation that the action has been performed. Exactly one terminal response, `completed` or `error`, then follows for that request.
- Argument errors/unknown command: `invalid_command`; execution errors after a command has started: `command_failed`; client busy: `busy`; interruption of an unfinished action by a screen, pause, or loss of the world: `interrupted`; cancellation/shutdown: `cancelled`; PNG error: `capture_failed`. JSON/envelope error: `invalid_request`, without `accepted`; a usable ID is retained, otherwise `id:null`.
- One long-running/state-changing command executes at a time. While it runs, `status`, `help`, `observe`, `inventory`, `cancel`, and `quit` remain available; other commands receive `busy` instead of being queued for execution. Reusing the active ID produces a `duplicate_active_id` event, not a second terminal response, including an invalid envelope whose usable ID matches the active request. There is no ID history or replay.
- `new`/`load`/`switch_world`: `loading` events, then `in_world`; `connect`: `connecting`, then `in_world`. Completion occurs only when the player is present, the world is not paused, the screen/overlay is closed, and game ticks are allowed. A loading error requiring interaction produces `load_requires_attention`, cancelled loading produces `load_cancelled`, and a failed connection produces `connection_failed`; there is no false success. `connect` uses the launched client's profile; a development profile does not replace Microsoft authentication. Server resource packs are not accepted automatically.
- `act`: `completed.data` contains `elapsedTicks`, `durationTicks`, `finished`, `start`/`end` with coordinates and slot, `maxY`, `sneakObserved`, `keys` with `pressed` and `queuedPresses` flags, and `released`. An error for a started action also contains the result and evidence of input release in `data`; cancellation does not pretend that all ticks were completed.
- With `capture`, completion arrives **after** the PNG is written. `data.capture` contains `path`, `frame`, `width`, `height`, `tick`, `render_sequence`, and `total_ms`. `action_finished_tick` is the tick when input ended; the capture tick is no earlier than it. The capture uses an already completed game frame and the final interpolation state, not an old framebuffer.
- A path may contain spaces in the JSON string. Relative paths are resolved from the working directory (`fabric/run/` for `runClient`). The successful PNG counter starts at zero in a new process, and matching files are overwritten. A write error does not increment the counter.
- `quit.completed` is sent after client resources have been released. EOF also cancels the active action, releases buttons, and saves the world normally; it has no ID, and a `stopped` event is emitted.

Example of the two responses to an action; additional `data` fields are omitted here:

```json
{"protocol":1,"type":"accepted","id":"walk-1","command":"act","data":{"state":"in_world"}}
{"protocol":1,"type":"completed","id":"walk-1","command":"act","data":{"elapsedTicks":20,"durationTicks":20,"finished":true,"released":true}}
```

**Action duration is measured in game ticks, not milliseconds.** Holding attack/use follows vanilla mechanics: the initial press, then breaking progress or cooldown/item holding. An action does not promise to break any block or use an unavailable item. Server restrictions, collisions, inertia, and game cooldowns remain in effect; movement is not replaced with teleportation. Completion means that client input was performed, not a separate server confirmation of all its effects.

## Protocol and world readiness

The `ready` event means the client accepts requests; it does not guarantee the main menu screen or a ready world. For example, the client may be on `AccessibilityOnboardingScreen`. Check `status.screen`; world readiness is confirmed by the loading/connection terminal response and game state, not by the `ready` event.

Launching the client: [development.md](development.md). MCP transport: [mcp.md](mcp.md).
