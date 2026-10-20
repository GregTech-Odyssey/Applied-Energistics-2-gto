package dev.emi.emi.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlParser;

class BundledEmiMetadataTest {
    @Test
    void modsInTheSameJarDoNotOrderAnExternalModBothBeforeAndAfter() throws IOException {
        var metadata = new TomlParser().parse(Files.readString(Path.of("src/main/resources/META-INF/mods.toml")));
        List<Config> mods = metadata.get("mods");
        var bundled = new HashSet<String>();
        for (var mod : mods) {
            bundled.add(mod.get("modId"));
        }
        assertTrue(bundled.containsAll(List.of("ae2", "emi", "emitrades")));
        var directions = new HashMap<String, String>();
        for (var mod : bundled) {
            List<Config> dependencies = metadata.get(List.of("dependencies", mod));
            if (dependencies == null) {
                continue;
            }
            for (var dependency : dependencies) {
                String target = dependency.get("modId");
                String ordering = dependency.get("ordering");
                if ("NONE".equals(ordering)) {
                    continue;
                }
                assertFalse(bundled.contains(target), "An ordering edge inside one physical jar creates a self-cycle");
                var previous = directions.putIfAbsent(target, ordering);
                if (previous != null) {
                    assertEquals(previous, ordering,
                            "Forge sorts physical mod files: contradictory ordering around " + target + " creates a cycle");
                }
            }
        }
    }
}
