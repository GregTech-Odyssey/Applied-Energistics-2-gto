package dev.emi.emi.planner.compat.gto;

import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.planner.ProductionPlanner;
import dev.emi.emi.planner.ProductionPlanner.Entry;
import dev.emi.emi.planner.ProductionPlanner.MachineSettingSpec;

import java.util.ArrayList;
import java.util.List;

final class GtoHatchCatalog {
	static final String AUXILIARY_MODULES = "gto_auxiliary_modules";
	static final String PARALLEL_HATCH_LIMIT = "gto_parallel_hatch_limit";
	static final String ACCELERATION_HATCH_TIER = "gto_acceleration_hatch_tier";
	static final String ACCELERATION_MULTIPLIER = "gto_acceleration_multiplier";
	static final String THREAD_HATCH_TIER = "gto_thread_hatch_tier";
	static final String THREAD_COUNT = "gto_thread_count";
	static final String OVERCLOCK_HATCH_TIER = "gto_overclock_hatch_tier";
	static final String OVERCLOCK_DIVISOR = "gto_overclock_divisor";

	private static final int[] PARALLEL_LIMITS = {
		0, 1, 4, 16, 64, 256, 1024, 4096, 16384, 65536, 262144, 1048576
	};

	private GtoHatchCatalog() {
	}

	static MachineSettingSpec auxiliaryModulesSpec() {
		return MachineSettingSpec.toggle(
			AUXILIARY_MODULES, "gto.hatch.auxiliary", "Auxiliary Modules", false,
			"gto.hatch.auxiliary_help", "Enable this when the multiblock expansion/auxiliary module is installed");
	}

	static MachineSettingSpec parallelHatchLimitSpec() {
		List<String> choices = new ArrayList<>();
		List<EmiStack> icons = new ArrayList<>();
		choices.add("AUTO");
		icons.add(EmiStack.EMPTY);
		choices.add("OFF / 1");
		icons.add(EmiStack.EMPTY);
		for (int choice = 2; choice < PARALLEL_LIMITS.length; choice++) {
			int limit = PARALLEL_LIMITS[choice];
			int tier = choice + 3; // choice 2 = IV, then every tier is x4 parallel
			EmiStack icon = parallelHatch(tier);
			String fallback = Integer.toString(limit);
			String name = GtoComponentCatalog.displayName(icon, fallback);
			choices.add(icon.isEmpty() ? fallback : name + " - " + limit);
			icons.add(icon);
		}
		return MachineSettingSpec.choice(
			PARALLEL_HATCH_LIMIT, "gto.hatch.parallel_limit", "Parallel Hatch Limit", choices, icons, 0,
			"gto.hatch.parallel_limit_help",
			"Maximum parallel printed on the installed Parallel Control Hatch; row PAR is the current configured parallel");
	}

	static MachineSettingSpec accelerationTierSpec() {
		List<String> choices = new ArrayList<>();
		List<EmiStack> icons = new ArrayList<>();
		choices.add("OFF");
		icons.add(EmiStack.EMPTY);
		for (int tier = 1; tier <= ProductionPlanner.maxVoltageTier(); tier++) {
			String tierName = ProductionPlanner.voltageTierName(tier);
			EmiStack icon = accelerationHatch(tier);
			choices.add(GtoComponentCatalog.displayName(icon, tierName + " Accelerate Hatch"));
			icons.add(icon);
		}
		return MachineSettingSpec.choice(
			ACCELERATION_HATCH_TIER, "gto.hatch.acceleration_tier", "Acceleration Hatch Tier", choices, icons, 0,
			"gto.hatch.acceleration_tier_help",
			"Acceleration Hatch tier; lower hatch tier than the recipe adds 20% duration per missing tier");
	}

	static MachineSettingSpec accelerationMultiplierSpec() {
		List<String> choices = new ArrayList<>();
		choices.add("MAX SPEED");
		for (int percent = 24; percent <= 100; percent++) {
			choices.add(percent + "%");
		}
		return MachineSettingSpec.cycleChoice(
			ACCELERATION_MULTIPLIER, "gto.hatch.acceleration_multiplier", "Acceleration Setting", choices, 0,
			"gto.hatch.acceleration_multiplier_help",
			"Requested duration percentage; MAX SPEED uses the fastest value allowed by the selected hatch tier");
	}

