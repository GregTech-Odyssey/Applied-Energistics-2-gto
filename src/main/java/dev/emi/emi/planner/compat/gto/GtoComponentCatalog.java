package dev.emi.emi.planner.compat.gto;

import dev.emi.emi.EmiPort;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.planner.ProductionPlanner;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Locale;

/** Shared runtime resolver for real GTO/GTCEu multiblock component stacks. */
final class GtoComponentCatalog {
    private GtoComponentCatalog() {
    }

    static EmiStack resolve(String... ids) {
        if (ids == null) {
            return EmiStack.EMPTY;
        }
        for (String id : ids) {
            EmiStack stack = resolveOne(id);
            if (!stack.isEmpty()) {
                return stack;
            }
        }
        return EmiStack.EMPTY;
    }

    static EmiStack findTiered(int tier, List<String> preferredNamespaces, String... pathFragments) {
        String tierToken = tierToken(tier);
        if (tierToken.isBlank()) {
            return EmiStack.EMPTY;
        }
        try {
            ResourceLocation best = null;
            int bestScore = Integer.MIN_VALUE;
            for (ResourceLocation id : EmiPort.getItemRegistry().keySet()) {
                String path = id.getPath().toLowerCase(Locale.ROOT);
                if (!hasTierPrefix(path, tierToken) || !containsAll(path, pathFragments)) {
                    continue;
                }
                int score = namespaceScore(id.getNamespace(), preferredNamespaces) - path.length();
                if (path.startsWith(tierToken + "_")) {
                    score += 100;
                }
                if (score > bestScore) {
                    best = id;
                    bestScore = score;
                }
            }
            if (best != null) {
                return resolveOne(best.toString());
            }
        } catch (Throwable ignored) {
        }
        return EmiStack.EMPTY;
    }

    static String displayName(EmiStack stack, String fallback) {
        if (stack != null && !stack.isEmpty()) {
            try {
                String name = stack.getName().getString();
                if (name != null && !name.isBlank()) {
                    return name;
                }
            } catch (Throwable ignored) {
            }
        }
        return fallback == null ? "" : fallback;
    }

    static String tierToken(int tier) {
        String name = ProductionPlanner.voltageTierName(tier);
        if (name == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = Character.toLowerCase(name.charAt(i));
            if (Character.isLetterOrDigit(c)) {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static EmiStack resolveOne(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return EmiStack.EMPTY;
        }
        try {
            ResourceLocation id = EmiPort.id(rawId);
            Item item = EmiPort.getItemRegistry().get(id);
            if (item != null && id.equals(EmiPort.getItemRegistry().getKey(item))) {
                EmiStack stack = EmiStack.of(item);
                if (!stack.isEmpty()) {
                    return stack;
                }
            }
            Block block = EmiPort.getBlockRegistry().get(id);
            if (block != null && id.equals(EmiPort.getBlockRegistry().getKey(block))) {
                EmiStack stack = EmiStack.of(block);
                if (!stack.isEmpty()) {
                    return stack;
                }
            }
        } catch (Throwable ignored) {
        }
        return EmiStack.EMPTY;
    }

    private static boolean containsAll(String path, String... fragments) {
        if (fragments == null) {
            return true;
        }
        for (String fragment : fragments) {
            if (fragment != null && !fragment.isBlank()
                    && !path.contains(fragment.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasTierPrefix(String path, String tierToken) {
        return path.equals(tierToken) || path.startsWith(tierToken + "_")
            || path.contains("_" + tierToken + "_") || path.endsWith("_" + tierToken);
    }

    private static int namespaceScore(String namespace, List<String> preferredNamespaces) {
        if (preferredNamespaces == null || preferredNamespaces.isEmpty()) {
            return 0;
        }
        for (int i = 0; i < preferredNamespaces.size(); i++) {
            if (preferredNamespaces.get(i).equals(namespace)) {
                return (preferredNamespaces.size() - i) * 1000;
            }
        }
        return 0;
    }
}
