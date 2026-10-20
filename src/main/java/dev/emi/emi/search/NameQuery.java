package dev.emi.emi.search;

import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.network.chat.Component;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

public class NameQuery extends Query {
    private final ReferenceOpenHashSet<EmiStack> valid;
    private final String name;

    public NameQuery(String name) {
        this.name = name.toLowerCase();
        var matches = EmiSearch.names.search(this.name);
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
        return getText(stack).getString().toLowerCase().contains(name);
    }

    public static Component getText(EmiStack stack) {
        return stack.getName();
    }
}
