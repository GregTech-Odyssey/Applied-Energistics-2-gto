package dev.emi.emi.planner.compat.gto;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.planner.compat.PlannerMachineIntrospection;
import dev.emi.emi.runtime.EmiLog;
import net.minecraft.network.chat.Component;

import java.util.*;

public final class GtoCapabilityAudit {
	private static final String[] INTROSPECTION_SIGNALS = {
		"processingtimemultiplier", "durationmultiplier", "speedmultiplier", "recipeefficiency",
		"parallelformula", "parallelmultiplier", "temperatureparallel", "glassparallel", "casingtierlimit"
	};

	private GtoCapabilityAudit() {
	}

	public static AuditResult run() {
		WorkstationScan workstationScan = collectWorkstations();
		Map<String, EmiStack> workstationStacks = workstationScan.plannerWorkstations();
		List<AuditEntry> auto = new ArrayList<>();
		List<AuditEntry> autoSuspicious = new ArrayList<>();
		List<AuditEntry> autoOverride = new ArrayList<>();
		List<AuditEntry> overrideOnly = new ArrayList<>();
		List<AuditEntry> suspiciousNone = new ArrayList<>();
		List<AuditEntry> none = new ArrayList<>();

		for (Map.Entry<String, EmiStack> workstation : workstationStacks.entrySet()) {
			String machineId = workstation.getKey();
			EmiStack stack = workstation.getValue();
			try {
				List<String> tooltip = tooltipLines(stack);
				GtoMachineCapabilityScanner.ScanResult scan = GtoMachineCapabilityScanner.scanDetailed(stack, machineId, tooltip);
				boolean gtoNamespace = machineId.startsWith("gtocore:");
				boolean hasAuto = scan.detected().hasAny();
				boolean hasOverride = scan.override().hasAny();
				if (!gtoNamespace && !hasOverride && !hasAuto) {
					continue;
				}
				String name = displayName(stack, machineId);
				List<String> signals = suspiciousSignals(stack, tooltip, scan.detected());
				AuditEntry entry = new AuditEntry(machineId, name, scan.detected().summary(), scan.override().summary(), signals);
				if (hasAuto && hasOverride) {
					autoOverride.add(entry);
				} else if (hasAuto && !signals.isEmpty()) {
					autoSuspicious.add(entry);
				} else if (hasAuto) {
					auto.add(entry);
				} else if (hasOverride) {
					overrideOnly.add(entry);
				} else if (!signals.isEmpty()) {
					suspiciousNone.add(entry);
				} else {
					none.add(entry);
				}
			} catch (Throwable throwable) {
				if (machineId.startsWith("gtocore:")) {
					String name = displayName(stack, machineId);
					String message = throwable.getMessage() == null ? "" : ": " + throwable.getMessage();
					suspiciousNone.add(new AuditEntry(machineId, name, "", "",
						List.of("audit-error: " + throwable.getClass().getSimpleName() + message)));
				}
				EmiLog.error("GTO capability audit failed for " + machineId, throwable);
			}
		}

		Comparator<AuditEntry> comparator = Comparator.comparing(AuditEntry::name, String.CASE_INSENSITIVE_ORDER)
			.thenComparing(AuditEntry::machineId);
		auto.sort(comparator);
		autoSuspicious.sort(comparator);
		autoOverride.sort(comparator);
		overrideOnly.sort(comparator);
		suspiciousNone.sort(comparator);
		none.sort(comparator);

		int total = auto.size() + autoSuspicious.size() + autoOverride.size() + overrideOnly.size() + suspiciousNone.size() + none.size();
		String report = buildReport(total, workstationScan.allWorkstationCount(), workstationStacks.size(),
			workstationScan.skippedNoOutputCount(), auto, autoSuspicious, autoOverride, overrideOnly, suspiciousNone, none);
		EmiLog.info("GTO Production Planner capability audit:\n" + report);
		return new AuditResult(report, total, auto.size(), autoSuspicious.size(), autoOverride.size(), overrideOnly.size(),
			suspiciousNone.size(), none.size(), workstationScan.skippedNoOutputCount());
	}

	private static WorkstationScan collectWorkstations() {
		Map<String, EmiStack> all = new LinkedHashMap<>();
		Map<String, EmiStack> planner = new LinkedHashMap<>();
		try {
			for (EmiRecipeCategory category : EmiApi.getRecipeManager().getCategories()) {
				boolean plannerReachable = categoryHasPlannerOutput(category);
				for (EmiIngredient workstation : EmiApi.getRecipeManager().getWorkstations(category)) {
					for (EmiStack stack : workstation.getEmiStacks()) {
						if (stack == null || stack.isEmpty() || stack.getId() == null) {
							continue;
						}
						String id = stack.getId().toString();
						all.putIfAbsent(id, stack);
						if (plannerReachable) {
							planner.putIfAbsent(id, stack);
						}
					}
				}
			}
		} catch (Throwable throwable) {
			EmiLog.error("Failed to collect workstations for GTO capability audit", throwable);
		}
		return new WorkstationScan(planner, all.size(), Math.max(0, all.size() - planner.size()));
	}

