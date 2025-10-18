package appeng.api.stacks;

import java.util.List;
import java.util.Objects;

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
import net.minecraftforge.fluids.FluidStack;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;

import appeng.api.storage.AEKeyFilter;
import appeng.core.AELog;
import appeng.util.Platform;

public final class AEFluidKey extends AEKey {

    private static final Object2ObjectOpenCustomHashMap<AEFluidKey, AEFluidKey> VALUES = new Object2ObjectOpenCustomHashMap<>(
            new Hash.Strategy<>() {
                @Override
                public int hashCode(AEFluidKey o) {
                    return Objects.hash(o.fluid, o.internedTag);
                }

                @Override
                public boolean equals(AEFluidKey a, AEFluidKey b) {
                    if (a == null)
                        return b == null;
                    if (b == null)
                        return false;
                    return Objects.equals(a.fluid, b.fluid) && Objects.equals(a.internedTag, b.internedTag);
                }
            });

    public static final int AMOUNT_BUCKET = 1000;
    public static final int AMOUNT_BLOCK = 1000;

    private final Fluid fluid;
    @NotNull
    private final InternedTag internedTag;
    @Nullable
    private FluidStack readOnlyStack;

    public AEFluidKey(Fluid fluid, @Nullable CompoundTag tag) {
        this.fluid = fluid;
        this.internedTag = InternedTag.of(tag, false);
    }

    public static AEFluidKey of(Fluid fluid, @Nullable CompoundTag tag) {
        var key = new AEFluidKey(fluid, tag != null ? tag.copy() : null);
        return VALUES.computeIfAbsent(key, k -> key);
    }

    public static AEFluidKey of(Fluid fluid) {
        return of(fluid, null);
    }

    @Nullable
    public static AEFluidKey of(FluidStack fluidVariant) {
        if (fluidVariant.isEmpty()) {
            return null;
        }
        return of(fluidVariant.getFluid(), fluidVariant.getTag());
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
    public AEKeyType getType() {
        return AEFluidKeys.INSTANCE;
    }

    @Override
    public AEFluidKey dropSecondary() {
        return of(fluid, null);
    }

    public static AEFluidKey fromTag(CompoundTag tag) {
        try {
            var fluid = BuiltInRegistries.FLUID.getOptional(new ResourceLocation(tag.getString("id")))
                    .orElseThrow(() -> new IllegalArgumentException("Unknown fluid id."));
            var extraTag = tag.contains("tag") ? tag.getCompound("tag") : null;
            return of(fluid, extraTag);
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
        if (readOnlyStack == null) {
            readOnlyStack = toStack(1);
        } else if (readOnlyStack.isEmpty()) {
            readOnlyStack = null;
            AELog.error("Something destroyed the read-only fluidStack of {}", this);
            return getReadOnlyStack();
        }
        return readOnlyStack;
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
        return VALUES.computeIfAbsent(new AEFluidKey(fluid, tag), k -> (AEFluidKey) k);
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
