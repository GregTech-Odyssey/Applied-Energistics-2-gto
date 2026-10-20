/*
 * Adapted from EMI Trades by Prismwork and Flamarine.
 * Copyright (c) 2022-2023 Prismwork. Licensed under MIT; see LICENSE-EMITrades.
 */
package io.github.prismwork.emitrades.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonReader;

import io.github.prismwork.emitrades.EMITradesPlugin;

import dev.emi.emi.forge.EmiForgePlatform;

public final class EMITradesConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Config load() {
        var file = EmiForgePlatform.getConfigDirectory().resolve("emitrades.json5");
        if (Files.exists(file)) {
            try (var reader = new JsonReader(Files.newBufferedReader(file, StandardCharsets.UTF_8))) {
                // Accept comments and unquoted keys from the original JSON5 config.
                reader.setLenient(true);
                Config config = GSON.fromJson(reader, Config.class);
                if (config != null)
                    return config;
            } catch (IOException | JsonParseException exception) {
                EMITradesPlugin.LOGGER.error("Failed to read EMI Trades config", exception);
            }
            return new Config();
        }
        var config = new Config();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(config), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            EMITradesPlugin.LOGGER.error("Failed to save EMI Trades config", exception);
        }
        return config;
    }

    public static final class Config {
        public boolean enable3DVillagerModelInRecipes = true;
    }
}
