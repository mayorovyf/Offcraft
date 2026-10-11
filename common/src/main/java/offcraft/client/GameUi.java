package offcraft.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.ParentElement;
import net.minecraft.client.gui.navigation.GuiNavigationType;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import offcraft.client.mixin.ClientGuiInvoker;
import offcraft.client.mixin.CreativeInventoryScreenInvoker;
import offcraft.client.mixin.GuiKeyBindingAccessor;
import offcraft.client.mixin.HandledScreenAccessor;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/** Synchronous client-thread reads and ordinary vanilla screen/interaction callbacks. */
public final class GameUi {
    // No input state survives a callback. Physical modifier queries remain untouched otherwise.
    private static final ThreadLocal<Integer> MODIFIERS = new ThreadLocal<>();

    private GameUi() {}

    /** Called by ScreenModifierMixin; -1 means normal physical input. */
    public static int inputModifiers() {
        Integer value = MODIFIERS.get();
        return value == null ? -1 : value;
    }

    public static JsonObject observe(MinecraftClient client) {
        JsonObject result = new JsonObject();
        result.addProperty("in_world", client.world != null);
        result.addProperty("paused", client.isPaused());
        result.addProperty("focused", client.isWindowFocused());
        result.add("gui", screenState(client));
        if (client.world != null) {
            result.addProperty("dimension", client.world.getRegistryKey().getValue().toString());
            result.addProperty("world_time", client.world.getTime());
            result.addProperty("time_of_day", client.world.getTimeOfDay());
        }
        ClientPlayerEntity player = client.player;
        if (player == null) {
            result.add("player", JsonNull.INSTANCE);
        } else {
            JsonObject state = entityState(player);
            state.addProperty("health", player.getHealth());
            state.addProperty("max_health", player.getMaxHealth());
            state.addProperty("absorption", player.getAbsorptionAmount());
            state.addProperty("armor", player.getArmor());
            state.addProperty("food", player.getHungerManager().getFoodLevel());
            state.addProperty("saturation", player.getHungerManager().getSaturationLevel());
            state.addProperty("exhaustion", player.getHungerManager().getExhaustion());
            state.addProperty("air", player.getAir());
            state.addProperty("max_air", player.getMaxAir());
            state.addProperty("alive", player.isAlive());
            state.addProperty("spectator", player.isSpectator());
            state.addProperty("creative", player.isCreative());
            state.addProperty("on_ground", player.isOnGround());
            state.addProperty("sneaking", player.isSneaking());
            state.addProperty("sprinting", player.isSprinting());
            state.addProperty("in_water", player.isTouchingWater());
            state.addProperty("on_fire", player.isOnFire());
            state.addProperty("using_item", player.isUsingItem());
            state.addProperty("selected_hotbar_slot", player.getInventory().selectedSlot);
            state.add("velocity", vector(player.getVelocity()));
            state.add("main_hand", stack(player.getMainHandStack()));
            state.add("off_hand", stack(player.getOffHandStack()));
            JsonArray effects = new JsonArray();
            for (StatusEffectInstance effect : player.getStatusEffects()) {
                JsonObject entry = new JsonObject();
                entry.addProperty("id", Registries.STATUS_EFFECT.getId(effect.getEffectType().value()).toString());
                entry.addProperty("name", effect.getEffectType().value().getName().getString());
                entry.addProperty("amplifier", effect.getAmplifier());
                entry.addProperty("duration_ticks", effect.getDuration());
                entry.addProperty("infinite", effect.isInfinite());
                entry.addProperty("ambient", effect.isAmbient());
                entry.addProperty("show_particles", effect.shouldShowParticles());
                entry.addProperty("show_icon", effect.shouldShowIcon());
                effects.add(entry);
            }
            state.add("effects", effects);
            result.add("player", state);
        }
        result.add("target", target(client));
        return result;
    }

