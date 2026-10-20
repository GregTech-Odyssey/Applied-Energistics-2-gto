package dev.emi.emi.planner.compat.gto;

record GtoMachineCapabilities(
		boolean parallelControl,
		boolean acceleration,
		boolean thread,
		boolean overclocking,
		boolean laserEnergy,
		boolean auxiliaryModules,
		boolean accelerationRequiresAuxiliary,
		boolean threadRequiresAuxiliary,
		boolean overclockingRequiresAuxiliary,
		int fixedParallel,
		double processingTimeMultiplier,
		double standardOcDurationMultiplier,
		boolean enablesPerfectOc,
		boolean disablesPerfectOc,
		int glassParallelBase,
		boolean casingTierLimit,
		boolean glassTierLimit,
		int coilParallelStepKelvin,
		int coilParallelFactor,
		GtoVoltageParallelFormula voltageParallelFormula,
		GtoConfiguredTierParallelFormula configuredTierParallelFormula,
		GtoCoilLogParallelFormula coilLogParallelFormula,
		GtoGlassDurationFormula glassDurationFormula,
		GtoCoilTierEfficiencyFormula coilTierEfficiencyFormula,
		GtoCoilTemperatureDurationFormula coilTemperatureDurationFormula,
		GtoCoilExponentialDurationFormula coilExponentialDurationFormula,
		int auxiliaryParallel,
		double auxiliaryDurationMultiplier,
		GtoConfiguredCountDurationFormula configuredCountDurationFormula,
		GtoStructureTemperatureFormula structureTemperatureFormula,
		GtoTierDurationFormula tierDurationFormula,
		GtoTierParallelFormula tierParallelFormula,
		GtoProductionBoostFormula productionBoostFormula,
		GtoNeutronFluxDurationFormula neutronFluxDurationFormula,
		GtoFissionCoolingFormula fissionCoolingFormula) {

	static final GtoMachineCapabilities EMPTY = new GtoMachineCapabilities(
		false, false, false, false, false, false, false, false, false,
		0, 0.0D, 0.0D, false, false, 0, false, false, 0, 0,
		null, null, null, null, null, null, null, 0, 0.0D, null, null, null, null, null, null, null);

	GtoMachineCapabilities merge(GtoMachineCapabilities other) {
		if (other == null) {
			return this;
		}
		return new GtoMachineCapabilities(
			parallelControl || other.parallelControl,
			acceleration || other.acceleration,
			thread || other.thread,
			overclocking || other.overclocking,
			laserEnergy || other.laserEnergy,
			auxiliaryModules || other.auxiliaryModules,
			accelerationRequiresAuxiliary || other.accelerationRequiresAuxiliary,
			threadRequiresAuxiliary || other.threadRequiresAuxiliary,
			overclockingRequiresAuxiliary || other.overclockingRequiresAuxiliary,
			other.fixedParallel > 0 ? other.fixedParallel : fixedParallel,
			other.processingTimeMultiplier > 0.0D ? other.processingTimeMultiplier : processingTimeMultiplier,
			other.standardOcDurationMultiplier > 0.0D ? other.standardOcDurationMultiplier : standardOcDurationMultiplier,
			enablesPerfectOc || other.enablesPerfectOc,
			disablesPerfectOc || other.disablesPerfectOc,
			other.glassParallelBase > 1 ? other.glassParallelBase : glassParallelBase,
			casingTierLimit || other.casingTierLimit,
			glassTierLimit || other.glassTierLimit,
			other.coilParallelStepKelvin > 0 ? other.coilParallelStepKelvin : coilParallelStepKelvin,
			other.coilParallelFactor > 1 ? other.coilParallelFactor : coilParallelFactor,
			other.voltageParallelFormula != null ? other.voltageParallelFormula : voltageParallelFormula,
			other.configuredTierParallelFormula != null ? other.configuredTierParallelFormula : configuredTierParallelFormula,
			other.coilLogParallelFormula != null ? other.coilLogParallelFormula : coilLogParallelFormula,
			other.glassDurationFormula != null ? other.glassDurationFormula : glassDurationFormula,
			other.coilTierEfficiencyFormula != null ? other.coilTierEfficiencyFormula : coilTierEfficiencyFormula,
			other.coilTemperatureDurationFormula != null ? other.coilTemperatureDurationFormula : coilTemperatureDurationFormula,
			other.coilExponentialDurationFormula != null ? other.coilExponentialDurationFormula : coilExponentialDurationFormula,
			other.auxiliaryParallel > 0 ? other.auxiliaryParallel : auxiliaryParallel,
			other.auxiliaryDurationMultiplier > 0.0D ? other.auxiliaryDurationMultiplier : auxiliaryDurationMultiplier,
			other.configuredCountDurationFormula != null ? other.configuredCountDurationFormula : configuredCountDurationFormula,
			other.structureTemperatureFormula != null ? other.structureTemperatureFormula : structureTemperatureFormula,
			other.tierDurationFormula != null ? other.tierDurationFormula : tierDurationFormula,
			other.tierParallelFormula != null ? other.tierParallelFormula : tierParallelFormula,
			other.productionBoostFormula != null ? other.productionBoostFormula : productionBoostFormula,
			other.neutronFluxDurationFormula != null ? other.neutronFluxDurationFormula : neutronFluxDurationFormula,
			other.fissionCoolingFormula != null ? other.fissionCoolingFormula : fissionCoolingFormula
		);
	}

	boolean needsGlassTierSetting() {
		return glassParallelBase > 1 || glassTierLimit || glassDurationFormula != null;
	}

	boolean needsCoilSetting() {
		return hasCoilParallel() || coilLogParallelFormula != null || coilTierEfficiencyFormula != null
			|| coilTemperatureDurationFormula != null || coilExponentialDurationFormula != null;
	}

	boolean hasRuleMechanics() {
		return parallelControl || acceleration || thread || overclocking || auxiliaryModules
			|| needsGlassTierSetting() || casingTierLimit || needsCoilSetting() || auxiliaryParallel > 0
			|| auxiliaryDurationMultiplier > 0.0D || voltageParallelFormula != null || configuredTierParallelFormula != null
			|| configuredCountDurationFormula != null || structureTemperatureFormula != null || tierDurationFormula != null
			|| tierParallelFormula != null || productionBoostFormula != null || neutronFluxDurationFormula != null || fissionCoolingFormula != null;
	}

	boolean hasRuntimeOverrides() {
		return fixedParallel > 0 || processingTimeMultiplier > 0.0D || standardOcDurationMultiplier > 0.0D
			|| enablesPerfectOc || disablesPerfectOc;
	}

	boolean hasAny() {
		return hasRuleMechanics() || hasRuntimeOverrides() || laserEnergy;
	}

	String summary() {
		java.util.List<String> parts = new java.util.ArrayList<>();
		if (parallelControl) parts.add("parallel-hatch");
		if (acceleration) parts.add("acceleration-hatch");
		if (thread) parts.add("thread-hatch");
		if (overclocking) parts.add("overclock-hatch");
		if (laserEnergy) parts.add("laser-energy");
		if (auxiliaryModules) parts.add("auxiliary-modules");
		if (accelerationRequiresAuxiliary) parts.add("acceleration-needs-aux");
		if (threadRequiresAuxiliary) parts.add("thread-needs-aux");
		if (overclockingRequiresAuxiliary) parts.add("overclock-needs-aux");
		if (glassParallelBase > 1) parts.add("glass-parallel");
		if (casingTierLimit) parts.add("casing-tier-limit");
		if (glassTierLimit) parts.add("glass-tier-limit");
		if (hasCoilParallel()) parts.add("coil-parallel");
		if (voltageParallelFormula != null) parts.add("voltage-tier-parallel");
		if (configuredTierParallelFormula != null) parts.add(configuredTierParallelFormula.summary());
		if (coilLogParallelFormula != null) parts.add("coil-log-parallel");
		if (glassDurationFormula != null) parts.add(glassDurationFormula.summary());
		if (coilTierEfficiencyFormula != null) parts.add(coilTierEfficiencyFormula.summary());
		if (coilTemperatureDurationFormula != null) parts.add("coil-log-duration");
		if (coilExponentialDurationFormula != null) parts.add("coil-exp-duration");
		if (auxiliaryParallel > 0) parts.add("aux-parallel=" + auxiliaryParallel);
		if (auxiliaryDurationMultiplier > 0.0D) parts.add("aux-duration=x" + auxiliaryDurationMultiplier);
		if (configuredCountDurationFormula != null) parts.add("configured-count-duration");
		if (structureTemperatureFormula != null) parts.add("structure-temp-parallel-duration");
		if (tierDurationFormula != null) parts.add("tier-duration");
		if (tierParallelFormula != null) parts.add("tier-parallel");
		if (productionBoostFormula != null) parts.add("production-boost");
		if (neutronFluxDurationFormula != null) parts.add("neutron-flux-duration");
		if (fissionCoolingFormula != null) parts.add("fission-cooling-parallel");
		if (fixedParallel > 0) parts.add("fixed-parallel=" + fixedParallel);
		if (processingTimeMultiplier > 0.0D) parts.add("duration=x" + processingTimeMultiplier);
		if (standardOcDurationMultiplier > 0.0D) parts.add("oc=x" + standardOcDurationMultiplier);
		if (enablesPerfectOc) parts.add("perfect-oc");
		if (disablesPerfectOc) parts.add("no-perfect-oc");
		return String.join(", ", parts);
	}

	boolean hasCoilParallel() {
		return coilParallelStepKelvin > 0 && coilParallelFactor > 1;
	}
}

