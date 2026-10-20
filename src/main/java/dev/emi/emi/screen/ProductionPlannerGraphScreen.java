package dev.emi.emi.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.emi.emi.EmiPort;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.input.EmiInput;
import dev.emi.emi.planner.PlannerText;
import dev.emi.emi.planner.ProductionPlanner;
import dev.emi.emi.planner.ProductionPlanner.*;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.runtime.EmiProductionPlannerGraphPersistence;
import dev.emi.emi.screen.WidgetGroup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.Fluid;
import org.lwjgl.glfw.GLFW;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;

public class ProductionPlannerGraphScreen extends Screen {
    private static volatile Function<Screen, Screen> screenFactory = ProductionPlannerGraphScreen::new;

    /** Allows integrations to host the graph in their own UI without depending on that UI library here. */
    public static void setScreenFactory(Function<Screen, Screen> factory) {
        screenFactory = factory;
    }

    public static Screen create(Screen parent) {
        return screenFactory.apply(parent);
    }

    public enum Action {
        FIT, FULL, TARGET_PATH, SELECTED_PATH, BYPRODUCTS, CONTEXT, HIDDEN, AUTO_LAYOUT
    }

    private static final int HEADER_HEIGHT = 28;
    private static final int FOOTER_HEIGHT = 22;
    private static final int RECIPE_MIN_WIDTH = 220;
    private static final int RECIPE_HEADER_HEIGHT = 21;
    private static final int RECIPE_FOOTER_HEIGHT = 30;
    private static final int RECIPE_PADDING = 8;
    private static final int RESOURCE_WIDTH = 152;
    private static final int RESOURCE_HEIGHT = 36;
    private static final int COLUMN_GAP = 118;
    private static final int ROW_GAP = 54;
    private static final int BAND_GAP = 150;
    private static final int BAND_SIDE_LANE = 64;
    private static final int CYCLE_LANE_GAP = 14;
    private static final int CYCLE_BASE_MARGIN = 20;
    private static final int ROUTE_CLEARANCE = 10;
    private static final int SNAP_GRID = 12;
    private static final int HIDDEN_RECIPE_WIDTH = 58;
    private static final int HIDDEN_RECIPE_HEIGHT = 24;
    private static final int ENDPOINT_GAP = 26;
    private static final int ENDPOINT_ROW_GAP = 10;
    private static final float FULL_RECIPE_ZOOM = 0.42f;
    private static final int BG_COLOR = 0xFF0B0B12;
    private static final int HEADER_COLOR = 0xFF24242A;
    private static final int PANEL_COLOR = 0xEE17171F;
    private static final int BORDER_COLOR = 0xFF60606A;
    private static final int HOVER_BORDER_COLOR = 0xFFD0D0DA;
    private static final int INPUT_COLOR = 0xFF69A7E8;
    private static final int INTERNAL_COLOR = 0xFF71C47B;
    private static final int OUTPUT_COLOR = 0xFFE7A45D;
    private static final int CYCLE_COLOR = 0xFFC58BE2;
    private static final int TARGET_COLOR = 0xFFFFD45C;
    private static final int LOAD_SAFE_BG = 0xEE142019;
    private static final int LOAD_MEDIUM_BG = 0xEE262113;
    private static final int LOAD_BOTTLENECK_BG = 0xEE291616;
    private static final int LOAD_SAFE_BORDER = 0xFF69D58C;
    private static final int LOAD_MEDIUM_BORDER = 0xFFFFD45C;
    private static final int LOAD_BOTTLENECK_BORDER = 0xFFFF6B6B;
    private static final double LOAD_MEDIUM_THRESHOLD = 80.0D;
    private static final double EPSILON = 0.0000001D;
    private static final Bounds EMPTY = Bounds.EMPTY;

    private final Screen parent;
    private final Line line;
    private Bounds viewport = EMPTY;
    private Bounds graphBounds = EMPTY;
    private boolean embedded;
    @Nullable
    private Set<GraphNode> cachedFocus;
    private int hiddenCount;
    private boolean statsDirty = true;
    private int statsZoom = -1;
    private int visibleRecipeCount;
    private int visibleFlowCount;
    private final String statsFormat = PlannerText.tr("graph.stats", "%s recipes | %s flows | zoom %s%%");
    private final String hiddenLabel = PlannerText.tr("graph.hidden", "HIDDEN");
    private Component graphStats = Component.empty();
    private List<GraphNode> nodes = List.of();
    private List<GraphEdge> edges = List.of();
    private Bounds backButton = EMPTY;
    private Bounds fitButton = EMPTY;
    private Bounds fullButton = EMPTY;
    private Bounds targetPathButton = EMPTY;
    private Bounds selectedPathButton = EMPTY;
    private Bounds byproductsButton = EMPTY;
    private Bounds contextButton = EMPTY;
    private Bounds hiddenButton = EMPTY;
    private Bounds autoLayoutButton = EMPTY;
    private ViewMode viewMode = ViewMode.FULL;
    private GraphNode selectedNode;
    private boolean showByproducts = true;
    private boolean showContext;
    private boolean revealHidden;
    private float viewScale = 1.0f;
    private double offX;
    private double offY;
    private int contentMinX;
    private int contentMaxX;
    private int contentMinY;
    private int contentMaxY;
    private Map<Integer, BandBounds> bandBounds = Map.of();
    private Map<Integer, CycleBounds> cycleBounds = Map.of();
    private int layoutSideLane = BAND_SIDE_LANE;
    private boolean panning;
    private GraphNode draggingNode;
    private int dragStartWorldX;
    private int dragStartWorldY;
    private int dragStartNodeX;
    private int dragStartNodeY;
    private boolean dragMoved;
    private int lastMouseX;
    private int lastMouseY;
    private final List<RouteRect> cycleLabelRects = new ArrayList<>();
    private final Map<String, EmiProductionPlannerGraphPersistence.Offset> manualOffsets = new LinkedHashMap<>();
    private final Set<String> hiddenNodeKeys = new LinkedHashSet<>();

    public ProductionPlannerGraphScreen(Screen parent) {
        super(EmiPort.literal(PlannerText.tr("graph.title", "Production Flow Graph")));
        this.parent = parent;
        this.line = ProductionPlanner.getOrCreateActiveLine();
        manualOffsets.putAll(EmiProductionPlannerGraphPersistence.load(lineLayoutKey()));
        hiddenNodeKeys.addAll(EmiProductionPlannerGraphPersistence.loadHidden(lineLayoutKey()));
    }

    @Override
    protected void init() {
        if (embedded) return;
        viewport = new Bounds(0, HEADER_HEIGHT, width, Math.max(1, height - HEADER_HEIGHT - FOOTER_HEIGHT));
        backButton = new Bounds(6, 4, 54, 20);
        fitButton = new Bounds(64, 4, 44, 20);
        fullButton = new Bounds(112, 4, 48, 20);
        targetPathButton = new Bounds(164, 4, 72, 20);
        selectedPathButton = new Bounds(240, 4, 84, 20);
        byproductsButton = new Bounds(328, 4, 116, 20);
        contextButton = new Bounds(448, 4, 104, 20);
        hiddenButton = new Bounds(556, 4, 82, 20);
        autoLayoutButton = new Bounds(642, 4, 92, 20);
        rebuildGraph();
        openReadableView();
    }

    private void rebuildGraph() {
        cachedFocus = null;
        statsDirty = true;
        List<GraphNode> builtNodes = new ArrayList<>();
        List<GraphEdge> builtEdges = new ArrayList<>();
        Map<Entry, GraphNode> recipeNodes = new LinkedHashMap<>();
        int order = 0;
        for (Entry entry : line.getEntries()) {
            EmiRecipe recipe = entry.getRecipe();
            double rate = line.getEffectiveRate(entry);
            if (recipe == null || !Double.isFinite(rate) || rate <= EPSILON) {
                order++;
                continue;
            }
            GraphNode node = GraphNode.recipe(entry, recipe, rate, order++);
            recipeNodes.put(entry, node);
            builtNodes.add(node);
        }

        Map<EmiStack, ResourcePool> root = collectGroupResiduals(0, recipeNodes, builtEdges);
        for (ResourcePool pool : root.values()) {
            LinkMode mode = line.getLinkMode(null, pool.stack);
            if (line.hasTarget(pool.stack) || mode != LinkMode.IGNORE) {
                matchPool(pool, builtEdges);
            }
        }

        int endpointOrder = order + 1000;
        for (ResourcePool pool : root.values()) {
            TargetMode targetMode = targetMode(pool.stack);
            for (FlowPart consumer : aggregateFlowParts(pool.consumers)) {
                if (consumer.amount <= EPSILON) {
                    continue;
                }
                GraphNode input = GraphNode.resource(NodeKind.INPUT, pool.stack, consumer.amount,
                    targetMode == TargetMode.INPUT, endpointOrder++);
                builtNodes.add(input);
                builtEdges.add(new GraphEdge(input, consumer.node, pool.stack, consumer.amount,
                    consumer.approximate, EdgeKind.INPUT));
            }
            for (FlowPart producer : aggregateFlowParts(pool.producers)) {
                if (producer.amount <= EPSILON) {
                    continue;
                }
                GraphNode output = GraphNode.resource(NodeKind.OUTPUT, pool.stack, producer.amount,
                    targetMode == TargetMode.OUTPUT, endpointOrder++);
                builtNodes.add(output);
                builtEdges.add(new GraphEdge(producer.node, output, pool.stack, producer.amount,
                    producer.approximate, EdgeKind.OUTPUT));
            }
        }

        hiddenCount = 0;
        for (GraphNode node : builtNodes) {
            node.hidden = hiddenNodeKeys.contains(nodeStateKey(node));
            if (node.hidden) hiddenCount++;
        }
        nodes = builtNodes;
        edges = builtEdges;
        layoutGraph(recipeNodes.values().stream().toList());
    }

    private Map<EmiStack, ResourcePool> collectGroupResiduals(int groupId, Map<Entry, GraphNode> recipeNodes,
            List<GraphEdge> builtEdges) {
        Map<EmiStack, ResourcePool> pools = new LinkedHashMap<>();
        for (Entry entry : line.getEntries()) {
            if (entry.getGroupId() != groupId) {
                continue;
            }
            GraphNode node = recipeNodes.get(entry);
            if (node == null) {
                continue;
            }
            addRecipeFlows(pools, node, entry.getRecipe(), node.rate);
        }
        for (Group child : line.getGroups()) {
            if (child.getParentId() != groupId) {
                continue;
            }
            mergePools(pools, collectGroupResiduals(child.getId(), recipeNodes, builtEdges));
        }
        if (groupId > 0) {
            Group group = line.getGroup(groupId);
            for (ResourcePool pool : pools.values()) {
                if (line.getLinkMode(group, pool.stack) == LinkMode.MATCH) {
                    matchPool(pool, builtEdges);
                }
            }
        }
        return pools;
    }

    private void addRecipeFlows(Map<EmiStack, ResourcePool> pools, GraphNode node, EmiRecipe recipe, double rate) {
        if (recipe == null || rate <= EPSILON) {
            return;
        }
        for (EmiIngredient ingredient : recipe.getInputs()) {
            if (ingredient == null || ingredient.isEmpty()) {
                continue;
            }
            ResourceRole role = ProductionPlanner.getInputResourceRole(recipe, ingredient);
            if (role != ResourceRole.CONSUMED && role != ResourceRole.CONTAINER) {
                // TOOL/CATALYST/RETURNED are startup inventory, not a per-second graph flow.
                continue;
            }
            EmiStack stack = firstStack(ingredient);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            double chance = Math.max(0.0D, ingredient.getChance());
            double amount = ingredient.getAmount() * chance * rate;
            if (amount <= EPSILON) {
                continue;
            }
            boolean approximate = Math.abs(chance - 1.0D) > 0.0001D || ingredient.getEmiStacks().size() > 1;
            ResourcePool pool = pools.computeIfAbsent(normalize(stack), ResourcePool::new);
            pool.consumers.add(new FlowPart(node, amount, approximate));

            // Keep transformed containers/cells visible as a real steady-state circulation path.
            EmiStack remainder = ProductionPlanner.getInputRemainder(ingredient);
            if (remainder != null && !remainder.isEmpty() && !sameResource(stack, remainder)
                    && !recipeOutputsResource(recipe, remainder)) {
                double returnedAmount = amount * Math.max(1L, remainder.getAmount());
                ResourcePool returnedPool = pools.computeIfAbsent(normalize(remainder), ResourcePool::new);
                returnedPool.producers.add(new FlowPart(node, returnedAmount, approximate));
            }
        }
        for (EmiStack output : recipe.getOutputs()) {
            if (output == null || output.isEmpty() || ProductionPlanner.isStartupOnlyOutput(recipe, output)) {
                continue;
            }
            double chance = Math.max(0.0D, output.getChance());
            double amount = output.getAmount() * chance * rate;
            if (amount <= EPSILON) {
                continue;
            }
            boolean approximate = Math.abs(chance - 1.0D) > 0.0001D;
            ResourcePool pool = pools.computeIfAbsent(normalize(output), ResourcePool::new);
            pool.producers.add(new FlowPart(node, amount, approximate));
        }
    }

    private boolean recipeOutputsResource(EmiRecipe recipe, EmiStack target) {
        for (EmiStack output : recipe.getOutputs()) {
            if (output != null && !output.isEmpty() && sameResource(output, target)) {
                return true;
            }
        }
        return false;
    }

    private boolean sameResource(EmiStack a, EmiStack b) {
        return a != null && b != null && !a.isEmpty() && !b.isEmpty() && normalize(a).equals(normalize(b));
    }

    private void mergePools(Map<EmiStack, ResourcePool> into, Map<EmiStack, ResourcePool> child) {
        for (ResourcePool source : child.values()) {
            ResourcePool target = into.computeIfAbsent(source.stack, ResourcePool::new);
            target.producers.addAll(source.producers);
            target.consumers.addAll(source.consumers);
        }
    }

    private List<FlowPart> aggregateFlowParts(List<FlowPart> parts) {
        Map<GraphNode, Double> amounts = new LinkedHashMap<>();
        Map<GraphNode, Boolean> approximate = new LinkedHashMap<>();
        for (FlowPart part : parts) {
            if (part.amount <= EPSILON) {
                continue;
            }
            amounts.merge(part.node, part.amount, Double::sum);
            approximate.merge(part.node, part.approximate, (a, b) -> a || b);
        }
        List<FlowPart> result = new ArrayList<>();
        for (Map.Entry<GraphNode, Double> entry : amounts.entrySet()) {
            result.add(new FlowPart(entry.getKey(), entry.getValue(), approximate.getOrDefault(entry.getKey(), false)));
        }
        return result;
    }

    private void matchPool(ResourcePool pool, List<GraphEdge> builtEdges) {
        int producerIndex = 0;
        int consumerIndex = 0;
        while (producerIndex < pool.producers.size() && consumerIndex < pool.consumers.size()) {
            FlowPart producer = pool.producers.get(producerIndex);
            FlowPart consumer = pool.consumers.get(consumerIndex);
            if (producer.amount <= EPSILON) {
                producerIndex++;
                continue;
            }
            if (consumer.amount <= EPSILON) {
                consumerIndex++;
                continue;
            }
            double amount = Math.min(producer.amount, consumer.amount);
            producer.amount -= amount;
            consumer.amount -= amount;
            if (amount > EPSILON && producer.node != consumer.node) {
                builtEdges.add(new GraphEdge(producer.node, consumer.node, pool.stack, amount,
                    producer.approximate || consumer.approximate, EdgeKind.INTERNAL));
            }
        }
        pool.producers.removeIf(part -> part.amount <= EPSILON);
        pool.consumers.removeIf(part -> part.amount <= EPSILON);
    }

