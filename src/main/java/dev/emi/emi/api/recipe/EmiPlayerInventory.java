package dev.emi.emi.api.recipe;

import java.util.*;
import java.util.function.Predicate;

import com.gto.fastcollection.fastutil.O2OOpenCacheHashMap;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import dev.emi.emi.EmiPort;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.recipe.handler.StandardRecipeHandler;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.registry.EmiRecipeFiller;
import dev.emi.emi.registry.EmiStackList;
import dev.emi.emi.runtime.EmiFavorite;
import it.unimi.dsi.fastutil.booleans.BooleanArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

@SuppressWarnings({ "unchecked", "rawtypes" })
public class EmiPlayerInventory {
    private final Comparison none = Comparison.DEFAULT_COMPARISON;
    private final Comparison strict = EmiPort.compareStrict();
    // Public Map descriptor is retained for EMI integrations; internal lookups use the concrete container.
    private final O2OOpenCacheHashMap<EmiStack, EmiStack> contents;
    public final Map<EmiStack, EmiStack> inventory;
    private final Reference2LongOpenHashMap<EmiStack> used = new Reference2LongOpenHashMap<>();

    @Deprecated
    @ApiStatus.Internal
    public EmiPlayerInventory(@NotNull Player entity) {
        contents = new O2OOpenCacheHashMap<>(entity.getInventory().items.size());
        inventory = contents;
        AbstractContainerScreen<?> screen = EmiApi.getHandledScreen();
        if (screen != null && screen.getMenu() != null) {
            if (screen.getMenu().getCarried() != null) {
                addStack(screen.getMenu().getCarried());
            }
            List<EmiRecipeHandler<?>> handlers = (List) EmiRecipeFiller.getAllHandlers(screen);
            if (!handlers.isEmpty()) {
                if (handlers.getFirst() instanceof StandardRecipeHandler standard) {
                    List<Slot> slots = standard.getInputSources(screen.getMenu());
                    for (Slot slot : slots) {
                        if (slot.mayPickup(entity)) {
                            addStack(slot.getItem());
                        }
                    }
                    return;
                }
            }
        }

        Inventory pInv = entity.getInventory();
        for (int i = 0; i < pInv.items.size(); i++) {
            addStack(pInv.items.get(i));
        }
    }

    public EmiPlayerInventory(@NotNull List<@NotNull EmiStack> stacks) {
        contents = new O2OOpenCacheHashMap<>(stacks.size());
        inventory = contents;
        for (EmiStack stack : stacks) {
            addStack(stack);
        }
        AbstractContainerScreen<?> screen = EmiApi.getHandledScreen();
        if (screen != null && screen.getMenu() != null) {
            if (screen.getMenu().getCarried() != null) {
                addStack(screen.getMenu().getCarried());
            }
        }
    }

    public static @NotNull EmiPlayerInventory of(@Nullable Player entity) {
        AbstractContainerScreen<?> screen = EmiApi.getHandledScreen();
        if (screen != null) {
            List<EmiRecipeHandler<?>> handlers = (List) EmiRecipeFiller.getAllHandlers(screen);
            if (!handlers.isEmpty()) {
                return handlers.getFirst().getInventory((AbstractContainerScreen) screen);
            }
        }
        if (entity == null) {
            return new EmiPlayerInventory(Collections.emptyList());
        }
        return new EmiPlayerInventory(entity);
    }

    private void addStack(ItemStack is) {
        EmiStack stack = EmiStack.of(is).comparison(c -> none);
        addStack(stack);
    }

    private void addStack(EmiStack stack) {
        if (!stack.isEmpty()) {
            contents.merge(stack, stack, (a, b) -> a.setAmount(a.getAmount() + b.getAmount()));
        }
    }

    public @Nullable Predicate<dev.emi.emi.api.recipe.EmiRecipe> getPredicate() {
        AbstractContainerScreen screen = EmiApi.getHandledScreen();
        List<EmiRecipeHandler> handlers = EmiRecipeFiller.getAllHandlers(screen);
        if (!handlers.isEmpty()) {
            EmiCraftContext context = new EmiCraftContext(screen, this, EmiCraftContext.Type.CRAFTABLE);
            return r -> {
                for (EmiRecipeHandler handler : handlers) {
                    if (handler.supportsRecipe(r)) {
                        return handler.canCraft(r, context);
                    }
                }
                return false;
            };
        }
        return null;
    }

    public List<EmiIngredient> getCraftables() {
        var predicate = getPredicate();
        if (predicate == null)
            return Collections.emptyList();
        var recipes = new ReferenceOpenHashSet<EmiRecipe>(contents.size());
        for (var stack : contents.keySet()) {
            recipes.addAll(EmiApi.getRecipeManager().getRecipesByInput(stack));
        }
        var craftables = new ObjectArrayList<EmiIngredient>(recipes.size());
        for (var recipe : recipes) {
            if (!recipe.hideCraftable() && !recipe.getOutputs().isEmpty() && predicate.test(recipe)) {
                craftables.add(new EmiFavorite.Craftable(recipe));
            }
        }
        craftables
                .sort(Comparator.comparingInt((EmiIngredient a) -> EmiStackList.getIndex(((EmiFavorite) a).getStack()))
                        .thenComparingLong(EmiIngredient::getAmount));
        return craftables;
    }

    public List<Boolean> getCraftAvailability(EmiRecipe recipe) {
        used.clear();
        var states = new BooleanArrayList(recipe.getInputs().size());
        for (var ingredient : recipe.getInputs()) {
            states.add(reserveIngredient(ingredient, 1));
        }
        return states;
    }

    public boolean canCraft(EmiRecipe recipe) {
        return canCraft(recipe, 1);
    }

    public boolean canCraft(EmiRecipe recipe, long amount) {
        used.clear();
        for (var ingredient : recipe.getInputs()) {
            if (!ingredient.isEmpty() && !reserveIngredient(ingredient, amount)) {
                return false;
            }
        }
        return true;
    }

    private boolean reserveIngredient(EmiIngredient ingredient, long amount) {
        for (var stack : ingredient.getEmiStacks()) {
            var identity = contents.get(stack);
            if (identity != null) {
                long desired = stack.getAmount() * amount;
                long alreadyUsed = used.getLong(identity);
                if (identity.getAmount() - alreadyUsed >= desired) {
                    used.put(identity, desired + alreadyUsed);
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isEqual(EmiPlayerInventory other) {
        if (other == null || other.contents.size() != contents.size())
            return false;
        for (var stack : contents.values()) {
            var candidate = other.contents.get(stack);
            if (candidate == null || candidate.getAmount() != stack.getAmount() || !strict.compare(candidate, stack)) {
                return false;
            }
        }
        return true;
    }
}
