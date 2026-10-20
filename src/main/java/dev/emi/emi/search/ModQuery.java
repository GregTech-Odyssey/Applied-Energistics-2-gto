package dev.emi.emi.search;

import dev.emi.emi.EmiUtil;
import dev.emi.emi.api.stack.EmiStack;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

public class ModQuery extends Query {
    private final ReferenceOpenHashSet<EmiStack> valid;
    private final String name;

    public ModQuery(String name) {
        this.name = name.toLowerCase();
        var matches = EmiSearch.mods.search(this.name);
        valid = new ReferenceOpenHashSet<>(matches.size());
        for (var match : matches)
            valid.add(match.stack);
    }

    @Override
    public boolean matches(EmiStack stack) {
        return valid.contains(stack);
    }

    @Override
    public boolean matchesUnbaked(EmiStack stack) {
        return EmiUtil.getModName(stack.getId().getNamespace()).toLowerCase().contains(name);
    }
}
