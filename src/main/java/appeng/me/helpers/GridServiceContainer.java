package appeng.me.helpers;

import java.util.Map;

import appeng.api.networking.IGridServiceProvider;

public record GridServiceContainer(
        Map<Class<?>, IGridServiceProvider> services,
        IGridServiceProvider[] serverStartTickServices,
        IGridServiceProvider[] levelStartTickServices,
        IGridServiceProvider[] levelEndtickServices,
        IGridServiceProvider[] serverEndTickServices) {

    public boolean hasServerStartTick() {
        return serverStartTickServices.length > 0;
    }

    public boolean hasLevelStartTick() {
        return levelStartTickServices.length > 0;
    }

    public boolean hasLevelEndTick() {
        for (IGridServiceProvider service : levelEndtickServices) {
            if (service.hasLevelEndTick()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasServerEndTick() {
        return serverEndTickServices.length > 0;
    }
}
