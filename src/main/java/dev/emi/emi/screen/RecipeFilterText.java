package dev.emi.emi.screen;

import java.util.Locale;

final class RecipeFilterText {
	private RecipeFilterText() {
	}

	static String normalize(String value) {
		return normalizeChemicalGlyphs(value).toLowerCase(Locale.ROOT);
	}

	static String normalizeChemicalGlyphs(String value) {
		if (value == null || value.isEmpty()) {
			return "";
		}
		return value
			.replace('\u2080', '0')
			.replace('\u2081', '1')
			.replace('\u2082', '2')
			.replace('\u2083', '3')
			.replace('\u2084', '4')
			.replace('\u2085', '5')
			.replace('\u2086', '6')
			.replace('\u2087', '7')
			.replace('\u2088', '8')
			.replace('\u2089', '9')
			.replace('\u2070', '0')
			.replace('\u00B9', '1')
			.replace('\u00B2', '2')
			.replace('\u00B3', '3')
			.replace('\u2074', '4')
			.replace('\u2075', '5')
			.replace('\u2076', '6')
			.replace('\u2077', '7')
			.replace('\u2078', '8')
			.replace('\u2079', '9')
			.replace('\u208A', '+')
			.replace('\u208B', '-')
			.replace('\u207A', '+')
			.replace('\u207B', '-')
			.replace('\u208D', '(')
			.replace('\u208E', ')');
	}
}
