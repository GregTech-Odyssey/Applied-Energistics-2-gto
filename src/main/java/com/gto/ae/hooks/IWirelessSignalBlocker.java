package com.gto.ae.hooks;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.level.Level;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;

/**
 * Hook for external wireless signal blockers (e.g. dimension-level storms) affecting wireless terminals.
 */
public interface IWirelessSignalBlocker {
    IWirelessSignalBlocker NONE = new IWirelessSignalBlocker() {
    };

    default long revision(Level level) {
        return 0L;
    }

    default boolean isBlocked(Level level, @Nullable IGridNode target) {
        return false;
    }

    default boolean isGridBlocked(Level level, @Nullable IGrid grid) {
        return false;
    }
}
