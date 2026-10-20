package dev.emi.emi.screen;

import dev.emi.emi.api.EmiScreenBoundsProvider;
import dev.emi.emi.api.widget.Bounds;
import appeng.mixins.emi.accessor.HandledScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.world.inventory.AbstractContainerMenu;
import java.util.List;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

public class EmiScreenBase {

    private static final Reference2ObjectOpenHashMap<Class<?>, List<EmiScreenBoundsProvider<?>>> PROVIDERS_BY_CLASS = new Reference2ObjectOpenHashMap<>();
    private static final ObjectArrayList<EmiScreenBoundsProvider<Screen>> GENERIC_PROVIDERS = new ObjectArrayList<>();

	private final Screen screen;
	private final Bounds bounds;

	private static final EmiScreenBase EMPTY = new EmiScreenBase(null, Bounds.EMPTY);

	private EmiScreenBase(Screen screen, Bounds bounds) {
		this.screen = screen;
		this.bounds = bounds;
	}

	public Screen screen() {
		return screen;
	}

	public Bounds bounds() {
		return bounds;
	}

	public boolean isEmpty() {
		return screen == null;
	}

	public static EmiScreenBase getCurrent() {
		Minecraft client = Minecraft.getInstance();
		return of(client.screen);
	}

	public static <T extends Screen> void addScreenBoundsProvider(Class<T> clazz, EmiScreenBoundsProvider<T> provider) {
        PROVIDERS_BY_CLASS.computeIfAbsent(clazz, k -> new ObjectArrayList<>(1)).add(provider);
	}

	public static void addGenericScreenBoundsProvider(EmiScreenBoundsProvider<Screen> provider) {
		GENERIC_PROVIDERS.add(provider);
	}

	public static void clearScreenBoundsProviders() {
		PROVIDERS_BY_CLASS.clear();
		GENERIC_PROVIDERS.clear();
	}

	public static EmiScreenBase of(Screen screen) {
		if (screen == null) {
			return EMPTY;
		}

		Class<?> screenClass = screen.getClass();
		List<EmiScreenBoundsProvider<?>> classProviders = PROVIDERS_BY_CLASS.get(screenClass);
		if (classProviders != null) {
			for (EmiScreenBoundsProvider<?> provider : classProviders) {
				@SuppressWarnings("unchecked")
				Bounds bounds = ((EmiScreenBoundsProvider<Screen>) provider).getBounds(screen);
				if (bounds != null && !bounds.empty()) {
					return new EmiScreenBase(screen, bounds);
				}
			}
		}
		for (EmiScreenBoundsProvider<Screen> provider : GENERIC_PROVIDERS) {
			Bounds bounds = provider.getBounds(screen);
			if (bounds != null && !bounds.empty()) {
				return new EmiScreenBase(screen, bounds);
			}
		}
		if (screen instanceof AbstractContainerScreen hs) {
			HandledScreenAccessor hsa = (HandledScreenAccessor) hs;
			AbstractContainerMenu sh = hs.getMenu();
			if (sh.slots != null && (!sh.slots.isEmpty()
                    || screen instanceof appeng.client.gui.me.crafting.CraftingCPUScreen<?>
                    || screen instanceof appeng.client.gui.me.crafting.CraftConfirmScreen)) {
				int extra = 0;
				if (hs instanceof RecipeUpdateListener provider) {
					if (provider.getRecipeBookComponent().isVisible()) {
						extra = 177;
					}
				}
                Bounds bounds = new Bounds(hsa.getX() - extra, hsa.getY(), hsa.getBackgroundWidth() + extra,
                        hsa.getBackgroundHeight());
				return new EmiScreenBase(screen, bounds);
			}
		} else if (screen instanceof RecipeScreen rs) {
			return new EmiScreenBase(rs, rs.getBounds());
		}
		return EMPTY;
	}
}
