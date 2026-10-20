package appeng.mixins.emi;

import java.util.Optional;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import dev.emi.emi.EmiPort;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.runtime.EmiSidebars;

@Mixin(ResultSlot.class)
public class CraftingResultSlotMixin {
    @Shadow
    @Final
    private CraftingContainer craftSlots;
    @Shadow
    @Final
    private Player player;

    @Inject(at = @At("HEAD"), method = "checkTakeAchievements(Lnet/minecraft/world/item/ItemStack;)V")
    private void onCrafted(ItemStack stack, CallbackInfo info) {
        Level world = player.level();
        if (world.isClientSide) {
            Optional<CraftingRecipe> opt = world.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, craftSlots,
                    world);
            if (opt.isPresent()) {
                EmiRecipe recipe = EmiApi.getRecipeManager().getRecipe(EmiPort.getId(opt.get()));
                if (recipe != null) {
                    EmiSidebars.craft(recipe);
                }
            }
        }
    }
}
