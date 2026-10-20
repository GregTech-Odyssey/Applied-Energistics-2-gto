package dev.emi.emi.planner.compat.gto;

import dev.emi.emi.planner.PlannerText;
import dev.emi.emi.planner.ProductionPlanner;
import dev.emi.emi.planner.ProductionPlanner.Entry;
import dev.emi.emi.planner.ProductionPlanner.MachineProfile;
import dev.emi.emi.planner.ProductionPlanner.MachineSettingSpec;
import dev.emi.emi.planner.compat.PlannerMachineRule;
import dev.emi.emi.planner.compat.gtceu.GtceuHeatingCoilCatalog;

import java.util.ArrayList;
import java.util.List;

final class GtoCapabilityMachineRule implements PlannerMachineRule {
    private static final String GLASS_TIER = "glass_tier";
    private static final String CASING_TIER = "machine_casing_tier";
    private static final String COIL_PARALLEL = "gto_coil_parallel_coil";
    private static final String STEEL_FRAME_LAYERS = "gto_steel_frame_layers";
    private static final String CURRENT_TEMPERATURE = "gto_current_temperature_k";
    private static final String PROGRESSION_TIER = "gto_progression_tier";
    private static final String PRODUCTION_BOOST = "gto_production_boosting_mode";
    private static final String CURRENT_NEUTRON_FLUX_KEV = "gto_current_neutron_flux_kev";
    private static final String REQUIRED_NEUTRON_FLUX_KEV = "gto_required_neutron_flux_kev";
    private static final String FISSION_RECIPE_HEAT = "gto_fission_recipe_heat";
    private static final String FISSION_TEMPERATURE = "gto_fission_temperature";
    private static final String FISSION_COOLING_COMPONENTS = "gto_fission_cooling_components";
    private static final String FISSION_ADJACENT_COMPONENTS = "gto_fission_adjacent_components";
    private final GtoMachineCapabilities capabilities;

    GtoCapabilityMachineRule(GtoMachineCapabilities capabilities) {
        this.capabilities = capabilities == null ? GtoMachineCapabilities.EMPTY : capabilities;
    }

    @Override
    public String id() {
        return "gto:auto-capabilities";
    }

    @Override
    public List<MachineSettingSpec> settings(MachineProfile profile) {
        List<MachineSettingSpec> settings = new ArrayList<>();
        if (capabilities.needsGlassTierSetting()) {
            settings.add(GtoGlassCatalog.spec(
                GLASS_TIER, "gto.precision.glass_tier", "Glass",
                "gto.precision.glass_tier_help", "Installed glass controls the machine mechanics detected from its tooltip"));
        }
        if (capabilities.casingTierLimit()) {
            List<String> casingChoices = new ArrayList<>();
            casingChoices.add("AUTO");
            for (int tier = 0; tier <= ProductionPlanner.maxVoltageTier(); tier++) {
                casingChoices.add(ProductionPlanner.voltageTierName(tier));
            }
            settings.add(MachineSettingSpec.choice(
                CASING_TIER, "gto.precision.casing_tier", "Machine Casing Tier", casingChoices, 0,
                "gto.precision.casing_tier_help", "Recipe tier cannot exceed the selected Machine Casing Tier; AUTO uses the recipe minimum"));
        }
        if (needsCoilSetting()) {
            settings.add(GtceuHeatingCoilCatalog.spec(
                COIL_PARALLEL, "gto.auto.coil_parallel", "Heating Coil",
                "gto.auto.coil_parallel_help", "Installed heating coil controls the machine mechanics detected from its tooltip"));
        }
        if (capabilities.configuredTierParallelFormula() != null) {
            GtoConfiguredTierParallelFormula formula = capabilities.configuredTierParallelFormula();
            settings.add(MachineSettingSpec.integer(
                formula.settingKey(), formula.labelKey(), formula.englishLabel(), formula.minTier(), formula.maxTier(), formula.defaultTier(), 1,
                formula.helpKey(), formula.englishHelp()));
        }
        if (capabilities.configuredCountDurationFormula() != null) {
            GtoConfiguredCountDurationFormula formula = capabilities.configuredCountDurationFormula();
            settings.add(MachineSettingSpec.integer(
                formula.settingKey(), formula.labelKey(), formula.englishLabel(), formula.minValue(), formula.maxValue(), formula.defaultValue(), 1,
                formula.helpKey(), formula.englishHelp()));
        }
        if (capabilities.structureTemperatureFormula() != null) {
            settings.add(MachineSettingSpec.integer(
                STEEL_FRAME_LAYERS, "gto.dynamic.steel_frame_layers", "Steel Frame Layers", 1, 64, 1, 1,
                "gto.dynamic.steel_frame_layers_help", "Structure height: each Steel Frame layer contributes the detected base parallel"));
            settings.add(MachineSettingSpec.integer(
                CURRENT_TEMPERATURE, "gto.dynamic.current_temperature", "Current Temperature (K)", 1, 100000, 400, 100,
                "gto.dynamic.current_temperature_help", "Current internal machine temperature used by the detected parallel and duration formulas"));
        }
        if (capabilities.tierDurationFormula() != null || capabilities.tierParallelFormula() != null) {
            settings.add(MachineSettingSpec.integer(
                PROGRESSION_TIER, "gto.dynamic.progression_tier", "Progression Tier", 1, 256, 1, 1,
                "gto.dynamic.progression_tier_help", "Accumulated machine progression tier used by the detected duration and parallel bonuses"));
        }
        if (capabilities.productionBoostFormula() != null) {
            GtoProductionBoostFormula formula = capabilities.productionBoostFormula();
            settings.add(MachineSettingSpec.toggle(
                PRODUCTION_BOOST, "gto.dynamic.production_boost", "Production-Boosting Mode", formula.enabledByDefault(),
                "gto.dynamic.production_boost_help",
                "Multiplies recipe output while applying the detected recipe-time and energy/steam penalties"));
        }
        if (capabilities.neutronFluxDurationFormula() != null) {
            settings.add(MachineSettingSpec.integer(
                CURRENT_NEUTRON_FLUX_KEV, "gto.dynamic.current_neutron_flux", "Current Neutron Flux (keV)",
                0, 1_000_000_000, 0, 100, "gto.dynamic.current_neutron_flux_help",
                "Current neutron flux; 0 keeps the planner neutral until a flux is configured"));
            settings.add(MachineSettingSpec.integer(
                REQUIRED_NEUTRON_FLUX_KEV, "gto.dynamic.required_neutron_flux", "Required Neutron Flux (keV)",
                0, 1_000_000_000, 0, 100, "gto.dynamic.required_neutron_flux_help",
                "Recipe-required neutron flux; 0 keeps the planner neutral until a flux is configured"));
        }
        if (capabilities.fissionCoolingFormula() != null) {
            settings.add(MachineSettingSpec.integer(
                FISSION_RECIPE_HEAT, "gto.dynamic.fission_recipe_heat", "Recipe Heat Generation",
                0, 1_000_000_000, 0, 1, "gto.dynamic.fission_recipe_heat_help",
                "Recipe heat generation used by the detected cooling-demand formula; 0 leaves the formula unconfigured"));
            settings.add(MachineSettingSpec.integer(
                FISSION_TEMPERATURE, "gto.dynamic.fission_temperature", "Current Reactor Temperature",
                0, 1_000_000_000, 0, 100, "gto.dynamic.fission_temperature_help",
                "Current reactor temperature used by the detected cooling-demand formula; 0 leaves the formula unconfigured"));
            settings.add(MachineSettingSpec.integer(
                FISSION_COOLING_COMPONENTS, "gto.dynamic.fission_cooling_components", "Cooling Component Count",
                0, 1_000_000, 0, 1, "gto.dynamic.fission_cooling_components_help",
                "Total cooling components used by the detected cooling-supply formula"));
            settings.add(MachineSettingSpec.integer(
                FISSION_ADJACENT_COMPONENTS, "gto.dynamic.fission_adjacent_components", "Cooling Component Adjacent Count",
                0, 1_000_000, 0, 1, "gto.dynamic.fission_adjacent_components_help",
                "Adjacent cooling-component count subtracted by the detected supply formula"));
        }
        if (capabilities.auxiliaryModules() && (capabilities.accelerationRequiresAuxiliary()
                || capabilities.threadRequiresAuxiliary() || capabilities.overclockingRequiresAuxiliary()
                || capabilities.auxiliaryParallel() > 0 || capabilities.auxiliaryDurationMultiplier() > 0.0D)) {
            settings.add(GtoHatchCatalog.auxiliaryModulesSpec());
        }
        if (capabilities.parallelControl()) {
            settings.add(GtoHatchCatalog.parallelHatchLimitSpec());
        }
        if (capabilities.acceleration()) {
            settings.add(GtoHatchCatalog.accelerationTierSpec());
            settings.add(GtoHatchCatalog.accelerationMultiplierSpec());
        }
        if (capabilities.thread()) {
            settings.add(GtoHatchCatalog.threadTierSpec());
            settings.add(GtoHatchCatalog.threadCountSpec());
        }
        if (capabilities.overclocking()) {
            settings.add(GtoHatchCatalog.overclockTierSpec());
            settings.add(GtoHatchCatalog.overclockDivisorSpec());
        }
        return List.copyOf(settings);
    }