	private static boolean categoryHasPlannerOutput(EmiRecipeCategory category) {
		try {
			for (EmiRecipe recipe : EmiApi.getRecipeManager().getRecipes(category)) {
				if (recipe == null || recipe.getOutputs() == null) {
					continue;
				}
				for (EmiStack output : recipe.getOutputs()) {
					if (output != null && !output.isEmpty()) {
						return true;
					}
				}
			}
			return false;
		} catch (Throwable throwable) {
			return true;
		}
	}

	private static List<String> tooltipLines(EmiStack stack) {
		List<String> lines = new ArrayList<>();
		try {
			for (Component text : stack.getTooltipText()) {
				if (text == null) {
					continue;
				}
				String value = text.getString().trim();
				if (!value.isEmpty()) {
					lines.add(value);
				}
			}
		} catch (Throwable ignored) {
		}
		return lines;
	}

	private static String displayName(EmiStack stack, String fallback) {
		try {
			if (stack.getName() != null) {
				String value = stack.getName().getString().trim();
				if (!value.isEmpty()) {
					return value;
				}
			}
		} catch (Throwable ignored) {
		}
		return fallback;
	}

	private static List<String> suspiciousSignals(EmiStack stack, List<String> tooltip, GtoMachineCapabilities detected) {
		Set<String> result = new LinkedHashSet<>();
		GtoMachineCapabilities capabilities = detected == null ? GtoMachineCapabilities.EMPTY : detected;
		boolean gtceuHeatHandled = hasGtceuHeatMechanics(tooltip);
		for (int i = 0; i < tooltip.size(); i++) {
			String raw = tooltip.get(i);
			String lower = raw.toLowerCase(Locale.ROOT);
			if (isContextualNonPlannerSignal(tooltip, i)) {
				continue;
			}
			if (isSuspiciousTooltipLine(lower) && !isModeledTooltipLine(lower, capabilities, gtceuHeatHandled)) {
				result.add("tooltip: " + compactTooltip(raw));
				if (i > 1) {
					result.add("context-before-2: " + compactTooltip(tooltip.get(i - 2)));
				}
				if (i > 0) {
					result.add("context-before: " + compactTooltip(tooltip.get(i - 1)));
				}
				if (i + 1 < tooltip.size()) {
					result.add("context-after: " + compactTooltip(tooltip.get(i + 1)));
				}
				if (i + 2 < tooltip.size()) {
					result.add("context-after-2: " + compactTooltip(tooltip.get(i + 2)));
				}
				if (result.size() >= 15) {
					break;
				}
			}
		}
		if (result.size() < 15) {
			PlannerMachineIntrospection.Snapshot inspection = PlannerMachineIntrospection.inspect(stack);
			for (String signal : INTROSPECTION_SIGNALS) {
				if (inspection.containsAny(signal) && !isModeledIntrospectionSignal(signal, capabilities)) {
					result.add("introspection: " + signal);
					if (result.size() >= 15) {
						break;
					}
				}
			}
		}
		if (!result.isEmpty()) {
			for (String probe : PlannerMachineIntrospection.formulaProbe(stack, 320)) {
				result.add("formula-probe: " + compactTooltip(probe));
			}
		}
		return List.copyOf(result);
	}

	private static boolean hasGtceuHeatMechanics(List<String> tooltip) {
		String joined = String.join("\n", tooltip == null ? List.of() : tooltip)
			.replaceAll("§.", "")
			.toLowerCase(Locale.ROOT);
		return joined.contains("every 900k") && joined.contains("1800k") && joined.contains("recipe temperature");
	}

	private static boolean isContextualNonPlannerSignal(List<String> tooltip, int index) {
		if (tooltip == null || index < 0 || index >= tooltip.size()) {
			return false;
		}
		String lower = tooltip.get(index).toLowerCase(Locale.ROOT);
		if (!lower.contains("coil efficiency bonus")) {
			return false;
		}
		for (int offset = -2; offset <= 2; offset++) {
			if (offset == 0) continue;
			int other = index + offset;
			if (other < 0 || other >= tooltip.size()) continue;
			String nearby = tooltip.get(other).toLowerCase(Locale.ROOT);
			if (nearby.contains("rotor startup speed") || nearby.contains("rotor wear rate")) {
				return true;
			}
		}
		return false;
	}

