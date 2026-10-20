package appeng.mixins.emi.accessor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemStack;

@Mixin(ItemRenderer.class)
public interface ItemRendererAccessor {

    @Invoker("renderModelLists")
    void invokeRenderBakedItemModel(BakedModel model, ItemStack stack, int light, int overlay, PoseStack matrices,
            VertexConsumer vertices);
}
