package dev.emi.emi.runtime;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.forge.EmiForgePlatform;
import dev.emi.emi.runtime.EmiLog;
import dev.emi.emi.runtime.EmiPersistentData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.level.material.Fluid;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

public final class EmiCollapsibleSidebarGroups {
    private static final Set<String> expanded = new HashSet<>();
    private static final IdentityHashMap<List<?>, Cache> caches = new IdentityHashMap<>();
    private static final Gson STATE_GSON = new Gson().newBuilder().setPrettyPrinting().create();
    private static final File STATE_FILE = new File(EmiForgePlatform.getConfigDirectory().toFile(), "emi-sidebar-groups.json");
    private static boolean gtReflectionReady;
    private static Method gtGetPrefix;
    private static Method gtPrefixId;
    private static Object gtNullPrefix;
    private static final String[][] GT_MATERIAL_SUFFIXES = {
            { "_ingot", "ingot" },
            { "_dust", "dust" },
            { "_nugget", "nugget" },
            { "_plate", "plate" },
            { "_foil", "foil" },
            { "_rod", "rod" },
            { "_bolt", "bolt" },
            { "_screw", "screw" },
            { "_round", "round" },
            { "_ring", "ring" },
            { "_spring", "spring" },
            { "_gear", "gear" },
            { "_rotor", "rotor" },
            { "_lens", "lens" },
            { "_gem", "gem" },
            { "_bucket", "fluidbucket" },
            { "_indicator", "surfacerock" },
            { "_turbine_blade", "turbineblade" },
            { "_drill_head", "drillhead" },
            { "_frame", "frame" }
    };
    private static final String[][] GT_TOOL_SUFFIXES = {
            { "_butchery_knife", "butcheryknife" },
            { "_wire_cutter", "wirecutter" },
            { "_soft_mallet", "softmallet" },
            { "_mining_hammer", "mininghammer" },
            { "_screwdriver", "screwdriver" },
            { "_crowbar", "crowbar" },
            { "_wrench", "wrench" },
            { "_hammer", "hammer" },
            { "_mortar", "mortar" },
            { "_plunger", "plunger" },
            { "_scythe", "scythe" },
            { "_pickaxe", "pickaxe" },
            { "_shovel", "shovel" },
            { "_sword", "sword" },
            { "_knife", "knife" },
            { "_file", "file" },
            { "_saw", "saw" },
            { "_axe", "axe" },
            { "_hoe", "hoe" }
    };
    private static final String[][] GENERAL_TOOL_SUFFIXES = {
            { "_pickaxe", "pickaxe" },
            { "_shovel", "shovel" },
            { "_axe", "axe" },
            { "_hoe", "hoe" }
    };
    private static final String[][] GT_WIRE_FORMS = {
            { "single", "wire1x" },
            { "double", "wire2x" },
            { "quadruple", "wire4x" },
            { "quad", "wire4x" },
            { "octal", "wire8x" },
            { "oct", "wire8x" },
            { "hex", "wire16x" },
            { "hexadecuple", "wire16x" }
    };
    private static final String[][] GT_CABLE_FORMS = {
            { "single", "cable1x" },
            { "double", "cable2x" },
            { "quadruple", "cable4x" },
            { "quad", "cable4x" },
            { "octal", "cable8x" },
            { "oct", "cable8x" },
            { "hex", "cable16x" },
            { "hexadecuple", "cable16x" }
    };
    private static final String[] GT_PIPE_SIZES = {
            "tiny", "small", "normal", "large", "huge", "quadruple", "nonuple"
    };
    private static final String[][] ARMOR_SUFFIXES = {
            { "_helmet", "helmet" },
            { "_chestplate", "chestplate" },
            { "_leggings", "leggings" },
            { "_boots", "boots" }
    };
    private static final String[][] WEAPON_SUFFIXES = {
            { "_crossbow", "crossbow" },
            { "_butchery_knife", "knife" },
            { "_revolver", "revolver" },
            { "_shotgun", "shotgun" },
            { "_launcher", "launcher" },
            { "_blaster", "blaster" },
            { "_pistol", "pistol" },
            { "_rifle", "rifle" },
            { "_trident", "trident" },
            { "_katana", "katana" },
            { "_dagger", "dagger" },
            { "_spear", "spear" },
            { "_sword", "sword" },
            { "_knife", "knife" },
            { "_mace", "mace" },
            { "_staff", "staff" },
            { "_gun", "gun" },
            { "_bow", "bow" }
    };

