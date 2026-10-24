package appeng.client.gui.me.common;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.Bootstrap;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;

class FinishedJobNotificationTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void displaysTheExactQuantityWithoutMouseInteractions() {
        var message = FinishedJobNotification.create(key(1, null), 26_553, TimeUnit.SECONDS.toNanos(23));
        var args = ((TranslatableContents) message.getContents()).getArgs();
        var amount = (Component) args[0];

        assertEquals("26553", amount.getString());
        assertNull(amount.getStyle().getClickEvent());
        assertNull(amount.getStyle().getHoverEvent());
        assertEquals("00:00:23", ((Component) args[2]).getString());
        assertTrue(((Component) args[1]).getString().contains("Silicon"));
    }

    @Test
    void preservesEveryDigitOfLargeItemAmounts() {
        var args = arguments(key(1, null), Long.MAX_VALUE, 0);
        var amount = (Component) args[0];

        assertEquals("9223372036854775807", amount.getString());
        assertNull(amount.getStyle().getHoverEvent());
        assertEquals("00:00:00", ((Component) args[2]).getString());
    }

    @Test
    void formatsFluidUnitsWithoutRoundingTheExactQuantity() {
        var args = arguments(key(1000, "B"), 1_234_567, 0);
        var amount = (Component) args[0];

        assertEquals("1234.567 B", amount.getString());
        assertNull(amount.getStyle().getHoverEvent());
    }

    @Test
    void doesNotWrapElapsedHoursAtMidnight() {
        var nanos = TimeUnit.HOURS.toNanos(49) + TimeUnit.MINUTES.toNanos(2) + TimeUnit.SECONDS.toNanos(3);
        var args = arguments(key(1, null), 100, nanos);

        assertEquals("100", ((Component) args[0]).getString());
        assertEquals("49:02:03", ((Component) args[2]).getString());
    }

    private static Object[] arguments(AEKey key, long amount, long elapsedNanos) {
        return ((TranslatableContents) FinishedJobNotification.create(key, amount, elapsedNanos).getContents())
                .getArgs();
    }

    private static AEKey key(int amountPerUnit, String unit) {
        var key = mock(AEKey.class);
        var type = mock(AEKeyType.class);
        when(key.getDisplayName()).thenReturn(Component.literal("Silicon"));
        when(key.getAmountPerUnit()).thenReturn(amountPerUnit);
        when(key.getType()).thenReturn(type);
        when(type.getUnitSymbol()).thenReturn(unit);
        return key;
    }
}