	static MachineSettingSpec threadTierSpec() {
		List<String> choices = new ArrayList<>();
		List<EmiStack> icons = new ArrayList<>();
		choices.add("OFF");
		icons.add(EmiStack.EMPTY);
		for (int tier = 8; tier <= ProductionPlanner.maxVoltageTier(); tier++) {
			String tierName = ProductionPlanner.voltageTierName(tier);
			EmiStack icon = threadHatch(tier);
			choices.add(GtoComponentCatalog.displayName(icon, tierName + " Thread Hatch"));
			icons.add(icon);
		}
		return MachineSettingSpec.choice(
			THREAD_HATCH_TIER, "gto.hatch.thread_tier", "Thread Hatch Tier", choices, icons, 0,
			"gto.hatch.thread_tier_help", "Thread Hatch tier determines the maximum simultaneous recipe threads");
	}

	static MachineSettingSpec threadCountSpec() {
		List<String> choices = new ArrayList<>();
		choices.add("MAX");
		for (int threads = 1; threads <= 256; threads++) {
			choices.add(Integer.toString(threads));
		}
		return MachineSettingSpec.cycleChoice(
			THREAD_COUNT, "gto.hatch.thread_count", "Thread Count", choices, 0,
			"gto.hatch.thread_count_help", "Number of active recipe threads; MAX uses the selected Thread Hatch maximum");
	}

	static MachineSettingSpec overclockTierSpec() {
		List<String> choices = new ArrayList<>();
		List<EmiStack> icons = new ArrayList<>();
		choices.add("OFF");
		icons.add(EmiStack.EMPTY);
		int maxTier = Math.min(14, ProductionPlanner.maxVoltageTier());
		for (int tier = 8; tier <= maxTier; tier++) {
			String tierName = ProductionPlanner.voltageTierName(tier);
			EmiStack icon = overclockHatch(tier);
			choices.add(GtoComponentCatalog.displayName(icon, tierName + " Overclock Hatch"));
			icons.add(icon);
		}
		return MachineSettingSpec.choice(
			OVERCLOCK_HATCH_TIER, "gto.hatch.overclock_tier", "Overclocking Hatch Tier", choices, icons, 0,
			"gto.hatch.overclock_tier_help", "Overclocking Hatch tier sets the strongest selectable time divisor per 4x EU/t overclock");
	}

	static MachineSettingSpec overclockDivisorSpec() {
		List<String> choices = new ArrayList<>();
		choices.add("MAX");
		for (int divisor = 2; divisor <= 8; divisor++) {
			choices.add("/" + divisor);
		}
		return MachineSettingSpec.cycleChoice(
			OVERCLOCK_DIVISOR, "gto.hatch.overclock_divisor", "OC Time Divisor", choices, 0,
			"gto.hatch.overclock_divisor_help", "Time divisor applied on each 4x EU/t overclock; MAX uses the strongest value allowed by the selected hatch tier");
	}

	private static EmiStack accelerationHatch(int tier) {
		String token = GtoComponentCatalog.tierToken(tier);
		EmiStack exact = GtoComponentCatalog.resolve(
			"gtocore:" + token + "_accelerate_hatch",
			"gtocore:" + token + "_acceleration_hatch");
		return exact.isEmpty()
			? GtoComponentCatalog.findTiered(tier, List.of("gtocore"), "acceler", "hatch")
			: exact;
	}

	private static EmiStack threadHatch(int tier) {
		String token = GtoComponentCatalog.tierToken(tier);
		EmiStack exact = GtoComponentCatalog.resolve("gtocore:" + token + "_thread_hatch");
		return exact.isEmpty()
			? GtoComponentCatalog.findTiered(tier, List.of("gtocore"), "thread", "hatch")
			: exact;
	}

	private static EmiStack overclockHatch(int tier) {
		String token = GtoComponentCatalog.tierToken(tier);
		EmiStack exact = GtoComponentCatalog.resolve("gtocore:" + token + "_overclock_hatch");
		return exact.isEmpty()
			? GtoComponentCatalog.findTiered(tier, List.of("gtocore"), "overclock", "hatch")
			: exact;
	}

	private static EmiStack parallelHatch(int tier) {
		if (tier > ProductionPlanner.maxVoltageTier()) {
			return EmiStack.EMPTY;
		}
		String token = GtoComponentCatalog.tierToken(tier);
		EmiStack exact;
		if (tier <= 8) {
			exact = GtoComponentCatalog.resolve(
				"gtceu:" + token + "_parallel_hatch",
				"gtocore:" + token + "_parallel_hatch");
		} else {
			exact = GtoComponentCatalog.resolve(
				"gtocore:" + token + "_parallel_hatch",
				"gtceu:" + token + "_parallel_hatch");
		}
		return exact.isEmpty()
			? GtoComponentCatalog.findTiered(tier, List.of("gtocore", "gtceu"), "parallel", "hatch")
			: exact;
	}