	private static boolean isModeledTooltipLine(String lower, GtoMachineCapabilities capabilities, boolean gtceuHeatHandled) {
		if (lower == null || lower.isBlank()) {
			return true;
		}
		if (lower.contains("no parallel control hatch required") || lower.contains("without parallel control hatch")) return true;
		if (lower.contains("parallel control") && capabilities.parallelControl()) return true;
		if ((lower.contains("acceleration hatch") || lower.contains("accelerate hatch")) && capabilities.acceleration()) return true;
		if (lower.contains("thread hatch") && capabilities.thread()) return true;
		if ((lower.contains("overclocking hatch") || lower.contains("overclock hatch")) && capabilities.overclocking()) return true;
		if (lower.contains("auxiliary module") && capabilities.auxiliaryModules()) return true;
		if ((lower.contains("perfect overclock") || lower.contains("perfect oc"))
				&& (capabilities.enablesPerfectOc() || capabilities.disablesPerfectOc())) return true;
		if (lower.contains("processing time multiplier") && capabilities.processingTimeMultiplier() > 0.0D) return true;
		if (lower.contains("casing tier") && capabilities.casingTierLimit()) return true;
		if (lower.contains("glass tier") && capabilities.needsGlassTierSetting()) return true;
		if (lower.contains("coil") && lower.contains("parallel") && capabilities.hasCoilParallel()) return true;
		if (lower.contains("parallel") && lower.contains("temperature") && capabilities.coilLogParallelFormula() != null) return true;
		if (lower.contains("parallel") && lower.contains("voltage tier") && capabilities.voltageParallelFormula() != null) return true;
		if (lower.contains("parallel") && capabilities.configuredTierParallelFormula() != null) {
			String key = capabilities.configuredTierParallelFormula().settingKey();
			if (key.contains("power_module") && lower.contains("power module")) return true;
			if (key.contains("hermetic") && lower.contains("hermetic")) return true;
			if (key.contains("energy_parallel") && lower.contains("eu consumed")) return true;
			if (key.contains("same_recipe_robots") && lower.contains("recipe robots")) return true;
		}
		if ((lower.contains("duration") || lower.contains("recipe time") || lower.contains("speed"))
				&& lower.contains("glass tier") && capabilities.glassDurationFormula() != null) return true;
		if (capabilities.coilTierEfficiencyFormula() != null && lower.contains("coil")
				&& (lower.contains("efficiency") || lower.contains("energy") || lower.contains("duration") || lower.contains("recipe time"))) return true;
		if (capabilities.coilTemperatureDurationFormula() != null
				&& (lower.contains("coil") || lower.contains("temperature"))
				&& (lower.contains("speed") || lower.contains("faster") || lower.contains("log("))) return true;
		if (capabilities.coilExponentialDurationFormula() != null
				&& (lower.contains("coil") || lower.contains("temperature") || lower.contains("time multiplier"))
				&& (lower.contains("faster") || lower.contains("multiplier") || lower.contains("temperature"))) return true;
		if (capabilities.auxiliaryParallel() > 0 && lower.contains("parallelism increases to")) return true;
		if (capabilities.auxiliaryDurationMultiplier() > 0.0D && lower.contains("duration reduction")) return true;
		if (capabilities.configuredCountDurationFormula() != null
				&& (lower.contains("additional speeding pipe") || lower.contains("efficiency formula"))) return true;
		if (capabilities.structureTemperatureFormula() != null
				&& (lower.contains("steel frames") || lower.contains("parallel multiplier") && lower.contains("500k")
					|| lower.contains("400 / temperature"))) return true;
		if (capabilities.tierDurationFormula() != null && lower.contains("duration reduction") && lower.contains("tier")) return true;
		if (capabilities.tierParallelFormula() != null
				&& (lower.contains("parallel multiplier") || lower.contains("special parallel mechanic") || lower.contains("built-in parallel processing"))) return true;
		if (capabilities.productionBoostFormula() != null
				&& (lower.contains("production-boosting mode") || lower.contains("more time, higher output"))) return true;
		if (capabilities.neutronFluxDurationFormula() != null && lower.contains("current neutron flux")
				&& lower.contains("required neutron flux")) return true;
		if (capabilities.fissionCoolingFormula() != null
				&& (lower.contains("demand =") && lower.contains("recipe heat generation") && lower.contains("actual parallel")
					|| lower.contains("supply =") && lower.contains("cooling component"))) return true;
		if (gtceuHeatHandled && (lower.contains("900k") || lower.contains("1800k"))
				&& lower.contains("recipe temperature")) return true;
		if (capabilities.processingTimeMultiplier() > 0.0D
				&& (lower.contains("after first run") || lower.contains("subsequent run") || lower.contains("duration reduction"))) return true;
		if (lower.contains("parallel processing architecture") && capabilities.fixedParallel() > 0) return true;
		if (lower.contains("parallel") && capabilities.fixedParallel() > 0 && !looksDynamicParallelSignal(lower)) return true;
		if (lower.contains("overclock") && capabilities.standardOcDurationMultiplier() > 0.0D) return true;
		return false;
	}