    private void layoutGraph(List<GraphNode> recipeNodes) {
        Set<GraphNode> activeRecipes = new LinkedHashSet<>(recipeNodes);
        Map<GraphNode, Set<GraphNode>> adjacency = new LinkedHashMap<>();
        for (GraphNode node : recipeNodes) {
            adjacency.put(node, new LinkedHashSet<>());
        }
        for (GraphEdge edge : edges) {
            if (edge.kind == EdgeKind.INTERNAL && activeRecipes.contains(edge.from) && activeRecipes.contains(edge.to)) {
                adjacency.get(edge.from).add(edge.to);
            }
        }

        List<List<GraphNode>> components = stronglyConnectedComponents(recipeNodes, adjacency);
        Map<GraphNode, Integer> componentOf = new HashMap<>();
        for (int i = 0; i < components.size(); i++) {
            for (GraphNode node : components.get(i)) {
                componentOf.put(node, i);
            }
        }
        List<Set<Integer>> componentEdges = new ArrayList<>();
        int[] indegree = new int[components.size()];
        int[] depth = new int[components.size()];
        for (int i = 0; i < components.size(); i++) {
            componentEdges.add(new LinkedHashSet<>());
        }
        for (Map.Entry<GraphNode, Set<GraphNode>> entry : adjacency.entrySet()) {
            int from = componentOf.getOrDefault(entry.getKey(), -1);
            for (GraphNode target : entry.getValue()) {
                int to = componentOf.getOrDefault(target, -1);
                if (from >= 0 && to >= 0 && from != to && componentEdges.get(from).add(to)) {
                    indegree[to]++;
                }
            }
        }
        Deque<Integer> queue = new ArrayDeque<>();
        for (int i = 0; i < indegree.length; i++) {
            if (indegree[i] == 0) {
                queue.add(i);
            }
        }
        while (!queue.isEmpty()) {
            int current = queue.removeFirst();
            for (int next : componentEdges.get(current)) {
                depth[next] = Math.max(depth[next], depth[current] + 1);
                if (--indegree[next] == 0) {
                    queue.addLast(next);
                }
            }
        }
        for (GraphNode node : recipeNodes) {
            node.depth = depth[componentOf.getOrDefault(node, 0)];
        }
        for (GraphEdge edge : edges) {
            edge.sameComponent = false;
            edge.cycle = false;
            edge.cycleComponent = -1;
            if (edge.kind == EdgeKind.INTERNAL && edge.from.kind == NodeKind.RECIPE && edge.to.kind == NodeKind.RECIPE) {
                int fromComponent = componentOf.getOrDefault(edge.from, -1);
                int toComponent = componentOf.getOrDefault(edge.to, -2);
                if (fromComponent >= 0 && fromComponent == toComponent && components.get(fromComponent).size() > 1) {
                    edge.sameComponent = true;
                    edge.cycleComponent = fromComponent;
                }
            }
        }

        Map<Integer, List<GraphNode>> columns = new LinkedHashMap<>();
        int maxDepth = 0;
        for (GraphNode node : recipeNodes) {
            columns.computeIfAbsent(node.depth, key -> new ArrayList<>()).add(node);
            maxDepth = Math.max(maxDepth, node.depth);
        }
        for (List<GraphNode> column : columns.values()) {
            column.sort((a, b) -> Integer.compare(a.order, b.order));
        }
        for (int pass = 0; pass < 4; pass++) {
            Map<GraphNode, Integer> forwardRanks = columnRanks(columns);
            for (int d = 1; d <= maxDepth; d++) {
                List<GraphNode> column = columns.get(d);
                if (column != null) {
                    column.sort((a, b) -> compareBarycenter(a, b, forwardRanks, true));
                }
            }
            Map<GraphNode, Integer> backwardRanks = columnRanks(columns);
            for (int d = maxDepth - 1; d >= 0; d--) {
                List<GraphNode> column = columns.get(d);
                if (column != null) {
                    column.sort((a, b) -> compareBarycenter(a, b, backwardRanks, false));
                }
            }
        }

        boolean compactFocus = viewMode != ViewMode.FULL && !showContext;
        int columnGap = compactFocus ? 70 : COLUMN_GAP;
        int rowGap = compactFocus ? 34 : ROW_GAP;
        int bandGap = compactFocus ? 92 : BAND_GAP;
        int sideLane = compactFocus ? 46 : BAND_SIDE_LANE;
        Map<Integer, Integer> columnWidths = new LinkedHashMap<>();
        Map<Integer, Integer> columnHeights = new LinkedHashMap<>();
        for (int d = 0; d <= maxDepth; d++) {
            List<GraphNode> column = columns.get(d);
            if (column == null || column.isEmpty()) {
                continue;
            }
            int columnWidth = 0;
            int columnHeight = 0;
            for (GraphNode node : column) {
                columnWidth = Math.max(columnWidth, node.width());
                columnHeight += recipeBlockHeight(node);
            }
            columnHeight += Math.max(0, column.size() - 1) * rowGap;
            columnWidths.put(d, columnWidth);
            columnHeights.put(d, columnHeight);
        }

        int targetWidth = compactFocus ? Math.min(1420, Math.max(1100, width - 240)) : Math.max(980, width - 180);
        Map<Integer, Integer> depthBand = new LinkedHashMap<>();
        Map<Integer, List<Integer>> bandDepths = new LinkedHashMap<>();
        int band = 0;
        int bandWidth = 0;
        for (int d = 0; d <= maxDepth; d++) {
            Integer columnWidth = columnWidths.get(d);
            if (columnWidth == null) {
                continue;
            }
            int needed = columnWidth + (bandWidth == 0 ? 0 : columnGap);
            if (bandWidth > 0 && bandWidth + needed > targetWidth) {
                band++;
                bandWidth = 0;
                needed = columnWidth;
            }
            depthBand.put(d, band);
            bandDepths.computeIfAbsent(band, ignored -> new ArrayList<>()).add(d);
            bandWidth += needed;
        }

        Map<Integer, BandBounds> builtBands = new LinkedHashMap<>();
        int bandTop = 0;
        for (Map.Entry<Integer, List<Integer>> bandEntry : bandDepths.entrySet()) {
            int bandIndex = bandEntry.getKey();
            List<Integer> depths = bandEntry.getValue();
            int bandHeight = 0;
            int naturalWidth = 0;
            for (int d : depths) {
                bandHeight = Math.max(bandHeight, columnHeights.getOrDefault(d, 0));
                naturalWidth += columnWidths.getOrDefault(d, 0);
            }
            naturalWidth += Math.max(0, depths.size() - 1) * columnGap;
            int x = 0;
            for (int d : depths) {
                List<GraphNode> column = columns.get(d);
                int columnWidth = columnWidths.getOrDefault(d, RECIPE_MIN_WIDTH);
                int columnHeight = columnHeights.getOrDefault(d, 0);
                int blockY = bandTop + Math.max(0, (bandHeight - columnHeight) / 2);
                if (column != null) {
                    for (GraphNode node : column) {
                        int blockHeight = recipeBlockHeight(node);
                        node.band = bandIndex;
                        node.x = x + (columnWidth - node.width()) / 2;
                        node.y = blockY + inputBlockHeight(node);
                        blockY += blockHeight + rowGap;
                    }
                }
                x += columnWidth + columnGap;
            }
            builtBands.put(bandIndex, new BandBounds(0, Math.max(1, naturalWidth), bandTop, bandTop + bandHeight));
            bandTop += bandHeight + bandGap;
        }
        bandBounds = builtBands;
        layoutSideLane = sideLane;

        for (int pass = 0; pass < 2; pass++) {
            for (int d = 1; d <= maxDepth; d++) {
                alignRecipeColumn(columns.get(d), true);
            }
            for (int d = maxDepth - 1; d >= 0; d--) {
                alignRecipeColumn(columns.get(d), false);
            }
        }
        clampColumnsToBands(columns);
        applyManualOffsets(recipeNodes);
        placeEndpointNodes();
        classifyCycleEdges();
        buildCycleBounds(components);
        assignEdgeRoutes();
        updateContentBounds();
    }

    private Map<GraphNode, Integer> columnRanks(Map<Integer, List<GraphNode>> columns) {
        Map<GraphNode, Integer> ranks = new HashMap<>();
        for (List<GraphNode> column : columns.values()) {
            for (int i = 0; i < column.size(); i++) {
                ranks.put(column.get(i), i);
            }
        }
        return ranks;
    }

    private int compareBarycenter(GraphNode a, GraphNode b, Map<GraphNode, Integer> ranks, boolean incoming) {
        double av = barycenter(a, ranks, incoming);
        double bv = barycenter(b, ranks, incoming);
        if (Double.isFinite(av) && Double.isFinite(bv)) {
            int compare = Double.compare(av, bv);
            if (compare != 0) {
                return compare;
            }
        } else if (Double.isFinite(av)) {
            return -1;
        } else if (Double.isFinite(bv)) {
            return 1;
        }
        return Integer.compare(a.order, b.order);
    }

    private double barycenter(GraphNode node, Map<GraphNode, Integer> ranks, boolean incoming) {
        double total = 0.0D;
        int count = 0;
        for (GraphEdge edge : edges) {
            if (!edgeShown(edge) || edge.sameComponent) {
                continue;
            }
            GraphNode neighbor = null;
            if (incoming && edge.to == node && edge.from.kind == NodeKind.RECIPE) {
                neighbor = edge.from;
            } else if (!incoming && edge.from == node && edge.to.kind == NodeKind.RECIPE) {
                neighbor = edge.to;
            }
            if (neighbor != null && ranks.containsKey(neighbor)) {
                total += ranks.get(neighbor);
                count++;
            }
        }
        return count == 0 ? Double.NaN : total / count;
    }

    private int recipeBlockHeight(GraphNode node) {
        return inputBlockHeight(node) + node.height() + outputBlockHeight(node);
    }

    private int inputBlockHeight(GraphNode node) {
        int count = endpointCount(node, true);
        return count <= 0 ? 0 : endpointStackHeight(count) + ENDPOINT_GAP;
    }

    private int outputBlockHeight(GraphNode node) {
        int count = endpointCount(node, false);
        return count <= 0 ? 0 : ENDPOINT_GAP + endpointStackHeight(count);
    }

    private int endpointCount(GraphNode node, boolean input) {
        int count = 0;
        for (GraphEdge edge : edges) {
            if (!edgeShown(edge)) {
                continue;
            }
            if (input && edge.kind == EdgeKind.INPUT && edge.to == node && edge.from.kind == NodeKind.INPUT && !edge.from.target) {
                count++;
            } else if (!input && edge.kind == EdgeKind.OUTPUT && edge.from == node && edge.to.kind == NodeKind.OUTPUT && !edge.to.target) {
                count++;
            }
        }
        return count;
    }

    private int endpointStackHeight(int count) {
        if (count <= 0) {
            return 0;
        }
        return count * RESOURCE_HEIGHT + (count - 1) * ENDPOINT_ROW_GAP;
    }

    private void alignRecipeColumn(List<GraphNode> column, boolean incoming) {
        if (column == null || column.isEmpty()) {
            return;
        }
        List<Double> desiredCenters = new ArrayList<>();
        double desiredAverage = 0.0D;
        for (GraphNode node : column) {
            double desired = connectedRecipeCenter(node, incoming);
            if (!Double.isFinite(desired)) {
                desired = node.centerY();
            }
            desiredCenters.add(desired);
            desiredAverage += desired;
        }
        desiredAverage /= column.size();
        int nextTop = Integer.MIN_VALUE / 4;
        for (int i = 0; i < column.size(); i++) {
            GraphNode node = column.get(i);
            int blockHeight = recipeBlockHeight(node);
            int blockTop = (int) Math.round(desiredCenters.get(i) - blockHeight / 2.0D);
            if (i > 0) {
                blockTop = Math.max(blockTop, nextTop);
            }
            node.y = blockTop + inputBlockHeight(node);
            nextTop = blockTop + blockHeight + (viewMode != ViewMode.FULL && !showContext ? 34 : ROW_GAP);
        }
        double actualAverage = 0.0D;
        for (GraphNode node : column) {
            actualAverage += node.centerY();
        }
        actualAverage /= column.size();
        int shift = (int) Math.round(desiredAverage - actualAverage);
        for (GraphNode node : column) {
            node.y += shift;
        }
    }

    private double connectedRecipeCenter(GraphNode node, boolean incoming) {
        double total = 0.0D;
        int count = 0;
        for (GraphEdge edge : edges) {
            if (!edgeShown(edge) || edge.kind != EdgeKind.INTERNAL || edge.sameComponent) {
                continue;
            }
            if (incoming && edge.to == node && edge.from.kind == NodeKind.RECIPE
                    && edge.from.depth < node.depth && edge.from.band == node.band) {
                total += edge.from.centerY();
                count++;
            } else if (!incoming && edge.from == node && edge.to.kind == NodeKind.RECIPE
                    && edge.to.depth > node.depth && edge.to.band == node.band) {
                total += edge.to.centerY();
                count++;
            }
        }
        return count == 0 ? Double.NaN : total / count;
    }

    private void clampColumnsToBands(Map<Integer, List<GraphNode>> columns) {
        for (List<GraphNode> column : columns.values()) {
            if (column == null || column.isEmpty()) {
                continue;
            }
            BandBounds bounds = bandBounds.get(column.get(0).band);
            if (bounds == null) {
                continue;
            }
            int minTop = Integer.MAX_VALUE;
            int maxBottom = Integer.MIN_VALUE;
            for (GraphNode node : column) {
                minTop = Math.min(minTop, node.y - inputBlockHeight(node));
                maxBottom = Math.max(maxBottom, node.y + node.height() + outputBlockHeight(node));
            }
            int shift = 0;
            if (minTop < bounds.top) {
                shift = bounds.top - minTop;
            }
            if (maxBottom + shift > bounds.bottom) {
                shift += bounds.bottom - (maxBottom + shift);
            }
            if (shift != 0) {
                for (GraphNode node : column) {
                    node.y += shift;
                }
            }
        }
    }

    private void applyManualOffsets(List<GraphNode> recipeNodes) {
        for (GraphNode node : recipeNodes) {
            node.autoX = node.x;
            node.autoY = node.y;
            EmiProductionPlannerGraphPersistence.Offset offset = manualOffsets.get(nodeLayoutKey(node));
            if (offset != null) {
                node.x += offset.x();
                node.y += offset.y();
            }
        }
    }

    private void refreshRoutesAfterManualMove() {
        if (draggingNode == null || draggingNode.kind == NodeKind.RECIPE) {
            placeEndpointNodes();
        }
        rebuildCycleBoundsFromCurrentNodes();
        assignEdgeRoutes();
        updateContentBounds();
    }

