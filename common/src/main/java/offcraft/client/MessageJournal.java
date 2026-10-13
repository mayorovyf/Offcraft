package offcraft.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/** Process-lifetime history of messages actually delivered to vanilla HUD callbacks. */
public final class MessageJournal {
    public static final int CAPACITY = 256;
    public static final int MAX_TEXT_LENGTH = 4096;
    private static final long MAX_SAFE_INTEGER = 9007199254740991L;
    private static final Entry[] ENTRIES = new Entry[CAPACITY];
    private static long latest;
    private static int size;
    private static boolean profilelessChat;

    private MessageJournal() {}

    /** Runs the vanilla callback with provenance preserved through its system-looking overload. */
    public static void displayProfilelessChat(ChatHud hud, Text message) {
        requireClientThread();
        boolean previous = profilelessChat;
        profilelessChat = true;
        try {
            hud.addMessage(message);
        } finally {
            profilelessChat = previous;
        }
    }

    public static boolean isProfilelessChat() {
        return profilelessChat;
    }

    public static void append(String kind, Text text) {
        requireClientThread();
        // Text's bounded visitor avoids constructing an unbounded flattened string.
        String content = text.asTruncatedString(MAX_TEXT_LENGTH + 1);
        boolean truncated = content.length() > MAX_TEXT_LENGTH;
        if (truncated) {
            int end = MAX_TEXT_LENGTH;
            if (Character.isHighSurrogate(content.charAt(end - 1))) end--;
            content = content.substring(0, end);
        }
        long seq = ++latest;
        ENTRIES[(int) ((seq - 1) % CAPACITY)] = new Entry(seq, kind, content, truncated);
        if (size < CAPACITY) size++;
    }

    /** Returns entries strictly after the cursor; future cursors are rejected. */
    public static JsonObject read(JsonObject args) {
        requireClientThread();
        long after = args.has("after") ? integer(args.get("after"), "after", 0, MAX_SAFE_INTEGER) : 0;
        int limit = args.has("limit") ? (int) integer(args.get("limit"), "limit", 1, CAPACITY) : CAPACITY;
        if (after > latest) throw new IllegalArgumentException("after must not exceed latest_seq (" + latest + ")");
        long oldest = size == 0 ? 0 : latest - size + 1;
        boolean gap = size > 0 && after < oldest - 1;
        long first = Math.max(after + 1, oldest);
        JsonArray messages = new JsonArray();
        long next = after;
        for (long seq = first; seq <= latest && messages.size() < limit; seq++) {
            Entry entry = ENTRIES[(int) ((seq - 1) % CAPACITY)];
            JsonObject message = new JsonObject();
            message.addProperty("seq", entry.seq());
            message.addProperty("kind", entry.kind());
            message.addProperty("text", entry.text());
            message.addProperty("truncated", entry.truncated());
            messages.add(message);
            next = seq;
        }
        JsonObject result = new JsonObject();
        result.add("messages", messages);
        result.addProperty("next", next);
        result.addProperty("oldest_seq", oldest);
        result.addProperty("latest_seq", latest);
        result.addProperty("gap", gap);
        result.addProperty("has_more", next < latest);
        result.addProperty("capacity", CAPACITY);
        result.addProperty("max_text_length", MAX_TEXT_LENGTH);
        return result;
    }

    private static long integer(JsonElement value, String name, long minimum, long maximum) {
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            try {
                long result = value.getAsBigDecimal().longValueExact();
                if (result >= minimum && result <= maximum) return result;
            } catch (ArithmeticException | NumberFormatException ignored) {
                // Never truncate fractional or overflowing cursors.
            }
        }
        throw new IllegalArgumentException(name + " must be an integer in [" + minimum + ", " + maximum + "]");
    }

    private static void requireClientThread() {
        if (!MinecraftClient.getInstance().isOnThread()) {
            throw new IllegalStateException("Message journal requires the client render thread");
        }
    }

    private record Entry(long seq, String kind, String text, boolean truncated) {}
}