record GtoVoltageParallelFormula(int base, int referenceTier) {
	GtoVoltageParallelFormula {
		base = Math.max(1, base);
		referenceTier = Math.max(0, referenceTier);
	}
}

record GtoConfiguredTierParallelFormula(
		String settingKey,
		String labelKey,
		String englishLabel,
		String helpKey,
		String englishHelp,
		int minTier,
		int maxTier,
		int defaultTier,
		Mode mode,
		double factorOrBase,
		int exponentOffset) {
	enum Mode { LINEAR, POWER }

	GtoConfiguredTierParallelFormula {
		settingKey = settingKey == null ? "gto_configured_tier" : settingKey;
		labelKey = labelKey == null ? "" : labelKey;
		englishLabel = englishLabel == null ? "Structure Tier" : englishLabel;
		helpKey = helpKey == null ? "" : helpKey;
		englishHelp = englishHelp == null ? "Installed structure tier controls built-in parallel" : englishHelp;
		minTier = Math.max(0, minTier);
		maxTier = Math.max(minTier, maxTier);
		defaultTier = Math.max(minTier, Math.min(maxTier, defaultTier));
		mode = mode == null ? Mode.LINEAR : mode;
		factorOrBase = Double.isFinite(factorOrBase) && factorOrBase > 0.0D ? factorOrBase : 1.0D;
	}

	String summary() {
		return mode == Mode.POWER ? "configured-tier-power-parallel" : "configured-tier-linear-parallel";
	}
}

