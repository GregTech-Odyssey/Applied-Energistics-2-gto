package appeng.integration.modules.emi;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import dev.emi.emi.api.EmiHooks;
import dev.emi.emi.api.stack.EmiStack;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.me.common.StackSizeRenderer;

public final class AppEngEmiHooks {
    private AppEngEmiHooks() {
    }

    public static void register() {
        EmiHooks.setAmountRenderer(AppEngEmiHooks::renderAmount);
        EmiHooks.addFluidResolver(AppEngEmiHooks::resolveFluid);
        EmiHooks.addFavoritesListener(EmiFavoriteKeyCache::recalculate);
    }

    private static boolean renderAmount(GuiGraphics draw, int x, int y, EmiStack stack, long amount) {
        if (stack.getAmount() <= 99) {
            return false;
        }
        var s = EmiStackHelper.toAEKey(stack);
        if (s != null) {
            StackSizeRenderer.renderSizeLabel(draw, Minecraft.getInstance().font, x + 1, y + 1,
                    s.formatAmount(amount, AmountFormat.SLOT), false);
            return true;
        }
        return false;
    }

    private static EmiStack resolveFluid(ItemStack stack) {
        var wrapped = GenericStack.fromItemStack(stack);
        if (wrapped != null && wrapped.what() instanceof AEFluidKey fluid) {
            return EmiStack.of(fluid.fluid, wrapped.amount());
        }
        return null;
    }
}
