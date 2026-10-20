package dev.emi.emi.planner.compat.gtceu;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.planner.PlannerText;
import dev.emi.emi.planner.ProductionPlanner.Entry;
import dev.emi.emi.planner.ProductionPlanner.MachineProfile;
import dev.emi.emi.planner.ProductionPlanner.MachineSettingSpec;
import dev.emi.emi.planner.compat.PlannerMachineRule;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

final class GtceuElectricBlastFurnaceRule implements PlannerMachineRule {
	static final GtceuElectricBlastFurnaceRule INSTANCE = new GtceuElectricBlastFurnaceRule();
	private static final String COIL = "gtceu_ebf_coil";
	private static final int MV_TIER = 2;
	private static final Map<EmiRecipe, Integer> RECIPE_TEMPERATURE_CACHE =
		Collections.synchronizedMap(new IdentityHashMap<>());

	private GtceuElectricBlastFurnaceRule() {
	}

	@Override
	public String id() {
		return "gtceu:electric_blast_furnace";
	}

	@Override
	public List<MachineSettingSpec> settings(MachineProfile profile) {
		return List.of(GtceuHeatingCoilCatalog.spec(
			COIL, "gtceu.ebf.coil", "Heating Coil", "gtceu.ebf.coil_help",
			"Select the installed EBF heating coil. AUTO keeps legacy planner behavior until a coil is selected"
		));
	}

	@Override
	public boolean allowsRecipe(Entry entry) {
		if (!coilSelected(entry)) {
			return true;
		}
		int recipeHeat = recipeTemperature(entry);
		return recipeHeat <= 0 || effectiveHeat(entry) >= recipeHeat;
	}

	@Override
	public String constraintError(Entry entry) {
		if (entry == null || allowsRecipe(entry) || !coilSelected(entry)) {
			return "";
		}
		int recipeHeat = recipeTemperature(entry);
		return PlannerText.tr("gtceu.ebf.heat_too_low", "EBF temperature is too low") + ": "
			+ effectiveHeat(entry) + "K < " + recipeHeat + "K";
	}

	@Override
	public double ocDurationMultiplierForStep(Entry entry, int overclockIndex, double fallback) {
		int perfect = perfectOverclocks(entry);
		if (overclockIndex < perfect) {
			return Math.min(fallback, 0.25D);
		}
		return fallback;
	}

	@Override
	public double energyMultiplier(Entry entry) {
		int steps = energyDiscountSteps(entry);
		return steps <= 0 ? 1.0D : Math.pow(0.95D, steps);
	}

	@Override
	public List<String> settingDetails(Entry entry, MachineSettingSpec spec) {
		if (entry == null || spec == null || !COIL.equals(spec.key())) {
			return List.of();
		}
		int recipeHeat = recipeTemperature(entry);
		if (!coilSelected(entry)) {
			List<String> lines = new ArrayList<>();
			lines.add(PlannerText.tr("gtceu.ebf.auto_heat", "AUTO: EBF heat bonuses are not modeled until a coil is selected"));
			if (recipeHeat > 0) {
				lines.add(PlannerText.tr("gtceu.ebf.recipe_heat", "Recipe required temperature") + ": " + recipeHeat + "K");
			} else {
				lines.add(PlannerText.tr("gtceu.ebf.recipe_heat_unknown", "Recipe temperature could not be detected"));
			}
			return List.copyOf(lines);
		}
		int coilHeat = coilHeat(entry);
		int voltageBonus = voltageHeatBonus(entry);
		int effective = coilHeat + voltageBonus;
		int excess = recipeHeat > 0 ? Math.max(0, effective - recipeHeat) : 0;
		double energy = energyMultiplier(entry);
		List<String> lines = new ArrayList<>();
		lines.add(PlannerText.tr("gtceu.ebf.coil_heat", "Coil temperature") + ": " + coilHeat + "K");
		lines.add(PlannerText.tr("gtceu.ebf.voltage_heat", "Voltage heat bonus") + ": +" + voltageBonus + "K");
		lines.add(PlannerText.tr("gtceu.ebf.effective_heat", "Effective machine temperature") + ": " + effective + "K");
		if (recipeHeat > 0) {
			lines.add(PlannerText.tr("gtceu.ebf.recipe_heat", "Recipe required temperature") + ": " + recipeHeat + "K");
			lines.add(PlannerText.tr("gtceu.ebf.excess_heat", "Excess temperature") + ": " + excess + "K");
			lines.add(PlannerText.tr("gtceu.ebf.energy_steps", "900K energy-discount steps") + ": " + energyDiscountSteps(entry));
			lines.add(PlannerText.tr("gtceu.ebf.energy_multiplier", "Heat EU/t multiplier") + ": x" + formatMultiplier(energy));
			lines.add(PlannerText.tr("gtceu.ebf.perfect_ocs", "Available heat perfect OCs") + ": " + perfectOverclocks(entry));
			lines.add(PlannerText.tr("gtceu.ebf.applied_perfect_ocs", "Applied heat perfect OCs") + ": "
				+ Math.min(perfectOverclocks(entry), entry.getOverclockCount()));
		} else {
			lines.add(PlannerText.tr("gtceu.ebf.recipe_heat_unknown", "Recipe temperature could not be detected"));
		}
		return List.copyOf(lines);
	}

