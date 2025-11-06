package appeng.integration.modules.emi;

import static appeng.integration.modules.jeirei.TransferHelper.BLUE_SLOT_HIGHLIGHT_COLOR;
import static appeng.integration.modules.jeirei.TransferHelper.RED_SLOT_HIGHLIGHT_COLOR;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;

import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.StandardRecipeHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.Widget;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

import appeng.api.stacks.AEKey;
import appeng.integration.modules.jeirei.EncodingHelper;
import appeng.integration.modules.jeirei.TransferHelper;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.me.items.CraftingTermMenu;

public abstract class AbstractRecipeHandler<T extends AEBaseMenu> implements StandardRecipeHandler<T> {
    public static final int CRAFTING_GRID_WIDTH = 3;
    public static final int CRAFTING_GRID_HEIGHT = 3;

    public final Class<T> containerClass;
    protected static final ExecutorService executorService = Executors.newSingleThreadExecutor();
    protected static volatile EmiPlayerInventory cachedInventory = null;
    protected static final AtomicBoolean refreshInProgress = new AtomicBoolean(false);

    public AbstractRecipeHandler(Class<T> containerClass) {
        this.containerClass = containerClass;
    }

    @Override
    public List<Slot> getInputSources(T menu) {
        var slots = new ArrayList<Slot>();
        slots.addAll(menu.getSlots(SlotSemantics.PLAYER_INVENTORY));
        slots.addAll(menu.getSlots(SlotSemantics.PLAYER_HOTBAR));
        slots.addAll(menu.getSlots(SlotSemantics.CRAFTING_GRID));
        return slots;
    }

    @Override
    public List<Slot> getCraftingSlots(T menu) {
        return menu.getSlots(SlotSemantics.CRAFTING_GRID);
    }

    @Override
    public @Nullable Slot getOutputSlot(T menu) {
        for (var slot : menu.getSlots(SlotSemantics.CRAFTING_RESULT)) {
            return slot;
        }
        return null;
    }

    @Override
    public EmiPlayerInventory getInventory(AbstractContainerScreen<T> screen) {
        EmiPlayerInventory local = cachedInventory;

        if (refreshInProgress.compareAndSet(false, true)) {
            executorService.submit(() -> {
                try {
                    List<EmiStack> allStack = new ObjectArrayList<>();
                    allStack.addAll(InventoryUtils.getStacks(screen, SlotSemantics.PLAYER_HOTBAR));
                    allStack.addAll(InventoryUtils.getStacks(screen, SlotSemantics.PLAYER_INVENTORY));
                    if (screen.getMenu() instanceof MEStorageMenu menu) {
                        allStack.addAll(InventoryUtils.getExistingStacks(menu));
                    }
                    addToEmiInventory(screen, allStack);
                    cachedInventory = new EmiPlayerInventory(allStack);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    refreshInProgress.set(false);
                }
            });
        }

        if (local != null) {
            return local;
        }

        return StandardRecipeHandler.super.getInventory(screen);
    }

    protected void addToEmiInventory(AbstractContainerScreen<T> screen, List<EmiStack> stacks) {
    }

    @Override
    public boolean canCraft(EmiRecipe recipe, EmiCraftContext<T> context) {
        if (context.getType() == EmiCraftContext.Type.FILL_BUTTON) {
            return transferRecipe(recipe, context, false).canCraft();
        }
        return StandardRecipeHandler.super.canCraft(recipe, context);
    }

    public abstract Result transferRecipe(T menu,
            @Nullable Recipe<?> holder,
            EmiRecipe emiRecipe,
            boolean doTransfer);

    public final Result transferRecipe(EmiRecipe emiRecipe, EmiCraftContext<T> context, boolean doTransfer) {
        if (!containerClass.isInstance(context.getScreenHandler())) {
            return Result.createNotApplicable();
        }

        T menu = containerClass.cast(context.getScreenHandler());

        var holder = getRecipeHolder(context.getScreenHandler().getPlayer().level(), emiRecipe);

        var result = transferRecipe(menu, holder, emiRecipe, doTransfer);
        if (result instanceof Result.Success && doTransfer) {
            Minecraft.getInstance().setScreen(context.getScreen());
        }
        return result;
    }

    @Override
    public boolean supportsRecipe(EmiRecipe recipe) {
        return true;
    }

