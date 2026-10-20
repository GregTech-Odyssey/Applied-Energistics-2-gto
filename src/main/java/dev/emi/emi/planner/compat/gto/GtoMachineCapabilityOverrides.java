package dev.emi.emi.planner.compat.gto;

final class GtoMachineCapabilityOverrides {
	private GtoMachineCapabilityOverrides() {
	}

	static GtoMachineCapabilities forMachineId(String machineId) {
		return GtoMachineCapabilities.EMPTY;
	}
}