    static {
        loadStateFile();
    }

    private EmiCollapsibleSidebarGroups() {
    }

    public static List<? extends EmiIngredient> visible(List<? extends EmiIngredient> source) {
        if (!EmiConfig.sidebarGroupingEnabled) {
            return source;
        }
        return cache(source).visible;
    }

    public static Group group(List<? extends EmiIngredient> source, EmiIngredient ingredient) {
        if (!EmiConfig.sidebarGroupingEnabled) {
            return null;
        }
        return cache(source).groupsByIngredient.get(ingredient);
    }

    public static boolean toggle(List<? extends EmiIngredient> source, EmiIngredient ingredient) {
        if (!EmiConfig.sidebarGroupingEnabled) {
            return false;
        }
        Group group = group(source, ingredient);
        if (group == null) {
            return false;
        }
        if (!expanded.add(group.key)) {
            expanded.remove(group.key);
        }
        caches.clear();
        saveStateFile();
        EmiPersistentData.save();
        return true;
    }

    public static JsonObject save() {
        JsonObject json = new JsonObject();
        JsonArray array = new JsonArray();
        expanded.stream().sorted().forEach(array::add);
        json.add("expanded", array);
        return json;
    }

    public static void load(JsonObject json) {
        if (loadStateFile()) {
            return;
        }
        applyState(json);
        saveStateFile();
    }