    @Override
    public int configuredMaxParallel(Entry entry, int fallback) {
        int value = fallback;
        if (capabilities.glassParallelBase() > 1) {
            int glassTier = configuredGlassTier(entry);
            value = saturatedPower(capabilities.glassParallelBase(), glassTier);
        }
        if (capabilities.hasCoilParallel()) {
            int temperature = configuredCoilTemperature(entry);
            if (temperature > 0) {
                int steps = Math.max(0, temperature / capabilities.coilParallelStepKelvin());
                value = saturatedPower(capabilities.coilParallelFactor(), steps);
            }
        }
        if (capabilities.coilLogParallelFormula() != null) {
            int temperature = configuredCoilTemperature(entry);
            if (temperature > 0) {
                value = coilLogParallel(capabilities.coilLogParallelFormula(), temperature);
            }
        }
        if (capabilities.voltageParallelFormula() != null && entry != null) {
            GtoVoltageParallelFormula formula = capabilities.voltageParallelFormula();
            int voltageTier = entry.getVoltageTier();
            if (voltageTier >= 0) {
                int exponent = Math.max(0, voltageTier - formula.referenceTier());
                value = saturatedPower(formula.base(), exponent);
            }
        }
        if (capabilities.configuredTierParallelFormula() != null) {
            GtoConfiguredTierParallelFormula formula = capabilities.configuredTierParallelFormula();
            MachineSettingSpec spec = setting(entry, formula.settingKey());
            if (spec != null) {
                int tier = entry.getMachineSettingValue(spec);
                value = configuredTierParallel(formula, tier);
            }
        }
        if (capabilities.structureTemperatureFormula() != null) {
            GtoStructureTemperatureFormula formula = capabilities.structureTemperatureFormula();
            int layers = configuredInteger(entry, STEEL_FRAME_LAYERS, 1);
            int temperature = configuredInteger(entry, CURRENT_TEMPERATURE, 400);
            int baseParallel = saturatedInt((double) layers * formula.parallelPerLayer());
            int temperatureMultiplier = Math.max(1, temperature / formula.parallelTemperatureStepKelvin());
            value = saturatedInt((double) baseParallel * temperatureMultiplier);
        }
        if (capabilities.tierParallelFormula() != null) {
            GtoTierParallelFormula formula = capabilities.tierParallelFormula();
            int tier = configuredInteger(entry, PROGRESSION_TIER, formula.minTier());
            value = saturatedParallel(formula.parallelForTier(tier));
        }
        if (capabilities.fissionCoolingFormula() != null && fissionCoolingConfigured(entry)) {
            value = fissionCoolingMaxParallel(entry, capabilities.fissionCoolingFormula());
        }
        if (capabilities.auxiliaryParallel() > 0 && GtoHatchCatalog.auxiliaryEnabled(entry)) {
            value = Math.max(value, capabilities.auxiliaryParallel());
        }
        if (capabilities.parallelControl()) {
            int hatchLimit = GtoHatchCatalog.parallelLimit(entry);
            if (hatchLimit > 0) {
                value = hatchLimit;
            }
        }
        return value;
    }

    @Override
    public boolean allowsRecipe(Entry entry) {
        if (entry == null) {
            return false;
        }
        if (capabilities.casingTierLimit()) {
            int casingTier = configuredCasingTier(entry);
            int recipeTier = entry.getRecipeTier();
            if (casingTier >= 0 && recipeTier >= 0 && recipeTier > casingTier) {
                return false;
            }
        }
        if (capabilities.glassTierLimit()) {
            int glassTier = configuredGlassTier(entry);
            int recipeTier = entry.getRecipeTier();
            if (glassTier > 0 && recipeTier >= 0 && recipeTier > glassTier) {
                return false;
            }
        }
        if (neutronFluxConfigured(entry)
                && configuredInteger(entry, CURRENT_NEUTRON_FLUX_KEV, 0) < configuredInteger(entry, REQUIRED_NEUTRON_FLUX_KEV, 0)) {
            return false;
        }
        if (capabilities.fissionCoolingFormula() != null && fissionCoolingConfigured(entry)
                && fissionCoolingMaxParallel(entry, capabilities.fissionCoolingFormula()) < 1) {
            return false;
        }
        return !invalidAuxiliaryConfiguration(entry);
    }

    @Override
    public String constraintError(Entry entry) {
        if (entry == null) {
            return "";
        }
        if (capabilities.casingTierLimit()) {
            int casingTier = configuredCasingTier(entry);
            int recipeTier = entry.getRecipeTier();
            if (casingTier >= 0 && recipeTier >= 0 && recipeTier > casingTier) {
                return PlannerText.tr("gto.precision.casing_tier_low", "Machine Casing Tier is too low") + ": "
                    + entry.getMachineProfile().displayName() + " (" + ProductionPlanner.voltageTierName(casingTier)
                    + " < " + ProductionPlanner.voltageTierName(recipeTier) + ")";
            }
        }
        if (capabilities.glassTierLimit()) {
            int glassTier = configuredGlassTier(entry);
            int recipeTier = entry.getRecipeTier();
            if (glassTier > 0 && recipeTier >= 0 && recipeTier > glassTier) {
                return PlannerText.tr("gto.dynamic.glass_tier_low", "Glass Tier is too low") + ": "
                    + entry.getMachineProfile().displayName() + " (" + glassTier + " < "
                    + ProductionPlanner.voltageTierName(recipeTier) + ")";
            }
        }
        if (neutronFluxConfigured(entry)
                && configuredInteger(entry, CURRENT_NEUTRON_FLUX_KEV, 0) < configuredInteger(entry, REQUIRED_NEUTRON_FLUX_KEV, 0)) {
            return PlannerText.tr("gto.dynamic.neutron_flux_low", "Current neutron flux is below the recipe requirement")
                + ": " + entry.getMachineProfile().displayName();
        }
        if (capabilities.fissionCoolingFormula() != null && fissionCoolingConfigured(entry)
                && fissionCoolingMaxParallel(entry, capabilities.fissionCoolingFormula()) < 1) {
            return PlannerText.tr("gto.dynamic.fission_cooling_low", "Cooling supply is below recipe demand")
                + ": " + entry.getMachineProfile().displayName();
        }
        if (invalidAuxiliaryConfiguration(entry)) {
            return PlannerText.tr("gto.hatch.auxiliary_required_error", "Selected GTO hatch requires Auxiliary Modules")
                + ": " + entry.getMachineProfile().displayName();
        }
        return "";
    }

