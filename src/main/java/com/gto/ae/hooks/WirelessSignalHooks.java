package com.gto.ae.hooks;

public final class WirelessSignalHooks {
    private static volatile IWirelessSignalBlocker blocker = IWirelessSignalBlocker.NONE;

    private WirelessSignalHooks() {
    }

    public static IWirelessSignalBlocker get() {
        return blocker;
    }

    public static void setBlocker(IWirelessSignalBlocker blocker) {
        WirelessSignalHooks.blocker = blocker;
    }
}
