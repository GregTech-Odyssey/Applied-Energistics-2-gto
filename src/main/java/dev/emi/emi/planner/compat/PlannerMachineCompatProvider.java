package dev.emi.emi.planner.compat;

import dev.emi.emi.api.stack.EmiStack;

import java.util.List;

public interface PlannerMachineCompatProvider {
	boolean isActive();

	PlannerMachineRuntimeOverride resolve(EmiStack machineStack, String machineId, List<String> tooltipLines);

	default String russianText(String key) {
		return null;
	}
}