    @Override
    public double durationMultiplier(Entry entry) {
        double multiplier = 1.0D;
        if (capabilities.glassDurationFormula() != null) {
            multiplier *= glassDurationMultiplier(entry, capabilities.glassDurationFormula());
        }
        if (capabilities.coilTierEfficiencyFormula() != null) {
            multiplier *= coilTierDurationMultiplier(entry, capabilities.coilTierEfficiencyFormula());
        }
        if (capabilities.coilTemperatureDurationFormula() != null) {
            multiplier *= coilTemperatureDurationMultiplier(entry, capabilities.coilTemperatureDurationFormula());
        }
        if (capabilities.coilExponentialDurationFormula() != null) {
            multiplier *= coilExponentialDurationMultiplier(entry, capabilities.coilExponentialDurationFormula());
        }
        if (capabilities.configuredCountDurationFormula() != null) {
            GtoConfiguredCountDurationFormula formula = capabilities.configuredCountDurationFormula();
            int count = configuredInteger(entry, formula.settingKey(), formula.defaultValue());
            multiplier *= Math.pow(formula.base(), Math.max(0, count));
        }
        if (capabilities.structureTemperatureFormula() != null) {
            GtoStructureTemperatureFormula formula = capabilities.structureTemperatureFormula();
            int temperature = configuredInteger(entry, CURRENT_TEMPERATURE, 400);
            if (temperature > 0) {
                multiplier *= Math.min(1.0D, formula.durationNumeratorKelvin() / temperature);
            }
        }
        if (capabilities.tierDurationFormula() != null) {
            multiplier *= tierDurationMultiplier(entry, capabilities.tierDurationFormula());
        }
        if (capabilities.productionBoostFormula() != null && productionBoostEnabled(entry)) {
            multiplier *= capabilities.productionBoostFormula().durationMultiplier();
        }
        if (capabilities.neutronFluxDurationFormula() != null) {
            multiplier *= neutronFluxDurationMultiplier(entry, capabilities.neutronFluxDurationFormula());
        }
        if (capabilities.auxiliaryDurationMultiplier() > 0.0D && GtoHatchCatalog.auxiliaryEnabled(entry)) {
            multiplier *= capabilities.auxiliaryDurationMultiplier();
        }
        if (capabilities.acceleration() && accelerationUsable(entry)) {
            multiplier *= GtoHatchCatalog.accelerationDurationMultiplier(entry);
        }
        return multiplier;
    }

    @Override
    public double energyMultiplier(Entry entry) {
        double multiplier = 1.0D;
        if (capabilities.coilTierEfficiencyFormula() != null) {
            multiplier *= coilTierEnergyMultiplier(entry, capabilities.coilTierEfficiencyFormula());
        }
        if (capabilities.productionBoostFormula() != null && productionBoostEnabled(entry)) {
            multiplier *= capabilities.productionBoostFormula().energyMultiplier();
        }
        return multiplier;
    }

    @Override
    public double standardOcDurationMultiplier(Entry entry, double fallback) {
        if (capabilities.overclocking() && overclockingUsable(entry)) {
            return GtoHatchCatalog.overclockDurationMultiplier(entry, fallback);
        }
        return fallback;
    }

    @Override
    public double throughputMultiplier(Entry entry) {
        if (capabilities.thread() && threadUsable(entry)) {
            return GtoHatchCatalog.effectiveThreads(entry);
        }
        return 1.0D;
    }

    @Override
    public double outputMultiplier(Entry entry) {
        if (capabilities.productionBoostFormula() != null && productionBoostEnabled(entry)) {
            return capabilities.productionBoostFormula().outputMultiplier();
        }
        return 1.0D;
    }

    @Override
    public List<String> settingDetails(Entry entry, MachineSettingSpec spec) {
        if (entry == null || spec == null) {
            return List.of();
        }
        if (GLASS_TIER.equals(spec.key())) {
            return glassDetails(entry);
        }
        if (CASING_TIER.equals(spec.key())) {
            return casingDetails(entry);
        }
        if (COIL_PARALLEL.equals(spec.key())) {
            return coilDetails(entry);
        }
        if (capabilities.configuredTierParallelFormula() != null
                && capabilities.configuredTierParallelFormula().settingKey().equals(spec.key())) {
            return configuredTierDetails(entry, capabilities.configuredTierParallelFormula());
        }
        if (capabilities.configuredCountDurationFormula() != null
                && capabilities.configuredCountDurationFormula().settingKey().equals(spec.key())) {
            return configuredCountDurationDetails(entry, capabilities.configuredCountDurationFormula());
        }
        if (STEEL_FRAME_LAYERS.equals(spec.key()) || CURRENT_TEMPERATURE.equals(spec.key())) {
            return structureTemperatureDetails(entry);
        }
        if (PROGRESSION_TIER.equals(spec.key())) {
            return tierDurationDetails(entry);
        }
        if (PRODUCTION_BOOST.equals(spec.key())) {
            return productionBoostDetails(entry);
        }
        if (CURRENT_NEUTRON_FLUX_KEV.equals(spec.key()) || REQUIRED_NEUTRON_FLUX_KEV.equals(spec.key())) {
            return neutronFluxDetails(entry);
        }
        if (FISSION_RECIPE_HEAT.equals(spec.key()) || FISSION_TEMPERATURE.equals(spec.key())
                || FISSION_COOLING_COMPONENTS.equals(spec.key()) || FISSION_ADJACENT_COMPONENTS.equals(spec.key())) {
            return fissionCoolingDetails(entry);
        }
        return switch (spec.key()) {
            case GtoHatchCatalog.AUXILIARY_MODULES -> auxiliaryDetails(entry);
            case GtoHatchCatalog.PARALLEL_HATCH_LIMIT -> parallelDetails(entry);
            case GtoHatchCatalog.ACCELERATION_HATCH_TIER, GtoHatchCatalog.ACCELERATION_MULTIPLIER -> accelerationDetails(entry);
            case GtoHatchCatalog.THREAD_HATCH_TIER, GtoHatchCatalog.THREAD_COUNT -> threadDetails(entry);
            case GtoHatchCatalog.OVERCLOCK_HATCH_TIER, GtoHatchCatalog.OVERCLOCK_DIVISOR -> overclockDetails(entry);
            default -> List.of();
        };
    }

