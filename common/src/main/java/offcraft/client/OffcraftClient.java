package offcraft.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/** All command execution, game input and capture stay on Minecraft's render thread. */
public final class OffcraftClient {
    private record InputLine(String line) {}
    private static final BlockingQueue<InputLine> COMMANDS = new ArrayBlockingQueue<>(32);
    private static boolean ready;
    private static boolean dispatching;
    private static boolean stopping;
    private static boolean actionTick;
    private static String state = "starting";
    private static long frames;
    private static long ticks;
    private static long renderSequence;
    private static long actionFinishedTick;
    private static CommandProtocol.Request active;
    private static CommandProtocol.Request quitRequest;
    private static PlayerAction action;
    private static Path capture;
    private static NativeImage image;
    private static JsonObject pendingResult;

    private OffcraftClient() {}

    public static void initialize() {
        Thread input = new Thread(() -> {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) COMMANDS.put(new InputLine(line));
            } catch (Exception e) {
                System.err.println("offcraft stdin error: " + e.getMessage());
            } finally {
                try {
                    COMMANDS.put(new InputLine(null));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "offcraft-stdin");
        input.setDaemon(true);
        input.start();
    }

    public static void beforeFrame(MinecraftClient client) {
        client.options.pauseOnLostFocus = false;
        if (dispatching || stopping) return;
        if (!ready) {
            if (client.getOverlay() != null) return;
            ready = true;
            state = "menu";
            CommandProtocol.event("ready", status(client));
        }
        if (action != null && !action.finished() && !playable(client)) {
            failActive(client, "interrupted", "World, screen or pause state interrupted the action");
        }
        InputLine input = COMMANDS.poll();
        if (input == null) return;
        if (input.line() == null) {
            stopRequested(client);
            client.scheduleStop();
            return;
        }
        if (input.line().isBlank()) return;
        CommandProtocol.Request request;
        try {
            request = CommandProtocol.parse(input.line());
        } catch (CommandProtocol.ParseException e) {
            if (duplicateActiveId(e.id())) return;
            CommandProtocol.error(e.id() == null ? null : new CommandProtocol.Request(e.id(), null, null),
                    "invalid_request", e.getMessage(), null);
            return;
        }
        if (duplicateActiveId(request.id())) return;
        CommandProtocol.accepted(request, status(client));
        if (active != null && !request.command().equals("status") && !request.command().equals("help")
                && !request.command().equals("observe") && !request.command().equals("inventory")
                && !request.command().equals("cancel") && !request.command().equals("quit")) {
            CommandProtocol.error(request, "busy", "Wait for completion of request " + active.id(), null);
            return;
        }
        dispatching = true;
        try {
            switch (request.command()) {
                case "status" -> CommandProtocol.completed(request, status(client));
                case "observe" -> CommandProtocol.completed(request, GameUi.observe(client));
                case "inventory" -> CommandProtocol.completed(request, GameUi.inventory(client));
                case "render_policy" -> CommandProtocol.completed(request, RenderPolicy.configure(request.args()));
                case "gui", "key", "click", "text", "click_slot" -> {
                    JsonObject result = switch (request.command()) {
                        case "gui" -> GameUi.gui(client, request.args());
                        case "key" -> GameUi.key(client, request.args());
                        case "click" -> GameUi.click(client, request.args());
                        case "text" -> GameUi.text(client, request.args());
                        default -> GameUi.clickSlot(client, request.args());
                    };
                    active = request;
                    pendingResult = result;
                }
                case "cancel" -> {
                    if (request.args().has("target") && (active == null
                            || !string(request.args(), "target").equals(active.id()))) {
                        throw new IllegalArgumentException("target does not match the active request");
                    }
                    if (action == null) throw new IllegalStateException("Only an active act can be cancelled");
                    String target = active.id();
                    failActive(client, "cancelled", "Action cancelled; held inputs released");
                    JsonObject result = status(client);
                    result.addProperty("cancelled_id", target);
                    CommandProtocol.completed(request, result);
                }
                case "disconnect" -> {
                    active = request;
                    disconnectWorld(client);
                    pendingResult = status(client);
                    pendingResult.remove("active_id");
                }
                case "connect" -> {
                    requireMenu(client);
                    String address = string(request.args(), "address");
                    if (!ServerAddress.isValid(address)) throw new IllegalArgumentException("Invalid server address");
                    active = request;
                    setState(client, "connecting");
                    ServerInfo info = new ServerInfo("Offcraft", address, ServerInfo.ServerType.OTHER);
                    info.setResourcePackPolicy(ServerInfo.ResourcePackPolicy.DISABLED);
                    ConnectScreen.connect(new TitleScreen(), client, ServerAddress.parse(address), info, false, null);
                }
                case "switch_world" -> {
                    String name = worldName(request.args());
                    boolean create = booleanValue(request.args(), "create", false);
                    checkWorldFolder(client, name, create);
                    active = request;
                    disconnectWorld(client);
                    startWorld(client, name, create);
                }
                case "help" -> {
                    JsonObject data = new JsonObject();
                    data.addProperty("commands", "new, load, switch_world, connect, disconnect, cancel, look, act, render, render_policy, status, observe, inventory, gui, key, click, text, click_slot, help, quit");
                    data.addProperty("buttons", "forward, back, left, right, jump, sneak, attack, use");
                    data.addProperty("ticks", "1..12000");
                    data.addProperty("slot", "1..9");
                    CommandProtocol.completed(request, data);
                }
                case "quit" -> {
                    quitRequest = request;
                    stopRequested(client);
                    client.scheduleStop();
                }
                case "look" -> {
                    requirePlayable(client);
                    float yaw = number(request.args(), "yaw");
                    float pitch = number(request.args(), "pitch");
                    if (pitch < -90 || pitch > 90) throw new IllegalArgumentException("pitch must be in [-90, 90]");
                    client.player.setYaw(yaw);
                    client.player.setPitch(pitch);
                    client.player.prevYaw = yaw;
                    client.player.prevPitch = pitch;
                    CommandProtocol.completed(request, status(client));
                }
                case "render" -> {
                    Path output = outputPath(client, request.args(), "path");
                    active = request;
                    capture = output;
                }
                case "act" -> {
                    requirePlayable(client);
                    PlayerAction next = PlayerAction.parse(request.args());
                    Path output = request.args().has("capture") ? outputPath(client, request.args(), "capture") : null;
                    active = request;
                    action = next;
                    capture = output;
                    next.start(client);
                }
                case "new", "load" -> {
                    requireMenu(client);
                    String name = worldName(request.args());
                    boolean create = request.command().equals("new");
                    checkWorldFolder(client, name, create);
                    active = request;
                    startWorld(client, name, create);
                }
                default -> throw new IllegalArgumentException("Unknown command: " + request.command());
            }
        } catch (Exception e) {
            if (active == request) failActive(client, "command_failed", e.getMessage());
            else CommandProtocol.error(request, "invalid_command", e.getMessage(), null);
        } finally {
            dispatching = false;
        }
    }

    private static boolean duplicateActiveId(String id) {
        if (active == null || !active.id().equals(id)) return false;
        // Invalid envelopes must not terminate an unrelated active operation either.
        JsonObject data = new JsonObject();
        data.addProperty("id", id);
        CommandProtocol.event("duplicate_active_id", data);
        return true;
    }

    public static void beforeTick(MinecraftClient client) {
        actionTick = false;
        if (stopping || action == null || action.finished()) return;
        if (!playable(client)) {
            failActive(client, "interrupted", "Action requires an unpaused world with no open screen");
            return;
        }
        action.beforeTick(client);
        actionTick = true;
    }

    public static void afterTick(MinecraftClient client) {
        ticks++;
        if (!actionTick || action == null) return;
        actionTick = false;
        action.afterTick(client);
        if (action.finished()) actionFinishedTick = ticks;
    }

    public static boolean attacking() {
        return action != null && !action.finished() && action.attacking();
    }

    public static boolean forceDraw() {
        return !ready || dispatching || capture != null && (action == null || action.finished())
                || pendingResult != null || active != null && action == null;
    }

    /** Captures action end-state, not a partial interpolation of the previous tick. */
    public static RenderTickCounter captureTickCounter(RenderTickCounter original) {
        return action != null && action.finished() && capture != null ? RenderTickCounter.ONE : original;
    }

    public static void afterFrame(MinecraftClient client) {
        if (dispatching || stopping) return;
        if (RenderPolicy.rendered()) renderSequence++;
        if (active == null) return;
        if (active.command().equals("new") || active.command().equals("load")
                || active.command().equals("switch_world") || active.command().equals("connect")) {
            if (playable(client)) {
                setState(client, "in_world");
                JsonObject data = status(client);
                data.remove("active_id");
                completeActive(data);
            } else if (client.getOverlay() == null && client.currentScreen != null) {
                String screen = client.currentScreen.getClass().getSimpleName();
                if (client.currentScreen instanceof DisconnectedScreen disconnected) {
                    failActive(client, "connection_failed", disconnected.getNarratedTitle().getString());
                } else if (!screen.equals("MessageScreen") && !screen.equals("LevelLoadingScreen")
                        && !screen.equals("DownloadingTerrainScreen") && !screen.equals("ProgressScreen")
                        && !screen.equals("ConnectScreen") && !screen.equals("ReconfiguringScreen")) {
                    failActive(client, "load_requires_attention", "World loading stopped at screen " + screen);
                }
            }
            return;
        }
        if (action != null && !action.finished()) return;
        if (action != null && (client.world == null || client.player == null)) {
            failActive(client, "interrupted", "World disappeared before the action result frame");
            return;
        }
        if (!RenderPolicy.rendered()) return;
        JsonObject data = pendingResult != null ? pendingResult : action == null ? status(client) : action.result();
        data.addProperty("tick", ticks);
        data.addProperty("render_sequence", renderSequence);
        if (action != null) data.addProperty("action_finished_tick", actionFinishedTick);
        try {
            if (capture != null) data.add("capture", saveFrame(client, capture));
            completeActive(data);
        } catch (Exception e) {
            failActive(client, "capture_failed", e.getMessage());
        }
    }

    private static JsonObject saveFrame(MinecraftClient client, Path requested) throws Exception {
        Path output = requested.toAbsolutePath().normalize();
        long started = System.nanoTime();
        var framebuffer = client.getFramebuffer();
        if (image == null || image.getWidth() != framebuffer.textureWidth || image.getHeight() != framebuffer.textureHeight) {
            closeImage();
            image = new NativeImage(framebuffer.textureWidth, framebuffer.textureHeight, false);
        }
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            framebuffer.beginRead();
            image.loadFromTextureImage(0, true);
            image.mirrorVertically();
        } finally {
            RenderSystem.bindTexture(previousTexture);
        }
        Files.createDirectories(output.getParent());
        image.writeTo(output);
        JsonObject data = new JsonObject();
        data.addProperty("frame", ++frames);
        data.addProperty("path", output.toString());
        data.addProperty("width", image.getWidth());
        data.addProperty("height", image.getHeight());
        data.addProperty("tick", ticks);
        data.addProperty("render_sequence", renderSequence);
        data.addProperty("total_ms", (System.nanoTime() - started) / 1_000_000.0);
        return data;
    }