	@Override
	public List<String> modifierDescriptions(MachineProfile profile) {
		return List.of(
			PlannerText.tr("gtceu.ebf.mod_heat", "GTCEu EBF heat: +100K per selected voltage tier above MV"),
			PlannerText.tr("gtceu.ebf.mod_discount", "GTCEu EBF heat discount: x0.95 EU/t for every 900K above recipe temperature"),
			PlannerText.tr("gtceu.ebf.mod_perfect", "GTCEu EBF heat OC: every 1800K above recipe temperature upgrades one OC to a perfect OC")
		);
	}

	private static int energyDiscountSteps(Entry entry) {
		int recipeHeat = recipeTemperature(entry);
		if (!coilSelected(entry) || recipeHeat <= 0) {
			return 0;
		}
		return Math.max(0, effectiveHeat(entry) - recipeHeat) / 900;
	}

	private static int perfectOverclocks(Entry entry) {
		int recipeHeat = recipeTemperature(entry);
		if (!coilSelected(entry) || recipeHeat <= 0) {
			return 0;
		}
		return Math.max(0, effectiveHeat(entry) - recipeHeat) / 1800;
	}

	private static boolean coilSelected(Entry entry) {
		return coilSettingValue(entry) > 0;
	}

	private static int coilHeat(Entry entry) {
		int value = coilSettingValue(entry);
		return GtceuHeatingCoilCatalog.temperatureForChoice(value);
	}

	private static int effectiveHeat(Entry entry) {
		return coilHeat(entry) + voltageHeatBonus(entry);
	}

	private static int voltageHeatBonus(Entry entry) {
		if (entry == null) {
			return 0;
		}
		int tier = entry.getVoltageTier();
		return tier > MV_TIER ? (tier - MV_TIER) * 100 : 0;
	}

	private static int coilSettingValue(Entry entry) {
		MachineSettingSpec spec = coilSetting(entry);
		return entry == null || spec == null ? 0 : entry.getMachineSettingValue(spec);
	}

	private static MachineSettingSpec coilSetting(Entry entry) {
		if (entry == null) {
			return null;
		}
		for (MachineSettingSpec spec : INSTANCE.settings(entry.getMachineProfile())) {
			if (COIL.equals(spec.key())) {
				return spec;
			}
		}
		return null;
	}

	private static String formatMultiplier(double value) {
		if (!Double.isFinite(value)) {
			return "1";
		}
		String text = String.format(java.util.Locale.ROOT, "%.6f", value);
		while (text.contains(".") && text.endsWith("0")) {
			text = text.substring(0, text.length() - 1);
		}
		return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
	}

	private static int recipeTemperature(Entry entry) {
		if (entry == null) {
			return 0;
		}
		EmiRecipe recipe = entry.getRecipe();
		if (recipe == null) {
			return 0;
		}
		Integer cached = RECIPE_TEMPERATURE_CACHE.get(recipe);
		if (cached != null) {
			return cached;
		}
		int resolved = resolveRecipeTemperature(recipe);
		RECIPE_TEMPERATURE_CACHE.put(recipe, resolved);
		return resolved;
	}

