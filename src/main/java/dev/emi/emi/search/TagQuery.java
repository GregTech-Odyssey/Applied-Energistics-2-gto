package dev.emi.emi.search;

import dev.emi.emi.EmiPort;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.runtime.EmiTagKey;
import java.util.regex.Pattern;
import net.minecraft.core.Registry;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

public class TagQuery extends Query {
    private final ReferenceOpenHashSet<Object> valid = new ReferenceOpenHashSet<>();

    public TagQuery(String name) {
        var lower = name.toLowerCase();
        collect(EmiPort.getItemRegistry(), valid, lower, null);
        collect(EmiPort.getBlockRegistry(), valid, lower, null);
    }

    static void collect(Registry<?> registry, ReferenceOpenHashSet<Object> valid, String name, Pattern pattern) {
        var tags = registry.getTagNames().iterator();
        while (tags.hasNext()) {
            var tag = EmiTagKey.of(tags.next());
            boolean matches = false;
            if (tag.hasTranslation()) {
                var translated = tag.getTagName().getString().toLowerCase();
                matches = pattern == null ? translated.contains(name) : pattern.matcher(translated).find();
            }
            if (!matches)
                matches = pattern == null ? tag.idString().contains(name) : pattern.matcher(tag.idString()).find();
            if (matches)
                valid.addAll(tag.getList());
        }
    }

    @Override
    public boolean matches(EmiStack stack) {
        return valid.contains(stack.getKey());
    }
}
