package offcraft.client;

import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;

/** Keep the client event/tick loop live; throttle only expensive drawing. */
public final class RenderPolicy {
    private static boolean reduced = true;
    private static int backgroundFps = 2;
    private static long nextDraw;
    private static boolean draw = true;
    private static boolean rendered;
    private static long draws;
    private static long skipped;

    private RenderPolicy() {}

    public static void begin(MinecraftClient client, boolean force) {
        long now = System.nanoTime();
        draw = !reduced || force || client.world == null || client.getOverlay() != null
                || now >= nextDraw;
        rendered = false;
        if (draw) nextDraw = now + 1_000_000_000L / backgroundFps;
    }

    public static boolean draw() { return draw; }
    public static boolean rendered() { return rendered; }
    public static void markRendered() { rendered = true; draws++; }
    public static void skipped() { skipped++; }

    public static int loopFps(MinecraftClient client) {
        // At least 20 client loop iterations/s even with an old low saved FPS limit.
        return reduced ? 30 : Math.max(20, client.getWindow().getFramerateLimit());
    }

    public static JsonObject configure(JsonObject args) {
        var mode = args.get("mode");
        if (mode == null || !mode.isJsonPrimitive() || !mode.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("mode must be normal or reduced");
        }
        boolean next = switch (mode.getAsString()) {
            case "normal" -> false;
            case "reduced" -> true;
            default -> throw new IllegalArgumentException("mode must be normal or reduced");
        };
        int fps = backgroundFps;
        if (args.has("fps")) {
            var value = args.get("fps");
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("fps must be an integer in [1, 60]");
            }
            try { fps = value.getAsBigDecimal().intValueExact(); }
            catch (ArithmeticException | NumberFormatException e) {
                throw new IllegalArgumentException("fps must be an integer in [1, 60]");
            }
            if (fps < 1 || fps > 60) throw new IllegalArgumentException("fps must be in [1, 60]");
        }
        reduced = next;
        backgroundFps = fps;
        nextDraw = 0;
        return status();
    }

    public static JsonObject status() {
        JsonObject result = new JsonObject();
        result.addProperty("mode", reduced ? "reduced" : "normal");
        result.addProperty("background_fps", backgroundFps);
        result.addProperty("draws", draws);
        result.addProperty("skipped", skipped);
        return result;
    }
}
