/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
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

package appeng.parts.reporting;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import gto_ae.api.config.ExtendedSettings;
import gto_ae.helpers.facility_management.FacilityManagement;
import gto_ae.helpers.facility_management.IFacilityManagementHost;
import gto_ae.helpers.facility_management.IO;
import gto_ae.helpers.facility_management.WorkingStatus;
import gto_ae.menu.implementations.FacilityManagementMenu;

import appeng.api.config.YesNo;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.util.IConfigManager;
import appeng.core.AppEng;
import appeng.items.parts.PartModels;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.parts.PartModel;
import appeng.util.ConfigManager;

public class FacilityManagementPart extends AbstractDisplayPart
        implements IFacilityManagementHost {

    @PartModels
    public static final ResourceLocation MODEL_OFF = new ResourceLocation(AppEng.MOD_ID,
            "part/facility_terminal_off");
    @PartModels
    public static final ResourceLocation MODEL_ON = new ResourceLocation(AppEng.MOD_ID,
            "part/facility_terminal_on");

    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_OFF, MODEL_STATUS_OFF);
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_ON);
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_HAS_CHANNEL);

    private final ConfigManager configManager = new ConfigManager(() -> this.getHost().markForSave());

    private final FacilityManagement logic = new FacilityManagement(this);

    public FacilityManagementPart(IPartItem<?> partItem) {
        super(partItem, true);
        this.configManager.registerSetting(ExtendedSettings.WORKING_STATUS_SETTING, WorkingStatus.NONE);
        this.configManager.registerSetting(ExtendedSettings.FILTER_MODE, IO.NONE);
        this.configManager.registerSetting(ExtendedSettings.HAS_CPU_TASK, YesNo.UNDECIDED);
    }

    @Override
    public boolean onPartActivate(Player player, InteractionHand hand, Vec3 pos) {
        if (!super.onPartActivate(player, hand, pos) && !isClientSide()) {
            MenuOpener.open(FacilityManagementMenu.TYPE, player, MenuLocators.forPart(this));
        }
        return true;
    }

    @Override
    public IPartModel getStaticModels() {
        return this.selectModel(MODELS_OFF, MODELS_ON, MODELS_HAS_CHANNEL);
    }

    @Override
    public IConfigManager getConfigManager() {
        return configManager;
    }

    public void writeToNBT(CompoundTag tag) {
        super.writeToNBT(tag);
        configManager.writeToNBT(tag);
        logic.serializeNBT(tag);
    }

    public void readFromNBT(CompoundTag tag) {
        super.readFromNBT(tag);
        configManager.readFromNBT(tag);
        logic.deserializeNBT(tag);
    }

    @Override
    public FacilityManagement getLogic() {
        return logic;
    }

    @Override
    public void markForSave() {
        getHost().markForSave();
    }

    @Override
    public IO getIOModeFilter() {
        return configManager.getSetting(ExtendedSettings.FILTER_MODE);
    }

    @Override
    public void setFilterMode(IO newMode) {
        configManager.putSetting(ExtendedSettings.FILTER_MODE, newMode);
    }

    @Override
    public WorkingStatus getWorkingFilter() {
        return configManager.getSetting(ExtendedSettings.WORKING_STATUS_SETTING);
    }

    @Override
    public void setWorkingFilter(WorkingStatus newStatus) {
        configManager.putSetting(ExtendedSettings.WORKING_STATUS_SETTING, newStatus);
    }

    @Override
    public YesNo getCraftingJobsFilter() {
        return configManager.getSetting(ExtendedSettings.HAS_CPU_TASK);
    }

    @Override
    public void setFilterCraftingJobs(YesNo newValue) {
        configManager.putSetting(ExtendedSettings.HAS_CPU_TASK, newValue);
    }

}