    @Override
    public boolean craft(EmiRecipe recipe, EmiCraftContext<T> context) {
        return transferRecipe(recipe, context, true).canCraft();
    }

    @Override
    public List<ClientTooltipComponent> getTooltip(EmiRecipe recipe, EmiCraftContext<T> context) {
        var tooltip = transferRecipe(recipe, context, false).getTooltip(recipe, context);
        if (tooltip != null) {
            return tooltip.stream()
                    .map(Component::getVisualOrderText)
                    .map(ClientTooltipComponent::create)
                    .toList();
        } else {
            return StandardRecipeHandler.super.getTooltip(recipe, context);
        }
    }

    @Override
    public void render(EmiRecipe recipe, EmiCraftContext<T> context, List<Widget> widgets, GuiGraphics draw) {
        transferRecipe(recipe, context, false).render(recipe, context, widgets, draw);
    }

    @Nullable
    public Recipe<?> getRecipeHolder(Level level, EmiRecipe recipe) {
        if (recipe.getBackingRecipe() != null) {
            return recipe.getBackingRecipe();
        }
        if (recipe.getId() != null) {
            // TODO: This can produce false positives...
            return level.getRecipeManager().byKey(recipe.getId()).orElse(null);
        }
        return null;
    }

    public final boolean isCraftingRecipe(Recipe<?> recipe, EmiRecipe emiRecipe) {
        return EncodingHelper.isSupportedCraftingRecipe(recipe)
                || emiRecipe.getCategory().equals(VanillaEmiRecipeCategories.CRAFTING);
    }

    public final boolean fitsIn3x3Grid(Recipe<?> recipe, EmiRecipe emiRecipe) {
        if (recipe != null) {
            return recipe.canCraftInDimensions(CRAFTING_GRID_WIDTH, CRAFTING_GRID_HEIGHT);
        } else {
            return true;
        }
    }

    public static abstract class Result {
        /**
         * @return null doesn't override the default tooltip.
         */
        @Nullable
        public List<Component> getTooltip(EmiRecipe recipe, EmiCraftContext<?> context) {
            return null;
        }

        public abstract boolean canCraft();

        public void render(EmiRecipe recipe, EmiCraftContext<? extends AEBaseMenu> context, List<Widget> widgets,
                GuiGraphics draw) {
        }

        public static final class Success extends Result {
            @Override
            public boolean canCraft() {
                return true;
            }
        }

        /**
         * There are missing ingredients, but at least one is present.
         */
        public static class PartiallyCraftable extends Result {
            public final CraftingTermMenu.MissingIngredientSlots missingSlots;

            public PartiallyCraftable(CraftingTermMenu.MissingIngredientSlots missingSlots) {
                this.missingSlots = missingSlots;
            }

            @Override
            public boolean canCraft() {
                return true;
            }

            @Override
            public List<Component> getTooltip(EmiRecipe recipe, EmiCraftContext<?> context) {
                // EMI caches this tooltip, we cannot dynamically react to control being held here
                return TransferHelper.createCraftingTooltip(missingSlots, false);
            }

            @Override
            public void render(EmiRecipe recipe, EmiCraftContext<? extends AEBaseMenu> context, List<Widget> widgets,
                    GuiGraphics guiGraphics) {
                renderMissingAndCraftableSlotOverlays(getRecipeInputSlots(recipe, widgets), guiGraphics,
                        missingSlots.missingSlots(),
                        missingSlots.craftableSlots());
            }
        }

        /**
         * Indicates that some of the slots can already be crafted by the auto-crafting system.
         */
        public static class EncodeWithCraftables extends Result {
            public final Set<AEKey> craftableKeys;

            /**
             * @param craftableKeys All keys that the current system can auto-craft.
             */
            public EncodeWithCraftables(Set<AEKey> craftableKeys) {
                this.craftableKeys = craftableKeys;
            }

            @Override
            public boolean canCraft() {
                return true;
            }

            @Override
            public List<Component> getTooltip(EmiRecipe emiRecipe, EmiCraftContext<?> context) {
                var anyCraftable = emiRecipe.getInputs().stream()
                        .anyMatch(ing -> isCraftable(craftableKeys, ing));
                if (anyCraftable) {
                    return TransferHelper.createEncodingTooltip(true);
                }
                return null;
            }

