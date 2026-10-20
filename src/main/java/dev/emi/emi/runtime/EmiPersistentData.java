package dev.emi.emi.runtime;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.jetbrains.annotations.Nullable;

import net.minecraft.util.GsonHelper;

import dev.emi.emi.bom.BoM;

public class EmiPersistentData {
    public static final File FILE = new File("emi.json");
    public static final Gson GSON = new Gson().newBuilder().setPrettyPrinting().create();
    private static final String FAVORITES_KEY = "favorites";

    private static final String LOOKUP_HISTORY_KEY = "lookup_history";

    private static final String CRAFT_HISTORY_KEY = "craft_history";

    private static final String RECIPE_DEFAULTS_KEY = "recipe_defaults";

    private static final String LEGACY_RECIPE_DEFAULTS_KEY = "recipeDefaults";

    private static final String HIDDEN_STACKS_KEY = "hidden_stacks";

    private static final String BY_ID_KEY = "byId";

    /** Writes a complete sidebar snapshot globally (null scope) or under the supplied server identity. */
    public static void save(JsonObject json, @Nullable String scope) throws IOException {
        EmiSidebars.save(json);
        json.add(RECIPE_DEFAULTS_KEY, BoM.saveAdded());
        json.add(HIDDEN_STACKS_KEY, EmiHidden.save());
        var persist = readPersist();
        if (scope != null) {
            var element = persist.get(BY_ID_KEY);
            var byId = element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
            byId.add(scope, json);
            persist.add(BY_ID_KEY, byId);
        } else {
            var byId = persist.get(BY_ID_KEY);
            persist = json;
            if (byId != null && byId.isJsonObject())
                persist.add(BY_ID_KEY, byId);
        }
        try (var writer = new FileWriter(FILE)) {
            GSON.toJson(persist, writer);
        }
    }

    public static JsonObject load(Reader reader, @Nullable String scope) throws IOException {
        JsonObject persist;
        try (reader) {
            persist = GSON.fromJson(reader, JsonObject.class);
        }
        if (persist == null)
            persist = new JsonObject();
        var byId = persist.get(BY_ID_KEY);
        if (scope != null && byId != null && byId.isJsonObject()) {
            var data = byId.getAsJsonObject().get(scope);
            return data != null && data.isJsonObject() ? normalizeData(data.getAsJsonObject()) : emptyData();
        }
        return normalizeData(persist);
    }

    private static JsonObject readPersist() throws IOException {
        if (!FILE.exists()) {
            return new JsonObject();
        }

        try (var reader = new FileReader(FILE)) {
            var persist = GSON.fromJson(reader, JsonObject.class);
            return persist == null ? new JsonObject() : persist;
        }
    }

    public static JsonObject emptyData() {
        final var obj = new JsonObject();
        obj.add(FAVORITES_KEY, new JsonArray());
        obj.add(LOOKUP_HISTORY_KEY, new JsonArray());
        obj.add(CRAFT_HISTORY_KEY, new JsonArray());
        obj.add(RECIPE_DEFAULTS_KEY, new JsonObject());
        obj.add(HIDDEN_STACKS_KEY, new JsonArray());
        return obj;
    }

    private static JsonObject normalizeData(JsonObject data) {
        ensureArray(data, FAVORITES_KEY);
        ensureArray(data, LOOKUP_HISTORY_KEY);
        ensureArray(data, CRAFT_HISTORY_KEY);
        ensureArray(data, HIDDEN_STACKS_KEY);

        var recipeDefaults = data.get(RECIPE_DEFAULTS_KEY);
        if (recipeDefaults == null || !recipeDefaults.isJsonObject()) {
            var legacyRecipeDefaults = data.get(LEGACY_RECIPE_DEFAULTS_KEY);
            data.add(RECIPE_DEFAULTS_KEY,
                    legacyRecipeDefaults != null && legacyRecipeDefaults.isJsonObject() ? legacyRecipeDefaults
                            : new JsonObject());
        }
        return data;
    }

    private static void ensureArray(JsonObject data, String key) {
        var element = data.get(key);
        if (element == null || !element.isJsonArray()) {
            data.add(key, new JsonArray());
        }
    }

    public static void save() {
        try {
            JsonObject json = new JsonObject();
            json.add("favorites", EmiFavorites.save());
            EmiSidebars.save(json);
            json.add("recipe_defaults", BoM.saveAdded());
            json.add("hidden_stacks", EmiHidden.save());
            FileWriter writer = new FileWriter(FILE);
            GSON.toJson(json, writer);
            writer.close();
        } catch (Exception e) {
            EmiLog.error("Failed to write persistent data", e);
        }
    }

    public static void load() {
        if (!FILE.exists()) {
            return;
        }
        try {
            JsonObject json = load(new FileReader(FILE), null);
            if (GsonHelper.isArrayNode(json, "favorites")) {
                EmiFavorites.load(GsonHelper.getAsJsonArray(json, "favorites"));
            }
            EmiSidebars.load(json);
            if (GsonHelper.isObjectNode(json, "recipe_defaults")) {
                BoM.loadAdded(GsonHelper.getAsJsonObject(json, "recipe_defaults"));
            }
            if (GsonHelper.isArrayNode(json, "hidden_stacks")) {
                EmiHidden.load(GsonHelper.getAsJsonArray(json, "hidden_stacks"));
            }
        } catch (Exception e) {
            EmiLog.error("Failed to parse persistent data", e);
        }
    }
}