	private static boolean isModeledIntrospectionSignal(String signal, GtoMachineCapabilities capabilities) {
		return switch (signal) {
			case "processingtimemultiplier", "durationmultiplier", "speedmultiplier" -> capabilities.processingTimeMultiplier() > 0.0D
				|| capabilities.glassDurationFormula() != null || capabilities.coilTierEfficiencyFormula() != null
				|| capabilities.coilTemperatureDurationFormula() != null || capabilities.coilExponentialDurationFormula() != null
				|| capabilities.configuredCountDurationFormula() != null || capabilities.structureTemperatureFormula() != null
				|| capabilities.tierDurationFormula() != null || capabilities.productionBoostFormula() != null
				|| capabilities.neutronFluxDurationFormula() != null || capabilities.auxiliaryDurationMultiplier() > 0.0D
				|| capabilities.acceleration();
			case "parallelformula", "parallelmultiplier", "temperatureparallel", "glassparallel" -> capabilities.fixedParallel() > 0
				|| capabilities.glassParallelBase() > 1 || capabilities.hasCoilParallel() || capabilities.coilLogParallelFormula() != null
				|| capabilities.voltageParallelFormula() != null || capabilities.configuredTierParallelFormula() != null
				|| capabilities.structureTemperatureFormula() != null || capabilities.tierParallelFormula() != null
				|| capabilities.fissionCoolingFormula() != null || capabilities.auxiliaryParallel() > 0;
			case "casingtierlimit" -> capabilities.casingTierLimit();
			default -> false;
		};
	}

	private static boolean looksDynamicParallelSignal(String lower) {
		return lower.contains("for each") || lower.contains("for every") || lower.contains("each layer")
			|| lower.contains("tier") || lower.contains("temperature") || lower.contains("formula")
			|| lower.contains("multiplier") || lower.contains("robots") || lower.contains("plasma")
			|| lower.contains("current neutron") || lower.contains("actual parallel") || lower.contains("^")
			|| lower.contains("log") || lower.contains("×") && lower.contains("(");
	}

	private static boolean isSuspiciousTooltipLine(String lower) {
		if (lower == null || lower.isBlank()) {
			return false;
		}
		if (isNonPlannerGeneratorSignal(lower)) {
			return false;
		}
		if (lower.contains("parallel control") || lower.contains("overclocking hatch") || lower.contains("overclock hatch")
				|| lower.contains("thread hatch") || lower.contains("acceleration hatch") || lower.contains("accelerate hatch")
				|| lower.contains("auxiliary module") || lower.contains("glass tier") || lower.contains("casing tier")
				|| lower.contains("processing time multiplier")) {
			return true;
		}
		if (lower.contains("parallel") && (lower.matches(".*[0-9].*") || lower.contains("formula") || lower.contains("multiplier"))) {
			return true;
		}
		if (lower.contains("coil")) {
			if (lower.contains("parallel")) {
				return true;
			}
			if (lower.contains("higher") && (lower.contains("temperature") || lower.contains("faster") || lower.contains("speed"))) {
				return true;
			}
			if ((lower.contains("duration") || lower.contains("recipe time") || lower.contains("energy") || lower.contains("efficien"))
					&& (lower.matches(".*[0-9].*") || lower.contains("%") || lower.contains("reduc")
						|| lower.contains("each ") || lower.contains("every ") || lower.contains("bonus") || lower.contains("multiplier"))) {
				return true;
			}
		}
		if ((lower.contains("duration") || lower.contains("recipe time") || lower.contains("speed"))
				&& (lower.contains("multiplier") || lower.contains("reduc") || lower.contains("discount")
					|| lower.contains("bonus") || lower.contains("penalty") || lower.contains("%"))) {
			return true;
		}
		if (lower.contains("eu cost") && lower.contains("parallel")) {
			return true;
		}
		return lower.contains("overclock") && (lower.contains("multiplier") || lower.contains("imperfect") || lower.contains("perfect"));
	}