    private void rebuildCycleBoundsFromCurrentNodes() {
        Map<Integer, Set<GraphNode>> components = new LinkedHashMap<>();
        for (GraphEdge edge : edges) {
            if (!edge.sameComponent || edge.cycleComponent < 0) {
                continue;
            }
            Set<GraphNode> members = components.computeIfAbsent(edge.cycleComponent, ignored -> new LinkedHashSet<>());
            members.add(edge.from);
            members.add(edge.to);
        }
        Map<Integer, CycleBounds> bounds = new LinkedHashMap<>();
        for (Map.Entry<Integer, Set<GraphNode>> entry : components.entrySet()) {
            int left = Integer.MAX_VALUE;
            int right = Integer.MIN_VALUE;
            int top = Integer.MAX_VALUE;
            int bottom = Integer.MIN_VALUE;
            int band = -1;
            boolean visible = false;
            for (GraphNode node : entry.getValue()) {
                if (!nodeShown(node)) {
                    continue;
                }
                visible = true;
                band = node.band;
                left = Math.min(left, node.x);
                right = Math.max(right, node.right());
                top = Math.min(top, node.y);
                bottom = Math.max(bottom, node.y + node.height());
            }
            if (visible) {
                bounds.put(entry.getKey(), new CycleBounds(left, right, top, bottom, band));
            }
        }
        cycleBounds = bounds;
    }

    private String lineLayoutKey() {
        return "line:" + Math.max(0, ProductionPlanner.getActiveIndex());
    }

    private String nodeLayoutKey(GraphNode node) {
        if (node != null && node.kind != NodeKind.RECIPE) {
            return "endpoint:" + nodeStateKey(node);
        }
        if (node != null && node.recipe != null && node.recipe.getId() != null) {
            return node.recipe.getId().toString();
        }
        return "entry:" + (node == null ? -1 : node.order);
    }

    private String nodeStateKey(GraphNode node) {
        if (node == null) {
            return "node:-1";
        }
        if (node.kind == NodeKind.RECIPE) {
            String id = node.recipe != null && node.recipe.getId() != null ? node.recipe.getId().toString() : "entry";
            return "recipe:" + id + ":" + node.order;
        }
        String key = node.stack == null || node.stack.isEmpty() ? "empty" : String.valueOf(node.stack.getKey());
        String nbt = node.stack == null || node.stack.isEmpty() ? "" : String.valueOf(node.stack.getNbt());
        return "resource:" + node.kind.name() + ":" + key + ":" + nbt + ":" + node.order;
    }

    private int activeHiddenCount() {
        return hiddenCount;
    }

    private boolean canHide(GraphNode node) {
        return node != null && !node.target;
    }

    private void toggleHidden(GraphNode node) {
        if (!canHide(node)) {
            return;
        }
        String key = nodeStateKey(node);
        node.hidden = !node.hidden;
        hiddenCount += node.hidden ? 1 : -1;
        if (node.hidden) {
            hiddenNodeKeys.add(key);
        } else {
            hiddenNodeKeys.remove(key);
        }
        EmiProductionPlannerGraphPersistence.saveHidden(lineLayoutKey(), hiddenNodeKeys);
        if (selectedNode == node && node.hidden && node.kind != NodeKind.RECIPE) {
            selectedNode = null;
            if (viewMode == ViewMode.SELECTED_PATH) {
                viewMode = ViewMode.FULL;
            }
        }
        refreshViewLayout(true);
    }

    private void restoreAllHidden() {
        hiddenCount = 0;
        hiddenNodeKeys.clear();
        for (GraphNode node : nodes) {
            node.hidden = false;
        }
        revealHidden = false;
        EmiProductionPlannerGraphPersistence.saveHidden(lineLayoutKey(), hiddenNodeKeys);
        refreshViewLayout(true);
    }

    private void placeEndpointNodes() {
        for (GraphNode recipe : nodes) {
            if (recipe.kind != NodeKind.RECIPE || !nodeShown(recipe)) {
                continue;
            }
            List<GraphNode> inputs = new ArrayList<>();
            List<GraphNode> outputs = new ArrayList<>();
            List<GraphNode> targetInputs = new ArrayList<>();
            List<GraphNode> targetOutputs = new ArrayList<>();
            for (GraphEdge edge : edges) {
                if (!edgeShown(edge)) {
                    continue;
                }
                if (edge.kind == EdgeKind.INPUT && edge.to == recipe && edge.from.kind == NodeKind.INPUT) {
                    (edge.from.target ? targetInputs : inputs).add(edge.from);
                } else if (edge.kind == EdgeKind.OUTPUT && edge.from == recipe && edge.to.kind == NodeKind.OUTPUT) {
                    (edge.to.target ? targetOutputs : outputs).add(edge.to);
                }
            }
            inputs.sort((a, b) -> Integer.compare(a.order, b.order));
            outputs.sort((a, b) -> Integer.compare(a.order, b.order));
            targetInputs.sort((a, b) -> Integer.compare(a.order, b.order));
            targetOutputs.sort((a, b) -> Integer.compare(a.order, b.order));
            placeEndpointStack(recipe, inputs, true);
            placeEndpointStack(recipe, outputs, false);
            placeTargetEndpoints(recipe, targetInputs, true);
            placeTargetEndpoints(recipe, targetOutputs, false);
        }
        for (GraphNode endpoint : nodes) {
            if (endpoint.kind == NodeKind.RECIPE) {
                continue;
            }
            endpoint.autoX = endpoint.x;
            endpoint.autoY = endpoint.y;
            EmiProductionPlannerGraphPersistence.Offset offset = manualOffsets.get(nodeLayoutKey(endpoint));
            if (offset != null) {
                endpoint.x += offset.x();
                endpoint.y += offset.y();
            }
        }
    }

    private void placeEndpointStack(GraphNode recipe, List<GraphNode> endpoints, boolean input) {
        if (endpoints.isEmpty()) {
            return;
        }
        int stackHeight = endpointStackHeight(endpoints.size());
        int x = recipe.x + (recipe.width() - RESOURCE_WIDTH) / 2;
        int y = input ? recipe.y - ENDPOINT_GAP - stackHeight : recipe.y + recipe.height() + ENDPOINT_GAP;
        for (GraphNode endpoint : endpoints) {
            endpoint.band = recipe.band;
            endpoint.x = x;
            endpoint.y = y;
            y += RESOURCE_HEIGHT + ENDPOINT_ROW_GAP;
        }
    }

    private void placeTargetEndpoints(GraphNode recipe, List<GraphNode> endpoints, boolean input) {
        if (endpoints.isEmpty()) {
            return;
        }
        int stackHeight = 0;
        for (GraphNode endpoint : endpoints) {
            stackHeight += endpoint.height();
        }
        stackHeight += Math.max(0, endpoints.size() - 1) * ENDPOINT_ROW_GAP;
        int y = recipe.centerY() - stackHeight / 2;
        for (GraphNode endpoint : endpoints) {
            endpoint.band = recipe.band;
            endpoint.x = input ? recipe.x - ENDPOINT_GAP - endpoint.width() : recipe.right() + ENDPOINT_GAP;
            endpoint.y = y;
            y += endpoint.height() + ENDPOINT_ROW_GAP;
        }
    }

    private void classifyCycleEdges() {
        for (GraphEdge edge : edges) {
            if (!edge.sameComponent || !edgeShown(edge)) {
                edge.cycle = false;
                continue;
            }
            edge.cycle = edge.from.centerY() >= edge.to.centerY();
        }
    }

    private void buildCycleBounds(List<List<GraphNode>> components) {
        Map<Integer, CycleBounds> bounds = new LinkedHashMap<>();
        for (int component = 0; component < components.size(); component++) {
            List<GraphNode> members = components.get(component);
            if (members.size() <= 1) {
                continue;
            }
            int left = Integer.MAX_VALUE;
            int right = Integer.MIN_VALUE;
            int top = Integer.MAX_VALUE;
            int bottom = Integer.MIN_VALUE;
            int band = -1;
            boolean visible = false;
            for (GraphNode node : members) {
                if (!nodeShown(node)) {
                    continue;
                }
                visible = true;
                band = node.band;
                left = Math.min(left, node.x);
                right = Math.max(right, node.right());
                top = Math.min(top, node.y);
                bottom = Math.max(bottom, node.y + node.height());
            }
            if (visible) {
                bounds.put(component, new CycleBounds(left, right, top, bottom, band));
            }
        }
        cycleBounds = bounds;
    }

    private void assignEdgeRoutes() {
        Map<String, List<GraphEdge>> grouped = new LinkedHashMap<>();
        Map<Integer, List<GraphEdge>> cycles = new LinkedHashMap<>();
        for (GraphEdge edge : edges) {
            edge.routeOffset = 0;
            edge.cycleLane = 0;
            edge.cycleLabel = false;
            edge.routePoints = List.of();
            if (!edgeShown(edge)) {
                continue;
            }
            if (edge.cycle) {
                cycles.computeIfAbsent(edge.cycleComponent, ignored -> new ArrayList<>()).add(edge);
                continue;
            }
            String key;
            if (edge.kind == EdgeKind.INTERNAL && edge.from.kind == NodeKind.RECIPE && edge.to.kind == NodeKind.RECIPE) {
                key = "internal:" + edge.from.band + ":" + edge.from.depth + ":" + edge.to.band + ":" + edge.to.depth;
            } else if (edge.kind == EdgeKind.INPUT) {
                key = "input:" + System.identityHashCode(edge.to);
            } else {
                key = "output:" + System.identityHashCode(edge.from);
            }
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(edge);
        }
        for (List<GraphEdge> group : grouped.values()) {
            group.sort((a, b) -> Integer.compare(a.from.centerY() + a.to.centerY(), b.from.centerY() + b.to.centerY()));
            int spacing = group.size() > 12 ? 4 : 7;
            for (int i = 0; i < group.size(); i++) {
                group.get(i).routeOffset = (i * 2 - group.size() + 1) * spacing / 2;
            }
        }
        for (List<GraphEdge> group : cycles.values()) {
            group.sort((a, b) -> {
                int compare = Integer.compare(a.from.centerY(), b.from.centerY());
                return compare != 0 ? compare : Integer.compare(a.to.centerY(), b.to.centerY());
            });
            Set<String> labeled = new LinkedHashSet<>();
            int spacing = group.size() > 8 ? 4 : 7;
            for (int i = 0; i < group.size(); i++) {
                GraphEdge edge = group.get(i);
                edge.cycleLane = i + 1;
                edge.routeOffset = (i * 2 - group.size() + 1) * spacing / 2;
                String key = edge.stack.getKey() + "|" + edge.stack.getNbt();
                edge.cycleLabel = labeled.add(key);
            }
        }
        List<RouteSegment> occupied = new ArrayList<>();
        List<GraphEdge> routable = new ArrayList<>();
        for (GraphEdge edge : edges) {
            if (edgeShown(edge) && edge.kind == EdgeKind.INTERNAL && !edge.cycle
                    && edge.from.kind == NodeKind.RECIPE && edge.to.kind == NodeKind.RECIPE) {
                routable.add(edge);
            }
        }
        routable.sort((a, b) -> {
            int sameBandA = a.from.band == a.to.band ? 0 : 1;
            int sameBandB = b.from.band == b.to.band ? 0 : 1;
            int compare = Integer.compare(sameBandA, sameBandB);
            if (compare != 0) {
                return compare;
            }
            return Integer.compare(Math.abs(a.from.depth - a.to.depth), Math.abs(b.from.depth - b.to.depth));
        });
        for (GraphEdge edge : routable) {
            edge.routePoints = buildSmartRoute(edge, occupied);
            occupied.addAll(routeSegments(edge.routePoints));
        }
        List<GraphEdge> recycleRoutable = new ArrayList<>();
        for (GraphEdge edge : edges) {
            if (edgeShown(edge) && edge.cycle && edge.kind == EdgeKind.INTERNAL
                    && edge.from.kind == NodeKind.RECIPE && edge.to.kind == NodeKind.RECIPE) {
                recycleRoutable.add(edge);
            }
        }
        recycleRoutable.sort((a, b) -> {
            int distanceA = Math.abs(a.from.centerX() - a.to.centerX()) + Math.abs(a.from.centerY() - a.to.centerY());
            int distanceB = Math.abs(b.from.centerX() - b.to.centerX()) + Math.abs(b.from.centerY() - b.to.centerY());
            return Integer.compare(distanceA, distanceB);
        });
        for (GraphEdge edge : recycleRoutable) {
            edge.routePoints = buildSmartRoute(edge, occupied);
            occupied.addAll(routeSegments(edge.routePoints));
        }
    }

