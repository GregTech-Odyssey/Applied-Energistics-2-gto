package appeng.crafting.pattern;

import java.util.LinkedHashMap;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import it.unimi.dsi.fastutil.objects.ObjectBidirectionalIterator;
import it.unimi.dsi.fastutil.objects.Reference2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;

/**
 * Helpers that apply to both processing and crafting patterns.
 */
final class AEPatternHelper {
    private AEPatternHelper() {
    }

    /**
     * Given an array of potentially null stacks, which can include multiples of the same type, produce an array that
     * has no null elements and only contains every input type once, while preserving order.
     */
    public static GenericStack[] condenseStacks(GenericStack[] sparseInput) {
        // Use a linked map to preserve ordering.
        var map = new Reference2LongLinkedOpenHashMap<AEKey>();

        for (var input : sparseInput) {
            if (input != null) {
                map.addTo(input.what(), input.amount());
            }
        }

        if (map.isEmpty()) {
            throw new IllegalStateException("No pattern here!");
        }

        GenericStack[] out = new GenericStack[map.size()];
        int i = 0;
        for (ObjectBidirectionalIterator<Reference2LongMap.Entry<AEKey>> it = map.reference2LongEntrySet().fastIterator(); it.hasNext(); ) {
            var entry = it.next();
            out[i++] = new GenericStack(entry.getKey(), entry.getLongValue());
        }
        return out;
    }
}
