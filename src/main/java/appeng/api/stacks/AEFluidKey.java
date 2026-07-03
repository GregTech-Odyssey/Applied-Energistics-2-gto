package appeng.api.stacks;

import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.storage.AEKeyFilter;
import appeng.core.AELog;
import appeng.hooks.IAEFluid;
import appeng.hooks.IUnique;
import appeng.util.Platform;

public final class AEFluidKey extends AEKey {

    public static final int AMOUNT_BUCKET = 1000;
    public static final int AMOUNT_BLOCK = 1000;

    public final Fluid fluid;
    public final int uid;
    @NotNull
    private final InternedTag internedTag;

    // cache
    @Nullable
    private FluidStack readOnlyStack;

    @ApiStatus.Internal
    public AEFluidKey(@NotNull Fluid fluid, @NotNull InternedTag tag) {
        this.fluid = fluid;
        this.internedTag = tag;
        this.uid = ((IUnique) fluid).ae2$getUid();
    }

    public static AEFluidKey of(Fluid fluid) {
        var aeFluid = (IAEFluid) fluid;
        return aeFluid.ae2$getAEKey();
    }

    public static AEFluidKey of(Fluid fluid, @Nullable CompoundTag tag) {
        var aeFluid = (IAEFluid) fluid;
        if (tag == null || tag.isEmpty()) {
            return aeFluid.ae2$getAEKey();
        }
        return aeFluid.ae2$getTagAEKeyCache().getCache(InternedTag.of(tag, true), t -> new AEFluidKey(fluid, t));
    }

    @Nullable
    public static AEFluidKey of(FluidStack fluidVariant) {
        var fluid = fluidVariant.getFluid();
        if (fluid == Fluids.EMPTY) {
            return null;
        }
        var aeFluid = (IAEFluid) fluid;
        var tag = fluidVariant.getTag();
        if (tag == null || tag.isEmpty()) {
            return aeFluid.ae2$getAEKey();
        }
        return aeFluid.ae2$getTagAEKeyCache().getCache(InternedTag.of(tag, true), t -> new AEFluidKey(fluid, t));
    }

    public static boolean matches(AEKey what, FluidStack fluid) {
        return what instanceof AEFluidKey fluidKey && fluidKey.matches(fluid);
    }

    public static boolean is(AEKey what) {
        return what instanceof AEFluidKey;
    }

    public static AEKeyFilter filter() {
        return AEFluidKey::is;
    }

    public boolean matches(FluidStack variant) {
        return !variant.isEmpty() && fluid.isSame(variant.getFluid())
                && Objects.equals(internedTag.tag, variant.getTag());
    }

    @Override
    public int getUid() {
        return uid;
    }

    @Override
    public AEKeyType getType() {
        return AEFluidKeys.INSTANCE;
    }

    @Override
    public int getAmountPerUnit() {
        return AMOUNT_BUCKET;
    }

    @Override
    public AEFluidKey dropSecondary() {
        return of(fluid);
    }

    public static AEFluidKey fromTag(CompoundTag tag) {
        try {
            var fluid = BuiltInRegistries.FLUID.getOptional(new ResourceLocation(tag.getString("id")))
                    .orElseThrow(() -> new IllegalArgumentException("Unknown fluid id."));
            var extraTag = tag.get("tag") instanceof CompoundTag compoundTag ? compoundTag : null;
            var aeFluid = (IAEFluid) fluid;
            if (extraTag == null || extraTag.isEmpty()) {
                return aeFluid.ae2$getAEKey();
            }
            return aeFluid.ae2$getTagAEKeyCache().getCache(InternedTag.of(extraTag, false),
                    t -> new AEFluidKey(fluid, t));
        } catch (Exception e) {
            AELog.debug("Tried to load an invalid fluid key from NBT: %s", tag, e);
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
        return fluid;
    }

    @Override
    public ResourceLocation getId() {
        return BuiltInRegistries.FLUID.getKey(fluid);
    }

    @Override
    public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {
        // Fluids are voided
    }

    @Override
    protected Component computeDisplayName() {
        return Platform.getFluidDisplayName(fluid, internedTag.tag);
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean isTagged(TagKey<?> tag) {
        // This will just return false for incorrectly cast tags
        return fluid.builtInRegistryHolder().is((TagKey<Fluid>) tag);
    }

    public FluidStack toStack(int amount) {
        return new FluidStack(fluid, amount, internedTag.tag);
    }

    public FluidStack getReadOnlyStack() {
        var stack = readOnlyStack;
        if (stack == null) {
            stack = readOnlyStack = toStack(1);
        } else if (stack.isEmpty()) {
            stack = readOnlyStack = toStack(1);
            AELog.error("Something destroyed the read-only fluidStack of {}", this);
        }
        return stack;
    }

    public Fluid getFluid() {
        return fluid;
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
    public void writeToPacket(FriendlyByteBuf data) {
        data.writeVarInt(BuiltInRegistries.FLUID.getId(fluid));
        data.writeNbt(internedTag.tag);
    }

    public static AEFluidKey fromPacket(FriendlyByteBuf data) {
        var fluid = BuiltInRegistries.FLUID.byId(data.readVarInt());
        var tag = data.readNbt();
        var aeFluid = (IAEFluid) fluid;
        if (tag == null || tag.isEmpty()) {
            return aeFluid.ae2$getAEKey();
        }
        return aeFluid.ae2$getTagAEKeyCache().getCache(InternedTag.of(tag, false), t -> new AEFluidKey(fluid, t));
    }

    public static boolean is(@Nullable GenericStack stack) {
        return stack != null && stack.what() instanceof AEFluidKey;
    }

    @Override
    public String toString() {
        var id = getId();
        String idString = id != BuiltInRegistries.FLUID.getDefaultKey() ? id.toString()
                : fluid.getClass().getName() + "(unregistered)";
        return internedTag.tag == null ? idString : idString + " (+tag)";
    }
}
