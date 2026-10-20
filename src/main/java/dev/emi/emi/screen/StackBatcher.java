package dev.emi.emi.screen;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.emi.emi.EmiPort;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.forge.EmiForgePlatform;
import dev.emi.emi.runtime.EmiLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.*;
import org.jetbrains.annotations.NotNull;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

/**
 * @author Una "unascribed" Thompson
 */
public class StackBatcher {
    private static MethodHandle sodiumSpriteHandle;
    private static boolean isIncompatibleSodiumLoaded;

    static {
        try {
            Class<?> clazz = null;
            // TODO this is a 1.18 -> 1.19 refactor for Sodium
            try {
                clazz = Class.forName("net.caffeinemc.sodium.render.texture.SpriteUtil");
            } catch (Throwable t) {
            }
            if (clazz == null) {
                clazz = Class.forName("me.jellysquid.mods.sodium.client.render.texture.SpriteUtil");
            }
            sodiumSpriteHandle = MethodHandles.lookup()
                    .findStatic(clazz, "markSpriteActive", MethodType.methodType(void.class, TextureAtlasSprite.class));
            if (sodiumSpriteHandle != null) {
                EmiLog.info("Discovered Sodium");
            }

            if (EmiForgePlatform.isModLoaded("sodium") || EmiForgePlatform.isModLoaded("rubidium")) {
                // Check for the modern VertexBufferWriter API. If so, we are likely on Sodium 0.5+ (or a derivative),
                // which can generally handle a custom VertexConsumer properly.
                try {
                    Class.forName("net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter");
                } catch (Throwable t) {
                    // Check for the legacy VBW API which *cannot* handle this
                    try {
                        Class.forName("me.jellysquid.mods.sodium.client.render.vertex.VertexBufferWriter");
                        // Success means the class exists
                        isIncompatibleSodiumLoaded = true;
                        EmiLog.info("Batching stack renderer disabled for compatibility with legacy Sodium");
                    } catch (Throwable t2) {
                        // Old enough Sodiums shouldn't have an issue
                    }
                }
            }
        } catch (Throwable e) {
        }
    }

    public interface Batchable {
        boolean isSideLit();

        boolean isUnbatchable();

        void setUnbatchable();

        void renderForBatch(MultiBufferSource vcp, GuiGraphics draw, int x, int y, int z, float delta);
    }

    private final BatcherVertexConsumerProvider imm;
    private final MultiBufferSource unlitFacade;
    private final Reference2ObjectLinkedOpenHashMap<RenderType, VertexBuffer> buffers = new Reference2ObjectLinkedOpenHashMap<>();
    private final Matrix4f matrix = new Matrix4f();
    private final ReferenceOpenHashSet<TextureAtlasSprite> spritesToUpdate = new ReferenceOpenHashSet<>();
    private boolean populated = false;
    private boolean dirty = false;
    private int x;
    private int y;
    private int z;

    public static final List<RenderType> EXTRA_RENDER_LAYERS = Lists.newArrayList();

    public static boolean isEnabled() {
        return EmiConfig.useBatchedRenderer && !isIncompatibleSodiumLoaded;
    }

    public StackBatcher() {
        Reference2ObjectOpenHashMap<RenderType, BufferBuilder> buffers = new Reference2ObjectOpenHashMap<>(6);
        assign(buffers, RenderType.solid());
        assign(buffers, RenderType.cutout());
        assign(buffers, RenderType.translucent());
        assign(buffers, Sheets.solidBlockSheet());
        assign(buffers, Sheets.cutoutBlockSheet());
        assign(buffers, Sheets.translucentCullBlockSheet());
        assign(buffers, RenderType.glint());
        assign(buffers, RenderType.glintDirect());
        assign(buffers, RenderType.entityGlint());
        for (RenderType layer : EXTRA_RENDER_LAYERS) {
            assign(buffers, layer);
        }
        imm = new BatcherVertexConsumerProvider(new BufferBuilder(256), buffers);
        unlitFacade = new UnlitFacade(imm);
    }

    private void assign(Reference2ObjectOpenHashMap<RenderType, BufferBuilder> buffers, RenderType layer) {
        buffers.put(layer, new BufferBuilder(layer.bufferSize()));
    }

    public boolean isPopulated() {
        return populated;
    }

    public void repopulate() {
        dirty = true;
    }

    public void begin(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
        if (dirty) {
            populated = false;
            dirty = false;
            spritesToUpdate.clear();
        }
    }

