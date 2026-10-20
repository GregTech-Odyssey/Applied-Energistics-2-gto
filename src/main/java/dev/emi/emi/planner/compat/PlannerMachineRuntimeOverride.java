package dev.emi.emi.planner.compat;

import java.util.ArrayList;
import java.util.List;

public record PlannerMachineRuntimeOverride(
		Integer fixedVoltageTier,
		Integer maxParallel,
		Boolean perfectOcKnown,
		Boolean allowsPerfectOc,
		Double durationMultiplier,
		Double energyMultiplier,
		Boolean parallelControl,
		Double coilEfficiencyPerTier,
		Double standardOcDurationMultiplier,
		PlannerMachineRule machineRule,
		List<String> notes) {
	public PlannerMachineRuntimeOverride {
		machineRule = machineRule == null ? PlannerMachineRule.NONE : machineRule;
		notes = notes == null ? List.of() : List.copyOf(notes);
	}

	public PlannerMachineRuntimeOverride merge(PlannerMachineRuntimeOverride other) {
		if (other == null) {
			return this;
		}
		List<String> mergedNotes = new ArrayList<>(notes);
		mergedNotes.addAll(other.notes);
		return new PlannerMachineRuntimeOverride(
			other.fixedVoltageTier != null ? other.fixedVoltageTier : fixedVoltageTier,
			other.maxParallel != null ? other.maxParallel : maxParallel,
			other.perfectOcKnown != null ? other.perfectOcKnown : perfectOcKnown,
			other.allowsPerfectOc != null ? other.allowsPerfectOc : allowsPerfectOc,
			multiply(durationMultiplier, other.durationMultiplier),
			multiply(energyMultiplier, other.energyMultiplier),
			other.parallelControl != null ? other.parallelControl : parallelControl,
			other.coilEfficiencyPerTier != null ? other.coilEfficiencyPerTier : coilEfficiencyPerTier,
			other.standardOcDurationMultiplier != null ? other.standardOcDurationMultiplier : standardOcDurationMultiplier,
			PlannerMachineRules.compose(machineRule, other.machineRule),
			List.copyOf(mergedNotes)
		);
	}

	private static Double multiply(Double first, Double second) {
		if (first == null) {
			return second;
		}
		if (second == null) {
			return first;
		}
		return first * second;
	}
}
