package offcraft.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import offcraft.client.mixin.KeyBindingAccessor;

/** Holds ordinary game bindings for a fixed number of eligible client ticks. */
public final class PlayerAction {
    private static final String[] BUTTON_NAMES = {
            "forward", "back", "left", "right", "jump", "sneak", "attack", "use"
    };
    private static final int ATTACK = 1 << 6;
    private static final int USE = 1 << 7;

    private final int durationTicks;
    private final int buttons;
    private final int slot;
    private KeyBinding[] keys;
    private ClientPlayerEntity actionPlayer;
    private boolean started;
    private boolean released;
    private boolean tickPrepared;
    private int elapsedTicks;
    private double startX;
    private double startY;
    private double startZ;
    private int startSlot;
    private double endX;
    private double endY;
    private double endZ;
    private int endSlot;
    private double maxY;
    private boolean sneakObserved;

    private PlayerAction(int durationTicks, int buttons, int slot) {
        this.durationTicks = durationTicks;
        this.buttons = buttons;
        this.slot = slot;
    }

    /** Validates the entire action without touching any client state. */
    public static PlayerAction parse(JsonObject args) {
        if (args == null) throw new IllegalArgumentException("Action arguments are required");
        int ticks = integer(args.get("ticks"), "ticks", 1, 12000);
        int slot = args.has("slot") ? integer(args.get("slot"), "slot", 1, 9) : 0;
        int buttons = 0;
        if (args.has("buttons")) {
            JsonElement value = args.get("buttons");
            if (!value.isJsonArray()) throw new IllegalArgumentException("buttons must be an array of strings");
            for (JsonElement button : value.getAsJsonArray()) {
                if (!button.isJsonPrimitive() || !button.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("buttons must contain only strings");
                }
                buttons |= switch (button.getAsString()) {
                    case "forward" -> 1;
                    case "back" -> 1 << 1;
                    case "left" -> 1 << 2;
                    case "right" -> 1 << 3;
                    case "jump" -> 1 << 4;
                    case "sneak" -> 1 << 5;
                    case "attack" -> ATTACK;
                    case "use" -> USE;
                    default -> throw new IllegalArgumentException("Unknown button: " + button.getAsString());
                };
            }
        }
        return new PlayerAction(ticks, buttons, slot);
    }

