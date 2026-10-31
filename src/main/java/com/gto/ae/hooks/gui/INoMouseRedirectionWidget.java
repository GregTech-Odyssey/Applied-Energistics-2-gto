package com.gto.ae.hooks.gui;

public interface INoMouseRedirectionWidget {
    default boolean shouldHandleRightClick() {
        return true;
    }
}