    public static JsonObject inventory(MinecraftClient client) {
        ClientPlayerEntity player = requirePlayer(client);
        ScreenHandler handler = player.currentScreenHandler;
        JsonObject result = new JsonObject();
        result.addProperty("state", "local");
        result.addProperty("sync_id", handler.syncId);
        result.addProperty("revision", handler.getRevision());
        result.addProperty("handler_type", handler.getClass().getName());
        result.addProperty("selected_hotbar_slot", player.getInventory().selectedSlot);
        result.add("gui", screenState(client));
        HandledScreen<?> screen = client.currentScreen instanceof HandledScreen<?> handled
                && handled.getScreenHandler() == handler ? handled : null;
        result.addProperty("slots_visible", screen != null);
        int left = screen == null ? 0 : ((HandledScreenAccessor) screen).offcraft$getLeft();
        int top = screen == null ? 0 : ((HandledScreenAccessor) screen).offcraft$getTop();
        JsonArray slots = new JsonArray();
        for (Slot slot : handler.slots) {
            JsonObject entry = stack(slot.getStack());
            entry.addProperty("id", slot.id);
            entry.addProperty("index", slot.getIndex());
            entry.addProperty("x", slot.x);
            entry.addProperty("y", slot.y);
            if (screen != null) {
                entry.addProperty("screen_x", left + slot.x);
                entry.addProperty("screen_y", top + slot.y);
                entry.addProperty("click_x", left + slot.x + 8);
                entry.addProperty("click_y", top + slot.y + 8);
            }
            entry.addProperty("inventory", slot.inventory == player.getInventory()
                    ? "player" : slot.inventory.getClass().getName());
            entry.addProperty("enabled", slot.isEnabled());
            entry.addProperty("can_take", slot.canTakeItems(player));
            entry.addProperty("max_count", slot.getMaxItemCount(slot.getStack()));
            slots.add(entry);
        }
        result.add("slots", slots);
        result.add("cursor_stack", stack(handler.getCursorStack()));
        return result;
    }
    public static JsonObject gui(MinecraftClient client, JsonObject args) {
        String action = string(args, "action");
        requireNoOverlay(client);
        switch (action) {
            case "inventory" -> openInventory(client);
            case "close" -> {
                Screen screen = client.currentScreen;
                if (screen != null) screen.close();
                else if (client.player != null
                        && client.player.currentScreenHandler != client.player.playerScreenHandler) {
                    client.player.closeHandledScreen();
                }
            }
            default -> throw new IllegalArgumentException("action must be inventory or close");
        }
        return screenState(client);
    }

    public static JsonObject key(MinecraftClient client, JsonObject args) {
        requireNoOverlay(client);
        int modifiers = modifiers(args);
        JsonElement value = args.get("key");
        String logical = value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString().toUpperCase(Locale.ROOT) : null;
        KeyStroke stroke = keyStroke(client, value, logical);
        Screen screen = client.currentScreen;
        boolean pressed;
        boolean released = false;
        int previous = inputModifiers();
        MODIFIERS.set(modifiers);
        try {
            if (screen != null) {
                client.setNavigationType(stroke.code() == GLFW.GLFW_KEY_TAB
                        ? GuiNavigationType.KEYBOARD_TAB : GuiNavigationType.KEYBOARD_ARROW);
                screen.applyKeyPressNarratorDelay();
                try {
                    pressed = screen.keyPressed(stroke.code(), stroke.scanCode(), modifiers);
                } finally {
                    released = screen.keyReleased(stroke.code(), stroke.scanCode(), modifiers);
                }
            } else {
                requirePlayer(client);
                if ("INVENTORY".equals(logical)
                        || client.options.inventoryKey.matchesKey(stroke.code(), stroke.scanCode())) {
                    openInventory(client);
                } else if ("CHAT".equals(logical)
                        || client.options.chatKey.matchesKey(stroke.code(), stroke.scanCode())) {
                    ((ClientGuiInvoker) client).offcraft$openChatScreen("");
                } else if (stroke.code() == GLFW.GLFW_KEY_ESCAPE) {
                    client.openGameMenu(false);
                } else {
                    throw new IllegalStateException("Without a screen, only inventory, chat and escape are supported; use act for gameplay");
                }
                pressed = true;
            }
        } finally {
            restoreModifiers(previous);
        }
        JsonObject result = screenState(client);
        result.addProperty("key_code", stroke.code());
        result.addProperty("scan_code", stroke.scanCode());
        result.addProperty("modifiers", modifiers);
        result.addProperty("press_consumed", pressed);
        result.addProperty("release_consumed", released);
        return result;
    }

