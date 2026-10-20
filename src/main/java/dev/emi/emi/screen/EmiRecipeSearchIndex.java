package dev.emi.emi.screen;

import dev.emi.emi.EmiUtil;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.registry.EmiTags;
import dev.emi.emi.runtime.EmiTagKey;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

final class EmiRecipeSearchIndex {
    private final Map<EmiRecipe, Document> cache = new IdentityHashMap<>();

    Document document(EmiRecipe recipe) {
        return cache.computeIfAbsent(recipe, Document::new);
    }

    static final class Document {
        private static final Map<String, String> MOD_NAMES = new ConcurrentHashMap<>();
        private static volatile Class<?> chemicalHelperClass;
        private static volatile boolean chemicalHelperResolved;
        private static volatile Method gtFluidTooltipMethod;
        private static volatile Object gtTooltipNormalFlag;
        private static volatile boolean gtFluidTooltipResolved;
        private static volatile boolean gtVoltageConstantsResolved;
        private static volatile String[] gtVoltageNames;
        private static volatile String[] gtVoltageNamesFormatted;
        private static volatile long[] gtVoltages;

        private final EmiRecipe recipe;
        private String inputs;
        private String outputs;
        private String any;
        private String mods;
        private String resourceIds;
        private String tags;
        private String tooltips;
        private String formulas;
        private String voltage;
        private List<EmiIngredient> inputIngredients;
        private List<EmiIngredient> outputIngredients;
        private Object gtRecipeDefinition;
        private boolean gtRecipeDefinitionResolved;

        private Document(EmiRecipe recipe) {
            this.recipe = recipe;
        }

        String inputs() {
            if (inputs == null) {
                inputs = buildSideText(true);
            }
            return inputs;
        }

        String outputs() {
            if (outputs == null) {
                outputs = buildSideText(false);
            }
            return outputs;
        }

        String any() {
            if (any == null) {
                StringBuilder builder = new StringBuilder();
                append(builder, inputs());
                append(builder, outputs());
                ResourceLocation recipeId = safeRecipeId();
                if (recipeId != null) {
                    append(builder, recipeId.toString());
                    appendMod(builder, recipeId.getNamespace());
                }
                any = normalize(builder.toString());
            }
            return any;
        }

        String mods() {
            if (mods == null) {
                StringBuilder builder = new StringBuilder();
                ResourceLocation recipeId = safeRecipeId();
                if (recipeId != null) {
                    appendMod(builder, recipeId.getNamespace());
                }
                for (EmiIngredient ingredient : ingredients(true, true)) {
                    appendIngredientMods(builder, ingredient);
                }
                for (EmiIngredient ingredient : ingredients(false, false)) {
                    appendIngredientMods(builder, ingredient);
                }
                mods = normalize(builder.toString());
            }
            return mods;
        }

        String resourceIds() {
            if (resourceIds == null) {
                StringBuilder builder = new StringBuilder();
                ResourceLocation recipeId = safeRecipeId();
                if (recipeId != null) {
                    append(builder, recipeId.toString());
                }
                for (EmiIngredient ingredient : ingredients(true, true)) {
                    appendIngredientIds(builder, ingredient);
                }
                for (EmiIngredient ingredient : ingredients(false, false)) {
                    appendIngredientIds(builder, ingredient);
                }
                resourceIds = normalize(builder.toString());
            }
            return resourceIds;
        }

        String tags() {
            if (tags == null) {
                StringBuilder builder = new StringBuilder();
                for (EmiIngredient ingredient : ingredients(true, true)) {
                    appendIngredientTags(builder, ingredient);
                }
                for (EmiIngredient ingredient : ingredients(false, false)) {
                    appendIngredientTags(builder, ingredient);
                }
                tags = normalize(builder.toString());
            }
            return tags;
        }

        String tooltips() {
            if (tooltips == null) {
                StringBuilder builder = new StringBuilder();
                for (EmiIngredient ingredient : ingredients(true, true)) {
                    appendIngredientTooltip(builder, ingredient, false);
                    appendIngredientDirectFormula(builder, ingredient);
                }
                for (EmiIngredient ingredient : ingredients(false, false)) {
                    appendIngredientTooltip(builder, ingredient, false);
                    appendIngredientDirectFormula(builder, ingredient);
                }
                appendRecipeTooltipText(builder);
                tooltips = normalize(builder.toString());
            }
            return tooltips;
        }