    public static void stopRequested(MinecraftClient client) {
        if (stopping) return;
        stopping = true;
        failActive(client, "cancelled", "Client stopping; held inputs released");
        setState(client, "stopping");
    }

    public static void closed(MinecraftClient client) {
        stopRequested(client);
        closeImage();
        state = "stopped";
        if (quitRequest != null) {
            JsonObject data = new JsonObject();
            data.addProperty("state", state);
            CommandProtocol.completed(quitRequest, data);
            quitRequest = null;
        }
        JsonObject data = new JsonObject();
        data.addProperty("state", state);
        CommandProtocol.event("stopped", data);
    }

    private static void completeActive(JsonObject data) {
        CommandProtocol.Request request = active;
        active = null;
        action = null;
        pendingResult = null;
        capture = null;
        CommandProtocol.completed(request, data);
    }

    private static void failActive(MinecraftClient client, String code, String message) {
        if (active == null) return;
        CommandProtocol.Request request = active;
        if (action != null) action.release(client);
        JsonObject data = action == null ? null : action.result();
        active = null;
        action = null;
        capture = null;
        pendingResult = null;
        actionTick = false;
        if (state.equals("loading") || state.equals("connecting")) setState(client, "error");
        CommandProtocol.error(request, code, message == null ? code : message, data);
    }

