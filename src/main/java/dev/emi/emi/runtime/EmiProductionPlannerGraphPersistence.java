package dev.emi.emi.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.emi.emi.forge.EmiForgePlatform;
import dev.emi.emi.runtime.EmiPersistentData;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class EmiProductionPlannerGraphPersistence {
    private static final String FILE_NAME = "emi-production-planner-graph.json";

    private EmiProductionPlannerGraphPersistence() {
    }

    public static synchronized Map<String, Offset> load(String lineKey) {
        Map<String, Offset> result = new LinkedHashMap<>();
        JsonObject line = getLine(readRoot(), lineKey);
        if (line == null) {
            return result;
        }
        JsonObject offsets = line.has("offsets") && line.get("offsets").isJsonObject()
            ? line.getAsJsonObject("offsets") : line;
        for (Map.Entry<String, JsonElement> entry : offsets.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject value = entry.getValue().getAsJsonObject();
            if (!value.has("x") || !value.has("y")) {
                continue;
            }
            try {
                result.put(entry.getKey(), new Offset(value.get("x").getAsInt(), value.get("y").getAsInt()));
            } catch (RuntimeException ignored) {
            }
        }
        return result;
    }

    public static synchronized Set<String> loadHidden(String lineKey) {
        Set<String> result = new LinkedHashSet<>();
        JsonObject line = getLine(readRoot(), lineKey);
        if (line == null || !line.has("hidden") || !line.get("hidden").isJsonArray()) {
            return result;
        }
        for (JsonElement element : line.getAsJsonArray("hidden")) {
            if (element.isJsonPrimitive()) {
                try {
                    result.add(element.getAsString());
                } catch (RuntimeException ignored) {
                }
            }
        }
        return result;
    }

    public static synchronized void save(String lineKey, Map<String, Offset> offsets) {
        JsonObject root = readRoot();
        JsonObject lines = getLines(root);
        JsonObject line = normalizedLine(lines, lineKey);
        JsonObject values = new JsonObject();
        for (Map.Entry<String, Offset> entry : offsets.entrySet()) {
            Offset offset = entry.getValue();
            if (offset == null || offset.x() == 0 && offset.y() == 0) {
                continue;
            }
            JsonObject value = new JsonObject();
            value.addProperty("x", offset.x());
            value.addProperty("y", offset.y());
            values.add(entry.getKey(), value);
        }
        if (values.size() == 0) {
            line.remove("offsets");
        } else {
            line.add("offsets", values);
        }
        putLine(lines, lineKey, line);
        root.add("lines", lines);
        writeRoot(root);
    }

    public static synchronized void saveHidden(String lineKey, Set<String> hidden) {
        JsonObject root = readRoot();
        JsonObject lines = getLines(root);
        JsonObject line = normalizedLine(lines, lineKey);
        JsonArray values = new JsonArray();
        for (String key : hidden) {
            if (key != null && !key.isBlank()) {
                values.add(key);
            }
        }
        if (values.size() == 0) {
            line.remove("hidden");
        } else {
            line.add("hidden", values);
        }
        putLine(lines, lineKey, line);
        root.add("lines", lines);
        writeRoot(root);
    }

    public static synchronized void clear(String lineKey) {
        JsonObject root = readRoot();
        JsonObject lines = getLines(root);
        JsonObject line = normalizedLine(lines, lineKey);
        line.remove("offsets");
        putLine(lines, lineKey, line);
        root.add("lines", lines);
        writeRoot(root);
    }

    private static JsonObject normalizedLine(JsonObject lines, String lineKey) {
        JsonObject existing = lines.has(lineKey) && lines.get(lineKey).isJsonObject()
            ? lines.getAsJsonObject(lineKey) : new JsonObject();
        if (existing.has("offsets")) {
            return existing.deepCopy();
        }
        JsonObject normalized = new JsonObject();
        JsonObject offsets = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : existing.entrySet()) {
            if ("hidden".equals(entry.getKey())) {
                normalized.add("hidden", entry.getValue().deepCopy());
            } else if (entry.getValue().isJsonObject()) {
                JsonObject value = entry.getValue().getAsJsonObject();
                if (value.has("x") && value.has("y")) {
                    offsets.add(entry.getKey(), value.deepCopy());
                }
            }
        }
        if (offsets.size() > 0) {
            normalized.add("offsets", offsets);
        }
        return normalized;
    }

    private static JsonObject getLines(JsonObject root) {
        return root.has("lines") && root.get("lines").isJsonObject()
            ? root.getAsJsonObject("lines") : new JsonObject();
    }

    private static JsonObject getLine(JsonObject root, String lineKey) {
        JsonObject lines = getLines(root);
        return lines.has(lineKey) && lines.get(lineKey).isJsonObject() ? lines.getAsJsonObject(lineKey) : null;
    }

    private static void putLine(JsonObject lines, String lineKey, JsonObject line) {
        if (line.size() == 0) {
            lines.remove(lineKey);
        } else {
            lines.add(lineKey, line);
        }
    }

    private static JsonObject readRoot() {
        Path file = file();
        if (!Files.exists(file)) {
            return new JsonObject();
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject root = EmiPersistentData.GSON.fromJson(json, JsonObject.class);
            return root == null ? new JsonObject() : root;
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    private static void writeRoot(JsonObject root) {
        Path file = file();
        Path temp = file.resolveSibling(FILE_NAME + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(temp, EmiPersistentData.GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            try {
                Files.deleteIfExists(temp);
            } catch (Exception ignored) {
            }
        }
    }

    private static Path file() {
        return EmiForgePlatform.getConfigDirectory().resolve(FILE_NAME);
    }

    public record Offset(int x, int y) {
    }
}
