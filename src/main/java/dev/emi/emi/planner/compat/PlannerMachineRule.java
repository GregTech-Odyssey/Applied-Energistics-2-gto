package dev.emi.emi.planner.compat;

import dev.emi.emi.planner.ProductionPlanner.Entry;
import dev.emi.emi.planner.ProductionPlanner.MachineProfile;
import dev.emi.emi.planner.ProductionPlanner.MachineSettingSpec;

import java.util.List;

public interface PlannerMachineRule {
	PlannerMachineRule NONE = new PlannerMachineRule() {
	};

	default String id() {
		return "";
	}

	default List<MachineSettingSpec> settings(MachineProfile profile) {
		return List.of();
	}

	default int configuredMaxParallel(Entry entry, int fallback) {
		return fallback;
	}

	default boolean allowsRecipe(Entry entry) {
		return true;
	}

	default String constraintError(Entry entry) {
		return "";
	}

	default double durationMultiplier(Entry entry) {
		return 1.0D;
	}

	default double standardOcDurationMultiplier(Entry entry, double fallback) {
		return fallback;
	}

	default double ocDurationMultiplierForStep(Entry entry, int overclockIndex, double fallback) {
		return fallback;
	}

	default double energyMultiplier(Entry entry) {
		return 1.0D;
	}

	default double throughputMultiplier(Entry entry) {
		return 1.0D;
	}

	/**
	 * Multiplier applied only to recipe outputs when the machine changes recipe yield.
	 * Unlike throughputMultiplier, this does not scale recipe inputs or recipe executions.
	 */
	default double outputMultiplier(Entry entry) {
		return 1.0D;
	}

	default List<String> settingDetails(Entry entry, MachineSettingSpec spec) {
		return List.of();
	}

	default List<String> modifierDescriptions(MachineProfile profile) {
		return List.of();
	}
}
