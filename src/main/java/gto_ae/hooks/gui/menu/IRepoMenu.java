package gto_ae.hooks.gui.menu;

import org.jetbrains.annotations.Nullable;

import appeng.api.stacks.AEKey;
import appeng.menu.me.common.IClientRepo;

public interface IRepoMenu {
    default boolean isKeyVisible(AEKey key) {
        return true;
    }

    @Nullable
    IClientRepo getClientRepo();
}
