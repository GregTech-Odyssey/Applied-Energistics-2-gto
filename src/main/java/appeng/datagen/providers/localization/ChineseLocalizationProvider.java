package appeng.datagen.providers.localization;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.DataProvider;

import appeng.datagen.providers.IAE2DataProvider;

import com.gto.ae.core.localization.ExtendedLangs;

public class ChineseLocalizationProvider implements IAE2DataProvider {
    private final DataGenerator generator;

    public ChineseLocalizationProvider(DataGenerator generator) {
        this.generator = generator;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        JsonObject translations;
        try (var reader = Files.newBufferedReader(Path.of(System.getProperty("ae2.chineseSource")),
                StandardCharsets.UTF_8)) {
            translations = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read AE2 Chinese translations", e);
        }
        // Fork-owned translations override the Crowdin entry when both define the same key.
        for (var entry : ExtendedLangs.values()) {
            translations.addProperty(entry.getTranslationKey(), entry.getChineseText());
        }
        var path = generator.getPackOutput().getOutputFolder().resolve("assets/ae2/lang/zh_cn.json");
        return DataProvider.saveStable(cache, translations, path);
    }

    @Override
    public String getName() {
        return "Localization (zh_cn)";
    }
}