    public void render(Batchable batchable, GuiGraphics draw, int x, int y, float delta) {
        if (!populated) {
            try {
                batchable.renderForBatch(batchable.isSideLit() ? imm : unlitFacade, draw, x - this.x, y + this.y, z,
                        delta);
            } catch (Throwable t) {
                if (EmiConfig.devMode) {
                    EmiLog.error("Batchable threw exception during batched rendering. See log for info", t);
                }
                batchable.setUnbatchable();
            }
        }
    }

    public void render(EmiIngredient stack, GuiGraphics draw, int x, int y, float delta) {
        render(stack, draw, x, y, delta, ~EmiIngredient.RENDER_AMOUNT);
    }

    public void render(EmiIngredient stack, GuiGraphics draw, int x, int y, float delta, int flags) {
        if (stack instanceof Batchable b && !b.isUnbatchable() && isEnabled()
                && (flags & EmiIngredient.RENDER_ICON) != 0) {
            if (!populated) {
                try {
                    b.renderForBatch(b.isSideLit() ? imm : unlitFacade, draw, x - this.x, -y - this.y, z, delta);
                    if (sodiumSpriteHandle != null && !stack.isEmpty()) {
                        ItemStack is = stack.getEmiStacks().get(0).getItemStack();
                        Minecraft client = Minecraft.getInstance();
                        BakedModel model = client.getItemRenderer().getItemModelShaper().getItemModel(is);
                        if (model != null) {
                            List<BakedQuad> quads = EmiPort.getQuads(model);
                            for (BakedQuad quad : quads) {
                                if (quad != null) {
                                    spritesToUpdate.add(quad.getSprite());
                                }
                            }
                        }
                    }
                } catch (Throwable t) {
                    if (EmiConfig.devMode) {
                        EmiLog.error("Stack threw exception during batched rendering. See log for info", t);
                    }
                    b.setUnbatchable();
                }
            }
            stack.render(draw, x, y, delta, flags & (~EmiIngredient.RENDER_ICON));
        } else {
            stack.render(draw, x, y, delta, flags);
        }
    }

    public void draw() {
        if (!isEnabled()) {
            return;
        }
        if (sodiumSpriteHandle != null) {
            try {
                for (TextureAtlasSprite sprite : spritesToUpdate) {
                    sodiumSpriteHandle.invoke(sprite);
                }
            } catch (Throwable t) {
            }
        }
        if (!populated) {
            bake();
            populated = true;
        }
        RenderSystem.enableDepthTest();
        Lighting.setupFor3DItems();
        Matrix4f mat = matrix.set(RenderSystem.getModelViewMatrix()).scale(1, -1, 1);
        // Flipped Y creates an offset
        mat.translate(x, -y - 16, 0);
        for (var iterator = buffers.reference2ObjectEntrySet().fastIterator(); iterator.hasNext();) {
            var en = iterator.next();
            en.getKey().setupRenderState();
            EmiPort.setShader(en.getValue(), mat);
            en.getKey().clearRenderState();
        }
        BufferUploader.reset();
    }

    private void bake() {
        imm.drawCurrentLayer();
        buffers.values().forEach(VertexBuffer::close);
        buffers.clear();
        for (RenderType layer : imm.getLayerBuffers().keySet()) {
            bake(layer);
        }
    }

    public void bake(RenderType layer) {
        BufferBuilder bldr = imm.getBufferInternal(layer);
        if (!imm.getActiveConsumers().remove(bldr))
            return;
        VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        EmiPort.upload(vb, bldr);
        buffers.put(layer, vb);
        bldr.discard();
    }

    // Apparently BufferBuilder leaks memory in vanilla. Go figure
    public static class ClaimedCollection {
        private final ReferenceOpenHashSet<StackBatcher> claimed = new ReferenceOpenHashSet<>();
        private final ObjectArrayList<StackBatcher> unclaimed = new ObjectArrayList<>();

        public @NotNull StackBatcher claim() {
            synchronized (this) {
                StackBatcher batcher;
                if (unclaimed.isEmpty()) {
                    batcher = new StackBatcher();
                } else {
                    batcher = unclaimed.remove(unclaimed.size() - 1);
                }
                claimed.add(batcher);
                return batcher;
            }
        }

        public void unclaim(@NotNull StackBatcher batcher) {
            synchronized (this) {
                claimed.remove(batcher);
                unclaimed.add(batcher);
            }
        }

        public void unclaimAll() {
            synchronized (this) {
                for (StackBatcher batcher : claimed) {
                    unclaimed.add(batcher);
                }
                claimed.clear();
            }
        }
    }

