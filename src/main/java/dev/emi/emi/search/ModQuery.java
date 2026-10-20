package dev.emi.emi.search;

import com.google.common.collect.Sets;
import dev.emi.emi.EmiUtil;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.search.EmiSearch;
import dev.emi.emi.search.Query;

import java.util.Set;

public class ModQuery extends Query {
	private final Set<EmiStack> valid = Sets.newIdentityHashSet();
	private final String name;

	public ModQuery(String name) {
		this.name = name.toLowerCase();
		EmiSearch.mods.search(this.name).forEach(s -> valid.add(s.stack));
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
