package appeng.client.gui.me.common;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.time.DurationFormatUtils;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import appeng.api.stacks.AEKey;

import gto_ae.core.localization.ExtendedLangs;

/**
 * Chat text for a completed crafting job.
 */
final class FinishedJobNotification {
    private FinishedJobNotification() {
    }

    static Component create(AEKey what, long amount, long elapsedTimeNanos) {
        var units = BigDecimal.valueOf(amount).divide(BigDecimal.valueOf(what.getAmountPerUnit()));
        var unitSymbol = what.getType().getUnitSymbol();
        var suffix = unitSymbol != null ? " " + unitSymbol : "";
        var exactAmount = units.stripTrailingZeros().toPlainString() + suffix;

        var quantity = Component.literal(exactAmount)
                .withStyle(ChatFormatting.GREEN);
        var name = Component.literal("[").append(what.getDisplayName()).append("]")
                .withStyle(ChatFormatting.AQUA);
        var duration = Component.literal(DurationFormatUtils.formatDuration(
                TimeUnit.NANOSECONDS.toMillis(elapsedTimeNanos), "HH:mm:ss"))
                .withStyle(ChatFormatting.GREEN);
        return ExtendedLangs.CraftFinishedChat.text(quantity, name, duration);
    }
}
