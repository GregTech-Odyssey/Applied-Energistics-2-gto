package dev.emi.emi.runtime;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.emi.emi.bom.BoM;
import net.minecraft.util.GsonHelper;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.io.IOException;
import java.io.Reader;
import org.jetbrains.annotations.Nullable;

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
        backupCurrentFileIfValid();
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

    private static final int BACKUP_COUNT = 5;
    private static final DateTimeFormatter CORRUPTED_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    static {
        preparePersistentFileForLoad();
    }

    public static void save() {
        try {
            JsonObject json = new JsonObject();
            json.add("favorites", EmiFavorites.save());
            json.addProperty("favorite_page_count", EmiFavorites.getFavoritePageCount());
            json.add("favorite_groups", EmiFavoriteGroups.save());
            json.add("bookmark_trees", EmiBookmarkTreePersistence.save());
            json.add("sidebar_grouping", EmiCollapsibleSidebarGroups.save());
            EmiSidebars.save(json);
            json.add("recipe_defaults", BoM.saveAdded());
            json.add("hidden_stacks", EmiHidden.save());
            FileWriter writer = new FileWriter(FILE);
            GSON.toJson(json, writer);
            writer.close();
            backupCurrentFileIfValid();
        } catch (Exception e) {
            EmiLog.error("Failed to write persistent data", e);
        }
    }

    public static void load() {
        if (!FILE.exists()) {
            return;
        }
        try {
            boolean trimFavoritePages;
            JsonObject json = load(new FileReader(FILE), null);
            if (GsonHelper.isArrayNode(json, "favorites")) {
                EmiFavorites.load(GsonHelper.getAsJsonArray(json, "favorites"));
            }
            EmiFavorites.setFavoritePageCount(GsonHelper.getAsInt(json, "favorite_page_count", 1));
            if (GsonHelper.isArrayNode(json, "favorite_groups")) {
                EmiFavoriteGroups.load(GsonHelper.getAsJsonArray(json, "favorite_groups"));
                EmiFavorites.takeEmbeddedFavoriteGroups();
            } else {
                JsonArray embeddedGroups = EmiFavorites.takeEmbeddedFavoriteGroups();
                EmiFavoriteGroups.load(embeddedGroups == null ? new JsonArray() : embeddedGroups);
            }
            trimFavoritePages = EmiFavorites.trimTrailingEmptyFavoritePages();
            if (GsonHelper.isObjectNode(json, "sidebar_grouping")) {
                EmiCollapsibleSidebarGroups.load(GsonHelper.getAsJsonObject(json, "sidebar_grouping"));
            } else {
                EmiCollapsibleSidebarGroups.load(new JsonObject());
            }
            EmiSidebars.load(json);
            if (GsonHelper.isObjectNode(json, "recipe_defaults")) {
                BoM.loadAdded(GsonHelper.getAsJsonObject(json, "recipe_defaults"));
            }
            if (GsonHelper.isArrayNode(json, "hidden_stacks")) {
                EmiHidden.load(GsonHelper.getAsJsonArray(json, "hidden_stacks"));
            }
            if (trimFavoritePages) {
                save();
            }
        } catch (Exception e) {
            EmiLog.error("Failed to parse persistent data", e);
        }
    }

    private static void preparePersistentFileForLoad() {
        if (!FILE.exists()) {
            if (recoverFromBackup()) {
                loadBookmarkTreeState();
            }
            return;
        }
        try {
            JsonObject json = readJson(FILE);
            EmiBookmarkTreePersistence.loadFromRoot(json);
            backupCurrentFileIfValid();
        } catch (Exception e) {
            EmiLog.error("Persistent data is corrupted, attempting recovery", e);
            preserveCorruptedFile();
            if (recoverFromBackup()) {
                loadBookmarkTreeState();
            }
        }
    }

    public static void reloadBookmarkTreeState() {
        loadBookmarkTreeState();
    }

    private static void loadBookmarkTreeState() {
        if (!FILE.exists()) {
            return;
        }
        try {
            EmiBookmarkTreePersistence.loadFromRoot(readJson(FILE));
        } catch (Exception e) {
            EmiLog.error("Failed to load bookmark tree state", e);
        }
    }

    private static void backupCurrentFileIfValid() {
        if (!FILE.exists()) {
            return;
        }
        try {
            readJson(FILE);
            File latest = getBackupFile(1);
            if (latest.exists() && Files.mismatch(FILE.toPath(), latest.toPath()) == -1) {
                return;
            }
            for (int i = BACKUP_COUNT; i >= 2; i--) {
                File previous = getBackupFile(i - 1);
                File next = getBackupFile(i);
                if (previous.exists()) {
                    Files.copy(previous.toPath(), next.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.deleteIfExists(next.toPath());
                }
            }
            Files.copy(FILE.toPath(), getBackupFile(1).toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            EmiLog.error("Failed to create persistent data backup", e);
        }
    }

    private static boolean recoverFromBackup() {
        for (int i = 1; i <= BACKUP_COUNT; i++) {
            File backup = getBackupFile(i);
            if (!backup.exists()) {
                continue;
            }
            try {
                readJson(backup);
                Files.copy(backup.toPath(), FILE.toPath(), StandardCopyOption.REPLACE_EXISTING);
                EmiLog.warn("Recovered persistent data from " + backup.getName());
                return true;
            } catch (Exception e) {
                EmiLog.error("Failed to recover persistent data from " + backup.getName(), e);
            }
        }
        return false;
    }

    private static JsonObject readJson(File file) throws Exception {
        try (FileReader reader = new FileReader(file)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json == null) {
                throw new IllegalStateException("Persistent data is empty");
            }
            return json;
        }
    }

    private static void preserveCorruptedFile() {
        if (!FILE.exists()) {
            return;
        }
        try {
            File parent = FILE.getAbsoluteFile().getParentFile();
            String timestamp = LocalDateTime.now().format(CORRUPTED_TIMESTAMP);
            File corrupted = new File(parent, "emi.corrupted-" + timestamp + ".json");
            Files.copy(FILE.toPath(), corrupted.toPath(), StandardCopyOption.REPLACE_EXISTING);
            EmiLog.warn("Preserved corrupted persistent data as " + corrupted.getName());
        } catch (Exception e) {
            EmiLog.error("Failed to preserve corrupted persistent data", e);
        }
    }

    private static File getBackupFile(int index) {
        return new File(FILE.getPath() + ".bak" + index);
    }
}