    /*
     * This class is mostly a copy of a 1.18.2 implementation of VertexConsumerProvider.Immediate The reimplementation
     * allows compatibility with shader mods, as well as less hackery.
     */
    private static class BatcherVertexConsumerProvider implements MultiBufferSource {
        protected final BufferBuilder fallbackBuffer;
        protected final Reference2ObjectOpenHashMap<RenderType, BufferBuilder> layerBuffers;
        protected Optional<RenderType> currentLayer = Optional.empty();
        protected final ReferenceOpenHashSet<BufferBuilder> activeConsumers = new ReferenceOpenHashSet<>();

        protected BatcherVertexConsumerProvider(BufferBuilder fallbackBuffer,
                Reference2ObjectOpenHashMap<RenderType, BufferBuilder> layerBuffers) {
            this.fallbackBuffer = fallbackBuffer;
            this.layerBuffers = layerBuffers;
        }

        @Override
        public VertexConsumer getBuffer(RenderType renderLayer) {
            Optional<RenderType> optional = renderLayer.asOptional();
            BufferBuilder bufferBuilder = this.getBufferInternal(renderLayer);
            if (!Objects.equals(this.currentLayer, optional)) {
                RenderType renderLayer2;
                if (this.currentLayer.isPresent()
                        && !this.layerBuffers.containsKey(renderLayer2 = this.currentLayer.get())) {
                    this.draw(renderLayer2);
                }
                if (this.activeConsumers.add(bufferBuilder)) {
                    bufferBuilder.begin(renderLayer.mode(), renderLayer.format());
                }
                this.currentLayer = optional;
            }
            return bufferBuilder;
        }

        private BufferBuilder getBufferInternal(RenderType layer) {
            return this.layerBuffers.getOrDefault(layer, this.fallbackBuffer);
        }

        public void drawCurrentLayer() {
            if (this.currentLayer.isPresent()) {
                RenderType renderLayer = this.currentLayer.get();
                if (!this.layerBuffers.containsKey(renderLayer)) {
                    this.draw(renderLayer);
                }
                this.currentLayer = Optional.empty();
            }
        }

        public void draw(RenderType layer) {
            BufferBuilder bufferBuilder = this.getBufferInternal(layer);
            boolean bl = Objects.equals(this.currentLayer, layer.asOptional());
            if (!bl && bufferBuilder == this.fallbackBuffer) {
                return;
            }
            if (!this.activeConsumers.remove(bufferBuilder)) {
                return;
            }
            layer.end(bufferBuilder, VertexSorting.ORTHOGRAPHIC_Z);
            if (bl) {
                this.currentLayer = Optional.empty();
            }
        }

        public Reference2ObjectOpenHashMap<RenderType, BufferBuilder> getLayerBuffers() {
            return layerBuffers;
        }

        public ReferenceOpenHashSet<BufferBuilder> getActiveConsumers() {
            return activeConsumers;
        }
    }

    private static class UnlitFacade implements MultiBufferSource {
        private final MultiBufferSource delegate;
        private final IdentityHashMap<VertexConsumer, VertexConsumer> cache = new IdentityHashMap<>();

        public UnlitFacade(MultiBufferSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public VertexConsumer getBuffer(RenderType layer) {
            return cache.computeIfAbsent(delegate.getBuffer(layer), Consumer::new);
        }

        private static final class Consumer implements VertexConsumer {
            private final VertexConsumer delegate;

            private Consumer(VertexConsumer delegate) {
                this.delegate = delegate;
            }

            @Override
            public VertexConsumer normal(float x, float y, float z) {
                delegate.normal(0, 1, 0); // this is the change
                return this;
            }

            // all other methods are direct delegation

            @Override
            public VertexConsumer vertex(double x, double y, double z) {
                delegate.vertex(x, y, z);
                return this;
            }

            @Override
            public void unsetDefaultColor() {
                delegate.unsetDefaultColor();
            }

            @Override
            public VertexConsumer uv(float u, float v) {
                delegate.uv(u, v);
                return this;
            }

            @Override
            public VertexConsumer overlayCoords(int u, int v) {
                delegate.overlayCoords(u, v);
                return this;
            }

            @Override
            public void endVertex() {
                delegate.endVertex();
            }

            @Override
            public VertexConsumer uv2(int u, int v) {
                delegate.uv2(u, v);
                return this;
            }

            @Override
            public void defaultColor(int r, int g, int b, int a) {
                delegate.defaultColor(r, g, b, a);
            }

            @Override
            public VertexConsumer color(int r, int g, int b, int a) {
                delegate.color(r, g, b, a);
                return this;
            }

        }
    }

}