    private static int integer(JsonElement value, String name, int minimum, int maximum) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(name + " must be an integer in [" + minimum + ", " + maximum + "]");
        }
        try {
            int number = value.getAsBigDecimal().intValueExact();
            if (number >= minimum && number <= maximum) return number;
        } catch (ArithmeticException | NumberFormatException ignored) {
            // Fractional, non-finite, and overflowing values are invalid, never truncated.
        }
        throw new IllegalArgumentException(name + " must be an integer in [" + minimum + ", " + maximum + "]");
    }

    /** Called on the client thread only after the parent has checked playable state. */
    public void start(MinecraftClient client) {
        if (started) throw new IllegalStateException("Action already started");
        if (client.player == null) throw new IllegalStateException("No player available");
        actionPlayer = client.player;
        var options = client.options;
        keys = new KeyBinding[]{
                options.forwardKey, options.backKey, options.leftKey, options.rightKey,
                options.jumpKey, options.sneakKey, options.attackKey, options.useKey
        };
        startX = actionPlayer.getX();
        startY = actionPlayer.getY();
        startZ = actionPlayer.getZ();
        startSlot = actionPlayer.getInventory().selectedSlot + 1;
        maxY = startY;
        started = true;
        clearKeys();
        if (slot != 0) actionPlayer.getInventory().selectedSlot = slot - 1;
        recordEnd();
    }

    /** Tick HEAD: input handling and the player movement tick must follow this call. */
    public void beforeTick(MinecraftClient client) {
        if (!started) throw new IllegalStateException("Action has not started");
        if (released || finished() || tickPrepared) return;
        for (int i = 0; i < keys.length; i++) {
            boolean pressed = (buttons & (1 << i)) != 0;
            KeyBindingAccessor state = (KeyBindingAccessor) keys[i];
            state.offcraft$setPressed(pressed);
            // Queue exactly one initial click, directly on each logical binding. Holding
            // thereafter uses vanilla breaking progress and the ordinary item-use cooldown.
            state.offcraft$setTimesPressed(elapsedTicks == 0 && pressed && i >= 6 ? 1 : 0);
        }
        tickPrepared = true;
    }

    /** Tick RETURN, and only for a corresponding eligible beforeTick. */
    public void afterTick(MinecraftClient client) {
        if (!tickPrepared || released) return;
        tickPrepared = false;
        elapsedTicks++;
        recordEnd();
        maxY = Math.max(maxY, endY);
        sneakObserved |= actionPlayer.isSneaking();
        if (finished()) release(client);
    }

    public boolean finished() {
        return elapsedTicks == durationTicks;
    }

    public int elapsedTicks() {
        return elapsedTicks;
    }

    public int durationTicks() {
        return durationTicks;
    }

    /** Clears every controlled binding even when a world or screen transition interrupted it. */
    public void release(MinecraftClient client) {
        if (!started || released) return;
        clearKeys();
        released = true;
        tickPrepared = false;
        recordEnd();
        // Do not wait for another input tick: cancellation can happen during a pause,
        // disconnect, or shutdown. Never send interaction cleanup for a different player.
        if (client.player == actionPlayer && client.world != null && client.interactionManager != null) {
            if ((buttons & ATTACK) != 0) client.interactionManager.cancelBlockBreaking();
            if ((buttons & USE) != 0 && actionPlayer.isUsingItem()) {
                client.interactionManager.stopUsingItem(actionPlayer);
            }
        }
    }

    private void clearKeys() {
        for (KeyBinding key : keys) {
            KeyBindingAccessor state = (KeyBindingAccessor) key;
            // Calling StickyKeyBinding.setPressed(false) would retain toggled sneak.
            state.offcraft$setPressed(false);
            state.offcraft$setTimesPressed(0);
        }
    }

    private void recordEnd() {
        endX = actionPlayer.getX();
        endY = actionPlayer.getY();
        endZ = actionPlayer.getZ();
        endSlot = actionPlayer.getInventory().selectedSlot + 1;
    }

    /** Allows the parent's cursor-gating redirect without actually capturing the mouse. */
    public boolean attacking() {
        return started && !released && (buttons & ATTACK) != 0;
    }

    /** Evidence is snapshotted at the last action tick/release, not at later image capture. */
    public JsonObject result() {
        JsonObject result = new JsonObject();
        result.addProperty("elapsedTicks", elapsedTicks);
        result.addProperty("durationTicks", durationTicks);
        result.addProperty("finished", finished());
        result.addProperty("maxY", maxY);
        result.addProperty("sneakObserved", sneakObserved);
        if (started) {
            result.add("start", position(startX, startY, startZ, startSlot));
            result.add("end", position(endX, endY, endZ, endSlot));
        }
        if (slot != 0) result.addProperty("requestedSlot", slot);
        JsonArray heldButtons = new JsonArray();
        JsonObject keyStates = new JsonObject();
        boolean allReleased = released;
        for (int i = 0; i < BUTTON_NAMES.length; i++) {
            if ((buttons & (1 << i)) != 0) heldButtons.add(BUTTON_NAMES[i]);
            if (keys != null) {
                JsonObject state = new JsonObject();
                boolean pressed = keys[i].isPressed();
                int queued = ((KeyBindingAccessor) keys[i]).offcraft$getTimesPressed();
                state.addProperty("pressed", pressed);
                state.addProperty("queuedPresses", queued);
                keyStates.add(BUTTON_NAMES[i], state);
                allReleased &= !pressed && queued == 0;
            }
        }
        result.add("buttons", heldButtons);
        result.add("keys", keyStates);
        result.addProperty("released", allReleased);
        return result;
    }

    private static JsonObject position(double x, double y, double z, int slot) {
        JsonObject position = new JsonObject();
        position.addProperty("x", x);
        position.addProperty("y", y);
        position.addProperty("z", z);
        position.addProperty("slot", slot);
        return position;
    }
}
