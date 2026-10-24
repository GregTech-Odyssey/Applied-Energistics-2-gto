package appeng.me.helpers;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.player.Player;

import appeng.api.networking.security.IActionHost;

/**
 * A player's crafting request, including its per-job completion notification choice.
 */
public final class CraftingJobSource extends PlayerSource {
    private final boolean following;

    public CraftingJobSource(Player player, @Nullable IActionHost via, boolean following) {
        super(player, via);
        this.following = following;
    }

    public boolean isFollowing() {
        return following;
    }
}
