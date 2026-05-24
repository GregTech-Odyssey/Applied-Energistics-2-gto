package appeng.client.gui.me.common;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;

class TestKey extends AEKey {
    private final ResourceLocation id;

    TestKey(String path) {
        this.id = new ResourceLocation("ae2", path);
    }

    @Override
    public AEKeyType getType() {
        throw new UnsupportedOperationException();
    }

    @Override
    public AEKey dropSecondary() {
        return this;
    }

    @Override
    public CompoundTag toTag() {
        return new CompoundTag();
    }

    @Override
    public Object getPrimaryKey() {
        return this;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public void writeToPacket(FriendlyByteBuf data) {
    }

    @Override
    protected Component computeDisplayName() {
        return Component.literal(id.getPath());
    }

    @Override
    public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {
    }
}