    public static JsonObject click(MinecraftClient client, JsonObject args) {
        Screen screen = requireScreen(client);
        double x = number(args, "x");
        double y = number(args, "y");
        if (x < 0 || y < 0 || x >= screen.width || y >= screen.height) {
            throw new IllegalArgumentException("x and y must be within the scaled screen dimensions");
        }
        int button = args.has("button") ? integer(args, "button", 0, 2) : 0;
        int modifiers = modifiers(args);
        boolean pressed;
        boolean released;
        int previous = inputModifiers();
        MODIFIERS.set(modifiers);
        try {
            client.setNavigationType(GuiNavigationType.MOUSE);
            screen.mouseMoved(x, y);
            screen.applyMousePressScrollNarratorDelay();
            try {
                pressed = screen.mouseClicked(x, y, button);
            } finally {
                released = screen.mouseReleased(x, y, button);
            }
        } finally {
            restoreModifiers(previous);
        }
        JsonObject result = screenState(client);
        result.addProperty("x", x);
        result.addProperty("y", y);
        result.addProperty("button", button);
        result.addProperty("modifiers", modifiers);
        result.addProperty("press_consumed", pressed);
        result.addProperty("release_consumed", released);
        return result;
    }

    public static JsonObject text(MinecraftClient client, JsonObject args) {
        Screen screen = requireScreen(client);
        String text = string(args, "text");
        int consumed = 0;
        int previous = inputModifiers();
        MODIFIERS.set(0);
        try {
            // Keyboard.onChar emits the same surrogate pair callbacks for supplementary Unicode.
            for (int i = 0; i < text.length(); i++) {
                if (client.currentScreen != screen) {
                    throw new IllegalStateException("Screen changed during text input");
                }
                if (screen.charTyped(text.charAt(i), 0)) consumed++;
            }
        } finally {
            restoreModifiers(previous);
        }
        JsonObject result = screenState(client);
        result.addProperty("submitted_utf16_units", text.length());
        result.addProperty("consumed_utf16_units", consumed);
        return result;
    }

    private static void restoreModifiers(int previous) {
        if (previous < 0) MODIFIERS.remove();
        else MODIFIERS.set(previous);
    }

    public static JsonObject clickSlot(MinecraftClient client, JsonObject args) {
        ClientPlayerEntity player = requirePlayer(client);
        requireNoOverlay(client);
        if (client.interactionManager == null) throw new IllegalStateException("No interaction manager");
        ScreenHandler handler = player.currentScreenHandler;
        int syncId = integer(args, "sync_id", 0, Integer.MAX_VALUE);
        if (syncId != handler.syncId) {
            throw new IllegalArgumentException("Stale sync_id: current handler is " + handler.syncId);
        }
        String name = string(args, "action");
        SlotActionType action = switch (name) {
            case "pickup" -> SlotActionType.PICKUP;
            case "quick_move" -> SlotActionType.QUICK_MOVE;
            case "swap" -> SlotActionType.SWAP;
            case "throw" -> SlotActionType.THROW;
            case "pickup_all" -> SlotActionType.PICKUP_ALL;
            default -> throw new IllegalArgumentException("Unsupported slot action: " + name);
        };
        int slotId = integer(args, "slot", -999, Integer.MAX_VALUE);
        if (slotId == -999 && action == SlotActionType.PICKUP) {
            // Vanilla's explicit outside-container cursor drop.
        } else {
            if (slotId < 0 || slotId >= handler.slots.size()) {
                throw new IllegalArgumentException("slot must be a current screen handler slot ID");
            }
            Slot slot = handler.slots.get(slotId);
            if (slot.id != slotId || !slot.isEnabled()) {
                throw new IllegalArgumentException("slot ID is unavailable in the current handler");
            }
        }
        int button = integer(args, "button", 0, action == SlotActionType.SWAP ? 40 : 1);
        if (action == SlotActionType.SWAP && button > 8 && button != 40) {
            throw new IllegalArgumentException("swap button must be hotbar index 0..8 or offhand 40");
        }
        if (client.currentScreen != null && !(client.currentScreen instanceof HandledScreen<?>)) {
            throw new IllegalStateException("Close the non-inventory screen before clicking slots");
        }
        if (client.currentScreen instanceof CreativeInventoryScreen screen) {
            CreativeInventoryScreenInvoker creative = (CreativeInventoryScreenInvoker) screen;
            boolean previousOutside = creative.offcraft$wasLastClickOutsideBounds();
            try {
                creative.offcraft$setLastClickOutsideBounds(slotId == -999);
                creative.offcraft$onMouseClick(slotId == -999 ? null : handler.slots.get(slotId),
                        slotId, button, action);
            } finally {
                creative.offcraft$setLastClickOutsideBounds(previousOutside);
            }
        } else {
            // Ordinary handlers use vanilla prediction and ClickSlotC2SPacket.
            client.interactionManager.clickSlot(syncId, slotId, button, action, player);
        }
        JsonObject result = inventory(client);
        result.addProperty("sent", true);
        result.addProperty("server_confirmed", false);
        result.addProperty("slot", slotId);
        result.addProperty("button", button);
        result.addProperty("action", name);
        return result;
    }