    private List<RoutePoint> buildSmartRoute(GraphEdge edge, List<RouteSegment> occupied) {
        SlotAnchor source = findSlotAnchor(edge.from, edge.stack, true);
        SlotAnchor target = findSlotAnchor(edge.to, edge.stack, false);
        int direction = target.x >= source.x ? 1 : -1;
        int sourceExitX = direction > 0 ? edge.from.right() + ROUTE_CLEARANCE : edge.from.x - ROUTE_CLEARANCE;
        int targetEntryX = direction > 0 ? edge.to.x - ROUTE_CLEARANCE : edge.to.right() + ROUTE_CLEARANCE;
        List<RouteRect> obstacles = routeObstacles(edge);
        LinkedHashSet<Integer> xCandidates = new LinkedHashSet<>();
        LinkedHashSet<Integer> yCandidates = new LinkedHashSet<>();
        xCandidates.add((sourceExitX + targetEntryX) / 2 + edge.routeOffset);
        xCandidates.add(Math.min(sourceExitX, targetEntryX) - 18 - Math.abs(edge.routeOffset));
        xCandidates.add(Math.max(sourceExitX, targetEntryX) + 18 + Math.abs(edge.routeOffset));
        yCandidates.add((source.y + target.y) / 2 + edge.routeOffset);
        yCandidates.add(Math.min(source.y, target.y) - 18 - Math.abs(edge.routeOffset));
        yCandidates.add(Math.max(source.y, target.y) + 18 + Math.abs(edge.routeOffset));
        for (RouteRect obstacle : obstacles) {
            xCandidates.add(obstacle.left - ROUTE_CLEARANCE);
            xCandidates.add(obstacle.right + ROUTE_CLEARANCE);
            yCandidates.add(obstacle.top - ROUTE_CLEARANCE);
            yCandidates.add(obstacle.bottom + ROUTE_CLEARANCE);
        }
        List<List<RoutePoint>> candidates = new ArrayList<>();
        RoutePoint start = new RoutePoint(source.x, source.y);
        RoutePoint finish = new RoutePoint(target.x, target.y);
        RoutePoint sourceExit = new RoutePoint(sourceExitX, source.y);
        RoutePoint targetEntry = new RoutePoint(targetEntryX, target.y);
        for (int x : xCandidates) {
            candidates.add(compactRoute(List.of(start, sourceExit, new RoutePoint(x, source.y),
                new RoutePoint(x, target.y), targetEntry, finish)));
        }
        for (int y : yCandidates) {
            candidates.add(compactRoute(List.of(start, sourceExit, new RoutePoint(sourceExitX, y),
                new RoutePoint(targetEntryX, y), targetEntry, finish)));
        }
        candidates.add(compactRoute(List.of(start, sourceExit, new RoutePoint(targetEntryX, source.y), targetEntry, finish)));
        candidates.add(compactRoute(List.of(start, sourceExit, new RoutePoint(sourceExitX, target.y), targetEntry, finish)));
        List<RoutePoint> best = List.of(start, finish);
        double bestScore = Double.POSITIVE_INFINITY;
        for (List<RoutePoint> candidate : candidates) {
            double score = routeScore(candidate, obstacles, occupied);
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private List<RouteRect> routeObstacles(GraphEdge edge) {
        List<RouteRect> obstacles = new ArrayList<>();
        for (GraphNode node : nodes) {
            if (!nodeShown(node) || node == edge.from || node == edge.to) {
                continue;
            }
            obstacles.add(new RouteRect(node.x - ROUTE_CLEARANCE, node.y - ROUTE_CLEARANCE,
                node.right() + ROUTE_CLEARANCE, node.y + node.height() + ROUTE_CLEARANCE));
        }
        return obstacles;
    }

    private double routeScore(List<RoutePoint> points, List<RouteRect> obstacles, List<RouteSegment> occupied) {
        double score = 0.0D;
        List<RouteSegment> segments = routeSegments(points);
        for (RouteSegment segment : segments) {
            score += segment.length();
            for (RouteRect obstacle : obstacles) {
                if (segmentIntersectsRect(segment, obstacle)) {
                    score += 100000.0D;
                }
            }
            for (RouteSegment other : occupied) {
                int crossing = segmentCrossing(segment, other);
                if (crossing == 1) {
                    score += 380.0D;
                } else if (crossing == 2) {
                    score += 860.0D;
                }
            }
        }
        score += Math.max(0, segments.size() - 1) * 22.0D;
        return score;
    }

    private List<RoutePoint> compactRoute(List<RoutePoint> points) {
        List<RoutePoint> compact = new ArrayList<>();
        for (RoutePoint point : points) {
            if (!compact.isEmpty() && compact.get(compact.size() - 1).equals(point)) {
                continue;
            }
            compact.add(point);
            while (compact.size() >= 3) {
                int size = compact.size();
                RoutePoint a = compact.get(size - 3);
                RoutePoint b = compact.get(size - 2);
                RoutePoint c = compact.get(size - 1);
                if (a.x == b.x && b.x == c.x || a.y == b.y && b.y == c.y) {
                    compact.remove(size - 2);
                } else {
                    break;
                }
            }
        }
        return compact;
    }

    private List<RouteSegment> routeSegments(List<RoutePoint> points) {
        List<RouteSegment> segments = new ArrayList<>();
        for (int i = 1; i < points.size(); i++) {
            RoutePoint from = points.get(i - 1);
            RoutePoint to = points.get(i);
            if (!from.equals(to)) {
                segments.add(new RouteSegment(from, to));
            }
        }
        return segments;
    }

    private boolean segmentIntersectsRect(RouteSegment segment, RouteRect rect) {
        if (segment.horizontal()) {
            int left = Math.min(segment.from.x, segment.to.x);
            int right = Math.max(segment.from.x, segment.to.x);
            return segment.from.y > rect.top && segment.from.y < rect.bottom && right > rect.left && left < rect.right;
        }
        if (segment.vertical()) {
            int top = Math.min(segment.from.y, segment.to.y);
            int bottom = Math.max(segment.from.y, segment.to.y);
            return segment.from.x > rect.left && segment.from.x < rect.right && bottom > rect.top && top < rect.bottom;
        }
        return true;
    }

    private int segmentCrossing(RouteSegment a, RouteSegment b) {
        if (a.horizontal() && b.vertical()) {
            return between(b.from.x, a.from.x, a.to.x) && between(a.from.y, b.from.y, b.to.y) ? 1 : 0;
        }
        if (a.vertical() && b.horizontal()) {
            return between(a.from.x, b.from.x, b.to.x) && between(b.from.y, a.from.y, a.to.y) ? 1 : 0;
        }
        if (a.horizontal() && b.horizontal() && a.from.y == b.from.y) {
            return rangesOverlap(a.from.x, a.to.x, b.from.x, b.to.x) ? 2 : 0;
        }
        if (a.vertical() && b.vertical() && a.from.x == b.from.x) {
            return rangesOverlap(a.from.y, a.to.y, b.from.y, b.to.y) ? 2 : 0;
        }
        return 0;
    }

    private boolean between(int value, int a, int b) {
        return value >= Math.min(a, b) && value <= Math.max(a, b);
    }

    private boolean rangesOverlap(int a1, int a2, int b1, int b2) {
        return Math.max(Math.min(a1, a2), Math.min(b1, b2)) <= Math.min(Math.max(a1, a2), Math.max(b1, b2));
    }


    private List<List<GraphNode>> stronglyConnectedComponents(List<GraphNode> recipeNodes,
            Map<GraphNode, Set<GraphNode>> adjacency) {
        Map<GraphNode, Integer> index = new HashMap<>();
        Map<GraphNode, Integer> low = new HashMap<>();
        Deque<GraphNode> stack = new ArrayDeque<>();
        Set<GraphNode> onStack = new HashSet<>();
        List<List<GraphNode>> result = new ArrayList<>();
        int[] nextIndex = { 0 };
        for (GraphNode node : recipeNodes) {
            if (!index.containsKey(node)) {
                tarjan(node, adjacency, index, low, stack, onStack, result, nextIndex);
            }
        }
        return result;
    }

    private void tarjan(GraphNode node, Map<GraphNode, Set<GraphNode>> adjacency,
            Map<GraphNode, Integer> index, Map<GraphNode, Integer> low, Deque<GraphNode> stack,
            Set<GraphNode> onStack, List<List<GraphNode>> result, int[] nextIndex) {
        int currentIndex = nextIndex[0]++;
        index.put(node, currentIndex);
        low.put(node, currentIndex);
        stack.push(node);
        onStack.add(node);
        for (GraphNode next : adjacency.getOrDefault(node, Set.of())) {
            if (!index.containsKey(next)) {
                tarjan(next, adjacency, index, low, stack, onStack, result, nextIndex);
                low.put(node, Math.min(low.get(node), low.get(next)));
            } else if (onStack.contains(next)) {
                low.put(node, Math.min(low.get(node), index.get(next)));
            }
        }
        if (low.get(node).equals(index.get(node))) {
            List<GraphNode> component = new ArrayList<>();
            GraphNode member;
            do {
                member = stack.pop();
                onStack.remove(member);
                component.add(member);
            } while (member != node);
            result.add(component);
        }
    }

    private void updateContentBounds() {
        if (nodes.isEmpty()) {
            graphBounds = EMPTY;
            contentMinX = contentMinY = -100;
            contentMaxX = contentMaxY = 100;
            return;
        }
        contentMinX = Integer.MAX_VALUE;
        contentMinY = Integer.MAX_VALUE;
        contentMaxX = Integer.MIN_VALUE;
        contentMaxY = Integer.MIN_VALUE;
        for (GraphNode node : nodes) {
            if (!nodeShown(node)) {
                continue;
            }
            contentMinX = Math.min(contentMinX, node.x - 36);
            contentMinY = Math.min(contentMinY, node.y - 40);
            contentMaxX = Math.max(contentMaxX, node.x + node.width() + 36);
            contentMaxY = Math.max(contentMaxY, node.y + node.height() + 40);
        }
        for (GraphEdge edge : edges) {
            if (!edgeShown(edge)) {
                continue;
            }
            for (RoutePoint point : edge.routePoints) {
                contentMinX = Math.min(contentMinX, point.x - 24);
                contentMaxX = Math.max(contentMaxX, point.x + 24);
                contentMinY = Math.min(contentMinY, point.y - 24);
                contentMaxY = Math.max(contentMaxY, point.y + 24);
            }
        }
        graphBounds = new Bounds(contentMinX, contentMinY,
            Math.max(1, contentMaxX - contentMinX), Math.max(1, contentMaxY - contentMinY));
    }

    private List<GraphNode> visibleRecipeNodes() {
        List<GraphNode> visible = new ArrayList<>();
        for (GraphNode node : nodes) {
            if (node.kind == NodeKind.RECIPE && nodeShown(node)) {
                visible.add(node);
            }
        }
        return visible;
    }

    private void refreshViewLayout(boolean fit) {
        cachedFocus = null;
        statsDirty = true;
        layoutGraph(visibleRecipeNodes());
        if (fit) {
            fitToView();
        }
    }

    private void openReadableView() {
        if (nodes.isEmpty()) {
            viewScale = 1.0f;
            offX = 0.0D;
            offY = 0.0D;
            return;
        }
        BandBounds first = bandBounds.get(0);
        if (first == null) {
            fitToView();
            return;
        }
        int bandWidth = Math.max(1, first.right - first.left);
        int bandHeight = Math.max(1, first.bottom - first.top);
        float sx = Math.max(0.05f, (viewport.width() - 70.0f) / (bandWidth + 80.0f));
        float sy = Math.max(0.05f, (viewport.height() - 50.0f) / (bandHeight + 80.0f));
        viewScale = Mth.clamp(Math.min(1.0f, Math.min(sx, sy)), 0.68f, 1.0f);
        offX = -((first.left + first.right) / 2.0D);
        offY = -((first.top + first.bottom) / 2.0D);
    }

    private void fitToView() {
        if (nodes.isEmpty()) {
            viewScale = 1.0f;
            offX = 0.0D;
            offY = 0.0D;
            return;
        }
        int contentWidth = Math.max(1, contentMaxX - contentMinX);
        int contentHeight = Math.max(1, contentMaxY - contentMinY);
        float sx = Math.max(0.05f, (viewport.width() - 32.0f) / (contentWidth + 30.0f));
        float sy = Math.max(0.05f, (viewport.height() - 20.0f) / (contentHeight + 30.0f));
        viewScale = Mth.clamp(Math.min(sx, sy), 0.12f, 1.35f);
        offX = -((contentMinX + contentMaxX) / 2.0D);
        offY = -((contentMinY + contentMaxY) / 2.0D);
    }

    @Override
    public void render(GuiGraphics raw, int mouseX, int mouseY, float delta) {
        renderGraph(raw, mouseX, mouseY, delta);
        EmiDrawContext context = EmiDrawContext.wrap(raw);
        renderHeader(context, mouseX, mouseY);
        renderFooter(context);
        renderTooltip(context, mouseX, mouseY);
    }

    private void renderGraph(GuiGraphics raw, int mouseX, int mouseY, float delta) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        EmiDrawContext context = EmiDrawContext.wrap(raw);
        raw.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        context.fill(viewport.x(), viewport.y(), viewport.width(), viewport.height(), BG_COLOR);
        if (nodes.isEmpty()) {
            context.drawCenteredText(EmiPort.literal(PlannerText.tr("graph.no_recipes", "No active recipes to graph")),
                (int) viewCenterX(), (int) viewCenterY(), 0xFFA0A0AA);
        } else {
            int worldMouseX = toWorldX(mouseX);
            int worldMouseY = toWorldY(mouseY);
            PoseStack view = RenderSystem.getModelViewStack();
            view.pushPose();
            view.translate(viewCenterX(), viewCenterY(), 0.0D);
            view.scale(viewScale, viewScale, 1.0f);
            view.translate(offX, offY, 0.0D);
            EmiPort.applyModelViewMatrix();
            renderGraphContent(raw, worldMouseX, worldMouseY, delta);
            view.popPose();
            EmiPort.applyModelViewMatrix();
        }

        raw.disableScissor();
    }

    /** World-space drawing for a host that owns the camera and clipping. */
    public void renderGraphContent(GuiGraphics graphics, int worldMouseX, int worldMouseY, float delta) {
        EmiDrawContext context = EmiDrawContext.wrap(graphics);
        Set<GraphNode> focusedNodes = focusNodes();
        boolean focusActive = viewMode != ViewMode.FULL && !focusedNodes.isEmpty();
        double left = getViewLeft(), top = getViewTop();
        double right = left + viewport.width() / viewScale, bottom = top + viewport.height() / viewScale;
        cycleLabelRects.clear();
        for (GraphEdge edge : edges) {
            if (!edgeShown(edge)) continue;
            boolean focused = !focusActive || focusedNodes.contains(edge.from) && focusedNodes.contains(edge.to);
            if (edge.from.hidden && edge.from.kind != NodeKind.RECIPE || edge.to.hidden && edge.to.kind != NodeKind.RECIPE) focused = false;
            renderEdge(context, edge, focused);
        }
        for (GraphNode node : nodes) {
            if (!nodeShown(node)) continue;
            // Recipe widgets may be expensive; do not render cards outside the host's visible world rectangle.
            if (node.right() < left || node.x > right || node.y + node.height() < top || node.y > bottom) continue;
            renderNode(context, node, worldMouseX, worldMouseY, delta, !focusActive || focusedNodes.contains(node));
        }
    }

    public Bounds getGraphBounds() {
        return graphBounds;
    }

    public float getViewScale() {
        return viewScale;
    }

    public double getViewLeft() {
        return -offX - viewport.width() / (2.0D * viewScale);
    }

    public double getViewTop() {
        return -offY - viewport.height() / (2.0D * viewScale);
    }

    public void setGraphView(float scale, double left, double top) {
        viewScale = scale;
        offX = -left - viewport.width() / (2.0D * scale);
        offY = -top - viewport.height() / (2.0D * scale);
    }

    public boolean hasNodeAt(double x, double y) {
        return viewport.contains((int) x, (int) y) && hoveredNode(x, y) != null;
    }

    public void setEmbeddedViewport(int x, int y, int width, int height) {
        embedded = true;
        boolean first = viewport == EMPTY;
        if (!first && viewport.x() == x && viewport.y() == y && viewport.width() == width && viewport.height() == height) return;
        viewport = new Bounds(x, y, Math.max(1, width), Math.max(1, height));
        if (first) {
            rebuildGraph();
            openReadableView();
        }
    }

    public void initEmbedded(int width, int height) {
        embedded = true;
        init(Minecraft.getInstance(), width, height);
    }

    public void renderGraphTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        renderTooltip(EmiDrawContext.wrap(graphics), mouseX, mouseY);
    }

    public boolean isActionEnabled(Action action) {
        return switch (action) {
            case SELECTED_PATH -> selectedNode != null;
            case CONTEXT -> viewMode != ViewMode.FULL;
            case HIDDEN -> activeHiddenCount() > 0;
            default -> true;
        };
    }

    public boolean isActionSelected(Action action) {
        return switch (action) {
            case FULL -> viewMode == ViewMode.FULL;
            case TARGET_PATH -> viewMode == ViewMode.TARGET_PATH;
            case SELECTED_PATH -> viewMode == ViewMode.SELECTED_PATH;
            case BYPRODUCTS -> showByproducts;
            case CONTEXT -> showContext;
            case HIDDEN -> revealHidden;
            default -> false;
        };
    }

    public Component getGraphStats() {
        int zoom = Math.round(viewScale * 100.0f);
        if (!statsDirty && zoom == statsZoom) return graphStats;
        if (statsDirty) {
            visibleRecipeCount = visibleFlowCount = 0;
            for (GraphNode node : nodes) {
                if (node.kind == NodeKind.RECIPE && nodeShown(node)) visibleRecipeCount++;
            }
            for (GraphEdge edge : edges) {
                if (edgeShown(edge)) visibleFlowCount++;
            }
        }
        graphStats = Component.translatableWithFallback("emi.production_planner.graph.stats", statsFormat, visibleRecipeCount, visibleFlowCount, zoom)
            .append(" | ").append(hiddenLabel).append(" ")
            .append(Integer.toString(activeHiddenCount()));
        statsZoom = zoom;
        statsDirty = false;
        return graphStats;
    }

