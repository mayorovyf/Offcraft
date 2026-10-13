package offcraft.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** On-demand save discovery and creation-only settings. Never opens or edits existing saves. */
final class WorldCatalog {
    private static final int LIMIT = 256;

    record Settings(long seed, GameMode gameMode, Difficulty difficulty) {}

    private WorldCatalog() {}

    static Settings settings(JsonObject args, boolean create) {
        if (!create) {
            if (args.has("seed") || args.has("game_mode") || args.has("difficulty")) {
                throw new IllegalArgumentException("World creation settings require new or create:true");
            }
            return null;
        }
        String seedText = string(args, "seed", "0");
        if (!seedText.matches("-?[0-9]{1,19}")) {
            throw new IllegalArgumentException("seed must be a signed 64-bit decimal string");
        }
        long seed;
        try {
            seed = Long.parseLong(seedText);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("seed must fit a signed 64-bit integer");
        }
        GameMode mode = switch (string(args, "game_mode", "creative")) {
            case "survival" -> GameMode.SURVIVAL;
            case "creative" -> GameMode.CREATIVE;
            case "adventure" -> GameMode.ADVENTURE;
            case "spectator" -> GameMode.SPECTATOR;
            default -> throw new IllegalArgumentException("game_mode must be survival, creative, adventure or spectator");
        };
        Difficulty difficulty = switch (string(args, "difficulty", "peaceful")) {
            case "peaceful" -> Difficulty.PEACEFUL;
            case "easy" -> Difficulty.EASY;
            case "normal" -> Difficulty.NORMAL;
            case "hard" -> Difficulty.HARD;
            default -> throw new IllegalArgumentException("difficulty must be peaceful, easy, normal or hard");
        };
        return new Settings(seed, mode, difficulty);
    }

    static JsonObject list(MinecraftClient client) throws IOException {
        JsonObject result = new JsonObject();
        JsonArray worlds = new JsonArray();
        boolean truncated = false;
        Path saves = client.runDirectory.toPath().resolve("saves");
        if (Files.isDirectory(saves)) {
            try (var entries = Files.newDirectoryStream(saves)) {
                for (Path entry : entries) {
                    if (!Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)
                            || !(Files.isRegularFile(entry.resolve("level.dat"), LinkOption.NOFOLLOW_LINKS)
                            || Files.isRegularFile(entry.resolve("level.dat_old"), LinkOption.NOFOLLOW_LINKS))) continue;
                    if (worlds.size() == LIMIT) {
                        truncated = true;
                        break;
                    }
                    JsonObject world = new JsonObject();
                    String name = entry.getFileName().toString();
                    world.addProperty("world", name);
                    world.addProperty("can_load", name.matches("[A-Za-z0-9_-]+"));
                    worlds.add(world);
                }
            }
        }
        result.add("worlds", worlds);
        result.addProperty("limit", LIMIT);
        result.addProperty("truncated", truncated);
        return result;
    }

    private static String string(JsonObject args, String key, String fallback) {
        if (!args.has(key)) return fallback;
        var value = args.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(key + " must be a string");
        }
        return value.getAsString();
    }
}
