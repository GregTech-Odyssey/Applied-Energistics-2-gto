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

public class EmiPersistentData {
	public static final File FILE = new File("emi.json");
	public static final Gson GSON = new Gson().newBuilder().setPrettyPrinting().create();
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
			dev.emi.emi.runtime.EmiLog.error("Failed to write persistent data", e);
		}
	}

	public static void load() {
		if (!FILE.exists()) {
			return;
		}
		try {
			boolean trimFavoritePages = false;
			JsonObject json = GSON.fromJson(new FileReader(FILE), JsonObject.class);
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
			dev.emi.emi.runtime.EmiLog.error("Failed to parse persistent data", e);
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
			dev.emi.emi.runtime.EmiLog.error("Persistent data is corrupted, attempting recovery", e);
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
			dev.emi.emi.runtime.EmiLog.error("Failed to load bookmark tree state", e);
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
			dev.emi.emi.runtime.EmiLog.error("Failed to create persistent data backup", e);
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
				dev.emi.emi.runtime.EmiLog.warn("Recovered persistent data from " + backup.getName());
				return true;
			} catch (Exception e) {
				dev.emi.emi.runtime.EmiLog.error("Failed to recover persistent data from " + backup.getName(), e);
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
			dev.emi.emi.runtime.EmiLog.warn("Preserved corrupted persistent data as " + corrupted.getName());
		} catch (Exception e) {
			EmiLog.error("Failed to preserve corrupted persistent data", e);
		}
	}

	private static File getBackupFile(int index) {
		return new File(FILE.getPath() + ".bak" + index);
	}
}
