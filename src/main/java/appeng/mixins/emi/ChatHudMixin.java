package appeng.mixins.emi;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;

import dev.emi.emi.runtime.RecipeShareLink;

@Mixin(ChatComponent.class)
public class ChatHudMixin {

    @ModifyVariable(at = @At("HEAD"), method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;ILnet/minecraft/client/GuiMessageTag;Z)V", argsOnly = true, ordinal = 0)
    private Component decorateRecipeShare(Component message) {
        return RecipeShareLink.decorateChatMessage(message);
    }
}