            @Override
            public void render(EmiRecipe recipe, EmiCraftContext<? extends AEBaseMenu> context, List<Widget> widgets,
                    GuiGraphics guiGraphics) {
                for (var widget : widgets) {
                    if (widget instanceof SlotWidget slot && isInputSlot(slot)) {
                        if (isCraftable(craftableKeys, slot.getStack())) {
                            var poseStack = guiGraphics.pose();
                            poseStack.pushPose();
                            poseStack.translate(0, 0, 400);
                            var bounds = getInnerBounds(slot);
                            guiGraphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(),
                                    BLUE_SLOT_HIGHLIGHT_COLOR);
                            poseStack.popPose();
                        }
                    }
                }
            }

            public static boolean isCraftable(Set<AEKey> craftableKeys, EmiIngredient ingredient) {
                return ingredient.getEmiStacks().stream().anyMatch(emiIngredient -> {
                    var stack = EmiStackHelper.toGenericStack(emiIngredient);
                    return stack != null && craftableKeys.contains(stack.what());
                });
            }
        }

        public static final class NotApplicable extends Result {
            @Override
            public boolean canCraft() {
                return false;
            }
        }

        public static class Error extends Result {
            public final Component message;
            public final IntSet missingSlots;

            public Error(Component message, IntSet missingSlots) {
                this.message = message;
                this.missingSlots = missingSlots;
            }

            public Component getMessage() {
                return message;
            }

            @Override
            public boolean canCraft() {
                return false;
            }

            @Override
            public void render(EmiRecipe recipe, EmiCraftContext<? extends AEBaseMenu> context, List<Widget> widgets,
                    GuiGraphics guiGraphics) {

                renderMissingAndCraftableSlotOverlays(getRecipeInputSlots(recipe, widgets), guiGraphics, missingSlots,
                        IntSets.emptySet());
            }
        }

        public static NotApplicable createNotApplicable() {
            return new NotApplicable();
        }

        public static Success createSuccessful() {
            return new Success();
        }

        public static Error createFailed(Component text) {
            return new Error(text, IntSets.emptySet());
        }

        public static Error createFailed(Component text, IntOpenHashSet missingSlots) {
            return new Error(text, missingSlots);
        }
    }

    public static void renderMissingAndCraftableSlotOverlays(Int2ObjectOpenHashMap<SlotWidget> inputSlots,
            GuiGraphics guiGraphics,
            IntSet missingSlots, IntSet craftableSlots) {
        for (ObjectIterator<Int2ObjectMap.Entry<SlotWidget>> it = inputSlots.int2ObjectEntrySet().fastIterator(); it
                .hasNext();) {
            var entry = it.next();
            boolean missing = missingSlots.contains(entry.getIntKey());
            boolean craftable = craftableSlots.contains(entry.getIntKey());
            if (missing || craftable) {
                var poseStack = guiGraphics.pose();
                poseStack.pushPose();
                poseStack.translate(0, 0, 400);
                var innerBounds = getInnerBounds(entry.getValue());
                guiGraphics.fill(innerBounds.x(), innerBounds.y(), innerBounds.right(),
                        innerBounds.bottom(), missing ? RED_SLOT_HIGHLIGHT_COLOR : BLUE_SLOT_HIGHLIGHT_COLOR);
                poseStack.popPose();
            }
        }
    }

    public static boolean isInputSlot(SlotWidget slot) {
        return slot.getRecipe() == null;
    }

    public static Bounds getInnerBounds(SlotWidget slot) {
        var bounds = slot.getBounds();
        return new Bounds(
                bounds.x() + 1,
                bounds.y() + 1,
                bounds.width() - 2,
                bounds.height() - 2);
    }

    public static Int2ObjectOpenHashMap<SlotWidget> getRecipeInputSlots(EmiRecipe recipe, List<Widget> widgets) {
        // Map ingredient indices to their respective slots
        var inputSlots = new Int2ObjectOpenHashMap<SlotWidget>(recipe.getInputs().size());
        for (int i = 0; i < recipe.getInputs().size(); i++) {
            for (var widget : widgets) {
                if (widget instanceof SlotWidget slot && isInputSlot(slot)) {
                    if (slot.getStack() == recipe.getInputs().get(i)) {
                        inputSlots.put(i, slot);
                    }
                }
            }
        }
        return inputSlots;
    }
}
