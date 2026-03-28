package gto_ae.client.gui.slot;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.Icon;
import appeng.client.gui.me.common.ClientReadOnlySlot;
import appeng.client.gui.me.common.Repo;
import appeng.menu.me.common.GridInventoryEntry;

import gto_ae.hooks.gui.menu.IDecoratedSlot;
import gto_ae.hooks.gui.menu.IDraggableSlot;
import gto_ae.hooks.gui.menu.IRepoSlot;

public class FixedRepoSlot extends ClientReadOnlySlot implements IRepoSlot, IDecoratedSlot, IDraggableSlot {

    private final Repo repo;
    private @Nullable AEKey what;
    private final List<Component> emptyTooltips = new ArrayList<>();
    private Icon icon;
    private boolean draggable = false;

    public FixedRepoSlot(Repo repo, @Nullable AEKey what, int displayX, int displayY) {
        super(displayX, displayY);
        this.repo = repo;
        this.what = what;
    }

    @Override
    public float getOpacityOfIcon() {
        return 0.4f;
    }

    @Override
    public Slot self() {
        return this;
    }

    @Override
    public @Nullable GridInventoryEntry getEntry() {
        if (this.what == null || repo == null) {
            return null;
        }
        if (this.repo.hasPower()) {
            return this.repo.getByKey(what);
        }
        return null;
    }

    @Override
    public Icon getIcon() {
        return this.icon;
    }

    public void setIcon(Icon icon) {
        this.icon = icon;
    }

    @Override
    public long getStoredAmount() {
        GridInventoryEntry entry = getEntry();
        return entry != null ? entry.getStoredAmount() : 0;
    }

    public long getRequestableAmount() {
        GridInventoryEntry entry = getEntry();
        return entry != null ? entry.getRequestableAmount() : 0;
    }

    @Override
    public boolean isCraftable() {
        GridInventoryEntry entry = getEntry();
        return entry != null && entry.isCraftable();
    }

    @Override
    public @NotNull ItemStack getItem() {
        GridInventoryEntry entry = getEntry();
        if (entry != null && entry.getWhat() != null) {
            return entry.getWhat().wrapForDisplayOrFilter();
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean hasItem() {
        return getEntry() != null;
    }

    @Override
    public @NotNull List<Component> getEmptyTooltipMessage() {
        return emptyTooltips;
    }

    public void setEmptyTooltipMessage(@NotNull List<Component> tooltips) {
        this.emptyTooltips.clear();
        this.emptyTooltips.addAll(tooltips);
    }

    @Override
    public boolean canSetFilterTo(ItemStack stack) {
        return draggable;
    }

    @Override
    public void onXEIDragged(ItemStack wrappedGenericStack) {
        what = GenericStack.unwrapItemStack(wrappedGenericStack).what();
    }

    @Override
    public int getIndex() {
        return index;
    }
}
