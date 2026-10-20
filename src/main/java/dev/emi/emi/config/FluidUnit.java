package dev.emi.emi.config;

import dev.emi.emi.EmiPort;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.config.ConfigEnum;
import it.unimi.dsi.fastutil.doubles.Double2ObjectFunction;
import net.minecraft.network.chat.Component;

public enum FluidUnit implements ConfigEnum {
    LITERS("liters", a -> EmiPort.translatable("emi.fluid.amount.liters",
        EmiRenderHelper.TEXT_FORMAT.format((int) a))),
    MILLIBUCKETS("millibuckets", a -> EmiPort.translatable("emi.fluid.amount.millibuckets",
        EmiRenderHelper.TEXT_FORMAT.format((int) a))),
    ;

    public static final int BUCKET = 1000;
    public static final int BOTTLE = 250;

    private final String name;
    private final Component translation;
    private final Double2ObjectFunction<Component> translator;

    private FluidUnit(String name, Double2ObjectFunction<Component> translator) {
        this.name = name;
        translation = EmiPort.translatable("emi.unit." + name);
        this.translator = translator;
    }

    @Override
    public String getName() {
        return name;
    }

    public Component translate(double amount) {
        return translator.apply(Double.valueOf(amount));
    }

    @Override
    public Component getText() {
        return translation;
    }
}
