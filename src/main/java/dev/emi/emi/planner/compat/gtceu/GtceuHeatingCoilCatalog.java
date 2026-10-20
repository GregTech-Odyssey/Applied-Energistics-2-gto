package dev.emi.emi.planner.compat.gtceu;

import dev.emi.emi.EmiPort;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.planner.ProductionPlanner.MachineSettingSpec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GtceuHeatingCoilCatalog {
	private record Coil(String name, int temperature) {
	}

	private static final List<Coil> COILS = buildCoils();
	private static final List<String> NAMES = COILS.stream().map(Coil::name).toList();
	private static volatile List<EmiStack> icons;

	private GtceuHeatingCoilCatalog() {
	}

	public static MachineSettingSpec spec(String key, String labelKey, String englishLabel, String helpKey, String englishHelp) {
		return MachineSettingSpec.choice(key, labelKey, englishLabel, NAMES, icons(), 0, helpKey, englishHelp);
	}

	public static int temperatureForChoice(int choice) {
		return choice >= 0 && choice < COILS.size() ? COILS.get(choice).temperature() : 0;
	}

	public static String nameForChoice(int choice) {
		return choice >= 0 && choice < COILS.size() ? COILS.get(choice).name() : "AUTO";
	}

	public static int temperatureForName(String name) {
		if (name == null || name.isBlank()) {
			return 0;
		}
		for (Coil coil : COILS) {
			if (coil.name().equalsIgnoreCase(name)) {
				return coil.temperature();
			}
		}
		return 0;
	}

	public static EmiStack iconForChoice(int choice) {
		List<EmiStack> resolved = icons();
		return choice >= 0 && choice < resolved.size() ? resolved.get(choice) : EmiStack.EMPTY;
	}

	public static EmiStack iconForName(String name) {
		if (name == null || name.isBlank()) {
			return EmiStack.EMPTY;
		}
		for (int i = 0; i < COILS.size(); i++) {
			if (COILS.get(i).name().equalsIgnoreCase(name)) {
				return iconForChoice(i);
			}
		}
		return resolveIcon(name);
	}

	private static List<EmiStack> icons() {
		List<EmiStack> current = icons;
		if (current != null) {
			return current;
		}
		synchronized (GtceuHeatingCoilCatalog.class) {
			if (icons == null) {
				List<EmiStack> resolved = new ArrayList<>(COILS.size());
				for (Coil coil : COILS) {
					resolved.add(resolveIcon(coil.name()));
				}
				icons = List.copyOf(resolved);
			}
			return icons;
		}
	}

	private static EmiStack resolveIcon(String coilName) {
		if (coilName == null || coilName.isBlank() || "AUTO".equalsIgnoreCase(coilName)) {
			return EmiStack.EMPTY;
		}
		try {
			String needle = normalize(coilName);
			ResourceLocation best = null;
			int bestScore = Integer.MIN_VALUE;
			for (ResourceLocation id : EmiPort.getBlockRegistry().keySet()) {
				String path = normalize(id.getPath());
				if (!path.contains("coil") || !path.contains(needle)) {
					continue;
				}
				int score = 0;
				if (path.startsWith(needle)) {
					score += 100;
				}
				if (path.equals(needle + "coilblock") || path.equals(needle + "heatingcoil")) {
					score += 200;
				}
				if ("gtceu".equals(id.getNamespace())) {
					score += 20;
				} else if (id.getNamespace().toLowerCase(Locale.ROOT).contains("gto")) {
					score += 10;
				}
				score -= path.length();
				if (score > bestScore) {
					bestScore = score;
					best = id;
				}
			}
			if (best != null) {
				Block block = EmiPort.getBlockRegistry().get(best);
				if (block != null) {
					EmiStack stack = EmiStack.of(block);
					if (!stack.isEmpty()) {
						return stack;
					}
				}
			}
		} catch (Throwable ignored) {
		}
		return EmiStack.EMPTY;
	}

	private static String normalize(String value) {
		if (value == null) {
			return "";
		}
		StringBuilder out = new StringBuilder(value.length());
		for (int i = 0; i < value.length(); i++) {
			char c = Character.toLowerCase(value.charAt(i));
			if (Character.isLetterOrDigit(c)) {
				out.append(c);
			}
		}
		return out.toString();
	}

	private static List<Coil> buildCoils() {
		List<Coil> coils = new ArrayList<>();

		// Keep the original GTCEu order stable: machine CFG values are persisted as numeric choice indexes.
		coils.add(new Coil("AUTO", 0));
		coils.add(new Coil("Cupronickel", 1800));
		coils.add(new Coil("Kanthal", 2700));
		coils.add(new Coil("Nichrome", 3600));
		coils.add(new Coil("RTM Alloy", 4500));
		coils.add(new Coil("HSS-G", 5400));
		coils.add(new Coil("Naquadah", 7200));
		coils.add(new Coil("Trinium", 9001));
		coils.add(new Coil("Tritanium", 10800));

		if (isGtoCorePresent()) {
			// GTOCore heating coils. Append only so existing saved GTCEu coil indexes never shift.
			coils.add(new Coil("Abyssalalloy", 12600));
			coils.add(new Coil("Titansteel", 14400));
			coils.add(new Coil("Adamantine", 16200));
			coils.add(new Coil("Naquadriatictaranium", 18900));
			coils.add(new Coil("Starmetal", 21600));
			coils.add(new Coil("Infinity", 36000));
			coils.add(new Coil("Hypogen", 62000));
			coils.add(new Coil("Eternity", 96000));
		}

		return List.copyOf(coils);
	}

	private static boolean isGtoCorePresent() {
		try {
			Class.forName("com.gtocore.common.data.GTOBlocks", false, GtceuHeatingCoilCatalog.class.getClassLoader());
			return true;
		} catch (ClassNotFoundException | LinkageError ignored) {
			return false;
		}
	}
}
