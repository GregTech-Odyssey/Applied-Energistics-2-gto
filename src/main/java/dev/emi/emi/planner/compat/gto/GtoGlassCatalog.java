package dev.emi.emi.planner.compat.gto;

import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.planner.ProductionPlanner.MachineSettingSpec;

import java.util.ArrayList;
import java.util.List;

/** Runtime catalog for the actual leveled glass blocks used by GTO. */
final class GtoGlassCatalog {
    /*
     * Choice values 0..14 intentionally keep the same numeric value as Stage 2,
     * so existing planner lines remain compatible. The three late-game glasses
     * are appended as choices 15..17 and map to their real glass tiers 18/24/30.
     */
    private static final List<GlassDefinition> DEFINITIONS = List.of(
        new GlassDefinition(0, 0, "AUTO"),
        new GlassDefinition(1, 1, "Glass", "minecraft:glass"),
        new GlassDefinition(2, 2, "Tempered Glass", "gtceu:tempered_glass"),
        new GlassDefinition(3, 3, "Borosilicate Glass", "gtocore:borosilicate_glass"),
        new GlassDefinition(4, 4, "Titanium Borosilicate Glass", "gtocore:titanium_borosilicate_glass"),
        new GlassDefinition(5, 5, "Tungsten Borosilicate Glass", "gtocore:tungsten_borosilicate_glass"),
        new GlassDefinition(6, 6, "HSS-S Borosilicate Glass", "gtocore:hsss_borosilicate_glass"),
        new GlassDefinition(7, 7, "Naquadah Borosilicate Glass", "gtocore:naquadah_borosilicate_glass"),
        new GlassDefinition(8, 8, "Tritanium Borosilicate Glass", "gtocore:tritanium_borosilicate_glass"),
        new GlassDefinition(9, 9, "Amprosium Borosilicate Glass", "gtocore:amprosium_borosilicate_glass"),
        new GlassDefinition(10, 10, "Enderium Borosilicate Glass", "gtocore:enderium_borosilicate_glass"),
        new GlassDefinition(11, 11, "Taranium Borosilicate Glass", "gtocore:taranium_borosilicate_glass"),
        new GlassDefinition(12, 12, "Quark-Borosilicate Glass", "gtocore:quarks_borosilicate_glass"),
        new GlassDefinition(13, 13, "Draconium Borosilicate Glass", "gtocore:draconium_borosilicate_glass"),
        new GlassDefinition(14, 14, "Cosmic Neutronium Borosilicate Glass", "gtocore:cosmic_neutronium_borosilicate_glass"),
        new GlassDefinition(15, 18, "Infinity Glass", "gtocore:infinity_glass"),
        new GlassDefinition(16, 24, "Chaos Infinity Glass", "gtocore:chaos_infinity_glass"),
        new GlassDefinition(17, 30, "Eternity Glass", "gtocore:eternity_glass")
    );
    private static volatile List<GlassOption> options;

    private GtoGlassCatalog() {
    }

    static MachineSettingSpec spec(String key, String labelKey, String englishLabel, String helpKey, String englishHelp) {
        List<GlassOption> resolved = options();
        List<String> names = new ArrayList<>(resolved.size());
        List<EmiStack> icons = new ArrayList<>(resolved.size());
        for (GlassOption option : resolved) {
            names.add(option.displayName());
            icons.add(option.icon());
        }
        return MachineSettingSpec.choice(key, labelKey, englishLabel, names, icons, 0, helpKey, englishHelp);
    }

    static int tierForChoice(int choice) {
        if (choice < 0 || choice >= DEFINITIONS.size()) {
            return 0;
        }
        return DEFINITIONS.get(choice).tier();
    }

    static String displayForTier(int tier) {
        for (GlassOption option : options()) {
            if (option.tier() == tier) {
                return option.displayName();
            }
        }
        return tier <= 0 ? "AUTO" : "T" + tier;
    }

    private static List<GlassOption> options() {
        List<GlassOption> current = options;
        if (current != null) {
            return current;
        }
        synchronized (GtoGlassCatalog.class) {
            if (options == null) {
                options = List.copyOf(buildOptions());
            }
            return options;
        }
    }

    private static List<GlassOption> buildOptions() {
        List<GlassOption> result = new ArrayList<>(DEFINITIONS.size());
        for (GlassDefinition definition : DEFINITIONS) {
            if (definition.choice() == 0) {
                result.add(new GlassOption(definition.tier(), "AUTO", EmiStack.EMPTY));
                continue;
            }
            EmiStack icon = GtoComponentCatalog.resolve(definition.ids());
            String name = GtoComponentCatalog.displayName(icon, definition.fallbackName());
            result.add(new GlassOption(definition.tier(), "T" + definition.tier() + " - " + name, icon));
        }
        return result;
    }

    private record GlassDefinition(int choice, int tier, String fallbackName, String... ids) {
    }

    private record GlassOption(int tier, String displayName, EmiStack icon) {
    }
}
