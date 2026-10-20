package dev.emi.emi.screen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class RecipeFilterQuery {
	enum Scope {
		ANY,
		INPUT,
		OUTPUT,
		MOD,
		TOOLTIP,
		TAG,
		RESOURCE_ID,
		FORMULA,
		VOLTAGE
	}

	record Term(Scope scope, String value, boolean negated) {
		boolean test(EmiRecipeSearchIndex.Document document) {
			boolean matched = switch (scope) {
				case ANY -> document.any().contains(value);
				case INPUT -> document.inputs().contains(value);
				case OUTPUT -> document.outputs().contains(value);
				case MOD -> document.mods().contains(value);
				case TOOLTIP -> document.tooltips().contains(value);
				case TAG -> document.tags().contains(value);
				case RESOURCE_ID -> document.resourceIds().contains(value);
				case FORMULA -> document.formulas().contains(value);
				case VOLTAGE -> document.voltage().contains(value);
			};
			return negated ? !matched : matched;
		}
	}

	record Group(List<Term> alternatives) {
		boolean test(EmiRecipeSearchIndex.Document document) {
			for (Term term : alternatives) {
				if (term.test(document)) {
					return true;
				}
			}
			return false;
		}
	}

	private final List<Group> groups;

	private RecipeFilterQuery(List<Group> groups) {
		this.groups = groups;
	}

	static RecipeFilterQuery parse(String raw) {
		if (raw == null || raw.isBlank()) {
			return new RecipeFilterQuery(Collections.emptyList());
		}

		List<String> groupTokens = splitOutsideQuotes(raw.trim(), ' ');
		List<Group> groups = new ArrayList<>();
		for (String groupToken : groupTokens) {
			if (groupToken.isBlank()) {
				continue;
			}
			List<String> alternatives = splitOutsideQuotes(groupToken, '|');
			List<Term> terms = new ArrayList<>();
			for (String alternative : alternatives) {
				Term term = parseTerm(alternative);
				if (term != null) {
					terms.add(term);
				}
			}
			if (!terms.isEmpty()) {
				groups.add(new Group(List.copyOf(terms)));
			}
		}
		return new RecipeFilterQuery(List.copyOf(groups));
	}

	boolean isEmpty() {
		return groups.isEmpty();
	}

	boolean test(EmiRecipeSearchIndex.Document document) {
		for (Group group : groups) {
			if (!group.test(document)) {
				return false;
			}
		}
		return true;
	}

	private static Term parseTerm(String raw) {
		if (raw == null) {
			return null;
		}
		String token = raw.trim();
		if (token.isEmpty()) {
			return null;
		}

		boolean negated = false;
		if (token.startsWith("-") && token.length() > 1) {
			negated = true;
			token = token.substring(1);
		}

		Scope scope = Scope.ANY;
		if (!token.isEmpty()) {
			scope = switch (token.charAt(0)) {
				case '<' -> Scope.INPUT;
				case '>' -> Scope.OUTPUT;
				case '@' -> Scope.MOD;
				case '#', '№' -> Scope.TOOLTIP;
				case '$' -> Scope.TAG;
				case '&', '^' -> Scope.RESOURCE_ID;
				case '=' -> Scope.FORMULA;
				case '%' -> Scope.VOLTAGE;
				default -> Scope.ANY;
			};
			if (scope != Scope.ANY) {
				token = token.substring(1);
			}
		}

		token = RecipeFilterText.normalize(unquote(token).trim());
		if (token.isEmpty()) {
			return null;
		}
		return new Term(scope, token, negated);
	}

	private static String unquote(String value) {
		if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
			return value.substring(1, value.length() - 1);
		}
		return value;
	}

	private static List<String> splitOutsideQuotes(String value, char separator) {
		List<String> result = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c == '"') {
				quoted = !quoted;
				current.append(c);
				continue;
			}
			if (!quoted && c == separator) {
				if (!current.isEmpty()) {
					result.add(current.toString());
					current.setLength(0);
				}
				continue;
			}
			current.append(c);
		}
		if (!current.isEmpty()) {
			result.add(current.toString());
		}
		return result;
	}
}
