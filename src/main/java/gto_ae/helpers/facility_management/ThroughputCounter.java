package gto_ae.helpers.facility_management;

import net.minecraft.network.FriendlyByteBuf;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyMap;
import appeng.core.AEConfig;

/**
 * x秒统计一次，记录最近x秒内工作过的物品
 * <p>
 * 从网络移除物品时使用{@link ThroughputCounter#remove(AEKey, long)}，从网络添加物品时使用{@link ThroughputCounter#add(AEKey, long)}
 * </p>
 * 这也意味着，从网络流出的物品会被记录为负数，向网络流入的物品会被记录为正数
 */
public class ThroughputCounter extends AEKeyMap<AEKey> {
    public static final ThroughputCounter EMPTY = new ThroughputCounter() {
        @Override
        public void add(AEKey key, long count) {
            throw new UnsupportedOperationException("Cannot modify empty throughput counter");
        }

        @Override
        public void remove(AEKey key, long count) {
            throw new UnsupportedOperationException("Cannot modify empty throughput counter");
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException("Cannot modify empty throughput counter");
        }
    };

    private boolean hasPositiveValues = false;
    private boolean hasNegativeValues = false;

    private boolean disableShowingInTerminal = false;

    private long lastRefreshTime = 0;
    private long lastRefreshInterval = 0;
    private ThroughputCounter immutableView = null;

    public void add(AEKey key, long count) {
        if (count == 0) {
            return;
        }
        this.addTo(key, count);
        hasPositiveValues |= count > 0;
        hasNegativeValues |= count < 0;
    }

    public void remove(AEKey key, long count) {
        if (count == 0) {
            return;
        }
        this.addTo(key, -count);
        hasPositiveValues |= count < 0;
        hasNegativeValues |= count > 0;
    }

    @Override
    public void clear() {
        super.clear();
        hasPositiveValues = false;
        hasNegativeValues = false;
    }

    public boolean hasPositiveValues() {
        return hasPositiveValues;
    }

    public boolean hasNegativeValues() {
        return hasNegativeValues;
    }

    public void tickRefresh() {
        var now = System.currentTimeMillis();
        if (now - lastRefreshTime >= AEConfig.instance().getThroughputCounterRefreshRate()) {
            lastRefreshInterval = lastRefreshTime == 0 ? 0 : now - lastRefreshTime;
            lastRefreshTime = now;
            this.captureImmutableView();
            this.clear();
        }
    }

    private void captureImmutableView() {
        immutableView = new ThroughputCounter();
        immutableView.putAll(this);
        immutableView.hasPositiveValues = this.hasPositiveValues;
        immutableView.hasNegativeValues = this.hasNegativeValues;
    }

    public static void writeToBuffer(FriendlyByteBuf buf, ThroughputCounter k) {
        buf.writeBoolean(k == EMPTY);
        if (k == EMPTY) {
            return;
        }
        if (k.immutableView == null) {
            k.captureImmutableView();
        }
        buf.writeInt(k.immutableView.size());
        for (var entry : k.immutableView.reference2LongEntrySet()) {
            AEKey.writeKey(buf, entry.getKey());
            buf.writeVarLong(entry.getLongValue());
        }
        buf.writeVarLong(k.getLastRefreshInterval());
        buf.writeVarLong(k.getLastRefreshTime());
        buf.writeBoolean(k.isDisableShowingInTerminal());
    }

    public static ThroughputCounter readFromBuffer(FriendlyByteBuf buf) {
        if (buf.readBoolean()) {
            return EMPTY;
        }
        var ret = new ThroughputCounter();
        int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            var key = AEKey.readKey(buf);
            var value = buf.readVarLong();
            ret.add(key, value);
        }
        ret.lastRefreshInterval = buf.readVarLong();
        ret.lastRefreshTime = buf.readVarLong();
        ret.disableShowingInTerminal = buf.readBoolean();
        return ret;
    }

    public long getLastRefreshTime() {
        return lastRefreshTime;
    }

    public long getLastRefreshInterval() {
        return lastRefreshInterval;
    }

    public boolean isDisableShowingInTerminal() {
        return disableShowingInTerminal;
    }

    public void setDisableShowingInTerminal(boolean disableShowingInTerminal) {
        this.disableShowingInTerminal = disableShowingInTerminal;
    }
}
