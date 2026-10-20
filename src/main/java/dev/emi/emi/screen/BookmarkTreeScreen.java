package dev.emi.emi.screen;

import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.emi.emi.EmiPort;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.EmiResolutionRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.bom.*;
import dev.emi.emi.planner.PlannerText;
import dev.emi.emi.runtime.*;
import dev.emi.emi.runtime.EmiFavoriteGroups.ChainPlan;
import dev.emi.emi.screen.tooltip.RecipeTooltipComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class BookmarkTreeScreen extends Screen {
    private static final int HEADER_HEIGHT = 24;
    private static final int STATS_PANEL_HEIGHT = 62;
    private static final int STATS_ICON_SIZE = 16;
    private static final int STATS_ICON_STEP = 26;
    private static final int NODE_SIZE = 18;
    private static final int NODE_HALF = NODE_SIZE / 2;
    private static final int HANDLER_SIZE = 8;
    private static final int NODE_HORIZONTAL_SPACING = 8;
    private static final int NODE_VERTICAL_SPACING = 30;
    private static final int BACKGROUND_COLOR = 0xFF0D0D14;
    private static final int HEADER_COLOR = 0xFFB8B8B8;
    private static final int HEADER_TEXT_COLOR = 0xFF252525;
    private static final int NODE_FILL_COLOR = 0xEE17171D;
    private static final int NODE_HOVER_COLOR = 0xEE2C3345;
    private static final int NODE_BORDER_COLOR = 0xFF77777F;
    private static final int NODE_HOVER_BORDER_COLOR = 0xFFB6C5FF;
    private static final int MISSING_BORDER_COLOR = 0xFFE34A4A;
    private static final int LINE_COLOR = 0xFF9A9AA2;
    private static final int BUTTON_COLOR = 0xFF3B3B42;
    private static final int BUTTON_HOVER_COLOR = 0xFF56565F;
    private static final int STATS_BACKGROUND_COLOR = 0xFF111118;
    private static final int STATS_SEPARATOR_COLOR = 0xFF6A6A72;
    private static final int STATS_LABEL_COLOR = 0xFFF0F0F0;
    private static final int SEARCH_MATCH_COLOR = 0xFF59C7FF;
    private static final int SEARCH_ACTIVE_COLOR = 0xFFFFD55A;
    private static final int SCREENSHOT_PADDING = 12;
    private static final int SCREENSHOT_STATS_MIN_WIDTH = 840;
    private static final int SCREENSHOT_MAX_DIMENSION = 4096;
    private static final int TREE_TAB_WIDTH = 86;
    private static final int GROUP_PICKER_WIDTH = 250;
    private static final int GROUP_PICKER_ROW_HEIGHT = 18;
    private static final Bounds EMPTY = Bounds.EMPTY;
    private static final dev.emi.emi.screen.StackBatcher BATCHER = new StackBatcher();
    private static final List<TreeWorkspace> WORKSPACES = new ArrayList<>();
    private static int activeWorkspaceIndex = -1;
    private static boolean persistentWorkspacesRestored;

    private final Screen old;
    private TreeWorkspace workspace;
    private EmiFavoriteGroups.Group group;
    private List<RootTree> roots = new ArrayList<>();
    private List<Node> nodes = List.of();
    private List<StatEntry> ingredientStats = List.of();
    private List<StatEntry> craftingStats = List.of();
    private List<StatEntry> ingredientNeededStats = List.of();
    private List<StatEntry> ingredientAvailableStats = List.of();
    private List<StatEntry> craftingNeededStats = List.of();
    private List<StatEntry> craftingAvailableStats = List.of();
    private List<StatEntry> resultStats = List.of();
    private List<StatEntry> remainderStats = List.of();
    private List<HandlerStat> handlerStats = List.of();
    private List<StatHitbox> statHitboxes = List.of();
    private List<StatsMenuHitbox> statsMenuHitboxes = List.of();
    private final Map<StatsSection, Boolean> statsVisibility = new LinkedHashMap<>();
    private Bounds snapshotButton = EMPTY;
    private Bounds fitButton = EMPTY;
    private Bounds collapseButton = EMPTY;
    private Bounds statsToggleButton = EMPTY;
    private Bounds statsConfigButton = EMPTY;
    private Bounds saveImageButton = EMPTY;
    private Bounds newTreeButton = EMPTY;
    private Bounds closeTreeButton = EMPTY;
    private List<TreeTabHitbox> treeTabHitboxes = List.of();
    private List<GroupPickerHitbox> groupPickerHitboxes = List.of();
    private boolean groupPickerOpen;
    private int groupPickerScroll;
    private EditBox renameField;
    private int renameWorkspaceIndex = -1;
    private EditBox searchField;
    private int searchFieldX;
    private String searchQuery = "";
    private List<Node> searchMatches = List.of();
    private Set<Node> searchMatchSet = Set.of();
    private int searchIndex = -1;
    private boolean renderingScreenshot;
    private EmiPlayerInventory inventorySnapshot;
    private EmiPlayerInventory snapshotRemaining;
    private boolean collapseCompleted;
    private boolean statsPanelVisible = true;
    private boolean statsConfigOpen;
    private Map<EmiIngredient, Long> snapshotMissing = Map.of();
    private float viewScale = 1f;
    private double offX;
    private double offY;
    private int contentMinX;
    private int contentMaxX;
    private int contentMinY;
    private int contentMaxY;

    public BookmarkTreeScreen(Screen old, EmiFavoriteGroups.Group group) {
        super(EmiPort.literal(PlannerText.tr("bookmark_tree.title", "Crafting Tree")));
        this.old = old;
        restorePersistentWorkspaces();
        workspace = getOrCreateWorkspace(group);
        loadWorkspaceState();
    }

    private static void restorePersistentWorkspaces() {
        if (persistentWorkspacesRestored) {
            return;
        }
        EmiBookmarkTreePersistence.loadFromDisk();
        JsonObject state = EmiBookmarkTreePersistence.getState();
        if (!state.has("trees") || !state.get("trees").isJsonArray()) {
            return;
        }
        persistentWorkspacesRestored = true;
        List<EmiFavoriteGroups.Group> groups = EmiFavoriteGroups.groups();
        JsonArray trees = state.getAsJsonArray("trees");
        for (JsonElement element : trees) {
            if (!element.isJsonObject()) {
                continue;
            }
            try {
                JsonObject object = element.getAsJsonObject();
                EmiFavoriteGroups.Group restoredGroup = resolvePersistentGroup(object, groups);
                if (restoredGroup == null || workspaceIndex(restoredGroup) >= 0) {
                    continue;
                }
                TreeWorkspace restored = new TreeWorkspace(restoredGroup);
                if (object.has("name")) {
                    String name = object.get("name").getAsString().trim();
                    restored.name = name.isEmpty() ? null : name;
                }
                restored.viewScale = Mth.clamp(object.has("view_scale") ? object.get("view_scale").getAsFloat() : 1f, 0.10f, 3.0f);
                restored.offX = object.has("off_x") ? object.get("off_x").getAsDouble() : 0;
                restored.offY = object.has("off_y") ? object.get("off_y").getAsDouble() : 0;
                restored.viewInitialized = object.has("view_initialized") && object.get("view_initialized").getAsBoolean();
                restored.statsPanelVisible = !object.has("stats_panel_visible") || object.get("stats_panel_visible").getAsBoolean();
                restored.searchQuery = object.has("search_query") ? object.get("search_query").getAsString() : "";
                restored.searchIndex = object.has("search_index") ? object.get("search_index").getAsInt() : -1;
                if (object.has("stats") && object.get("stats").isJsonObject()) {
                    JsonObject stats = object.getAsJsonObject("stats");
                    for (StatsSection section : StatsSection.values()) {
                        if (stats.has(section.name())) {
                            restored.statsVisibility.put(section, stats.get(section.name()).getAsBoolean());
                        }
                    }
                }
                WORKSPACES.add(restored);
            } catch (Throwable ignored) {
            }
        }
        if (!WORKSPACES.isEmpty()) {
            int restoredActive = state.has("active") ? state.get("active").getAsInt() : 0;
            activeWorkspaceIndex = Mth.clamp(restoredActive, 0, WORKSPACES.size() - 1);
        }
    }

    private static EmiFavoriteGroups.Group resolvePersistentGroup(JsonObject object, List<EmiFavoriteGroups.Group> groups) {
        String key = object.has("group_key") ? object.get("group_key").getAsString() : "";
        EmiFavoriteGroups.Group indexed = null;
        if (object.has("group_index")) {
            int index = object.get("group_index").getAsInt();
            if (index >= 0 && index < groups.size()) {
                indexed = groups.get(index);
                if (key.isEmpty() || key.equals(groupPersistentKey(indexed))) {
                    return indexed;
                }
            }
        }
        if (!key.isEmpty()) {
            for (EmiFavoriteGroups.Group candidate : groups) {
                if (key.equals(groupPersistentKey(candidate))) {
                    return candidate;
                }
            }
        }
        return indexed;
    }

    private static String groupPersistentKey(EmiFavoriteGroups.Group target) {
        StringBuilder key = new StringBuilder();
        for (EmiFavorite favorite : target.members()) {
            key.append(favorite.getRole().name()).append(':');
            if (favorite.getRecipeId() != null) {
                key.append(favorite.getRecipeId());
            }
            key.append(':');
            for (EmiStack stack : favorite.getStack().getEmiStacks()) {
                key.append(stack.getId()).append(',');
            }
            key.append(';');
        }
        return key.toString();
    }

    private static JsonObject serializePersistentWorkspaces() {
        JsonObject state = new JsonObject();
        JsonArray trees = new JsonArray();
        List<EmiFavoriteGroups.Group> groups = EmiFavoriteGroups.groups();
        int serializedActive = -1;
        for (int i = 0; i < WORKSPACES.size(); i++) {
            TreeWorkspace saved = WORKSPACES.get(i);
            int groupIndex = groups.indexOf(saved.group);
            if (groupIndex < 0) {
                continue;
            }
            JsonObject object = new JsonObject();
            object.addProperty("group_index", groupIndex);
            object.addProperty("group_key", groupPersistentKey(saved.group));
            if (saved.name != null && !saved.name.isBlank()) {
                object.addProperty("name", saved.name);
            }
            object.addProperty("view_scale", saved.viewScale);
            object.addProperty("off_x", saved.offX);
            object.addProperty("off_y", saved.offY);
            object.addProperty("view_initialized", saved.viewInitialized);
            object.addProperty("stats_panel_visible", saved.statsPanelVisible);
            object.addProperty("search_query", saved.searchQuery == null ? "" : saved.searchQuery);
            object.addProperty("search_index", saved.searchIndex);
            JsonObject stats = new JsonObject();
            for (StatsSection section : StatsSection.values()) {
                stats.addProperty(section.name(), saved.statsVisibility.getOrDefault(section, false));
            }
            object.add("stats", stats);
            if (i == activeWorkspaceIndex) {
                serializedActive = trees.size();
            }
            trees.add(object);
        }
        state.add("trees", trees);
        state.addProperty("active", serializedActive < 0 ? 0 : serializedActive);
        return state;
    }

    private static void syncPersistentWorkspaces() {
        EmiBookmarkTreePersistence.setState(serializePersistentWorkspaces());
    }

    private void persistWorkspaces() {
        saveWorkspaceState();
        syncPersistentWorkspaces();
        EmiBookmarkTreePersistence.saveToDisk();
    }

    private static TreeWorkspace getOrCreateWorkspace(EmiFavoriteGroups.Group group) {
        pruneWorkspaces();
        for (int i = 0; i < WORKSPACES.size(); i++) {
            TreeWorkspace candidate = WORKSPACES.get(i);
            if (candidate.group == group) {
                activeWorkspaceIndex = i;
                return candidate;
            }
        }
        TreeWorkspace created = new TreeWorkspace(group);
        WORKSPACES.add(created);
        activeWorkspaceIndex = WORKSPACES.size() - 1;
        return created;
    }

    private static void pruneWorkspaces() {
        List<EmiFavoriteGroups.Group> groups = EmiFavoriteGroups.groups();
        for (int i = WORKSPACES.size() - 1; i >= 0; i--) {
            if (!groups.contains(WORKSPACES.get(i).group)) {
                WORKSPACES.remove(i);
                if (activeWorkspaceIndex > i) {
                    activeWorkspaceIndex--;
                }
            }
        }
        if (WORKSPACES.isEmpty()) {
            activeWorkspaceIndex = -1;
        } else {
            activeWorkspaceIndex = Mth.clamp(activeWorkspaceIndex, 0, WORKSPACES.size() - 1);
        }
    }

    private static int workspaceSourceSignature(EmiFavoriteGroups.Group target) {
        int hash = 1;
        hash = 31 * hash + BoM.addedRecipes.hashCode();
        hash = 31 * hash + BoM.defaultRecipes.hashCode();
        hash = 31 * hash + BoM.disabledRecipes.hashCode();
        hash = 31 * hash + Long.hashCode(target.quantity);
        for (EmiFavorite favorite : target.members()) {
            hash = 31 * hash + favorite.getRole().hashCode();
            hash = 31 * hash + Long.hashCode(favorite.getAmount());
            EmiRecipe recipe = favorite.getRecipe();
            if (recipe != null) {
                hash = 31 * hash + (recipe.getId() == null ? System.identityHashCode(recipe) : recipe.getId().hashCode());
                hash = 31 * hash + Long.hashCode(EmiFavoriteGroups.recipeQuantity(target, recipe));
            }
        }
        return hash;
    }

    private void loadWorkspaceState() {
        group = workspace.group;
        roots = workspace.roots;
        inventorySnapshot = workspace.inventorySnapshot;
        snapshotRemaining = workspace.snapshotRemaining;
        collapseCompleted = workspace.collapseCompleted;
        statsPanelVisible = workspace.statsPanelVisible;
        statsConfigOpen = false;
        snapshotMissing = workspace.snapshotMissing;
        viewScale = workspace.viewScale;
        offX = workspace.offX;
        offY = workspace.offY;
        searchQuery = workspace.searchQuery;
        searchIndex = workspace.searchIndex;
        statsVisibility.clear();
        statsVisibility.putAll(workspace.statsVisibility);
    }

    private void saveWorkspaceState() {
        if (workspace == null) {
            return;
        }
        workspace.inventorySnapshot = inventorySnapshot;
        workspace.snapshotRemaining = snapshotRemaining;
        workspace.collapseCompleted = collapseCompleted;
        workspace.statsPanelVisible = statsPanelVisible;
        workspace.snapshotMissing = snapshotMissing;
        workspace.viewScale = viewScale;
        workspace.offX = offX;
        workspace.offY = offY;
        workspace.viewInitialized = true;
        workspace.searchQuery = searchQuery;
        workspace.searchIndex = searchIndex;
        workspace.statsVisibility.clear();
        workspace.statsVisibility.putAll(statsVisibility);
    }

    @Override
    protected void init() {
        snapshotButton = new Bounds(4, 3, 18, 18);
        fitButton = new Bounds(26, 3, 18, 18);
        collapseButton = new Bounds(48, 3, 18, 18);
        statsToggleButton = new Bounds(70, 3, 18, 18);
        statsConfigButton = new Bounds(92, 3, 18, 18);
        saveImageButton = new Bounds(114, 3, 18, 18);
        newTreeButton = new Bounds(136, 3, 18, 18);
        closeTreeButton = new Bounds(158, 3, 18, 18);
        int searchWidth = Math.max(100, Math.min(220, width / 4));
        searchFieldX = width - searchWidth - 4;
        searchField = new EditBox(minecraft.font, searchFieldX, 5,
            searchWidth, 14, EmiPort.literal(PlannerText.tr("bookmark_tree.search", "Search")));
        searchField.setMaxLength(128);
        searchField.setSuggestion(searchQuery.isEmpty() ? PlannerText.tr("bookmark_tree.search_hint", "Search...") : "");
        searchField.setValue(searchQuery);
        searchField.setResponder(this::updateSearchQuery);
        int sourceSignature = workspaceSourceSignature(group);
        boolean rebuild = !workspace.built || workspace.sourceSignature != sourceSignature;
        if (rebuild) {
            buildTrees();
            workspace.built = true;
            workspace.sourceSignature = sourceSignature;
        }
        if (inventorySnapshot != null) {
            applyInventorySnapshot();
        }
        recalculateForest();
        if (workspace.viewInitialized) {
            viewScale = workspace.viewScale;
            offX = workspace.offX;
            offY = workspace.offY;
        } else {
            fitToView();
            workspace.viewInitialized = true;
        }
    }

    private void updateSearchQuery(String query) {
        searchQuery = query == null ? "" : query;
        if (searchField != null) {
            searchField.setSuggestion(searchQuery.isEmpty() ? PlannerText.tr("bookmark_tree.search_hint", "Search...") : "");
        }
        searchIndex = -1;
        refreshSearchMatches();
    }

    private void refreshSearchMatches() {
        String query = searchQuery.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty() || nodes.isEmpty()) {
            searchMatches = List.of();
            searchMatchSet = Set.of();
            searchIndex = -1;
            return;
        }
        List<Node> matches = new ArrayList<>();
        Set<Node> matchSet = new HashSet<>();
        for (Node node : nodes) {
            if (node.matchesSearch(query)) {
                matches.add(node);
                matchSet.add(node);
            }
        }
        searchMatches = List.copyOf(matches);
        searchMatchSet = Set.copyOf(matchSet);
        if (searchMatches.isEmpty()) {
            searchIndex = -1;
        } else if (searchIndex >= searchMatches.size()) {
            searchIndex = 0;
        }
    }

    private void jumpSearch(int direction) {
        if (searchMatches.isEmpty()) {
            return;
        }
        if (direction < 0) {
            searchIndex = searchIndex < 0 ? searchMatches.size() - 1
                : (searchIndex - 1 + searchMatches.size()) % searchMatches.size();
        } else {
            searchIndex = searchIndex < 0 ? 0 : (searchIndex + 1) % searchMatches.size();
        }
        Node node = searchMatches.get(searchIndex);
        offX = -node.centerX();
        offY = -node.y;
    }

    private void buildTrees() {
        roots.clear();
        LinkedHashMap<Object, EmiRecipe> unique = new LinkedHashMap<>();
        for (EmiFavorite favorite : group.members()) {
            EmiRecipe recipe = favorite.getRecipe();
            if (recipe == null || favorite.getRole() == EmiFavorite.Role.ITEM || recipe.getOutputs().isEmpty()) {
                continue;
            }
            Object key = recipe.getId() == null ? recipe : recipe.getId();
            unique.putIfAbsent(key, recipe);
        }
        List<EmiRecipe> recipes = new ArrayList<>(unique.values());
        if (recipes.isEmpty()) {
            return;
        }

        ChainPlan plan = EmiFavoriteGroups.calculatePlan(group, null);
        List<EmiRecipe> requestedRecipes = new ArrayList<>();
        for (EmiRecipe recipe : recipes) {
            if (EmiFavoriteGroups.recipeQuantity(group, recipe) > 0L) {
                requestedRecipes.add(recipe);
            }
        }
        List<EmiRecipe> rootRecipes = findRoots(requestedRecipes);
        for (EmiRecipe rootRecipe : rootRecipes) {
            long batches = plan.batchesFor(rootRecipe);
            if (batches <= 0L) {
                batches = safeMultiply(Math.max(1L, group.quantity), EmiFavoriteGroups.recipeQuantity(group, rootRecipe));
            }
            if (batches <= 0L) {
                continue;
            }
            MaterialTree tree = new BookmarkMaterialTree(rootRecipe);
            tree.batches = batches;
            populateResolutions(tree, recipes);
            tree.recalculate();
            roots.add(new RootTree(tree));
        }
    }

    private void populateResolutions(MaterialTree tree, List<EmiRecipe> recipes) {
        for (EmiRecipe recipe : recipes) {
            for (EmiStack output : recipe.getOutputs()) {
                if (output != null && !output.isEmpty()) {
                    tree.resolutions.putIfAbsent(normalize(output), recipe);
                }
            }
        }
        for (EmiRecipe consumer : recipes) {
            for (EmiIngredient input : consumer.getInputs()) {
                if (input == null || input.isEmpty()) {
                    continue;
                }
                for (EmiRecipe producer : recipes) {
                    if (!sameRecipe(consumer, producer) && acceptsAnyOutput(input, producer)) {
                        tree.resolutions.putIfAbsent(normalize(input), producer);
                        break;
                    }
                }
            }
        }
    }

    private EmiIngredient normalize(EmiIngredient ingredient) {
        return ingredient.copy().setAmount(1).setChance(1);
    }

    private List<EmiRecipe> findRoots(List<EmiRecipe> recipes) {
        List<EmiRecipe> result = new ArrayList<>();
        for (EmiRecipe candidate : recipes) {
            boolean consumed = false;
            for (EmiRecipe other : recipes) {
                if (sameRecipe(candidate, other)) {
                    continue;
                }
                for (EmiIngredient input : other.getInputs()) {
                    if (acceptsAnyOutput(input, candidate)) {
                        consumed = true;
                        break;
                    }
                }
                if (consumed) {
                    break;
                }
            }
            if (!consumed) {
                result.add(candidate);
            }
        }
        if (result.isEmpty() && !recipes.isEmpty()) {
            result.add(recipes.get(0));
        }
        return result;
    }

    private boolean sameRecipe(EmiRecipe a, EmiRecipe b) {
        if (a == b) {
            return true;
        }
        return a != null && b != null && a.getId() != null && a.getId().equals(b.getId());
    }

    private boolean acceptsAnyOutput(EmiIngredient input, EmiRecipe producer) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        for (EmiStack output : producer.getOutputs()) {
            if (output == null || output.isEmpty()) {
                continue;
            }
            for (EmiStack option : input.getEmiStacks()) {
                if (EmiCraftingToolCompat.matches(option, output)) {
                    return true;
                }
            }
        }
        return false;
    }


    private void captureInventorySnapshot() {
        inventorySnapshot = copyInventory(EmiPlayerInventory.of(minecraft == null ? null : minecraft.player));
        applyInventorySnapshot();
        recalculateForest();
    }

    private void clearInventorySnapshot() {
        inventorySnapshot = null;
        snapshotRemaining = null;
        collapseCompleted = false;
        snapshotMissing = Map.of();
        for (RootTree root : roots) {
            resetProgress(root.tree.goal);
        }
        recalculateForest();
    }

    private void resetProgress(MaterialNode node) {
        if (node == null) {
            return;
        }
        node.progress = ProgressState.UNSTARTED;
        node.totalNeeded = 0L;
        node.neededBatches = 0L;
        if (node.children != null) {
            for (MaterialNode child : node.children) {
                resetProgress(child);
            }
        }
    }

    private EmiPlayerInventory copyInventory(EmiPlayerInventory source) {
        EmiPlayerInventory copy = new EmiPlayerInventory(List.of());
        copy.inventory.clear();
        if (source == null) {
            return copy;
        }
        for (EmiStack stack : source.inventory.values()) {
            if (stack == null || stack.isEmpty() || stack.getAmount() <= 0L) {
                continue;
            }
            EmiStack cloned = stack.copy();
            copy.inventory.put(cloned, cloned);
        }
        return copy;
    }

    private void applyInventorySnapshot() {
        if (inventorySnapshot == null) {
            return;
        }
        LinkedHashMap<EmiIngredient, Long> missing = new LinkedHashMap<>();
        EmiPlayerInventory remaining = copyInventory(inventorySnapshot);
        for (RootTree root : roots) {
            root.tree.calculateProgress(remaining);
            for (FlatMaterialCost cost : root.tree.cost.costs.values()) {
                addMissing(missing, cost.ingredient, cost.getEffectiveAmount());
            }
            for (ChanceMaterialCost cost : root.tree.cost.chanceCosts.values()) {
                addMissing(missing, cost.ingredient, cost.getEffectiveAmount());
            }
            remaining = inventoryFromRemainders(root.tree.cost.remainders);
        }
        snapshotMissing = missing;
        snapshotRemaining = copyInventory(remaining);
    }

    private EmiPlayerInventory inventoryFromRemainders(Map<EmiStack, FlatMaterialCost> remainders) {
        EmiPlayerInventory inventory = new EmiPlayerInventory(List.of());
        inventory.inventory.clear();
        for (FlatMaterialCost cost : remainders.values()) {
            if (cost == null || cost.amount <= 0L || cost.ingredient == null || cost.ingredient.isEmpty()) {
                continue;
            }
            List<EmiStack> stacks = cost.ingredient.getEmiStacks();
            if (stacks.isEmpty()) {
                continue;
            }
            EmiStack stack = stacks.get(0).copy().setAmount(cost.amount);
            inventory.inventory.merge(stack, stack, (a, b) -> a.setAmount(safeAdd(a.getAmount(), b.getAmount())));
        }
        return inventory;
    }

    private void addMissing(LinkedHashMap<EmiIngredient, Long> missing, EmiIngredient ingredient, long amount) {
        if (ingredient == null || ingredient.isEmpty() || amount <= 0L) {
            return;
        }
        EmiIngredient key = normalize(ingredient);
        missing.merge(key, amount, this::safeAdd);
    }

    private long getMissingAmount(EmiIngredient ingredient) {
        if (inventorySnapshot == null || ingredient == null || ingredient.isEmpty()) {
            return 0L;
        }
        Long value = snapshotMissing.get(normalize(ingredient));
        return value == null ? 0L : value;
    }

    private void recalculateForest() {
        TreeVolume forest = null;
        for (RootTree root : roots) {
            TreeVolume volume = addNewNodes(root.tree.goal, root.tree.batches, 1L, 0, ChanceState.DEFAULT);
            if (forest == null) {
                forest = volume;
            } else {
                forest.addToRight(volume);
            }
        }
        if (forest == null) {
            nodes = List.of();
            contentMinX = contentMaxX = contentMinY = contentMaxY = 0;
            rebuildStats();
            refreshSearchMatches();
            BATCHER.repopulate();
            return;
        }

        int center = (forest.getMinLeft() + forest.getMaxRight()) / 2;
        for (Node node : forest.nodes) {
            node.x -= center;
        }
        nodes = List.copyOf(forest.nodes);
        calculateContentBounds();
        rebuildStats();
        refreshSearchMatches();
        BATCHER.repopulate();
    }


    private void rebuildStats() {
        LinkedHashMap<EmiIngredient, StatAccumulator> ingredients = new LinkedHashMap<>();
        LinkedHashMap<EmiIngredient, StatAccumulator> crafting = new LinkedHashMap<>();
        LinkedHashMap<EmiIngredient, StatAccumulator> results = new LinkedHashMap<>();
        LinkedHashMap<EmiRecipeCategory, Long> handlers = new LinkedHashMap<>();
        for (RootTree root : roots) {
            collectStats(root.tree.goal, root.tree.batches, 1L, ChanceState.DEFAULT, true,
                ingredients, crafting, results, handlers);
        }
        ingredientStats = toStatEntries(ingredients);
        craftingStats = toStatEntries(crafting);
        resultStats = toStatEntries(results);
        List<HandlerStat> hs = new ArrayList<>();
        for (Map.Entry<EmiRecipeCategory, Long> entry : handlers.entrySet()) {
            if (entry.getKey() != null && entry.getValue() > 0L) {
                hs.add(new HandlerStat(entry.getKey(), entry.getValue()));
            }
        }
        handlerStats = List.copyOf(hs);
        rebuildProgressStats();
    }

    private void rebuildProgressStats() {
        if (inventorySnapshot == null) {
            ingredientNeededStats = ingredientStats;
            ingredientAvailableStats = List.of();
            craftingNeededStats = craftingStats;
            craftingAvailableStats = List.of();
            remainderStats = List.of();
            return;
        }

        LinkedHashMap<EmiIngredient, StatAccumulator> ingredientNeeded = new LinkedHashMap<>();
        for (Map.Entry<EmiIngredient, Long> entry : snapshotMissing.entrySet()) {
            addStat(ingredientNeeded, entry.getKey(), entry.getValue(), false);
        }
        ingredientNeededStats = toStatEntries(ingredientNeeded);
        ingredientAvailableStats = availableFromSnapshot(ingredientStats);
        craftingAvailableStats = availableFromSnapshot(craftingStats);

        LinkedHashMap<EmiIngredient, StatAccumulator> craftingNeeded = new LinkedHashMap<>();
        for (RootTree root : roots) {
            collectCraftingNeeded(root.tree.goal, true, craftingNeeded);
        }
        craftingNeededStats = toStatEntries(craftingNeeded);
        remainderStats = calculateSnapshotRemainders();
    }

    private void collectCraftingNeeded(MaterialNode node, boolean root,
            LinkedHashMap<EmiIngredient, StatAccumulator> craftingNeeded) {
        if (node == null) {
            return;
        }
        if (!root && node.recipe != null && !(node.recipe instanceof EmiResolutionRecipe)
                && node.progress != ProgressState.COMPLETED && node.totalNeeded > 0L) {
            addStat(craftingNeeded, node.ingredient, node.totalNeeded, false);
        }
        if (node.children != null) {
            for (MaterialNode child : node.children) {
                collectCraftingNeeded(child, false, craftingNeeded);
            }
        }
    }

    private List<StatEntry> availableFromSnapshot(List<StatEntry> totals) {
        List<StatEntry> available = new ArrayList<>();
        for (StatEntry entry : totals) {
            long amount = Math.min(entry.amount, countSnapshotAmount(entry.ingredient));
            if (amount > 0L) {
                available.add(new StatEntry(entry.ingredient, amount, entry.approximate));
            }
        }
        return List.copyOf(available);
    }

    private long countSnapshotAmount(EmiIngredient ingredient) {
        if (inventorySnapshot == null || ingredient == null || ingredient.isEmpty()) {
            return 0L;
        }
        long amount = 0L;
        for (EmiStack stack : inventorySnapshot.inventory.values()) {
            if (stack == null || stack.isEmpty() || stack.getAmount() <= 0L) {
                continue;
            }
            for (EmiStack option : ingredient.getEmiStacks()) {
                if (EmiCraftingToolCompat.matches(option, stack)) {
                    amount = safeAdd(amount, stack.getAmount());
                    break;
                }
            }
        }
        return amount;
    }

    private List<StatEntry> calculateSnapshotRemainders() {
        if (inventorySnapshot == null || snapshotRemaining == null) {
            return List.of();
        }
        LinkedHashMap<EmiIngredient, Long> before = aggregateInventory(inventorySnapshot);
        LinkedHashMap<EmiIngredient, Long> after = aggregateInventory(snapshotRemaining);
        LinkedHashMap<EmiIngredient, StatAccumulator> remainders = new LinkedHashMap<>();
        for (Map.Entry<EmiIngredient, Long> entry : after.entrySet()) {
            long original = before.getOrDefault(entry.getKey(), 0L);
            long added = entry.getValue() - original;
            if (added > 0L) {
                addStat(remainders, entry.getKey(), added, false);
            }
        }
        return toStatEntries(remainders);
    }

    private LinkedHashMap<EmiIngredient, Long> aggregateInventory(EmiPlayerInventory inventory) {
        LinkedHashMap<EmiIngredient, Long> amounts = new LinkedHashMap<>();
        if (inventory == null) {
            return amounts;
        }
        for (EmiStack stack : inventory.inventory.values()) {
            if (stack == null || stack.isEmpty() || stack.getAmount() <= 0L) {
                continue;
            }
            EmiIngredient key = normalize(stack);
            amounts.merge(key, stack.getAmount(), this::safeAdd);
        }
        return amounts;
    }

    private void collectStats(MaterialNode node, long multiplier, long divisor, ChanceState chance, boolean root,
            LinkedHashMap<EmiIngredient, StatAccumulator> ingredients,
            LinkedHashMap<EmiIngredient, StatAccumulator> crafting,
            LinkedHashMap<EmiIngredient, StatAccumulator> results,
            LinkedHashMap<EmiRecipeCategory, Long> handlers) {
        if (node == null) {
            return;
        }
        if (node.catalyst) {
            multiplier = node.amount;
        } else {
            long crafts = ceilDiv(Math.max(0L, multiplier), Math.max(1L, divisor));
            multiplier = safeMultiply(node.amount, crafts);
        }
        boolean approximate = chance.chanced();
        long visibleAmount = approximate
            ? Math.max(node.amount, Math.round(multiplier * chance.chance()))
            : multiplier;
        if (root) {
            addStat(results, node.ingredient, visibleAmount, approximate);
        }

        if (node.recipe != null && node.children != null && !node.children.isEmpty()) {
            ChanceState produced = chance.produce(node.produceChance);
            if (node.recipe instanceof EmiResolutionRecipe) {
                collectStats(node.children.get(0), multiplier, node.divisor, chance, root,
                    ingredients, crafting, results, handlers);
                return;
            }
            if (!root) {
                addStat(crafting, node.ingredient, visibleAmount, approximate);
            }
            long recipeCrafts = ceilDiv(Math.max(0L, multiplier), Math.max(1L, node.divisor));
            EmiRecipeCategory category = node.recipe.getCategory();
            if (category != null && recipeCrafts > 0L) {
                handlers.merge(category, recipeCrafts, this::safeAdd);
            }
            for (MaterialNode child : node.children) {
                ChanceState consumed = produced.consume(child.consumeChance);
                collectStats(child, multiplier, node.divisor, consumed, false,
                    ingredients, crafting, results, handlers);
            }
        } else if (!root) {
            addStat(ingredients, node.ingredient, visibleAmount, approximate);
        }
    }

    private void addStat(LinkedHashMap<EmiIngredient, StatAccumulator> map, EmiIngredient ingredient, long amount, boolean approximate) {
        if (ingredient == null || ingredient.isEmpty() || amount <= 0L) {
            return;
        }
        EmiIngredient key = normalize(ingredient);
        StatAccumulator accumulator = map.get(key);
        if (accumulator == null) {
            map.put(key, new StatAccumulator(key, amount, approximate));
        } else {
            accumulator.amount = safeAdd(accumulator.amount, amount);
            accumulator.approximate |= approximate;
        }
    }

    private long safeAdd(long a, long b) {
        if (a >= Long.MAX_VALUE - b) {
            return Long.MAX_VALUE;
        }
        return a + b;
    }

    private List<StatEntry> toStatEntries(LinkedHashMap<EmiIngredient, StatAccumulator> map) {
        List<StatEntry> entries = new ArrayList<>();
        for (StatAccumulator accumulator : map.values()) {
            entries.add(new StatEntry(accumulator.ingredient, accumulator.amount, accumulator.approximate));
        }
        return List.copyOf(entries);
    }

    private void calculateContentBounds() {
        if (nodes.isEmpty()) {
            contentMinX = contentMaxX = contentMinY = contentMaxY = 0;
            return;
        }
        contentMinX = Integer.MAX_VALUE;
        contentMaxX = Integer.MIN_VALUE;
        contentMinY = Integer.MAX_VALUE;
        contentMaxY = Integer.MIN_VALUE;
        for (Node node : nodes) {
            contentMinX = Math.min(contentMinX, node.x - node.width / 2 - HANDLER_SIZE);
            contentMaxX = Math.max(contentMaxX, node.x + node.width / 2 + 4);
            contentMinY = Math.min(contentMinY, node.y - NODE_HALF - HANDLER_SIZE);
            contentMaxY = Math.max(contentMaxY, node.y + NODE_HALF + 10);
        }
    }

    private TreeVolume addNewNodes(MaterialNode node, long multiplier, long divisor, int depth, ChanceState chance) {
        if (node.catalyst) {
            multiplier = node.amount;
        } else {
            long crafts = ceilDiv(Math.max(0L, multiplier), Math.max(1L, divisor));
            multiplier = safeMultiply(node.amount, crafts);
        }
        if (node.recipe != null && node.children != null && !node.children.isEmpty() && node.state == FoldState.EXPANDED
                && !(inventorySnapshot != null && collapseCompleted && node.progress == ProgressState.COMPLETED)) {
            ChanceState produced = chance.produce(node.produceChance);
            if (node.recipe instanceof EmiResolutionRecipe) {
                TreeVolume volume = addNewNodes(node.children.get(0), multiplier, node.divisor, depth, produced);
                volume.nodes.get(0).resolution = node;
                return volume;
            }
            TreeVolume left = null;
            for (MaterialNode child : node.children) {
                ChanceState consumed = produced.consume(child.consumeChance);
                TreeVolume volume = addNewNodes(child, multiplier, node.divisor, depth + 1, consumed);
                if (left == null) {
                    left = volume;
                } else {
                    left.addToRight(volume);
                }
            }
            left.addHead(node, multiplier, depth * NODE_VERTICAL_SPACING, chance);
            return left;
        }
        return new TreeVolume(node, multiplier, depth * NODE_VERTICAL_SPACING, chance);
    }

    private long ceilDiv(long value, long divisor) {
        if (value <= 0L) {
            return 0L;
        }
        return value / divisor + (value % divisor == 0L ? 0L : 1L);
    }

    private long safeMultiply(long a, long b) {
        if (a <= 0L || b <= 0L) {
            return 0L;
        }
        if (a > Long.MAX_VALUE / b) {
            return Long.MAX_VALUE;
        }
        return a * b;
    }

    private void fitToView() {
        if (nodes.isEmpty()) {
            viewScale = 1f;
            offX = offY = 0;
            return;
        }
        int contentWidth = Math.max(1, contentMaxX - contentMinX);
        int contentHeight = Math.max(1, contentMaxY - contentMinY);
        float sx = Math.max(0.05f, (width - 40f) / (contentWidth + 20f));
        float sy = Math.max(0.05f, (statsPanelTop() - HEADER_HEIGHT - 20f) / (contentHeight + 20f));
        viewScale = Mth.clamp(Math.min(sx, sy), 0.12f, 1.35f);
        offX = -((contentMinX + contentMaxX) / 2.0);
        offY = -((contentMinY + contentMaxY) / 2.0);
        if (workspace != null) {
            workspace.viewInitialized = true;
        }
    }

    @Override
    public void render(GuiGraphics raw, int mouseX, int mouseY, float delta) {
        EmiDrawContext context = EmiDrawContext.wrap(raw);
        context.fill(0, 0, width, height, BACKGROUND_COLOR);

        if (nodes.isEmpty()) {
            context.drawCenteredText(EmiPort.literal(PlannerText.tr("bookmark_tree.no_roots", "No active recipe roots in this bookmark group")), width / 2, height / 2);
        } else {
            int mx = toTreeX(mouseX);
            int my = toTreeY(mouseY);
            PoseStack view = RenderSystem.getModelViewStack();
            view.pushPose();
            view.translate(width / 2.0, viewCenterY(), 0);
            view.scale(viewScale, viewScale, 1);
            view.translate(offX, offY, 0);
            EmiPort.applyModelViewMatrix();

            BATCHER.begin(0, 0, 0);
            for (Node node : nodes) {
                node.render(context, mx, my, delta);
            }
            BATCHER.draw();
            for (Node node : nodes) {
                node.renderOverlay(context, mx, my, delta);
            }

            view.popPose();
            EmiPort.applyModelViewMatrix();
        }

        context.fill(0, 0, width, HEADER_HEIGHT, HEADER_COLOR);
        context.fill(0, HEADER_HEIGHT - 1, width, 1, 0xFF6A6A6A);
        drawSnapshotButton(context, mouseX, mouseY);
        drawFitButton(context, mouseX, mouseY);
        drawCollapseButton(context, mouseX, mouseY);
        drawStatsToggleButton(context, mouseX, mouseY);
        drawStatsConfigButton(context, mouseX, mouseY);
        drawSaveImageButton(context, mouseX, mouseY);
        drawNewTreeButton(context, mouseX, mouseY);
        drawCloseTreeButton(context, mouseX, mouseY);
        renderTreeTabs(context, mouseX, mouseY);
        if (renameField != null) {
            renameField.render(raw, mouseX, mouseY, delta);
        }
        if (searchField != null) {
            searchField.render(raw, mouseX, mouseY, delta);
            if (!searchQuery.isBlank()) {
                String status;
                if (searchMatches.isEmpty()) {
                    status = "0";
                } else if (searchIndex >= 0) {
                    status = (searchIndex + 1) + "/" + searchMatches.size();
                } else {
                    status = Integer.toString(searchMatches.size());
                }
                int tw = font.width(status);
                context.drawTextWithShadow(EmiPort.literal(status), searchFieldX - tw - 5, 8, 0xFFFFFFFF);
            }
        }
        renderStatsPanel(context, mouseX, mouseY, delta);
        renderStatsConfigMenu(context, mouseX, mouseY);
        renderGroupPicker(context, mouseX, mouseY);

        Hover hover = getHoveredStack(mouseX, mouseY);
        if (hover != null && hover.stack != null) {
            List<ClientTooltipComponent> tooltip = Lists.newArrayList();
            tooltip.addAll(hover.stack.getTooltip());
            if (hover.node != null && hover.node.recipe != null) {
                tooltip.add(new RecipeTooltipComponent(hover.node.recipe));
            }
            EmiRenderHelper.drawTooltip(this, context, tooltip, mouseX, mouseY);
        } else if (hover != null && hover.category != null) {
            EmiRenderHelper.drawTooltip(this, context, hover.category.getTooltip(), mouseX, mouseY);
        } else {
            StatHover statHover = getStatsHover(mouseX, mouseY);
            if (statHover != null) {
                statHover.drawTooltip(context, mouseX, mouseY);
            } else if (statsToggleButton.contains(mouseX, mouseY)) {
                List<ClientTooltipComponent> tooltip = Lists.newArrayList();
                tooltip.add(ClientTooltipComponent.create(EmiPort.literal(statsPanelVisible
                    ? PlannerText.tr("bookmark_tree.hide_stats", "Hide stats panel")
                    : PlannerText.tr("bookmark_tree.show_stats", "Show stats panel")).getVisualOrderText()));
                EmiRenderHelper.drawTooltip(this, context, tooltip, mouseX, mouseY);
            } else if (statsConfigButton.contains(mouseX, mouseY)) {
                List<ClientTooltipComponent> tooltip = Lists.newArrayList();
                tooltip.add(ClientTooltipComponent.create(EmiPort.literal(PlannerText.tr("bookmark_tree.choose_sections", "Choose visible stats sections")).getVisualOrderText()));
                EmiRenderHelper.drawTooltip(this, context, tooltip, mouseX, mouseY);
            } else if (saveImageButton.contains(mouseX, mouseY)) {
                List<ClientTooltipComponent> tooltip = Lists.newArrayList();
                tooltip.add(ClientTooltipComponent.create(EmiPort.literal(PlannerText.tr("bookmark_tree.save_image", "Save tree as image")).getVisualOrderText()));
                EmiRenderHelper.drawTooltip(this, context, tooltip, mouseX, mouseY);
            } else if (newTreeButton.contains(mouseX, mouseY)) {
                List<ClientTooltipComponent> tooltip = Lists.newArrayList();
                tooltip.add(ClientTooltipComponent.create(EmiPort.literal(PlannerText.tr("bookmark_tree.open_group", "Open another bookmark group as a crafting tree")).getVisualOrderText()));
                EmiRenderHelper.drawTooltip(this, context, tooltip, mouseX, mouseY);
            } else if (closeTreeButton.contains(mouseX, mouseY)) {
                List<ClientTooltipComponent> tooltip = Lists.newArrayList();
                tooltip.add(ClientTooltipComponent.create(EmiPort.literal(PlannerText.tr("bookmark_tree.close_tree", "Close current crafting tree")).getVisualOrderText()));
                EmiRenderHelper.drawTooltip(this, context, tooltip, mouseX, mouseY);
            } else if (getHoveredTreeTab(mouseX, mouseY) != null) {
                TreeTabHitbox tab = getHoveredTreeTab(mouseX, mouseY);
                List<ClientTooltipComponent> tooltip = Lists.newArrayList();
                tooltip.add(ClientTooltipComponent.create(EmiPort.literal(workspaceDisplayName(tab.index)).getVisualOrderText()));
                tooltip.add(ClientTooltipComponent.create(EmiPort.literal(PlannerText.tr("bookmark_tree.rename_hint", "Right-click to rename")).getVisualOrderText()));
                EmiRenderHelper.drawTooltip(this, context, tooltip, mouseX, mouseY);
            } else if (snapshotButton.contains(mouseX, mouseY)) {
                List<ClientTooltipComponent> tooltip = Lists.newArrayList();
                String text = inventorySnapshot == null
                    ? PlannerText.tr("bookmark_tree.snapshot_recompute", "Recompute tree using player inventory (snapshot)")
                    : PlannerText.tr("bookmark_tree.snapshot_clear", "Using inventory snapshot - click to clear");
                tooltip.add(ClientTooltipComponent.create(EmiPort.literal(text).getVisualOrderText()));
                EmiRenderHelper.drawTooltip(this, context, tooltip, mouseX, mouseY);
            } else if (fitButton.contains(mouseX, mouseY)) {
                List<ClientTooltipComponent> tooltip = Lists.newArrayList();
                tooltip.add(ClientTooltipComponent.create(EmiPort.literal(PlannerText.tr("bookmark_tree.fit_view", "Fit tree to view")).getVisualOrderText()));
                tooltip.add(ClientTooltipComponent.create(EmiPort.literal("[F]").getVisualOrderText()));
                EmiRenderHelper.drawTooltip(this, context, tooltip, mouseX, mouseY);
            } else if (collapseButton.contains(mouseX, mouseY)) {
                List<ClientTooltipComponent> tooltip = Lists.newArrayList();
                tooltip.add(ClientTooltipComponent.create(EmiPort.literal(PlannerText.tr("bookmark_tree.collapse_done", "Collapse items with nothing left to craft")).getVisualOrderText()));
                if (inventorySnapshot == null) {
                    tooltip.add(ClientTooltipComponent.create(EmiPort.literal(PlannerText.tr("bookmark_tree.requires_snapshot", "Requires an inventory snapshot")).getVisualOrderText()));
                }
                EmiRenderHelper.drawTooltip(this, context, tooltip, mouseX, mouseY);
            }
        }
    }

    private double viewCenterY() {
        return (HEADER_HEIGHT + statsPanelTop()) / 2.0;
    }

    private int toTreeX(double screenX) {
        return (int) ((screenX - width / 2.0) / viewScale - offX);
    }

    private int toTreeY(double screenY) {
        return (int) ((screenY - viewCenterY()) / viewScale - offY);
    }


    private int statsPanelTop() {
        if (!statsPanelVisible) {
            return height;
        }
        return Math.max(HEADER_HEIGHT + 1, height - STATS_PANEL_HEIGHT);
    }

    private void renderStatsPanel(EmiDrawContext context, int mouseX, int mouseY, float delta) {
        statHitboxes = new ArrayList<>();
        if (!statsPanelVisible) {
            return;
        }
        int top = statsPanelTop();
        context.fill(0, top, width, height - top, STATS_BACKGROUND_COLOR);
        context.fill(0, top, width, 1, STATS_SEPARATOR_COLOR);

        List<StatsSection> visible = new ArrayList<>();
        for (StatsSection section : StatsSection.values()) {
            if (statsVisibility.getOrDefault(section, false)) {
                visible.add(section);
            }
        }
        if (visible.isEmpty()) {
            context.drawCenteredText(EmiPort.literal(PlannerText.tr("bookmark_tree.no_sections", "No stats sections selected")), width / 2, top + 25, 0xFF9A9AA2);
            return;
        }
        for (int i = 0; i < visible.size(); i++) {
            StatsSection section = visible.get(i);
            int startX = i * width / visible.size();
            int endX = (i + 1) * width / visible.size();
            switch (section) {
                case INGREDIENTS_NEEDED -> renderStatSection(context, section.label() + ":", ingredientNeededStats,
                    startX, endX, top, delta, inventorySnapshot != null);
                case INGREDIENTS_AVAILABLE -> renderStatSection(context, section.label() + ":", ingredientAvailableStats,
                    startX, endX, top, delta, false);
                case CRAFTING_NEEDED -> renderStatSection(context, section.label() + ":", craftingNeededStats,
                    startX, endX, top, delta, false);
                case CRAFTING_AVAILABLE -> renderStatSection(context, section.label() + ":", craftingAvailableStats,
                    startX, endX, top, delta, false);
                case RESULTS -> renderStatSection(context, section.label() + ":", resultStats,
                    startX, endX, top, delta, false);
                case REMAINDERS -> renderStatSection(context, section.label() + ":", remainderStats,
                    startX, endX, top, delta, false);
                case HANDLERS -> renderHandlerSection(context, section.label() + ":", handlerStats,
                    startX, endX, top, delta);
            }
        }
    }

    private void renderStatSection(EmiDrawContext context, String label, List<StatEntry> entries,
            int startX, int endX, int top, float delta, boolean markMissing) {
        int sectionWidth = Math.max(1, endX - startX);
        context.drawCenteredText(EmiPort.literal(label), startX + sectionWidth / 2, top + 5, STATS_LABEL_COLOR);
        int available = Math.max(1, sectionWidth - 8);
        int perRow = Math.max(1, available / STATS_ICON_STEP);
        int capacity = perRow * 2;
        int count = Math.min(entries.size(), capacity);
        for (int i = 0; i < count; i++) {
            int row = i / perRow;
            int col = i % perRow;
            int x = startX + 4 + col * STATS_ICON_STEP;
            int y = top + 18 + row * 20;
            StatEntry entry = entries.get(i);
            entry.ingredient.render(context.raw(), x, y, delta, -1 ^ (EmiIngredient.RENDER_AMOUNT | EmiIngredient.RENDER_REMAINDER));
            EmiRenderHelper.renderAmount(context, x, y, entry.getAmountText());
            if (markMissing && getMissingAmount(entry.ingredient) > 0L) {
                drawBorder(context, x - 1, y - 1, STATS_ICON_SIZE + 2, STATS_ICON_SIZE + 2, MISSING_BORDER_COLOR);
            }
            statHitboxes.add(new StatHitbox(new Bounds(x, y, STATS_ICON_SIZE, STATS_ICON_SIZE), new StatHover(entry)));
        }
        if (entries.size() > capacity) {
            String more = "+" + (entries.size() - capacity);
            context.drawTextWithShadow(EmiPort.literal(more), Math.max(startX + 4, endX - 24), top + 45, 0xFFB8B8C0);
        }
    }

    private void renderHandlerSection(EmiDrawContext context, String label, List<HandlerStat> entries,
            int startX, int endX, int top, float delta) {
        int sectionWidth = Math.max(1, endX - startX);
        context.drawCenteredText(EmiPort.literal(label), startX + sectionWidth / 2, top + 5, STATS_LABEL_COLOR);
        int available = Math.max(1, sectionWidth - 8);
        int perRow = Math.max(1, available / STATS_ICON_STEP);
        int capacity = perRow * 2;
        int count = Math.min(entries.size(), capacity);
        for (int i = 0; i < count; i++) {
            int row = i / perRow;
            int col = i % perRow;
            int x = startX + 4 + col * STATS_ICON_STEP;
            int y = top + 18 + row * 20;
            HandlerStat entry = entries.get(i);
            entry.category.renderSimplified(context.raw(), x, y, delta);
            String amount = formatHandlerCount(entry.crafts);
            int tw = font.width(amount);
            context.drawTextWithShadow(EmiPort.literal(amount), x + 17 - tw, y + 9, 0xFFFFFFFF);
            statHitboxes.add(new StatHitbox(new Bounds(x, y, STATS_ICON_SIZE, STATS_ICON_SIZE), new StatHover(entry)));
        }
        if (entries.size() > capacity) {
            String more = "+" + (entries.size() - capacity);
            context.drawTextWithShadow(EmiPort.literal(more), Math.max(startX + 4, endX - 24), top + 45, 0xFFB8B8C0);
        }
    }

    private String formatHandlerCount(long value) {
        return formatCompactAmount(value);
    }

    private String formatCompactAmount(long value) {
        if (value < 0L) {
            if (value == Long.MIN_VALUE) {
                return "-9.2E";
            }
            return "-" + formatCompactAmount(-value);
        }
        if (value < 1_000L) {
            return Long.toString(value);
        }
        String[] suffixes = { "", "K", "M", "G", "T", "P", "E" };
        long divisor = 1L;
        int unit = 0;
        while (unit < suffixes.length - 1 && value / divisor >= 1_000L) {
            if (divisor > Long.MAX_VALUE / 1_000L) {
                break;
            }
            divisor *= 1_000L;
            unit++;
        }
        long whole = value / divisor;
        long remainder = value % divisor;
        if (whole < 10L && remainder > 0L) {
            long tenth = Math.round((double) remainder * 10.0D / (double) divisor);
            if (tenth >= 10L) {
                whole++;
                tenth = 0L;
            }
            if (tenth > 0L) {
                return whole + "." + tenth + suffixes[unit];
            }
        }
        return whole + suffixes[unit];
    }

    private Component getCompactAmountText(long amount, boolean approximate) {
        Component text = EmiPort.literal(formatCompactAmount(Math.max(0L, amount)));
        return approximate ? EmiPort.append(EmiPort.literal("≈"), text) : text;
    }

    private StatHover getStatsHover(int mouseX, int mouseY) {
        if (mouseY < statsPanelTop()) {
            return null;
        }
        for (int i = statHitboxes.size() - 1; i >= 0; i--) {
            StatHitbox hitbox = statHitboxes.get(i);
            if (hitbox.bounds.contains(mouseX, mouseY)) {
                return hitbox.hover;
            }
        }
        return null;
    }


    private void drawStatsToggleButton(EmiDrawContext context, int mouseX, int mouseY) {
        boolean hovered = statsToggleButton.contains(mouseX, mouseY);
        int x = statsToggleButton.x();
        int y = statsToggleButton.y();
        int w = statsToggleButton.width();
        int h = statsToggleButton.height();
        int base = statsPanelVisible ? 0xFF365248 : BUTTON_COLOR;
        context.fill(x, y, w, h, hovered ? BUTTON_HOVER_COLOR : base);
        drawBorder(context, x, y, w, h, hovered ? 0xFFE0E0E0 : 0xFF707070);
        int c = 0xFFFFFFFF;
        context.fill(x + 4, y + 11, 2, 3, c);
        context.fill(x + 8, y + 7, 2, 7, c);
        context.fill(x + 12, y + 4, 2, 10, c);
    }

    private void drawStatsConfigButton(EmiDrawContext context, int mouseX, int mouseY) {
        boolean hovered = statsConfigButton.contains(mouseX, mouseY);
        int x = statsConfigButton.x();
        int y = statsConfigButton.y();
        int w = statsConfigButton.width();
        int h = statsConfigButton.height();
        int base = statsConfigOpen ? 0xFF365248 : BUTTON_COLOR;
        context.fill(x, y, w, h, hovered ? BUTTON_HOVER_COLOR : base);
        drawBorder(context, x, y, w, h, hovered ? 0xFFE0E0E0 : 0xFF707070);
        int c = 0xFFFFFFFF;
        context.fill(x + 7, y + 7, 4, 4, c);
        drawBorder(context, x + 5, y + 5, 8, 8, c);
        context.fill(x + 8, y + 3, 2, 2, c);
        context.fill(x + 8, y + 13, 2, 2, c);
        context.fill(x + 3, y + 8, 2, 2, c);
        context.fill(x + 13, y + 8, 2, 2, c);
    }

    private void renderStatsConfigMenu(EmiDrawContext context, int mouseX, int mouseY) {
        statsMenuHitboxes = new ArrayList<>();
        if (!statsConfigOpen) {
            return;
        }
        int x = statsConfigButton.x();
        int y = HEADER_HEIGHT + 2;
        int rowHeight = 18;
        int menuWidth = 142;
        int menuHeight = StatsSection.values().length * rowHeight + 4;
        context.fill(x, y, menuWidth, menuHeight, 0xF0202028);
        drawBorder(context, x, y, menuWidth, menuHeight, 0xFF7B7B84);
        int rowY = y + 2;
        for (StatsSection section : StatsSection.values()) {
            Bounds bounds = new Bounds(x + 2, rowY, menuWidth - 4, rowHeight);
            boolean hovered = bounds.contains(mouseX, mouseY);
            if (hovered) {
                context.fill(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 0xFF3D3D48);
            }
            boolean enabled = statsVisibility.getOrDefault(section, false);
            int boxColor = enabled ? 0xFF6BBF7B : 0xFF595961;
            drawBorder(context, x + 7, rowY + 5, 8, 8, boxColor);
            if (enabled) {
                context.fill(x + 9, rowY + 7, 4, 4, boxColor);
            }
            context.drawTextWithShadow(EmiPort.literal(section.label()), x + 20, rowY + 5, 0xFFFFFFFF);
            statsMenuHitboxes.add(new StatsMenuHitbox(bounds, section));
            rowY += rowHeight;
        }
    }

    private boolean isOverStatsConfig(int mouseX, int mouseY) {
        if (!statsConfigOpen) {
            return false;
        }
        int x = statsConfigButton.x();
        int y = HEADER_HEIGHT + 2;
        int menuWidth = 142;
        int menuHeight = StatsSection.values().length * 18 + 4;
        return mouseX >= x && mouseX < x + menuWidth && mouseY >= y && mouseY < y + menuHeight;
    }

    private void drawSaveImageButton(EmiDrawContext context, int mouseX, int mouseY) {
        boolean hovered = saveImageButton.contains(mouseX, mouseY);
        int x = saveImageButton.x();
        int y = saveImageButton.y();
        int w = saveImageButton.width();
        int h = saveImageButton.height();
        context.fill(x, y, w, h, hovered ? BUTTON_HOVER_COLOR : BUTTON_COLOR);
        drawBorder(context, x, y, w, h, hovered ? 0xFFE0E0E0 : 0xFF707070);
        int c = 0xFFFFFFFF;
        drawBorder(context, x + 4, y + 3, 10, 12, c);
        context.fill(x + 6, y + 5, 6, 3, c);
        context.fill(x + 6, y + 11, 6, 2, c);
    }

    private void saveTreeImage() {
        if (nodes.isEmpty()) {
            return;
        }
        int treeWidth = Math.max(1, contentMaxX - contentMinX + SCREENSHOT_PADDING * 2);
        int treeHeight = Math.max(1, contentMaxY - contentMinY + SCREENSHOT_PADDING * 2);
        int statsHeight = statsPanelVisible ? STATS_PANEL_HEIGHT : 0;

        double scale = 1.0;
        int maxTreeHeight = Math.max(1, SCREENSHOT_MAX_DIMENSION - statsHeight);
        if (treeWidth > SCREENSHOT_MAX_DIMENSION || treeHeight > maxTreeHeight) {
            scale = Math.min((double) SCREENSHOT_MAX_DIMENSION / treeWidth, (double) maxTreeHeight / treeHeight);
        }
        int scaledTreeWidth = Math.max(1, (int) Math.ceil(treeWidth * scale));
        int scaledTreeHeight = Math.max(1, (int) Math.ceil(treeHeight * scale));
        int imageWidth = scaledTreeWidth;
        if (statsPanelVisible) {
            imageWidth = Math.max(imageWidth, SCREENSHOT_STATS_MIN_WIDTH);
        }
        imageWidth = Math.min(SCREENSHOT_MAX_DIMENSION, imageWidth);
        int imageHeight = Math.min(SCREENSHOT_MAX_DIMENSION, scaledTreeHeight + statsHeight);
        double finalScale = scale;
        int finalWidth = imageWidth;
        int finalHeight = imageHeight;
        EmiScreenshotRecorder.saveScreenshot("dev/emi/emi/crafting-trees/bookmark-tree", finalWidth, finalHeight,
            () -> renderTreeScreenshot(finalWidth, finalHeight, finalScale));
    }

    private void renderTreeScreenshot(int imageWidth, int imageHeight, double screenshotScale) {
        Minecraft minecraft = Minecraft.getInstance();
        GuiGraphics raw = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
        EmiDrawContext context = EmiDrawContext.wrap(raw);
        context.fill(0, 0, imageWidth, imageHeight, BACKGROUND_COLOR);

        int statsHeight = statsPanelVisible ? STATS_PANEL_HEIGHT : 0;
        int treeAreaHeight = imageHeight - statsHeight;
        double contentCenterX = (contentMinX + contentMaxX) / 2.0;
        double treeCenterY = (contentMinY + contentMaxY) / 2.0;
        PoseStack view = RenderSystem.getModelViewStack();
        view.pushPose();
        view.translate(imageWidth / 2.0, treeAreaHeight / 2.0, 0);
        view.scale((float) screenshotScale, (float) screenshotScale, 1);
        view.translate(-contentCenterX, -treeCenterY, 0);
        EmiPort.applyModelViewMatrix();

        renderingScreenshot = true;
        try {
            BATCHER.begin(0, 0, 0);
            for (Node node : nodes) {
                node.render(context, Integer.MIN_VALUE, Integer.MIN_VALUE, 0);
            }
            BATCHER.draw();
            for (Node node : nodes) {
                node.renderOverlay(context, Integer.MIN_VALUE, Integer.MIN_VALUE, 0);
            }
        } finally {
            renderingScreenshot = false;
            view.popPose();
            EmiPort.applyModelViewMatrix();
        }

        if (statsPanelVisible) {
            int oldWidth = width;
            int oldHeight = height;
            List<StatHitbox> oldHitboxes = statHitboxes;
            try {
                width = imageWidth;
                height = imageHeight;
                renderStatsPanel(context, -10000, -10000, 0);
            } finally {
                width = oldWidth;
                height = oldHeight;
                statHitboxes = oldHitboxes;
            }
        }
    }

    private void drawSnapshotButton(EmiDrawContext context, int mouseX, int mouseY) {
        boolean hovered = snapshotButton.contains(mouseX, mouseY);
        int x = snapshotButton.x();
        int y = snapshotButton.y();
        int w = snapshotButton.width();
        int h = snapshotButton.height();
        int base = inventorySnapshot != null ? 0xFF365248 : BUTTON_COLOR;
        context.fill(x, y, w, h, hovered ? BUTTON_HOVER_COLOR : base);
        drawBorder(context, x, y, w, h, hovered ? 0xFFE0E0E0 : 0xFF707070);
        int c = 0xFFFFFFFF;
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 3; col++) {
                int sx = x + 4 + col * 4;
                int sy = y + 5 + row * 4;
                context.fill(sx, sy, 3, 3, c);
            }
        }
    }

    private void drawCollapseButton(EmiDrawContext context, int mouseX, int mouseY) {
        boolean enabled = inventorySnapshot != null;
        boolean hovered = collapseButton.contains(mouseX, mouseY);
        int x = collapseButton.x();
        int y = collapseButton.y();
        int w = collapseButton.width();
        int h = collapseButton.height();
        int base = collapseCompleted && enabled ? 0xFF365248 : BUTTON_COLOR;
        context.fill(x, y, w, h, hovered && enabled ? BUTTON_HOVER_COLOR : base);
        drawBorder(context, x, y, w, h, enabled ? (hovered ? 0xFFE0E0E0 : 0xFF707070) : 0xFF505057);
        int c = enabled ? 0xFFFFFFFF : 0xFF77777F;
        drawLine(context, x + 4, y + 7, x + 13, y + 7, c);
        drawLine(context, x + 6, y + 10, x + 11, y + 10, c);
        if (!collapseCompleted) {
            drawLine(context, x + 8, y + 4, x + 8, y + 13, c);
        }
    }

    private void drawFitButton(EmiDrawContext context, int mouseX, int mouseY) {
        boolean hovered = fitButton.contains(mouseX, mouseY);
        int x = fitButton.x();
        int y = fitButton.y();
        int w = fitButton.width();
        int h = fitButton.height();
        context.fill(x, y, w, h, hovered ? BUTTON_HOVER_COLOR : BUTTON_COLOR);
        drawBorder(context, x, y, w, h, hovered ? 0xFFE0E0E0 : 0xFF707070);
        int c = 0xFFFFFFFF;
        drawLine(context, x + 4, y + 4, x + 8, y + 4, c);
        drawLine(context, x + 4, y + 4, x + 4, y + 8, c);
        drawLine(context, x + w - 9, y + 4, x + w - 5, y + 4, c);
        drawLine(context, x + w - 5, y + 4, x + w - 5, y + 8, c);
        drawLine(context, x + 4, y + h - 5, x + 8, y + h - 5, c);
        drawLine(context, x + 4, y + h - 9, x + 4, y + h - 5, c);
        drawLine(context, x + w - 9, y + h - 5, x + w - 5, y + h - 5, c);
        drawLine(context, x + w - 5, y + h - 9, x + w - 5, y + h - 5, c);
    }

    private void drawNewTreeButton(EmiDrawContext context, int mouseX, int mouseY) {
        boolean hovered = newTreeButton.contains(mouseX, mouseY);
        int x = newTreeButton.x();
        int y = newTreeButton.y();
        context.fill(x, y, newTreeButton.width(), newTreeButton.height(), hovered ? BUTTON_HOVER_COLOR : BUTTON_COLOR);
        drawBorder(context, x, y, newTreeButton.width(), newTreeButton.height(), hovered ? 0xFFE0E0E0 : 0xFF707070);
        context.drawCenteredText(EmiPort.literal("+"), x + newTreeButton.width() / 2, y + 5, 0xFFFFFFFF);
    }

    private void drawCloseTreeButton(EmiDrawContext context, int mouseX, int mouseY) {
        boolean hovered = closeTreeButton.contains(mouseX, mouseY);
        int x = closeTreeButton.x();
        int y = closeTreeButton.y();
        context.fill(x, y, closeTreeButton.width(), closeTreeButton.height(), hovered ? BUTTON_HOVER_COLOR : BUTTON_COLOR);
        drawBorder(context, x, y, closeTreeButton.width(), closeTreeButton.height(), hovered ? 0xFFE0E0E0 : 0xFF707070);
        context.drawCenteredText(EmiPort.literal("x"), x + closeTreeButton.width() / 2, y + 5, 0xFFFFFFFF);
    }

    private String workspaceDisplayName(int index) {
        if (index < 0 || index >= WORKSPACES.size()) {
            return PlannerText.tr("bookmark_tree.tree", "Tree");
        }
        String custom = WORKSPACES.get(index).name;
        return custom == null || custom.isBlank()
            ? PlannerText.tr("bookmark_tree.tree_number", "Tree %s", index + 1)
            : custom;
    }

    private void startWorkspaceRename(TreeTabHitbox tab) {
        if (tab.index < 0 || tab.index >= WORKSPACES.size()) {
            return;
        }
        renameWorkspaceIndex = tab.index;
        renameField = new EditBox(minecraft.font, tab.bounds.x() + 2, tab.bounds.y() + 2,
            Math.max(20, tab.bounds.width() - 4), tab.bounds.height() - 4, EmiPort.literal(PlannerText.tr("bookmark_tree.tree_name", "Tree name")));
        renameField.setMaxLength(48);
        renameField.setValue(workspaceDisplayName(tab.index));
        EmiPort.focus(renameField, true);
    }

    private void commitWorkspaceRename() {
        if (renameField == null || renameWorkspaceIndex < 0 || renameWorkspaceIndex >= WORKSPACES.size()) {
            cancelWorkspaceRename();
            return;
        }
        String name = renameField.getValue().trim();
        WORKSPACES.get(renameWorkspaceIndex).name = name.isEmpty() ? null : name;
        cancelWorkspaceRename();
        saveWorkspaceState();
        syncPersistentWorkspaces();
        EmiBookmarkTreePersistence.saveToDisk();
    }

    private void cancelWorkspaceRename() {
        if (renameField != null) {
            EmiPort.focus(renameField, false);
        }
        renameField = null;
        renameWorkspaceIndex = -1;
    }

    private void renderTreeTabs(EmiDrawContext context, int mouseX, int mouseY) {
        treeTabHitboxes = new ArrayList<>();
        if (WORKSPACES.isEmpty()) {
            return;
        }
        int startX = 180;
        int available = Math.max(0, searchFieldX - startX - 6);
        int maxVisible = Math.max(1, available / TREE_TAB_WIDTH);
        int first = 0;
        if (WORKSPACES.size() > maxVisible) {
            first = Mth.clamp(activeWorkspaceIndex - maxVisible / 2, 0, WORKSPACES.size() - maxVisible);
        }
        int last = Math.min(WORKSPACES.size(), first + maxVisible);
        for (int i = first; i < last; i++) {
            int x = startX + (i - first) * TREE_TAB_WIDTH;
            Bounds bounds = new Bounds(x, 3, TREE_TAB_WIDTH - 2, 18);
            boolean hovered = bounds.contains(mouseX, mouseY);
            boolean active = i == activeWorkspaceIndex;
            int base = active ? 0xFF365248 : BUTTON_COLOR;
            context.fill(bounds.x(), bounds.y(), bounds.width(), bounds.height(), hovered ? BUTTON_HOVER_COLOR : base);
            drawBorder(context, bounds.x(), bounds.y(), bounds.width(), bounds.height(), hovered ? 0xFFE0E0E0 : 0xFF707070);
            if (renameWorkspaceIndex != i || renameField == null) {
                String label = font.plainSubstrByWidth(workspaceDisplayName(i), Math.max(10, bounds.width() - 8));
                context.drawCenteredText(EmiPort.literal(label), bounds.x() + bounds.width() / 2, bounds.y() + 5, 0xFFFFFFFF);
            }
            treeTabHitboxes.add(new TreeTabHitbox(bounds, i));
        }
    }

    private TreeTabHitbox getHoveredTreeTab(int mouseX, int mouseY) {
        for (TreeTabHitbox tab : treeTabHitboxes) {
            if (tab.bounds.contains(mouseX, mouseY)) {
                return tab;
            }
        }
        return null;
    }

    private void renderGroupPicker(EmiDrawContext context, int mouseX, int mouseY) {
        groupPickerHitboxes = new ArrayList<>();
        if (!groupPickerOpen) {
            return;
        }
        List<EmiFavoriteGroups.Group> groups = EmiFavoriteGroups.groups();
        int availableRows = Math.max(1, (statsPanelTop() - HEADER_HEIGHT - 8) / GROUP_PICKER_ROW_HEIGHT);
        int maxScroll = Math.max(0, groups.size() - availableRows);
        groupPickerScroll = Mth.clamp(groupPickerScroll, 0, maxScroll);
        int rows = Math.min(availableRows, groups.size() - groupPickerScroll);
        int panelX = Math.min(newTreeButton.x(), Math.max(4, width - GROUP_PICKER_WIDTH - 4));
        int panelY = HEADER_HEIGHT;
        int panelHeight = Math.max(GROUP_PICKER_ROW_HEIGHT, rows * GROUP_PICKER_ROW_HEIGHT + 4);
        context.fill(panelX, panelY, GROUP_PICKER_WIDTH, panelHeight, 0xF019191F);
        drawBorder(context, panelX, panelY, GROUP_PICKER_WIDTH, panelHeight, 0xFF77777F);
        for (int row = 0; row < rows; row++) {
            int groupIndex = groupPickerScroll + row;
            EmiFavoriteGroups.Group candidate = groups.get(groupIndex);
            int y = panelY + 2 + row * GROUP_PICKER_ROW_HEIGHT;
            Bounds bounds = new Bounds(panelX + 2, y, GROUP_PICKER_WIDTH - 4, GROUP_PICKER_ROW_HEIGHT - 1);
            boolean hovered = bounds.contains(mouseX, mouseY);
            if (hovered) {
                context.fill(bounds.x(), bounds.y(), bounds.width(), bounds.height(), BUTTON_HOVER_COLOR);
            }
            String label = groupDisplayName(candidate);
            int existing = workspaceIndex(candidate);
            if (existing >= 0) {
                label = workspaceDisplayName(existing) + " - " + label;
            }
            if (label.length() > 35) {
                label = label.substring(0, 32) + "...";
            }
            context.drawTextWithShadow(EmiPort.literal(label), bounds.x() + 4, bounds.y() + 5, 0xFFFFFFFF);
            groupPickerHitboxes.add(new GroupPickerHitbox(bounds, candidate));
        }
    }

    private String groupDisplayName(EmiFavoriteGroups.Group target) {
        for (EmiFavorite favorite : target.members()) {
            if (favorite.getRole() == EmiFavorite.Role.RESULT && !favorite.getStack().getEmiStacks().isEmpty()) {
                return favorite.getStack().getEmiStacks().get(0).getName().getString();
            }
        }
        for (EmiFavorite favorite : target.members()) {
            if (!favorite.getStack().getEmiStacks().isEmpty()) {
                return favorite.getStack().getEmiStacks().get(0).getName().getString();
            }
        }
        return PlannerText.tr("bookmark_tree.bookmark_group", "Bookmark Group");
    }

    private static int workspaceIndex(EmiFavoriteGroups.Group target) {
        for (int i = 0; i < WORKSPACES.size(); i++) {
            if (WORKSPACES.get(i).group == target) {
                return i;
            }
        }
        return -1;
    }

    private void switchWorkspace(int index) {
        if (index < 0 || index >= WORKSPACES.size() || index == activeWorkspaceIndex) {
            groupPickerOpen = false;
            return;
        }
        saveWorkspaceState();
        activeWorkspaceIndex = index;
        workspace = WORKSPACES.get(index);
        loadWorkspaceState();
        groupPickerOpen = false;
        int sourceSignature = workspaceSourceSignature(group);
        if (!workspace.built || workspace.sourceSignature != sourceSignature) {
            buildTrees();
            workspace.built = true;
            workspace.sourceSignature = sourceSignature;
        }
        if (inventorySnapshot != null) {
            applyInventorySnapshot();
        }
        recalculateForest();
        if (searchField != null) {
            searchField.setValue(searchQuery);
            searchField.setSuggestion(searchQuery.isEmpty() ? PlannerText.tr("bookmark_tree.search_hint", "Search...") : "");
        }
        if (!workspace.viewInitialized) {
            fitToView();
        }
        saveWorkspaceState();
        syncPersistentWorkspaces();
    }

    private void addOrSwitchWorkspace(EmiFavoriteGroups.Group target) {
        int existing = workspaceIndex(target);
        if (existing >= 0) {
            switchWorkspace(existing);
            return;
        }
        saveWorkspaceState();
        TreeWorkspace created = new TreeWorkspace(target);
        WORKSPACES.add(created);
        activeWorkspaceIndex = WORKSPACES.size() - 1;
        workspace = created;
        loadWorkspaceState();
        groupPickerOpen = false;
        buildTrees();
        workspace.built = true;
        workspace.sourceSignature = workspaceSourceSignature(group);
        recalculateForest();
        fitToView();
        if (searchField != null) {
            searchField.setValue("");
            searchField.setSuggestion(PlannerText.tr("bookmark_tree.search_hint", "Search..."));
        }
        persistWorkspaces();
    }

    private void closeCurrentWorkspace() {
        if (activeWorkspaceIndex < 0 || activeWorkspaceIndex >= WORKSPACES.size()) {
            return;
        }
        WORKSPACES.remove(activeWorkspaceIndex);
        if (WORKSPACES.isEmpty()) {
            activeWorkspaceIndex = -1;
            syncPersistentWorkspaces();
            EmiBookmarkTreePersistence.saveToDisk();
            Minecraft.getInstance().setScreen(old);
            return;
        }
        activeWorkspaceIndex = Math.min(activeWorkspaceIndex, WORKSPACES.size() - 1);
        workspace = WORKSPACES.get(activeWorkspaceIndex);
        loadWorkspaceState();
        groupPickerOpen = false;
        int sourceSignature = workspaceSourceSignature(group);
        if (!workspace.built || workspace.sourceSignature != sourceSignature) {
            buildTrees();
            workspace.built = true;
            workspace.sourceSignature = sourceSignature;
        }
        if (inventorySnapshot != null) {
            applyInventorySnapshot();
        }
        recalculateForest();
        if (searchField != null) {
            searchField.setValue(searchQuery);
            searchField.setSuggestion(searchQuery.isEmpty() ? PlannerText.tr("bookmark_tree.search_hint", "Search...") : "");
        }
        if (!workspace.viewInitialized) {
            fitToView();
        }
        persistWorkspaces();
    }

    private Hover getHoveredStack(int mouseX, int mouseY) {
        if (groupPickerOpen || isOverStatsConfig(mouseX, mouseY) || mouseY < HEADER_HEIGHT || mouseY >= statsPanelTop()) {
            return null;
        }
        int mx = toTreeX(mouseX);
        int my = toTreeY(mouseY);
        for (int i = nodes.size() - 1; i >= 0; i--) {
            Hover hover = nodes.get(i).getHover(mx, my);
            if (hover != null) {
                return hover;
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX;
        int my = (int) mouseY;
        if (renameField != null && renameField.mouseClicked(mouseX, mouseY, button)) {
            EmiPort.focus(renameField, true);
            return true;
        }
        if (renameField != null && button == 0) {
            commitWorkspaceRename();
        }
        if (button == 0 && newTreeButton.contains(mx, my)) {
            groupPickerOpen = !groupPickerOpen;
            statsConfigOpen = false;
            return true;
        }
        if (button == 0 && closeTreeButton.contains(mx, my)) {
            closeCurrentWorkspace();
            return true;
        }
        for (TreeTabHitbox tab : treeTabHitboxes) {
            if (tab.bounds.contains(mx, my)) {
                if (button == 1) {
                    startWorkspaceRename(tab);
                    return true;
                }
                if (button == 0) {
                    switchWorkspace(tab.index);
                    return true;
                }
            }
        }
        if (groupPickerOpen) {
            for (GroupPickerHitbox hitbox : groupPickerHitboxes) {
                if (button == 0 && hitbox.bounds.contains(mx, my)) {
                    addOrSwitchWorkspace(hitbox.group);
                    return true;
                }
            }
            groupPickerOpen = false;
            return true;
        }
        if (searchField != null && searchField.mouseClicked(mouseX, mouseY, button)) {
            EmiPort.focus(searchField, true);
            return true;
        }
        if (searchField != null && searchField.isFocused()) {
            EmiPort.focus(searchField, false);
        }
        if (button == 0 && saveImageButton.contains(mx, my)) {
            saveTreeImage();
            return true;
        }
        if (button == 0 && statsToggleButton.contains(mx, my)) {
            statsPanelVisible = !statsPanelVisible;
            fitToView();
            return true;
        }
        if (button == 0 && statsConfigButton.contains(mx, my)) {
            statsConfigOpen = !statsConfigOpen;
            return true;
        }
        if (statsConfigOpen) {
            for (StatsMenuHitbox hitbox : statsMenuHitboxes) {
                if (button == 0 && hitbox.bounds.contains(mx, my)) {
                    boolean enabled = statsVisibility.getOrDefault(hitbox.section, false);
                    statsVisibility.put(hitbox.section, !enabled);
                    return true;
                }
            }
            if (isOverStatsConfig(mx, my)) {
                return true;
            }
            statsConfigOpen = false;
        }
        if (button == 0 && snapshotButton.contains(mx, my)) {
            if (inventorySnapshot == null) {
                captureInventorySnapshot();
            } else {
                clearInventorySnapshot();
            }
            return true;
        }
        if (button == 0 && fitButton.contains((int) mouseX, (int) mouseY)) {
            fitToView();
            return true;
        }
        if (button == 0 && collapseButton.contains((int) mouseX, (int) mouseY) && inventorySnapshot != null) {
            collapseCompleted = !collapseCompleted;
            recalculateForest();
            fitToView();
            return true;
        }
        StatHover statHover = getStatsHover((int) mouseX, (int) mouseY);
        if (button == 0 && statHover != null && statHover.entry != null) {
            saveWorkspaceState();
            Minecraft.getInstance().setScreen(old);
            EmiApi.displayRecipes(statHover.entry.ingredient);
            return true;
        }
        Hover hover = getHoveredStack((int) mouseX, (int) mouseY);
        if (hover != null && hover.node != null) {
            if (button == 1 && hover.node.recipe != null && hover.node.children != null && !hover.node.children.isEmpty()
                    && !(hover.node.recipe instanceof EmiResolutionRecipe)) {
                hover.node.state = hover.node.state == FoldState.EXPANDED ? FoldState.COLLAPSED : FoldState.EXPANDED;
                recalculateForest();
                return true;
            }
            if (button == 0 && hover.stack != null && !hover.stack.isEmpty()) {
                saveWorkspaceState();
                Minecraft.getInstance().setScreen(old);
                if (hover.node.recipe != null) {
                    EmiApi.displayRecipe(hover.node.recipe);
                } else {
                    EmiApi.displayRecipes(hover.stack);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (groupPickerOpen) {
            int availableRows = Math.max(1, (statsPanelTop() - HEADER_HEIGHT - 8) / GROUP_PICKER_ROW_HEIGHT);
            int maxScroll = Math.max(0, EmiFavoriteGroups.groups().size() - availableRows);
            if (maxScroll > 0 && amount != 0) {
                groupPickerScroll = Mth.clamp(groupPickerScroll + (amount < 0 ? 1 : -1), 0, maxScroll);
            }
            return true;
        }
        if (amount == 0) {
            return true;
        }
        float oldScale = viewScale;
        viewScale = Mth.clamp((float) (viewScale * Math.pow(1.12, amount)), 0.10f, 3.0f);
        if (oldScale != viewScale) {
            double worldX = (mouseX - width / 2.0) / oldScale - offX;
            double worldY = (mouseY - viewCenterY()) / oldScale - offY;
            offX = (mouseX - width / 2.0) / viewScale - worldX;
            offY = (mouseY - viewCenterY()) / viewScale - worldY;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 || button == 2) {
            offX += deltaX / viewScale;
            offY += deltaY / viewScale;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (renameField != null && renameField.isFocused()) {
            renameField.charTyped(chr, modifiers);
            return true;
        }
        if (searchField != null && searchField.isFocused()) {
            searchField.charTyped(chr, modifiers);
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (renameField != null && renameField.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                cancelWorkspaceRename();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                commitWorkspaceRename();
                return true;
            }
            renameField.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (searchField != null && searchField.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                EmiPort.focus(searchField, false);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                jumpSearch((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1);
                return true;
            }
            searchField.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && searchField != null) {
            EmiPort.focus(searchField, true);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && groupPickerOpen) {
            groupPickerOpen = false;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F) {
            fitToView();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (renameField != null) {
            commitWorkspaceRename();
        }
        persistWorkspaces();
        Minecraft.getInstance().setScreen(old);
    }

    @Override
    public void removed() {
        if (renameField != null) {
            commitWorkspaceRename();
        }
        persistWorkspaces();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static void drawLine(EmiDrawContext context, int x1, int y1, int x2, int y2) {
        drawLine(context, x1, y1, x2, y2, LINE_COLOR);
    }

    private static void drawLine(EmiDrawContext context, int x1, int y1, int x2, int y2, int color) {
        if (x2 < x1) {
            drawLine(context, x2, y1, x1, y2, color);
            return;
        }
        if (y2 < y1) {
            drawLine(context, x1, y2, x2, y1, color);
            return;
        }
        context.fill(x1, y1, x2 - x1 + 1, y2 - y1 + 1, color);
    }

    private static void drawBorder(EmiDrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x, y, width, 1, color);
        context.fill(x, y + height - 1, width, 1, color);
        context.fill(x, y, 1, height, color);
        context.fill(x + width - 1, y, 1, height, color);
    }

    private static class BookmarkMaterialTree extends MaterialTree {
        private BookmarkMaterialTree(EmiRecipe recipe) {
            super(recipe);
        }

        @Override
        public EmiRecipe getRecipe(EmiIngredient stack) {
            if (resolutions.containsKey(stack)) {
                return resolutions.get(stack);
            }
            return BoM.getRecipe(stack);
        }
    }


    private static class TreeWorkspace {
        private final EmiFavoriteGroups.Group group;
        private String name;
        private final List<RootTree> roots = new ArrayList<>();
        private final Map<StatsSection, Boolean> statsVisibility = new LinkedHashMap<>();
        private EmiPlayerInventory inventorySnapshot;
        private EmiPlayerInventory snapshotRemaining;
        private Map<EmiIngredient, Long> snapshotMissing = Map.of();
        private boolean collapseCompleted;
        private boolean statsPanelVisible = true;
        private float viewScale = 1f;
        private double offX;
        private double offY;
        private boolean viewInitialized;
        private String searchQuery = "";
        private int searchIndex = -1;
        private boolean built;
        private int sourceSignature;

        private TreeWorkspace(EmiFavoriteGroups.Group group) {
            this.group = group;
            statsVisibility.put(StatsSection.INGREDIENTS_NEEDED, true);
            statsVisibility.put(StatsSection.INGREDIENTS_AVAILABLE, false);
            statsVisibility.put(StatsSection.CRAFTING_NEEDED, true);
            statsVisibility.put(StatsSection.CRAFTING_AVAILABLE, false);
            statsVisibility.put(StatsSection.RESULTS, true);
            statsVisibility.put(StatsSection.REMAINDERS, false);
            statsVisibility.put(StatsSection.HANDLERS, true);
        }
    }

    private record TreeTabHitbox(Bounds bounds, int index) {
    }

    private record GroupPickerHitbox(Bounds bounds, EmiFavoriteGroups.Group group) {
    }

    private enum StatsSection {
        INGREDIENTS_NEEDED("bookmark_tree.section.ingredients_needed", "Ingredients Needed"),
        INGREDIENTS_AVAILABLE("bookmark_tree.section.ingredients_available", "Ingredients Available"),
        CRAFTING_NEEDED("bookmark_tree.section.crafting_needed", "Crafting Needed"),
        CRAFTING_AVAILABLE("bookmark_tree.section.crafting_available", "Crafting Available"),
        RESULTS("bookmark_tree.section.results", "Results"),
        REMAINDERS("bookmark_tree.section.remainders", "Remainders"),
        HANDLERS("bookmark_tree.section.handlers", "Handlers");

        private final String key;
        private final String english;

        StatsSection(String key, String english) {
            this.key = key;
            this.english = english;
        }

        private String label() {
            return PlannerText.tr(key, english);
        }
    }

    private record StatsMenuHitbox(Bounds bounds, StatsSection section) {
    }

    private static class StatAccumulator {
        private final EmiIngredient ingredient;
        private long amount;
        private boolean approximate;

        private StatAccumulator(EmiIngredient ingredient, long amount, boolean approximate) {
            this.ingredient = ingredient;
            this.amount = amount;
            this.approximate = approximate;
        }
    }

    private class StatEntry {
        private final EmiIngredient ingredient;
        private final long amount;
        private final boolean approximate;

        private StatEntry(EmiIngredient ingredient, long amount, boolean approximate) {
            this.ingredient = ingredient;
            this.amount = amount;
            this.approximate = approximate;
        }

        private Component getAmountText() {
            return getCompactAmountText(amount, approximate);
        }

        private Component getExactAmountText() {
            Component amountText = EmiRenderHelper.getAmountText(ingredient, amount);
            return approximate ? EmiPort.append(EmiPort.literal("≈"), amountText) : amountText;
        }
    }

    private record HandlerStat(EmiRecipeCategory category, long crafts) {
    }

    private record StatHitbox(Bounds bounds, StatHover hover) {
    }

    private class StatHover {
        private final StatEntry entry;
        private final HandlerStat handler;

        private StatHover(StatEntry entry) {
            this.entry = entry;
            this.handler = null;
        }

        private StatHover(HandlerStat handler) {
            this.entry = null;
            this.handler = handler;
        }

        private void drawTooltip(EmiDrawContext context, int mouseX, int mouseY) {
            if (entry != null) {
                List<ClientTooltipComponent> tooltip = Lists.newArrayList();
                tooltip.addAll(entry.ingredient.getTooltip());
                tooltip.add(ClientTooltipComponent.create(EmiPort.literal(PlannerText.tr("bookmark_tree.total", "Total: %s", entry.getExactAmountText().getString())).getVisualOrderText()));
                long missing = getMissingAmount(entry.ingredient);
                if (inventorySnapshot != null) {
                    tooltip.add(ClientTooltipComponent.create(EmiPort.literal(PlannerText.tr("bookmark_tree.missing", "Missing: %s", missing)).getVisualOrderText()));
                }
                EmiRenderHelper.drawTooltip(BookmarkTreeScreen.this, context, tooltip, mouseX, mouseY);
            } else if (handler != null) {
                List<ClientTooltipComponent> tooltip = Lists.newArrayList(handler.category.getTooltip());
                tooltip.add(ClientTooltipComponent.create(EmiPort.literal(PlannerText.tr("bookmark_tree.crafts", "Crafts: %s", handler.crafts)).getVisualOrderText()));
                EmiRenderHelper.drawTooltip(BookmarkTreeScreen.this, context, tooltip, mouseX, mouseY);
            }
        }
    }

    private record RootTree(MaterialTree tree) {
    }

    private class Hover {
        private final EmiIngredient stack;
        private final MaterialNode node;
        private final EmiRecipeCategory category;

        private Hover(EmiIngredient stack, MaterialNode node) {
            this.stack = stack;
            this.node = node;
            this.category = null;
        }

        private Hover(EmiRecipeCategory category, MaterialNode node) {
            this.stack = null;
            this.node = node;
            this.category = category;
        }
    }

    private class Node {
        private Node parent;
        private MaterialNode resolution;
        private final MaterialNode node;
        private int width;
        private int x;
        private int y;
        private int midOffset;
        private final long amount;
        private final ChanceState chance;

        private Node(MaterialNode node, long amount, int x, int y, ChanceState chance) {
            this.node = node;
            this.amount = amount;
            this.x = x;
            this.y = y;
            this.chance = chance;
            int textWidth = EmiRenderHelper.getAmountOverflow(getAmountText());
            width = NODE_SIZE + textWidth;
            midOffset = textWidth / -2;
        }

        private int centerX() {
            return x + midOffset;
        }

        private int left() {
            return centerX() - NODE_HALF;
        }

        private int top() {
            return y - NODE_HALF;
        }

        private void render(EmiDrawContext context, int mouseX, int mouseY, float delta) {
            if (parent != null) {
                int childX = centerX();
                int parentX = parent.centerX();
                int parentBottom = parent.y + NODE_HALF + 1;
                int childTop = y - NODE_HALF - 1;
                int jointY = (parentBottom + childTop) / 2;
                drawLine(context, parentX, parentBottom, parentX, jointY);
                drawLine(context, Math.min(parentX, childX), jointY, Math.max(parentX, childX), jointY);
                drawLine(context, childX, jointY, childX, childTop);
                if (resolution != null) {
                    context.drawTexture(EmiRenderHelper.WIDGETS, childX - 3, jointY - 3, 9, 192, 7, 7);
                }
            }

            int lx = left();
            int ly = top();
            boolean hovered = mouseX >= lx && mouseY >= ly && mouseX < lx + NODE_SIZE && mouseY < ly + NODE_SIZE;
            context.fill(lx, ly, NODE_SIZE, NODE_SIZE, hovered ? NODE_HOVER_COLOR : NODE_FILL_COLOR);
            boolean missing = inventorySnapshot != null && node.recipe == null && node.progress != ProgressState.COMPLETED;
            int border = hovered ? NODE_HOVER_BORDER_COLOR : (missing ? MISSING_BORDER_COLOR : NODE_BORDER_COLOR);
            drawBorder(context, lx, ly, NODE_SIZE, NODE_SIZE, border);
            if (!renderingScreenshot && searchMatchSet.contains(this)) {
                int searchColor = searchIndex >= 0 && searchIndex < searchMatches.size() && searchMatches.get(searchIndex) == this
                    ? SEARCH_ACTIVE_COLOR : SEARCH_MATCH_COLOR;
                drawBorder(context, lx - 2, ly - 2, NODE_SIZE + 4, NODE_SIZE + 4, searchColor);
            }
            BATCHER.render(node.ingredient, context.raw(), centerX() - 8, y - 8, 0);
            EmiRenderHelper.renderAmount(context, centerX() - 8, y - 8, getAmountText());
        }

        private boolean matchesSearch(String query) {
            for (EmiStack stack : node.ingredient.getEmiStacks()) {
                if (stack != null && !stack.isEmpty() && stack.getName().getString().toLowerCase(Locale.ROOT).contains(query)) {
                    return true;
                }
            }
            if (node.recipe != null) {
                if (node.recipe.getId() != null && node.recipe.getId().toString().toLowerCase(Locale.ROOT).contains(query)) {
                    return true;
                }
                EmiRecipeCategory category = node.recipe.getCategory();
                if (category != null && category.getName().getString().toLowerCase(Locale.ROOT).contains(query)) {
                    return true;
                }
            }
            return false;
        }

        private void renderOverlay(EmiDrawContext context, int mouseX, int mouseY, float delta) {
            if (node.recipe == null) {
                return;
            }
            renderHandlerIcon(context, node.recipe.getCategory(), left() - 4, top() - 4, delta);
            if (node.children != null && !node.children.isEmpty() && !(node.recipe instanceof EmiResolutionRecipe)) {
                int plusY = y + NODE_HALF + 2;
                context.fill(centerX() - 3, plusY - 1, 7, 9, BACKGROUND_COLOR);
                context.drawCenteredText(EmiPort.literal("+"), centerX(), plusY, 0xFFFFFFFF);
            }
        }

        private void renderHandlerIcon(EmiDrawContext context, EmiRecipeCategory category, int x, int y, float delta) {
            if (category == null) {
                return;
            }
            context.fill(x, y, HANDLER_SIZE, HANDLER_SIZE, 0xE5141418);
            drawBorder(context, x, y, HANDLER_SIZE, HANDLER_SIZE, 0xFF6F6F77);
            context.push();
            context.resetColor();
            context.enableDepthTest();
            float scale = 0.42f;
            context.matrices().translate(x + 1, y + 1, 410);
            context.matrices().scale(scale, scale, 1);
            category.renderSimplified(context.raw(), 0, 0, delta);
            context.pop();
            context.resetColor();
        }

        private Component getAmountText() {
            if (chance.chanced()) {
                long adjusted = Math.max(node.amount, Math.round(amount * chance.chance()));
                return getCompactAmountText(adjusted, true);
            }
            return getCompactAmountText(amount, false);
        }

        private Hover getHover(int mouseX, int mouseY) {
            if (node.recipe != null && node.recipe.getCategory() != null) {
                int hx = left() - 4;
                int hy = top() - 4;
                if (mouseX >= hx && mouseX < hx + HANDLER_SIZE && mouseY >= hy && mouseY < hy + HANDLER_SIZE) {
                    return new Hover(node.recipe.getCategory(), node);
                }
            }
            int lx = left();
            int ly = top();
            if (mouseX >= lx && mouseX < lx + NODE_SIZE && mouseY >= ly && mouseY < ly + NODE_SIZE) {
                return new Hover(node.ingredient, node);
            }
            return null;
        }
    }

    private class TreeVolume {
        private final List<Width> widths = new ArrayList<>();
        private final List<Node> nodes = new ArrayList<>();

        private TreeVolume(MaterialNode node, long amount, int y, ChanceState chance) {
            Node head = new Node(node, amount, 0, y, chance);
            int left = head.width / 2;
            widths.add(new Width(-left, head.width - left));
            nodes.add(head);
        }

        private void addHead(MaterialNode node, long amount, int y, ChanceState chance) {
            int x = (getLeft(0) + getRight(0)) / 2;
            Node head = new Node(node, amount, x, y, chance);
            for (Node child : nodes) {
                if (child.parent == null) {
                    child.parent = head;
                }
                child.y += NODE_VERTICAL_SPACING;
            }
            int left = head.width / 2;
            widths.add(0, new Width(x - left, x + head.width - left));
            nodes.add(0, head);
        }

        private int getDepth() {
            return widths.size();
        }

        private int getMinLeft() {
            int value = getLeft(0);
            for (int i = 1; i < getDepth(); i++) {
                value = Math.min(value, getLeft(i));
            }
            return value;
        }

        private int getMaxRight() {
            int value = getRight(0);
            for (int i = 1; i < getDepth(); i++) {
                value = Math.max(value, getRight(i));
            }
            return value;
        }

        private int getLeft(int depth) {
            return widths.get(depth).left;
        }

        private int getRight(int depth) {
            return widths.get(depth).right;
        }

        private void addToRight(TreeVolume other) {
            int rightOffset = getRight(0) - other.getLeft(0) + NODE_HORIZONTAL_SPACING;
            for (int i = 1; i < getDepth() && i < other.getDepth(); i++) {
                rightOffset = Math.max(rightOffset, getRight(i) - other.getLeft(i) + NODE_HORIZONTAL_SPACING);
            }
            for (int i = 0; i < other.getDepth(); i++) {
                if (i < getDepth()) {
                    widths.get(i).right = other.getRight(i) + rightOffset;
                } else {
                    widths.add(new Width(other.getLeft(i) + rightOffset, other.getRight(i) + rightOffset));
                }
            }
            for (Node node : other.nodes) {
                node.x += rightOffset;
                nodes.add(node);
            }
        }
    }

    private static class Width {
        private int left;
        private int right;

        private Width(int left, int right) {
            this.left = left;
            this.right = right;
        }
    }
}
