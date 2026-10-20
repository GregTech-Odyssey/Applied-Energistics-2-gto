package dev.emi.emi.runtime;

import com.google.gson.JsonObject;
import dev.emi.emi.runtime.EmiLog;
import dev.emi.emi.runtime.EmiPersistentData;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class EmiProductionPlannerPersistence {
	public static final File FILE = new File("emi-production-planner.json");
	private static final File TEMP_FILE = new File("emi-production-planner.json.tmp");
	private static final int BACKUP_COUNT = 3;
	private static final DateTimeFormatter CORRUPTED_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

	private EmiProductionPlannerPersistence() {
	}

	public static synchronized JsonObject load() {
		if (!FILE.exists()) {
			return new JsonObject();
		}
		try {
			JsonObject state = readJson(FILE);
			backupCurrentFileIfValid();
			return state;
		} catch (Exception e) {
			dev.emi.emi.runtime.EmiLog.error("Production planner state is corrupted, attempting recovery", e);
			preserveCorruptedFile();
			JsonObject recovered = recoverFromBackup();
			return recovered == null ? new JsonObject() : recovered;
		}
	}

	public static synchronized void save(JsonObject state) {
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
			dev.emi.emi.runtime.EmiLog.error("Failed to write production planner state", e);
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
				throw new IllegalStateException("Production planner state is empty");
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
			dev.emi.emi.runtime.EmiLog.error("Failed to create production planner state backup", e);
		}
	}

	private static JsonObject recoverFromBackup() {
		for (int i = 1; i <= BACKUP_COUNT; i++) {
			File backup = getBackupFile(i);
			if (!backup.exists()) {
				continue;
			}
			try {
				JsonObject recovered = readJson(backup);
				Files.copy(backup.toPath(), FILE.toPath(), StandardCopyOption.REPLACE_EXISTING);
				dev.emi.emi.runtime.EmiLog.warn("Recovered production planner state from " + backup.getName());
				return recovered;
			} catch (Exception e) {
				dev.emi.emi.runtime.EmiLog.error("Failed to recover production planner state from " + backup.getName(), e);
			}
		}
		return null;
	}

	private static File getBackupFile(int index) {
		return new File("emi-production-planner.json.bak" + index);
	}

	private static void preserveCorruptedFile() {
		if (!FILE.exists()) {
			return;
		}
		try {
			String timestamp = LocalDateTime.now().format(CORRUPTED_TIMESTAMP);
			File corrupted = new File("emi-production-planner.corrupted-" + timestamp + ".json");
			Files.copy(FILE.toPath(), corrupted.toPath(), StandardCopyOption.REPLACE_EXISTING);
			dev.emi.emi.runtime.EmiLog.warn("Preserved corrupted production planner state as " + corrupted.getName());
		} catch (Exception e) {
			EmiLog.error("Failed to preserve corrupted production planner state", e);
		}
	}
}
