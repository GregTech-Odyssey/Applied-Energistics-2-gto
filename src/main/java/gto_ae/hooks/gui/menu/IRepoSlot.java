package gto_ae.hooks.gui.menu;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.api.behaviors.ContainerItemStrategies;
import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AmountFormat;
import appeng.client.gui.me.common.PendingCraftingJobs;
import appeng.client.gui.me.common.Repo;
import appeng.client.gui.me.common.StackSizeRenderer;
import appeng.client.gui.style.Blitter;
import appeng.core.AELog;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.GuiText;
import appeng.core.localization.Tooltips;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.MEInteractionPacket;
import appeng.helpers.InventoryAction;
import appeng.menu.me.common.GridInventoryEntry;
import appeng.menu.me.common.MEStorageMenu;

public interface IRepoSlot {

    Slot self();

    @Nullable
    GridInventoryEntry getEntry();

    long getStoredAmount();

    boolean isCraftable();

    @NotNull
    ItemStack getItem();

    boolean hasItem();

    default void renderDecoration(GuiGraphics guiGraphics) {
        var entry = this.getEntry();
        if (entry != null && PendingCraftingJobs.hasPendingJob(entry.getWhat())) {
            var frames = 192 / 16;
            var frame = (int) ((System.currentTimeMillis() / 100) % frames);

            Blitter.texture("block/molecular_assembler_lights.png", 16, 192)
                    .src(2, 2 + frame * 16, 12, 12)
                    .dest(self().x - 1, self().y - 1, 18, 18)
                    .blit(guiGraphics);
        }
    }

    default void renderSlot(GuiGraphics guiGraphics, Repo repo, boolean useLargeFonts, boolean isViewOnlyCraftable) {
        var s = self();
        var minecraft = Minecraft.getInstance();
        var font = minecraft.font;

        if (s instanceof IDecoratedSlot iconSlot && iconSlot.getIcon() != null) {
            iconSlot.getIcon().getBlitter()
                    .dest(s.x, s.y)
                    .opacity(iconSlot.getOpacityOfIcon())
                    .blit(guiGraphics);
        }
        if (!repo.hasPower()) {
            guiGraphics.fill(s.x, s.y, 16 + s.x, 16 + s.y, 0x66111111);
        } else {
            GridInventoryEntry entry = this.getEntry();
            if (entry != null) {
                try {
                    AEKeyRendering.drawInGui(
                            minecraft,
                            guiGraphics,
                            s.x,
                            s.y, entry.getWhat());
                } catch (Exception err) {
                    AELog.warn("[AppEng] AE prevented crash while drawing slot: " + err);
                }

                // If a view mode is selected that only shows craftable items, display the "craftable" text
                // regardless of stack size
                long storedAmount = entry.getStoredAmount();
                boolean craftable = entry.isCraftable();
                if (craftable && (isViewOnlyCraftable || storedAmount <= 0)) {
                    var craftLabelText = useLargeFonts ? GuiText.LargeFontCraft.getLocal()
                            : GuiText.SmallFontCraft.getLocal();
                    StackSizeRenderer.renderSizeLabel(guiGraphics, font, s.x, s.y, craftLabelText);
                } else {
                    AmountFormat format = useLargeFonts ? AmountFormat.SLOT_LARGE_FONT
                            : AmountFormat.SLOT;
                    var text = entry.getWhat().formatAmount(storedAmount, format);
                    StackSizeRenderer.renderSizeLabel(guiGraphics, font, s.x, s.y, text, useLargeFonts);
                    if (craftable) {
                        StackSizeRenderer.renderSizeLabel(guiGraphics, font, s.x - 11, s.y - 11, "+", false);
                    }
                }
            }
        }
    }

