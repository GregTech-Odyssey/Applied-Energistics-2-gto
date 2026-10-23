package appeng.client.gui.me.crafting;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyObjectMap;

/**
 * Filters immutable crafting entry snapshots without changing the complete plan or CPU status.
 */
final class CraftingMaterialSearch<T> {
    private final Function<T, AEKey> keyExtractor;
    private final AEKeyObjectMap<AEKey, String> names = new AEKeyObjectMap<>();
    private final ObjectArrayList<T> matches = new ObjectArrayList<>();
    private List<T> lastEntries = Collections.emptyList();
    private String searchTerm = "";
    private boolean dirty;

    CraftingMaterialSearch(Function<T, AEKey> keyExtractor) {
        this.keyExtractor = keyExtractor;
    }

    void setSearchTerm(String searchTerm) {
        var normalized = searchTerm.strip().toLowerCase(Locale.ROOT);
        if (!this.searchTerm.equals(normalized)) {
            this.searchTerm = normalized;
            dirty = true;
        }
    }

    List<T> filter(List<T> entries) {
        if (searchTerm.isEmpty()) {
            return entries;
        }
        if (dirty || entries != lastEntries) {
            matches.clear();
            matches.ensureCapacity(entries.size());
            for (int i = 0; i < entries.size(); i++) {
                var entry = entries.get(i);
                var key = keyExtractor.apply(entry);
                var name = names.get(key);
                if (name == null) {
                    name = key.getDisplayName().getString().toLowerCase(Locale.ROOT);
                    names.put(key, name);
                }
                if (name.contains(searchTerm)) {
                    matches.add(entry);
                }
            }
            lastEntries = entries;
            dirty = false;
        }
        return matches;
    }
}
