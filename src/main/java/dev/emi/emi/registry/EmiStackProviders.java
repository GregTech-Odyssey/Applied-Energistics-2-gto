package dev.emi.emi.registry;

import appeng.mixins.emi.accessor.CraftingResultSlotAccessor;
import appeng.mixins.emi.accessor.HandledScreenAccessor;
import com.google.common.collect.Lists;
import dev.emi.emi.EmiPort;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.EmiStackProvider;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import java.util.List;
import java.util.Map;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

public class EmiStackProviders {
    public static Map<Class<?>, List<EmiStackProvider<?>>> fromClass = new Reference2ObjectOpenHashMap<>();
    public static List<EmiStackProvider<?>> generic = Lists.newArrayList();

    public static void clear() {
        fromClass.clear();
        generic.clear();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static EmiStackInteraction getStackAt(Screen screen, int x, int y, boolean notClick) {
        var registered = fromClass.get(screen.getClass());
        if (registered != null) {
            for (EmiStackProvider provider : registered) {
                EmiStackInteraction stack = provider.getStackAt(screen, x, y);
                if (!stack.isEmpty() && (notClick || stack.isClickable())) {
                    return stack;
                }
            }
        }
        for (EmiStackProvider handler : generic) {
            EmiStackInteraction stack = handler.getStackAt(screen, x, y);
            if (!stack.isEmpty() && (notClick || stack.isClickable())) {
                return stack;
            }
        }
        if (notClick && screen instanceof HandledScreenAccessor handled) {
            Slot s = handled.getFocusedSlot();
            if (s != null) {
                ItemStack stack = s.getItem();
                if (!stack.isEmpty()) {
                    if (s instanceof ResultSlot craf) {
                        // Emi be making assumptions
                        try {
                            CraftingContainer inv = ((CraftingResultSlotAccessor) craf).getInput();
                            Minecraft client = Minecraft.getInstance();
                            List<CraftingRecipe> list = client.level.getRecipeManager()
                                    .getRecipesFor(RecipeType.CRAFTING, inv, client.level);
                            if (!list.isEmpty()) {
                                ResourceLocation id = EmiPort.getId(list.get(0));
                                EmiRecipe recipe = EmiApi.getRecipeManager().getRecipe(id);
                                if (recipe != null) {
                                    return new EmiStackInteraction(EmiStack.of(stack), recipe, false);
                                }
                            }
                        } catch (Exception e) {
                        }
                    }
                    return new EmiStackInteraction(EmiStack.of(stack));
                }
            }
        }
        return EmiStackInteraction.EMPTY;
    }
}
