package dev.emi.emi.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;

import org.junit.jupiter.api.Test;

class EmiPersistentDataTest {
    @Test
    void selectsOnlyTheRequestedServer() throws Exception {
        var json = EmiPersistentData.load(new StringReader("""
                {"favorites":["global"],"byId":{
                    "first":{"favorites":["first"]},
                    "second":{"favorites":["second"]}
                }}
                """), "second");
        assertEquals("second", json.getAsJsonArray("favorites").get(0).getAsString());
    }

    @Test
    void unknownServerGetsEmptyDataInsteadOfGlobalFavorites() throws Exception {
        var json = EmiPersistentData.load(new StringReader("""
                {"favorites":["global"],"byId":{"first":{"favorites":["first"]}}}
                """), "unknown");
        assertTrue(json.getAsJsonArray("favorites").isEmpty());
        assertTrue(json.getAsJsonArray("lookup_history").isEmpty());
        assertEquals(0, json.getAsJsonObject("recipe_defaults").size());
    }

    @Test
    void globalDataAndLegacyDefaultsRemainReadable() throws Exception {
        var json = EmiPersistentData.load(new StringReader("""
                {"favorites":["global"],"recipeDefaults":{"legacy":true},
                 "byId":{"first":{"favorites":["first"]}}}
                """), null);
        assertEquals("global", json.getAsJsonArray("favorites").get(0).getAsString());
        assertTrue(json.getAsJsonObject("recipe_defaults").get("legacy").getAsBoolean());
    }

    @Test
    void normalizesMissingAndMalformedSectionsWithoutLosingExtensionData() throws Exception {
        var json = EmiPersistentData.load(new StringReader("""
                {"favorites":false,"lookup_history":null,"hidden_stacks":{},
                 "recipe_defaults":[],"extension_count":3,"extension_data":{"saved":true}}
                """), null);
        assertTrue(json.getAsJsonArray("favorites").isEmpty());
        assertTrue(json.getAsJsonArray("lookup_history").isEmpty());
        assertTrue(json.getAsJsonArray("craft_history").isEmpty());
        assertTrue(json.getAsJsonArray("hidden_stacks").isEmpty());
        assertEquals(0, json.getAsJsonObject("recipe_defaults").size());
        assertEquals(3, json.get("extension_count").getAsInt());
        assertTrue(json.getAsJsonObject("extension_data").get("saved").getAsBoolean());
    }
}