    private static void closeImage() {
        if (image != null) {
            image.close();
            image = null;
        }
    }

    private static boolean playable(MinecraftClient client) {
        return client.world != null && client.player != null && client.interactionManager != null
                && client.currentScreen == null && client.getOverlay() == null && !client.isPaused()
                && client.world.getTickManager().shouldTick() && !client.player.isDead() && !client.player.isSleeping();
    }

    private static void requirePlayable(MinecraftClient client) {
        if (!playable(client)) throw new IllegalStateException("Wait for an unpaused world with screen=none");
    }

    private static void requireMenu(MinecraftClient client) {
        if (client.world != null || client.getServer() != null || client.getOverlay() != null) {
            throw new IllegalStateException("World loading requires the main menu");
        }
    }
    private static String worldName(JsonObject args) {
        String name = string(args, "world");
        if (!name.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("Invalid world folder name");
        return name;
    }

    private static boolean booleanValue(JsonObject args, String name, boolean fallback) {
        if (!args.has(name)) return fallback;
        var value = args.get(name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(name + " must be boolean");
        }
        return value.getAsBoolean();
    }

    private static void checkWorldFolder(MinecraftClient client, String name, boolean create) {
        Path folder = client.runDirectory.toPath().resolve("saves").resolve(name);
        if (create && Files.exists(folder)) throw new IllegalArgumentException("World already exists; use load");
        if (!create && !Files.isDirectory(folder)) throw new IllegalArgumentException("World not found: " + name);
    }

    private static void startWorld(MinecraftClient client, String name, boolean create) {
        setState(client, "loading");
        if (create) {
            LevelInfo info = new LevelInfo(name, GameMode.CREATIVE, false, Difficulty.PEACEFUL,
                    true, new GameRules(), DataConfiguration.SAFE_MODE);
            client.createIntegratedServerLoader().createAndStart(name, info, new GeneratorOptions(0L, true, false),
                    registries -> registries.get(RegistryKeys.WORLD_PRESET)
                            .getOrThrow(WorldPresets.DEFAULT).createDimensionsRegistryHolder(), client.currentScreen);
        } else {
            client.createIntegratedServerLoader().start(name,
                    () -> failActive(client, "load_cancelled", "World loading cancelled"));
        }
    }

    private static void disconnectWorld(MinecraftClient client) {
        setState(client, "disconnecting");
        if (client.currentScreen instanceof ConnectScreen) {
            throw new IllegalStateException("Wait for the connection request to finish");
        }
        if (client.world != null) client.world.disconnect();
        client.disconnect();
        client.setScreen(new TitleScreen());
        setState(client, "menu");
    }


    private static String string(JsonObject args, String name) {
        JsonElement value = args.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || value.getAsString().isBlank()) {
            throw new IllegalArgumentException(name + " must be a nonempty string");
        }
        return value.getAsString();
    }