    public void performAction(Action action) {
        if (!isActionEnabled(action)) return;
        switch (action) {
            case FIT -> {
                fitToView();
                return;
            }
            case FULL -> viewMode = ViewMode.FULL;
            case TARGET_PATH -> viewMode = ViewMode.TARGET_PATH;
            case SELECTED_PATH -> viewMode = ViewMode.SELECTED_PATH;
            case BYPRODUCTS -> {
                showByproducts = !showByproducts;
                if (!showByproducts && selectedNode != null && isByproductNode(selectedNode)) {
                    selectedNode = null;
                    if (viewMode == ViewMode.SELECTED_PATH) viewMode = ViewMode.FULL;
                }
            }
            case CONTEXT -> showContext = !showContext;
            case HIDDEN -> {
                if (EmiInput.isShiftDown()) {
                    restoreAllHidden();
                    return;
                }
                revealHidden = !revealHidden;
            }
            case AUTO_LAYOUT -> {
                manualOffsets.clear();
                EmiProductionPlannerGraphPersistence.clear(lineLayoutKey());
            }
        }
        refreshViewLayout(true);
    }

    private void renderHeader(EmiDrawContext context, int mouseX, int mouseY) {
        context.fill(0, 0, width, HEADER_HEIGHT, HEADER_COLOR);
        context.fill(0, HEADER_HEIGHT - 1, width, 1, 0xFF60606A);
        drawButton(context, backButton, mouseX, mouseY, PlannerText.tr("graph.back", "BACK"));
        drawButton(context, fitButton, mouseX, mouseY, PlannerText.tr("graph.fit", "FIT"));
        drawModeButton(context, fullButton, mouseX, mouseY, PlannerText.tr("graph.full", "FULL"), viewMode == ViewMode.FULL, true);
        drawModeButton(context, targetPathButton, mouseX, mouseY, PlannerText.tr("graph.target_path", "TARGET"), viewMode == ViewMode.TARGET_PATH, true);
        drawModeButton(context, selectedPathButton, mouseX, mouseY, PlannerText.tr("graph.selected_path", "SELECTED"), viewMode == ViewMode.SELECTED_PATH, selectedNode != null);
        drawModeButton(context, byproductsButton, mouseX, mouseY,
            PlannerText.tr(showByproducts ? "graph.byproducts_on" : "graph.byproducts_off", showByproducts ? "BYPRODUCTS ON" : "BYPRODUCTS OFF"),
            showByproducts, true);
        drawModeButton(context, contextButton, mouseX, mouseY,
            PlannerText.tr(showContext ? "graph.context_on" : "graph.context_off", showContext ? "CONTEXT ON" : "CONTEXT OFF"),
            showContext, viewMode != ViewMode.FULL);
        int hiddenCount = activeHiddenCount();
        drawModeButton(context, hiddenButton, mouseX, mouseY,
            PlannerText.tr("graph.hidden", "HIDDEN") + " " + hiddenCount, revealHidden, hiddenCount > 0);
        drawButton(context, autoLayoutButton, mouseX, mouseY, PlannerText.tr("graph.auto_layout", "AUTO LAYOUT"));
        String lineName = ProductionPlanner.displayName(ProductionPlanner.getActiveIndex());
        String title = PlannerText.tr("graph.title", "Production Flow Graph") + " - " + lineName;
        int titleX = autoLayoutButton.x() + autoLayoutButton.width() + 10;
        context.drawTextWithShadow(EmiPort.literal(font.plainSubstrByWidth(title, Math.max(80, width - titleX - 300))), titleX, 10, 0xFFFFFFFF);

        int recipeCount = 0;
        for (GraphNode node : nodes) {
            if (node.kind == NodeKind.RECIPE && nodeShown(node)) {
                recipeCount++;
            }
        }
        int flowCount = 0;
        for (GraphEdge edge : edges) {
            if (edgeShown(edge)) {
                flowCount++;
            }
        }
        String stats = PlannerText.tr("graph.stats", "%s recipes | %s flows | zoom %s%%",
            recipeCount, flowCount, Math.round(viewScale * 100.0f));
        int statsWidth = font.width(stats);
        if (width - statsWidth - 8 > titleX + 120) {
            context.drawTextWithShadow(EmiPort.literal(stats), width - statsWidth - 8, 10, 0xFFAAAAAF);
        }
    }

    private void renderFooter(EmiDrawContext context) {
        int y = height - FOOTER_HEIGHT;
        context.fill(0, y, width, FOOTER_HEIGHT, 0xFF17171F);
        context.fill(0, y, width, 1, 0xFF55555F);
        String hint = PlannerText.tr("graph.hint", "LMB node: focus path | Shift+LMB: open recipe | H: hide/show | RMB drag: move | Shift+RMB drag: free move | drag: pan | wheel: zoom | F: fit | Esc: back");
        context.drawTextWithShadow(EmiPort.literal(font.plainSubstrByWidth(hint, Math.max(20, width - 450))), 8, y + 7, 0xFF9B9BA6);
        int legendX = Math.max(8, width - 424);
        drawLegend(context, legendX, y + 7, INPUT_COLOR, PlannerText.tr("graph.external", "External"));
        drawLegend(context, legendX + 92, y + 7, INTERNAL_COLOR, PlannerText.tr("graph.internal", "Internal"));
        drawLegend(context, legendX + 184, y + 7, OUTPUT_COLOR, PlannerText.tr("graph.output", "Output"));
        drawLegend(context, legendX + 276, y + 7, CYCLE_COLOR, PlannerText.tr("graph.recycle", "RECYCLE"));
    }

    private void drawLegend(EmiDrawContext context, int x, int y, int color, String label) {
        context.fill(x, y + 2, 8, 8, color);
        context.drawTextWithShadow(EmiPort.literal(label), x + 12, y, 0xFFC8C8D0);
    }

    private void renderEdge(EmiDrawContext context, GraphEdge edge, boolean focused) {
        int color = edge.cycle ? CYCLE_COLOR : switch (edge.kind) {
            case INPUT -> INPUT_COLOR;
            case INTERNAL -> INTERNAL_COLOR;
            case OUTPUT -> OUTPUT_COLOR;
        };
        if (!focused) {
            color = withAlpha(color, 0x32);
        }
        if (edge.kind == EdgeKind.INPUT) {
            renderInputEdge(context, edge, color);
            return;
        }
        if (edge.kind == EdgeKind.OUTPUT) {
            renderOutputEdge(context, edge, color);
            return;
        }
        renderInternalEdge(context, edge, color);
    }

    private void renderInputEdge(EmiDrawContext context, GraphEdge edge, int color) {
        SlotAnchor target = findSlotAnchor(edge.to, edge.stack, false);
        if (edge.from.target) {
            int sx = edge.from.right();
            int sy = edge.from.centerY();
            int bendX = sx + Math.max(12, (edge.to.x - sx) / 2);
            drawHorizontal(context, sx, bendX, sy, color);
            drawVertical(context, bendX, sy, target.y, color);
            drawHorizontal(context, bendX, target.x, target.y, color);
            drawArrow(context, target.x, target.y, color);
            return;
        }
        int sx = edge.from.x + edge.from.width() / 2;
        int sy = edge.from.y + edge.from.height();
        int laneY = edge.to.y - 12 - Math.abs(edge.routeOffset);
        int sideX = edge.to.x - 12 - Math.abs(edge.routeOffset);
        drawVertical(context, sx, sy, laneY, color);
        drawFlowArrowVertical(context, sx, sy, laneY, color);
        drawHorizontal(context, sx, sideX, laneY, color);
        drawFlowArrowHorizontal(context, sx, sideX, laneY, color);
        drawVertical(context, sideX, laneY, target.y, color);
        drawFlowArrowVertical(context, sideX, laneY, target.y, color);
        drawHorizontal(context, sideX, target.x, target.y, color);
        drawArrow(context, target.x, target.y, color);
    }

    private void renderOutputEdge(EmiDrawContext context, GraphEdge edge, int color) {
        SlotAnchor source = findSlotAnchor(edge.from, edge.stack, true);
        if (edge.to.target) {
            int tx = edge.to.x;
            int ty = edge.to.centerY();
            int bendX = source.x + Math.max(12, (tx - source.x) / 2);
            drawHorizontal(context, source.x, bendX, source.y, color);
            drawVertical(context, bendX, source.y, ty, color);
            drawHorizontal(context, bendX, tx, ty, color);
            drawArrow(context, tx, ty, color);
            return;
        }
        int tx = edge.to.x + edge.to.width() / 2;
        int ty = edge.to.y;
        int sideX = edge.from.right() + 12 + Math.abs(edge.routeOffset);
        int laneY = edge.from.y + edge.from.height() + 12 + Math.abs(edge.routeOffset);
        drawHorizontal(context, source.x, sideX, source.y, color);
        drawFlowArrowHorizontal(context, source.x, sideX, source.y, color);
        drawVertical(context, sideX, source.y, laneY, color);
        drawFlowArrowVertical(context, sideX, source.y, laneY, color);
        drawHorizontal(context, sideX, tx, laneY, color);
        drawFlowArrowHorizontal(context, sideX, tx, laneY, color);
        drawVertical(context, tx, laneY, ty, color);
        drawArrowDown(context, tx, ty, color);
    }

    private void renderInternalEdge(EmiDrawContext context, GraphEdge edge, int color) {
        if (edge.cycle) {
            renderCycleEdge(context, edge, color);
            return;
        }
        if (!edge.routePoints.isEmpty()) {
            renderSmartInternalEdge(context, edge, color);
            return;
        }
        if (edge.sameComponent) {
            renderComponentEdge(context, edge, color);
            return;
        }
        SlotAnchor source = findSlotAnchor(edge.from, edge.stack, true);
        SlotAnchor target = findSlotAnchor(edge.to, edge.stack, false);
        int sourceExit = edge.from.right() + 10;
        int targetEntry = edge.to.x - 10;
        drawHorizontal(context, source.x, sourceExit, source.y, color);
        if (edge.from.band == edge.to.band && targetEntry > sourceExit + 20) {
            int bendX = sourceExit + (targetEntry - sourceExit) / 2 + edge.routeOffset;
            bendX = Mth.clamp(bendX, sourceExit + 8, targetEntry - 8);
            drawHorizontal(context, sourceExit, bendX, source.y, color);
            drawFlowArrowHorizontal(context, sourceExit, bendX, source.y, color);
            drawVertical(context, bendX, source.y, target.y, color);
            drawFlowArrowVertical(context, bendX, source.y, target.y, color);
            drawHorizontal(context, bendX, targetEntry, target.y, color);
            drawFlowArrowHorizontal(context, bendX, targetEntry, target.y, color);
            drawHorizontal(context, targetEntry, target.x, target.y, color);
            drawArrow(context, target.x, target.y, color);
            renderInternalEdgeLabel(context, edge, bendX, targetEntry, target.y, color);
            return;
        }
        BandBounds fromBand = bandBounds.get(edge.from.band);
        BandBounds toBand = bandBounds.get(edge.to.band);
        int right = Math.max(fromBand == null ? edge.from.right() : fromBand.right,
            toBand == null ? edge.to.right() : toBand.right);
        int corridorX = right + layoutSideLane + Math.abs(edge.routeOffset);
        int laneY;
        if (fromBand != null && toBand != null && edge.from.band != edge.to.band) {
            if (edge.from.band < edge.to.band) {
                laneY = fromBand.bottom + Math.max(18, (toBand.top - fromBand.bottom) / 2) + edge.routeOffset;
            } else {
                laneY = toBand.bottom + Math.max(18, (fromBand.top - toBand.bottom) / 2) + edge.routeOffset;
            }
        } else {
            laneY = Math.max(edge.from.y + edge.from.height(), edge.to.y + edge.to.height()) + 36 + Math.abs(edge.routeOffset);
        }
        drawHorizontal(context, sourceExit, corridorX, source.y, color);
        drawFlowArrowHorizontal(context, sourceExit, corridorX, source.y, color);
        drawVertical(context, corridorX, source.y, laneY, color);
        drawFlowArrowVertical(context, corridorX, source.y, laneY, color);
        drawHorizontal(context, targetEntry, corridorX, laneY, color);
        drawFlowArrowHorizontal(context, corridorX, targetEntry, laneY, color);
        drawVertical(context, targetEntry, laneY, target.y, color);
        drawFlowArrowVertical(context, targetEntry, laneY, target.y, color);
        drawHorizontal(context, targetEntry, target.x, target.y, color);
        drawArrow(context, target.x, target.y, color);
        renderInternalEdgeLabel(context, edge, targetEntry, corridorX, laneY, color);
    }

    private void renderSmartInternalEdge(EmiDrawContext context, GraphEdge edge, int color) {
        List<RouteSegment> segments = routeSegments(edge.routePoints);
        if (segments.isEmpty()) {
            return;
        }
        RouteSegment longest = null;
        RouteSegment longestHorizontal = null;
        for (RouteSegment segment : segments) {
            if (segment.horizontal()) {
                drawHorizontal(context, segment.from.x, segment.to.x, segment.from.y, color);
                if (longestHorizontal == null || segment.length() > longestHorizontal.length()) {
                    longestHorizontal = segment;
                }
            } else if (segment.vertical()) {
                drawVertical(context, segment.from.x, segment.from.y, segment.to.y, color);
            }
            if (longest == null || segment.length() > longest.length()) {
                longest = segment;
            }
        }
        if (longest != null && longest.length() >= 70) {
            if (longest.horizontal()) {
                drawFlowArrowHorizontal(context, longest.from.x, longest.to.x, longest.from.y, color);
            } else {
                drawFlowArrowVertical(context, longest.from.x, longest.from.y, longest.to.y, color);
            }
        }
        RouteSegment last = segments.get(segments.size() - 1);
        drawRouteArrow(context, last, color);
        if (longestHorizontal != null) {
            renderInternalEdgeLabel(context, edge, longestHorizontal.from.x, longestHorizontal.to.x, longestHorizontal.from.y, color);
        }
    }

    private void drawRouteArrow(EmiDrawContext context, RouteSegment segment, int color) {
        RoutePoint end = segment.to;
        if (segment.horizontal()) {
            if (end.x >= segment.from.x) {
                drawArrow(context, end.x, end.y, color);
            } else {
                drawArrowLeft(context, end.x, end.y, color);
            }
        } else if (end.y >= segment.from.y) {
            drawArrowDown(context, end.x, end.y, color);
        } else {
            drawArrowUp(context, end.x, end.y, color);
        }
    }

