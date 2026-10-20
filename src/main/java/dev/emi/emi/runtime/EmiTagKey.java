package dev.emi.emi.runtime;

import dev.emi.emi.EmiPort;
import dev.emi.emi.EmiUtil;
import dev.emi.emi.registry.EmiTags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnmodifiableView;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

// Wrapper around TagKeys
public class EmiTagKey<T> {
    private static final Reference2ObjectOpenHashMap<TagKey<?>, EmiTagKey<?>> KEYS = new Reference2ObjectOpenHashMap<>();
    public static final Map<TagKey<?>, EmiTagKey<?>> CACHE = KEYS;
    private final TagKey<T> raw;
    private Registry<T> registry;
    private ObjectArrayList<T> cached;
    private ReferenceOpenHashSet<T> cachedSet;
    private final String idString;

    private EmiTagKey(TagKey<T> raw) {
        this.raw = raw;
        this.idString = raw.location().toString();
        recalculate();
    }

    public void recalculate() {
        registry = Minecraft.getInstance().level.registryAccess().registry(raw.registry()).orElseThrow();
        var holders = registry.getTag(raw).orElse(null);
        cached = new ObjectArrayList<>(holders == null ? 0 : holders.size());
        if (holders != null) {
            boolean fluidRegistry = registry == EmiPort.getFluidRegistry();
            for (var holder : holders) {
                var value = holder.value();
                if (!fluidRegistry || ((Fluid) value).isSource(((Fluid) value).defaultFluidState()))
                    cached.add(value);
            }
        }
        cachedSet = new ReferenceOpenHashSet<>(cached.size());
        cachedSet.addAll(cached);
    }

    public String idString() {
        return idString;
    }

    public TagKey<T> raw() {
        return raw;
    }

    public boolean isOf(Registry<?> registry) {
        return raw.isFor(registry.key());
    }

    public ResourceLocation id() {
        return raw.location();
    }

    public @NotNull Registry<T> registry() {
        return registry;
    }

    public Stream<T> stream() {
        // Compatibility boundary for integrations; internal consumers use the cached collections.
        return cached.stream();
    }

    public @NotNull @UnmodifiableView List<@NotNull T> getList() {
        return cached;
    }

    public @NotNull @UnmodifiableView Set<@NotNull T> getSet() {
        return cachedSet;
    }

    public Component getTagName() {
        String s = getTagTranslationKey();
        if (s == null) {
            return EmiPort.literal("#" + this.id());
        } else {
            return EmiPort.translatable(s);
        }
    }

    public boolean hasTranslation() {
        return getTagTranslationKey() != null;
    }

    private @Nullable String getTagTranslationKey() {
        ResourceLocation registry = raw.registry().location();
        if (registry.getNamespace().equals("minecraft")) {
            String s = translatePrefix("tag." + registry.getPath().replace("/", ".") + ".", this.id());
            if (s != null) {
                return s;
            }
        } else {
            String s = translatePrefix(
                    "tag." + registry.getNamespace() + "." + registry.getPath().replace("/", ".") + ".", this.id());
            if (s != null) {
                return s;
            }
        }
        return translatePrefix("tag.", this.id());
    }

    private static @Nullable String translatePrefix(String prefix, ResourceLocation id) {
        String s = EmiUtil.translateId(prefix, id);
        if (I18n.exists(s)) {
            return s;
        }
        return null;
    }

    public @Nullable ResourceLocation getCustomModel() {
        return EmiTags.MODELED_TAGS.get(raw());
    }

    public boolean hasCustomModel() {
        return getCustomModel() != null;
    }

    @Override
    public int hashCode() {
        return raw().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof EmiTagKey other && raw().equals(other.raw());
    }

    @SuppressWarnings("unchecked")
    public static <T> EmiTagKey<T> of(TagKey<T> raw) {
        return (EmiTagKey<T>) KEYS.computeIfAbsent(raw, (TagKey<?> key) -> new EmiTagKey<>(key));
    }

    public static <T> EmiTagKey<T> of(Registry<T> registry, ResourceLocation id) {
        return of(TagKey.create(registry.key(), id));
    }

    public static <T> Stream<EmiTagKey<T>> fromRegistry(Registry<T> registry) {
        return registry.getTagNames().map(EmiTagKey::of);
    }

    public static void reload() {
        for (EmiTagKey<?> key : KEYS.values()) {
            key.recalculate();
        }
    }
}