    @Override
    public List<String> modifierDescriptions(MachineProfile profile) {
        List<String> lines = new ArrayList<>();
        if (capabilities.standardOcDurationMultiplier() > 0.0D) {
            lines.add(PlannerText.tr("gto.mod.special_oc", "GTO auto-detected special standard OC time multiplier") + ": "
                + Math.round(capabilities.standardOcDurationMultiplier() * 10000.0D) / 100.0D + "%");
        }
        if (capabilities.enablesPerfectOc()) {
            lines.add(PlannerText.tr("gto.mod.perfect_oc", "GTO Perfect OC support detected automatically"));
        }
        if (capabilities.glassParallelBase() > 1) {
            lines.add(PlannerText.tr("gto.mod.glass_parallel", "GTO auto-detected glass-tier built-in parallel formula") + ": " + capabilities.glassParallelBase() + "^(Glass Tier)");
        }
        if (capabilities.casingTierLimit()) {
            lines.add(PlannerText.tr("gto.mod.casing_limit", "GTO auto-detected recipe-tier limit from machine casing tier"));
        }
        if (capabilities.glassTierLimit()) {
            lines.add(PlannerText.tr("gto.mod.glass_limit", "GTO auto-detected recipe-tier limit from glass tier"));
        }
        if (capabilities.hasCoilParallel()) {
            lines.add(PlannerText.tr("gto.mod.coil_parallel", "GTO auto-detected coil parallel") + ": x" + capabilities.coilParallelFactor() + " / "
                + capabilities.coilParallelStepKelvin() + "K");
        }
        if (capabilities.coilLogParallelFormula() != null) {
            GtoCoilLogParallelFormula formula = capabilities.coilLogParallelFormula();
            lines.add(PlannerText.tr("gto.mod.coil_log_parallel", "GTO auto-detected coil logarithmic parallel formula") + ": log" + formula.logBase()
                + "(T-" + formula.temperatureOffset() + ")-" + formula.subtract());
        }
        if (capabilities.voltageParallelFormula() != null) {
            GtoVoltageParallelFormula formula = capabilities.voltageParallelFormula();
            lines.add(PlannerText.tr("gto.mod.voltage_parallel", "GTO auto-detected voltage-tier parallel formula") + ": " + formula.base()
                + "^(" + PlannerText.tr("gto.mod.tiers_above", "tiers above") + " " + ProductionPlanner.voltageTierName(formula.referenceTier()) + ")");
        }
        if (capabilities.configuredTierParallelFormula() != null) {
            GtoConfiguredTierParallelFormula formula = capabilities.configuredTierParallelFormula();
            lines.add(PlannerText.tr("gto.mod.configured_parallel", "GTO auto-detected configured-tier parallel formula") + ": " + configuredTierFormulaText(formula));
        }
        if (capabilities.glassDurationFormula() != null) {
            lines.add(PlannerText.tr("gto.mod.glass_duration", "GTO auto-detected glass-tier duration formula") + ": " + glassDurationFormulaText(capabilities.glassDurationFormula()));
        }
        if (capabilities.coilTierEfficiencyFormula() != null) {
            GtoCoilTierEfficiencyFormula formula = capabilities.coilTierEfficiencyFormula();
            if (formula.durationReductionPerTier() > 0.0D) {
                lines.add(PlannerText.tr("gto.mod.coil_duration_reduction", "GTO auto-detected coil-tier duration reduction") + ": -" + formatNumber(formula.durationReductionPerTier() * 100.0D) + "%/tier");
            }
            if (formula.energyReductionPerTier() > 0.0D) {
                lines.add(PlannerText.tr("gto.mod.coil_energy_reduction", "GTO auto-detected coil-tier energy reduction") + ": -" + formatNumber(formula.energyReductionPerTier() * 100.0D) + "%/tier");
            }
        }
        if (capabilities.coilTemperatureDurationFormula() != null) {
            lines.add(PlannerText.tr("gto.mod.coil_temp_duration", "GTO auto-detected coil-temperature duration formula") + ": log(" + formatNumber(capabilities.coilTemperatureDurationFormula().numeratorKelvin()) + ") / log(T)");
        }
        if (capabilities.coilExponentialDurationFormula() != null) {
            GtoCoilExponentialDurationFormula formula = capabilities.coilExponentialDurationFormula();
            lines.add(PlannerText.tr("gto.mod.coil_exp_duration", "GTO auto-detected coil-temperature exponential duration formula") + ": " + formatNumber(formula.leadingMultiplier()) + " x " + formatNumber(formula.exponentialBase()) + "^((T-" + formula.temperatureOffset() + ")/" + formula.temperatureScale() + ")");
        }
        if (capabilities.auxiliaryParallel() > 0) {
            lines.add(PlannerText.tr("gto.mod.aux_parallel", "GTO auto-detected Auxiliary Module built-in parallel") + ": " + capabilities.auxiliaryParallel());
        }
        if (capabilities.auxiliaryDurationMultiplier() > 0.0D) {
            lines.add(PlannerText.tr("gto.mod.aux_duration", "GTO auto-detected Auxiliary Module duration multiplier") + ": x" + formatMultiplier(capabilities.auxiliaryDurationMultiplier()));
        }
        if (capabilities.configuredCountDurationFormula() != null) {
            GtoConfiguredCountDurationFormula formula = capabilities.configuredCountDurationFormula();
            lines.add(PlannerText.tr("gto.mod.count_duration", "GTO auto-detected count-based duration formula") + ": " + formatNumber(formula.base()) + "^count");
        }
        if (capabilities.structureTemperatureFormula() != null) {
            GtoStructureTemperatureFormula formula = capabilities.structureTemperatureFormula();
            lines.add(PlannerText.tr("gto.mod.structure_temp", "GTO auto-detected structure/temperature mechanics") + ": " + formula.parallelPerLayer() + " parallel/layer, x1/" + formula.parallelTemperatureStepKelvin() + "K, duration x" + formatNumber(formula.durationNumeratorKelvin()) + "/T");
        }
        if (capabilities.tierDurationFormula() != null) {
            lines.add(PlannerText.tr("gto.mod.progression_duration", "GTO auto-detected progression-tier duration bonus"));
        }
        if (capabilities.tierParallelFormula() != null) {
            lines.add(PlannerText.tr("gto.mod.progression_parallel", "GTO auto-detected progression-tier parallel formula from machine bytecode"));
        }
        if (capabilities.productionBoostFormula() != null) {
            GtoProductionBoostFormula formula = capabilities.productionBoostFormula();
            lines.add(PlannerText.tr("gto.mod.production_boost", "GTO auto-detected Production-Boosting Mode") + ": output x" + formatMultiplier(formula.outputMultiplier()) + ", duration x" + formatMultiplier(formula.durationMultiplier()) + ", energy/steam x" + formatMultiplier(formula.energyMultiplier()) + (formula.enabledByDefault() ? " (default ON)" : ""));
        }
        if (capabilities.neutronFluxDurationFormula() != null) {
            GtoNeutronFluxDurationFormula formula = capabilities.neutronFluxDurationFormula();
            lines.add(PlannerText.tr("gto.mod.neutron_formula", "GTO auto-detected neutron-flux duration formula") + ": sqrt(" + formatNumber(formula.baseTerm()) + " - (current-required)/" + formatNumber(formula.divisorMeV()) + "MeV)");
        }
        if (capabilities.fissionCoolingFormula() != null) {
            GtoFissionCoolingFormula formula = capabilities.fissionCoolingFormula();
            lines.add(PlannerText.tr("gto.mod.fission_cooling", "GTO auto-detected fission cooling parallel limit") + ": supply/demand, T divisor " + formatNumber(formula.temperatureDivisor()));
        }
        if (capabilities.parallelControl()) {
            lines.add(PlannerText.tr("gto.mod.parallel_hatch", "GTO/GTM Parallel Control Hatch detected automatically"));
        }
        if (capabilities.acceleration()) {
            lines.add(PlannerText.tr("gto.mod.acceleration_hatch", "GTO Acceleration Hatch detected automatically"));
        }
        if (capabilities.thread()) {
            lines.add(PlannerText.tr("gto.mod.thread_hatch", "GTO Thread Hatch detected automatically"));
        }
        if (capabilities.overclocking()) {
            lines.add(PlannerText.tr("gto.mod.overclock_hatch", "GTO Overclocking Hatch detected automatically"));
        }
        if (capabilities.laserEnergy()) {
            lines.add(PlannerText.tr("gto.mod.laser_hatch", "GTO Laser Energy Hatch support detected"));
        }
        return List.copyOf(lines);
    }

    private List<String> glassDetails(Entry entry) {
        int glassTier = configuredGlassTier(entry);
        List<String> lines = new ArrayList<>();
        lines.add(PlannerText.tr("gto.dynamic.selected_glass_tier", "Selected Glass") + ": " + GtoGlassCatalog.displayForTier(glassTier));
        if (capabilities.glassParallelBase() > 1) {
            lines.add(PlannerText.tr("gto.precision.current_parallel", "Current built-in parallel") + ": "
                + configuredMaxParallel(entry, entry.getMachineProfile().maxParallel()));
            lines.add(PlannerText.tr("gto.auto.detected_formula", "Detected formula") + ": "
                + capabilities.glassParallelBase() + "^(Glass Tier)");
        }
        if (capabilities.glassTierLimit()) {
            int recipeTier = entry.getRecipeTier();
            lines.add(PlannerText.tr("gto.precision.recipe_tier", "Recipe Tier") + ": "
                + (recipeTier < 0 ? "--" : ProductionPlanner.voltageTierName(recipeTier)));
            lines.add(PlannerText.tr("gto.precision.recipe_allowed", "Recipe allowed") + ": " + yesNo(allowsRecipe(entry)));
        }
        if (capabilities.glassDurationFormula() != null) {
            lines.add(PlannerText.tr("gto.dynamic.duration_multiplier", "Duration multiplier") + ": x"
                + formatMultiplier(glassDurationMultiplier(entry, capabilities.glassDurationFormula())));
            lines.add(PlannerText.tr("gto.auto.detected_formula", "Detected formula") + ": "
                + glassDurationFormulaText(capabilities.glassDurationFormula()));
        }
        return List.copyOf(lines);
    }