	private static boolean isNonPlannerGeneratorSignal(String lower) {
		return lower.contains("high-speed mode energy output multiplier")
			|| lower.contains("rotor damage multiplier")
			|| lower.contains("maintenance issue chance multiplier")
			|| lower.contains("rotor startup speed")
			|| lower.contains("glass tier limits the energy output hatch tier")
			|| lower.contains("hermetic casing tier") && lower.contains("efficiency is increased");
	}

	private static String compactTooltip(String raw) {
		String compact = raw == null ? "" : raw.replaceAll("\\s+", " ").trim();
		if (compact.length() > 220) {
			return compact.substring(0, 217) + "...";
		}
		return compact;
	}

	private static String buildReport(int total, int allWorkstationCount, int plannerWorkstationCount, int skippedNoOutputCount,
			List<AuditEntry> auto, List<AuditEntry> autoSuspicious, List<AuditEntry> autoOverride, List<AuditEntry> overrideOnly,
			List<AuditEntry> suspiciousNone, List<AuditEntry> none) {
		StringBuilder report = new StringBuilder();
		report.append("=== GTO Production Planner Capability Audit ===\n");
		report.append("Workstation stacks scanned: ").append(allWorkstationCount).append('\n');
		report.append("Planner-reachable workstation stacks: ").append(plannerWorkstationCount).append('\n');
		report.append("Skipped workstations with no item/fluid recipe output: ").append(skippedNoOutputCount).append('\n');
		report.append("GTO/override machines included: ").append(total).append("\n\n");
		report.append("AUTO = detected and no extra suspicious tooltip/introspection mechanics remain\n");
		report.append("AUTO + SUSPICIOUS = at least one mechanic was detected, but another hint is still unmodeled\n");
		report.append("SUSPICIOUS NONE = no known capability, but tooltip/introspection hints at an unmodeled mechanic\n");
		report.append("NONE = no GTO-specific mechanic detected; this is not automatically an error\n\n");
		report.append("AUTO: ").append(auto.size()).append('\n');
		report.append("AUTO + SUSPICIOUS: ").append(autoSuspicious.size()).append('\n');
		report.append("AUTO + OVERRIDE: ").append(autoOverride.size()).append('\n');
		report.append("OVERRIDE ONLY: ").append(overrideOnly.size()).append('\n');
		report.append("SUSPICIOUS NONE: ").append(suspiciousNone.size()).append('\n');
		report.append("NONE: ").append(none.size()).append("\n\n");
		appendSection(report, "AUTO", auto, true, false, false);
		appendSection(report, "AUTO + SUSPICIOUS", autoSuspicious, true, false, true);
		appendSection(report, "AUTO + OVERRIDE", autoOverride, true, true, true);
		appendSection(report, "OVERRIDE ONLY", overrideOnly, false, true, true);
		appendSection(report, "SUSPICIOUS NONE", suspiciousNone, false, false, true);
		appendSection(report, "NONE", none, false, false, false);
		return report.toString();
	}

	private static void appendSection(StringBuilder report, String title, List<AuditEntry> entries,
			boolean showAuto, boolean showOverride, boolean showSignals) {
		report.append("=== ").append(title).append(" (").append(entries.size()).append(") ===\n");
		if (entries.isEmpty()) {
			report.append("<empty>\n\n");
			return;
		}
		for (AuditEntry entry : entries) {
			report.append(entry.name()).append(" [").append(entry.machineId()).append("]\n");
			if (showAuto) {
				report.append("  AUTO: ").append(entry.autoSummary().isBlank() ? "<none>" : entry.autoSummary()).append('\n');
			}
			if (showOverride) {
				report.append("  OVERRIDE: ").append(entry.overrideSummary().isBlank() ? "<none>" : entry.overrideSummary()).append('\n');
			}
			if (showSignals) {
				for (String signal : entry.signals()) {
					report.append("  SIGNAL: ").append(signal).append('\n');
				}
			}
		}
		report.append('\n');
	}

	private record AuditEntry(String machineId, String name, String autoSummary, String overrideSummary, List<String> signals) {
	}

	private record WorkstationScan(Map<String, EmiStack> plannerWorkstations, int allWorkstationCount, int skippedNoOutputCount) {
	}

	public record AuditResult(String report, int total, int auto, int autoSuspicious, int autoOverride,
			int overrideOnly, int suspiciousNone, int none, int skippedNoOutput) {
		public String shortStatus() {
			return "GTO audit copied: " + total + " planner machines, suspicious " + (autoSuspicious + suspiciousNone)
				+ ", skipped " + skippedNoOutput;
		}
	}
}
