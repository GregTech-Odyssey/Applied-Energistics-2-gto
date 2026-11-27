package de.mari_023.ae2wtlib;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public class AE2wtlibConfig {
    private static final double DEFAULT_MAGNET_CARD_RANGE = 16.0;

    private static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.DoubleValue MAGNET_CARD_RANGE;

    static {
        var builder = new ForgeConfigSpec.Builder();
        MAGNET_CARD_RANGE = builder
                .comment("Pickup radius of the Magnet Card in blocks.")
                .defineInRange("magnetCardRange", DEFAULT_MAGNET_CARD_RANGE, 0.0, 256.0);
        SPEC = builder.build();
    }

    public static AE2wtlibConfig INSTANCE = new AE2wtlibConfig();

    public double magnetCardRange() {
        return SPEC.isLoaded() ? MAGNET_CARD_RANGE.get() : DEFAULT_MAGNET_CARD_RANGE;
    }

    public static void init() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC, "ae2wtlib.toml");
    }
}
