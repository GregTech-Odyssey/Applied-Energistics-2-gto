package de.mari_023.ae2wtlib.wat;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import de.mari_023.ae2wtlib.AE2wtlibSlotSemantics;
import de.mari_023.ae2wtlib.wut.CycleTerminalButton;
import de.mari_023.ae2wtlib.wut.IUniversalTerminalCapable;

import appeng.client.gui.me.patternaccess.PatternAccessTermScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ScrollableUpgradesPanel;
import appeng.client.gui.widgets.ToolboxPanel;
import appeng.core.definitions.AEItems;
import appeng.menu.SlotSemantics;

public class WATScreen extends PatternAccessTermScreen<WATMenu> implements IUniversalTerminalCapable {
    public WATScreen(WATMenu container, Inventory playerInventory, Component title, ScreenStyle style) {
        super(container, playerInventory, title, style);
        if (getMenu().isWUT())
            addToLeftToolbar(new CycleTerminalButton(btn -> cycleTerminal()));

        widgets.add("upgrades", new ScrollableUpgradesPanel(List.of(
                ScrollableUpgradesPanel.SlotGroup.ofUpgrades(getMenu().getSlots(SlotSemantics.UPGRADE),
                        getMenu().getHost()),
                new ScrollableUpgradesPanel.SlotGroup(
                        getMenu().getSlots(AE2wtlibSlotSemantics.SINGULARITY),
                        ScrollableUpgradesPanel.constantTooltip(
                                AEItems.QUANTUM_ENTANGLED_SINGULARITY.asItem().getDescription())))));
        if (getMenu().getToolbox().isPresent())
            widgets.add("toolbox", new ToolboxPanel(style, getMenu().getToolbox().getName()));
    }

    @Override
    public void storeState() {
    }
}