    private void renderComponentEdge(EmiDrawContext context, GraphEdge edge, int color) {
        SlotAnchor source = findSlotAnchor(edge.from, edge.stack, true);
        SlotAnchor target = findSlotAnchor(edge.to, edge.stack, false);
        int sourceExit = edge.from.right() + 12 + Math.abs(edge.routeOffset);
        int targetEntry = edge.to.x - 12 - Math.abs(edge.routeOffset);
        int sourceBottom = edge.from.y + edge.from.height() + outputBlockHeight(edge.from);
        int targetTop = edge.to.y - inputBlockHeight(edge.to);
        int laneY = sourceBottom < targetTop
            ? sourceBottom + Math.max(12, (targetTop - sourceBottom) / 2)
            : Math.max(sourceBottom, targetTop) + 22 + Math.abs(edge.routeOffset);
        drawHorizontal(context, source.x, sourceExit, source.y, color);
        drawVertical(context, sourceExit, source.y, laneY, color);
        drawFlowArrowVertical(context, sourceExit, source.y, laneY, color);
        drawHorizontal(context, sourceExit, targetEntry, laneY, color);
        drawFlowArrowHorizontal(context, sourceExit, targetEntry, laneY, color);
        drawVertical(context, targetEntry, laneY, target.y, color);
        drawFlowArrowVertical(context, targetEntry, laneY, target.y, color);
        drawHorizontal(context, targetEntry, target.x, target.y, color);
        drawArrow(context, target.x, target.y, color);
        renderInternalEdgeLabel(context, edge, sourceExit, targetEntry, laneY, color);
    }

    private void renderInternalEdgeLabel(EmiDrawContext context, GraphEdge edge, int x1, int x2, int y, int color) {
        float threshold = viewMode == ViewMode.FULL ? 0.58f : 0.38f;
        if (viewScale < threshold || edge.cycle || edge.stack == null || edge.stack.isEmpty()) {
            return;
        }
        int available = Math.abs(x2 - x1) - 16;
        if (available < 72) {
            return;
        }
        String name = edge.stack.getName().getString();
        String rate = formatCompactRate(edge.rate, edge.approximate) + unitSuffixShort(edge.stack);
        String text = font.plainSubstrByWidth(name + "  " + rate, Math.min(180, available));
        int textWidth = font.width(text);
        if (textWidth + 10 > available) {
            return;
        }
        int center = (x1 + x2) / 2;
        int left = center - textWidth / 2 - 4;
        context.fill(left, y - 7, textWidth + 8, 12, 0xE80E0E14);
        context.drawTextWithShadow(EmiPort.literal(text), left + 4, y - 5, color);
    }

    private void renderCycleEdge(EmiDrawContext context, GraphEdge edge, int color) {
        if (edge.routePoints.isEmpty()) {
            renderComponentEdge(context, edge, color);
            return;
        }
        List<RouteSegment> segments = routeSegments(edge.routePoints);
        if (segments.isEmpty()) {
            return;
        }
        RouteSegment longest = null;
        RouteSegment longestHorizontal = null;
        for (RouteSegment segment : segments) {
            if (segment.horizontal()) {
                drawHorizontal(context, segment.from.x, segment.to.x, segment.from.y, color);
                if (longestHorizontal == null || segment.length() > longestHorizontal.length()) {
                    longestHorizontal = segment;
                }
            } else if (segment.vertical()) {
                drawVertical(context, segment.from.x, segment.from.y, segment.to.y, color);
            }
            if (longest == null || segment.length() > longest.length()) {
                longest = segment;
            }
        }
        if (longest != null && longest.length() >= 70) {
            if (longest.horizontal()) {
                drawFlowArrowHorizontal(context, longest.from.x, longest.to.x, longest.from.y, color);
            } else {
                drawFlowArrowVertical(context, longest.from.x, longest.from.y, longest.to.y, color);
            }
        }
        drawRouteArrow(context, segments.get(segments.size() - 1), color);
        if (edge.cycleLabel && viewScale >= 0.36f && longestHorizontal != null && longestHorizontal.length() >= 88) {
            CycleLabelPlacement placement = findCycleLabelPlacement(edge, segments);
            if (placement != null) {
                context.fill(placement.x, placement.y, placement.width, 11, 0xEE101017);
                context.drawTextWithShadow(EmiPort.literal(placement.text), placement.x + 4, placement.y + 2, color);
                cycleLabelRects.add(new RouteRect(placement.x - 3, placement.y - 3, placement.x + placement.width + 3, placement.y + 14));
            }
        }
    }

    private CycleLabelPlacement findCycleLabelPlacement(GraphEdge edge, List<RouteSegment> segments) {
        List<RouteSegment> horizontal = new ArrayList<>();
        for (RouteSegment segment : segments) {
            if (segment.horizontal() && segment.length() >= 88) {
                horizontal.add(segment);
            }
        }
        horizontal.sort((a, b) -> Integer.compare(b.length(), a.length()));
        String label = PlannerText.tr("graph.recycle", "RECYCLE") + ": " + edge.stack.getName().getString();
        String rate = formatCompactRate(edge.rate, edge.approximate) + unitSuffixShort(edge.stack);
        String fullText = label + "  " + rate;
        for (RouteSegment segment : horizontal) {
            String text = font.plainSubstrByWidth(fullText, Math.max(90, segment.length() - 24));
            int labelWidth = font.width(text) + 8;
            int centerX = (segment.from.x + segment.to.x) / 2;
            int x = centerX - labelWidth / 2;
            int[] yCandidates = { segment.from.y - 15, segment.from.y + 4 };
            for (int y : yCandidates) {
                RouteRect rect = new RouteRect(x - 3, y - 3, x + labelWidth + 3, y + 14);
                if (cycleLabelRectAvailable(rect)) {
                    return new CycleLabelPlacement(text, x, y, labelWidth);
                }
            }
        }
        if (horizontal.isEmpty()) {
            return null;
        }
        RouteSegment segment = horizontal.get(0);
        String text = font.plainSubstrByWidth(fullText, Math.max(90, segment.length() - 24));
        int labelWidth = font.width(text) + 8;
        int x = (segment.from.x + segment.to.x) / 2 - labelWidth / 2;
        int baseY = segment.from.y <= Math.min(edge.from.y, edge.to.y) ? segment.from.y - 15 : segment.from.y + 4;
        int direction = baseY < segment.from.y ? -1 : 1;
        for (int step = 0; step < 8; step++) {
            int y = baseY + direction * step * 13;
            RouteRect rect = new RouteRect(x - 3, y - 3, x + labelWidth + 3, y + 14);
            if (cycleLabelRectAvailable(rect)) {
                return new CycleLabelPlacement(text, x, y, labelWidth);
            }
        }
        return null;
    }

    private boolean cycleLabelRectAvailable(RouteRect rect) {
        for (GraphNode node : nodes) {
            if (!nodeShown(node)) {
                continue;
            }
            RouteRect nodeRect = new RouteRect(node.x - 4, node.y - 4, node.right() + 4, node.y + node.height() + 4);
            if (rectsOverlap(rect, nodeRect)) {
                return false;
            }
        }
        for (RouteRect placed : cycleLabelRects) {
            if (rectsOverlap(rect, placed)) {
                return false;
            }
        }
        return true;
    }

    private boolean rectsOverlap(RouteRect a, RouteRect b) {
        return a.right > b.left && a.left < b.right && a.bottom > b.top && a.top < b.bottom;
    }


    private void renderNode(EmiDrawContext context, GraphNode node, int mouseX, int mouseY, float delta, boolean focused) {
        boolean hovered = node.contains(mouseX, mouseY);
        if (node.hidden && node.kind == NodeKind.RECIPE) {
            int border = node == selectedNode ? 0xFF73C7FF : hovered ? HOVER_BORDER_COLOR : 0xFF777782;
            context.fill(node.x, node.y, node.width(), node.height(), 0xEE111118);
            drawBorder(context, node.x, node.y, node.width(), node.height(), border);
            EmiStack icon = firstOutput(node.recipe);
            if (!icon.isEmpty()) {
                context.drawStack(icon, node.x + 6, node.y + 4, EmiIngredient.RENDER_ICON);
            }
            context.drawCenteredText(EmiPort.literal("..."), node.x + node.width() / 2 + 8, node.y + 8, 0xFFB0B0BA);
            if (!focused) {
                context.fill(node.x, node.y, node.width(), node.height(), 0xB20B0B12);
            }
            return;
        }
        int fill = PANEL_COLOR;
        int border = hovered ? HOVER_BORDER_COLOR : BORDER_COLOR;
        if (node.kind == NodeKind.RECIPE) {
            LoadInfo load = loadInfo(node.entry, node.rate);
            if (load.available) {
                fill = switch (load.state) {
                    case SAFE -> LOAD_SAFE_BG;
                    case MEDIUM -> LOAD_MEDIUM_BG;
                    case BOTTLENECK -> LOAD_BOTTLENECK_BG;
                    case NONE -> PANEL_COLOR;
                };
                border = hovered ? HOVER_BORDER_COLOR : switch (load.state) {
                    case SAFE -> LOAD_SAFE_BORDER;
                    case MEDIUM -> LOAD_MEDIUM_BORDER;
                    case BOTTLENECK -> LOAD_BOTTLENECK_BORDER;
                    case NONE -> BORDER_COLOR;
                };
            }
        } else if (node.target) {
            fill = 0xEE2A2410;
            border = hovered ? 0xFFFFFFFF : TARGET_COLOR;
        }
        if (node == selectedNode) {
            border = 0xFF73C7FF;
        }
        context.fill(node.x, node.y, node.width(), node.height(), fill);
        drawBorder(context, node.x, node.y, node.width(), node.height(), border);
        if (node.target) {
            context.fill(node.x, node.y, 4, node.height(), TARGET_COLOR);
            drawBorder(context, node.x + 2, node.y + 2, node.width() - 4, node.height() - 4, withAlpha(TARGET_COLOR, 0x88));
        }

        if (node.kind == NodeKind.RECIPE) {
            EmiStack machineIcon = node.entry.getMachineProfile().icon();
            int textX = node.x + 7;
            if (machineIcon != null && !machineIcon.isEmpty()) {
                context.drawStack(machineIcon, node.x + 5, node.y + 3, EmiIngredient.RENDER_ICON);
                textX = node.x + 25;
            }
            String machine = font.plainSubstrByWidth(node.entry.getMachineProfileName(), node.right() - textX - 6);
            context.drawTextWithShadow(EmiPort.literal(machine), textX, node.y + 6, 0xFFFFFFFF);
            if (viewScale >= FULL_RECIPE_ZOOM) {
                renderRecipeWidgets(context, node, mouseX, mouseY, delta);
            } else {
                EmiStack output = firstOutput(node.recipe);
                if (!output.isEmpty()) {
                    context.drawStack(output, node.x + node.width() / 2 - 8, node.y + RECIPE_HEADER_HEIGHT + 8, EmiIngredient.RENDER_ICON);
                }
            }
            int footerY = node.y + node.height() - RECIPE_FOOTER_HEIGHT;
            context.fill(node.x + 1, footerY, node.width() - 2, 1, 0x664C4C55);
            String result = font.plainSubstrByWidth(recipeName(node.recipe), node.width() - 12);
            context.drawTextWithShadow(EmiPort.literal(result), node.x + 6, footerY + 4, 0xFFDADAE1);
            String rate = formatCompactRate(node.rate, false) + "/s";
            String details = "M " + node.entry.getMachines() + "  P " + node.entry.getParallel() + "  " + node.entry.getVoltageName() + "  " + rate;
            context.drawTextWithShadow(EmiPort.literal(font.plainSubstrByWidth(details, node.width() - 12)), node.x + 6, footerY + 17, 0xFF9797A3);
        } else {
            context.drawStack(node.stack, node.x + 8, node.y + (node.target ? 14 : 10), EmiIngredient.RENDER_ICON);
            String rawTitle = node.target
                ? PlannerText.tr("graph.target_badge", "TARGET") + "  " + node.stack.getName().getString()
                : node.stack.getName().getString();
            String title = font.plainSubstrByWidth(rawTitle, node.width() - 35);
            context.drawTextWithShadow(EmiPort.literal(title), node.x + 31, node.y + (node.target ? 9 : 7), node.target ? TARGET_COLOR : 0xFFFFFFFF);
            String prefix = node.kind == NodeKind.INPUT
                ? PlannerText.tr(node.target ? "graph.target_input" : "graph.external_input", node.target ? "Target input" : "External input")
                : PlannerText.tr(node.target ? "graph.target_output" : "graph.net_output", node.target ? "Target output" : "Net output");
            String value = prefix + ": " + formatCompactRate(node.rate, false) + unitSuffixShort(node.stack);
            context.drawTextWithShadow(EmiPort.literal(font.plainSubstrByWidth(value, node.width() - 31)), node.x + 31, node.y + (node.target ? 27 : 21),
                node.kind == NodeKind.INPUT ? INPUT_COLOR : OUTPUT_COLOR);
        }
        if (node.hidden && revealHidden) {
            context.fill(node.x, node.y, node.width(), node.height(), 0xA80B0B12);
            drawBorder(context, node.x, node.y, node.width(), node.height(), 0xFF9B7CC7);
            context.drawCenteredText(EmiPort.literal(PlannerText.tr("graph.hidden_node", "HIDDEN")),
                node.x + node.width() / 2, node.y + node.height() / 2 - 4, 0xFFC9A8EF);
        }
        if (!focused) {
            context.fill(node.x, node.y, node.width(), node.height(), 0xB20B0B12);
        }
    }

    private void renderRecipeWidgets(EmiDrawContext context, GraphNode node, int mouseX, int mouseY, float delta) {
        if (node.widgets == null) {
            return;
        }
        int localX = mouseX - node.recipeOriginX();
        int localY = mouseY - node.recipeOriginY();
        context.push();
        context.matrices().translate(node.recipeOriginX(), node.recipeOriginY(), 0);
        EmiPort.applyModelViewMatrix();
        for (Widget widget : node.widgets.widgets) {
            widget.render(context.raw(), localX, localY, delta);
        }
        context.pop();
        EmiPort.applyModelViewMatrix();
    }