	private static int resolveRecipeTemperature(EmiRecipe recipe) {
		List<Object> queue = new ArrayList<>();
		try {
			addCandidate(queue, recipe.getBackingRecipe());
		} catch (Throwable ignored) {
		}
		addCandidate(queue, recipe);
		Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		int cursor = 0;
		while (cursor < queue.size() && visited.size() < 128) {
			Object target = queue.get(cursor++);
			if (target == null || !visited.add(target)) {
				continue;
			}
			int value = readRecipeTemperature(target);
			if (value > 0) {
				return value;
			}
			collectNestedRecipeObjects(target, queue);
		}
		return 0;
	}

	private static int readRecipeTemperature(Object target) {
		if (target == null) {
			return 0;
		}
		Object data = readField(target, "data");
		if (data == null) {
			data = invokeNoArg(target, "getData");
		}
		if (data == null) {
			data = invokeNoArg(target, "data");
		}
		int value = readRequiredTempFromData(data);
		if (value > 0) {
			return value;
		}
		for (String field : List.of("requiredTemp", "requiredTemperature", "recipeTemperature", "blastTemperature", "temperature")) {
			Object fieldValue = readField(target, field);
			value = positiveInt(fieldValue);
			if (value > 0) {
				return value;
			}
		}
		for (String method : List.of(
			"getRequiredTemp", "getRequiredTemperature", "getRecipeTemperature", "getBlastTemperature", "getTemperature")) {
			value = positiveInt(invokeNoArg(target, method));
			if (value > 0) {
				return value;
			}
		}
		return 0;
	}

	private static int readRequiredTempFromData(Object data) {
		if (data == null) {
			return 0;
		}
		int registered = readRegisteredEbfTemperature(data);
		if (registered > 0) {
			return registered;
		}
		if (data instanceof Map<?, ?> map) {
			try {
				for (Map.Entry<?, ?> entry : map.entrySet()) {
					if (isRequiredTemperatureKey(entry.getKey())) {
						int value = positiveInt(entry.getValue());
						if (value > 0) {
							return value;
						}
					}
				}
			} catch (Throwable ignored) {
			}
		} else {
			for (String key : List.of("RequiredTemp", "requiredTemp", "required_temp", "Temperature", "temperature")) {
				for (String getter : List.of("getInt", "getLong", "getShort", "getByte")) {
					int value = positiveInt(invokeStringArg(data, getter, key));
					if (value > 0) {
						return value;
					}
				}
				Object raw = invokeStringArg(data, "get", key);
				int value = positiveInt(raw);
				if (value > 0) {
					return value;
				}
			}
		}
		return parseRequiredTempText(String.valueOf(data));
	}

	private static int readRegisteredEbfTemperature(Object data) {
		Object key = staticFieldValue(
			"com.gregtechceu.gtceu.common.data.GTRecipeDataKeys", "EBF_TEMP", data.getClass().getClassLoader());
		if (key == null) {
			return 0;
		}
		for (String getter : List.of("getInt", "getLong", "get")) {
			int value = positiveInt(invokeCompatibleArg(data, getter, key));
			if (value > 0) {
				return value;
			}
		}
		return 0;
	}

	private static Object staticFieldValue(String className, String fieldName, ClassLoader preferredLoader) {
		Class<?> type = null;
		ClassLoader[] loaders = { preferredLoader, Thread.currentThread().getContextClassLoader(),
			GtceuElectricBlastFurnaceRule.class.getClassLoader() };
		for (ClassLoader loader : loaders) {
			if (loader == null) {
				continue;
			}
			try {
				type = Class.forName(className, false, loader);
				break;
			} catch (Throwable ignored) {
			}
		}
		if (type == null) {
			try {
				type = Class.forName(className);
			} catch (Throwable ignored) {
				return null;
			}
		}
		for (Class<?> owner = type; owner != null; owner = owner.getSuperclass()) {
			try {
				Field field = owner.getDeclaredField(fieldName);
				if (!Modifier.isStatic(field.getModifiers())) {
					continue;
				}
				field.setAccessible(true);
				return field.get(null);
			} catch (Throwable ignored) {
			}
		}
		return null;
	}

