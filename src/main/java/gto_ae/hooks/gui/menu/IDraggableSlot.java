package gto_ae.hooks.gui.menu;

import net.minecraft.world.item.ItemStack;

public interface IDraggableSlot {

    boolean canSetFilterTo(ItemStack filter);

    /**
     * {@link IDraggableSlot#onXEIDragged(ItemStack)} will only be called if this returns true. This is used to prevent
     * dragging from slots that don't support it, such as crafting result slots.
     * 
     * @see appeng.integration.modules.jeirei.DropTargets
     */
    default boolean customDragging() {
        return false;
    }

    default void onXEIDragged(ItemStack wrappedGenericStack) {
    }

    int getIndex();
}