    private List<String> casingDetails(Entry entry) {
        int recipeTier = entry.getRecipeTier();
        return List.of(
            PlannerText.tr("gto.precision.recipe_tier", "Recipe Tier") + ": "
                + (recipeTier < 0 ? "--" : ProductionPlanner.voltageTierName(recipeTier)),
            PlannerText.tr("gto.precision.recipe_allowed", "Recipe allowed") + ": " + yesNo(allowsRecipe(entry))
        );
    }

    private List<String> coilDetails(Entry entry) {
        int choice = configuredCoilChoice(entry);
        int temperature = GtceuHeatingCoilCatalog.temperatureForChoice(choice);
        if (choice <= 0 || temperature <= 0) {
            return List.of(PlannerText.tr("gto.auto.coil_parallel_auto", "AUTO keeps the detected/profile machine mechanics"));
        }
        List<String> lines = new ArrayList<>();
        lines.add(PlannerText.tr("gto.auto.selected_coil", "Selected heating coil") + ": "
            + GtceuHeatingCoilCatalog.nameForChoice(choice));
        lines.add(PlannerText.tr("gto.auto.coil_temperature", "Coil temperature") + ": " + temperature + "K");
        if (capabilities.hasCoilParallel()) {
            int steps = temperature / capabilities.coilParallelStepKelvin();
            lines.add(PlannerText.tr("gto.auto.parallel_steps", "Parallel formula steps") + ": " + steps);
            lines.add(PlannerText.tr("gto.precision.current_parallel", "Current built-in parallel") + ": "
                + configuredMaxParallel(entry, entry.getMachineProfile().maxParallel()));
            lines.add(PlannerText.tr("gto.auto.detected_formula", "Detected formula") + ": " + capabilities.coilParallelFactor()
                + "^floor(T / " + capabilities.coilParallelStepKelvin() + "K)");
        }
        if (capabilities.coilLogParallelFormula() != null) {
            GtoCoilLogParallelFormula formula = capabilities.coilLogParallelFormula();
            lines.add(PlannerText.tr("gto.precision.current_parallel", "Current built-in parallel") + ": "
                + configuredMaxParallel(entry, entry.getMachineProfile().maxParallel()));
            lines.add(PlannerText.tr("gto.auto.detected_formula", "Detected formula") + ": log" + formula.logBase()
                + "(T - " + formula.temperatureOffset() + ") - " + formula.subtract()
                + ", min " + formula.minimumParallel());
        }
        if (capabilities.coilTierEfficiencyFormula() != null) {
            GtoCoilTierEfficiencyFormula formula = capabilities.coilTierEfficiencyFormula();
            int tiersAbove = Math.max(0, choice - formula.referenceChoice());
            lines.add(PlannerText.tr("gto.auto.coil_tiers_above_reference", "Coil tiers above Cupronickel") + ": " + tiersAbove);
            if (formula.durationReductionPerTier() > 0.0D) {
                lines.add(PlannerText.tr("gto.dynamic.duration_multiplier", "Duration multiplier") + ": x"
                    + formatMultiplier(coilTierDurationMultiplier(entry, formula)));
            }
            if (formula.energyReductionPerTier() > 0.0D) {
                lines.add(PlannerText.tr("gto.auto.energy_multiplier", "Energy multiplier") + ": x"
                    + formatMultiplier(coilTierEnergyMultiplier(entry, formula)));
            }
        }
        if (capabilities.coilTemperatureDurationFormula() != null) {
            GtoCoilTemperatureDurationFormula formula = capabilities.coilTemperatureDurationFormula();
            lines.add(PlannerText.tr("gto.dynamic.duration_multiplier", "Duration multiplier") + ": x"
                + formatMultiplier(coilTemperatureDurationMultiplier(entry, formula)));
            lines.add(PlannerText.tr("gto.auto.detected_formula", "Detected formula") + ": log("
                + formatNumber(formula.numeratorKelvin()) + ") / log(T)");
        }
        if (capabilities.coilExponentialDurationFormula() != null) {
            GtoCoilExponentialDurationFormula formula = capabilities.coilExponentialDurationFormula();
            lines.add(PlannerText.tr("gto.dynamic.duration_multiplier", "Duration multiplier") + ": x"
                + formatMultiplier(coilExponentialDurationMultiplier(entry, formula)));
            lines.add(PlannerText.tr("gto.auto.detected_formula", "Detected formula") + ": "
                + formatNumber(formula.leadingMultiplier()) + " x " + formatNumber(formula.exponentialBase())
                + "^((T-" + formula.temperatureOffset() + ")/" + formula.temperatureScale() + ")");
        }
        return List.copyOf(lines);
    }

    private List<String> configuredTierDetails(Entry entry, GtoConfiguredTierParallelFormula formula) {
        MachineSettingSpec spec = setting(entry, formula.settingKey());
        int tier = spec == null ? formula.defaultTier() : entry.getMachineSettingValue(spec);
        return List.of(
            PlannerText.tr("gto.dynamic.selected_structure_tier", "Selected structure tier") + ": " + tier,
            PlannerText.tr("gto.precision.current_parallel", "Current built-in parallel") + ": "
                + configuredMaxParallel(entry, entry.getMachineProfile().maxParallel()),
            PlannerText.tr("gto.auto.detected_formula", "Detected formula") + ": " + configuredTierFormulaText(formula)
        );
    }

    private List<String> configuredCountDurationDetails(Entry entry, GtoConfiguredCountDurationFormula formula) {
        int count = configuredInteger(entry, formula.settingKey(), formula.defaultValue());
        double multiplier = Math.pow(formula.base(), Math.max(0, count));
        return List.of(
            formula.englishLabel() + ": " + count,
            PlannerText.tr("gto.dynamic.duration_multiplier", "Duration multiplier") + ": x" + formatMultiplier(multiplier),
            PlannerText.tr("gto.auto.detected_formula", "Detected formula") + ": " + formatNumber(formula.base()) + "^count"
        );
    }

    private List<String> structureTemperatureDetails(Entry entry) {
        GtoStructureTemperatureFormula formula = capabilities.structureTemperatureFormula();
        if (formula == null) return List.of();
        int layers = configuredInteger(entry, STEEL_FRAME_LAYERS, 1);
        int temperature = configuredInteger(entry, CURRENT_TEMPERATURE, 400);
        return List.of(
            "Steel Frame Layers: " + layers,
            "Current Temperature: " + temperature + "K",
            PlannerText.tr("gto.precision.current_parallel", "Current built-in parallel") + ": "
                + configuredMaxParallel(entry, entry.getMachineProfile().maxParallel()),
            PlannerText.tr("gto.dynamic.duration_multiplier", "Duration multiplier") + ": x"
                + formatMultiplier(temperature > 0 ? Math.min(1.0D, formula.durationNumeratorKelvin() / temperature) : 1.0D),
            PlannerText.tr("gto.auto.detected_formula", "Detected formula") + ": base PAR=" + formula.parallelPerLayer()
                + "*layers; temp multiplier=max(1,floor(T/" + formula.parallelTemperatureStepKelvin()
                + ")); duration=" + formatNumber(formula.durationNumeratorKelvin()) + "/T"
        );
    }

    private List<String> tierDurationDetails(Entry entry) {
        GtoTierDurationFormula duration = capabilities.tierDurationFormula();
        GtoTierParallelFormula parallel = capabilities.tierParallelFormula();
        if (duration == null && parallel == null) return List.of();
        int tier = configuredInteger(entry, PROGRESSION_TIER, 1);
        List<String> lines = new ArrayList<>();
        lines.add(PlannerText.tr("gto.dynamic.progression_tier", "Progression Tier") + ": " + tier);
        if (duration != null) {
            lines.add(PlannerText.tr("gto.dynamic.duration_multiplier", "Duration multiplier") + ": x"
                + formatMultiplier(tierDurationMultiplier(entry, duration)));
            lines.add(PlannerText.tr("gto.auto.detected_formula", "Detected duration formula") + ": tier 1-" + duration.firstEndTier()
                + " -" + formatNumber(duration.firstReductionPerTier() * 100.0D) + "%/tier; then -"
                + formatNumber(duration.secondReductionPerTier() * 100.0D) + "%/tier; floor x" + formatMultiplier(duration.floorMultiplier()));
        }
        if (parallel != null) {
            long raw = parallel.parallelForTier(tier);
            lines.add(PlannerText.tr("gto.precision.current_parallel", "Current built-in parallel") + ": "
                + (raw == Long.MAX_VALUE ? "Long.MAX_VALUE" : Long.toString(raw)));
            if (raw > 1_000_000_000L) {
                lines.add(PlannerText.tr("gto.mod.parallel_cap", "Planner effective parallel cap") + ": 1000000000");
            }
            lines.add(PlannerText.tr("gto.auto.detected_formula", "Detected parallel formula")
                + ": extracted from getMaxParallel(short)");
        }
        return List.copyOf(lines);
    }

