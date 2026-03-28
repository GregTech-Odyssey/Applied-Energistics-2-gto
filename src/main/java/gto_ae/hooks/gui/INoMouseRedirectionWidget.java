package gto_ae.hooks.gui;

public interface INoMouseRedirectionWidget {
    default boolean shouldHandleRightClick() {
        return true;
    }
}
