package dev.emi.emi.search;

import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.network.chat.Component;
import java.util.List;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

public class TooltipQuery extends Query {
    private final ReferenceOpenHashSet<EmiStack> valid;
    private final String name;

    public TooltipQuery(String name) {
        this.name = name.toLowerCase();
        var matches = EmiSearch.tooltips.search(this.name);
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
        for (Component text : getText(stack)) {
            if (text.getString().toLowerCase().contains(name)) {
                return true;
            }
        }
        return false;
    }

    public static List<Component> getText(EmiStack stack) {
        List<Component> lines = stack.getTooltipText();
        if (lines.isEmpty()) {
            return lines;
        } else {
            return lines.subList(1, lines.size());
        }
    }
}
