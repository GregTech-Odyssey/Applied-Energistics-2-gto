package appeng.mixins.emi.accessor;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.resources.ResourceLocation;

@Mixin(ModelManager.class)
public interface BakedModelManagerAccessor {

    @Accessor("bakedRegistry")
    Map<ResourceLocation, BakedModel> getModels();
}