record GtoCoilLogParallelFormula(double logBase, int temperatureOffset, double subtract, int minimumParallel) {
	GtoCoilLogParallelFormula {
		logBase = Double.isFinite(logBase) && logBase > 1.0D ? logBase : 2.0D;
		temperatureOffset = Math.max(0, temperatureOffset);
		subtract = Double.isFinite(subtract) ? subtract : 0.0D;
		minimumParallel = Math.max(1, minimumParallel);
	}
}

record GtoGlassDurationFormula(Mode mode, double base) {
	enum Mode { SQRT_RECIPROCAL, POWER_RECIPROCAL }

	GtoGlassDurationFormula {
		mode = mode == null ? Mode.SQRT_RECIPROCAL : mode;
		base = Double.isFinite(base) && base > 1.0D ? base : 1.1D;
	}

	String summary() {
		return mode == Mode.POWER_RECIPROCAL ? "glass-duration-power" : "glass-duration-sqrt";
	}
}

record GtoCoilTierEfficiencyFormula(int referenceChoice, double durationReductionPerTier, double energyReductionPerTier) {
	GtoCoilTierEfficiencyFormula {
		referenceChoice = Math.max(1, referenceChoice);
		durationReductionPerTier = sanitizeReduction(durationReductionPerTier);
		energyReductionPerTier = sanitizeReduction(energyReductionPerTier);
	}

	private static double sanitizeReduction(double value) {
		if (!Double.isFinite(value) || value <= 0.0D) return 0.0D;
		return Math.min(0.95D, value);
	}

	String summary() {
		if (durationReductionPerTier > 0.0D && energyReductionPerTier > 0.0D) return "coil-tier-duration-energy";
		if (durationReductionPerTier > 0.0D) return "coil-tier-duration";
		return "coil-tier-energy";
	}
}

record GtoCoilTemperatureDurationFormula(double numeratorKelvin) {
	GtoCoilTemperatureDurationFormula {
		numeratorKelvin = Double.isFinite(numeratorKelvin) && numeratorKelvin > 1.0D ? numeratorKelvin : 900.0D;
	}
}

record GtoCoilExponentialDurationFormula(double leadingMultiplier, double exponentialBase, int temperatureOffset, int temperatureScale) {
	GtoCoilExponentialDurationFormula {
		leadingMultiplier = Double.isFinite(leadingMultiplier) && leadingMultiplier > 0.0D ? leadingMultiplier : 1.0D;
		exponentialBase = Double.isFinite(exponentialBase) && exponentialBase > 0.0D ? exponentialBase : 1.0D;
		temperatureOffset = Math.max(0, temperatureOffset);
		temperatureScale = Math.max(1, temperatureScale);
	}
}