        String formulas() {
            if (formulas == null) {
                StringBuilder builder = new StringBuilder();
                for (EmiIngredient ingredient : ingredients(true, true)) {
                    appendIngredientFormula(builder, ingredient);
                }
                for (EmiIngredient ingredient : ingredients(false, false)) {
                    appendIngredientFormula(builder, ingredient);
                }
                formulas = normalize(builder.toString());
            }
            return formulas;
        }

        String voltage() {
            if (voltage == null) {
                voltage = buildVoltageText();
            }
            return voltage;
        }

        private String buildSideText(boolean input) {
            StringBuilder builder = new StringBuilder();
            for (EmiIngredient ingredient : ingredients(input, input)) {
                appendIngredientBase(builder, ingredient);
            }
            return normalize(builder.toString());
        }

        private List<EmiIngredient> ingredients(boolean input, boolean includeCatalysts) {
            if (input) {
                if (inputIngredients == null) {
                    List<EmiIngredient> result = new ArrayList<>();
                    try {
                        result.addAll(recipe.getInputs());
                    } catch (Throwable ignored) {
                    }
                    if (includeCatalysts) {
                        try {
                            result.addAll(recipe.getCatalysts());
                        } catch (Throwable ignored) {
                        }
                    }
                    inputIngredients = List.copyOf(result);
                }
                return inputIngredients;
            }

            if (outputIngredients == null) {
                List<EmiIngredient> result = new ArrayList<>();
                try {
                    result.addAll(recipe.getOutputs());
                } catch (Throwable ignored) {
                }
                outputIngredients = List.copyOf(result);
            }
            return outputIngredients;
        }

        private ResourceLocation safeRecipeId() {
            try {
                return recipe.getId();
            } catch (Throwable ignored) {
                return null;
            }
        }

        private void appendIngredientBase(StringBuilder builder, EmiIngredient ingredient) {
            for (EmiStack stack : safeStacks(ingredient)) {
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                try {
                    Component name = stack.getName();
                    if (name != null) {
                        append(builder, name.getString());
                    }
                } catch (Throwable ignored) {
                }
                ResourceLocation id = safeId(stack);
                if (id != null) {
                    append(builder, id.toString());
                    appendMod(builder, id.getNamespace());
                }
                long amount = safeAmount(stack);
                if (amount > 0L) {
                    append(builder, Long.toString(amount));
                    if (safeKey(stack) instanceof Fluid) {
                        append(builder, amount + "mb");
                    } else {
                        append(builder, amount + "x");
                    }
                }
            }
        }

        private void appendIngredientMods(StringBuilder builder, EmiIngredient ingredient) {
            for (EmiStack stack : safeStacks(ingredient)) {
                ResourceLocation id = safeId(stack);
                if (id != null) {
                    appendMod(builder, id.getNamespace());
                }
            }
        }

        private void appendIngredientIds(StringBuilder builder, EmiIngredient ingredient) {
            for (EmiStack stack : safeStacks(ingredient)) {
                ResourceLocation id = safeId(stack);
                if (id != null) {
                    append(builder, id.toString());
                }
            }
        }

