package appeng.api.stacks;

import java.util.function.Function;
import java.util.function.UnaryOperator;

import com.gto.fastcollection.cache.WeakValueHashCache;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;

public final class IdentityTag {

    public static final IdentityTag EMPTY = new IdentityTag(null);

    public static final Function<CompoundTag, IdentityTag> CREATE_FUNCTION = IdentityTag::new;
    public static final UnaryOperator<CompoundTag> COPY = CompoundTag::copy;

    public static final WeakValueHashCache<CompoundTag, IdentityTag> CACHE = new WeakValueHashCache<>();

    public CompoundTag tag;

    private IdentityTag(CompoundTag tag) {
        this.tag = tag;
    }

    public static IdentityTag of(@Nullable CompoundTag tag, boolean copy) {
        if (tag == null) {
            return EMPTY;
        } else if (copy) {
            return CACHE.getCache(tag, CREATE_FUNCTION, COPY);
        } else {
            return CACHE.getCache(tag, CREATE_FUNCTION);
        }
    }
}