    private void renderTooltip(EmiDrawContext context, int mouseX, int mouseY) {
        if (!embedded) {
            if (backButton.contains(mouseX, mouseY)) {
                drawTooltip(context, mouseX, mouseY, PlannerText.tr("graph.back_help", "Return to Production Planner"));
                return;
            }
            if (fitButton.contains(mouseX, mouseY)) {
                drawTooltip(context, mouseX, mouseY, PlannerText.tr("graph.fit_help", "Fit the whole graph to the screen"), "[F]");
                return;
            }
            if (fullButton.contains(mouseX, mouseY)) {
                drawTooltip(context, mouseX, mouseY, PlannerText.tr("graph.full_help", "Show the complete production graph"));
                return;
            }
            if (targetPathButton.contains(mouseX, mouseY)) {
                drawTooltip(context, mouseX, mouseY, PlannerText.tr("graph.target_path_help", "Highlight only flows that lead to the selected Line target"));
                return;
            }
            if (selectedPathButton.contains(mouseX, mouseY)) {
                drawTooltip(context, mouseX, mouseY, selectedNode == null
                    ? PlannerText.tr("graph.selected_path_empty", "Select a recipe or resource first")
                    : PlannerText.tr("graph.selected_path_help", "Highlight inputs and target path through the selected node"));
                return;
            }
            if (byproductsButton.contains(mouseX, mouseY)) {
                drawTooltip(context, mouseX, mouseY, PlannerText.tr("graph.byproducts_help", "Show or hide non-target net output cards"));
                return;
            }
            if (contextButton.contains(mouseX, mouseY)) {
                drawTooltip(context, mouseX, mouseY, PlannerText.tr("graph.context_help", "Keep unrelated branches visible while focusing a path"));
                return;
            }
            if (hiddenButton.contains(mouseX, mouseY)) {
                drawTooltip(context, mouseX, mouseY,
                    PlannerText.tr("graph.hidden_help", "Click to reveal hidden nodes; Shift+click restores all hidden nodes"));
                return;
            }
            if (autoLayoutButton.contains(mouseX, mouseY)) {
                drawTooltip(context, mouseX, mouseY, PlannerText.tr("graph.auto_layout_help", "Clear manual node positions and rebuild the automatic layout"));
                return;
            }
        }
        if (!viewport.contains(mouseX, mouseY)) {
            return;
        }
        GraphNode node = hoveredNode(mouseX, mouseY);
        if (node == null) {
            return;
        }
        int worldX = toWorldX(mouseX);
        int worldY = toWorldY(mouseY);
        if (node.kind == NodeKind.RECIPE && !node.hidden && viewScale >= FULL_RECIPE_ZOOM) {
            List<ClientTooltipComponent> widgetTooltip = recipeWidgetTooltip(node, worldX, worldY);
            if (!widgetTooltip.isEmpty()) {
                EmiRenderHelper.drawTooltip(this, context, widgetTooltip, mouseX, mouseY);
                return;
            }
        }
        List<ClientTooltipComponent> tooltip = new ArrayList<>();
        if (node.kind == NodeKind.RECIPE) {
            tooltip.add(ClientTooltipComponent.create(EmiPort.literal(recipeName(node.recipe)).getVisualOrderText()));
            tooltip.add(ClientTooltipComponent.create(EmiPort.literal(node.entry.getMachineProfileName()).getVisualOrderText()));
            tooltip.add(ClientTooltipComponent.create(EmiPort.literal(PlannerText.tr("graph.rate", "Rate") + ": " + formatExactRate(node.rate) + "/s").getVisualOrderText()));
            tooltip.add(ClientTooltipComponent.create(EmiPort.translatable("emi.production_planner.graph.machine_settings",
                node.entry.getMachines(), node.entry.getParallel(), node.entry.getVoltageName()).getVisualOrderText()));
            if (node.entry.getGroupId() > 0) {
                tooltip.add(ClientTooltipComponent.create(EmiPort.literal(PlannerText.tr("graph.group", "Group") + ": " + line.getEntryGroupName(node.entry)).getVisualOrderText()));
            }
            LoadInfo load = loadInfo(node.entry, node.rate);
            if (load.available) {
                tooltip.add(ClientTooltipComponent.create(EmiPort.literal(PlannerText.tr("graph.load", "Load") + ": "
                    + trimNumber(load.percent, 3) + "% - " + load.state.displayName()).getVisualOrderText()));
            }
            tooltip.add(ClientTooltipComponent.create(EmiPort.literal(PlannerText.tr("graph.focus_path", "Click to focus this path")).getVisualOrderText()));
            tooltip.add(ClientTooltipComponent.create(EmiPort.literal(PlannerText.tr("graph.open_recipe", "Shift+click to open recipe")).getVisualOrderText()));
            tooltip.add(ClientTooltipComponent.create(EmiPort.literal(PlannerText.tr("graph.move_node", "Right-drag to move; snaps to grid. Hold Shift for free movement; the position is saved")).getVisualOrderText()));
            if (canHide(node)) {
                tooltip.add(ClientTooltipComponent.create(EmiPort.literal(PlannerText.tr(node.hidden ? "graph.show_node" : "graph.hide_node", node.hidden ? "H: show node" : "H: hide node")).getVisualOrderText()));
            }
        } else {
            tooltip.addAll(node.stack.getTooltip());
            String kind = node.kind == NodeKind.INPUT
                ? PlannerText.tr(node.target ? "graph.target_input" : "graph.external_input", node.target ? "Target input" : "External input")
                : PlannerText.tr(node.target ? "graph.target_output" : "graph.net_output", node.target ? "Target output" : "Net output");
            tooltip.add(ClientTooltipComponent.create(EmiPort.literal(kind + ": " + formatExactRate(node.rate) + unitSuffixShort(node.stack)).getVisualOrderText()));
            tooltip.add(ClientTooltipComponent.create(EmiPort.literal(PlannerText.tr("graph.focus_path", "Click to focus this path")).getVisualOrderText()));
            tooltip.add(ClientTooltipComponent.create(EmiPort.literal(PlannerText.tr("graph.open_recipe", "Shift+click to open recipe")).getVisualOrderText()));
            tooltip.add(ClientTooltipComponent.create(EmiPort.literal(PlannerText.tr("graph.move_node", "Right-drag to move; snaps to grid. Hold Shift for free movement; the position is saved")).getVisualOrderText()));
            if (canHide(node)) {
                tooltip.add(ClientTooltipComponent.create(EmiPort.literal(PlannerText.tr(node.hidden ? "graph.show_node" : "graph.hide_node", node.hidden ? "H: show node" : "H: hide node")).getVisualOrderText()));
            }
        }
        EmiRenderHelper.drawTooltip(this, context, tooltip, mouseX, mouseY);
    }

    private List<ClientTooltipComponent> recipeWidgetTooltip(GraphNode node, int worldX, int worldY) {
        if (node.widgets == null) {
            return List.of();
        }
        int localX = worldX - node.recipeOriginX();
        int localY = worldY - node.recipeOriginY();
        for (int i = node.widgets.widgets.size() - 1; i >= 0; i--) {
            Widget widget = node.widgets.widgets.get(i);
            if (widget.getBounds().contains(localX, localY)) {
                List<ClientTooltipComponent> tooltip = widget.getTooltip(localX, localY);
                if (!tooltip.isEmpty()) {
                    return tooltip;
                }
            }
        }
        return List.of();
    }

    private void drawTooltip(EmiDrawContext context, int mouseX, int mouseY, String... lines) {
        List<ClientTooltipComponent> tooltip = new ArrayList<>();
        for (String line : lines) {
            tooltip.add(ClientTooltipComponent.create(EmiPort.literal(line).getVisualOrderText()));
        }
        EmiRenderHelper.drawTooltip(this, context, tooltip, mouseX, mouseY);
    }

    private LoadInfo loadInfo(Entry entry, double rate) {
        if (entry == null || !line.isBalanceEnabled() || rate <= EPSILON) {
            return LoadInfo.none();
        }
        MachineSizing sizing = entry.getMachineSizing(rate);
        if (!sizing.available() || !Double.isFinite(sizing.capacityRate()) || sizing.capacityRate() <= EPSILON) {
            return LoadInfo.none();
        }
        double percent = Math.max(0.0D, rate / sizing.capacityRate() * 100.0D);
        boolean propagated = line.hasMachineCapacityShortfall()
            && entry.getMachineProfileName().equals(line.getBottleneckName()) && percent >= 99.0D;
        LoadState state = !sizing.sufficient() || percent > 100.0D + EPSILON || propagated
            ? LoadState.BOTTLENECK : percent >= LOAD_MEDIUM_THRESHOLD ? LoadState.MEDIUM : LoadState.SAFE;
        return new LoadInfo(true, Math.min(percent, 99999.0D), state);
    }

    private TargetMode targetMode(EmiStack stack) {
        for (Target target : line.getTargets()) {
            if (target.getStack().isEqual(stack, EmiPort.compareStrict())) {
                return target.getMode();
            }
        }
        return null;
    }

    private GraphNode hoveredNode(double screenX, double screenY) {
        int wx = toWorldX(screenX);
        int wy = toWorldY(screenY);
        for (int i = nodes.size() - 1; i >= 0; i--) {
            GraphNode node = nodes.get(i);
            if (!nodeShown(node)) {
                continue;
            }
            if (node.contains(wx, wy)) {
                return node;
            }
        }
        return null;
    }

    private boolean baseNodeShown(GraphNode node) {
        if (node == null) {
            return false;
        }
        if (node.hidden && node.kind != NodeKind.RECIPE && !revealHidden) {
            return false;
        }
        if (node.hidden && revealHidden) {
            return true;
        }
        return showByproducts || !isByproductNode(node);
    }

    private boolean baseEdgeShown(GraphEdge edge) {
        return edge != null && baseNodeShown(edge.from) && baseNodeShown(edge.to);
    }

    private boolean nodeShown(GraphNode node) {
        if (!baseNodeShown(node)) {
            return false;
        }
        if (viewMode == ViewMode.FULL || showContext) {
            return true;
        }
        Set<GraphNode> focused = focusNodes();
        return focused.isEmpty() || focused.contains(node);
    }

    private boolean edgeShown(GraphEdge edge) {
        return edge != null && nodeShown(edge.from) && nodeShown(edge.to);
    }

    private boolean isByproductNode(GraphNode node) {
        return node != null && node.kind == NodeKind.OUTPUT && !node.target;
    }

    private Set<GraphNode> focusNodes() {
        if (cachedFocus == null) cachedFocus = computeFocusNodes();
        return cachedFocus;
    }

    private Set<GraphNode> computeFocusNodes() {
        if (viewMode == ViewMode.FULL) {
            return Set.of();
        }
        if (viewMode == ViewMode.TARGET_PATH) {
            Set<GraphNode> targets = targetNodes();
            if (targets.isEmpty()) {
                return Set.of();
            }
            Set<GraphNode> result = new LinkedHashSet<>();
            for (GraphNode target : targets) {
                result.addAll(traverse(Set.of(target), target.kind == NodeKind.OUTPUT));
            }
            return result;
        }
        if (selectedNode == null || !baseNodeShown(selectedNode)) {
            return Set.of();
        }
        Set<GraphNode> ancestors = traverse(Set.of(selectedNode), true);
        Set<GraphNode> descendants = traverse(Set.of(selectedNode), false);
        Set<GraphNode> targets = targetNodes();
        if (!targets.isEmpty()) {
            Set<GraphNode> targetAncestors = traverse(targets, true);
            descendants.retainAll(targetAncestors);
        }
        ancestors.addAll(descendants);
        ancestors.add(selectedNode);
        return ancestors;
    }

    private Set<GraphNode> targetNodes() {
        Set<GraphNode> result = new LinkedHashSet<>();
        for (GraphNode node : nodes) {
            if (node.target && baseNodeShown(node)) {
                result.add(node);
            }
        }
        return result;
    }