    private List<String> productionBoostDetails(Entry entry) {
        GtoProductionBoostFormula formula = capabilities.productionBoostFormula();
        if (formula == null) return List.of();
        boolean enabled = productionBoostEnabled(entry);
        return List.of(
            PlannerText.tr("gto.dynamic.production_boost_state", "Production-Boosting Mode") + ": " + yesNo(enabled),
            PlannerText.tr("gto.dynamic.output_multiplier", "Output multiplier") + ": x"
                + formatMultiplier(enabled ? formula.outputMultiplier() : 1.0D),
            PlannerText.tr("gto.dynamic.duration_multiplier", "Duration multiplier") + ": x"
                + formatMultiplier(enabled ? formula.durationMultiplier() : 1.0D),
            PlannerText.tr("gto.dynamic.energy_steam_multiplier", "Energy/steam multiplier") + ": x"
                + formatMultiplier(enabled ? formula.energyMultiplier() : 1.0D)
        );
    }

    private List<String> neutronFluxDetails(Entry entry) {
        GtoNeutronFluxDurationFormula formula = capabilities.neutronFluxDurationFormula();
        if (formula == null) return List.of();
        int current = configuredInteger(entry, CURRENT_NEUTRON_FLUX_KEV, 0);
        int required = configuredInteger(entry, REQUIRED_NEUTRON_FLUX_KEV, 0);
        List<String> lines = new ArrayList<>();
        lines.add(PlannerText.tr("gto.dynamic.current_neutron_flux", "Current Neutron Flux") + ": " + (current <= 0 ? "AUTO" : current + " keV"));
        lines.add(PlannerText.tr("gto.dynamic.required_neutron_flux", "Required Neutron Flux") + ": " + (required <= 0 ? "AUTO" : required + " keV"));
        if (neutronFluxConfigured(entry)) {
            lines.add(PlannerText.tr("gto.dynamic.duration_multiplier", "Duration multiplier") + ": x"
                + formatMultiplier(neutronFluxDurationMultiplier(entry, formula)));
            lines.add(PlannerText.tr("gto.precision.recipe_allowed", "Recipe allowed") + ": " + yesNo(allowsRecipe(entry)));
        } else {
            lines.add(PlannerText.tr("gto.mod.set_flux", "Set both flux values to apply the detected runtime formula"));
        }
        lines.add(PlannerText.tr("gto.auto.detected_formula", "Detected formula") + ": sqrt("
            + formatNumber(formula.baseTerm()) + " - (current-required)/" + formatNumber(formula.divisorMeV()) + "MeV)");
        return List.copyOf(lines);
    }

    private List<String> fissionCoolingDetails(Entry entry) {
        GtoFissionCoolingFormula formula = capabilities.fissionCoolingFormula();
        if (formula == null) return List.of();
        int heat = configuredInteger(entry, FISSION_RECIPE_HEAT, 0);
        int temperature = configuredInteger(entry, FISSION_TEMPERATURE, 0);
        int components = configuredInteger(entry, FISSION_COOLING_COMPONENTS, 0);
        int adjacent = configuredInteger(entry, FISSION_ADJACENT_COMPONENTS, 0);
        List<String> lines = new ArrayList<>();
        lines.add(PlannerText.tr("gto.dynamic.fission_recipe_heat", "Recipe Heat Generation") + ": " + (heat <= 0 ? "AUTO" : heat));
        lines.add(PlannerText.tr("gto.dynamic.fission_temperature", "Current Reactor Temperature") + ": " + (temperature <= 0 ? "AUTO" : temperature));
        lines.add(PlannerText.tr("gto.dynamic.fission_cooling_components", "Cooling Components") + ": " + components + ", " + PlannerText.tr("gto.mod.adjacent_count", "adjacent count") + ": " + adjacent);
        if (fissionCoolingConfigured(entry)) {
            lines.add(PlannerText.tr("gto.mod.cooling_supply", "Cooling supply") + ": " + formatNumber(fissionCoolingSupply(entry, formula)));
            lines.add(PlannerText.tr("gto.mod.demand_parallel", "Demand per parallel") + ": " + formatNumber(fissionDemandPerParallel(entry, formula)));
            lines.add(PlannerText.tr("gto.precision.current_parallel", "Current built-in parallel") + ": "
                + fissionCoolingMaxParallel(entry, formula));
            lines.add(PlannerText.tr("gto.precision.recipe_allowed", "Recipe allowed") + ": " + yesNo(allowsRecipe(entry)));
        } else {
            lines.add(PlannerText.tr("gto.mod.set_cooling", "Set recipe heat, reactor temperature and cooling component count to apply the detected cooling limit"));
        }
        lines.add(PlannerText.tr("gto.auto.detected_formula", "Detected formula") + ": Demand=heat*PAR*T/"
            + formatNumber(formula.temperatureDivisor()) + "; Supply=(components-adjacent/"
            + formatNumber(formula.adjacentDivisor()) + ")*" + formatNumber(formula.supplyPerComponent()));
        return List.copyOf(lines);
    }

    private List<String> auxiliaryDetails(Entry entry) {
        List<String> lines = new ArrayList<>();
        boolean enabled = GtoHatchCatalog.auxiliaryEnabled(entry);
        lines.add(PlannerText.tr("gto.hatch.auxiliary_state", "Auxiliary Modules enabled") + ": " + yesNo(enabled));
        if (capabilities.auxiliaryParallel() > 0) {
            lines.add(PlannerText.tr("gto.precision.current_parallel", "Current built-in parallel") + ": "
                + configuredMaxParallel(entry, entry.getMachineProfile().maxParallel()));
            lines.add(PlannerText.tr("gto.auto.detected_formula", "Detected formula") + ": Auxiliary ON -> "
                + capabilities.auxiliaryParallel() + " parallel");
        }
        if (capabilities.auxiliaryDurationMultiplier() > 0.0D) {
            lines.add(PlannerText.tr("gto.dynamic.duration_multiplier", "Duration multiplier") + ": x"
                + formatMultiplier(enabled ? capabilities.auxiliaryDurationMultiplier() : 1.0D));
            lines.add(PlannerText.tr("gto.auto.detected_formula", "Detected formula") + ": Auxiliary ON -> duration x"
                + formatMultiplier(capabilities.auxiliaryDurationMultiplier()));
        }
        return List.copyOf(lines);
    }

    private List<String> parallelDetails(Entry entry) {
        int selected = GtoHatchCatalog.parallelLimit(entry);
        int current = configuredMaxParallel(entry, entry.getMachineProfile().maxParallel());
        List<String> lines = new ArrayList<>();
        lines.add(PlannerText.tr("gto.hatch.current_max_parallel", "Current hatch max parallel") + ": "
            + (current > 0 ? current : PlannerText.tr("gto.hatch.unknown", "unknown")));
        if (selected <= 0) {
            lines.add(PlannerText.tr("gto.hatch.auto_parallel", "AUTO keeps the detected/profile parallel limit"));
        }
        lines.add(PlannerText.tr("gto.hatch.row_parallel", "Row PAR remains the currently configured hatch parallel"));
        return List.copyOf(lines);
    }

