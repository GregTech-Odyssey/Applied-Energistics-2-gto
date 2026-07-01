package appeng.parts;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;

import appeng.util.BlockApiCache;

/**
 * Utility class to cache an API that is adjacent to a part.
 */
public class PartAdjacentApi<C> {
    private final AEBasePart part;
    private final Capability<C> apiLookup;
    private BlockApiCache<C> apiCache;

    public PartAdjacentApi(AEBasePart part, Capability<C> apiLookup) {
        this.apiLookup = apiLookup;
        this.part = part;
    }

    @Nullable
    private BlockApiCache<C> getApiCache() {
        var apiCache = this.apiCache;
        if (apiCache == null) {
            if (!(part.getLevel() instanceof ServerLevel serverLevel)) {
                return null;
            }
            var host = part.getHost().getBlockEntity();
            var targetPos = host.getBlockPos().relative(part.getSide());
            this.apiCache = apiCache = BlockApiCache.create(apiLookup, serverLevel, targetPos);
        }
        return apiCache;
    }

    public void clear() {
        apiCache = null;
    }

    @Nullable
    public BlockEntity getBlockEntity() {
        var apiCache = getApiCache();
        if (apiCache == null) {
            return null;
        }
        return apiCache.getBlockEntity();
    }

    @Nullable
    public C find() {
        var apiCache = getApiCache();
        if (apiCache == null) {
            return null;
        }
        return apiCache.find(part.getSide().getOpposite());
    }
}
