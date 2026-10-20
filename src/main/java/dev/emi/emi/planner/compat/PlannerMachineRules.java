package dev.emi.emi.planner.compat;

import dev.emi.emi.planner.ProductionPlanner.Entry;
import dev.emi.emi.planner.ProductionPlanner.MachineProfile;
import dev.emi.emi.planner.ProductionPlanner.MachineSettingSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PlannerMachineRules {
	private PlannerMachineRules() {
	}

	public static PlannerMachineRule compose(PlannerMachineRule first, PlannerMachineRule second) {
		PlannerMachineRule left = first == null ? PlannerMachineRule.NONE : first;
		PlannerMachineRule right = second == null ? PlannerMachineRule.NONE : second;
		if (left == PlannerMachineRule.NONE) {
			return right;
		}
		if (right == PlannerMachineRule.NONE) {
			return left;
		}
		List<PlannerMachineRule> rules = new ArrayList<>();
		flatten(left, rules);
		flatten(right, rules);
		return new CompositeRule(List.copyOf(rules));
	}

	private static void flatten(PlannerMachineRule rule, List<PlannerMachineRule> output) {
		if (rule instanceof CompositeRule composite) {
			output.addAll(composite.rules);
		} else if (rule != null && rule != PlannerMachineRule.NONE) {
			output.add(rule);
		}
	}

	private static final class CompositeRule implements PlannerMachineRule {
		private final List<PlannerMachineRule> rules;

		private CompositeRule(List<PlannerMachineRule> rules) {
			this.rules = rules;
		}

		@Override
		public String id() {
			StringBuilder builder = new StringBuilder();
			for (PlannerMachineRule rule : rules) {
				String id = rule.id();
				if (id == null || id.isBlank()) {
					continue;
				}
				if (builder.length() > 0) {
					builder.append('+');
				}
				builder.append(id);
			}
			return builder.toString();
		}

		@Override
		public List<MachineSettingSpec> settings(MachineProfile profile) {
			Map<String, MachineSettingSpec> specs = new LinkedHashMap<>();
			for (PlannerMachineRule rule : rules) {
				for (MachineSettingSpec spec : rule.settings(profile)) {
					if (spec != null && spec.key() != null && !spec.key().isBlank()) {
						specs.putIfAbsent(spec.key(), spec);
					}
				}
			}
			return List.copyOf(specs.values());
		}

		@Override
		public int configuredMaxParallel(Entry entry, int fallback) {
			int value = fallback;
			for (PlannerMachineRule rule : rules) {
				value = rule.configuredMaxParallel(entry, value);
			}
			return value;
		}

		@Override
		public boolean allowsRecipe(Entry entry) {
			for (PlannerMachineRule rule : rules) {
				if (!rule.allowsRecipe(entry)) {
					return false;
				}
			}
			return true;
		}

		@Override
		public String constraintError(Entry entry) {
			for (PlannerMachineRule rule : rules) {
				String error = rule.constraintError(entry);
				if (error != null && !error.isBlank()) {
					return error;
				}
			}
			return "";
		}

		@Override
		public double durationMultiplier(Entry entry) {
			double value = 1.0D;
			for (PlannerMachineRule rule : rules) {
				value *= sanitizeMultiplier(rule.durationMultiplier(entry));
			}
			return value;
		}

		@Override
		public double standardOcDurationMultiplier(Entry entry, double fallback) {
			double value = fallback;
			for (PlannerMachineRule rule : rules) {
				value = sanitizeOcMultiplier(rule.standardOcDurationMultiplier(entry, value), value);
			}
			return value;
		}

		@Override
		public double ocDurationMultiplierForStep(Entry entry, int overclockIndex, double fallback) {
			double value = fallback;
			for (PlannerMachineRule rule : rules) {
				value = sanitizeOcMultiplier(rule.ocDurationMultiplierForStep(entry, overclockIndex, value), value);
			}
			return value;
		}

		@Override
		public double energyMultiplier(Entry entry) {
			double value = 1.0D;
			for (PlannerMachineRule rule : rules) {
				value *= sanitizeMultiplier(rule.energyMultiplier(entry));
			}
			return value;
		}

		@Override
		public double throughputMultiplier(Entry entry) {
			double value = 1.0D;
			for (PlannerMachineRule rule : rules) {
				value *= sanitizeMultiplier(rule.throughputMultiplier(entry));
			}
			return value;
		}

		@Override
		public double outputMultiplier(Entry entry) {
			double value = 1.0D;
			for (PlannerMachineRule rule : rules) {
				value *= sanitizeMultiplier(rule.outputMultiplier(entry));
			}
			return value;
		}

		@Override
		public List<String> settingDetails(Entry entry, MachineSettingSpec spec) {
			List<String> lines = new ArrayList<>();
			for (PlannerMachineRule rule : rules) {
				List<String> details = rule.settingDetails(entry, spec);
				if (details != null && !details.isEmpty()) {
					lines.addAll(details);
				}
			}
			return List.copyOf(lines);
		}

		@Override
		public List<String> modifierDescriptions(MachineProfile profile) {
			List<String> lines = new ArrayList<>();
			for (PlannerMachineRule rule : rules) {
				List<String> descriptions = rule.modifierDescriptions(profile);
				if (descriptions != null && !descriptions.isEmpty()) {
					lines.addAll(descriptions);
				}
			}
			return List.copyOf(lines);
		}

		private static double sanitizeMultiplier(double value) {
			return Double.isFinite(value) && value > 0.0D ? value : 1.0D;
		}

		private static double sanitizeOcMultiplier(double value, double fallback) {
			return Double.isFinite(value) && value > 0.0D ? value : fallback;
		}
	}
}