	static boolean auxiliaryEnabled(Entry entry) {
		return entry != null && entry.getMachineSettingValue(auxiliaryModulesSpec()) != 0;
	}

	static int parallelLimit(Entry entry) {
		if (entry == null) {
			return 0;
		}
		int choice = entry.getMachineSettingValue(parallelHatchLimitSpec());
		if (choice < 0 || choice >= PARALLEL_LIMITS.length) {
			return 0;
		}
		return PARALLEL_LIMITS[choice];
	}

	static int accelerationTier(Entry entry) {
		if (entry == null) {
			return 0;
		}
		int choice = entry.getMachineSettingValue(accelerationTierSpec());
		return Math.max(0, Math.min(ProductionPlanner.maxVoltageTier(), choice));
	}

	static int accelerationMinimumPercent(int tier) {
		if (tier <= 0) {
			return 100;
		}
		return Math.max(24, Math.min(100, 52 - tier * 2));
	}

	static int accelerationRequestedPercent(Entry entry) {
		if (entry == null) {
			return 100;
		}
		int tier = accelerationTier(entry);
		if (tier <= 0) {
			return 100;
		}
		int setting = entry.getMachineSettingValue(accelerationMultiplierSpec());
		if (setting <= 0) {
			return accelerationMinimumPercent(tier);
		}
		return Math.max(24, Math.min(100, 23 + setting));
	}

	static int accelerationEffectivePercent(Entry entry) {
		int tier = accelerationTier(entry);
		if (tier <= 0 || entry == null) {
			return 100;
		}
		int base = Math.max(accelerationMinimumPercent(tier), accelerationRequestedPercent(entry));
		int recipeTier = entry.getRecipeTier();
		int missing = recipeTier < 0 ? 0 : Math.max(0, recipeTier - tier);
		return Math.min(100, base + missing * 20);
	}

	static double accelerationDurationMultiplier(Entry entry) {
		return accelerationEffectivePercent(entry) / 100.0D;
	}

	static int threadTierChoice(Entry entry) {
		if (entry == null) {
			return 0;
		}
		return entry.getMachineSettingValue(threadTierSpec());
	}

	static int threadTier(Entry entry) {
		int choice = threadTierChoice(entry);
		if (choice <= 0) {
			return 0;
		}
		return Math.min(ProductionPlanner.maxVoltageTier(), choice + 7);
	}

	static int maxThreads(Entry entry) {
		int choice = threadTierChoice(entry);
		if (choice <= 0) {
			return 1;
		}
		int shift = Math.min(8, choice + 1);
		return 1 << shift;
	}

	static int effectiveThreads(Entry entry) {
		int max = maxThreads(entry);
		if (threadTierChoice(entry) <= 0 || entry == null) {
			return 1;
		}
		int selected = entry.getMachineSettingValue(threadCountSpec());
		if (selected <= 0) {
			return max;
		}
		return Math.max(1, Math.min(max, selected));
	}

	static boolean accelerationSelected(Entry entry) {
		return accelerationTier(entry) > 0;
	}

	static boolean threadSelected(Entry entry) {
		return threadTierChoice(entry) > 0;
	}

	static int overclockTierChoice(Entry entry) {
		if (entry == null) {
			return 0;
		}
		return entry.getMachineSettingValue(overclockTierSpec());
	}

	static int overclockTier(Entry entry) {
		int choice = overclockTierChoice(entry);
		if (choice <= 0) {
			return 0;
		}
		return Math.min(14, choice + 7);
	}

	static int overclockMaxDivisor(Entry entry) {
		int choice = overclockTierChoice(entry);
		return choice <= 0 ? 1 : Math.min(8, choice + 1);
	}

	static int overclockEffectiveDivisor(Entry entry) {
		int max = overclockMaxDivisor(entry);
		if (max <= 1 || entry == null) {
			return 1;
		}
		int selected = entry.getMachineSettingValue(overclockDivisorSpec());
		if (selected <= 0) {
			return max;
		}
		return Math.max(2, Math.min(max, selected + 1));
	}

	static double overclockDurationMultiplier(Entry entry, double fallback) {
		int divisor = overclockEffectiveDivisor(entry);
		if (divisor <= 1) {
			return fallback;
		}
		return Math.min(fallback, 1.0D / divisor);
	}

	static boolean overclockSelected(Entry entry) {
		return overclockTierChoice(entry) > 0;
	}
}
