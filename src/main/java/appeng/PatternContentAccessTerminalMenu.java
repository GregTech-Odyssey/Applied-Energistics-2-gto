package appeng;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.slot.FakeSlot;

public class PatternContentAccessTerminalMenu extends AEBaseMenu {

    private final int Rows = 32;
    public final FakeSlot[] TargetAEKeys = new FakeSlot[Rows * 9];

    public static final MenuType<PatternContentAccessTerminalMenu> TYPE = MenuTypeBuilder
            .create(PatternContentAccessTerminalMenu::new, PatternContentAccessTerminalPart.class)
            .build("patterncontentaccessterminal");

    public PatternContentAccessTerminalMenu(int id, Inventory ip, PatternContentAccessTerminalPart part) {
        super(TYPE, id, ip, part);
        for (int i = 0; i < Rows * 9; i++) {
            TargetAEKeys[i] = new FakeSlot(part.getConfig().createMenuWrapper(), i);
            TargetAEKeys[i].x = 8 + (i % 9) * 18;
            TargetAEKeys[i].y = 18 + (i / 9) * 18;
            this.addSlot(TargetAEKeys[i],
                    SlotSemantics.PROCESSING_INPUTS);

        }
        createPlayerInventorySlots(ip);
    }
}