        private void appendIngredientTags(StringBuilder builder, EmiIngredient ingredient) {
            for (EmiStack stack : safeStacks(ingredient)) {
                Object key = safeKey(stack);
                if (key == null) {
                    continue;
                }
                for (EmiTagKey<?> tag : EmiTags.TAGS) {
                    try {
                        if (tag.getList().contains(key)) {
                            append(builder, tag.id().toString());
                            append(builder, tag.id().getPath());
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        }

        private void appendRecipeTooltipText(StringBuilder builder) {
            Object definition = gtRecipeDefinition();
            if (definition == null) {
                return;
            }

            Object conditions = readField(definition, "conditions");
            if (conditions instanceof Object[] array) {
                for (Object condition : array) {
                    appendTextValue(builder, invokeNoArgs(condition, "getTooltips"));
                }
            } else if (conditions instanceof Iterable<?> iterable) {
                for (Object condition : iterable) {
                    appendTextValue(builder, invokeNoArgs(condition, "getTooltips"));
                }
            }

            Object recipeType = readField(definition, "recipeType");
            Object dataInfos = invokeNoArgs(recipeType, "getDataInfos");
            if (dataInfos instanceof Iterable<?> iterable) {
                for (Object value : iterable) {
                    if (!(value instanceof Function<?, ?> function)) {
                        continue;
                    }
                    try {
                        @SuppressWarnings("unchecked")
                        Function<Object, Object> typed = (Function<Object, Object>) function;
                        appendTextValue(builder, typed.apply(definition));
                    } catch (Throwable ignored) {
                    }
                }
            }

            append(builder, voltage());
        }

        private String buildVoltageText() {
            Object definition = gtRecipeDefinition();
            if (definition == null) {
                return "";
            }

            StringBuilder builder = new StringBuilder();
            long inputEu = numberAsLong(invokeNoArgs(definition, "getInputEUt"));
            long outputEu = numberAsLong(invokeNoArgs(definition, "getOutputEUt"));
            long eu = inputEu != 0L ? inputEu : outputEu;
            if (eu == 0L) {
                eu = numberAsLong(readField(definition, "eut"));
            }

            if (eu != 0L) {
                long absolute = eu == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(eu);
                append(builder, Long.toString(absolute));
                append(builder, absolute + " EU/t");
                append(builder, absolute + "EU/t");
                append(builder, absolute + " EUt");
                append(builder, absolute + "EUt");
                append(builder, "usage " + absolute);
            }

            int tier = numberAsInt(readField(definition, "tier"), -1);
            if (tier >= 0) {
                append(builder, Integer.toString(tier));
                append(builder, "tier " + tier);
                resolveGtVoltageConstants();
                appendArrayEntry(builder, gtVoltageNames, tier);
                appendArrayEntry(builder, gtVoltageNamesFormatted, tier);
                if (gtVoltages != null && tier < gtVoltages.length) {
                    long nominal = gtVoltages[tier];
                    append(builder, Long.toString(nominal));
                    append(builder, nominal + " V");
                    append(builder, nominal + "V");
                    append(builder, "voltage " + nominal);
                }
            }

            return normalize(builder.toString());
        }

        private Object gtRecipeDefinition() {
            if (gtRecipeDefinitionResolved) {
                return gtRecipeDefinition;
            }
            gtRecipeDefinitionResolved = true;

            Object value = readField(recipe, "recipe");
            if (value == null) {
                value = invokeNoArgs(recipe, "getRecipe");
            }
            if (value == null) {
                value = invokeNoArgs(recipe, "getRecipeDefinition");
            }
            if (value != null && value.getClass().getName().endsWith("GTRecipeDefinition")) {
                gtRecipeDefinition = value;
            }
            return gtRecipeDefinition;
        }

        private static void appendTextValue(StringBuilder builder, Object value) {
            if (value == null) {
                return;
            }
            if (value instanceof Component text) {
                appendTooltipText(builder, text.getString(), false);
                return;
            }
            if (value instanceof CharSequence sequence) {
                appendTooltipText(builder, sequence.toString(), false);
                return;
            }
            if (value instanceof Iterable<?> iterable) {
                for (Object element : iterable) {
                    appendTextValue(builder, element);
                }
                return;
            }
            appendTooltipText(builder, componentOrString(value), false);
        }

        private static long numberAsLong(Object value) {
            return value instanceof Number number ? number.longValue() : 0L;
        }

        private static int numberAsInt(Object value, int fallback) {
            return value instanceof Number number ? number.intValue() : fallback;
        }

        private static void appendArrayEntry(StringBuilder builder, String[] values, int index) {
            if (values == null || index < 0 || index >= values.length) {
                return;
            }
            append(builder, stripFormatting(values[index]));
        }

        private static void resolveGtVoltageConstants() {
            if (gtVoltageConstantsResolved) {
                return;
            }
            synchronized (Document.class) {
                if (gtVoltageConstantsResolved) {
                    return;
                }
                try {
                    Class<?> values = Class.forName("com.gregtechceu.gtceu.api.GTValues", false, Document.class.getClassLoader());
                    Object vn = readStaticField(values, "VN");
                    if (vn instanceof String[] array) {
                        gtVoltageNames = array;
                    }
                    Object vnf = readStaticField(values, "VNF");
                    if (vnf instanceof String[] array) {
                        gtVoltageNamesFormatted = array;
                    }
                    Object volts = readStaticField(values, "V");
                    if (volts instanceof long[] array) {
                        gtVoltages = array;
                    }
                } catch (Throwable ignored) {
                    gtVoltageNames = null;
                    gtVoltageNamesFormatted = null;
                    gtVoltages = null;
                }
                gtVoltageConstantsResolved = true;
            }
        }

        private static Object readStaticField(Class<?> type, String name) {
            if (type == null) {
                return null;
            }
            try {
                Field field = type.getField(name);
                field.setAccessible(true);
                return field.get(null);
            } catch (Throwable ignored) {
            }
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(null);
            } catch (Throwable ignored) {
                return null;
            }
        }

        private void appendIngredientTooltip(StringBuilder builder, EmiIngredient ingredient, boolean formulaOnly) {
            for (EmiStack stack : safeStacks(ingredient)) {
                try {
                    for (Component line : stack.getTooltipText()) {
                        appendTooltipLine(builder, line, formulaOnly);
                    }
                } catch (Throwable ignored) {
                }
                appendGtFluidTooltip(builder, stack, formulaOnly);
            }
        }

        private void appendIngredientFormula(StringBuilder builder, EmiIngredient ingredient) {
            boolean foundDirect = appendIngredientDirectFormula(builder, ingredient);
            if (!foundDirect) {
                appendIngredientTooltip(builder, ingredient, true);
            }
        }

        private boolean appendIngredientDirectFormula(StringBuilder builder, EmiIngredient ingredient) {
            boolean found = false;
            for (EmiStack stack : safeStacks(ingredient)) {
                Object key = safeKey(stack);
                String keyFormula = tryGtFormula(key);
                if (!keyFormula.isBlank()) {
                    append(builder, keyFormula);
                    found = true;
                }

                ItemStack itemStack = safeItemStack(stack);
                if (!itemStack.isEmpty()) {
                    String itemFormula = tryGtFormula(itemStack);
                    if (!itemFormula.isBlank()) {
                        append(builder, itemFormula);
                        found = true;
                    }
                }

                Object fluidStack = forgeFluidStack(key, safeAmount(stack));
                String fluidFormula = tryGtFormula(fluidStack);
                if (!fluidFormula.isBlank()) {
                    append(builder, fluidFormula);
                    found = true;
                }
            }
            return found;
        }

        private static void appendGtFluidTooltip(StringBuilder builder, EmiStack stack, boolean formulaOnly) {
            Object fluidStack = forgeFluidStack(safeKey(stack), safeAmount(stack));
            if (fluidStack == null) {
                return;
            }
            Method method = gtFluidTooltipMethod();
            if (method == null || gtTooltipNormalFlag == null) {
                return;
            }
            Consumer<Object> consumer = value -> appendTextValueFiltered(builder, value, formulaOnly);
            try {
                method.invoke(null, fluidStack, consumer, gtTooltipNormalFlag);
            } catch (Throwable ignored) {
            }
        }

        private static Method gtFluidTooltipMethod() {
            if (gtFluidTooltipResolved) {
                return gtFluidTooltipMethod;
            }
            synchronized (Document.class) {
                if (!gtFluidTooltipResolved) {
                    try {
                        Class<?> type = Class.forName("com.gregtechceu.gtceu.client.TooltipsHandler", false, Document.class.getClassLoader());
                        Class<?> fluidStack = FluidStack.class;
                        for (Method method : type.getDeclaredMethods()) {
                            if (!Modifier.isStatic(method.getModifiers()) || !"appendFluidTooltips".equals(method.getName())) {
                                continue;
                            }
                            Class<?>[] parameters = method.getParameterTypes();
                            if (parameters.length == 3 && parameters[0].isAssignableFrom(fluidStack)
                                    && Consumer.class.isAssignableFrom(parameters[1])) {
                                method.setAccessible(true);
                                gtFluidTooltipMethod = method;
                                gtTooltipNormalFlag = normalTooltipFlag(parameters[2]);
                                break;
                            }
                        }
                    } catch (Throwable ignored) {
                        gtFluidTooltipMethod = null;
                        gtTooltipNormalFlag = null;
                    }
                    gtFluidTooltipResolved = true;
                }
            }
            return gtFluidTooltipMethod;
        }

        private static Object normalTooltipFlag(Class<?> type) {
            if (type == null) {
                return null;
            }
            if (type.isEnum()) {
                for (Object value : type.getEnumConstants()) {
                    if (value instanceof Enum<?> e && "NORMAL".equals(e.name())) {
                        return value;
                    }
                }
            }
            Object value = readStaticField(type, "NORMAL");
            if (value != null) {
                return value;
            }
            for (Class<?> nested : type.getDeclaredClasses()) {
                value = readStaticField(nested, "NORMAL");
                if (value != null && type.isInstance(value)) {
                    return value;
                }
            }
            return null;
        }

        private static void appendTextValueFiltered(StringBuilder builder, Object value, boolean formulaOnly) {
            if (value instanceof Component text) {
                appendTooltipText(builder, text.getString(), formulaOnly);
            } else if (value != null) {
                appendTooltipText(builder, componentOrString(value), formulaOnly);
            }
        }

        private static void appendTooltipLine(StringBuilder builder, Component text, boolean formulaOnly) {
            if (text != null) {
                appendTooltipText(builder, text.getString(), formulaOnly);
            }
        }

        private static void appendTooltipText(StringBuilder builder, String value, boolean formulaOnly) {
            String text = stripFormatting(value);
            if (text.isBlank()) {
                return;
            }
            if (!formulaOnly || looksLikeFormula(text)) {
                append(builder, text);
            }
        }

        private static String tryGtFormula(Object value) {
            try {
                Class<?> helper = chemicalHelper();
                if (helper == null || value == null) {
                    return "";
                }

                String direct = extractFormula(value);
                if (!direct.isBlank()) {
                    return direct;
                }

                String[] lookupNames = {"getMaterial", "getMaterialStack"};
                for (String lookupName : lookupNames) {
                    Object materialStack = invokeCompatibleStatic(helper, lookupName, value);
                    String formula = extractFormula(materialStack);
                    if (!formula.isBlank()) {
                        return formula;
                    }
                }
            } catch (Throwable ignored) {
            }
            return "";
        }

        private static String extractFormula(Object value) {
            if (value == null) {
                return "";
            }
            Object isNull = invokeNoArgs(value, "isNull");
            if (Boolean.TRUE.equals(isNull)) {
                return "";
            }
            Object formula = invokeNoArgs(value, "getChemicalFormula");
            if (formula == null) {
                formula = invokeNoArgs(value, "getFormula");
            }
            String formulaText = componentOrString(formula);
            if (!formulaText.isBlank()) {
                return formulaText;
            }
            Object material = invokeNoArgs(value, "material");
            if (material == null) {
                material = invokeNoArgs(value, "getMaterial");
            }
            if (material == null) {
                material = readField(value, "material");
            }
            if (material == null || material == value) {
                return "";
            }
            formula = invokeNoArgs(material, "getChemicalFormula");
            if (formula == null) {
                formula = invokeNoArgs(material, "getFormula");
            }
            return componentOrString(formula);
        }

        private static String componentOrString(Object value) {
            if (value == null) {
                return "";
            }
            if (value instanceof Component text) {
                return text.getString();
            }
            Object string = invokeNoArgs(value, "getString");
            if (string instanceof String text) {
                return text;
            }
            return String.valueOf(value);
        }

        private static Class<?> chemicalHelper() {
            if (chemicalHelperResolved) {
                return chemicalHelperClass;
            }
            synchronized (Document.class) {
                if (!chemicalHelperResolved) {
                    try {
                        chemicalHelperClass = Class.forName("com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper", false,
                            Document.class.getClassLoader());
                    } catch (Throwable ignored) {
                        chemicalHelperClass = null;
                    }
                    chemicalHelperResolved = true;
                }
            }
            return chemicalHelperClass;
        }

        private static Object invokeCompatibleStatic(Class<?> type, String name, Object argument) {
            for (Method method : type.getMethods()) {
                if (!Modifier.isStatic(method.getModifiers()) || !name.equals(method.getName())) {
                    continue;
                }
                Class<?>[] parameters = method.getParameterTypes();
                if (parameters.length != 1 || !parameters[0].isInstance(argument)) {
                    continue;
                }
                try {
                    return method.invoke(null, argument);
                } catch (Throwable ignored) {
                }
            }
            return null;
        }

        private static Object readField(Object target, String name) {
            if (target == null) {
                return null;
            }
            for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
                try {
                    Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (Throwable ignored) {
                }
            }
            return null;
        }

        private static boolean looksLikeFormula(String value) {
            String text = RecipeFilterText.normalizeChemicalGlyphs(value == null ? "" : value.trim());
            if (text.length() < 2 || text.length() > 128 || text.indexOf(' ') >= 0) {
                return false;
            }
            boolean upper = false;
            boolean structure = false;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (Character.isUpperCase(c)) {
                    upper = true;
                }
                if (Character.isDigit(c) || c == '(' || c == ')' || c == '[' || c == ']') {
                    structure = true;
                }
            }
            return upper && structure;
        }

        private static void appendMod(StringBuilder builder, String namespace) {
            if (namespace == null || namespace.isBlank()) {
                return;
            }
            append(builder, namespace);
            String display = MOD_NAMES.computeIfAbsent(namespace, id -> {
                try {
                    return EmiUtil.getModName(id);
                } catch (Throwable ignored) {
                    return id;
                }
            });
            append(builder, display);
        }

        private static List<EmiStack> safeStacks(EmiIngredient ingredient) {
            if (ingredient == null) {
                return List.of();
            }
            try {
                List<EmiStack> stacks = ingredient.getEmiStacks();
                return stacks == null ? List.of() : stacks;
            } catch (Throwable ignored) {
                return List.of();
            }
        }

        private static ItemStack safeItemStack(EmiStack stack) {
            if (stack == null) {
                return ItemStack.EMPTY;
            }
            try {
                ItemStack value = stack.getItemStack();
                return value == null ? ItemStack.EMPTY : value;
            } catch (Throwable ignored) {
                return ItemStack.EMPTY;
            }
        }

        private static ResourceLocation safeId(EmiStack stack) {
            try {
                return stack == null || stack.isEmpty() ? null : stack.getId();
            } catch (Throwable ignored) {
                return null;
            }
        }

        private static Object safeKey(EmiStack stack) {
            try {
                return stack == null || stack.isEmpty() ? null : stack.getKey();
            } catch (Throwable ignored) {
                return null;
            }
        }

        private static long safeAmount(EmiStack stack) {
            try {
                return stack == null ? 0L : stack.getAmount();
            } catch (Throwable ignored) {
                return 0L;
            }
        }

        private static FluidStack forgeFluidStack(Object key, long amount) {
            if (!(key instanceof Fluid fluid)) {
                return null;
            }
            int count = amount <= 0L ? 1000 : (int) Math.min(Integer.MAX_VALUE, amount);
            return new FluidStack(fluid, count);
        }

        private static Object invokeNoArgs(Object target, String name) {
            if (target == null) {
                return null;
            }
            for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
                try {
                    Method method = type.getDeclaredMethod(name);
                    method.setAccessible(true);
                    return method.invoke(target);
                } catch (NoSuchMethodException ignored) {
                } catch (Throwable ignored) {
                    return null;
                }
            }
            return null;
        }

        private static String stripFormatting(String value) {
            if (value == null || value.isEmpty()) {
                return "";
            }
            StringBuilder result = new StringBuilder(value.length());
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c == '\u00A7' && i + 1 < value.length()) {
                    i++;
                    continue;
                }
                result.append(c);
            }
            return result.toString();
        }

        private static void append(StringBuilder builder, String value) {
            if (value == null || value.isBlank()) {
                return;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(value);
        }

        private static String normalize(String value) {
            return RecipeFilterText.normalize(value);
        }
    }
}