    private static void openInventory(MinecraftClient client) {
        ClientPlayerEntity player = requirePlayer(client);
        if (!player.isAlive() || player.isSleeping()) {
            throw new IllegalStateException("Inventory cannot open while dead or sleeping");
        }
        if (client.currentScreen != null) throw new IllegalStateException("Close the current screen first");
        if (client.interactionManager == null) throw new IllegalStateException("No interaction manager");
        if (client.interactionManager.hasRidingInventory()) {
            player.openRidingInventory();
        } else {
            client.getTutorialManager().onInventoryOpened();
            client.setScreen(new InventoryScreen(player));
        }
    }

    private static ClientPlayerEntity requirePlayer(MinecraftClient client) {
        if (client.player == null || client.world == null) throw new IllegalStateException("No player in a world");
        return client.player;
    }

    private static void requireNoOverlay(MinecraftClient client) {
        if (client.getOverlay() != null) throw new IllegalStateException("A loading overlay is active");
    }

    private static Screen requireScreen(MinecraftClient client) {
        requireNoOverlay(client);
        if (client.currentScreen == null) throw new IllegalStateException("No GUI screen is open");
        return client.currentScreen;
    }

    private record KeyStroke(int code, int scanCode) {}

    private static KeyStroke keyStroke(MinecraftClient client, JsonElement value, String logical) {
        if (logical != null) {
            if (logical.equals("INVENTORY")) return boundKey(client.options.inventoryKey);
            if (logical.equals("CHAT")) return boundKey(client.options.chatKey);
            int code = switch (logical) {
                case "ESCAPE" -> GLFW.GLFW_KEY_ESCAPE;
                case "ENTER" -> GLFW.GLFW_KEY_ENTER;
                case "TAB" -> GLFW.GLFW_KEY_TAB;
                case "BACKSPACE" -> GLFW.GLFW_KEY_BACKSPACE;
                case "LEFT" -> GLFW.GLFW_KEY_LEFT;
                case "RIGHT" -> GLFW.GLFW_KEY_RIGHT;
                case "UP" -> GLFW.GLFW_KEY_UP;
                case "DOWN" -> GLFW.GLFW_KEY_DOWN;
                case "DELETE" -> GLFW.GLFW_KEY_DELETE;
                case "HOME" -> GLFW.GLFW_KEY_HOME;
                case "END" -> GLFW.GLFW_KEY_END;
                case "PAGE_UP" -> GLFW.GLFW_KEY_PAGE_UP;
                case "PAGE_DOWN" -> GLFW.GLFW_KEY_PAGE_DOWN;
                case "SPACE" -> GLFW.GLFW_KEY_SPACE;
                default -> throw new IllegalArgumentException("Unknown key name: " + logical);
            };
            return new KeyStroke(code, GLFW.glfwGetKeyScancode(code));
        }
        int code = integer(value, "key", GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_LAST);
        if (!validGlfwKey(code)) throw new IllegalArgumentException("key is a reserved GLFW key code");
        return new KeyStroke(code, GLFW.glfwGetKeyScancode(code));
    }

