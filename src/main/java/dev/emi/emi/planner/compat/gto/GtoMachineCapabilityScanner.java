package dev.emi.emi.planner.compat.gto;

import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.planner.compat.PlannerMachineIntrospection;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GtoMachineCapabilityScanner {
	private static final Pattern PROCESSING_TIME = Pattern.compile("(?i)processing\\s+time\\s+multiplier\\s*[:=]\\s*([0-9]+(?:\\.[0-9]+)?)");
	private static final Pattern FIXED_PARALLEL = Pattern.compile("(?i)(?:\\([^)]*\\)\\s*)?parallels?\\s*[:=]\\s*([0-9][0-9,._]*)\\s*(?:$|[,;])");
	private static final Pattern MAX_PARALLEL = Pattern.compile("(?i)(?:supports\\s+up\\s+to|max(?:imum)?(?:\\s+of)?)\\s*([0-9][0-9,._]*)\\s*parallels?");
	private static final Pattern OC_PERCENT = Pattern.compile("(?i)(?:time|duration)[^%]{0,80}(?:multiplied\\s+by|multiplier\\s*[:=x]?)\\s*([0-9]+(?:\\.[0-9]+)?)\\s*%");
	private static final Pattern GLASS_POWER = Pattern.compile("(?i)([0-9]+)\\s*\\^\\s*\\(?\\s*glass\\s*tier");
	private static final Pattern COIL_PARALLEL = Pattern.compile("(?i)for\\s+each\\s+([0-9]+)\\s*k[^,;]*coil\\s+temperature[^,;]*[,;:]?\\s*([0-9]+)\\s*x\\s*parallels?");
	private static final Pattern COIL_FORMULA = Pattern.compile("(?i)([0-9]+)\\s*\\^.*temperature\\s*/\\s*([0-9]+)");
	private static final Pattern VOLTAGE_TIER_PARALLEL = Pattern.compile("(?i)for\\s+(?:each|every)\\s+voltage\\s+tier\\s+above\\s+([a-z]+)[^0-9]{0,40}([0-9]+)\\s*[x×]\\s*parallels?");
	private static final Pattern VOLTAGE_TIER_PARALLEL_REVERSED = Pattern.compile("(?i)([0-9]+)\\s*[x×]\\s*parallels?.{0,80}?for\\s+(?:each|every)\\s+voltage\\s+tier\\s+above\\s+([a-z]+)");
	private static final Pattern CONFIG_LINEAR_PARALLEL = Pattern.compile("(?i)parallels?\\s*[:=]\\s*\\(?\\s*([^)]*?tier)\\s*\\)?\\s*[x×*]\\s*([0-9]+)");
	private static final Pattern POWER_MODULE_PARALLEL = Pattern.compile("(?i)parallels?\\s*[:=]\\s*([0-9]+)\\s*\\^\\s*\\(\\s*power\\s+module\\s+tier\\s*-\\s*([0-9]+)\\s*\\)");
	private static final Pattern GLASS_DURATION_POWER = Pattern.compile("(?i)1\\s*/\\s*([0-9]+(?:\\.[0-9]+)?)\\s*\\^\\s*glass\\s+tier");
	private static final Pattern COIL_LOG_PARALLEL = Pattern.compile("(?i)parallels?\\s*=\\s*log\\s*([0-9]+(?:\\.[0-9]+)?)\\s*\\(\\s*temperature\\s*-\\s*([0-9]+)\\s*\\)\\s*-\\s*([0-9]+(?:\\.[0-9]+)?)");
	private static final Pattern MINIMUM_AFTER_FORMULA = Pattern.compile("(?:>=|≥)\\s*([0-9]+)");
	private static final Pattern COIL_TIER_REDUCTION = Pattern.compile("(?i)(?:each|every)\\s+coil(?:\\s+tier)?\\s+(?:above|after)\\s+cupronickel.*?([0-9]+(?:\\.[0-9]+)?)\\s*%");
	private static final Pattern COIL_LOG_DURATION = Pattern.compile("(?i)(?:speed|time|duration)\\s+multiplier\\s*[:=]\\s*log\\s*\\(\\s*([0-9]+(?:\\.[0-9]+)?)\\s*\\)\\s*/\\s*log\\s*\\(\\s*temperature\\s*\\)");
	private static final Pattern CONTINUOUS_DURATION_REDUCTION = Pattern.compile("(?i)after\\s+(?:the\\s+)?first\\s+run.*?([0-9]+(?:\\.[0-9]+)?)\\s*%\\s*duration\\s+reduction");
	private static final Pattern COIL_EXP_DURATION = Pattern.compile(
		"(?i)(?:time|duration)\\s+multiplier\\s*[:=]\\s*([0-9]+(?:\\.[0-9]+)?)\\s*[x×*]\\s*([0-9]+(?:\\.[0-9]+)?)\\s*\\^\\s*\\(\\s*\\(?\\s*temperature\\s*-\\s*([0-9]+)\\s*\\)?\\s*/\\s*([0-9]+)\\s*\\)");
	private static final Pattern AUXILIARY_PARALLEL = Pattern.compile("(?i)parallelism\\s+increases\\s+to\\s+([0-9]+)\\s*[x×]");
	private static final Pattern AUXILIARY_DURATION = Pattern.compile("(?i)gain\\s+([0-9]+(?:\\.[0-9]+)?)\\s*[x×]\\s*duration\\s+reduction");
	private static final Pattern ADDITIONAL_BLOCK_DURATION = Pattern.compile("(?i)efficiency\\s+formula\\s*[:=]\\s*([0-9]+(?:\\.[0-9]+)?)\\s*\\^\\s*number\\s+of\\s+additional\\s+blocks");
	private static final Pattern ENERGY_UNIT_PARALLEL = Pattern.compile("(?i)for\\s+each\\s+([0-9][0-9,._]*)\\s*eu\\s+consumed\\s*,?\\s*\\+?1\\s+parallel");
	private static final Pattern STEEL_FRAME_PARALLEL = Pattern.compile("(?i)each\\s+layer\\s+of\\s+steel\\s+frames\\s+provides\\s+([0-9]+)\\s+parallels?");
	private static final Pattern TEMPERATURE_PARALLEL_STEP = Pattern.compile("(?i)([0-9]+(?:\\.[0-9]+)?)\\s*[x×]\\s+parallel\\s+multiplier.*?every\\s+([0-9]+)\\s*k");
	private static final Pattern TEMPERATURE_DURATION_RECIPROCAL = Pattern.compile("(?i)duration\\s+multiplier\\s+reduction\\s+of\\s*\\(\\s*([0-9]+(?:\\.[0-9]+)?)\\s*/\\s*temperature\\s*\\)");
	private static final Pattern TIER_DURATION_PIECEWISE = Pattern.compile("(?i)tier1-([0-9]+).*?-([0-9]+(?:\\.[0-9]+)?)%\\s+per\\s+tier.*?tier[0-9]+-([0-9]+).*?-([0-9]+(?:\\.[0-9]+)?)%\\s+per\\s+tier.*?fixed\\s+-([0-9]+(?:\\.[0-9]+)?)%");
	private static final Pattern SAME_RECIPE_ROBOT_PARALLEL = Pattern.compile("(?i)parallelism\\s+of\\s*[x×]?\\s*([0-9]+)\\s+for\\s+the\\s+same\\s+recipe\\s+robots?");
	private static final Pattern NEUTRON_FLUX_DURATION = Pattern.compile("(?i)0\\.9\\s*-\\s*\\(\\s*current\\s+neutron\\s+flux\\s*-\\s*required\\s+neutron\\s+flux\\s*\\)\\s*/\\s*([0-9]+(?:\\.[0-9]+)?)\\s*mev");
	private static final Pattern FISSION_DEMAND = Pattern.compile("(?i)demand\\s*=\\s*recipe\\s+heat\\s+generation\\s*[x×*]\\s*actual\\s+parallel\\s*[x×*]\\s*current\\s+temperature\\s*/\\s*([0-9]+(?:\\.[0-9]+)?)");
	private static final Pattern FISSION_SUPPLY = Pattern.compile("(?i)supply\\s*=\\s*\\(\\s*cooling\\s+component\\s+count\\s*-\\s*\\(\\s*cooling\\s+component\\s*\\)\\s*adjacent\\s+count\\s*/\\s*([0-9]+(?:\\.[0-9]+)?)\\s*\\)\\s*[x×*]\\s*([0-9]+(?:\\.[0-9]+)?)");
	private static final Pattern PRODUCTION_BOOST_TIME = Pattern.compile("(?i)production-boosting\\s+mode.*?recipe\\s+time.*?penalty\\s*[:=]\\s*([0-9]+(?:\\.[0-9]+)?)\\s*[x×]");
	private static final Pattern PRODUCTION_BOOST_ENERGY = Pattern.compile("(?i)production-boosting\\s+mode.*?(?:steam|energy).*?penalty\\s*[:=]\\s*([0-9]+(?:\\.[0-9]+)?)\\s*[x×]");

	private GtoMachineCapabilityScanner() {
	}

	static GtoMachineCapabilities scan(EmiStack stack, String machineId, List<String> tooltipLines) {
		return scanDetailed(stack, machineId, tooltipLines).merged();
	}

	static ScanResult scanDetailed(EmiStack stack, String machineId, List<String> tooltipLines) {
		GtoMachineCapabilities detected = scanDetected(stack, tooltipLines);
		GtoMachineCapabilities override = GtoMachineCapabilityOverrides.forMachineId(machineId);
		return new ScanResult(detected, override, detected.merge(override));
	}

	private static GtoMachineCapabilities scanDetected(EmiStack stack, List<String> tooltipLines) {
		boolean parallel = false;
		boolean acceleration = false;
		boolean thread = false;
		boolean overclocking = false;
		boolean laser = false;
		boolean auxiliary = false;
		boolean accelerationNeedsAuxiliary = false;
		boolean threadNeedsAuxiliary = false;
		boolean overclockingNeedsAuxiliary = false;
		int fixedParallel = 0;
		double processingTime = 0.0D;
		double standardOc = 0.0D;
		boolean enablesPerfectOc = false;
		boolean disablesPerfectOc = false;
		int glassBase = 0;
		boolean casingTierLimit = false;
		boolean glassTierLimit = false;
		int coilStep = 0;
		int coilFactor = 0;
		GtoVoltageParallelFormula voltageParallel = null;
		GtoConfiguredTierParallelFormula configuredTierParallel = null;
		GtoCoilLogParallelFormula coilLogParallel = null;
		GtoGlassDurationFormula glassDuration = null;
		GtoCoilTierEfficiencyFormula coilTierEfficiency = null;
		GtoCoilTemperatureDurationFormula coilTemperatureDuration = null;
		GtoCoilExponentialDurationFormula coilExponentialDuration = null;
		int auxiliaryParallel = 0;
		double auxiliaryDurationMultiplier = 0.0D;
		GtoConfiguredCountDurationFormula configuredCountDuration = null;
		double additionalBlockDurationBase = 0.0D;
		GtoStructureTemperatureFormula structureTemperature = null;
		GtoTierDurationFormula tierDuration = null;
		GtoTierParallelFormula tierParallel = null;
		GtoProductionBoostFormula productionBoost = null;
		boolean productionBoostSeen = false;
		boolean productionBoostDefault = false;
		double productionBoostDuration = 0.0D;
		double productionBoostEnergy = 0.0D;
		GtoNeutronFluxDurationFormula neutronFluxDuration = null;
		GtoFissionCoolingFormula fissionCooling = null;
		int steelFrameParallelPerLayer = 0;
		int temperatureParallelStep = 0;
		double temperatureDurationNumerator = 0.0D;

		StringBuilder joined = new StringBuilder();
		for (String raw : tooltipLines == null ? List.<String>of() : tooltipLines) {
			String line = raw == null ? "" : raw.trim();
			String lower = line.toLowerCase(Locale.ROOT);
			String normalized = normalizeFormulaText(line);
			String normalizedLower = normalized.toLowerCase(Locale.ROOT);
			if (joined.length() > 0) {
				joined.append('\n');
			}
			joined.append(lower);
			if (lower.contains("production-boosting mode")) {
				productionBoostSeen = true;
				productionBoostDefault |= lower.contains("enabled by default");
				Matcher boostTime = PRODUCTION_BOOST_TIME.matcher(line);
				if (boostTime.find()) {
					productionBoostDuration = positiveDouble(boostTime.group(1));
				}
				Matcher boostEnergy = PRODUCTION_BOOST_ENERGY.matcher(line);
				if (boostEnergy.find()) {
					productionBoostEnergy = positiveDouble(boostEnergy.group(1));
				}
			}
			if (lower.contains("auxiliary module")) {
				auxiliary = true;
			}
			if (lower.contains("parallel control") && !negative(lower, "parallel control")) {
				parallel = true;
			}
			if ((lower.contains("acceleration hatch") || lower.contains("accelerate hatch")) && !negative(lower, "hatch")) {
				acceleration = true;
			}
			if ((lower.contains("thread hatch") || lower.contains("multi-threading") || lower.contains("multi-threaded")) && !negative(lower, "thread")) {
				thread = true;
			}
			if ((lower.contains("overclocking hatch") || lower.contains("overclock hatch")) && !negative(lower, "overclock")) {
				overclocking = true;
			}
			if (lower.contains("laser energy hatch")) {
				laser = true;
			}
			if (lower.contains("perfect overclock") || lower.contains("perfect oc")) {
				if (negativePerfectOc(lower)) {
					disablesPerfectOc = true;
				} else {
					enablesPerfectOc = true;
				}
			}
			if (lower.contains("unlocked hatch types")) {
				if (lower.contains("acceleration hatch") || lower.contains("accelerate hatch")) {
					accelerationNeedsAuxiliary = true;
				}
				if (lower.contains("thread hatch")) {
					threadNeedsAuxiliary = true;
				}
				if (lower.contains("overclocking hatch") || lower.contains("overclock hatch")) {
					overclockingNeedsAuxiliary = true;
				}
			}

			Matcher processing = PROCESSING_TIME.matcher(line);
			if (processing.find()) {
				processingTime = positiveDouble(processing.group(1));
			}
			if (!looksDynamicParallelLine(lower)) {
				Matcher fixed = FIXED_PARALLEL.matcher(line);
				if (fixed.find()) {
					fixedParallel = Math.max(fixedParallel, positiveInt(fixed.group(1)));
				}
				Matcher maximum = MAX_PARALLEL.matcher(line);
				if (maximum.find()) {
					fixedParallel = Math.max(fixedParallel, positiveInt(maximum.group(1)));
				}
			}
			Matcher oc = OC_PERCENT.matcher(line);
			if (oc.find() && (lower.contains("4") || lower.contains("overclock") || lower.contains("power"))) {
				double percent = positiveDouble(oc.group(1));
				if (percent > 0.0D && percent <= 100.0D) {
					standardOc = percent / 100.0D;
				}
			}
			Matcher coil = COIL_PARALLEL.matcher(line);
			if (coil.find()) {
				coilStep = positiveInt(coil.group(1));
				coilFactor = positiveInt(coil.group(2));
			}
			Matcher formula = COIL_FORMULA.matcher(line);
			if (formula.find()) {
				coilFactor = positiveInt(formula.group(1));
				coilStep = positiveInt(formula.group(2));
			}

			Matcher voltage = VOLTAGE_TIER_PARALLEL.matcher(line);
			if (voltage.find()) {
				int referenceTier = voltageTier(voltage.group(1));
				int base = positiveInt(voltage.group(2));
				if (referenceTier >= 0 && base > 1) {
					voltageParallel = new GtoVoltageParallelFormula(base, referenceTier);
				}
			} else {
				Matcher reversedVoltage = VOLTAGE_TIER_PARALLEL_REVERSED.matcher(line);
				if (reversedVoltage.find()) {
					int base = positiveInt(reversedVoltage.group(1));
					int referenceTier = voltageTier(reversedVoltage.group(2));
					if (referenceTier >= 0 && base > 1) {
						voltageParallel = new GtoVoltageParallelFormula(base, referenceTier);
					}
				}
			}

			Matcher powerModule = POWER_MODULE_PARALLEL.matcher(normalized);
			if (powerModule.find()) {
				int base = positiveInt(powerModule.group(1));
				int offset = positiveInt(powerModule.group(2));
				if (base > 1) {
					configuredTierParallel = new GtoConfiguredTierParallelFormula(
						"gto_power_module_tier", "gto.dynamic.power_module_tier", "Power Module Tier",
						"gto.dynamic.power_module_tier_help", "Installed Power Module tier controls built-in parallel",
						1, 15, 1, GtoConfiguredTierParallelFormula.Mode.POWER, base, offset);
				}
			} else {
				Matcher configuredLinear = CONFIG_LINEAR_PARALLEL.matcher(normalized);
				if (configuredLinear.find()) {
					String source = configuredLinear.group(1).trim().toLowerCase(Locale.ROOT);
					int factor = positiveInt(configuredLinear.group(2));
					if (source.contains("hermetic") && source.contains("casing") && factor > 0) {
						configuredTierParallel = new GtoConfiguredTierParallelFormula(
							"gto_hermetic_casing_tier", "gto.dynamic.hermetic_casing_tier", "Hermetic Casing Tier",
							"gto.dynamic.hermetic_casing_tier_help", "Installed Hermetic Casing tier controls built-in parallel",
							1, 15, 1, GtoConfiguredTierParallelFormula.Mode.LINEAR, factor, 0);
					}
				}
			}

			Matcher coilLog = COIL_LOG_PARALLEL.matcher(normalized);
			if (coilLog.find()) {
				double logBase = positiveDouble(coilLog.group(1));
				int temperatureOffset = positiveInt(coilLog.group(2));
				double subtract = positiveDouble(coilLog.group(3));
				int minimum = 1;
				Matcher min = MINIMUM_AFTER_FORMULA.matcher(normalized);
				if (min.find()) {
					minimum = Math.max(1, positiveInt(min.group(1)));
				}
				if (logBase > 1.0D && temperatureOffset > 0) {
					coilLogParallel = new GtoCoilLogParallelFormula(logBase, temperatureOffset, subtract, minimum);
				}
			}

			if (normalizedLower.contains("glass tier") && (normalizedLower.contains("time multiplier") || normalizedLower.contains("duration"))) {
				if (normalizedLower.contains("sqrt(1 / glass tier)") || normalizedLower.contains("sqrt(1/glass tier)")) {
					glassDuration = new GtoGlassDurationFormula(GtoGlassDurationFormula.Mode.SQRT_RECIPROCAL, 1.0D);
				} else {
					Matcher durationPower = GLASS_DURATION_POWER.matcher(normalized);
					if (durationPower.find()) {
						double base = positiveDouble(durationPower.group(1));
						if (base > 1.0D) {
							glassDuration = new GtoGlassDurationFormula(GtoGlassDurationFormula.Mode.POWER_RECIPROCAL, base);
						}
					}
				}
			}

			Matcher coilTierReduction = COIL_TIER_REDUCTION.matcher(normalized);
			if (coilTierReduction.find()) {
				double reduction = positiveDouble(coilTierReduction.group(1)) / 100.0D;
				boolean affectsDuration = normalizedLower.contains("duration") || normalizedLower.contains("recipe time") || normalizedLower.contains("time usage");
				boolean affectsEnergy = normalizedLower.contains("energy") || normalizedLower.contains("eu");
				if (reduction > 0.0D && (affectsDuration || affectsEnergy)) {
					double durationReduction = affectsDuration ? reduction : 0.0D;
					double energyReduction = affectsEnergy ? reduction : 0.0D;
					coilTierEfficiency = new GtoCoilTierEfficiencyFormula(1, durationReduction, energyReduction);
				}
			}

			Matcher coilLogDurationMatcher = COIL_LOG_DURATION.matcher(normalized);
			if (coilLogDurationMatcher.find()) {
				double numerator = positiveDouble(coilLogDurationMatcher.group(1));
				if (numerator > 1.0D) {
					coilTemperatureDuration = new GtoCoilTemperatureDurationFormula(numerator);
				}
			}

			Matcher coilExpDurationMatcher = COIL_EXP_DURATION.matcher(normalized);
			if (coilExpDurationMatcher.find()) {
				double leading = positiveDouble(coilExpDurationMatcher.group(1));
				double base = positiveDouble(coilExpDurationMatcher.group(2));
				int offset = positiveInt(coilExpDurationMatcher.group(3));
				int scale = positiveInt(coilExpDurationMatcher.group(4));
				if (leading > 0.0D && base > 0.0D && scale > 0) {
					coilExponentialDuration = new GtoCoilExponentialDurationFormula(leading, base, offset, scale);
				}
			}

			Matcher additionalBlockDuration = ADDITIONAL_BLOCK_DURATION.matcher(normalized);
			if (additionalBlockDuration.find()) {
				double base = positiveDouble(additionalBlockDuration.group(1));
				if (base > 0.0D && base <= 1.0D) {
					additionalBlockDurationBase = base;
				}
			}

			Matcher energyParallel = ENERGY_UNIT_PARALLEL.matcher(normalized);
			if (energyParallel.find()) {
				int euPerParallel = positiveInt(energyParallel.group(1));
				if (euPerParallel > 0) {
					String unit = euPerParallel >= 1000 && euPerParallel % 1000 == 0 ? (euPerParallel / 1000) + "k EU" : euPerParallel + " EU";
					configuredTierParallel = new GtoConfiguredTierParallelFormula(
						"gto_energy_parallel_units", "gto.dynamic.energy_parallel_units", unit + " Units Consumed",
						"gto.dynamic.energy_parallel_units_help", "Enter how many complete " + unit + " chunks are consumed per operation; each chunk adds one parallel",
						1, 1000000, 1, GtoConfiguredTierParallelFormula.Mode.LINEAR, 1.0D, 0);
				}
			}

			Matcher frameParallel = STEEL_FRAME_PARALLEL.matcher(normalized);
			if (frameParallel.find()) {
				steelFrameParallelPerLayer = positiveInt(frameParallel.group(1));
			}
			Matcher temperatureParallel = TEMPERATURE_PARALLEL_STEP.matcher(normalized);
			if (temperatureParallel.find()) {
				double multiplierPerStep = positiveDouble(temperatureParallel.group(1));
				int step = positiveInt(temperatureParallel.group(2));
				if (Math.abs(multiplierPerStep - 1.0D) < 0.000001D && step > 0) {
					temperatureParallelStep = step;
				}
			}
			Matcher temperatureDuration = TEMPERATURE_DURATION_RECIPROCAL.matcher(normalized);
			if (temperatureDuration.find()) {
				temperatureDurationNumerator = positiveDouble(temperatureDuration.group(1));
			}

			Matcher tierDurationMatcher = TIER_DURATION_PIECEWISE.matcher(normalized);
			if (tierDurationMatcher.find()) {
				int firstEnd = positiveInt(tierDurationMatcher.group(1));
				double firstReduction = positiveDouble(tierDurationMatcher.group(2)) / 100.0D;
				int secondEnd = positiveInt(tierDurationMatcher.group(3));
				double secondReduction = positiveDouble(tierDurationMatcher.group(4)) / 100.0D;
				double floor = 1.0D - positiveDouble(tierDurationMatcher.group(5)) / 100.0D;
				if (firstEnd > 0 && secondEnd >= firstEnd && firstReduction > 0.0D && secondReduction > 0.0D && floor > 0.0D) {
					tierDuration = new GtoTierDurationFormula(firstEnd, firstReduction, secondEnd, secondReduction, floor);
				}
			}

			Matcher sameRecipeRobots = SAME_RECIPE_ROBOT_PARALLEL.matcher(normalized);
			if (sameRecipeRobots.find()) {
				int factor = positiveInt(sameRecipeRobots.group(1));
				if (factor > 0) {
					configuredTierParallel = new GtoConfiguredTierParallelFormula(
						"gto_same_recipe_robots", "gto.dynamic.same_recipe_robots", "Same Recipe Robots",
						"gto.dynamic.same_recipe_robots_help", "Number of same-recipe robots; each contributes the detected built-in parallel",
						1, 256, 1, GtoConfiguredTierParallelFormula.Mode.LINEAR, factor, 0);
				}
			}

			Matcher continuousReduction = CONTINUOUS_DURATION_REDUCTION.matcher(normalized);
			if (continuousReduction.find()) {
				double percent = positiveDouble(continuousReduction.group(1));
				if (percent > 0.0D && percent < 100.0D) {
					processingTime = 1.0D - percent / 100.0D;
				}
			}
		}

		String tooltip = joined.toString();
		String normalizedTooltip = normalizeFormulaText(tooltip).toLowerCase(Locale.ROOT);
		if (additionalBlockDurationBase > 0.0D && normalizedTooltip.contains("additional speeding pipe")) {
			configuredCountDuration = new GtoConfiguredCountDurationFormula(
				"gto_additional_speeding_pipes", "gto.dynamic.additional_speeding_pipes", "Additional Speeding Pipes",
				"gto.dynamic.additional_speeding_pipes_help", "Additional Speeding Pipes reduce recipe duration using the detected efficiency formula",
				0, 256, 0, additionalBlockDurationBase);
		}

		Matcher auxiliaryParallelMatcher = AUXILIARY_PARALLEL.matcher(normalizedTooltip);
		if (auxiliary && normalizedTooltip.contains("after module installation") && auxiliaryParallelMatcher.find()) {
			auxiliaryParallel = positiveInt(auxiliaryParallelMatcher.group(1));
		}
		Matcher auxiliaryDurationMatcher = AUXILIARY_DURATION.matcher(normalizedTooltip);
		if (auxiliary && normalizedTooltip.contains("after module installation") && auxiliaryDurationMatcher.find()) {
			auxiliaryDurationMultiplier = positiveDouble(auxiliaryDurationMatcher.group(1));
		}
		if (steelFrameParallelPerLayer > 0 && temperatureParallelStep > 0 && temperatureDurationNumerator > 0.0D) {
			structureTemperature = new GtoStructureTemperatureFormula(
				steelFrameParallelPerLayer, temperatureParallelStep, temperatureDurationNumerator);
		}
		Matcher neutronFlux = NEUTRON_FLUX_DURATION.matcher(normalizedTooltip);
		if (neutronFlux.find() && normalizedTooltip.contains("required neutron flux")) {
			double divisorMeV = positiveDouble(neutronFlux.group(1));
			if (divisorMeV > 0.0D) {
				neutronFluxDuration = new GtoNeutronFluxDurationFormula(0.9D, divisorMeV, 0.01D);
			}
		}
		Matcher fissionDemand = FISSION_DEMAND.matcher(normalizedTooltip);
		Matcher fissionSupply = FISSION_SUPPLY.matcher(normalizedTooltip);
		if (fissionDemand.find() && fissionSupply.find()) {
			double temperatureDivisor = positiveDouble(fissionDemand.group(1));
			double adjacentDivisor = positiveDouble(fissionSupply.group(1));
			double supplyPerComponent = positiveDouble(fissionSupply.group(2));
			if (temperatureDivisor > 0.0D && adjacentDivisor > 0.0D && supplyPerComponent > 0.0D) {
				fissionCooling = new GtoFissionCoolingFormula(temperatureDivisor, adjacentDivisor, supplyPerComponent);
			}
		}
		disablesPerfectOc |= tooltip.contains("worse-than-imperfect overclock") || tooltip.contains("worse than imperfect overclock");
		if (disablesPerfectOc) {
			enablesPerfectOc = false;
		}
		Matcher glass = GLASS_POWER.matcher(tooltip);
		if (glass.find()) {
			glassBase = positiveInt(glass.group(1));
		}
		if (tooltip.contains("decided by glass tier") && glassBase <= 1) {
			glassBase = 4;
		}
		casingTierLimit = tooltip.contains("recipe tier is limited by machine casing tier")
			|| tooltip.contains("recipe tier cannot exceed") && tooltip.contains("casing tier");
		glassTierLimit = tooltip.contains("recipe tier is limited by glass tier")
			|| tooltip.contains("recipe tier cannot exceed") && tooltip.contains("glass tier");

		PlannerMachineIntrospection.Snapshot inspection = PlannerMachineIntrospection.inspect(stack);
		parallel |= inspection.containsAny("parallelcontrolhatch", "parallel_hatch", "parallelcontrol");
		acceleration |= inspection.containsAny("accelerationhatch", "acceleration_hatch", "acceleratehatch");
		thread |= inspection.containsAny("threadhatch", "thread_hatch", "multithread");
		overclocking |= inspection.containsAny("overclockinghatch", "overclock_hatch", "overclockhatch");
		laser |= inspection.containsAny("laserenergyhatch", "laser_energy_hatch");
		auxiliary |= inspection.containsAny("auxiliarymodule", "auxiliary_module");

		if (productionBoostSeen && productionBoostDuration > 0.0D && productionBoostEnergy > 0.0D) {
			Number output = PlannerMachineIntrospection.staticNumericField(stack, "PRODUCT_MULTIPLY");
			double outputMultiplier = output == null ? 0.0D : output.doubleValue();
			if (Double.isFinite(outputMultiplier) && outputMultiplier > 0.0D) {
				productionBoost = new GtoProductionBoostFormula(
					outputMultiplier, productionBoostDuration, productionBoostEnergy, productionBoostDefault);
			}
		}

		if (tierDuration != null && tooltip.contains("parallel multiplier") && tooltip.contains("tier")) {
			List<Long> tierParallels = PlannerMachineIntrospection.shortLongMethodTable(stack, "getMaxParallel", 1, 256);
			if (tierParallels.size() == 256) {
				tierParallel = new GtoTierParallelFormula(1, tierParallels);
			}
		}

		return new GtoMachineCapabilities(
			parallel, acceleration, thread, overclocking, laser, auxiliary,
			accelerationNeedsAuxiliary, threadNeedsAuxiliary, overclockingNeedsAuxiliary,
			fixedParallel, processingTime, standardOc, enablesPerfectOc, disablesPerfectOc,
			glassBase, casingTierLimit, glassTierLimit, coilStep, coilFactor,
			voltageParallel, configuredTierParallel, coilLogParallel, glassDuration,
			coilTierEfficiency, coilTemperatureDuration, coilExponentialDuration, auxiliaryParallel,
			auxiliaryDurationMultiplier, configuredCountDuration, structureTemperature, tierDuration, tierParallel, productionBoost,
			neutronFluxDuration, fissionCooling);
	}

	private static boolean negative(String line, String subject) {
		if (!line.contains(subject)) {
			return false;
		}
		return line.contains("no " + subject) || line.contains("not supported") || line.contains("unavailable")
			|| line.contains("disabled") || line.contains("✗");
	}

	private static boolean negativePerfectOc(String lower) {
		return lower.contains("not supported") || lower.contains("unavailable") || lower.contains("disabled")
			|| lower.contains("false") || lower.contains("✗") || lower.contains("worse-than-imperfect")
			|| lower.contains("worse than imperfect");
	}

	private static boolean looksDynamicParallelLine(String lower) {
		return lower.contains("for each") || lower.contains("for every") || lower.contains("every ")
			|| lower.contains("tier") || lower.contains("temperature") || lower.contains("formula")
			|| lower.contains("multiplier") || lower.contains("robots") || lower.contains("layer")
			|| lower.contains("plasma") || lower.contains("current neutron") || lower.contains("actual parallel")
			|| lower.contains("^") || lower.contains("log") || lower.contains("×") && lower.contains("(");
	}

	private static String normalizeFormulaText(String value) {
		if (value == null || value.isEmpty()) {
			return "";
		}
		return value
			.replaceAll("§.", "")
			.replace('₀', '0').replace('₁', '1').replace('₂', '2').replace('₃', '3').replace('₄', '4')
			.replace('₅', '5').replace('₆', '6').replace('₇', '7').replace('₈', '8').replace('₉', '9')
			.replace('−', '-').replace('–', '-').replace('—', '-')
			.replace("√", "sqrt")
			.replaceAll("\\s+", " ")
			.trim();
	}

	private static int voltageTier(String value) {
		if (value == null) {
			return -1;
		}
		return switch (value.trim().toLowerCase(Locale.ROOT)) {
			case "ulv" -> 0;
			case "lv" -> 1;
			case "mv" -> 2;
			case "hv" -> 3;
			case "ev" -> 4;
			case "iv" -> 5;
			case "luv" -> 6;
			case "zpm" -> 7;
			case "uv" -> 8;
			case "uhv" -> 9;
			case "uev" -> 10;
			case "uiv" -> 11;
			case "uxv" -> 12;
			case "opv" -> 13;
			case "max" -> 14;
			default -> -1;
		};
	}

	private static int positiveInt(String value) {
		if (value == null) {
			return 0;
		}
		String digits = value.replaceAll("[^0-9]", "");
		if (digits.isEmpty()) {
			return 0;
		}
		try {
			long parsed = Long.parseLong(digits);
			return (int) Math.min(parsed, 1_000_000_000L);
		} catch (Throwable ignored) {
			return 0;
		}
	}

	private static double positiveDouble(String value) {
		try {
			double parsed = Double.parseDouble(value);
			return Double.isFinite(parsed) && parsed > 0.0D ? parsed : 0.0D;
		} catch (Throwable ignored) {
			return 0.0D;
		}
	}

	record ScanResult(GtoMachineCapabilities detected, GtoMachineCapabilities override, GtoMachineCapabilities merged) {
	}
}