    private static float number(JsonObject args, String name) {
        JsonElement value = args.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(name + " must be a finite number");
        }
        float result = value.getAsFloat();
        if (!Float.isFinite(result)) throw new IllegalArgumentException(name + " must be finite");
        return result;
    }

    private static Path outputPath(MinecraftClient client, JsonObject args, String name) {
        return args.has(name) ? Path.of(string(args, name))
                : client.runDirectory.toPath().resolve("output/frame-%04d.png".formatted(frames + 1));
    }

    private static void setState(MinecraftClient client, String next) {
        if (state.equals(next)) return;
        state = next;
        CommandProtocol.event("state", status(client));
    }

    private static JsonObject status(MinecraftClient client) {
        JsonObject data = new JsonObject();
        long handle = client.getWindow().getHandle();
        data.addProperty("state", state);
        data.addProperty("pid", ProcessHandle.current().pid());
        data.addProperty("visible", GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_VISIBLE) == GLFW.GLFW_TRUE);
        data.addProperty("focused", GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE);
        data.addProperty("paused", client.isPaused());
        data.addProperty("world", client.world != null);
        data.addProperty("time", client.world == null ? -1 : client.world.getTime());
        data.addProperty("frames", frames);
        data.addProperty("tick", ticks);
        data.addProperty("render_sequence", renderSequence);
        data.add("render_policy", RenderPolicy.status());
        data.addProperty("width", client.getFramebuffer().textureWidth);
        data.addProperty("height", client.getFramebuffer().textureHeight);
        data.addProperty("screen", client.currentScreen == null ? "none" : client.currentScreen.getClass().getSimpleName());
        if (active != null) data.addProperty("active_id", active.id());
        if (action != null) {
            data.addProperty("elapsed_ticks", action.elapsedTicks());
            data.addProperty("duration_ticks", action.durationTicks());
        }
        if (client.player != null) {
            JsonObject position = new JsonObject();
            position.addProperty("x", client.player.getX());
            position.addProperty("y", client.player.getY());
            position.addProperty("z", client.player.getZ());
            data.add("position", position);
            data.addProperty("yaw", client.player.getYaw());
            data.addProperty("pitch", client.player.getPitch());
            data.addProperty("slot", client.player.getInventory().selectedSlot + 1);
        }
        return data;
    }
}
