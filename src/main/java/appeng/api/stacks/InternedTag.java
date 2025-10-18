package appeng.api.stacks;

import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;

public class InternedTag {

    public static final InternedTag EMPTY = new InternedTag(null);

    public static final WeakHashMap<InternedTag, WeakReference<InternedTag>> INTERNED = new WeakHashMap<>();

    public CompoundTag tag;
    public final int hashCode;

    public InternedTag(CompoundTag tag) {
        this.tag = tag;
        this.hashCode = Objects.hashCode(tag);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        InternedTag internedTag = (InternedTag) o;
        return Objects.equals(tag, internedTag.tag);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    public static InternedTag of(@Nullable CompoundTag tag, boolean copy) {
        if (tag == null) {
            return EMPTY;
        }
        var searchHolder = new InternedTag(tag);
        synchronized (AEItemKey.class) {
            var weakRef = INTERNED.get(searchHolder);
            InternedTag ret = null;

            if (weakRef != null) {
                ret = weakRef.get();
            }

            if (ret == null) {
                ret = searchHolder;
                // Copy the tag if we don't get to have ownership of it
                if (copy) {
                    ret.tag = tag.copy();
                }
                INTERNED.put(ret, new WeakReference<>(ret));
            }

            return ret;
        }
    }
}
