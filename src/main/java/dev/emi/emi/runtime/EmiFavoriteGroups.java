package dev.emi.emi.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class EmiFavoriteGroups {
    private static final List<Group> GROUPS = new ArrayList<>();
    private static final EmiFavorite SIDEBAR_SPACER = new EmiFavorite(EmiStack.EMPTY, null);
    private static List<EmiFavorite> visibleFavorites = List.of();
    private static boolean visibilityDirty = true;
    private static int sidebarNamespacePageSize = -1;
    private static List<Integer> sidebarPageNamespaces = List.of(0);
    private static @Nullable Group movePreviewGroup;
    private static int movePreviewRawInsertionIndex = -1;

    private EmiFavoriteGroups() {
    }

    public static List<Group> groups() {
        return GROUPS;
    }

    public static JsonArray save() {
        JsonArray result = new JsonArray();
        for (Group group : GROUPS) {
            normalizeGroup(group);
            if (group.members.size() < 2) {
                continue;
            }
            JsonObject object = new JsonObject();
            JsonArray members = new JsonArray();
            JsonArray bases = new JsonArray();
            for (EmiFavorite favorite : group.members) {
                int index = identityIndexOf(EmiFavorites.favorites, favorite);
                if (index >= 0) {
                    members.add(index);
                    bases.add(group.baseAmount(favorite));
                }
            }
            if (members.size() < 2) {
                continue;
            }
            object.add("members", members);
            object.add("base_amounts", bases);
            object.addProperty("collapsed", group.collapsed);
            object.addProperty("group_mode", group.groupMode);
            object.addProperty("crafting_chain", group.craftingChain);
            object.addProperty("quantity", group.quantity);
            JsonObject recipeQuantities = new JsonObject();
            for (Map.Entry<ResourceLocation, Long> entry : group.recipeQuantities.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null && entry.getValue() != 1L) {
                    recipeQuantities.addProperty(entry.getKey().toString(), entry.getValue());
                }
            }
            if (recipeQuantities.size() > 0) {
                object.add("recipe_quantities", recipeQuantities);
            }
            result.add(object);
        }
        return result;
    }

    public static void load(JsonArray array) {
        clearGroupMovePreview();
        GROUPS.clear();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            if (!GsonHelper.isArrayNode(object, "members")) {
                continue;
            }
            JsonArray membersJson = GsonHelper.getAsJsonArray(object, "members");
            JsonArray basesJson = GsonHelper.getAsJsonArray(object, "base_amounts", new JsonArray());
            List<EmiFavorite> members = new ArrayList<>();
            List<Long> bases = new ArrayList<>();
            for (int i = 0; i < membersJson.size(); i++) {
                try {
                    int index = membersJson.get(i).getAsInt();
                    if (index >= 0 && index < EmiFavorites.favorites.size()) {
                        EmiFavorite favorite = EmiFavorites.favorites.get(index);
                        if (!containsIdentity(members, favorite)) {
                            members.add(favorite);
                            long base = i < basesJson.size() ? Math.max(1L, basesJson.get(i).getAsLong()) : Math.max(1L, favorite.getAmount());
                            bases.add(base);
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            if (members.size() < 2) {
                continue;
            }
            Group group = new Group(members);
            for (int i = 0; i < members.size(); i++) {
                group.baseAmounts.put(members.get(i), bases.get(i));
            }
            group.collapsed = GsonHelper.getAsBoolean(object, "collapsed", false);
            group.groupMode = GsonHelper.getAsBoolean(object, "group_mode", false);
            group.craftingChain = GsonHelper.getAsBoolean(object, "crafting_chain", false);
            group.quantity = Math.max(1L, GsonHelper.getAsLong(object, "quantity", 1L));
            if (object.has("recipe_quantities") && object.get("recipe_quantities").isJsonObject()) {
                JsonObject recipeQuantities = object.getAsJsonObject("recipe_quantities");
                for (Map.Entry<String, JsonElement> entry : recipeQuantities.entrySet()) {
                    try {
                        ResourceLocation id = new ResourceLocation(entry.getKey());
                        long quantity = Math.max(0L, entry.getValue().getAsLong());
                        group.recipeQuantities.put(id, quantity);
                    } catch (Throwable ignored) {
                    }
                }
            }
            GROUPS.add(group);
            applyQuantity(group);
        }
        normalizeAll();
    }

    public static void onFavoritesChanged() {
        clearGroupMovePreview();
        normalizeAll();
        visibilityDirty = true;
    }

    public static List<EmiFavorite> visibleFavorites() {
        if (!visibilityDirty) {
            return visibleFavorites;
        }
        List<EmiFavorite> result = new ArrayList<>();
        for (EmiFavorite favorite : EmiFavorites.favorites) {
            Group group = groupFor(favorite);
            if (group != null && group.collapsed && group.firstMember() != favorite) {
                continue;
            }
            result.add(favorite);
        }
        visibleFavorites = List.copyOf(result);
        visibilityDirty = false;
        return visibleFavorites;
    }

    public static int visibleFavoriteCount() {
        return visibleFavorites().size();
    }

    public static @Nullable EmiFavorite visibleFavorite(int index) {
        List<EmiFavorite> visible = visibleFavorites();
        if (index < 0 || index >= visible.size()) {
            return null;
        }
        return visible.get(index);
    }

    public static int visibleIndexOf(EmiFavorite favorite) {
        return identityIndexOf(visibleFavorites(), favorite);
    }

    public static List<EmiFavorite> sidebarFavorites() {
        if (EmiFavorites.resolveRecipeReferences()) {
            for (Group group : GROUPS) {
                applyQuantity(group);
            }
            visibilityDirty = true;
        }
        EmiScreenManager.SidebarPanel panel = EmiScreenManager.getPanelFor(SidebarType.FAVORITES);
        if (panel == null || panel.space == null || panel.space.pageSize <= 0) {
            return fallbackSidebarFavorites();
        }
        return buildSidebarLayout(panel.space);
    }

    private static List<EmiFavorite> fallbackSidebarFavorites() {
        List<EmiFavorite> fallback = new ArrayList<>(visibleFavorites());
        fallback.addAll(EmiFavorites.syntheticFavorites);
        return fallback;
    }

    public static int sidebarFavoriteCount() {
        return sidebarFavorites().size();
    }

    public static @Nullable EmiFavorite sidebarFavorite(int index) {
        List<EmiFavorite> sidebar = sidebarFavorites();
        if (index < 0 || index >= sidebar.size()) {
            return null;
        }
        EmiFavorite favorite = sidebar.get(index);
        return favorite == SIDEBAR_SPACER || favorite.isEmpty() ? null : favorite;
    }

    public static boolean isSidebarSpacer(EmiFavorite favorite) {
        return favorite == SIDEBAR_SPACER;
    }

    public static int rawInsertionIndexForSidebarEdge(int sidebarEdge) {
        List<EmiFavorite> sidebar = sidebarFavorites();
        if (sidebarEdge <= 0) {
            return 0;
        }
        int edge = Math.min(sidebarEdge, sidebar.size());
        for (int i = edge; i < sidebar.size(); i++) {
            EmiFavorite favorite = sidebar.get(i);
            int raw = identityIndexOf(EmiFavorites.favorites, favorite);
            if (raw >= 0) {
                return raw;
            }
        }
        for (int i = edge - 1; i >= 0; i--) {
            EmiFavorite favorite = sidebar.get(i);
            int raw = identityIndexOf(EmiFavorites.favorites, favorite);
            if (raw >= 0) {
                return raw + 1;
            }
        }
        return EmiFavorites.favorites.size();
    }

    public static int snapGroupInsertion(Group moving, int rawInsertionIndex) {
        if (moving == null) {
            return -1;
        }
        normalizeGroup(moving);
        EmiFavorite first = moving.firstMember();
        if (first == null) {
            return -1;
        }
        int page = EmiFavorites.getFavoritePage(first);
        int pageStart = EmiFavorites.favorites.size();
        int pageEnd = -1;
        for (int i = 0; i < EmiFavorites.favorites.size(); i++) {
            if (EmiFavorites.getFavoritePage(EmiFavorites.favorites.get(i)) == page) {
                pageStart = Math.min(pageStart, i);
                pageEnd = Math.max(pageEnd, i + 1);
            }
        }
        if (pageEnd < 0) {
            return -1;
        }
        int insertion = Math.max(pageStart, Math.min(pageEnd, rawInsertionIndex));
        for (Group group : GROUPS) {
            if (group == moving) {
                continue;
            }
            EmiFavorite targetFirst = group.firstMember();
            if (targetFirst == null || EmiFavorites.getFavoritePage(targetFirst) != page) {
                continue;
            }
            int start = groupStart(group);
            int end = groupEnd(group) + 1;
            if (insertion > start && insertion < end) {
                int left = insertion - start;
                int right = end - insertion;
                insertion = left <= right ? start : end;
                break;
            }
        }
        return insertion;
    }

    public static void setGroupMovePreview(Group group, int rawInsertionIndex) {
        int insertion = snapGroupInsertion(group, rawInsertionIndex);
        if (insertion < 0) {
            clearGroupMovePreview();
            return;
        }
        movePreviewGroup = group;
        movePreviewRawInsertionIndex = insertion;
    }

    public static void clearGroupMovePreview() {
        movePreviewGroup = null;
        movePreviewRawInsertionIndex = -1;
    }

    public static boolean hasGroupMovePreview(Group group) {
        return movePreviewGroup == group && movePreviewRawInsertionIndex >= 0;
    }

    private static List<EmiFavorite> favoritesForLayout() {
        if (movePreviewGroup == null || movePreviewRawInsertionIndex < 0) {
            return EmiFavorites.favorites;
        }
        List<EmiFavorite> order = new ArrayList<>(EmiFavorites.favorites);
        List<EmiFavorite> moving = new ArrayList<>(movePreviewGroup.members);
        int before = 0;
        for (EmiFavorite favorite : moving) {
            int index = identityIndexOf(order, favorite);
            if (index >= 0 && index < movePreviewRawInsertionIndex) {
                before++;
            }
        }
        for (EmiFavorite favorite : moving) {
            removeIdentity(order, favorite);
        }
        int target = Math.max(0, Math.min(order.size(), movePreviewRawInsertionIndex - before));
        order.addAll(target, moving);
        return order;
    }

    private static List<EmiFavorite> buildSidebarLayout(EmiScreenManager.ScreenSpace space) {
        int pageSize = space.pageSize;
        if (pageSize <= 0 || space.th <= 0) {
            sidebarNamespacePageSize = pageSize;
            sidebarPageNamespaces = List.of(0);
            return List.of();
        }

        List<EmiFavorite> result = new ArrayList<>();
        List<Integer> pageNamespaces = new ArrayList<>();
        List<EmiFavorite> layoutFavorites = favoritesForLayout();
        List<Group> layoutGroups = new ArrayList<>(GROUPS);
        layoutGroups.sort(Comparator.comparingInt(group -> groupStart(group, layoutFavorites)));
        int namespaceCount = EmiFavorites.getFavoritePageCount();
        for (int namespace = 0; namespace < namespaceCount; namespace++) {
            int start = result.size();
            for (EmiFavorite favorite : layoutFavorites) {
                if (EmiFavorites.getFavoritePage(favorite) == namespace && groupFor(favorite) == null) {
                    result.add(favorite);
                }
            }
            if (namespace == 0) {
                result.addAll(EmiFavorites.syntheticFavorites);
            }
            padToNextRow(result, space);
            for (Group group : layoutGroups) {
                normalizeGroup(group);
                EmiFavorite first = group.firstMember();
                if (first == null || EmiFavorites.getFavoritePage(first) != namespace) {
                    continue;
                }
                List<EmiFavorite> members = group.collapsed ? List.of(first) : List.copyOf(group.members);
                appendGroupRows(result, members, space);
                padToNextRow(result, space);
            }
            while (result.size() > start && result.get(result.size() - 1) == SIDEBAR_SPACER) {
                result.remove(result.size() - 1);
            }
            int used = result.size() - start;
            int pages = Math.max(1, (used + pageSize - 1) / pageSize);
            int target = start + pages * pageSize;
            while (result.size() < target) {
                result.add(SIDEBAR_SPACER);
            }
            for (int i = 0; i < pages; i++) {
                pageNamespaces.add(namespace);
            }
        }
        sidebarNamespacePageSize = pageSize;
        sidebarPageNamespaces = pageNamespaces.isEmpty() ? List.of(0) : List.copyOf(pageNamespaces);
        return result;
    }

    public static int namespaceForSidebarPage(EmiScreenManager.ScreenSpace space, int page) {
        if (space == null || space.pageSize <= 0) {
            return 0;
        }
        if (sidebarNamespacePageSize != space.pageSize || sidebarPageNamespaces.isEmpty()) {
            buildSidebarLayout(space);
        }
        if (page < 0) {
            return 0;
        }
        if (page >= sidebarPageNamespaces.size()) {
            return Math.max(0, EmiFavorites.getFavoritePageCount() - 1);
        }
        return sidebarPageNamespaces.get(page);
    }

    public static int firstSidebarPageForNamespace(EmiScreenManager.ScreenSpace space, int namespace) {
        if (space == null || space.pageSize <= 0) {
            return 0;
        }
        buildSidebarLayout(space);
        for (int i = 0; i < sidebarPageNamespaces.size(); i++) {
            if (sidebarPageNamespaces.get(i) == namespace) {
                return i;
            }
        }
        return Math.max(0, sidebarPageNamespaces.size() - 1);
    }

    private static void appendGroupRows(List<EmiFavorite> result, List<EmiFavorite> members,
            EmiScreenManager.ScreenSpace space) {
        ResourceLocation currentRecipe = null;
        boolean inRecipeRow = false;
        for (EmiFavorite member : members) {
            ResourceLocation recipeId = member.getRecipeId();
            if (recipeId != null) {
                if (inRecipeRow && !recipeId.equals(currentRecipe)) {
                    padToNextRow(result, space);
                }
                currentRecipe = recipeId;
                inRecipeRow = true;
            } else if (inRecipeRow) {
                padToNextRow(result, space);
                currentRecipe = null;
                inRecipeRow = false;
            }
            result.add(member);
        }
    }

    private static void padToNextRow(List<EmiFavorite> result, EmiScreenManager.ScreenSpace space) {
        if (result.isEmpty() || space.pageSize <= 0 || space.th <= 0) {
            return;
        }
        int pageOffset = result.size() % space.pageSize;
        if (pageOffset == 0) {
            return;
        }
        int rowStart = 0;
        for (int row = 0; row < space.th; row++) {
            int width = space.getWidth(row);
            int rowEnd = rowStart + width;
            if (pageOffset <= rowEnd) {
                int remaining = rowEnd - pageOffset;
                for (int i = 0; i < remaining; i++) {
                    result.add(SIDEBAR_SPACER);
                }
                return;
            }
            rowStart = rowEnd;
        }
    }

    private static List<EmiFavorite> spacerPage(int pageSize) {
        return new ArrayList<>(Collections.nCopies(pageSize, SIDEBAR_SPACER));
    }

    public static int rawInsertionIndexForVisibleEdge(int visibleEdge) {
        List<EmiFavorite> visible = visibleFavorites();
        if (visibleEdge <= 0 || visible.isEmpty()) {
            return 0;
        }
        if (visibleEdge >= visible.size()) {
            return EmiFavorites.favorites.size();
        }
        return Math.max(0, identityIndexOf(EmiFavorites.favorites, visible.get(visibleEdge)));
    }

    public static @Nullable Group groupFor(EmiFavorite favorite) {
        for (Group group : GROUPS) {
            if (containsIdentity(group.members, favorite)) {
                return group;
            }
        }
        return null;
    }

    public static @Nullable Group createOrInclude(int rawStart, int rawEnd) {
        if (EmiFavorites.favorites.size() < 2) {
            return null;
        }
        int start = Math.max(0, Math.min(rawStart, rawEnd));
        int end = Math.min(EmiFavorites.favorites.size() - 1, Math.max(rawStart, rawEnd));
        if (end <= start) {
            return null;
        }
        int mergedStart = start;
        int mergedEnd = end;
        List<Group> merged = new ArrayList<>();
        boolean expanded;
        do {
            expanded = false;
            for (Group group : GROUPS) {
                if (merged.contains(group)) {
                    continue;
                }
                int gs = groupStart(group);
                int ge = groupEnd(group);
                if (gs <= mergedEnd && ge >= mergedStart) {
                    merged.add(group);
                    mergedStart = Math.min(mergedStart, gs);
                    mergedEnd = Math.max(mergedEnd, ge);
                    expanded = true;
                }
            }
        } while (expanded);

        Group group;
        if (merged.isEmpty()) {
            group = new Group(new ArrayList<>(EmiFavorites.favorites.subList(mergedStart, mergedEnd + 1)));
        } else {
            group = merged.get(0);
            LinkedHashMap<ResourceLocation, Long> recipeQuantities = new LinkedHashMap<>();
            for (Group old : merged) {
                for (Map.Entry<ResourceLocation, Long> entry : old.recipeQuantities.entrySet()) {
                    recipeQuantities.putIfAbsent(entry.getKey(), entry.getValue());
                }
            }
            GROUPS.removeAll(merged);
            List<EmiFavorite> members = new ArrayList<>(EmiFavorites.favorites.subList(mergedStart, mergedEnd + 1));
            IdentityHashMap<EmiFavorite, Long> bases = new IdentityHashMap<>();
            for (Group old : merged) {
                for (EmiFavorite favorite : old.members) {
                    bases.put(favorite, old.baseAmount(favorite));
                }
            }
            group.members.clear();
            group.members.addAll(members);
            group.baseAmounts.clear();
            group.recipeQuantities.clear();
            group.recipeQuantities.putAll(recipeQuantities);
            for (EmiFavorite favorite : members) {
                group.baseAmounts.put(favorite, bases.getOrDefault(favorite, Math.max(1L, favorite.getAmount())));
            }
        }
        GROUPS.add(group);
        normalizeGroup(group);
        applyQuantity(group);
        changed();
        return group;
    }

    public static @Nullable Group createGroupFromFavorites(List<EmiFavorite> favorites, boolean craftingChain) {
        List<EmiFavorite> selected = new ArrayList<>();
        for (EmiFavorite favorite : favorites) {
            if (identityIndexOf(EmiFavorites.favorites, favorite) >= 0 && !containsIdentity(selected, favorite)) {
                selected.add(favorite);
            }
        }
        if (selected.size() < 2) {
            return null;
        }
        for (Group old : new ArrayList<>(GROUPS)) {
            for (EmiFavorite favorite : selected) {
                if (containsIdentity(old.members, favorite)) {
                    GROUPS.remove(old);
                    break;
                }
            }
        }
        int targetPage = EmiFavorites.getFavoritePage(selected.get(0));
        for (EmiFavorite favorite : selected) {
            removeIdentity(EmiFavorites.favorites, favorite);
            EmiFavorites.setFavoritePage(favorite, targetPage);
        }
        int insertion = EmiFavorites.favorites.size();
        for (int i = 0; i < EmiFavorites.favorites.size(); i++) {
            if (EmiFavorites.getFavoritePage(EmiFavorites.favorites.get(i)) > targetPage) {
                insertion = i;
                break;
            }
        }
        EmiFavorites.favorites.addAll(insertion, selected);
        Group group = new Group(selected);
        group.craftingChain = craftingChain;
        GROUPS.add(group);
        if (craftingChain) {
            sortRecipeMembers(group);
        }
        applyQuantity(group);
        changed();
        return group;
    }

    public static void excludeRange(int rawStart, int rawEnd) {
        if (GROUPS.isEmpty()) {
            return;
        }
        int start = Math.max(0, Math.min(rawStart, rawEnd));
        int end = Math.min(EmiFavorites.favorites.size() - 1, Math.max(rawStart, rawEnd));
        Set<EmiFavorite> excluded = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = start; i <= end && i < EmiFavorites.favorites.size(); i++) {
            excluded.add(EmiFavorites.favorites.get(i));
        }
        List<Group> replacement = new ArrayList<>();
        for (Group group : new ArrayList<>(GROUPS)) {
            boolean intersects = group.members.stream().anyMatch(excluded::contains);
            if (!intersects) {
                continue;
            }
            GROUPS.remove(group);
            List<EmiFavorite> run = new ArrayList<>();
            int lastIndex = -2;
            for (EmiFavorite favorite : group.members) {
                if (excluded.contains(favorite)) {
                    if (run.size() >= 2) {
                        replacement.add(groupFromRun(group, run));
                    }
                    run = new ArrayList<>();
                    lastIndex = -2;
                    continue;
                }
                int index = identityIndexOf(EmiFavorites.favorites, favorite);
                if (!run.isEmpty() && index != lastIndex + 1) {
                    if (run.size() >= 2) {
                        replacement.add(groupFromRun(group, run));
                    }
                    run = new ArrayList<>();
                }
                run.add(favorite);
                lastIndex = index;
            }
            if (run.size() >= 2) {
                replacement.add(groupFromRun(group, run));
            }
        }
        GROUPS.addAll(replacement);
        changed();
    }

    public static boolean moveFavoriteRelative(EmiFavorite moving, EmiFavorite anchor, boolean after) {
        if (moving == null || anchor == null || moving == anchor) {
            return false;
        }
        int sourceIndex = identityIndexOf(EmiFavorites.favorites, moving);
        int anchorIndex = identityIndexOf(EmiFavorites.favorites, anchor);
        if (sourceIndex < 0 || anchorIndex < 0) {
            return false;
        }
        if (EmiFavorites.getFavoritePage(moving) != EmiFavorites.getFavoritePage(anchor)) {
            return false;
        }
        Group movingGroup = groupFor(moving);
        Group anchorGroup = groupFor(anchor);
        if (movingGroup != anchorGroup) {
            return false;
        }

        EmiFavorites.favorites.remove(sourceIndex);
        anchorIndex = identityIndexOf(EmiFavorites.favorites, anchor);
        if (anchorIndex < 0) {
            EmiFavorites.favorites.add(Math.min(sourceIndex, EmiFavorites.favorites.size()), moving);
            return false;
        }
        int insertion = anchorIndex + (after ? 1 : 0);
        EmiFavorites.favorites.add(Math.max(0, Math.min(EmiFavorites.favorites.size(), insertion)), moving);
        changed();
        return true;
    }

    public static void moveGroup(Group group, int rawInsertionIndex) {
        clearGroupMovePreview();
        normalizeGroup(group);
        if (group.members.isEmpty()) {
            return;
        }
        int start = groupStart(group);
        int end = groupEnd(group);
        if (rawInsertionIndex >= start && rawInsertionIndex <= end + 1) {
            return;
        }
        List<EmiFavorite> moving = new ArrayList<>(group.members);
        int before = 0;
        for (EmiFavorite favorite : moving) {
            int index = identityIndexOf(EmiFavorites.favorites, favorite);
            if (index >= 0 && index < rawInsertionIndex) {
                before++;
            }
        }
        for (EmiFavorite favorite : moving) {
            removeIdentity(EmiFavorites.favorites, favorite);
        }
        int target = Math.max(0, Math.min(EmiFavorites.favorites.size(), rawInsertionIndex - before));
        EmiFavorites.favorites.addAll(target, moving);
        normalizeAll();
        changed();
    }

    public static boolean moveGroupToFavoritePage(Group group, int targetPage) {
        normalizeGroup(group);
        EmiFavorite first = group.firstMember();
        if (first == null) {
            return false;
        }
        int sourcePage = EmiFavorites.getFavoritePage(first);
        int pageCount = EmiFavorites.getFavoritePageCount();
        if (targetPage < 0 || targetPage > pageCount || targetPage == sourcePage) {
            return false;
        }
        if (targetPage == pageCount) {
            targetPage = EmiFavorites.addFavoritePage();
        }
        List<EmiFavorite> moving = new ArrayList<>(group.members);
        for (EmiFavorite favorite : moving) {
            removeIdentity(EmiFavorites.favorites, favorite);
            EmiFavorites.setFavoritePage(favorite, targetPage);
        }
        int insertion = EmiFavorites.favorites.size();
        for (int i = 0; i < EmiFavorites.favorites.size(); i++) {
            if (EmiFavorites.getFavoritePage(EmiFavorites.favorites.get(i)) > targetPage) {
                insertion = i;
                break;
            }
        }
        EmiFavorites.favorites.addAll(insertion, moving);
        GROUPS.remove(group);
        GROUPS.add(group);
        normalizeAll();
        EmiFavorites.removeTrailingEmptyFavoritePage(sourcePage);
        changed();
        return true;
    }

    public static void removeGroup(Group group) {
        if (!GROUPS.remove(group)) {
            return;
        }
        for (EmiFavorite favorite : new ArrayList<>(group.members)) {
            removeIdentity(EmiFavorites.favorites, favorite);
            EmiFavorites.forgetFavoritePage(favorite);
        }
        changed();
    }

    public static void toggleCollapsed(Group group) {
        group.collapsed = !group.collapsed;
        changed();
    }

    public static void toggleGroupMode(Group group) {
        group.groupMode = !group.groupMode;
        if (group.groupMode) {
            sortRecipeMembers(group);
        }
        changed();
    }

    public static void toggleCraftingChain(Group group) {
        group.craftingChain = !group.craftingChain;
        if (group.craftingChain) {
            sortRecipeMembers(group);
        }
        applyQuantity(group);
        changed();
    }

    public static void adjustQuantity(Group group, int direction, long step) {
        if (direction == 0) {
            return;
        }
        group.quantity = stepQuantity(group.quantity, direction, step);
        applyQuantity(group);
        changed();
    }

    public static void adjustRecipeQuantity(Group group, EmiRecipe recipe, int direction, long step) {
        if (group == null || recipe == null || recipe.getId() == null || direction == 0) {
            return;
        }
        ResourceLocation id = recipe.getId();
        long next = stepRecipeQuantity(group.recipeQuantities.getOrDefault(id, 1L), direction, step);
        if (next == 1L) {
            group.recipeQuantities.remove(id);
        } else {
            group.recipeQuantities.put(id, next);
        }
        applyQuantity(group);
        changed();
    }

    public static long recipeQuantity(Group group, @Nullable EmiRecipe recipe) {
        if (group == null || recipe == null || recipe.getId() == null) {
            return 1L;
        }
        return Math.max(0L, group.recipeQuantities.getOrDefault(recipe.getId(), 1L));
    }

    private static long recipeQuantity(Group group, @Nullable ResourceLocation recipeId) {
        if (group == null || recipeId == null) {
            return 1L;
        }
        return Math.max(0L, group.recipeQuantities.getOrDefault(recipeId, 1L));
    }

    public static void setRecipeQuantity(Group group, EmiRecipe recipe, long quantity) {
        if (group == null || recipe == null || recipe.getId() == null) {
            return;
        }
        long normalized = Math.max(0L, quantity);
        if (normalized == 1L) {
            group.recipeQuantities.remove(recipe.getId());
        } else {
            group.recipeQuantities.put(recipe.getId(), normalized);
        }
        applyQuantity(group);
        changed();
    }

    private static long stepQuantity(long current, int direction, long step) {
        current = Math.max(1L, current);
        step = Math.max(1L, step);
        if (step == 1L) {
            return Math.max(1L, safeAdd(current, direction));
        }
        if (direction > 0) {
            long remainder = current % step;
            long delta = remainder == 0L ? step : step - remainder;
            return safeAdd(current, delta);
        }
        if (current <= step) {
            return 1L;
        }
        long remainder = current % step;
        return remainder == 0L ? current - step : current - remainder;
    }

    private static long stepRecipeQuantity(long current, int direction, long step) {
        current = Math.max(0L, current);
        step = Math.max(1L, step);
        if (step == 1L) {
            return Math.max(0L, safeAdd(current, direction));
        }
        if (direction > 0) {
            long remainder = current % step;
            long delta = remainder == 0L ? step : step - remainder;
            return safeAdd(current, delta);
        }
        if (current <= step) {
            return 0L;
        }
        long remainder = current % step;
        return remainder == 0L ? current - step : current - remainder;
    }

    public static void applyQuantity(Group group) {
        if (group.craftingChain) {
            ChainPlan plan = calculatePlan(group, null);
            for (EmiFavorite favorite : group.members) {
                long amount = plan.requiredFavorites.getOrDefault(favorite, safeMultiply(group.baseAmount(favorite), group.quantity));
                favorite.setAmount(Math.max(0L, amount));
            }
        } else {
            for (EmiFavorite favorite : group.members) {
                long rowQuantity = recipeQuantity(group, favorite.getRecipeId());
                long multiplier = safeMultiply(group.quantity, rowQuantity);
                favorite.setAmount(safeMultiply(group.baseAmount(favorite), multiplier));
            }
        }
    }

    public static ChainPlan calculatePlan(Group group, @Nullable EmiPlayerInventory inventory) {
        LinkedHashMap<ResourceLocation, EmiRecipe> recipes = new LinkedHashMap<>();
        for (EmiFavorite favorite : group.members) {
            EmiRecipe recipe = favorite.getRecipe();
            if (recipe != null && recipe.getId() != null && favorite.getRole() != EmiFavorite.Role.ITEM) {
                recipes.putIfAbsent(recipe.getId(), recipe);
            }
        }
        if (recipes.isEmpty()) {
            List<AmountEntry> results = new ArrayList<>();
            IdentityHashMap<EmiFavorite, Long> required = new IdentityHashMap<>();
            for (EmiFavorite favorite : group.members) {
                long amount = favorite.getRole() == EmiFavorite.Role.CATALYST
                    ? 1L
                    : safeMultiply(group.baseAmount(favorite), group.quantity);
                results.add(new AmountEntry(normalize(favorite.getStack()), amount));
                required.put(favorite, amount);
            }
            return new ChainPlan(List.copyOf(results), List.of(), List.of(), required, new IdentityHashMap<>());
        }

        List<EmiRecipe> recipeList = new ArrayList<>(recipes.values());
        List<EmiRecipe> roots = new ArrayList<>();
        for (EmiRecipe candidate : recipeList) {
            boolean consumed = false;
            for (EmiRecipe other : recipeList) {
                if (sameRecipe(candidate, other)) {
                    continue;
                }
                for (EmiIngredient input : other.getInputs()) {
                    if (anyOutputAccepted(candidate, input)) {
                        consumed = true;
                        break;
                    }
                }
                if (consumed) {
                    break;
                }
            }
            if (!consumed) {
                roots.add(candidate);
            }
        }
        if (roots.isEmpty()) {
            roots.add(recipeList.get(0));
        }

        Map<EmiIngredient, Long> ingredients = new LinkedHashMap<>();
        Map<EmiIngredient, Long> results = new LinkedHashMap<>();
        IdentityHashMap<EmiRecipe, Long> recipeBatches = new IdentityHashMap<>();
        Set<ResourceLocation> path = new LinkedHashSet<>();
        for (EmiRecipe root : roots) {
            long batches = safeMultiply(Math.max(1L, group.quantity), recipeQuantity(group, root));
            for (EmiStack output : root.getOutputs()) {
                if (output != null && !output.isEmpty()) {
                    mergeAmount(results, output, safeMultiply(Math.max(1L, output.getAmount()), batches));
                }
            }
            expandRecipe(root, batches, recipeList, recipeBatches, ingredients, path);
        }

        IdentityHashMap<EmiFavorite, Long> required = new IdentityHashMap<>();
        for (EmiFavorite favorite : group.members) {
            EmiRecipe recipe = favorite.getRecipe();
            long amount;
            if (favorite.getRole() == EmiFavorite.Role.CATALYST) {
                amount = 1L;
            } else if (recipe != null && favorite.getRole() != EmiFavorite.Role.ITEM) {
                long batches = batchesFor(recipeBatches, recipe);
                if (batches <= 0) {
                    batches = safeMultiply(group.quantity, recipeQuantity(group, recipe));
                }
                amount = safeMultiply(group.baseAmount(favorite), batches);
            } else {
                amount = safeMultiply(group.baseAmount(favorite), group.quantity);
            }
            required.put(favorite, Math.max(0L, amount));
        }

        List<AmountEntry> resultList = toAmountList(results);
        List<AmountEntry> ingredientList = toAmountList(ingredients);
        List<AmountEntry> missing = new ArrayList<>();
        if (inventory != null) {
            for (AmountEntry entry : ingredientList) {
                long available = available(inventory, entry.ingredient());
                long amount = Math.max(0L, entry.amount() - available);
                if (amount > 0) {
                    missing.add(new AmountEntry(entry.ingredient(), amount));
                }
            }
        }
        return new ChainPlan(List.copyOf(resultList), List.copyOf(ingredientList), List.copyOf(missing), required, recipeBatches);
    }

    private static void expandRecipe(EmiRecipe recipe, long batches, List<EmiRecipe> recipes,
            IdentityHashMap<EmiRecipe, Long> recipeBatches, Map<EmiIngredient, Long> ingredients, Set<ResourceLocation> path) {
        if (recipe == null || recipe.getId() == null || batches <= 0) {
            return;
        }
        recipeBatches.merge(recipe, batches, EmiFavoriteGroups::safeAdd);
        if (!path.add(recipe.getId())) {
            return;
        }
        try {
            for (EmiIngredient input : recipe.getInputs()) {
                if (input == null || input.isEmpty()) {
                    continue;
                }
                long needed = safeMultiply(Math.max(1L, input.getAmount()), batches);
                EmiRecipe producer = findProducer(recipes, input, recipe);
                if (producer == null || producer.getId() == null || path.contains(producer.getId())) {
                    mergeAmount(ingredients, input, needed);
                    continue;
                }
                long outputAmount = matchingOutputAmount(producer, input);
                long producerBatches = ceilDiv(needed, Math.max(1L, outputAmount));
                expandRecipe(producer, producerBatches, recipes, recipeBatches, ingredients, path);
            }
        } finally {
            path.remove(recipe.getId());
        }
    }

    private static @Nullable EmiRecipe findProducer(List<EmiRecipe> recipes, EmiIngredient input, EmiRecipe consumer) {
        for (EmiRecipe recipe : recipes) {
            if (sameRecipe(recipe, consumer)) {
                continue;
            }
            if (anyOutputAccepted(recipe, input)) {
                return recipe;
            }
        }
        return null;
    }

    private static boolean anyOutputAccepted(EmiRecipe recipe, EmiIngredient input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        for (EmiStack output : recipe.getOutputs()) {
            if (accepts(input, output)) {
                return true;
            }
        }
        return false;
    }

    private static long matchingOutputAmount(EmiRecipe recipe, EmiIngredient input) {
        long amount = 0L;
        for (EmiStack output : recipe.getOutputs()) {
            if (accepts(input, output)) {
                amount = safeAdd(amount, Math.max(1L, output.getAmount()));
            }
        }
        return Math.max(1L, amount);
    }

    private static boolean accepts(EmiIngredient ingredient, EmiStack output) {
        if (ingredient == null || output == null || output.isEmpty()) {
            return false;
        }
        for (EmiStack option : ingredient.getEmiStacks()) {
            if (EmiCraftingToolCompat.matches(option, output)) {
                return true;
            }
        }
        return false;
    }

    private static void mergeAmount(Map<EmiIngredient, Long> map, EmiIngredient ingredient, long amount) {
        EmiIngredient key = normalize(ingredient);
        for (Map.Entry<EmiIngredient, Long> entry : map.entrySet()) {
            if (EmiIngredient.areEqual(entry.getKey(), key)) {
                entry.setValue(safeAdd(entry.getValue(), amount));
                return;
            }
        }
        map.put(key, amount);
    }

    private static List<AmountEntry> toAmountList(Map<EmiIngredient, Long> values) {
        List<AmountEntry> list = new ArrayList<>();
        for (Map.Entry<EmiIngredient, Long> entry : values.entrySet()) {
            if (entry.getValue() > 0) {
                list.add(new AmountEntry(entry.getKey(), entry.getValue()));
            }
        }
        return list;
    }

    private static EmiIngredient normalize(EmiIngredient ingredient) {
        try {
            return ingredient.copy().setAmount(1).setChance(1);
        } catch (Throwable ignored) {
            return ingredient;
        }
    }

    private static long available(EmiPlayerInventory inventory, EmiIngredient ingredient) {
        long total = 0L;
        for (EmiStack stack : inventory.inventory.values()) {
            for (EmiStack option : ingredient.getEmiStacks()) {
                if (!EmiCraftingToolCompat.matches(option, stack)) {
                    continue;
                }
                if (EmiCraftingToolCompat.isReusable(option)) {
                    long uses = EmiCraftingToolCompat.getSafeCraftingUses(stack);
                    if (uses < 0L) {
                        return Long.MAX_VALUE;
                    }
                    total = safeAdd(total, uses);
                    break;
                }
                total = safeAdd(total, Math.max(0L, stack.getAmount()));
                break;
            }
        }
        return total;
    }

    private static long batchesFor(IdentityHashMap<EmiRecipe, Long> map, EmiRecipe recipe) {
        Long direct = map.get(recipe);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<EmiRecipe, Long> entry : map.entrySet()) {
            if (sameRecipe(entry.getKey(), recipe)) {
                return entry.getValue();
            }
        }
        return 0L;
    }

    private static boolean sameRecipe(EmiRecipe a, EmiRecipe b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null || a.getId() == null || b.getId() == null) {
            return false;
        }
        return a.getId().equals(b.getId());
    }

    private static void sortRecipeMembers(Group group) {
        normalizeGroup(group);
        if (group.members.size() < 2) {
            return;
        }
        int start = groupStart(group);
        if (start < 0) {
            return;
        }
        List<EmiFavorite> original = new ArrayList<>(group.members);
        IdentityHashMap<EmiFavorite, Integer> originalOrder = new IdentityHashMap<>();
        LinkedHashMap<ResourceLocation, Integer> recipeOrder = new LinkedHashMap<>();
        IdentityHashMap<EmiFavorite, Integer> itemOrder = new IdentityHashMap<>();
        int nextOrder = 0;
        for (int i = 0; i < original.size(); i++) {
            EmiFavorite favorite = original.get(i);
            originalOrder.put(favorite, i);
            ResourceLocation recipeId = favorite.getRecipeId();
            if (recipeId != null) {
                if (!recipeOrder.containsKey(recipeId)) {
                    recipeOrder.put(recipeId, nextOrder++);
                }
            } else {
                itemOrder.put(favorite, nextOrder++);
            }
        }
        List<EmiFavorite> sorted = new ArrayList<>(original);
        sorted.sort((a, b) -> {
            int ao = sortBlock(a, recipeOrder, itemOrder);
            int bo = sortBlock(b, recipeOrder, itemOrder);
            if (ao != bo) {
                return Integer.compare(ao, bo);
            }
            int ar = roleOrder(a.getRole());
            int br = roleOrder(b.getRole());
            if (ar != br) {
                return Integer.compare(ar, br);
            }
            return Integer.compare(originalOrder.getOrDefault(a, 0), originalOrder.getOrDefault(b, 0));
        });
        if (sameIdentityOrder(sorted, original)) {
            return;
        }
        for (EmiFavorite favorite : original) {
            removeIdentity(EmiFavorites.favorites, favorite);
        }
        EmiFavorites.favorites.addAll(Math.min(start, EmiFavorites.favorites.size()), sorted);
        group.members.clear();
        group.members.addAll(sorted);
        visibilityDirty = true;
    }

    private static int sortBlock(EmiFavorite favorite, Map<ResourceLocation, Integer> recipeOrder,
            IdentityHashMap<EmiFavorite, Integer> itemOrder) {
        ResourceLocation recipeId = favorite.getRecipeId();
        if (recipeId != null) {
            return recipeOrder.getOrDefault(recipeId, Integer.MAX_VALUE - 1);
        }
        return itemOrder.getOrDefault(favorite, Integer.MAX_VALUE);
    }

    private static int roleOrder(EmiFavorite.Role role) {
        return switch (role) {
            case RESULT -> 0;
            case INGREDIENT -> 1;
            case CATALYST -> 2;
            case ITEM -> 3;
        };
    }

    private static void normalizeAll() {
        for (Group group : new ArrayList<>(GROUPS)) {
            normalizeGroup(group);
            EmiFavorite first = group.firstMember();
            if (first != null) {
                int page = EmiFavorites.getFavoritePage(first);
                for (EmiFavorite favorite : group.members) {
                    EmiFavorites.setFavoritePage(favorite, page);
                }
            }
            if (group.members.size() < 2) {
                GROUPS.remove(group);
            }
        }
        GROUPS.sort(Comparator.comparingInt(EmiFavoriteGroups::groupStart));
        visibilityDirty = true;
    }

    private static void normalizeGroup(Group group) {
        group.members.removeIf(favorite -> identityIndexOf(EmiFavorites.favorites, favorite) < 0);
        group.members.sort(Comparator.comparingInt(favorite -> identityIndexOf(EmiFavorites.favorites, favorite)));
        group.baseAmounts.keySet().removeIf(favorite -> !containsIdentity(group.members, favorite));
        Set<ResourceLocation> recipeIds = new LinkedHashSet<>();
        for (EmiFavorite favorite : group.members) {
            group.baseAmounts.putIfAbsent(favorite, Math.max(1L, favorite.getAmount()));
            ResourceLocation recipeId = favorite.getRecipeId();
            if (recipeId != null) {
                recipeIds.add(recipeId);
            }
        }
        group.recipeQuantities.keySet().removeIf(id -> !recipeIds.contains(id));
    }

    private static Group groupFromRun(Group source, List<EmiFavorite> run) {
        Group group = new Group(run);
        group.collapsed = source.collapsed;
        group.groupMode = source.groupMode;
        group.craftingChain = source.craftingChain;
        group.quantity = source.quantity;
        group.recipeQuantities.putAll(source.recipeQuantities);
        for (EmiFavorite favorite : run) {
            group.baseAmounts.put(favorite, source.baseAmount(favorite));
        }
        return group;
    }

    private static int groupStart(Group group) {
        return groupStart(group, EmiFavorites.favorites);
    }

    private static int groupStart(Group group, List<EmiFavorite> order) {
        int result = Integer.MAX_VALUE;
        for (EmiFavorite favorite : group.members) {
            int index = identityIndexOf(order, favorite);
            if (index >= 0) {
                result = Math.min(result, index);
            }
        }
        return result == Integer.MAX_VALUE ? -1 : result;
    }

    private static int groupEnd(Group group) {
        int result = -1;
        for (EmiFavorite favorite : group.members) {
            result = Math.max(result, identityIndexOf(EmiFavorites.favorites, favorite));
        }
        return result;
    }

    private static void changed() {
        clearGroupMovePreview();
        normalizeAll();
        visibilityDirty = true;
        EmiScreenManager.repopulatePanels(SidebarType.FAVORITES);
        EmiPersistentData.save();
    }

    private static int identityIndexOf(List<EmiFavorite> list, EmiFavorite target) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == target) {
                return i;
            }
        }
        return -1;
    }

    private static boolean containsIdentity(List<EmiFavorite> list, EmiFavorite target) {
        return identityIndexOf(list, target) >= 0;
    }

    private static boolean sameIdentityOrder(List<EmiFavorite> a, List<EmiFavorite> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i) != b.get(i)) {
                return false;
            }
        }
        return true;
    }

    private static void removeIdentity(List<EmiFavorite> list, EmiFavorite target) {
        int index = identityIndexOf(list, target);
        if (index >= 0) {
            list.remove(index);
        }
    }

    private static long ceilDiv(long a, long b) {
        if (a <= 0) {
            return 0L;
        }
        return 1L + (a - 1L) / Math.max(1L, b);
    }

    private static long safeMultiply(long a, long b) {
        if (a <= 0 || b <= 0) {
            return 1L;
        }
        if (a > Long.MAX_VALUE / b) {
            return Long.MAX_VALUE;
        }
        return a * b;
    }

    private static long safeAdd(long a, long b) {
        if (b > 0 && a > Long.MAX_VALUE - b) {
            return Long.MAX_VALUE;
        }
        if (b < 0 && a < Long.MIN_VALUE - b) {
            return Long.MIN_VALUE;
        }
        return a + b;
    }

    public static final class Group {
        private final List<EmiFavorite> members = new ArrayList<>();
        private final IdentityHashMap<EmiFavorite, Long> baseAmounts = new IdentityHashMap<>();
        private final LinkedHashMap<ResourceLocation, Long> recipeQuantities = new LinkedHashMap<>();
        public boolean collapsed;
        public boolean groupMode;
        public boolean craftingChain;
        public long quantity = 1L;

        private Group(List<EmiFavorite> members) {
            this.members.addAll(members);
            for (EmiFavorite favorite : members) {
                baseAmounts.put(favorite, Math.max(1L, favorite.getAmount()));
            }
        }

        public List<EmiFavorite> members() {
            return Collections.unmodifiableList(members);
        }

        public long baseAmount(EmiFavorite favorite) {
            return Math.max(1L, baseAmounts.getOrDefault(favorite, Math.max(1L, favorite.getAmount())));
        }

        public @Nullable EmiFavorite firstMember() {
            normalizeGroup(this);
            return members.isEmpty() ? null : members.get(0);
        }
    }

    public record AmountEntry(EmiIngredient ingredient, long amount) {
    }

    public static final class ChainPlan {
        public static final ChainPlan EMPTY = new ChainPlan(List.of(), List.of(), List.of(), new IdentityHashMap<>(), new IdentityHashMap<>());
        public final List<AmountEntry> results;
        public final List<AmountEntry> ingredients;
        public final List<AmountEntry> missing;
        public final IdentityHashMap<EmiFavorite, Long> requiredFavorites;
        public final IdentityHashMap<EmiRecipe, Long> recipeBatches;

        private ChainPlan(List<AmountEntry> results, List<AmountEntry> ingredients, List<AmountEntry> missing,
                IdentityHashMap<EmiFavorite, Long> requiredFavorites, IdentityHashMap<EmiRecipe, Long> recipeBatches) {
            this.results = results;
            this.ingredients = ingredients;
            this.missing = missing;
            this.requiredFavorites = requiredFavorites;
            this.recipeBatches = recipeBatches;
        }

        public long batchesFor(EmiRecipe recipe) {
            Long direct = recipeBatches.get(recipe);
            if (direct != null) {
                return direct;
            }
            if (recipe == null || recipe.getId() == null) {
                return 0L;
            }
            for (Map.Entry<EmiRecipe, Long> entry : recipeBatches.entrySet()) {
                EmiRecipe candidate = entry.getKey();
                if (candidate != null && candidate.getId() != null && candidate.getId().equals(recipe.getId())) {
                    return entry.getValue();
                }
            }
            return 0L;
        }
    }
}
