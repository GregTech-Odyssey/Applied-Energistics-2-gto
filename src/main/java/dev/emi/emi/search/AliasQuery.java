package dev.emi.emi.search;

import dev.emi.emi.api.stack.EmiStack;
import com.gto.fastcollection.fastutil.OpenCacheHashSet;

public class AliasQuery extends Query {
    private final OpenCacheHashSet<EmiStack> valid;

    public AliasQuery(String name) {
        valid = new OpenCacheHashSet<>(EmiSearch.aliases.search(name.toLowerCase()));
    }

    @Override
    public boolean matches(EmiStack stack) {
        return valid.contains(stack);
    }
}
