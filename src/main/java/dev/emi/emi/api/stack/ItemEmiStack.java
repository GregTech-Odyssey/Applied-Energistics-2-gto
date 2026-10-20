package dev.emi.emi.api.stack;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.platform.Lighting;
import dev.emi.emi.EmiPort;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.api.render.EmiRender;
import dev.emi.emi.forge.EmiForgePlatform;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.StackBatcher.Batchable;
import dev.emi.emi.screen.tooltip.EmiTextTooltipWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import appeng.api.stacks.AEItemKey;

@ApiStatus.Internal
public class ItemEmiStack extends EmiStack implements Batchable {
    private static final Minecraft client = Minecraft.getInstance();

    private final AEItemKey item;

    private boolean unbatchable;

    public ItemEmiStack(ItemStack stack) {
        this(stack, stack.getCount());
    }

    public ItemEmiStack(ItemStack stack, long amount) {
        this.item = AEItemKey.of(stack.getItem(), stack.getTag());
        this.amount = amount;
    }

    public ItemEmiStack(Item item, CompoundTag nbt, long amount) {
        this.item = AEItemKey.of(item, nbt);
        this.amount = amount;
    }

    public ItemEmiStack(AEItemKey item, long amount) {
        this.item = item;
        this.amount = amount;
    }

    @Override
    public ItemStack getItemStack() {
        return item.toStack((int) Math.min(Integer.MAX_VALUE, amount));
    }

    @Override
    public EmiStack copy() {
        EmiStack e = new ItemEmiStack(item, amount);
        e.setChance(chance);
        e.setRemainder(getRemainder().copy());
        e.comparison = comparison;
        return e;
    }

    @Override
    public boolean isEmpty() {
        return amount == 0 || item.getItem() == Items.AIR;
    }

    @Override
    public CompoundTag getNbt() {
        return item.getTag();
    }

    @Override
    public Object getKey() {
        return item.getItem();
    }

    @Override
    public AEItemKey getAEKey() {
        return item;
    }

    @Override
    public ResourceLocation getId() {
        return item.getId();
    }

    @Override
    public void render(GuiGraphics draw, int x, int y, float delta, int flags) {
        EmiDrawContext context = EmiDrawContext.wrap(draw);
        ItemStack stack = getItemStack();
        if ((flags & RENDER_ICON) != 0) {
            Lighting.setupFor3DItems();
            draw.renderFakeItem(stack, x, y);
            draw.renderItemDecorations(client.font, stack, x, y, "");
        }
        if ((flags & RENDER_AMOUNT) != 0) {
            String count = "";
            if (amount != 1) {
                count += amount;
            }
            EmiRenderHelper.renderAmount(context, x, y, this, amount, EmiPort.literal(count));
        }
        if ((flags & RENDER_REMAINDER) != 0) {
            EmiRender.renderRemainderIcon(this, context.raw(), x, y);
        }
    }

    @Override
    public boolean isSideLit() {
        return client.getItemRenderer().getModel(getItemStack(), null, null, 0).usesBlockLight();
    }

    @Override
    public boolean isUnbatchable() {
        ItemStack stack = getItemStack();
        return unbatchable || stack.hasFoil() || stack.isDamaged() || !EmiForgePlatform.canBatch(stack)
            || client.getItemRenderer().getModel(getItemStack(), null, null, 0).isCustomRenderer();
    }

    @Override
    public void setUnbatchable() {
        this.unbatchable = true;
    }

    @Override
    public void renderForBatch(MultiBufferSource vcp, GuiGraphics draw, int x, int y, int z, float delta) {
        EmiDrawContext context = EmiDrawContext.wrap(draw);
        ItemStack stack = getItemStack();
        ItemRenderer ir = client.getItemRenderer();
        BakedModel model = ir.getModel(stack, null, null, 0);
        context.push();
        try {
            context.matrices().translate(x, y, 100.0f + z + (model.isGui3d() ? 50 : 0));
            context.matrices().translate(8.0, 8.0, 0.0);
            context.matrices().scale(16.0f, 16.0f, 16.0f);
            ir.render(stack, ItemDisplayContext.GUI, false, context.matrices(), vcp, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, model);
        } finally {
            context.pop();
        }
    }

    @Override
    public List<Component> getTooltipText() {
        return getItemStack().getTooltipLines(client.player, TooltipFlag.NORMAL);
    }

    @Override
    public List<ClientTooltipComponent> getTooltip() {
        ItemStack stack = getItemStack();
        List<ClientTooltipComponent> list = Lists.newArrayList();
        if (!isEmpty()) {
            list.addAll(EmiForgePlatform.getItemTooltip(stack));
            if (!list.isEmpty() && list.getFirst() instanceof ClientTextTooltip ottc) {
                list.set(0, new EmiTextTooltipWrapper(this, ottc));
            }
            //String namespace = EmiPort.getItemRegistry().getId(stack.getItem()).getNamespace();
            //String mod = EmiUtil.getModName(namespace);
            //list.add(TooltipComponent.of(EmiLang.literal(mod, Formatting.BLUE, Formatting.ITALIC)));
            list.addAll(super.getTooltip());
        }
        return list;
    }

    @Override
    public Component getName() {
        if (isEmpty()) {
            return EmiPort.literal("");
        }
        return getItemStack().getHoverName();
    }

    static class ItemEntry {
    }
}
