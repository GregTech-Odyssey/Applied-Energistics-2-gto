package com.ultramega.ae2insertexportcard.registry;

import com.ultramega.ae2insertexportcard.AE2InsertExportCard;

import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;

public final class ModSlotSemantics {
    public static final SlotSemantic INSERT_CONFIG = SlotSemantics
            .register(AE2InsertExportCard.MOD_ID.toUpperCase() + "_INSERT_CONFIG", false);
    public static final SlotSemantic EXPORT_CONFIG = SlotSemantics
            .register(AE2InsertExportCard.MOD_ID.toUpperCase() + "_EXPORT_CONFIG", false);
}
