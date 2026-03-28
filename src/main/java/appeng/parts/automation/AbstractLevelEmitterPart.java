/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.parts.automation;

import com.google.common.collect.ImmutableSet;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongMaps;

import appeng.api.config.RedstoneMode;
import appeng.api.config.Setting;
import appeng.api.config.Settings;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.stacks.AEKey;
import appeng.api.util.AECableType;
import appeng.api.util.IConfigManager;
import appeng.helpers.IConfigInvHost;
import appeng.util.Platform;
import appeng.util.SettingsFrom;

import gto_ae.helpers.facility_management.IStatusTracked;
import gto_ae.helpers.facility_management.ThroughputCounter;
import gto_ae.helpers.facility_management.WorkingStatus;

public abstract class AbstractLevelEmitterPart extends UpgradeablePart implements IStatusTracked {
    private boolean prevState;
    protected long lastReportedValue;
    private long reportingValue;

    private boolean clientSideOn;
    private final ThroughputCounter throughputCounter = new ThroughputCounter();

    public AbstractLevelEmitterPart(IPartItem<?> partItem) {
        super(partItem);

        // Level emitters do not require a channel to function
        getMainNode().setFlags();
        throughputCounter.setDisableShowingInTerminal(true);

        this.getConfigManager().registerSetting(Settings.REDSTONE_EMITTER, RedstoneMode.HIGH_SIGNAL);
    }

    protected abstract void configureWatchers();

    protected abstract boolean hasDirectOutput();

    protected abstract boolean getDirectOutput();

    @Override
    protected final void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);

        if (getMainNode().hasGridBooted()) {
            updateState();
        }
    }

    @Override
    public void writeToStream(FriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeBoolean(prevState);
    }

    @Override
    public boolean readFromStream(FriendlyByteBuf data) {
        var changed = super.readFromStream(data);
        var wasOn = this.clientSideOn;
        this.clientSideOn = data.readBoolean();
        return changed || wasOn != this.clientSideOn;
    }

    @Override
    public void writeVisualStateToNBT(CompoundTag data) {
        super.writeVisualStateToNBT(data);

        data.putBoolean("on", isLevelEmitterOn());
    }

    @Override
    public void readVisualStateFromNBT(CompoundTag data) {
        super.readVisualStateFromNBT(data);

        this.clientSideOn = data.getBoolean("on");
    }

    protected void updateState() {
        var isOn = this.isLevelEmitterOn();
        if (this.prevState != isOn) {
            this.getHost().markForUpdate();
            var te = this.getHost().getBlockEntity();
            this.prevState = isOn;
            Platform.notifyBlocksOfNeighbors(te.getLevel(), te.getBlockPos());
            Platform.notifyBlocksOfNeighbors(te.getLevel(), te.getBlockPos().relative(this.getSide()));
        }
    }

    public final long getReportingValue() {
        return this.reportingValue;
    }

    public final void setReportingValue(long v) {
        this.reportingValue = v;
        onReportingValueChanged();
        this.updateState();
    }

    protected void onReportingValueChanged() {
    }

    @Override
    public final int isProvidingStrongPower() {
        return prevState ? 15 : 0;
    }

    @Override
    public final int isProvidingWeakPower() {
        return prevState ? 15 : 0;
    }

    @Override
    public final void animateTick(Level level, BlockPos pos, RandomSource r) {
        if (this.isLevelEmitterOn()) {
            final Direction d = this.getSide();

            final double d0 = d.getStepX() * 0.45F + (r.nextFloat() - 0.5F) * 0.2D;
            final double d1 = d.getStepY() * 0.45F + (r.nextFloat() - 0.5F) * 0.2D;
            final double d2 = d.getStepZ() * 0.45F + (r.nextFloat() - 0.5F) * 0.2D;

            level.addParticle(DustParticleOptions.REDSTONE, 0.5 + pos.getX() + d0, 0.5 + pos.getY() + d1,
                    0.5 + pos.getZ() + d2, 0.0D, 0.0D, 0.0D);
        }
    }

    protected boolean isLevelEmitterOn() {
        if (isClientSide()) {
            return clientSideOn;
        }

        if (!this.getMainNode().isActive()) {
            return false;
        }

        if (hasDirectOutput()) {
            return getDirectOutput();
        }

        final boolean flipState = this.getConfigManager()
                .getSetting(Settings.REDSTONE_EMITTER) == RedstoneMode.LOW_SIGNAL;
        return flipState == (this.reportingValue >= this.lastReportedValue + 1);
    }

    @Override
    public final boolean canConnectRedstone() {
        return true;
    }

    @Override
    public void readFromNBT(CompoundTag data) {
        super.readFromNBT(data);
        this.lastReportedValue = data.getLong("lastReportedValue");
        this.reportingValue = data.getLong("reportingValue");
        this.prevState = data.getBoolean("prevState");
    }

    @Override
    public void writeToNBT(CompoundTag data) {
        super.writeToNBT(data);
        data.putLong("lastReportedValue", this.lastReportedValue);
        data.putLong("reportingValue", this.reportingValue);
        data.putBoolean("prevState", this.prevState);
    }

    @Override
    public final float getCableConnectionLength(AECableType cable) {
        return 16;
    }

    @Override
    public final void getBoxes(IPartCollisionHelper bch) {
        bch.addBox(7, 7, 11, 9, 9, 16);
    }

    @Override
    public final AECableType getDesiredConnectionType() {
        return AECableType.SMART;
    }

    @Override
    public final void onSettingChanged(IConfigManager manager, Setting<?> setting) {
        this.configureWatchers();
    }

    @Override
    public void importSettings(SettingsFrom mode, CompoundTag input, @Nullable Player player) {
        super.importSettings(mode, input, player);
        setReportingValue(input.getLong("reportingValue"));
    }

    @Override
    public void exportSettings(SettingsFrom mode, CompoundTag output) {
        super.exportSettings(mode, output);

        if (mode == SettingsFrom.MEMORY_CARD) {
            output.putLong("reportingValue", reportingValue);
        }
    }

    @Override
    public Reference2LongMap<AEKey> getConfiguredSetting() {
        if (this instanceof IConfigInvHost host) {
            var key = host.getConfig().getKey(0);
            if (key != null) {
                return Reference2LongMaps.singleton(key, reportingValue);
            }
        }
        return IStatusTracked.super.getConfiguredSetting();
    }

    @Override
    protected boolean shouldSendPowerStateToClient() {
        return false; // We handle this completely in our enabled flag
    }

    @Override
    protected boolean shouldSendMissingChannelStateToClient() {
        return false; // We handle this completely in our enabled flag
    }

    @Override
    public ImmutableSet<ICraftingLink> getRequestedJobs() {
        return ImmutableSet.of();
    }

    @Override
    public @NotNull WorkingStatus getStatus() {
        return lastReportedValue > 0 ? WorkingStatus.WORKING : WorkingStatus.IDLE;
    }

    @Override
    public void openGui(Player player) {
        this.onPartActivate(player, InteractionHand.MAIN_HAND, getBlockEntity().getBlockPos().getCenter());
    }

    @Override
    public @NotNull ThroughputCounter getThroughputCounter() {
        return throughputCounter;
    }
}
