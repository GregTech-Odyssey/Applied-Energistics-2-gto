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

import appeng.api.storage.AEKeyFilter;
import appeng.core.AELog;
import appeng.hooks.IAEItem;
import appeng.hooks.IUnique;

public final class AEItemKey extends AEKey {

    public final Item item;
    public final int uid;
    private final InternedTag internedTag;

    // cache
    @Nullable
    private ItemStack readOnlyStack;
    private int maxStackSize = -1;
    private int fuzzySearchValue = -1;
    private int fuzzySearchMaxValue = -1;

    @ApiStatus.Internal
    public AEItemKey(Item item, InternedTag internedTag) {
        this.item = item;
        this.internedTag = internedTag;
        this.uid = ((IUnique) item).ae2$getUid();
    }

    public static AEItemKey of(ItemLike item) {
        var aeItem = (IAEItem) item.asItem();
        return aeItem.ae2$getAEKey();
    }

    public static AEItemKey of(ItemLike item, @Nullable CompoundTag tag) {
        var i = item.asItem();
        var aeItem = (IAEItem) i;
        if (tag == null || tag.isEmpty()) {
            return aeItem.ae2$getAEKey();
        }
        return aeItem.ae2$getTagAEKeyCache().getCache(InternedTag.of(tag, true), t -> new AEItemKey(i, t));
    }

    @Nullable
    public static AEItemKey of(ItemStack stack) {
        var item = stack.getItem();
        if (item == Items.AIR) {
            return null;
        }
        var aeItem = (IAEItem) item;
        var tag = stack.getTag();
        if (tag == null || tag.isEmpty()) {
            return aeItem.ae2$getAEKey();
        }
        return aeItem.ae2$getTagAEKeyCache().getCache(InternedTag.of(tag, true), t -> new AEItemKey(item, t));
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
        return of(item);
    }

    public boolean matches(ItemStack stack) {
        // TODO: remove or optimize cap check if it becomes too slow >:-(
        return !stack.isEmpty() && stack.is(item) && Objects.equals(stack.getTag(), internedTag.tag);
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
            stack = readOnlyStack = new ItemStack(item, 1);
            stack.setTag(internedTag.tag);
        } else if (stack.isEmpty()) {
            stack = readOnlyStack = new ItemStack(item, 1);
            stack.setTag(internedTag.tag);
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
            return aeItem.ae2$getTagAEKeyCache().getCache(InternedTag.of(extraTag, false), t -> new AEItemKey(item, t));
        } catch (Exception e) {
            AELog.debug("Tried to load an invalid item key from NBT: %s", tag, e);
            return null;
        }
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag result = new CompoundTag();
        result.putString("id", getId().toString());

        if (internedTag.tag != null) {
            result.put("tag", internedTag.tag.copy());
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
        if (ret == -1) {
            fuzzySearchValue = ret = getReadOnlyStack().getDamageValue();
        }
        return ret;
    }

    /**
     * @see ItemStack#getMaxDamage()
     */
    @Override
    public int getFuzzySearchMaxValue() {
        int ret = fuzzySearchMaxValue;
        if (ret == -1) {
            fuzzySearchMaxValue = ret = getReadOnlyStack().getMaxDamage();
        }
        return ret;
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
        return internedTag.tag;
    }

    @Nullable
    public CompoundTag copyTag() {
        return internedTag.tag != null ? internedTag.tag.copy() : null;
    }

    public boolean hasTag() {
        return internedTag.tag != null;
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

        if (ret == -1) {
            maxStackSize = ret = getReadOnlyStack().getMaxStackSize();
        }

        return ret;
    }

    @Override
    public void writeToPacket(FriendlyByteBuf data) {
        data.writeVarInt(Item.getId(item));
        CompoundTag compoundTag = null;
        if (item.canBeDepleted() || item.shouldOverrideMultiplayerNbt()) {
            compoundTag = item.getShareTag(toStack());
        }
        data.writeNbt(compoundTag);
    }

    public static AEItemKey fromPacket(FriendlyByteBuf data) {
        var item = Item.byId(data.readVarInt());
        var shareTag = data.readNbt();
        var stack = new ItemStack(item);
        stack.readShareTag(shareTag);
        var tag = stack.getTag();
        var aeItem = (IAEItem) item;
        if (tag == null || tag.isEmpty()) {
            return aeItem.ae2$getAEKey();
        }
        return aeItem.ae2$getTagAEKeyCache().getCache(InternedTag.of(tag, false), t -> new AEItemKey(item, t));
    }

    @Override
    public String toString() {
        var id = getId();
        String idString = id != BuiltInRegistries.ITEM.getDefaultKey() ? id.toString()
                : item.getClass().getName() + "(unregistered)";
        return internedTag.tag == null ? idString : idString + " (+tag)";
    }
}
