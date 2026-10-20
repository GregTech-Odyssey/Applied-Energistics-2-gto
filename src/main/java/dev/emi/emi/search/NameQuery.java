package dev.emi.emi.search;

import com.google.common.collect.Sets;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.search.EmiSearch;
import dev.emi.emi.search.Query;
import net.minecraft.network.chat.Component;

import java.util.Set;

public class NameQuery extends Query {
	private final Set<EmiStack> valid = Sets.newIdentityHashSet();
	private final String name;
	
	public NameQuery(String name) {
		EmiSearch.names.search(name.toLowerCase()).forEach(s -> valid.add(s.stack));
		this.name = name.toLowerCase();
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