    private static KeyStroke boundKey(KeyBinding binding) {
        InputUtil.Key key = ((GuiKeyBindingAccessor) binding).offcraft$getBoundKey();
        if (key.getCategory() == InputUtil.Type.SCANCODE) return new KeyStroke(GLFW.GLFW_KEY_UNKNOWN, key.getCode());
        if (key.getCategory() != InputUtil.Type.KEYSYM || key.getCode() == GLFW.GLFW_KEY_UNKNOWN) {
            throw new IllegalStateException("Logical GUI binding is not assigned to a keyboard key");
        }
        return new KeyStroke(key.getCode(), GLFW.glfwGetKeyScancode(key.getCode()));
    }

    private static boolean validGlfwKey(int code) {
        return code == 32 || code == 39 || code >= 44 && code <= 57 || code == 59 || code == 61
                || code >= 65 && code <= 93 || code == 96 || code == 161 || code == 162
                || code >= 256 && code <= 269 || code >= 280 && code <= 284
                || code >= 290 && code <= 314 || code >= 320 && code <= 336
                || code >= 340 && code <= 348;
    }

    private static int modifiers(JsonObject args) {
        return args.has("modifiers") ? integer(args, "modifiers", 0, 15) : 0;
    }

    private static String string(JsonObject args, String field) {
        JsonElement value = args.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(field + " must be a string");
        }
        return value.getAsString();
    }

    private static int integer(JsonObject args, String field, int minimum, int maximum) {
        return integer(args.get(field), field, minimum, maximum);
    }

    private static int integer(JsonElement value, String field, int minimum, int maximum) {
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            try {
                int number = value.getAsBigDecimal().intValueExact();
                if (number >= minimum && number <= maximum) return number;
            } catch (ArithmeticException | NumberFormatException ignored) {
                // Fractional, non-finite, and overflowing values must never be truncated.
            }
        }
        throw new IllegalArgumentException(field + " must be an integer in [" + minimum + ", " + maximum + "]");
    }

    private static double number(JsonObject args, String field) {
        JsonElement value = args.get(field);
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            double number = value.getAsDouble();
            if (Double.isFinite(number)) return number;
        }
        throw new IllegalArgumentException(field + " must be a finite number");
    }

    private static JsonObject screenState(MinecraftClient client) {
        JsonObject result = new JsonObject();
        Screen screen = client.currentScreen;
        result.addProperty("open", screen != null);
        result.addProperty("type", screen == null ? null : screen.getClass().getName());
        result.addProperty("title", screen == null ? null : screen.getTitle().getString());
        result.addProperty("width", screen == null ? client.getWindow().getScaledWidth() : screen.width);
        result.addProperty("height", screen == null ? client.getWindow().getScaledHeight() : screen.height);
        result.addProperty("framebuffer_width", client.getWindow().getFramebufferWidth());
        result.addProperty("framebuffer_height", client.getWindow().getFramebufferHeight());
        result.addProperty("scale_factor", client.getWindow().getScaleFactor());
        result.addProperty("coordinate_space", "scaled_gui_pixels");
        result.addProperty("overlay", client.getOverlay() != null);
        if (screen instanceof HandledScreen<?> handled) {
            HandledScreenAccessor layout = (HandledScreenAccessor) handled;
            result.addProperty("container_x", layout.offcraft$getLeft());
            result.addProperty("container_y", layout.offcraft$getTop());
            result.addProperty("container_width", layout.offcraft$getBackgroundWidth());
            result.addProperty("container_height", layout.offcraft$getBackgroundHeight());
            result.addProperty("screen_sync_id", handled.getScreenHandler().syncId);
            result.addProperty("screen_handler_type", handled.getScreenHandler().getClass().getName());
            result.addProperty("matches_player_handler", client.player != null
                    && handled.getScreenHandler() == client.player.currentScreenHandler);
        }
        JsonArray fields = new JsonArray();
        if (screen != null) collectTextFields(screen, fields);
        result.add("text_fields", fields);
        return result;
    }

    private static void collectTextFields(ParentElement parent, JsonArray fields) {
        for (Element child : parent.children()) {
            if (child instanceof TextFieldWidget field) {
                JsonObject entry = new JsonObject();
                entry.addProperty("text", field.getText());
                entry.addProperty("focused", field.isFocused());
                entry.addProperty("active", field.isActive());
                entry.addProperty("visible", field.isVisible());
                entry.addProperty("cursor", field.getCursor());
                entry.addProperty("x", field.getX());
                entry.addProperty("y", field.getY());
                entry.addProperty("width", field.getWidth());
                entry.addProperty("height", field.getHeight());
                fields.add(entry);
            }
            if (child instanceof ParentElement nested) collectTextFields(nested, fields);
        }
    }

    private static JsonObject stack(ItemStack stack) {
        JsonObject result = new JsonObject();
        result.addProperty("empty", stack.isEmpty());
        result.addProperty("item", Registries.ITEM.getId(stack.getItem()).toString());
        result.addProperty("count", stack.getCount());
        result.addProperty("name", stack.getName().getString());
        result.addProperty("max_count", stack.getMaxCount());
        result.addProperty("damageable", stack.isDamageable());
        result.addProperty("damage", stack.getDamage());
        result.addProperty("max_damage", stack.getMaxDamage());
        result.addProperty("enchanted", stack.hasEnchantments());
        result.addProperty("glint", stack.hasGlint());
        return result;
    }

    private static JsonObject entityState(Entity entity) {
        JsonObject result = new JsonObject();
        result.addProperty("id", entity.getId());
        result.addProperty("uuid", entity.getUuidAsString());
        result.addProperty("type", Registries.ENTITY_TYPE.getId(entity.getType()).toString());
        result.addProperty("name", entity.getName().getString());
        result.add("position", vector(entity.getPos()));
        JsonObject rotation = new JsonObject();
        rotation.addProperty("yaw", entity.getYaw());
        rotation.addProperty("pitch", entity.getPitch());
        result.add("rotation", rotation);
        Box bounds = entity.getBoundingBox();
        JsonObject box = new JsonObject();
        box.add("min", vector(bounds.minX, bounds.minY, bounds.minZ));
        box.add("max", vector(bounds.maxX, bounds.maxY, bounds.maxZ));
        result.add("bounds", box);
        if (entity instanceof LivingEntity living) {
            result.addProperty("health", living.getHealth());
            result.addProperty("max_health", living.getMaxHealth());
        }
        return result;
    }

    private static JsonObject target(MinecraftClient client) {
        JsonObject result = new JsonObject();
        HitResult hit = client.crosshairTarget;
        if (hit == null) {
            result.addProperty("type", "unavailable");
            return result;
        }
        result.addProperty("type", hit.getType().name().toLowerCase(Locale.ROOT));
        result.add("hit_position", vector(hit.getPos()));
        if (client.player != null) {
            result.addProperty("distance", client.player.getEyePos().distanceTo(hit.getPos()));
        }
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult block && client.world != null) {
            BlockPos position = block.getBlockPos();
            JsonObject blockPosition = new JsonObject();
            blockPosition.addProperty("x", position.getX());
            blockPosition.addProperty("y", position.getY());
            blockPosition.addProperty("z", position.getZ());
            result.add("block_position", blockPosition);
            result.addProperty("face", block.getSide().getName());
            result.addProperty("inside_block", block.isInsideBlock());
            BlockState state = client.world.getBlockState(position);
            result.addProperty("block", Registries.BLOCK.getId(state.getBlock()).toString());
            JsonObject properties = new JsonObject();
            state.getEntries().forEach((property, value) -> properties.addProperty(property.getName(), value.toString()));
            result.add("properties", properties);
        } else if (hit.getType() == HitResult.Type.ENTITY && hit instanceof EntityHitResult entity) {
            result.add("entity", entityState(entity.getEntity()));
        }
        return result;
    }

    private static JsonObject vector(Vec3d vector) {
        return vector(vector.x, vector.y, vector.z);
    }

    private static JsonObject vector(double x, double y, double z) {
        JsonObject result = new JsonObject();
        result.addProperty("x", x);
        result.addProperty("y", y);
        result.addProperty("z", z);
        return result;
    }

}