    default void handleGridInventoryEntryMouseClick(MEStorageMenu menu,
            int mouseButton,
            ClickType clickType,
            boolean shouldCraftOnClick) {
        var entry = this.getEntry();
        if (entry != null) {
            AELog.debug("Clicked on grid inventory entry serial=%s, key=%s", entry.getSerial(), entry.getWhat());
        }

        // Is there an emptying action? If so, send it to the server
        if (mouseButton == 1 && clickType == ClickType.PICKUP && !menu.getCarried().isEmpty()) {
            var emptyingAction = ContainerItemStrategies.getEmptyingAction(menu.getCarried());
            if (emptyingAction != null && menu.isKeyVisible(emptyingAction.what())) {
                menu.handleInteraction(-1, InventoryAction.EMPTY_ITEM);
                return;
            }
        }

        if (entry == null) {
            // The only interaction allowed on an empty virtual slot is putting down the currently held item
            if (clickType == ClickType.PICKUP && !menu.getCarried().isEmpty()) {
                InventoryAction action = mouseButton == 1 ? InventoryAction.SPLIT_OR_PLACE_SINGLE
                        : InventoryAction.PICKUP_OR_SET_DOWN;
                menu.handleInteraction(-1, action);
            }
            return;
        }

        long serial = entry.getSerial();

        if (InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_SPACE)) {
            // Move everything from the same group of slots (i.e. player inventory excluding hotbar)
            menu.handleInteraction(serial, InventoryAction.MOVE_REGION);
        } else {
            InventoryAction action = null;

            switch (clickType) {
                case PICKUP: // pickup / set-down.
                    action = mouseButton == 1 ? InventoryAction.SPLIT_OR_PLACE_SINGLE
                            : InventoryAction.PICKUP_OR_SET_DOWN;

                    if (action == InventoryAction.PICKUP_OR_SET_DOWN
                            && shouldCraftOnClick
                            && menu.getCarried().isEmpty()) {
                        menu.handleInteraction(serial, InventoryAction.AUTO_CRAFT);
                        return;
                    }

                    break;
                case QUICK_MOVE:
                    action = mouseButton == 1 ? InventoryAction.PICKUP_SINGLE : InventoryAction.SHIFT_CLICK;
                    break;

                case CLONE: // creative dupe:
                    if (entry.isCraftable()) {
                        menu.handleInteraction(serial, InventoryAction.AUTO_CRAFT);
                        return;
                    } else if (menu.getPlayer().getAbilities().instabuild) {
                        action = InventoryAction.CREATIVE_DUPLICATE;
                    }
                    break;

                case THROW: // drop item:
            }

            if (action != null) {
                menu.handleInteraction(serial, action);
            }
        }
    }

    default void renderTooltip(MEStorageMenu menu, GuiGraphics guiGraphics, int x, int y, TooltipHandler emptyDrawer,
            TooltipHandler itemDrawer, boolean isViewOnlyCraftable) {
        List<Component> tooltip;
        var carried = menu.getCarried();
        var repoSlot = this;
        if (repoSlot.getStoredAmount() == 0 && repoSlot instanceof IDecoratedSlot fxRepoSlot
                && !fxRepoSlot.getEmptyTooltipMessage().isEmpty())
            tooltip = fxRepoSlot.getEmptyTooltipMessage();
        else if (carried.isEmpty()) {
            tooltip = List.of();
        } else {
            var emptyingAction = ContainerItemStrategies.getEmptyingAction(carried);
            tooltip = emptyingAction != null && menu.isKeyVisible(emptyingAction.what())
                    ? Tooltips.getEmptyingTooltip(ButtonToolTips.StoreAction, carried, emptyingAction)
                    : List.of();
        }
        if (!tooltip.isEmpty()) {
            emptyDrawer.drawTooltip(
                    guiGraphics,
                    x,
                    y,
                    tooltip);
            return;
        }

        // Vanilla doesn't show item tooltips when the player have something in their hand
        if (carried.isEmpty()) {
            GridInventoryEntry entry = repoSlot.getEntry();
            if (entry != null) {
                renderGridInventoryEntryTooltip(guiGraphics, entry, x, y, isViewOnlyCraftable, itemDrawer);
            }
        }

    }

    default void renderGridInventoryEntryTooltip(GuiGraphics guiGraphics, GridInventoryEntry entry, int x, int y,
            boolean isViewOnlyCraftable, TooltipHandler itemDrawer) {

        var currentToolTip = AEKeyRendering.getTooltip(entry.getWhat());

        if (Tooltips.shouldShowAmountTooltip(entry.getWhat(), entry.getStoredAmount())) {
            currentToolTip.add(
                    Tooltips.getAmountTooltip(ButtonToolTips.StoredAmount, entry.getWhat(), entry.getStoredAmount()));
        }

        var requestableAmount = entry.getRequestableAmount();
        if (requestableAmount > 0) {
            var formattedAmount = entry.getWhat().formatAmount(requestableAmount, AmountFormat.FULL);
            currentToolTip.add(ButtonToolTips.RequestableAmount.text(formattedAmount));
        }

        // When we're _NOT_ showing the "craft" text as the amount anyway, add a Craftable entry to the tooltip
        if (entry.isCraftable() && !(isViewOnlyCraftable || entry.getStoredAmount() <= 0)) {
            currentToolTip.add(ButtonToolTips.Craftable.text().copy().withStyle(ChatFormatting.DARK_GRAY));
        }

        if (Minecraft.getInstance().options.advancedItemTooltips) {
            currentToolTip
                    .add(ButtonToolTips.Serial.text(entry.getSerial()).withStyle(ChatFormatting.DARK_GRAY));
        }

        itemDrawer.drawTooltip(guiGraphics, x, y, currentToolTip);
    }

    default void mouseScrolled(MEStorageMenu menu, double wheelDelta) {
        GridInventoryEntry entry = this.getEntry();
        long serial = entry != null ? entry.getSerial() : -1;
        final InventoryAction direction = wheelDelta > 0 ? InventoryAction.ROLL_DOWN
                : InventoryAction.ROLL_UP;
        int times = (int) Math.abs(wheelDelta);
        for (int h = 0; h < times; h++) {
            final MEInteractionPacket p = new MEInteractionPacket(menu.containerId, serial, direction);
            NetworkHandler.instance().sendToServer(p);
        }
    }

    @FunctionalInterface
    interface TooltipHandler {
        void drawTooltip(GuiGraphics guiGraphics, int x, int y, List<Component> lines);
    }
}
