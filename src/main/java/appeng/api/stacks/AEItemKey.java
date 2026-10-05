package appeng.api.stacks;

import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.extensions.IForgeItem;

import appeng.api.storage.AEKeyFilter;
import appeng.core.AELog;
import appeng.hooks.IAEItem;
import appeng.hooks.IUnique;

public final class AEItemKey extends AEKey {

    private static final ClassValue<Boolean> SHARE_TAG_OVERRIDDEN = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("getShareTag", ItemStack.class).getDeclaringClass() != IForgeItem.class;
            } catch (NoSuchMethodException e) {
                return true;
            }
        }
    };

    public final Item item;
    public final int uid;
    @Nullable
    private final CompoundTag internedTag;

    // cache
    @Nullable
    private volatile ItemStack readOnlyStack;
    private int maxStackSize;
    private int fuzzySearchValue;
    private int fuzzySearchMaxValue;

    @ApiStatus.Internal
    public AEItemKey(Item item, @Nullable CompoundTag internedTag) {
        this.item = item;
        this.internedTag = internedTag;
        this.uid = IUnique.getUid(item);
    }

    public static AEItemKey of(ItemLike item) {
        return ((IAEItem) item.asItem()).ae2$getDefaultAEKey();
    }

    public static AEItemKey of(ItemLike item, @Nullable CompoundTag tag) {
        var i = item.asItem();
        return tag == null ? ((IAEItem) i).ae2$getAEKey() : ofTagged(i, tag);
    }

    @Nullable
    public static AEItemKey of(ItemStack stack) {
        var tag = stack.getTag();
        return tag == null ? ofUntagged(stack.getItem()) : ofTagged(stack, tag);
    }

    @Nullable
    private static AEItemKey ofUntagged(Item item) {
        return item == Items.AIR ? null : ((IAEItem) item).ae2$getAEKey();
    }

    @Nullable
    private static AEItemKey ofTagged(ItemStack stack, CompoundTag tag) {
        var item = stack.getItem();
        return item == Items.AIR ? null : ofTagged(item, tag);
    }

    private static AEItemKey ofTagged(Item item, CompoundTag tag) {
        var aeItem = (IAEItem) item;
        if (tag.isEmpty()) {
            return aeItem.ae2$getAEKey();
        }
        var cache = aeItem.ae2$getTagAEKeyCache();
        return cache.getCache(tag, cache.createFunction(), IdentityTag.COPY);
    }

    public static boolean matches(AEKey what, ItemStack itemStack) {
        return what instanceof AEItemKey itemKey && itemKey.matches(itemStack);
    }

    public static boolean is(AEKey what) {
        return what instanceof AEItemKey;
    }

    public static AEKeyFilter filter() {
        return AEItemKey::is;
    }

    @Override
    public int getUid() {
        return uid;
    }

    @Override
    public AEKeyType getType() {
        return AEItemKeys.INSTANCE;
    }

    @Override
    public AEItemKey dropSecondary() {
        return ((IAEItem) item).ae2$getAEKey();
    }

    public boolean matches(ItemStack stack) {
        // TODO: remove or optimize cap check if it becomes too slow >:-(
        return !stack.isEmpty() && stack.is(item) && Objects.equals(stack.getTag(), internedTag);
    }

    public boolean matches(Ingredient ingredient) {
        return ingredient.test(getReadOnlyStack());
    }

    /**
     * @return The ItemStack represented by this key. <strong>NEVER MUTATE THIS</strong>
     */
    public ItemStack getReadOnlyStack() {
        var stack = readOnlyStack;
        if (stack == null) {
            stack = new ItemStack(item, 1);
            stack.setTag(internedTag);
            readOnlyStack = stack;
        } else if (stack.isEmpty()) {
            stack = new ItemStack(item, 1);
            stack.setTag(internedTag);
            readOnlyStack = stack;
            AELog.error("Something destroyed the read-only itemstack of {}", this);
        }
        return stack;
    }

    public ItemStack toStack() {
        return toStack(1);
    }

    public ItemStack toStack(int count) {
        if (count <= 0) {
            return ItemStack.EMPTY;
        }

        var result = new ItemStack(item, count);
        result.setTag(copyTag());
        return result;
    }

    public Item getItem() {
        return item;
    }

    @Nullable
    public static AEItemKey fromTag(CompoundTag tag) {
        try {
            var item = BuiltInRegistries.ITEM.getOptional(new ResourceLocation(tag.getString("id")))
                    .orElseThrow(() -> new IllegalArgumentException("Unknown item id."));
            var extraTag = tag.get("tag") instanceof CompoundTag compoundTag ? compoundTag : null;
            var aeItem = (IAEItem) item;
            if (extraTag == null || extraTag.isEmpty()) {
                return aeItem.ae2$getAEKey();
            }
            var cache = aeItem.ae2$getTagAEKeyCache();
            return cache.getCache(extraTag, cache.createFunction());
        } catch (Exception e) {
            AELog.debug("Tried to load an invalid item key from NBT: %s", tag, e);
            return null;
        }
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag result = new CompoundTag();
        result.putString("id", getId().toString());

        if (internedTag != null) {
            result.put("tag", internedTag.copy());
        }

        return result;
    }

    @Override
    public Object getPrimaryKey() {
        return item;
    }

    /**
     * @see ItemStack#getDamageValue()
     */
    @Override
    public int getFuzzySearchValue() {
        int ret = fuzzySearchValue;
        if (ret == 0) {
            fuzzySearchValue = ret = getReadOnlyStack().getDamageValue() + 1;
        }
        return ret - 1;
    }

    /**
     * @see ItemStack#getMaxDamage()
     */
    @Override
    public int getFuzzySearchMaxValue() {
        int ret = fuzzySearchMaxValue;
        if (ret == 0) {
            fuzzySearchMaxValue = ret = getReadOnlyStack().getMaxDamage() + 1;
        }
        return ret - 1;
    }

    @Override
    public ResourceLocation getId() {
        return BuiltInRegistries.ITEM.getKey(item);
    }

    /**
     * @return <strong>NEVER MODIFY THE RETURNED TAG</strong>
     */
    @Nullable
    public CompoundTag getTag() {
        return internedTag;
    }

    @Nullable
    public CompoundTag copyTag() {
        return internedTag != null ? internedTag.copy() : null;
    }

    public boolean hasTag() {
        return internedTag != null;
    }

    @Override
    public ItemStack wrapForDisplayOrFilter() {
        return toStack();
    }

    @Override
    public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {
        while (amount > 0) {
            if (drops.size() > 1000) {
                AELog.warn("Tried dropping an excessive amount of items, ignoring %s %ss", amount, item);
                break;
            }

            var taken = Math.min(amount, getMaxStackSize());
            amount -= taken;
            drops.add(toStack((int) taken));
        }
    }

    @Override
    protected Component computeDisplayName() {
        return getReadOnlyStack().getHoverName();
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean isTagged(TagKey<?> tag) {
        // This will just return false for incorrectly cast tags
        return item.builtInRegistryHolder().is((TagKey<Item>) tag);
    }

    /**
     * @return True if the item represented by this key is damaged.
     */
    public boolean isDamaged() {
        return getFuzzySearchValue() > 0;
    }

    public int getMaxStackSize() {
        int ret = maxStackSize;
        if (ret == 0) {
            maxStackSize = ret = computeMaxStackSize();
        }
        return ret;
    }

    private int computeMaxStackSize() {
        if (internedTag == null) {
            return Math.max(1, getReadOnlyStack().getMaxStackSize());
        }
        var probe = new ItemStack(item, 1);
        probe.setTag(internedTag);
        return Math.max(1, probe.getMaxStackSize());
    }

    @Override
    public void writeToPacket(FriendlyByteBuf data) {
        data.writeVarInt(Item.getId(item));
        CompoundTag compoundTag = null;
        if (item.canBeDepleted() || item.shouldOverrideMultiplayerNbt()) {
            compoundTag = SHARE_TAG_OVERRIDDEN.get(item.getClass()) ? item.getShareTag(toStack()) : internedTag;
        }
        data.writeNbt(compoundTag);
    }

    public static AEItemKey fromPacket(FriendlyByteBuf data) {
        var item = Item.byId(data.readVarInt());
        var shareTag = data.readNbt();
        var aeItem = (IAEItem) item;
        if (shareTag == null || shareTag.isEmpty()) {
            return aeItem.ae2$getAEKey();
        }
        var stack = new ItemStack(item);
        stack.readShareTag(shareTag);
        var tag = stack.getTag();
        if (tag == null || tag.isEmpty()) {
            return aeItem.ae2$getAEKey();
        }
        var cache = aeItem.ae2$getTagAEKeyCache();
        return cache.getCache(tag, cache.createFunction());
    }

    @Override
    public String toString() {
        var id = getId();
        String idString = id != BuiltInRegistries.ITEM.getDefaultKey() ? id.toString()
                : item.getClass().getName() + "(unregistered)";
        return internedTag == null ? idString : idString + " (+tag)";
    }
}