    private Set<GraphNode> traverse(Set<GraphNode> starts, boolean reverse) {
        Set<GraphNode> visited = new LinkedHashSet<>();
        Deque<GraphNode> queue = new ArrayDeque<>();
        for (GraphNode start : starts) {
            if (start != null && baseNodeShown(start) && visited.add(start)) {
                queue.addLast(start);
            }
        }
        while (!queue.isEmpty()) {
            GraphNode current = queue.removeFirst();
            for (GraphEdge edge : edges) {
                if (!baseEdgeShown(edge)) {
                    continue;
                }
                GraphNode next = null;
                if (reverse && edge.to == current) {
                    next = edge.from;
                } else if (!reverse && edge.from == current) {
                    next = edge.to;
                }
                if (next != null && visited.add(next)) {
                    queue.addLast(next);
                }
            }
        }
        return visited;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!embedded && button == 0) {
            if (backButton.contains((int) mouseX, (int) mouseY)) {
                onClose();
                return true;
            }
            Action action;
            if (fitButton.contains((int) mouseX, (int) mouseY)) action = Action.FIT;
            else if (fullButton.contains((int) mouseX, (int) mouseY)) action = Action.FULL;
            else if (targetPathButton.contains((int) mouseX, (int) mouseY)) action = Action.TARGET_PATH;
            else if (selectedPathButton.contains((int) mouseX, (int) mouseY)) action = Action.SELECTED_PATH;
            else if (byproductsButton.contains((int) mouseX, (int) mouseY)) action = Action.BYPRODUCTS;
            else if (contextButton.contains((int) mouseX, (int) mouseY)) action = Action.CONTEXT;
            else if (hiddenButton.contains((int) mouseX, (int) mouseY)) action = Action.HIDDEN;
            else if (autoLayoutButton.contains((int) mouseX, (int) mouseY)) action = Action.AUTO_LAYOUT;
            else action = null;
            if (action != null) {
                performAction(action);
                return true;
            }
        }
        if (viewport.contains((int) mouseX, (int) mouseY)) {
            GraphNode node = hoveredNode(mouseX, mouseY);
            if (button == 1 && node != null) {
                draggingNode = node;
                dragStartWorldX = toWorldX(mouseX);
                dragStartWorldY = toWorldY(mouseY);
                dragStartNodeX = node.x;
                dragStartNodeY = node.y;
                dragMoved = false;
                return true;
            }
            if (button == 0 && node != null) {
                if (EmiInput.isShiftDown()) {
                    Minecraft.getInstance().setScreen(parent);
                    if (node.kind == NodeKind.RECIPE && node.recipe != null) {
                        EmiApi.displayRecipe(node.recipe);
                    } else if (node.stack != null && !node.stack.isEmpty()) {
                        EmiApi.displayRecipes(node.stack);
                    }
                } else {
                    selectedNode = node;
                    viewMode = ViewMode.SELECTED_PATH;
                    refreshViewLayout(true);
                }
                return true;
            }
            if (button == 0 || button == 2) {
                panning = true;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        panning = false;
        if (button == 1 && draggingNode != null) {
            if (dragMoved) {
                manualOffsets.put(nodeLayoutKey(draggingNode), new EmiProductionPlannerGraphPersistence.Offset(
                    draggingNode.x - draggingNode.autoX, draggingNode.y - draggingNode.autoY));
                EmiProductionPlannerGraphPersistence.save(lineLayoutKey(), manualOffsets);
            }
            draggingNode = null;
            dragMoved = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 1 && draggingNode != null) {
            int worldX = toWorldX(mouseX);
            int worldY = toWorldY(mouseY);
            int nextX = dragStartNodeX + worldX - dragStartWorldX;
            int nextY = dragStartNodeY + worldY - dragStartWorldY;
            if (!EmiInput.isShiftDown()) {
                nextX = snap(nextX, SNAP_GRID);
                nextY = snap(nextY, SNAP_GRID);
            }
            draggingNode.x = nextX;
            draggingNode.y = nextY;
            dragMoved = true;
            refreshRoutesAfterManualMove();
            return true;
        }
        if (panning && (button == 0 || button == 2)) {
            offX += deltaX / viewScale;
            offY += deltaY / viewScale;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!viewport.contains((int) mouseX, (int) mouseY)) return false;
        if (amount == 0.0D) {
            return true;
        }
        float oldScale = viewScale;
        viewScale = Mth.clamp((float) (viewScale * Math.pow(1.12D, amount)), 0.10f, 3.0f);
        if (oldScale != viewScale) {
            double worldX = (mouseX - viewCenterX()) / oldScale - offX;
            double worldY = (mouseY - viewCenterY()) / oldScale - offY;
            offX = (mouseX - viewCenterX()) / viewScale - worldX;
            offY = (mouseY - viewCenterY()) / viewScale - worldY;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_H) {
            if (!viewport.contains(lastMouseX, lastMouseY)) return false;
            GraphNode node = hoveredNode(lastMouseX, lastMouseY);
            if (node != null && canHide(node)) {
                toggleHidden(node);
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_F) {
            fitToView();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int snap(int value, int grid) {
        return grid <= 1 ? value : Math.round(value / (float) grid) * grid;
    }

    private double viewCenterY() {
        return viewport.y() + viewport.height() / 2.0D;
    }

    private double viewCenterX() {
        return viewport.x() + viewport.width() / 2.0D;
    }

    private int toWorldX(double screenX) {
        return (int) ((screenX - viewCenterX()) / viewScale - offX);
    }

    private int toWorldY(double screenY) {
        return (int) ((screenY - viewCenterY()) / viewScale - offY);
    }

    private SlotAnchor findSlotAnchor(GraphNode node, EmiStack stack, boolean output) {
        if (node.hidden && node.kind == NodeKind.RECIPE) {
            return new SlotAnchor(output ? node.right() : node.x, node.centerY());
        }
        if (node.widgets != null) {
            SlotWidget best = null;
            for (Widget widget : node.widgets.widgets) {
                if (!(widget instanceof SlotWidget slot) || !stackMatches(slot.getStack(), stack)) {
                    continue;
                }
                boolean slotOutput = slot.getRecipe() != null;
                if (slotOutput != output) {
                    continue;
                }
                if (best == null) {
                    best = slot;
                } else if (output && slot.getBounds().x() > best.getBounds().x()) {
                    best = slot;
                } else if (!output && slot.getBounds().x() < best.getBounds().x()) {
                    best = slot;
                }
            }
            if (best == null) {
                for (Widget widget : node.widgets.widgets) {
                    if (widget instanceof SlotWidget slot && stackMatches(slot.getStack(), stack)) {
                        if (best == null || output && slot.getBounds().x() > best.getBounds().x()
                            || !output && slot.getBounds().x() < best.getBounds().x()) {
                            best = slot;
                        }
                    }
                }
            }
            if (best != null) {
                Bounds bounds = best.getBounds();
                int x = node.recipeOriginX() + (output ? bounds.x() + bounds.width() : bounds.x());
                int y = node.recipeOriginY() + bounds.y() + bounds.height() / 2;
                return new SlotAnchor(x, y);
            }
        }
        return new SlotAnchor(output ? node.right() : node.x, node.centerY());
    }

    private boolean stackMatches(EmiIngredient ingredient, EmiStack stack) {
        if (ingredient == null || stack == null || stack.isEmpty()) {
            return false;
        }
        for (EmiStack candidate : ingredient.getEmiStacks()) {
            if (candidate != null && !candidate.isEmpty() && candidate.isEqual(stack, EmiPort.compareStrict())) {
                return true;
            }
        }
        return false;
    }

    private void drawButton(EmiDrawContext context, Bounds bounds, int mouseX, int mouseY, String label) {
        boolean hovered = bounds.contains(mouseX, mouseY);
        context.fill(bounds.x(), bounds.y(), bounds.width(), bounds.height(), hovered ? 0xFF3A3A44 : 0xFF2B2B32);
        drawBorder(context, bounds.x(), bounds.y(), bounds.width(), bounds.height(), hovered ? 0xFFD0D0D8 : BORDER_COLOR);
        context.drawCenteredText(EmiPort.literal(label), bounds.x() + bounds.width() / 2, bounds.y() + 6, 0xFFFFFFFF);
    }

    private void drawModeButton(EmiDrawContext context, Bounds bounds, int mouseX, int mouseY, String label, boolean active, boolean enabled) {
        boolean hovered = enabled && bounds.contains(mouseX, mouseY);
        int fill = !enabled ? 0xFF25252B : active ? 0xFF355048 : hovered ? 0xFF3A3A44 : 0xFF2B2B32;
        int border = !enabled ? 0xFF44444B : active ? 0xFF78C7A8 : hovered ? 0xFFD0D0D8 : BORDER_COLOR;
        int text = enabled ? 0xFFFFFFFF : 0xFF777780;
        context.fill(bounds.x(), bounds.y(), bounds.width(), bounds.height(), fill);
        drawBorder(context, bounds.x(), bounds.y(), bounds.width(), bounds.height(), border);
        context.drawCenteredText(EmiPort.literal(font.plainSubstrByWidth(label, bounds.width() - 8)), bounds.x() + bounds.width() / 2, bounds.y() + 6, text);
    }

    private int withAlpha(int color, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (color & 0x00FFFFFF);
    }

    private void drawFlowArrowHorizontal(EmiDrawContext context, int x1, int x2, int y, int color) {
        if (Math.abs(x2 - x1) < 70) {
            return;
        }
        int x = (x1 + x2) / 2;
        if (x2 > x1) {
            context.fill(x - 3, y - 3, 1, 7, color);
            context.fill(x - 2, y - 2, 1, 5, color);
            context.fill(x - 1, y - 1, 2, 3, color);
        } else {
            context.fill(x + 3, y - 3, 1, 7, color);
            context.fill(x + 2, y - 2, 1, 5, color);
            context.fill(x, y - 1, 2, 3, color);
        }
    }

    private void drawFlowArrowVertical(EmiDrawContext context, int x, int y1, int y2, int color) {
        if (Math.abs(y2 - y1) < 70) {
            return;
        }
        int y = (y1 + y2) / 2;
        if (y2 > y1) {
            context.fill(x - 3, y - 3, 7, 1, color);
            context.fill(x - 2, y - 2, 5, 1, color);
            context.fill(x - 1, y - 1, 3, 2, color);
        } else {
            context.fill(x - 3, y + 3, 7, 1, color);
            context.fill(x - 2, y + 2, 5, 1, color);
            context.fill(x - 1, y, 3, 2, color);
        }
    }

    private void drawHorizontal(EmiDrawContext context, int x1, int x2, int y, int color) {
        int left = Math.min(x1, x2);
        int width = Math.max(1, Math.abs(x2 - x1));
        context.fill(left, y, width, 2, color);
    }

    private void drawVertical(EmiDrawContext context, int x, int y1, int y2, int color) {
        int top = Math.min(y1, y2);
        int height = Math.max(1, Math.abs(y2 - y1));
        context.fill(x, top, 2, height, color);
    }

    private void drawArrow(EmiDrawContext context, int x, int y, int color) {
        context.fill(x - 5, y - 4, 5, 1, color);
        context.fill(x - 4, y - 3, 4, 1, color);
        context.fill(x - 3, y - 2, 3, 1, color);
        context.fill(x - 2, y - 1, 2, 4, color);
        context.fill(x - 3, y + 3, 3, 1, color);
        context.fill(x - 4, y + 4, 4, 1, color);
    }

    private void drawArrowDown(EmiDrawContext context, int x, int y, int color) {
        context.fill(x - 4, y - 5, 9, 1, color);
        context.fill(x - 3, y - 4, 7, 1, color);
        context.fill(x - 2, y - 3, 5, 1, color);
        context.fill(x - 1, y - 2, 3, 1, color);
        context.fill(x, y - 1, 1, 2, color);
    }

    private void drawArrowLeft(EmiDrawContext context, int x, int y, int color) {
        context.fill(x, y - 4, 5, 1, color);
        context.fill(x, y - 3, 4, 1, color);
        context.fill(x, y - 2, 3, 1, color);
        context.fill(x, y - 1, 2, 4, color);
        context.fill(x, y + 3, 3, 1, color);
        context.fill(x, y + 4, 4, 1, color);
    }

    private void drawArrowUp(EmiDrawContext context, int x, int y, int color) {
        context.fill(x - 4, y, 9, 1, color);
        context.fill(x - 3, y + 1, 7, 1, color);
        context.fill(x - 2, y + 2, 5, 1, color);
        context.fill(x - 1, y + 3, 3, 1, color);
        context.fill(x, y + 4, 1, 2, color);
    }

    private void drawBorder(EmiDrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x, y, width, 1, color);
        context.fill(x, y + height - 1, width, 1, color);
        context.fill(x, y, 1, height, color);
        context.fill(x + width - 1, y, 1, height, color);
    }

    private EmiStack firstStack(EmiIngredient ingredient) {
        for (EmiStack stack : ingredient.getEmiStacks()) {
            if (!stack.isEmpty()) {
                return stack;
            }
        }
        return EmiStack.EMPTY;
    }

    private EmiStack firstOutput(EmiRecipe recipe) {
        if (recipe != null) {
            for (EmiStack stack : recipe.getOutputs()) {
                if (stack != null && !stack.isEmpty()) {
                    return stack;
                }
            }
        }
        return EmiStack.EMPTY;
    }

    private String recipeName(EmiRecipe recipe) {
        EmiStack output = firstOutput(recipe);
        if (!output.isEmpty()) {
            return output.getName().getString();
        }
        return recipe == null || recipe.getId() == null ? PlannerText.tr("graph.recipe", "Recipe") : recipe.getId().toString();
    }

    private EmiStack normalize(EmiStack stack) {
        return stack.copy().setAmount(1).setChance(1);
    }


    private String unitSuffixShort(EmiStack stack) {
        return stack.getKey() instanceof Fluid ? " mB/s" : "/s";
    }

    private String formatCompactRate(double value, boolean approximate) {
        double abs = Math.abs(value);
        String[] suffixes = { "", "K", "M", "G", "T", "P", "E" };
        int unit = 0;
        while (abs >= 1000.0D && unit < suffixes.length - 1) {
            abs /= 1000.0D;
            unit++;
        }
        if (value < 0.0D) {
            abs = -abs;
        }
        String number;
        if (Math.abs(abs) >= 100.0D || unit == 0 && Math.abs(abs) >= 10.0D) {
            number = BigDecimal.valueOf(abs).setScale(0, RoundingMode.HALF_UP).toPlainString();
        } else if (Math.abs(abs) >= 10.0D) {
            number = trimNumber(abs, 1);
        } else {
            number = trimNumber(abs, 2);
        }
        return (approximate ? "~" : "") + number + suffixes[unit];
    }

    private String formatExactRate(double value) {
        return trimNumber(value, 4);
    }

    private String trimNumber(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private enum ViewMode {
        FULL,
        TARGET_PATH,
        SELECTED_PATH
    }

    private enum NodeKind {
        RECIPE,
        INPUT,
        OUTPUT
    }

    private enum EdgeKind {
        INPUT,
        INTERNAL,
        OUTPUT
    }

    private enum LoadState {
        NONE,
        SAFE,
        MEDIUM,
        BOTTLENECK;

        private String displayName() {
            return switch (this) {
                case SAFE -> PlannerText.tr("load.status.safe", "Healthy");
                case MEDIUM -> PlannerText.tr("load.status.medium", "High load");
                case BOTTLENECK -> PlannerText.tr("load.status.bottleneck", "BOTTLENECK");
                case NONE -> "";
            };
        }
    }

    private record LoadInfo(boolean available, double percent, LoadState state) {
        private static LoadInfo none() {
            return new LoadInfo(false, 0.0D, LoadState.NONE);
        }
    }

    private static final class GraphNode {
        private final NodeKind kind;
        private final Entry entry;
        private final EmiRecipe recipe;
        private final EmiStack stack;
        private final double rate;
        private final boolean target;
        private final int order;
        private final dev.emi.emi.screen.WidgetGroup widgets;
        private final int cardWidth;
        private final int cardHeight;
        private int depth;
        private int band;
        private int x;
        private int y;
        private int autoX;
        private int autoY;
        private boolean hidden;

        private GraphNode(NodeKind kind, Entry entry, EmiRecipe recipe, EmiStack stack, double rate, boolean target, int order,
                          WidgetGroup widgets, int cardWidth, int cardHeight) {
            this.kind = kind;
            this.entry = entry;
            this.recipe = recipe;
            this.stack = stack == null ? EmiStack.EMPTY : stack;
            this.rate = rate;
            this.target = target;
            this.order = order;
            this.widgets = widgets;
            this.cardWidth = cardWidth;
            this.cardHeight = cardHeight;
        }

        private static GraphNode recipe(Entry entry, EmiRecipe recipe, double rate, int order) {
            int displayWidth = Math.max(1, recipe.getDisplayWidth());
            int displayHeight = Math.max(1, recipe.getDisplayHeight());
            WidgetGroup widgets = new WidgetGroup(recipe, 0, 0, displayWidth, displayHeight);
            try {
                recipe.addWidgets(widgets);
            } catch (Throwable t) {
                widgets.error(t);
            }
            int width = Math.max(RECIPE_MIN_WIDTH, displayWidth + RECIPE_PADDING * 2 + 8);
            int height = RECIPE_HEADER_HEIGHT + displayHeight + RECIPE_FOOTER_HEIGHT + RECIPE_PADDING * 2;
            return new GraphNode(NodeKind.RECIPE, entry, recipe, EmiStack.EMPTY, rate, false, order, widgets, width, height);
        }

        private static GraphNode resource(NodeKind kind, EmiStack stack, double rate, boolean target, int order) {
            return new GraphNode(kind, null, null, stack, rate, target, order, null, target ? 190 : RESOURCE_WIDTH, target ? 44 : RESOURCE_HEIGHT);
        }

        private int width() {
            return hidden && kind == NodeKind.RECIPE ? HIDDEN_RECIPE_WIDTH : cardWidth;
        }

        private int height() {
            return hidden && kind == NodeKind.RECIPE ? HIDDEN_RECIPE_HEIGHT : cardHeight;
        }

        private int right() {
            return x + width();
        }

        private int centerX() {
            return x + width() / 2;
        }

        private int centerY() {
            return y + height() / 2;
        }

        private int recipeOriginX() {
            if (widgets == null) {
                return x;
            }
            return x + (width() - widgets.width) / 2;
        }

        private int recipeOriginY() {
            return y + RECIPE_HEADER_HEIGHT + RECIPE_PADDING;
        }

        private boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseY >= y && mouseX < right() && mouseY < y + height();
        }
    }

    private static final class GraphEdge {
        private final GraphNode from;
        private final GraphNode to;
        private final EmiStack stack;
        private final double rate;
        private final boolean approximate;
        private final EdgeKind kind;
        private boolean sameComponent;
        private boolean cycle;
        private int cycleComponent = -1;
        private int cycleLane;
        private boolean cycleLabel;
        private int routeOffset;
        private List<RoutePoint> routePoints = List.of();

        private GraphEdge(GraphNode from, GraphNode to, EmiStack stack, double rate, boolean approximate, EdgeKind kind) {
            this.from = from;
            this.to = to;
            this.stack = stack;
            this.rate = rate;
            this.approximate = approximate;
            this.kind = kind;
        }
    }

    private record RoutePoint(int x, int y) {
    }

    private record RouteSegment(RoutePoint from, RoutePoint to) {
        private boolean horizontal() {
            return from.y == to.y;
        }

        private boolean vertical() {
            return from.x == to.x;
        }

        private int length() {
            return Math.abs(to.x - from.x) + Math.abs(to.y - from.y);
        }
    }

    private record CycleLabelPlacement(String text, int x, int y, int width) {
    }

    private record RouteRect(int left, int top, int right, int bottom) {
    }

    private record SlotAnchor(int x, int y) {
    }

    private record BandBounds(int left, int right, int top, int bottom) {
    }

    private record CycleBounds(int left, int right, int top, int bottom, int band) {
    }

    private static final class ResourcePool {
        private final EmiStack stack;
        private final List<FlowPart> producers = new ArrayList<>();
        private final List<FlowPart> consumers = new ArrayList<>();

        private ResourcePool(EmiStack stack) {
            this.stack = stack;
        }
    }

    private static final class FlowPart {
        private final GraphNode node;
        private double amount;
        private final boolean approximate;

        private FlowPart(GraphNode node, double amount, boolean approximate) {
            this.node = node;
            this.amount = amount;
            this.approximate = approximate;
        }
    }
}
