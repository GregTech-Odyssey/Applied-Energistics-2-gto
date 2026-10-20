package dev.emi.emi.network;

import dev.emi.emi.network.EmiNetwork;
import dev.emi.emi.network.EmiPacket;
import dev.emi.emi.runtime.EmiLog;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class CreateItemC2SPacket implements EmiPacket {
    private final int mode;
    private final ItemStack stack;

    public CreateItemC2SPacket(int mode, ItemStack stack) {
        this.mode = mode;
        this.stack = stack;
    }

    public CreateItemC2SPacket(FriendlyByteBuf buf) {
        this(buf.readByte(), buf.readItem());
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeByte(mode);
        buf.writeItem(stack);
    }

    @Override
    public void apply(Player player) {
        if ((player.hasPermissions(2) || player.isCreative()) && player.containerMenu != null) {
            if (stack.isEmpty()) {
                if (mode == 1 && !player.containerMenu.getCarried().isEmpty()) {
                    EmiLog.info(player.getScoreboardName() + " deleted " + player.containerMenu.getCarried());
                    player.containerMenu.setCarried(stack);
                }
            } else {
                EmiLog.info(player.getScoreboardName() + " cheated in " + stack);
                if (mode == 0) {
                    player.getInventory().placeItemBackInInventory(stack);
                } else if (mode == 1) {
                    player.containerMenu.setCarried(stack);
                }
            }
        }
    }

    @Override
    public ResourceLocation getId() {
        return EmiNetwork.CREATE_ITEM;
    }
}
