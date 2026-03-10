package appeng.integration.modules.jeirei;

import java.util.ArrayList;
import java.util.List;

import com.almostreliable.merequester.client.RequestSlot;
import com.almostreliable.merequester.platform.Platform;
import com.google.common.primitives.Ints;

import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

import gto_ae.hooks.gui.menu.IDraggableSlot;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.widgets.AETextField;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.InventoryActionPacket;
import appeng.helpers.InventoryAction;

public final class DropTargets {
    private DropTargets() {
    }

    public static List<DropTarget> getTargets(AEBaseScreen<?> aeScreen) {
        List<DropTarget> targets = new ArrayList<>();
        for (var slot : aeScreen.getMenu().slots) {
            if (slot.isActive() && slot instanceof IDraggableSlot fakeSlot) {
                var area = new Rect2i(aeScreen.getGuiLeft() + slot.x, aeScreen.getGuiTop() + slot.y, 16, 16);
                targets.add(new FakeSlotDropTarget(area, fakeSlot));
            }
        }

        for (var widget : aeScreen.getWidgets().getWidgets().values()) {
            if (widget instanceof AETextField search) {
                var area = new Rect2i(search.getX(), search.getY(),
                        search.getWidth(), search.getHeight());
                targets.add(new SearchBarDropTarget(area, search));
            }
        }

        return targets;
    }

    private record FakeSlotDropTarget(Rect2i area, IDraggableSlot slot) implements DropTarget {
        @Override
        public boolean canDrop(GenericStack stack) {
            // Use the standard inventory function to test if the dragged stack would in theory be accepted
            return slot.canSetFilterTo(wrapFilterAsItem(stack));
        }

        @Override
        public boolean drop(GenericStack stack) {
            var itemStack = wrapFilterAsItem(stack);

            if (slot.canSetFilterTo(itemStack)) {
                if (slot instanceof RequestSlot requestSlot) {
                    Platform.sendDragAndDrop(requestSlot.getRequesterReference().getRequesterId(),
                            requestSlot.getSlot(), itemStack);
                } else if (slot.customDragging()) {
                    slot.onXEIDragged(itemStack);
                } else {
                    NetworkHandler.instance().sendToServer(new InventoryActionPacket(InventoryAction.SET_FILTER,
                            slot.getIndex(), itemStack));
                }
                return true;
            }

            return false;
        }

        // Fake slots use GenericStacks wrapped in ItemStack for non-items internally
        private static ItemStack wrapFilterAsItem(GenericStack genericStack) {
            if (genericStack.what() instanceof AEItemKey itemKey) {
                return itemKey.toStack(Ints.saturatedCast(Math.max(1, genericStack.amount())));
            } else {
                return GenericStack.wrapInItemStack(genericStack.what(), Math.max(1, genericStack.amount()));
            }
        }

    }

    private record SearchBarDropTarget(Rect2i area, AETextField search) implements DropTarget {

        @Override
        public boolean canDrop(GenericStack stack) {
            return true;
        }

        @Override
        public boolean drop(GenericStack stack) {
            while (stack.what() instanceof AEItemKey aik && GenericStack.unwrapItemStack(aik.toStack()) != null) {
                stack = GenericStack.unwrapItemStack(aik.toStack());
            }
            search.setValue(stack.what().getDisplayName().getString());
            return true;
        }
    }
}
