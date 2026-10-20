package dev.emi.emi.planner.compat.gtceu;

import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.planner.compat.PlannerMachineIntrospection;

import java.util.List;
import java.util.Locale;

final class GtceuMachineCapabilityScanner {
    private GtceuMachineCapabilityScanner() {
    }

    static Capabilities scan(EmiStack stack, String machineId, List<String> tooltipLines) {
        String id = machineId == null ? "" : machineId.toLowerCase(Locale.ROOT);
        String tooltip = String.join("\n", tooltipLines == null ? List.of() : tooltipLines).toLowerCase(Locale.ROOT);
        PlannerMachineIntrospection.Snapshot inspection = PlannerMachineIntrospection.inspect(stack);

        boolean standardEbfId = "gtceu:electric_blast_furnace".equals(id);
        boolean electricBlastFurnaceRecipes = standardEbfId
            || inspection.containsAny("electric_blast_furnace", "electricblastfurnace", "blast_recipes", "blastrecipes")
            || hasElectricBlastFurnaceRecipeType(tooltip);

        boolean explicitEbfHeat = standardEbfId || hasStandardEbfHeatDescription(tooltip);

        boolean heatingCoils = inspection.containsAny(
            "coilworkableelectricmultiblockmachine", "coilworkablemultiblockmachine",
            "heatingcoils", "heating_coils", "heatingcoil", "coiltype", "coilpredicate")
            || tooltip.contains("heating coil")
            || isBlastFurnaceFamilyId(id);

        boolean ebfHeatMechanics = explicitEbfHeat || electricBlastFurnaceRecipes && heatingCoils;
        return new Capabilities(electricBlastFurnaceRecipes, heatingCoils, ebfHeatMechanics,
            explicitEbfHeat, inspection);
    }

    private static boolean hasElectricBlastFurnaceRecipeType(String tooltip) {
        if (tooltip == null || tooltip.isBlank()) {
            return false;
        }
        for (String line : tooltip.split("\\R")) {
            String lower = line.trim().toLowerCase(Locale.ROOT);
            if ((lower.contains("recipe type") || lower.contains("recipe map"))
                    && lower.contains("electric blast furnace")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasStandardEbfHeatDescription(String tooltip) {
        return tooltip != null
            && tooltip.contains("every 900k")
            && tooltip.contains("1800k")
            && tooltip.contains("recipe temperature");
    }

    private static boolean isBlastFurnaceFamilyId(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        int colon = id.indexOf(':');
        String path = colon >= 0 && colon + 1 < id.length() ? id.substring(colon + 1) : id;
        return path.endsWith("blast_furnace") || path.contains("_blast_furnace_");
    }

    record Capabilities(
            boolean electricBlastFurnaceRecipes,
            boolean heatingCoils,
            boolean ebfHeatMechanics,
            boolean explicitEbfHeat,
            PlannerMachineIntrospection.Snapshot inspection) {
    }
}
