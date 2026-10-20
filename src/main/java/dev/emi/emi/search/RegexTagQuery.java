package dev.emi.emi.search;

import dev.emi.emi.EmiPort;
import dev.emi.emi.api.stack.EmiStack;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

public class RegexTagQuery extends Query {
    private final ReferenceOpenHashSet<Object> valid = new ReferenceOpenHashSet<>();

    public RegexTagQuery(String name) {
        Pattern pattern;
        try {
            pattern = Pattern.compile(name, Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException invalid) {
            return;
        }
        TagQuery.collect(EmiPort.getItemRegistry(), valid, null, pattern);
        TagQuery.collect(EmiPort.getBlockRegistry(), valid, null, pattern);
    }

    @Override
    public boolean matches(EmiStack stack) {
        return valid.contains(stack.getKey());
    }
}