	private static Object invokeCompatibleArg(Object target, String name, Object argument) {
		if (target == null || argument == null) {
			return null;
		}
		for (Method method : target.getClass().getMethods()) {
			if (method.getName().equals(name) && method.getParameterCount() == 1
				&& method.getParameterTypes()[0].isAssignableFrom(argument.getClass())) {
				try {
					return method.invoke(target, argument);
				} catch (Throwable ignored) {
				}
			}
		}
		for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
			for (Method method : type.getDeclaredMethods()) {
				if (!method.getName().equals(name) || method.getParameterCount() != 1
					|| !method.getParameterTypes()[0].isAssignableFrom(argument.getClass())) {
					continue;
				}
				try {
					method.setAccessible(true);
					return method.invoke(target, argument);
				} catch (Throwable ignored) {
				}
			}
		}
		return null;
	}

	private static boolean isRequiredTemperatureKey(Object key) {
		if (key == null) {
			return false;
		}
		List<Object> candidates = new ArrayList<>();
		candidates.add(key);
		for (String method : List.of("getName", "name", "getId", "id", "getKey", "key", "location", "getLocation")) {
			Object value = invokeNoArg(key, method);
			if (value != null && value != key) {
				candidates.add(value);
			}
		}
		for (Class<?> type = key.getClass(); type != null; type = type.getSuperclass()) {
			for (Field field : type.getDeclaredFields()) {
				if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
					continue;
				}
				try {
					field.setAccessible(true);
					Object value = field.get(key);
					if (value != null && value != key) {
						candidates.add(value);
					}
				} catch (Throwable ignored) {
				}
			}
		}
		for (Object candidate : candidates) {
			String normalized = normalizeDataKey(candidate);
			if (normalized.equals("requiredtemp") || normalized.equals("requiredtemperature")
				|| normalized.equals("temperature") || normalized.endsWith("requiredtemp")
				|| normalized.endsWith("requiredtemperature") || normalized.contains("requiredtemp")) {
				return true;
			}
		}
		return false;
	}

	private static String normalizeDataKey(Object key) {
		String text;
		try {
			text = String.valueOf(key);
		} catch (Throwable ignored) {
			return "";
		}
		return text.replaceAll("[^A-Za-z0-9]", "").toLowerCase(java.util.Locale.ROOT);
	}

	private static int positiveInt(Object value) {
		if (value instanceof Number number) {
			return Math.max(0, number.intValue());
		}
		if (value == null) {
			return 0;
		}
		for (String method : List.of("getAsInt", "getAsLong", "intValue", "longValue", "getValue", "value")) {
			Object nested = invokeNoArg(value, method);
			if (nested instanceof Number number && number.longValue() > 0L) {
				return number.longValue() >= Integer.MAX_VALUE ? Integer.MAX_VALUE : number.intValue();
			}
		}
		for (String field : List.of("value", "data", "number")) {
			Object nested = readField(value, field);
			if (nested instanceof Number number && number.longValue() > 0L) {
				return number.longValue() >= Integer.MAX_VALUE ? Integer.MAX_VALUE : number.intValue();
			}
		}
		return parsePositiveInt(String.valueOf(value));
	}

	private static int parseRequiredTempText(String text) {
		if (text == null || text.isBlank()) {
			return 0;
		}
		java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
			"(?i)(?:required[_ ]?temp(?:erature)?|temperature)\\D{0,24}(\\d{3,7})").matcher(text);
		if (matcher.find()) {
			return parsePositiveInt(matcher.group(1));
		}
		return 0;
	}

	private static int parsePositiveInt(String text) {
		if (text == null) {
			return 0;
		}
		java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?<!\\d)(\\d{1,9})(?!\\d)").matcher(text);
		if (!matcher.find()) {
			return 0;
		}
		try {
			long value = Long.parseLong(matcher.group(1));
			return value <= 0L ? 0 : value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
		} catch (NumberFormatException ignored) {
			return 0;
		}
	}

	private static void collectNestedRecipeObjects(Object target, List<Object> output) {
		if (target == null) {
			return;
		}
		for (String method : List.of(
			"getRecipe", "recipe", "getRecipeDefinition", "getBackingRecipe", "getOriginalRecipe", "getWrappedRecipe",
			"getRecipeHolder", "getValue", "value", "getDelegate", "delegate", "unwrap")) {
			addCandidate(output, invokeNoArg(target, method));
		}
		if (target instanceof java.util.Optional<?> optional) {
			optional.ifPresent(value -> addCandidate(output, value));
		}
		if (target instanceof java.util.function.Supplier<?> supplier) {
			try {
				addCandidate(output, supplier.get());
			} catch (Throwable ignored) {
			}
		}
		if (target instanceof Map<?, ?> map) {
			int count = 0;
			for (Object value : map.values()) {
				addCandidate(output, value);
				if (++count >= 32) {
					break;
				}
			}
		}
		if (target instanceof Iterable<?> iterable) {
			int count = 0;
			for (Object value : iterable) {
				addCandidate(output, value);
				if (++count >= 32) {
					break;
				}
			}
		}
		Class<?> targetClass = target.getClass();
		if (targetClass.isArray()) {
			int length = Math.min(Array.getLength(target), 32);
			for (int i = 0; i < length; i++) {
				addCandidate(output, Array.get(target, i));
			}
		}
		boolean recipeLikeOwner = isRecipeLikeClass(targetClass);
		for (Class<?> type = targetClass; type != null; type = type.getSuperclass()) {
			for (Field field : type.getDeclaredFields()) {
				if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive() || field.getType().isEnum()) {
					continue;
				}
				String fieldName = field.getName().toLowerCase(java.util.Locale.ROOT);
				String fieldType = field.getType().getName().toLowerCase(java.util.Locale.ROOT);
				boolean likely = fieldName.contains("recipe") || fieldName.contains("holder") || fieldName.contains("delegate")
					|| fieldName.equals("value") || fieldName.equals("wrapped") || fieldType.contains("recipe")
					|| fieldType.contains("holder");
				if (!likely && !recipeLikeOwner) {
					continue;
				}
				try {
					field.setAccessible(true);
					Object value = field.get(target);
					if (likely || isRecipeLikeObject(value)) {
						addCandidate(output, value);
					}
				} catch (Throwable ignored) {
				}
			}
		}
	}

	private static boolean isRecipeLikeClass(Class<?> type) {
		if (type == null) {
			return false;
		}
		String name = type.getName().toLowerCase(java.util.Locale.ROOT);
		return name.contains("recipe") || name.contains("dev/emi/emi") || name.startsWith("com.gregtechceu.")
			|| name.startsWith("com.lowdragmc.");
	}

	private static boolean isRecipeLikeObject(Object value) {
		return value != null && isRecipeLikeClass(value.getClass());
	}

	private static void addCandidate(List<Object> output, Object value) {
		if (value == null || output.size() >= 128) {
			return;
		}
		if (value instanceof java.util.Optional<?> optional) {
			optional.ifPresent(item -> addCandidate(output, item));
			return;
		}
		if (value instanceof java.util.function.Supplier<?> supplier) {
			try {
				addCandidate(output, supplier.get());
			} catch (Throwable ignored) {
			}
			return;
		}
		output.add(value);
	}

	private static Object readField(Object target, String name) {
		if (target == null) {
			return null;
		}
		for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
			try {
				Field field = type.getDeclaredField(name);
				field.setAccessible(true);
				return field.get(target);
			} catch (Throwable ignored) {
			}
		}
		return null;
	}

	private static Object invokeNoArg(Object target, String name) {
		if (target == null) {
			return null;
		}
		try {
			Method method = target.getClass().getMethod(name);
			if (method.getParameterCount() == 0) {
				return method.invoke(target);
			}
		} catch (Throwable ignored) {
		}
		for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
			try {
				Method method = type.getDeclaredMethod(name);
				if (method.getParameterCount() != 0) {
					continue;
				}
				method.setAccessible(true);
				return method.invoke(target);
			} catch (Throwable ignored) {
			}
		}
		return null;
	}

	private static Object invokeStringArg(Object target, String name, String value) {
		if (target == null) {
			return null;
		}
		try {
			Method method = target.getClass().getMethod(name, String.class);
			return method.invoke(target, value);
		} catch (Throwable ignored) {
		}
		for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
			try {
				Method method = type.getDeclaredMethod(name, String.class);
				method.setAccessible(true);
				return method.invoke(target, value);
			} catch (Throwable ignored) {
			}
		}
		return null;
	}
}