    private List<String> accelerationDetails(Entry entry) {
        if (!GtoHatchCatalog.accelerationSelected(entry)) {
            return List.of(PlannerText.tr("gto.hatch.disabled", "Hatch disabled"));
        }
        int tier = GtoHatchCatalog.accelerationTier(entry);
        int recipeTier = entry.getRecipeTier();
        int missing = recipeTier < 0 ? 0 : Math.max(0, recipeTier - tier);
        List<String> lines = new ArrayList<>();
        lines.add(PlannerText.tr("gto.hatch.min_duration", "Minimum base duration percentage for this tier") + ": "
            + GtoHatchCatalog.accelerationMinimumPercent(tier) + "%");
        lines.add(PlannerText.tr("gto.hatch.effective_duration", "Effective duration multiplier") + ": "
            + GtoHatchCatalog.accelerationEffectivePercent(entry) + "%");
        lines.add(PlannerText.tr("gto.hatch.recipe_tier", "Recipe tier") + ": "
            + (recipeTier < 0 ? "--" : ProductionPlanner.voltageTierName(recipeTier)));
        lines.add(PlannerText.tr("gto.hatch.missing_tiers", "Missing hatch tiers") + ": " + missing);
        if (capabilities.accelerationRequiresAuxiliary()) {
            lines.add(PlannerText.tr("gto.hatch.requires_auxiliary", "Requires Auxiliary Modules") + ": "
                + yesNo(GtoHatchCatalog.auxiliaryEnabled(entry)));
        }
        return List.copyOf(lines);
    }

    private List<String> threadDetails(Entry entry) {
        if (!GtoHatchCatalog.threadSelected(entry)) {
            return List.of(PlannerText.tr("gto.hatch.disabled", "Hatch disabled"));
        }
        List<String> lines = new ArrayList<>();
        lines.add(PlannerText.tr("gto.hatch.max_threads", "Maximum threads for selected hatch") + ": "
            + GtoHatchCatalog.maxThreads(entry));
        lines.add(PlannerText.tr("gto.hatch.effective_threads", "Effective recipe threads") + ": "
            + GtoHatchCatalog.effectiveThreads(entry));
        if (capabilities.threadRequiresAuxiliary()) {
            lines.add(PlannerText.tr("gto.hatch.requires_auxiliary", "Requires Auxiliary Modules") + ": "
                + yesNo(GtoHatchCatalog.auxiliaryEnabled(entry)));
        }
        return List.copyOf(lines);
    }

    private List<String> overclockDetails(Entry entry) {
        if (!GtoHatchCatalog.overclockSelected(entry)) {
            return List.of(PlannerText.tr("gto.hatch.disabled", "Hatch disabled"));
        }
        double base = entry.getMachineProfile().standardOcDurationMultiplier();
        double effective = GtoHatchCatalog.overclockDurationMultiplier(entry, base);
        List<String> lines = new ArrayList<>();
        lines.add(PlannerText.tr("gto.hatch.max_oc_divisor", "Selected hatch maximum OC time divisor") + ": /"
            + GtoHatchCatalog.overclockMaxDivisor(entry));
        lines.add(PlannerText.tr("gto.hatch.effective_oc_divisor", "Effective OC time divisor") + ": /"
            + GtoHatchCatalog.overclockEffectiveDivisor(entry));
        lines.add(PlannerText.tr("gto.hatch.base_oc_multiplier", "Machine base standard OC time multiplier") + ": "
            + formatPercent(base));
        lines.add(PlannerText.tr("gto.hatch.effective_oc_multiplier", "Effective standard OC time multiplier") + ": "
            + formatPercent(effective));
        if (capabilities.overclockingRequiresAuxiliary()) {
            lines.add(PlannerText.tr("gto.hatch.requires_auxiliary", "Requires Auxiliary Modules") + ": "
                + yesNo(GtoHatchCatalog.auxiliaryEnabled(entry)));
        }
        return List.copyOf(lines);
    }

    private boolean invalidAuxiliaryConfiguration(Entry entry) {
        if (!capabilities.auxiliaryModules() || GtoHatchCatalog.auxiliaryEnabled(entry)) {
            return false;
        }
        return capabilities.accelerationRequiresAuxiliary() && GtoHatchCatalog.accelerationSelected(entry)
            || capabilities.threadRequiresAuxiliary() && GtoHatchCatalog.threadSelected(entry)
            || capabilities.overclockingRequiresAuxiliary() && GtoHatchCatalog.overclockSelected(entry);
    }

    private boolean accelerationUsable(Entry entry) {
        return GtoHatchCatalog.accelerationSelected(entry)
            && (!capabilities.accelerationRequiresAuxiliary() || GtoHatchCatalog.auxiliaryEnabled(entry));
    }

    private boolean threadUsable(Entry entry) {
        return GtoHatchCatalog.threadSelected(entry)
            && (!capabilities.threadRequiresAuxiliary() || GtoHatchCatalog.auxiliaryEnabled(entry));
    }

    private boolean overclockingUsable(Entry entry) {
        return GtoHatchCatalog.overclockSelected(entry)
            && (!capabilities.overclockingRequiresAuxiliary() || GtoHatchCatalog.auxiliaryEnabled(entry));
    }

    private int configuredInteger(Entry entry, String key, int fallback) {
        MachineSettingSpec spec = setting(entry, key);
        return spec == null || entry == null ? fallback : entry.getMachineSettingValue(spec);
    }

    private boolean productionBoostEnabled(Entry entry) {
        GtoProductionBoostFormula formula = capabilities.productionBoostFormula();
        if (formula == null || entry == null) return false;
        MachineSettingSpec spec = setting(entry, PRODUCTION_BOOST);
        return spec != null && entry.getMachineSettingValue(spec) != 0;
    }

    private boolean neutronFluxConfigured(Entry entry) {
        return capabilities.neutronFluxDurationFormula() != null
            && configuredInteger(entry, CURRENT_NEUTRON_FLUX_KEV, 0) > 0
            && configuredInteger(entry, REQUIRED_NEUTRON_FLUX_KEV, 0) > 0;
    }

    private double neutronFluxDurationMultiplier(Entry entry, GtoNeutronFluxDurationFormula formula) {
        if (formula == null || !neutronFluxConfigured(entry)) return 1.0D;
        double currentMeV = configuredInteger(entry, CURRENT_NEUTRON_FLUX_KEV, 0) / 1000.0D;
        double requiredMeV = configuredInteger(entry, REQUIRED_NEUTRON_FLUX_KEV, 0) / 1000.0D;
        if (currentMeV < requiredMeV) return 1.0D;
        double inside = formula.baseTerm() - (currentMeV - requiredMeV) / formula.divisorMeV();
        double minimumSquared = formula.minimumMultiplier() * formula.minimumMultiplier();
        return Math.sqrt(Math.max(minimumSquared, inside));
    }

    private boolean fissionCoolingConfigured(Entry entry) {
        return capabilities.fissionCoolingFormula() != null
            && configuredInteger(entry, FISSION_RECIPE_HEAT, 0) > 0
            && configuredInteger(entry, FISSION_TEMPERATURE, 0) > 0
            && configuredInteger(entry, FISSION_COOLING_COMPONENTS, 0) > 0;
    }

    private double fissionCoolingSupply(Entry entry, GtoFissionCoolingFormula formula) {
        if (formula == null) return 0.0D;
        double components = Math.max(0, configuredInteger(entry, FISSION_COOLING_COMPONENTS, 0));
        double adjacent = Math.max(0, configuredInteger(entry, FISSION_ADJACENT_COMPONENTS, 0));
        return Math.max(0.0D, (components - adjacent / formula.adjacentDivisor()) * formula.supplyPerComponent());
    }

    private double fissionDemandPerParallel(Entry entry, GtoFissionCoolingFormula formula) {
        if (formula == null) return 0.0D;
        double heat = Math.max(0, configuredInteger(entry, FISSION_RECIPE_HEAT, 0));
        double temperature = Math.max(0, configuredInteger(entry, FISSION_TEMPERATURE, 0));
        return heat * temperature / formula.temperatureDivisor();
    }

    private int fissionCoolingMaxParallel(Entry entry, GtoFissionCoolingFormula formula) {
        if (formula == null || !fissionCoolingConfigured(entry)) return 0;
        double demand = fissionDemandPerParallel(entry, formula);
        if (!Double.isFinite(demand) || demand <= 0.0D) return 0;
        double raw = Math.floor(fissionCoolingSupply(entry, formula) / demand);
        if (!Double.isFinite(raw) || raw <= 0.0D) return 0;
        return (int) Math.min(1_000_000_000D, raw);
    }

