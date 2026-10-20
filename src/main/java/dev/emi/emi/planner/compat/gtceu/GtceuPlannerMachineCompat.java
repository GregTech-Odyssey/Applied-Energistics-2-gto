package dev.emi.emi.planner.compat.gtceu;

import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.forge.EmiForgePlatform;
import dev.emi.emi.planner.compat.PlannerMachineCompatProvider;
import dev.emi.emi.planner.compat.PlannerMachineRuntimeOverride;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class GtceuPlannerMachineCompat implements PlannerMachineCompatProvider {
    private static final Map<String, String> RU = Map.ofEntries(
        Map.entry("gtceu.ebf.coil", "Нагревательная катушка"),
        Map.entry("gtceu.ebf.coil_help", "Выбранная катушка задает базовую температуру EBF; AUTO сохраняет прежний расчет без бонусов нагрева"),
        Map.entry("gtceu.ebf.auto_heat", "AUTO: бонусы нагрева EBF не моделируются до выбора катушки"),
        Map.entry("gtceu.ebf.coil_heat", "Температура катушки"),
        Map.entry("gtceu.ebf.voltage_heat", "Бонус температуры от напряжения"),
        Map.entry("gtceu.ebf.effective_heat", "Итоговая температура машины"),
        Map.entry("gtceu.ebf.recipe_heat", "Требуемая температура рецепта"),
        Map.entry("gtceu.ebf.excess_heat", "Избыточная температура"),
        Map.entry("gtceu.ebf.energy_steps", "Шаги скидки энергии по 900K"),
        Map.entry("gtceu.ebf.energy_multiplier", "Множитель EU/t от нагрева"),
        Map.entry("gtceu.ebf.perfect_ocs", "Доступные тепловые perfect OC"),
        Map.entry("gtceu.ebf.applied_perfect_ocs", "Примененные тепловые perfect OC"),
        Map.entry("gtceu.ebf.heat_too_low", "Температура EBF слишком низкая"),
        Map.entry("gtceu.note.modeled", "GTCEu: механика температуры Electric Blast Furnace смоделирована"),
        Map.entry("gtceu.note.recipe_type", "GTCEu: тип рецепта EBF обнаружен автоматически"),
        Map.entry("gtceu.note.coils", "GTCEu: поддержка нагревательных катушек обнаружена автоматически"),
        Map.entry("gtceu.note.inherited", "GTCEu: механика температуры EBF получена из типа рецепта + поддержки нагревательных катушек"),
        Map.entry("gtceu.ebf.recipe_heat_unknown", "Температура рецепта не определена")
    );

    @Override
    public boolean isActive() {
        return EmiForgePlatform.isModLoaded("gtceu");
    }

    @Override
    public PlannerMachineRuntimeOverride resolve(EmiStack machineStack, String machineId, List<String> tooltipLines) {
        GtceuMachineCapabilityScanner.Capabilities capabilities =
            GtceuMachineCapabilityScanner.scan(machineStack, machineId, tooltipLines);
        if (!capabilities.ebfHeatMechanics()) {
            return null;
        }

        List<String> notes = new ArrayList<>();
        notes.add("GTCEu Electric Blast Furnace heat mechanics modeled");
        if (capabilities.electricBlastFurnaceRecipes()) {
            notes.add("GTCEu EBF recipe type detected automatically");
        }
        if (capabilities.heatingCoils()) {
            notes.add("GTCEu heating-coil capability detected automatically");
        }
        if (!capabilities.explicitEbfHeat()) {
            notes.add("GTCEu EBF heat inherited from recipe type + heating-coil capability");
        }

        return new PlannerMachineRuntimeOverride(
            null,
            null,
            Boolean.TRUE,
            Boolean.FALSE,
            null,
            null,
            null,
            null,
            null,
            GtceuElectricBlastFurnaceRule.INSTANCE,
            List.copyOf(notes)
        );
    }


    @Override
    public String russianText(String key) {
        return RU.get(key);
    }
}