    private static void applyState(JsonObject json) {
        expanded.clear();
        JsonElement element = json.get("expanded");
        if (element != null && element.isJsonArray()) {
            for (JsonElement entry : element.getAsJsonArray()) {
                if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
                    expanded.add(entry.getAsString());
                }
            }
        }
        caches.clear();
    }

    private static boolean loadStateFile() {
        if (!STATE_FILE.exists()) {
            return false;
        }
        try (FileReader reader = new FileReader(STATE_FILE)) {
            JsonObject json = STATE_GSON.fromJson(reader, JsonObject.class);
            if (json != null) {
                applyState(json);
                return true;
            }
        } catch (Exception e) {
            EmiLog.error("Failed to load sidebar grouping state", e);
        }
        return false;
    }

    private static void saveStateFile() {
        try {
            File parent = STATE_FILE.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            try (FileWriter writer = new FileWriter(STATE_FILE)) {
                STATE_GSON.toJson(save(), writer);
            }
        } catch (Exception e) {
            EmiLog.error("Failed to save sidebar grouping state", e);
        }
    }

    public static boolean isExpanded(Group group) {
        return group != null && expanded.contains(group.key);
    }

    private static Cache cache(List<? extends EmiIngredient> source) {
        int settingsHash = settingsHash();
        Cache cache = caches.get(source);
        if (cache != null && cache.size == source.size() && cache.settingsHash == settingsHash) {
            return cache;
        }
        if (caches.size() > 12) {
            caches.clear();
        }
        cache = build(source, settingsHash);
        caches.put(source, cache);
        return cache;
    }

    private static Cache build(List<? extends EmiIngredient> source, int settingsHash) {
        Settings settings = settings();
        List<String> keys = new ArrayList<>(source.size());
        Map<String, List<EmiIngredient>> members = new LinkedHashMap<>();
        for (EmiIngredient ingredient : source) {
            String key = key(ingredient, settings);
            keys.add(key);
            if (key != null) {
                members.computeIfAbsent(key, k -> new ArrayList<>()).add(ingredient);
            }
        }
        members.entrySet().removeIf(entry -> entry.getValue().size() < 2);
        Map<String, Group> groups = new HashMap<>();
        for (Map.Entry<String, List<EmiIngredient>> entry : members.entrySet()) {
            groups.put(entry.getKey(), new Group(entry.getKey(), List.copyOf(entry.getValue())));
        }
        IdentityHashMap<EmiIngredient, Group> groupsByIngredient = new IdentityHashMap<>();
        for (Group group : groups.values()) {
            for (EmiIngredient ingredient : group.members()) {
                groupsByIngredient.put(ingredient, group);
            }
        }
        List<EmiIngredient> visible = new ArrayList<>(source.size());
        Set<String> emitted = new HashSet<>();
        for (int i = 0; i < source.size(); i++) {
            EmiIngredient ingredient = source.get(i);
            String key = keys.get(i);
            Group group = key == null ? null : groups.get(key);
            if (group == null) {
                visible.add(ingredient);
                continue;
            }
            if (!emitted.add(key)) {
                continue;
            }
            List<EmiIngredient> ordered = orderedMembers(group, settings);
            if (expanded.contains(key)) {
                visible.addAll(ordered);
            } else {
                visible.add(ordered.get(0));
            }
        }
        return new Cache(source.size(), settingsHash, List.copyOf(visible), groupsByIngredient);
    }

    private static List<EmiIngredient> orderedMembers(Group group, Settings settings) {
        if (!group.key.startsWith("tier:")) {
            return group.members();
        }
        List<EmiIngredient> ordered = new ArrayList<>(group.members());
        ordered.sort(Comparator.comparingInt((EmiIngredient ingredient) -> tierOrder(ingredient, settings.tierPrefixes)));
        return ordered;
    }

    private static int tierOrderAnywhere(EmiIngredient ingredient, List<String> prefixes) {
        List<EmiStack> stacks = ingredient.getEmiStacks();
        if (stacks.size() != 1) {
            return Integer.MAX_VALUE;
        }
        ResourceLocation id = stacks.get(0).getId();
        if (id == null) {
            return Integer.MAX_VALUE;
        }
        return tierIndex(id.getPath().toLowerCase(Locale.ROOT), prefixes);
    }

    private static int tierIndex(String path, List<String> prefixes) {
        for (int i = 0; i < prefixes.size(); i++) {
            String prefix = prefixes.get(i);
            if (path.equals(prefix) || path.startsWith(prefix + "_") || path.endsWith("_" + prefix)
                    || path.contains("_" + prefix + "_")) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    private static int tierOrder(EmiIngredient ingredient, List<String> prefixes) {
        List<EmiStack> stacks = ingredient.getEmiStacks();
        if (stacks.size() != 1) {
            return Integer.MAX_VALUE;
        }
        ResourceLocation id = stacks.get(0).getId();
        if (id == null) {
            return Integer.MAX_VALUE;
        }
        String path = id.getPath().toLowerCase(Locale.ROOT);
        for (int i = 0; i < prefixes.size(); i++) {
            String prefix = prefixes.get(i) + "_";
            if (path.startsWith(prefix)) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    private static String key(EmiIngredient ingredient, Settings settings) {
        List<EmiStack> stacks = ingredient.getEmiStacks();
        if (stacks.size() != 1) {
            return null;
        }
        EmiStack stack = stacks.get(0);
        ResourceLocation id = stack.getId();
        if (id == null) {
            return null;
        }
        String namespace = id.getNamespace().toLowerCase(Locale.ROOT);
        String path = id.getPath().toLowerCase(Locale.ROOT);
        String fullId = namespace + ":" + path;
        if (matchesAny(settings.blacklist, fullId, path)) {
            return null;
        }
        if (EmiConfig.sidebarGroupingCustomRulesEnabled) {
            for (String rule : settings.customRules) {
                if (matches(rule, fullId, path)) {
                    return "custom:" + rule;
                }
            }
        }
        boolean gtNamespace = namespace.equals("gtceu") || namespace.equals("gtocore");
        if (EmiConfig.sidebarGroupingGtFluids && gtNamespace && stack.getKey() instanceof Fluid) {
            return "gt-prefix:fluid";
        }
        ItemStack itemStack = stack.getItemStack();
        if (itemStack.isEmpty()) {
            return null;
        }
        int circuitTier = circuitTier(itemStack, namespace, path, settings.tierPrefixes);
        if (circuitTier != Integer.MAX_VALUE) {
            if (!EmiConfig.sidebarGroupingCircuits) {
                return null;
            }
            return "gt-circuit:" + settings.tierPrefixes.get(circuitTier);
        }
        boolean spawnEgg = itemStack.getItem() instanceof SpawnEggItem || path.endsWith("_spawn_egg");
        if (spawnEgg) {
            return EmiConfig.sidebarGroupingSpawnEggs ? "equipment:spawn_egg" : null;
        }
        String tool = vanillaToolCategory(itemStack.getItem());
        if (tool == null) {
            tool = suffixCategory(path, GENERAL_TOOL_SUFFIXES);
        }
        if (tool != null) {
            return EmiConfig.sidebarGroupingTools ? "equipment:tool:" + tool : null;
        }
        String armor = suffixCategory(path, ARMOR_SUFFIXES);
        if (armor != null || itemStack.getItem() instanceof ArmorItem) {
            return EmiConfig.sidebarGroupingArmor ? "equipment:armor:" + (armor == null ? "armor" : armor) : null;
        }
        String weapon = vanillaWeaponCategory(itemStack.getItem());
        if (weapon == null) {
            weapon = suffixCategory(path, WEAPON_SUFFIXES);
        }
        if (weapon != null) {
            return EmiConfig.sidebarGroupingWeapons ? "equipment:weapon:" + weapon : null;
        }
        String chisel = chiselCategory(namespace, path);
        if (chisel != null) {
            return EmiConfig.sidebarGroupingChiselBlocks ? "chisel:" + chisel : null;
        }
        String gtPrefix = gtPrefix(itemStack, namespace, path);
        if (gtPrefix != null) {
            return gtPrefixEnabled(gtPrefix) ? "gt-prefix:" + gtPrefix : null;
        }
        if (EmiConfig.sidebarGroupingTierRules) {
            String stripped = stripPrefix(path, settings.tierPrefixes);
            if (stripped != null) {
                String tierNamespace = gtNamespace ? "gt" : namespace;
                return "tier:" + tierNamespace + ":" + stripped;
            }
        }
        if (EmiConfig.sidebarGroupingColorRules) {
            String stripped = stripPrefix(path, settings.colorPrefixes);
            if (stripped != null) {
                return "color:" + namespace + ":" + stripped;
            }
        }
        if (EmiConfig.sidebarGroupingWoodRules) {
            String stripped = stripPrefix(path, settings.woodPrefixes);
            if (stripped != null) {
                return "wood:" + namespace + ":" + stripped;
            }
        }
        if (EmiConfig.sidebarGroupingSameItemId) {
            return "id:" + id;
        }
        return null;
    }

    private static int circuitTier(ItemStack stack, String namespace, String path, List<String> prefixes) {
        int taggedTier = circuitTier(stack, prefixes);
        if (taggedTier != Integer.MAX_VALUE) {
            return taggedTier;
        }
        if (!namespace.equals("gtceu") && !namespace.equals("gtocore")) {
            return Integer.MAX_VALUE;
        }
        if (!path.contains("circuit")) {
            return Integer.MAX_VALUE;
        }
        if (path.contains("assembler") || path.contains("board") || path.contains("breaker")
                || path.contains("pattern")) {
            return Integer.MAX_VALUE;
        }
        return tierIndex(path, prefixes);
    }

    private static int circuitTier(ItemStack stack, List<String> prefixes) {
        for (TagKey<Item> tag : stack.getTags().toList()) {
            String path = tag.location().getPath().toLowerCase(Locale.ROOT);
            if (!path.startsWith("circuits/")) {
                continue;
            }
            String tier = path.substring("circuits/".length());
            for (int i = 0; i < prefixes.size(); i++) {
                if (tier.equals(prefixes.get(i))) {
                    return i;
                }
            }
        }
        return Integer.MAX_VALUE;
    }

    private static String vanillaToolCategory(Item item) {
        if (item instanceof PickaxeItem) {
            return "pickaxe";
        }
        if (item instanceof AxeItem) {
            return "axe";
        }
        if (item instanceof ShovelItem) {
            return "shovel";
        }
        if (item instanceof HoeItem) {
            return "hoe";
        }
        return null;
    }

    private static String vanillaWeaponCategory(Item item) {
        if (item instanceof SwordItem) {
            return "sword";
        }
        if (item instanceof BowItem) {
            return "bow";
        }
        if (item instanceof CrossbowItem) {
            return "crossbow";
        }
        return null;
    }

    private static String chiselCategory(String namespace, String path) {
        if (!namespace.equals("chisel") && !namespace.equals("rechiseled")) {
            return null;
        }
        int slash = path.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < path.length()) {
            return path.substring(slash + 1);
        }
        return null;
    }

    private static boolean gtPrefixEnabled(String prefix) {
        if (prefix.startsWith("wire") || prefix.equals("finewire")) {
            return EmiConfig.sidebarGroupingGtWires;
        }
        if (prefix.startsWith("cable")) {
            return EmiConfig.sidebarGroupingGtCables;
        }
        if (prefix.startsWith("fluidpipe") || prefix.startsWith("itempipe")) {
            return EmiConfig.sidebarGroupingGtPipes;
        }
        if (prefix.equals("fluidbucket")) {
            return EmiConfig.sidebarGroupingGtFluidBuckets;
        }
        if (prefix.equals("surfacerock")) {
            return EmiConfig.sidebarGroupingGtSurfaceRocks;
        }
        if (prefix.equals("turbineblade")) {
            return EmiConfig.sidebarGroupingGtTurbineBlades;
        }
        if (prefix.equals("drillhead")) {
            return EmiConfig.sidebarGroupingGtDrillHeads;
        }
        if (prefix.equals("frame")) {
            return EmiConfig.sidebarGroupingGtFrames;
        }
        if (prefix.equals("coilblock")) {
            return EmiConfig.sidebarGroupingGtCoilBlocks;
        }
        if (prefix.equals("toolsword") || prefix.equals("toolknife") || prefix.equals("toolbutcheryknife")) {
            return EmiConfig.sidebarGroupingWeapons;
        }
        if (prefix.startsWith("tool")) {
            return EmiConfig.sidebarGroupingTools;
        }
        return EmiConfig.sidebarGroupingGtTagPrefix;
    }

    private static String suffixCategory(String path, String[][] suffixes) {
        for (String[] suffix : suffixes) {
            String token = suffix[0];
            if ((path.endsWith(token) && path.length() > token.length())
                    || (token.startsWith("_") && path.equals(token.substring(1)))) {
                return suffix[1];
            }
        }
        return null;
    }

    private static String stripPrefix(String path, List<String> prefixes) {
        for (String prefix : prefixes) {
            String start = prefix + "_";
            if (path.startsWith(start) && path.length() > start.length()) {
                return path.substring(start.length());
            }
        }
        return null;
    }

    private static boolean matchesAny(List<String> patterns, String fullId, String path) {
        for (String pattern : patterns) {
            if (matches(pattern, fullId, path)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(String pattern, String fullId, String path) {
        String target = pattern.indexOf(':') >= 0 ? fullId : path;
        return glob(pattern, target);
    }

    private static boolean glob(String pattern, String value) {
        int p = 0;
        int v = 0;
        int star = -1;
        int retry = -1;
        while (v < value.length()) {
            if (p < pattern.length() && pattern.charAt(p) == value.charAt(v)) {
                p++;
                v++;
            } else if (p < pattern.length() && pattern.charAt(p) == '*') {
                star = p++;
                retry = v;
            } else if (star != -1) {
                p = star + 1;
                v = ++retry;
            } else {
                return false;
            }
        }
        while (p < pattern.length() && pattern.charAt(p) == '*') {
            p++;
        }
        return p == pattern.length();
    }

    private static Settings settings() {
        return new Settings(
                split(EmiConfig.sidebarGroupingTierPrefixes),
                split(EmiConfig.sidebarGroupingColorPrefixes),
                split(EmiConfig.sidebarGroupingWoodPrefixes),
                split(EmiConfig.sidebarGroupingCustomRules),
                split(EmiConfig.sidebarGroupingBlacklist));
    }

    private static List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String entry : value.split(",")) {
            String normalized = entry.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }
        return List.copyOf(result);
    }

    private static int settingsHash() {
        int result = Boolean.hashCode(EmiConfig.sidebarGroupingEnabled);
        result = 31 * result + Boolean.hashCode(EmiConfig.sidebarGroupingGtTagPrefix);
        result = 31 * result + Boolean.hashCode(EmiConfig.sidebarGroupingGtWires);
        result = 31 * result + Boolean.hashCode(EmiConfig.sidebarGroupingGtCables);
        result = 31 * result + Boolean.hashCode(EmiConfig.sidebarGroupingGtPipes);
        result = 31 * result + Boolean.hashCode(EmiConfig.sidebarGroupingGtFluidBuckets);
        result = 31 * result + Boolean.hashCode(EmiConfig.sidebarGroupingGtFluids);
        result = 31 * result + Boolean.hashCode(EmiConfig.sidebarGroupingGtSurfaceRocks);
        result = 31 * result + Boolean.hashCode(EmiConfig.sidebarGroupingGtTurbineBlades);
        result = 31 * result + Boolean.hashCode(EmiConfig.sidebarGroupingGtDrillHeads);
        result = 31 * result + Boolean.hashCode(EmiConfig.sidebarGroupingGtFrames);
        result = 31 * result + Boolean.hashCode(EmiConfig.sidebarGroupingGtCoilBlocks);
        result = 31 * result + Boolean.hashCode(EmiConfig.sidebarGroupingTierRules);
        result = 31 * result + Boolean.hashCode(EmiConfig.sidebarGroupingCircuits);
        result = 31 * result + Boolean.hashCode(EmiConfig.sidebarGroupingSpawnEggs);
        result = 31 * result + Boolean.hashCode(EmiConfig.sidebarGroupingTools);
        result = 31 * result + Boolean.hashCode(EmiConfig.sidebarGroupingArmor);
        result = 31 * result + Boolean.hashCode(EmiConfig.sidebarGroupingWeapons);
        result = 31 * result + Boolean.hashCode(EmiConfig.sidebarGroupingChiselBlocks);
        result = 31 * result + Boolean.hashCode(EmiConfig.sidebarGroupingColorRules);
        result = 31 * result + Boolean.hashCode(EmiConfig.sidebarGroupingWoodRules);
        result = 31 * result + Boolean.hashCode(EmiConfig.sidebarGroupingSameItemId);
        result = 31 * result + EmiConfig.sidebarGroupingTierPrefixes.hashCode();
        result = 31 * result + EmiConfig.sidebarGroupingColorPrefixes.hashCode();
        result = 31 * result + EmiConfig.sidebarGroupingWoodPrefixes.hashCode();
        result = 31 * result + Boolean.hashCode(EmiConfig.sidebarGroupingCustomRulesEnabled);
        result = 31 * result + EmiConfig.sidebarGroupingCustomRules.hashCode();
        result = 31 * result + EmiConfig.sidebarGroupingBlacklist.hashCode();
        return result;
    }

    private static String gtPrefix(ItemStack stack, String namespace, String path) {
        String className = stack.getItem().getClass().getName();
        boolean gtNamespace = namespace.equals("gtceu") || namespace.equals("gtocore");
        if (!gtNamespace && !className.startsWith("com.gregtechceu.") && !className.contains("TagPrefix")) {
            return null;
        }
        String special = gtNamespace ? specialGtPrefix(path) : null;
        if (special != null) {
            return special;
        }
        initGtReflection();
        if (gtGetPrefix != null && gtPrefixId != null) {
            try {
                Object prefix = gtGetPrefix.invoke(null, stack);
                if (prefix != null && prefix != gtNullPrefix) {
                    Object id = gtPrefixId.invoke(prefix);
                    if (id != null) {
                        String normalized = normalizeGtPrefix(id.toString());
                        if (normalized != null) {
                            return normalized;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return gtNamespace ? fallbackGtPrefix(path) : null;
    }

    private static String normalizeGtPrefix(String value) {
        int colon = value.indexOf(':');
        if (colon >= 0 && colon + 1 < value.length()) {
            value = value.substring(colon + 1);
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = Character.toLowerCase(value.charAt(i));
            if (Character.isLetterOrDigit(c)) {
                builder.append(c);
            }
        }
        if (builder.isEmpty()) {
            return null;
        }
        String normalized = builder.toString();
        if (normalized.equals("null") || normalized.equals("nullprefix")) {
            return null;
        }
        if (normalized.startsWith("ore") && !normalized.equals("ore")) {
            return "ore";
        }
        return normalized;
    }

    private static String specialGtPrefix(String path) {
        String wire = multiplicityForm(path, "wire", GT_WIRE_FORMS);
        if (wire != null) {
            return wire;
        }
        String cable = multiplicityForm(path, "cable", GT_CABLE_FORMS);
        if (cable != null) {
            return cable;
        }
        String fluidPipe = pipeForm(path, "fluid_pipe");
        if (fluidPipe != null) {
            return "fluidpipe" + fluidPipe;
        }
        String itemPipe = pipeForm(path, "item_pipe");
        if (itemPipe != null) {
            return "itempipe" + itemPipe;
        }
        if (path.startsWith("hot_") && path.endsWith("_ingot")) {
            return "hotingot";
        }
        if (path.startsWith("small_") && path.endsWith("_dust")) {
            return "smalldust";
        }
        if (path.startsWith("tiny_") && path.endsWith("_dust")) {
            return "tinydust";
        }
        if (path.startsWith("impure_") && path.endsWith("_dust")) {
            return "impuredust";
        }
        if (path.startsWith("pure_") && path.endsWith("_dust")) {
            return "puredust";
        }
        if (path.startsWith("refined_") && path.endsWith("_ore")) {
            return "refinedore";
        }
        if (path.startsWith("purified_") && path.endsWith("_ore")) {
            return "purifiedore";
        }
        if (path.startsWith("crushed_") && path.endsWith("_ore")) {
            return "crushedore";
        }
        if (path.startsWith("raw_") && path.endsWith("_block")) {
            return "raworeblock";
        }
        if (path.startsWith("chipped_") && path.endsWith("_gem")) {
            return "chippedgem";
        }
        if (path.startsWith("flawed_") && path.endsWith("_gem")) {
            return "flawedgem";
        }
        if (path.startsWith("flawless_") && path.endsWith("_gem")) {
            return "flawlessgem";
        }
        if (path.startsWith("exquisite_") && path.endsWith("_gem")) {
            return "exquisitegem";
        }
        if (path.startsWith("dense_") && path.endsWith("_plate")) {
            return "denseplate";
        }
        if (path.startsWith("double_") && path.endsWith("_plate")) {
            return "doubleplate";
        }
        if (path.startsWith("triple_") && path.endsWith("_plate")) {
            return "tripleplate";
        }
        if (path.startsWith("quadruple_") && path.endsWith("_plate")) {
            return "quadrupleplate";
        }
        if (path.startsWith("quintuple_") && path.endsWith("_plate")) {
            return "quintupleplate";
        }
        if (path.startsWith("superdense_") && path.endsWith("_plate")) {
            return "superdenseplate";
        }
        if (path.startsWith("long_") && path.endsWith("_rod")) {
            return "longrod";
        }
        if (path.startsWith("small_") && path.endsWith("_spring")) {
            return "smallspring";
        }
        if (path.startsWith("small_") && path.endsWith("_gear")) {
            return "smallgear";
        }
        if (path.startsWith("fine_") && path.endsWith("_wire")) {
            return "finewire";
        }
        if (path.endsWith("_coil_block")) {
            return "coilblock";
        }
        if (path.endsWith("_indicator")) {
            return "surfacerock";
        }
        if (path.endsWith("_turbine_blade")) {
            return "turbineblade";
        }
        if (path.endsWith("_drill_head")) {
            return "drillhead";
        }
        if (path.endsWith("_frame")) {
            return "frame";
        }
        if (path.endsWith("_bucket")) {
            return "fluidbucket";
        }
        for (String[] suffix : GT_TOOL_SUFFIXES) {
            if (path.endsWith(suffix[0]) && path.length() > suffix[0].length()) {
                return "tool" + suffix[1];
            }
        }
        if (path.endsWith("_ore")) {
            return "ore";
        }
        return null;
    }

    private static String multiplicityForm(String path, String suffix, String[][] forms) {
        if (!path.endsWith("_" + suffix)) {
            return null;
        }
        for (String[] form : forms) {
            String token = "_" + form[0] + "_";
            if (path.contains(token) || path.startsWith(form[0] + "_")) {
                return form[1];
            }
        }
        return null;
    }

    private static String pipeForm(String path, String suffix) {
        if (!path.endsWith("_" + suffix)) {
            return null;
        }
        for (String size : GT_PIPE_SIZES) {
            String token = "_" + size + "_";
            if (path.contains(token) || path.startsWith(size + "_")) {
                return size;
            }
        }
        return "normal";
    }

    private static String fallbackGtPrefix(String path) {
        if (path.startsWith("raw_") && path.length() > 4) {
            return "raw";
        }
        for (String[] suffix : GT_MATERIAL_SUFFIXES) {
            if (path.endsWith(suffix[0]) && path.length() > suffix[0].length()) {
                return suffix[1];
            }
        }
        return null;
    }

    private static void initGtReflection() {
        if (gtReflectionReady) {
            return;
        }
        gtReflectionReady = true;
        try {
            Class<?> helper = Class.forName("com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper");
            Class<?> prefix = Class.forName("com.gregtechceu.gtceu.api.data.tag.TagPrefix");
            gtGetPrefix = helper.getMethod("getPrefix", ItemStack.class);
            try {
                gtPrefixId = prefix.getMethod("id");
            } catch (Throwable ignored) {
                gtPrefixId = prefix.getMethod("getName");
            }
            try {
                Field field = prefix.getField("NULL_PREFIX");
                gtNullPrefix = field.get(null);
            } catch (Throwable ignored) {
                gtNullPrefix = null;
            }
        } catch (Throwable t) {
            gtGetPrefix = null;
            gtPrefixId = null;
        }
    }

    public static final class Group {
        private final String key;
        private final List<EmiIngredient> members;

        private Group(String key, List<EmiIngredient> members) {
            this.key = key;
            this.members = members;
        }

        public List<EmiIngredient> members() {
            return members;
        }

        public int size() {
            return members.size();
        }
    }

    private record Settings(List<String> tierPrefixes, List<String> colorPrefixes, List<String> woodPrefixes,
            List<String> customRules, List<String> blacklist) {
    }

    private record Cache(int size, int settingsHash, List<EmiIngredient> visible,
            IdentityHashMap<EmiIngredient, Group> groupsByIngredient) {
    }
}