    private double tierDurationMultiplier(Entry entry, GtoTierDurationFormula formula) {
        if (formula == null) return 1.0D;
        int tier = Math.max(1, configuredInteger(entry, PROGRESSION_TIER, 1));
        if (tier >= formula.secondEndTier()) return formula.floorMultiplier();
        double reduction = Math.min(tier, formula.firstEndTier()) * formula.firstReductionPerTier();
        if (tier > formula.firstEndTier()) {
            reduction += (tier - formula.firstEndTier()) * formula.secondReductionPerTier();
        }
        return Math.max(formula.floorMultiplier(), 1.0D - reduction);
    }

    private int configuredCasingTier(Entry entry) {
        MachineSettingSpec casing = setting(entry, CASING_TIER);
        if (casing == null) {
            return -1;
        }
        int value = entry.getMachineSettingValue(casing);
        return value <= 0 ? -1 : value - 1;
    }

    private int configuredGlassTier(Entry entry) {
        MachineSettingSpec glass = setting(entry, GLASS_TIER);
        if (glass == null || entry == null) {
            return 0;
        }
        return GtoGlassCatalog.tierForChoice(entry.getMachineSettingValue(glass));
    }

    private int configuredCoilChoice(Entry entry) {
        MachineSettingSpec coil = setting(entry, COIL_PARALLEL);
        return coil == null || entry == null ? 0 : Math.max(0, entry.getMachineSettingValue(coil));
    }

    private int configuredCoilTemperature(Entry entry) {
        return GtceuHeatingCoilCatalog.temperatureForChoice(configuredCoilChoice(entry));
    }

    private boolean needsCoilSetting() {
        return capabilities.needsCoilSetting();
    }

    private MachineSettingSpec setting(Entry entry, String key) {
        if (entry == null) {
            return null;
        }
        for (MachineSettingSpec spec : settings(entry.getMachineProfile())) {
            if (key.equals(spec.key())) {
                return spec;
            }
        }
        return null;
    }

    private static int configuredTierParallel(GtoConfiguredTierParallelFormula formula, int tier) {
        if (formula == null) {
            return 0;
        }
        int safeTier = Math.max(formula.minTier(), Math.min(formula.maxTier(), tier));
        if (formula.mode() == GtoConfiguredTierParallelFormula.Mode.POWER) {
            int base = Math.max(1, (int) Math.round(formula.factorOrBase()));
            return saturatedPower(base, Math.max(0, safeTier - formula.exponentOffset()));
        }
        return saturatedInt(safeTier * formula.factorOrBase());
    }

    private static int coilLogParallel(GtoCoilLogParallelFormula formula, int temperature) {
        if (formula == null || temperature <= formula.temperatureOffset()) {
            return formula == null ? 1 : formula.minimumParallel();
        }
        double argument = temperature - formula.temperatureOffset();
        double raw = Math.log(argument) / Math.log(formula.logBase()) - formula.subtract();
        return Math.max(formula.minimumParallel(), saturatedInt(Math.floor(raw)));
    }

    private double coilTierDurationMultiplier(Entry entry, GtoCoilTierEfficiencyFormula formula) {
        if (formula == null || formula.durationReductionPerTier() <= 0.0D) {
            return 1.0D;
        }
        int tiersAbove = Math.max(0, configuredCoilChoice(entry) - formula.referenceChoice());
        return Math.max(0.01D, 1.0D - formula.durationReductionPerTier() * tiersAbove);
    }

    private double coilTierEnergyMultiplier(Entry entry, GtoCoilTierEfficiencyFormula formula) {
        if (formula == null || formula.energyReductionPerTier() <= 0.0D) {
            return 1.0D;
        }
        int tiersAbove = Math.max(0, configuredCoilChoice(entry) - formula.referenceChoice());
        return Math.max(0.01D, 1.0D - formula.energyReductionPerTier() * tiersAbove);
    }

    private double coilTemperatureDurationMultiplier(Entry entry, GtoCoilTemperatureDurationFormula formula) {
        if (formula == null) {
            return 1.0D;
        }
        int temperature = configuredCoilTemperature(entry);
        if (temperature <= 1) {
            return 1.0D;
        }
        double denominator = Math.log(temperature);
        if (!Double.isFinite(denominator) || denominator <= 0.0D) {
            return 1.0D;
        }
        double value = Math.log(formula.numeratorKelvin()) / denominator;
        return Double.isFinite(value) && value > 0.0D ? value : 1.0D;
    }

    private double coilExponentialDurationMultiplier(Entry entry, GtoCoilExponentialDurationFormula formula) {
        if (formula == null) {
            return 1.0D;
        }
        int temperature = configuredCoilTemperature(entry);
        if (temperature <= 0) {
            return 1.0D;
        }
        double exponent = (temperature - formula.temperatureOffset()) / (double) formula.temperatureScale();
        double value = formula.leadingMultiplier() * Math.pow(formula.exponentialBase(), exponent);
        return Double.isFinite(value) && value > 0.0D ? value : 1.0D;
    }

    private static double glassDurationMultiplier(Entry entry, GtoGlassDurationFormula formula) {
        if (formula == null) {
            return 1.0D;
        }
        int tier = 0;
        if (entry != null) {
            MachineSettingSpec glass = null;
            for (MachineSettingSpec spec : entry.getMachineProfile().specialSettings()) {
                if (GLASS_TIER.equals(spec.key())) {
                    glass = spec;
                    break;
                }
            }
            if (glass != null) {
                tier = GtoGlassCatalog.tierForChoice(entry.getMachineSettingValue(glass));
            }
        }
        if (tier <= 0) {
            return 1.0D;
        }
        return switch (formula.mode()) {
            case SQRT_RECIPROCAL -> Math.sqrt(1.0D / tier);
            case POWER_RECIPROCAL -> 1.0D / Math.pow(formula.base(), tier);
        };
    }

    private static String configuredTierFormulaText(GtoConfiguredTierParallelFormula formula) {
        if (formula == null) {
            return "--";
        }
        if (formula.mode() == GtoConfiguredTierParallelFormula.Mode.POWER) {
            return formatNumber(formula.factorOrBase()) + "^(Tier - " + formula.exponentOffset() + ")";
        }
        return PlannerText.tr("gto.mod.tier_x", "Tier x") + " " + formatNumber(formula.factorOrBase());
    }

    private static String glassDurationFormulaText(GtoGlassDurationFormula formula) {
        if (formula == null) {
            return "--";
        }
        return switch (formula.mode()) {
            case SQRT_RECIPROCAL -> "sqrt(1 / Glass Tier)";
            case POWER_RECIPROCAL -> "1 / " + formatNumber(formula.base()) + "^Glass Tier";
        };
    }

    private static int saturatedPower(int base, int exponent) {
        long value = 1L;
        for (int i = 0; i < Math.max(0, exponent); i++) {
            value *= Math.max(1, base);
            if (value >= 1_000_000_000L) {
                return 1_000_000_000;
            }
        }
        return (int) value;
    }

    private static int saturatedParallel(long value) {
        if (value <= 1L) return 1;
        return (int) Math.min(1_000_000_000L, value);
    }

    private static int saturatedInt(double value) {
        if (!Double.isFinite(value) || value <= 1.0D) {
            return 1;
        }
        return (int) Math.min(1_000_000_000D, Math.floor(value));
    }

    private static String formatPercent(double multiplier) {
        return String.format(java.util.Locale.ROOT, "%.2f%%", multiplier * 100.0D);
    }

    private static String formatMultiplier(double multiplier) {
        return String.format(java.util.Locale.ROOT, "%.6f", multiplier).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0000001D) {
            return Long.toString(Math.round(value));
        }
        return Double.toString(value);
    }

    private static String yesNo(boolean value) {
        return value
            ? PlannerText.tr("gto.common.yes", "YES")
            : PlannerText.tr("gto.common.no", "NO");
    }
}
