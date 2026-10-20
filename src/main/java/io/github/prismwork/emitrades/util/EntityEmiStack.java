/*
 * Adapted from EMI Trades by Prismwork and Flamarine.
 * Copyright (c) 2022-2023 Prismwork. Licensed under MIT; see LICENSE-EMITrades.
 * Modified for the GTO AE fork: detached snapshots and Mojang mappings.
 */
package io.github.prismwork.emitrades.util;

import java.util.List;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import dev.emi.emi.EmiPort;
import dev.emi.emi.EmiUtil;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.screen.tooltip.RemainderTooltipComponent;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

@SuppressWarnings("DuplicatedCode")
public class EntityEmiStack extends EmiStack {
    private static final ResourceLocation EMPTY_ID = new ResourceLocation("emitrades", "empty");
    private final @Nullable TradeEntitySnapshot snapshot;
    private final float scale;

    protected EntityEmiStack(@Nullable Entity entity, float scale) {
        this(entity == null ? null : TradeEntitySnapshot.of(entity), scale);
    }

    private EntityEmiStack(@Nullable TradeEntitySnapshot snapshot, float scale) {
        this.snapshot = snapshot;
        this.scale = scale;
    }

    public static EntityEmiStack of(@Nullable Entity entity) {
        return ofScaled(entity, 8.0f);
    }

    public static EntityEmiStack ofScaled(@Nullable Entity entity, float scale) {
        return new EntityEmiStack(entity, scale);
    }

    @Override
    public EmiStack copy() {
        EntityEmiStack stack = ofSnapshot(snapshot, scale);
        stack.amount = amount;
        stack.chance = chance;
        stack.setRemainder(getRemainder().copy());
        stack.comparison = comparison;
        return stack;
    }

    @Override
    public boolean isEmpty() {
        return snapshot == null;
    }

    @Override
    public void render(GuiGraphics draw, int x, int y, float delta, int flags) {
        if ((flags & RENDER_ICON) == 0)
            return;
        Entity entity = snapshot == null ? null : snapshot.entity();
        if (entity != null) {
            MouseHandler mouse = Minecraft.getInstance().mouseHandler;
            if (entity instanceof LivingEntity living)
                drawLivingEntity(draw, x, y, scale,
                        (float) (mouse.xpos() * draw.guiWidth() / Minecraft.getInstance().getWindow().getScreenWidth()),
                        (float) (mouse.ypos() * draw.guiHeight()
                                / Minecraft.getInstance().getWindow().getScreenHeight()),
                        living);
            else
                drawEntity(draw, x, y, scale,
                        (float) (mouse.xpos() * draw.guiWidth() / Minecraft.getInstance().getWindow().getScreenWidth()),
                        (float) (mouse.ypos() * draw.guiHeight()
                                / Minecraft.getInstance().getWindow().getScreenHeight()),
                        entity);
        }
    }

    @Override
    public CompoundTag getNbt() {
        return snapshot == null ? null : snapshot.tag();
    }

    @Override
    public Object getKey() {
        return snapshot == null ? EntityEmiStack.class : snapshot;
    }

    @Override
    public ResourceLocation getId() {
        return snapshot == null ? EMPTY_ID : snapshot.id();
    }

    @Override
    public List<Component> getTooltipText() {
        return List.of(getName());
    }

    @Override
    public List<ClientTooltipComponent> getTooltip() {
        var list = new ObjectArrayList<ClientTooltipComponent>(3);
        if (snapshot != null) {
            list.add(ClientTooltipComponent.create(getName().getVisualOrderText()));
            list.add(ClientTooltipComponent.create(
                    EmiPort.literal(EmiUtil.getModName(snapshot.modId()), ChatFormatting.BLUE, ChatFormatting.ITALIC)
                            .getVisualOrderText()));
            if (!getRemainder().isEmpty())
                list.add(new RemainderTooltipComponent(this));
        }
        return list;
    }

    @Override
    public Component getName() {
        return snapshot == null ? Component.empty() : snapshot.name();
    }

    public static EntityEmiStack ofSnapshot(@Nullable TradeEntitySnapshot snapshot, float scale) {
        return new EntityEmiStack(snapshot, scale);
    }

    public static void drawLivingEntity(GuiGraphics ctx, int x, int y, float size, float mouseX, float mouseY,
            LivingEntity entity) {
        float mouseX0 = x + 8 - mouseX;
        float mouseY0 = y + 8 - mouseY;
        float f = (float) Math.atan(mouseX0 / 40.0F);
        float g = (float) Math.atan(mouseY0 / 40.0F);
        Quaternionf quaternionf = (new Quaternionf()).rotateZ(3.1415927F);
        Quaternionf quaternionf2 = (new Quaternionf()).rotateX(g * 20.0F * 0.017453292F);
        quaternionf.mul(quaternionf2);
        float h = entity.yBodyRot;
        float i = entity.getYRot();
        float j = entity.getXRot();
        float k = entity.yHeadRotO;
        float l = entity.yHeadRot;
        entity.yBodyRot = 180.0F + f * 20.0F;
        entity.setYRot(180.0F + f * 40.0F);
        entity.setXRot(-g * 20.0F);
        entity.yHeadRot = entity.getYRot();
        entity.yHeadRotO = entity.getYRot();
        draw(ctx, x, y, size, quaternionf, quaternionf2, entity);
        entity.yBodyRot = h;
        entity.setYRot(i);
        entity.setXRot(j);
        entity.yHeadRotO = k;
        entity.yHeadRot = l;
    }

    public static void drawEntity(GuiGraphics ctx, int x, int y, float size, float mouseX, float mouseY,
            Entity entity) {
        float mouseX0 = x + 8 - mouseX;
        float mouseY0 = y + 8 - mouseY;
        float f = (float) Math.atan(mouseX0 / 40.0F);
        float g = (float) Math.atan(mouseY0 / 40.0F);
        Quaternionf quaternionf = (new Quaternionf()).rotateZ(3.1415927F);
        Quaternionf quaternionf2 = (new Quaternionf()).rotateX(g * 20.0F * 0.017453292F);
        quaternionf.mul(quaternionf2);
        float i = entity.getYRot();
        float j = entity.getXRot();
        entity.setYRot(180.0F + f * 40.0F);
        entity.setXRot(-g * 20.0F);
        draw(ctx, x, y, size, quaternionf, quaternionf2, entity);
        entity.setYRot(i);
        entity.setXRot(j);
    }

    @SuppressWarnings("deprecation")
    private static void draw(GuiGraphics ctx, int x, int y, float size, Quaternionf quaternion,
            @Nullable Quaternionf quaternion2, Entity entity) {
        ctx.pose().pushPose();
        ctx.pose().translate(x + 8, y + 16, 50.0);
        ctx.pose().mulPoseMatrix((new Matrix4f()).scaling(size, size, -size));
        ctx.pose().mulPose(quaternion);
        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        if (quaternion2 != null) {
            quaternion2.conjugate();
            dispatcher.overrideCameraOrientation(quaternion2);
        }

        dispatcher.setRenderShadow(false);
        RenderSystem.runAsFancy(
                () -> dispatcher.render(entity, 0.0, 0.0, 0.0, 0.0F, 1.0F, ctx.pose(), ctx.bufferSource(), 15728880));
        ctx.flush();
        dispatcher.setRenderShadow(true);
        ctx.pose().popPose();
        Lighting.setupFor3DItems();
    }
}
