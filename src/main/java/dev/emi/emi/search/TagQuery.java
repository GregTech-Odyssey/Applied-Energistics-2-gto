package dev.emi.emi.search;

import dev.emi.emi.EmiPort;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.runtime.EmiTagKey;
import dev.emi.emi.search.Query;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TagQuery extends Query {
	private final Set<Object> valid;

	public TagQuery(String name) {
		String lowerName = name.toLowerCase();
		valid = Stream.<EmiTagKey<?>>concat(
			EmiTagKey.fromRegistry(EmiPort.getItemRegistry()),
			EmiTagKey.fromRegistry(EmiPort.getBlockRegistry())
		).filter(t -> {
			if (t.hasTranslation()) {
				if (t.getTagName().getString().toLowerCase().contains(lowerName)) {
					return true;
				}
			}
			if (t.id().toString().contains(lowerName)) {
				return true;
			}
			return false;
		}).flatMap(v -> v.stream()).collect(Collectors.toSet());
	}

	@Override
	public boolean matches(EmiStack stack) {
		return valid.contains(stack.getKey());
	}
}
