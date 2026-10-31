package com.gto.ae.client.gui.me.facility_management;

import java.util.function.BiFunction;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.player.Player;

import appeng.client.gui.widgets.IconButton;

import com.gto.ae.api.util.DirectionalGlobalPos;

public final class FacilityHighlightProvider {
    private static BiFunction<DirectionalGlobalPos, Player, IconButton> factory;

    private FacilityHighlightProvider() {
    }

    public static void register(BiFunction<DirectionalGlobalPos, Player, IconButton> factory) {
        FacilityHighlightProvider.factory = factory;
    }

    public static @Nullable IconButton create(DirectionalGlobalPos position, Player player) {
        return factory == null ? null : factory.apply(position, player);
    }
}
