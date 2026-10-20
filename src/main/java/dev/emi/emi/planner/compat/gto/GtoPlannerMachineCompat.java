package dev.emi.emi.planner.compat.gto;

import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.forge.EmiForgePlatform;
import dev.emi.emi.planner.compat.PlannerMachineCompatProvider;
import dev.emi.emi.planner.compat.PlannerMachineRuntimeOverride;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class GtoPlannerMachineCompat implements PlannerMachineCompatProvider {
    private static final Map<String, String> RU = Map.ofEntries(
        Map.entry("gto.precision.glass_tier", "Стекло"),
        Map.entry("gto.precision.glass_tier_help", "Установленное стекло определяет механику машины, обнаруженную по её подсказке"),
        Map.entry("gto.precision.casing_tier", "Тир корпуса машины"),
        Map.entry("gto.precision.casing_tier_help", "Тир рецепта не может быть выше тира корпуса машины; AUTO использует минимум рецепта"),
        Map.entry("gto.precision.current_parallel", "Текущие встроенные параллели"),
        Map.entry("gto.precision.recipe_tier", "Тир рецепта"),
        Map.entry("gto.precision.recipe_allowed", "Рецепт разрешен"),
        Map.entry("gto.precision.casing_tier_low", "Тир корпуса машины слишком низкий"),
        Map.entry("gto.auto.coil_parallel", "Нагревательная катушка"),
        Map.entry("gto.auto.coil_parallel_help", "Выбранная катушка управляет автоматически обнаруженной формулой встроенного parallel"),
        Map.entry("gto.auto.coil_parallel_auto", "AUTO сохраняет обнаруженный/профильный лимит parallel"),
        Map.entry("gto.auto.coil_temperature", "Температура катушки"),
        Map.entry("gto.auto.selected_coil", "Выбранная нагревательная катушка"),
        Map.entry("gto.auto.coil_tiers_above_reference", "Тиров катушки выше Cupronickel"),
        Map.entry("gto.auto.energy_multiplier", "Множитель энергии"),
        Map.entry("gto.auto.parallel_steps", "Шаги формулы parallel"),
        Map.entry("gto.auto.detected_formula", "Обнаруженная формула"),
        Map.entry("gto.dynamic.power_module_tier", "Тир силового модуля"),
        Map.entry("gto.dynamic.power_module_tier_help", "Тир установленного силового модуля задает встроенный parallel"),
        Map.entry("gto.dynamic.hermetic_casing_tier", "Тир герметичного корпуса"),
        Map.entry("gto.dynamic.hermetic_casing_tier_help", "Тир установленного герметичного корпуса задает встроенный parallel"),
        Map.entry("gto.dynamic.selected_glass_tier", "Выбранное стекло"),
        Map.entry("gto.dynamic.selected_structure_tier", "Выбранный тир структуры"),
        Map.entry("gto.dynamic.duration_multiplier", "Множитель времени"),
        Map.entry("gto.dynamic.production_boost", "Режим увеличения производства"),
        Map.entry("gto.dynamic.production_boost_help", "Увеличивает выход рецепта ценой обнаруженного штрафа времени и энергии/пара"),
        Map.entry("gto.dynamic.production_boost_state", "Режим увеличения производства"),
        Map.entry("gto.dynamic.output_multiplier", "Множитель выхода"),
        Map.entry("gto.dynamic.energy_steam_multiplier", "Множитель энергии/пара"),
        Map.entry("gto.dynamic.glass_tier_low", "Тир стекла слишком низкий"),
        Map.entry("gto.hatch.auxiliary", "Вспомогательные модули"),
        Map.entry("gto.hatch.auxiliary_help", "Включите, если расширение/вспомогательный модуль мультиблока установлен"),
        Map.entry("gto.hatch.auxiliary_state", "Вспомогательные модули включены"),
        Map.entry("gto.hatch.parallel_limit", "Лимит параллельного хэтча"),
        Map.entry("gto.hatch.parallel_limit_help", "Максимальный parallel, указанный на Parallel Control Hatch; PAR в строке остается текущей настройкой хэтча"),
        Map.entry("gto.hatch.acceleration_tier", "Тир хэтча ускорения"),
        Map.entry("gto.hatch.acceleration_tier_help", "Тир Acceleration Hatch; за каждый недостающий относительно рецепта тир добавляется 20% времени"),
        Map.entry("gto.hatch.acceleration_multiplier", "Настройка ускорения"),
        Map.entry("gto.hatch.acceleration_multiplier_help", "Желаемый процент времени; MAX SPEED использует максимальное ускорение выбранного тира"),
        Map.entry("gto.hatch.thread_tier", "Тир потокового хэтча"),
        Map.entry("gto.hatch.thread_tier_help", "Тир Thread Hatch определяет максимальное число одновременно работающих потоков рецепта"),
        Map.entry("gto.hatch.thread_count", "Количество потоков"),
        Map.entry("gto.hatch.thread_count_help", "Число активных потоков рецепта; MAX использует максимум выбранного Thread Hatch"),
        Map.entry("gto.hatch.overclock_tier", "Тир хэтча оверклока"),
        Map.entry("gto.hatch.overclock_tier_help", "Тир Overclocking Hatch задает максимальный делитель времени на каждый оверклок x4 EU/t"),
        Map.entry("gto.hatch.overclock_divisor", "Делитель времени OC"),
        Map.entry("gto.hatch.overclock_divisor_help", "Делитель времени на каждый оверклок x4 EU/t; MAX использует максимум выбранного тира"),
        Map.entry("gto.hatch.max_oc_divisor", "Максимальный делитель времени OC выбранного хэтча"),
        Map.entry("gto.hatch.effective_oc_divisor", "Эффективный делитель времени OC"),
        Map.entry("gto.hatch.base_oc_multiplier", "Базовый множитель времени стандартного OC машины"),
        Map.entry("gto.hatch.effective_oc_multiplier", "Эффективный множитель времени стандартного OC"),
        Map.entry("gto.hatch.current_max_parallel", "Текущий максимум parallel хэтча"),
        Map.entry("gto.hatch.auto_parallel", "AUTO сохраняет обнаруженный/профильный лимит parallel"),
        Map.entry("gto.hatch.row_parallel", "PAR в строке остается текущим настроенным parallel хэтча"),
        Map.entry("gto.hatch.unknown", "неизвестно"),
        Map.entry("gto.hatch.disabled", "Хэтч выключен"),
        Map.entry("gto.hatch.min_duration", "Минимальный базовый процент времени для этого тира"),
        Map.entry("gto.hatch.effective_duration", "Итоговый множитель времени"),
        Map.entry("gto.hatch.recipe_tier", "Тир рецепта"),
        Map.entry("gto.hatch.missing_tiers", "Недостающие тиры хэтча"),
        Map.entry("gto.hatch.max_threads", "Максимум потоков выбранного хэтча"),
        Map.entry("gto.hatch.effective_threads", "Эффективные потоки рецепта"),
        Map.entry("gto.hatch.requires_auxiliary", "Требует вспомогательные модули"),
        Map.entry("gto.hatch.auxiliary_required_error", "Выбранный GTO-хэтч требует вспомогательные модули"),
        Map.entry("gto.common.yes", "ДА"),
        Map.entry("gto.dynamic.energy_parallel_units", "Единицы потребления энергии"),
        Map.entry("gto.dynamic.energy_parallel_units_help", "Укажите число полных энергетических пакетов на операцию; каждый пакет добавляет один parallel"),
        Map.entry("gto.dynamic.same_recipe_robots", "Роботы одного рецепта"),
        Map.entry("gto.dynamic.same_recipe_robots_help", "Количество роботов одного рецепта; каждый добавляет обнаруженный встроенный parallel"),
        Map.entry("gto.dynamic.additional_speeding_pipes", "Дополнительные ускоряющие трубы"),
        Map.entry("gto.dynamic.additional_speeding_pipes_help", "Дополнительные ускоряющие трубы уменьшают длительность рецепта по обнаруженной формуле эффективности"),
        Map.entry("gto.dynamic.steel_frame_layers", "Слои стальных рам"),
        Map.entry("gto.dynamic.steel_frame_layers_help", "Высота структуры: каждый слой стальных рам добавляет обнаруженный базовый parallel"),
        Map.entry("gto.dynamic.current_temperature", "Текущая температура (K)"),
        Map.entry("gto.dynamic.current_temperature_help", "Текущая внутренняя температура машины для обнаруженных формул parallel и длительности"),
        Map.entry("gto.dynamic.progression_tier", "Тир прогрессии"),
        Map.entry("gto.dynamic.progression_tier_help", "Накопленный тир прогрессии машины для обнаруженных бонусов длительности и parallel"),
        Map.entry("gto.dynamic.current_neutron_flux", "Текущий нейтронный поток (keV)"),
        Map.entry("gto.dynamic.current_neutron_flux_help", "Текущий нейтронный поток; 0 оставляет расчет нейтральным до настройки потока"),
        Map.entry("gto.dynamic.required_neutron_flux", "Требуемый нейтронный поток (keV)"),
        Map.entry("gto.dynamic.required_neutron_flux_help", "Требуемый рецептом нейтронный поток; 0 оставляет расчет нейтральным до настройки"),
        Map.entry("gto.dynamic.fission_recipe_heat", "Тепловыделение рецепта"),
        Map.entry("gto.dynamic.fission_recipe_heat_help", "Тепловыделение рецепта для обнаруженной формулы потребности охлаждения; 0 оставляет формулу ненастроенной"),
        Map.entry("gto.dynamic.fission_temperature", "Текущая температура реактора"),
        Map.entry("gto.dynamic.fission_temperature_help", "Текущая температура реактора для обнаруженной формулы потребности охлаждения; 0 оставляет формулу ненастроенной"),
        Map.entry("gto.dynamic.fission_cooling_components", "Количество компонентов охлаждения"),
        Map.entry("gto.dynamic.fission_cooling_components_help", "Общее количество компонентов охлаждения для обнаруженной формулы охлаждения"),
        Map.entry("gto.dynamic.fission_adjacent_components", "Соседние компоненты охлаждения"),
        Map.entry("gto.dynamic.fission_adjacent_components_help", "Количество соседних компонентов охлаждения, вычитаемое обнаруженной формулой"),
        Map.entry("gto.dynamic.neutron_flux_low", "Текущий нейтронный поток ниже требуемого"),
        Map.entry("gto.dynamic.fission_cooling_low", "Недостаточно охлаждения для выбранного parallel"),
        Map.entry("gto.note.scanner", "GTO: общий сканер возможностей распознал эту машину"),
        Map.entry("gto.note.capabilities", "GTO авто-возможности"),
        Map.entry("gto.note.configurable", "GTO: настраиваемые механики созданы из обнаруженных возможностей"),
        Map.entry("gto.note.fixed_parallel", "GTO: обнаружен фиксированный встроенный parallel"),
        Map.entry("gto.note.time_multiplier", "GTO: обнаружен множитель времени обработки"),
        Map.entry("gto.note.oc_multiplier", "GTO: обнаружен стандартный множитель OC"),
        Map.entry("gto.common.no", "НЕТ")
    );

    @Override
    public boolean isActive() {
        return EmiForgePlatform.isModLoaded("gtocore");
    }

    @Override
    public PlannerMachineRuntimeOverride resolve(EmiStack machineStack, String machineId, List<String> tooltipLines) {
        GtoMachineCapabilities capabilities = GtoMachineCapabilityScanner.scan(machineStack, machineId, tooltipLines);
        if (!capabilities.hasAny()) {
            return null;
        }

        List<String> notes = new ArrayList<>();
        notes.add("GTO generic capability scanner matched this machine");
        if (!capabilities.summary().isBlank()) {
            notes.add("GTO auto capabilities: " + capabilities.summary());
        }
        if (capabilities.hasRuleMechanics()) {
            notes.add("GTO configurable mechanics are generated from detected capabilities");
        }
        if (capabilities.fixedParallel() > 0) {
            notes.add("GTO fixed built-in parallel detected: " + capabilities.fixedParallel());
        }
        if (capabilities.processingTimeMultiplier() > 0.0D) {
            notes.add("GTO processing-time multiplier detected: x" + capabilities.processingTimeMultiplier());
        }
        if (capabilities.standardOcDurationMultiplier() > 0.0D) {
            notes.add("GTO standard OC multiplier detected: x" + capabilities.standardOcDurationMultiplier());
        }

        Boolean perfectKnown = capabilities.enablesPerfectOc() || capabilities.disablesPerfectOc() ? Boolean.TRUE : null;
        Boolean allowsPerfect = capabilities.disablesPerfectOc() ? Boolean.FALSE
            : capabilities.enablesPerfectOc() ? Boolean.TRUE : null;
        return new PlannerMachineRuntimeOverride(
            null,
            capabilities.fixedParallel() > 0 ? capabilities.fixedParallel() : null,
            perfectKnown,
            allowsPerfect,
            capabilities.processingTimeMultiplier() > 0.0D ? capabilities.processingTimeMultiplier() : null,
            null,
            capabilities.parallelControl() ? Boolean.TRUE : null,
            null,
            capabilities.standardOcDurationMultiplier() > 0.0D ? capabilities.standardOcDurationMultiplier() : null,
            capabilities.hasRuleMechanics() ? new GtoCapabilityMachineRule(capabilities) : null,
            List.copyOf(notes)
        );
    }


    @Override
    public String russianText(String key) {
        return RU.get(key);
    }
}
