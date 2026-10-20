package dev.emi.emi.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

public interface EmiPacket {
	
	void write(FriendlyByteBuf buf);
	
	void apply(Player player);

	ResourceLocation getId();
}
