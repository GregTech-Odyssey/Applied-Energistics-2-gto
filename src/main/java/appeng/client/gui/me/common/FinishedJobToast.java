package appeng.client.gui.me.common;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.core.localization.GuiText;

/**
 * A Minecraft toast for a finished crafting job.
 */
public class FinishedJobToast extends ContentToast {
    private final long amount;

    public FinishedJobToast(AEKey what, long amount) {
        super(what);
        this.amount = amount;
    }

    @Override
    protected void addInfoLines(List<FormattedCharSequence> lines) {
        super.addInfoLines(lines);

        var minecraft = Minecraft.getInstance();
        var font = minecraft.font;

        var formattedAmount = what.formatAmount(amount, AmountFormat.SLOT);

        var text = GuiText.ToastCraftingJobFinishedText.text(formattedAmount, AEKeyRendering.getDisplayName(what));
        lines.addAll(font.split(text, width() - 30 - 5));
    }

    @Override
    protected Component getTitle() {
        return GuiText.ToastCraftingJobFinishedTitle.text();
    }
}