record GtoConfiguredCountDurationFormula(
		String settingKey, String labelKey, String englishLabel, String helpKey, String englishHelp,
		int minValue, int maxValue, int defaultValue, double base) {
	GtoConfiguredCountDurationFormula {
		settingKey = settingKey == null ? "gto_configured_count" : settingKey;
		labelKey = labelKey == null ? "" : labelKey;
		englishLabel = englishLabel == null ? "Additional Blocks" : englishLabel;
		helpKey = helpKey == null ? "" : helpKey;
		englishHelp = englishHelp == null ? "Configured block count controls duration" : englishHelp;
		minValue = Math.max(0, minValue);
		maxValue = Math.max(minValue, maxValue);
		defaultValue = Math.max(minValue, Math.min(maxValue, defaultValue));
		base = Double.isFinite(base) && base > 0.0D ? base : 1.0D;
	}
}

record GtoStructureTemperatureFormula(int parallelPerLayer, int parallelTemperatureStepKelvin, double durationNumeratorKelvin) {
	GtoStructureTemperatureFormula {
		parallelPerLayer = Math.max(1, parallelPerLayer);
		parallelTemperatureStepKelvin = Math.max(1, parallelTemperatureStepKelvin);
		durationNumeratorKelvin = Double.isFinite(durationNumeratorKelvin) && durationNumeratorKelvin > 0.0D
			? durationNumeratorKelvin : 400.0D;
	}
}

record GtoTierDurationFormula(
		int firstEndTier, double firstReductionPerTier, int secondEndTier, double secondReductionPerTier, double floorMultiplier) {
	GtoTierDurationFormula {
		firstEndTier = Math.max(1, firstEndTier);
		firstReductionPerTier = Math.max(0.0D, Math.min(0.95D, firstReductionPerTier));
		secondEndTier = Math.max(firstEndTier, secondEndTier);
		secondReductionPerTier = Math.max(0.0D, Math.min(0.95D, secondReductionPerTier));
		floorMultiplier = Math.max(0.01D, Math.min(1.0D, floorMultiplier));
	}
}


record GtoTierParallelFormula(int minTier, java.util.List<Long> values) {
	GtoTierParallelFormula {
		minTier = Math.max(0, minTier);
		if (values == null || values.isEmpty()) {
			values = java.util.List.of();
		} else {
			java.util.List<Long> sanitized = new java.util.ArrayList<>(values.size());
			for (Long value : values) {
				sanitized.add(value == null || value < 1L ? 1L : value);
			}
			values = java.util.List.copyOf(sanitized);
		}
	}

	int maxTier() {
		return values.isEmpty() ? minTier : minTier + values.size() - 1;
	}

	long parallelForTier(int tier) {
		if (values.isEmpty()) return 1L;
		int clamped = Math.max(minTier, Math.min(maxTier(), tier));
		return values.get(clamped - minTier);
	}
}


record GtoProductionBoostFormula(double outputMultiplier, double durationMultiplier, double energyMultiplier, boolean enabledByDefault) {
	GtoProductionBoostFormula {
		outputMultiplier = sanitize(outputMultiplier);
		durationMultiplier = sanitize(durationMultiplier);
		energyMultiplier = sanitize(energyMultiplier);
	}

	private static double sanitize(double value) {
		return Double.isFinite(value) && value > 0.0D ? value : 1.0D;
	}
}


record GtoNeutronFluxDurationFormula(double baseTerm, double divisorMeV, double minimumMultiplier) {
	GtoNeutronFluxDurationFormula {
		baseTerm = Double.isFinite(baseTerm) && baseTerm > 0.0D ? baseTerm : 0.9D;
		divisorMeV = Double.isFinite(divisorMeV) && divisorMeV > 0.0D ? divisorMeV : 10.0D;
		minimumMultiplier = Double.isFinite(minimumMultiplier) && minimumMultiplier > 0.0D
			? Math.min(1.0D, minimumMultiplier) : 0.01D;
	}
}

record GtoFissionCoolingFormula(double temperatureDivisor, double adjacentDivisor, double supplyPerComponent) {
	GtoFissionCoolingFormula {
		temperatureDivisor = Double.isFinite(temperatureDivisor) && temperatureDivisor > 0.0D ? temperatureDivisor : 1500.0D;
		adjacentDivisor = Double.isFinite(adjacentDivisor) && adjacentDivisor > 0.0D ? adjacentDivisor : 3.0D;
		supplyPerComponent = Double.isFinite(supplyPerComponent) && supplyPerComponent > 0.0D ? supplyPerComponent : 8.0D;
	}
}
