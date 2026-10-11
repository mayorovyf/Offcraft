package offcraft.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

/** Version-1 JSON Lines requests and responses, independent of Minecraft's stdout logger. */
public final class CommandProtocol {
    public static final int VERSION = 1;

    private static final Gson GSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
    private static final TypeAdapter<JsonElement> JSON = GSON.getAdapter(JsonElement.class);
    // Keep this stream open for the process lifetime; System.out may be a logging wrapper.
    private static final PrintStream OUTPUT = new PrintStream(
            new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);

    private CommandProtocol() {}

    /** args retains the entire request object, including id and command. */
    public record Request(String id, String command, JsonObject args) {}

    /** A valid envelope ID survives errors in other fields; null means no usable ID was found. */
    public static final class ParseException extends IllegalArgumentException {
        private final String id;

        private ParseException(String id, String message, Throwable cause) {
            super(message, cause);
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    /** Parses exactly one strict JSON object; malformed input is reported as IllegalArgumentException. */
    public static Request parse(String line) {
        if (line == null) {
            throw new ParseException(null, "A JSON object is required", null);
        }
        JsonElement value;
        try (JsonReader reader = new JsonReader(new StringReader(line))) {
            reader.setLenient(false);
            value = JSON.read(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new ParseException(null, "Exactly one JSON object is required", null);
            }
        } catch (ParseException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new ParseException(null, "Invalid JSON: " + e.getMessage(), e);
        }
        if (value == null || !value.isJsonObject()) {
            throw new ParseException(null, "A JSON object is required", null);
        }
        JsonObject args = value.getAsJsonObject();
        String id = stringField(args, "id", null);
        if (id.length() > 128) {
            throw new ParseException(null, "id must be at most 128 characters", null);
        }
        String command = stringField(args, "command", id);
        return new Request(id, command, args);
    }

    private static String stringField(JsonObject args, String name, String id) {
        JsonElement value = args.get(name);
        if (value == null || !value.isJsonPrimitive()) {
            throw new ParseException(id, name + " must be a nonempty string", null);
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (!primitive.isString() || primitive.getAsString().isBlank()) {
            throw new ParseException(id, name + " must be a nonempty string", null);
        }
        return primitive.getAsString();
    }

    public static void accepted(Request request, JsonObject data) {
        JsonObject response = response("accepted", request);
        response.add("data", data == null ? new JsonObject() : data);
        emit(response);
    }

    public static void completed(Request request, JsonObject data) {
        JsonObject response = response("completed", request);
        response.add("data", data == null ? new JsonObject() : data);
        emit(response);
    }

    /** request may be null, or carry only a preserved ID, when parsing failed. */
    public static void error(Request request, String code, String message, JsonObject data) {
        JsonObject response = response("error", request);
        response.addProperty("code", code);
        response.addProperty("message", message);
        if (data != null) response.add("data", data);
        emit(response);
    }

    public static void event(String name, JsonObject data) {
        JsonObject response = new JsonObject();
        response.addProperty("protocol", VERSION);
        response.addProperty("type", "event");
        response.addProperty("event", name);
        response.add("data", data == null ? new JsonObject() : data);
        emit(response);
    }

    private static JsonObject response(String type, Request request) {
        JsonObject response = new JsonObject();
        response.addProperty("protocol", VERSION);
        response.addProperty("type", type);
        response.addProperty("id", request == null ? null : request.id());
        response.addProperty("command", request == null ? null : request.command());
        return response;
    }

    private static void emit(JsonObject response) {
        String line = GSON.toJson(response);
        synchronized (OUTPUT) {
            OUTPUT.println(line);
        }
    }
}
