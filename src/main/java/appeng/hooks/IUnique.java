package appeng.hooks;

import java.util.concurrent.atomic.AtomicInteger;

import org.jetbrains.annotations.Range;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

public interface IUnique {

    AtomicInteger ID = new AtomicInteger(1);

    @Range(from = 1, to = Integer.MAX_VALUE)
    static int getUid(Item item) {
        return ((IUnique) item).ae2$getUid();
    }

    @Range(from = 1, to = Integer.MAX_VALUE)
    static int getUid(Fluid fluid) {
        return ((IUnique) fluid).ae2$getUid();
    }

    @Range(from = 1, to = Integer.MAX_VALUE)
    static int getUid(Block block) {
        return ((IUnique) block).ae2$getUid();
    }

    @Range(from = 1, to = Integer.MAX_VALUE)
    int ae2$getUid();
}
