package dev.emi.emi.runtime;

import com.google.gson.JsonObject;
import dev.emi.emi.runtime.EmiLog;
import dev.emi.emi.runtime.EmiPersistentData;
import net.minecraft.util.GsonHelper;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class EmiBookmarkTreePersistence {
    public static final File FILE = new File("emi-bookmark-trees.json");
    private static final File TEMP_FILE = new File("emi-bookmark-trees.json.tmp");
    private static final int BACKUP_COUNT = 3;
    private static final DateTimeFormatter CORRUPTED_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static JsonObject state = new JsonObject();

    private EmiBookmarkTreePersistence() {
    }

    public static synchronized void loadFromRoot(JsonObject root) {
        if (FILE.exists()) {
            return;
        }
        if (root != null && GsonHelper.isObjectNode(root, "bookmark_trees")) {
            state = GsonHelper.getAsJsonObject(root, "bookmark_trees").deepCopy();
        }
    }

    public static synchronized void loadFromDisk() {
        if (!FILE.exists()) {
            if (!state.entrySet().isEmpty()) {
                saveToDisk();
            } else {
                state = new JsonObject();
            }
            return;
        }
        try {
            state = readJson(FILE).deepCopy();
            backupCurrentFileIfValid();
        } catch (Exception e) {
            EmiLog.error("Bookmark tree state is corrupted, attempting recovery", e);
            preserveCorruptedFile();
            if (!recoverFromBackup()) {
                state = new JsonObject();
            }
        }
    }

    public static synchronized JsonObject getState() {
        return state.deepCopy();
    }

    public static synchronized void setState(JsonObject value) {
        state = value == null ? new JsonObject() : value.deepCopy();
    }

    public static synchronized JsonObject save() {
        return state.deepCopy();
    }

    public static synchronized void saveToDisk() {
        try {
            try (FileWriter writer = new FileWriter(TEMP_FILE)) {
                EmiPersistentData.GSON.toJson(state, writer);
            }
            readJson(TEMP_FILE);
            try {
                Files.move(TEMP_FILE.toPath(), FILE.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(TEMP_FILE.toPath(), FILE.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            backupCurrentFileIfValid();
        } catch (Exception e) {
            EmiLog.error("Failed to write bookmark tree state", e);
            try {
                Files.deleteIfExists(TEMP_FILE.toPath());
            } catch (Exception ignored) {
            }
        }
    }

    private static JsonObject readJson(File file) throws Exception {
        try (FileReader reader = new FileReader(file)) {
            JsonObject json = EmiPersistentData.GSON.fromJson(reader, JsonObject.class);
            if (json == null) {
                throw new IllegalStateException("Bookmark tree state is empty");
            }
            return json;
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
            Files.copy(FILE.toPath(), latest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            EmiLog.error("Failed to create bookmark tree state backup", e);
        }
    }

    private static boolean recoverFromBackup() {
        for (int i = 1; i <= BACKUP_COUNT; i++) {
            File backup = getBackupFile(i);
            if (!backup.exists()) {
                continue;
            }
            try {
                JsonObject recovered = readJson(backup);
                Files.copy(backup.toPath(), FILE.toPath(), StandardCopyOption.REPLACE_EXISTING);
                state = recovered.deepCopy();
                EmiLog.warn("Recovered bookmark tree state from " + backup.getName());
                return true;
            } catch (Exception e) {
                EmiLog.error("Failed to recover bookmark tree state from " + backup.getName(), e);
            }
        }
        return false;
    }

    private static File getBackupFile(int index) {
        return new File("emi-bookmark-trees.json.bak" + index);
    }

    private static void preserveCorruptedFile() {
        if (!FILE.exists()) {
            return;
        }
        try {
            String timestamp = LocalDateTime.now().format(CORRUPTED_TIMESTAMP);
            File corrupted = new File("emi-bookmark-trees.corrupted-" + timestamp + ".json");
            Files.copy(FILE.toPath(), corrupted.toPath(), StandardCopyOption.REPLACE_EXISTING);
            EmiLog.warn("Preserved corrupted bookmark tree state as " + corrupted.getName());
        } catch (Exception e) {
            EmiLog.error("Failed to preserve corrupted bookmark tree state", e);
        }
    }
}
