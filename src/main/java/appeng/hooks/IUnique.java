package appeng.hooks;

import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.core.registries.BuiltInRegistries;

public interface IUnique {

    AtomicInteger ID = new AtomicInteger(1);

    Object LOCK = new Object();

    int ae2$getUid();

    void ae2$setUid();

    static void assignAll() {
        for (var block : BuiltInRegistries.BLOCK)
            ((IUnique) block).ae2$setUid();
        for (var item : BuiltInRegistries.ITEM)
            ((IUnique) item).ae2$setUid();
        for (var fluid : BuiltInRegistries.FLUID)
            ((IUnique) fluid).ae2$setUid();
    }
}
