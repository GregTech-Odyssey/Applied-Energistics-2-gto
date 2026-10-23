package appeng.client.gui.me.crafting;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

class CraftingSearchLayoutTest {
    @Test
    void materialSearchFitsInsideBothHeadersWithoutCoveringTheTableOrTitle() throws IOException {
        for (var screen : new String[] { "craft_confirm", "crafting_cpu" }) {
            var widgets = layout(screen).getAsJsonObject("widgets");
            var search = widgets.getAsJsonObject("materialSearch");
            int left = search.get("left").getAsInt();
            int top = search.get("top").getAsInt();
            assertTrue(top >= 0, screen + ": search must be inside the panel");
            assertTrue(top + search.get("height").getAsInt() <= 19, screen + ": search must end before the table");
            assertTrue(left + search.get("width").getAsInt() <= 213, screen + ": search must end before the back tab");
            var title = widgets.getAsJsonObject("dialogTitle");
            assertNotNull(title, screen + ": title needs bounded rendering");
            assertTrue(title.get("left").getAsInt() + title.get("width").getAsInt() < left);
        }
    }

    @Test
    void cpuSelectorDoesNotDrawTheRemovedSearchTray() throws IOException {
        var status = layout("crafting_status");
        var widgets = status.getAsJsonObject("widgets");
        assertFalse(widgets.has("search"));
        int height = widgets.getAsJsonObject("selectCpuList").get("height").getAsInt();
        assertEquals(164, height, "The CPU list must stop before the old search tray");
        assertEquals(height, status.getAsJsonObject("images").getAsJsonObject("cpuList")
                .getAsJsonArray("srcRect").get(3).getAsInt());
        var filter = widgets.getAsJsonObject("filter");
        assertTrue(filter.get("top").getAsInt() + 16 <= 23, "The retained filter belongs beside the CPU list title");
    }

    private static JsonObject layout(String name) throws IOException {
        return JsonParser
                .parseString(Files.readString(Path.of("src/main/resources/assets/ae2/screens/" + name + ".json")))
                .getAsJsonObject();
    }
}
