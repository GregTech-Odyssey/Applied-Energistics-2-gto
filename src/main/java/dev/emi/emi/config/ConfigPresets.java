package dev.emi.emi.config;

import com.mojang.blaze3d.platform.InputConstants;
import dev.emi.emi.com.unascribed.qdcss.QDCSS;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.config.EmiConfig.Comment;
import dev.emi.emi.config.EmiConfig.ConfigGroup;
import dev.emi.emi.config.EmiConfig.ConfigValue;
import dev.emi.emi.config.HeaderType;
import dev.emi.emi.config.ScreenAlign;
import dev.emi.emi.config.SidebarPages;
import dev.emi.emi.config.SidebarTheme;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.input.EmiBind;
import dev.emi.emi.input.EmiInput;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class ConfigPresets {

	// Sidebars

	@ConfigGroup("presets.sidebars")
	@Comment("Index on the right, a smaller set of craftables on the left, and favorites in the top left."
		+ " Ideal for getting the most out of EMI's features")
	@ConfigValue("presets.productive")
	public static Runnable productive = () -> {
		setPages(dev.emi.emi.config.EmiConfig.rightSidebarPages, List.of(
			new dev.emi.emi.config.SidebarPages.SidebarPage(SidebarType.INDEX)
		));

		setPages(dev.emi.emi.config.EmiConfig.leftSidebarPages, List.of(
			new dev.emi.emi.config.SidebarPages.SidebarPage(SidebarType.CRAFTABLES)
		));

		setPages(dev.emi.emi.config.EmiConfig.topSidebarPages, List.of(
			new dev.emi.emi.config.SidebarPages.SidebarPage(SidebarType.FAVORITES)
		));

		setPages(dev.emi.emi.config.EmiConfig.bottomSidebarPages, List.of());

		dev.emi.emi.config.EmiConfig.leftSidebarTheme = SidebarTheme.MODERN;
		dev.emi.emi.config.EmiConfig.leftSidebarHeader = dev.emi.emi.config.HeaderType.VISIBLE;

		Minecraft client = Minecraft.getInstance();
		if (client.getWindow().getGuiScaledHeight() < 260) {
			dev.emi.emi.config.EmiConfig.leftSidebarSize.values.set(0, 10);
			dev.emi.emi.config.EmiConfig.leftSidebarSize.values.set(1, 8);
		} else {
			dev.emi.emi.config.EmiConfig.leftSidebarSize.values.set(0, 10);
			dev.emi.emi.config.EmiConfig.leftSidebarSize.values.set(1, 10);
		}

		dev.emi.emi.config.EmiConfig.leftSidebarAlign = new dev.emi.emi.config.ScreenAlign(
			dev.emi.emi.config.ScreenAlign.Horizontal.RIGHT,
			dev.emi.emi.config.ScreenAlign.Vertical.CENTER
		);

		dev.emi.emi.config.EmiConfig.rightSidebarTheme = SidebarTheme.MODERN;
		dev.emi.emi.config.EmiConfig.rightSidebarHeader = dev.emi.emi.config.HeaderType.VISIBLE;
		
		dev.emi.emi.config.EmiConfig.rightSidebarSize.values.set(0, 12);
		dev.emi.emi.config.EmiConfig.rightSidebarSize.values.set(1, 100);

		dev.emi.emi.config.EmiConfig.rightSidebarAlign = new dev.emi.emi.config.ScreenAlign(
			dev.emi.emi.config.ScreenAlign.Horizontal.RIGHT,
			dev.emi.emi.config.ScreenAlign.Vertical.TOP
		);

		dev.emi.emi.config.EmiConfig.topSidebarTheme = SidebarTheme.TRANSPARENT;
		dev.emi.emi.config.EmiConfig.topSidebarHeader = dev.emi.emi.config.HeaderType.INVISIBLE;
		
		dev.emi.emi.config.EmiConfig.topSidebarSize.values.set(0, 12);
		dev.emi.emi.config.EmiConfig.topSidebarSize.values.set(1, 4);

		dev.emi.emi.config.EmiConfig.topSidebarAlign = new dev.emi.emi.config.ScreenAlign(
			dev.emi.emi.config.ScreenAlign.Horizontal.LEFT,
			dev.emi.emi.config.ScreenAlign.Vertical.TOP
		);
	};

	@Comment("Use a smaller, recipe book styled panel on the left for craftables and favorites,"
		+ " and use a classic index on the right.")
	@ConfigValue("presets.recipe-book-plus")
	public static Runnable recipeBookPlus = () -> {
		setPages(dev.emi.emi.config.EmiConfig.rightSidebarPages, List.of(
			new dev.emi.emi.config.SidebarPages.SidebarPage(SidebarType.INDEX)
		));

		setPages(dev.emi.emi.config.EmiConfig.leftSidebarPages, List.of(
			new dev.emi.emi.config.SidebarPages.SidebarPage(SidebarType.CRAFTABLES),
			new dev.emi.emi.config.SidebarPages.SidebarPage(SidebarType.FAVORITES)
		));

		setPages(dev.emi.emi.config.EmiConfig.topSidebarPages, List.of());

		setPages(dev.emi.emi.config.EmiConfig.bottomSidebarPages, List.of());

		dev.emi.emi.config.EmiConfig.leftSidebarTheme = SidebarTheme.VANILLA;
		dev.emi.emi.config.EmiConfig.leftSidebarHeader = dev.emi.emi.config.HeaderType.VISIBLE;
		
		dev.emi.emi.config.EmiConfig.leftSidebarSize.values.set(0, 8);
		dev.emi.emi.config.EmiConfig.leftSidebarSize.values.set(1, 7);

		dev.emi.emi.config.EmiConfig.leftSidebarAlign = new dev.emi.emi.config.ScreenAlign(
			dev.emi.emi.config.ScreenAlign.Horizontal.RIGHT,
			dev.emi.emi.config.ScreenAlign.Vertical.CENTER
		);

		dev.emi.emi.config.EmiConfig.rightSidebarTheme = SidebarTheme.MODERN;
		dev.emi.emi.config.EmiConfig.rightSidebarHeader = HeaderType.VISIBLE;
		
		dev.emi.emi.config.EmiConfig.rightSidebarSize.values.set(0, 12);
		dev.emi.emi.config.EmiConfig.rightSidebarSize.values.set(1, 100);

		dev.emi.emi.config.EmiConfig.rightSidebarAlign = new dev.emi.emi.config.ScreenAlign(
			dev.emi.emi.config.ScreenAlign.Horizontal.RIGHT,
			ScreenAlign.Vertical.TOP
		);
	};

	@Comment("Use the right sidebar as the index, but only while searching")
	@ConfigValue("presets.low-distraction")
	public static Runnable lowDistraction = () -> {
		dev.emi.emi.config.EmiConfig.searchSidebarFocus = SidebarType.INDEX;
		dev.emi.emi.config.EmiConfig.emptySearchSidebarFocus = SidebarType.EMPTY;
		
		setPages(dev.emi.emi.config.EmiConfig.rightSidebarPages, List.of(
			new dev.emi.emi.config.SidebarPages.SidebarPage(SidebarType.INDEX),
			new dev.emi.emi.config.SidebarPages.SidebarPage(SidebarType.EMPTY)
		));
	};

	@Comment("Use index and craftables on the right panel, craftable by default, and index when searching")
	@ConfigValue("presets.empty-search-craftable")
	public static Runnable emptySearchCraftable = () -> {
		dev.emi.emi.config.EmiConfig.searchSidebarFocus = SidebarType.INDEX;
		dev.emi.emi.config.EmiConfig.emptySearchSidebarFocus = SidebarType.CRAFTABLES;
		
		setPages(dev.emi.emi.config.EmiConfig.rightSidebarPages, List.of(
			new dev.emi.emi.config.SidebarPages.SidebarPage(SidebarType.INDEX),
			new dev.emi.emi.config.SidebarPages.SidebarPage(SidebarType.CRAFTABLES)
		));
		
		setPages(dev.emi.emi.config.EmiConfig.leftSidebarPages, List.of(
			new dev.emi.emi.config.SidebarPages.SidebarPage(SidebarType.FAVORITES)
		));

		setPages(dev.emi.emi.config.EmiConfig.topSidebarPages, List.of());

		setPages(dev.emi.emi.config.EmiConfig.bottomSidebarPages, List.of());
	};

	// Binds

	@ConfigGroup("presets.binds")
	@Comment("Use some of the binds EMI's author uses, do they know best?")
	@ConfigValue("presets.author-binds")
	public static Runnable authorBinds = () -> {
		dev.emi.emi.config.EmiConfig.toggleVisibility.setBinds(
			EmiBind.ModifiedKey.of(GLFW.GLFW_KEY_O, EmiInput.CONTROL_MASK)
		);
		dev.emi.emi.config.EmiConfig.focusSearch.setBinds(
			EmiBind.ModifiedKey.of(GLFW.GLFW_KEY_F, EmiInput.CONTROL_MASK)
		);
		dev.emi.emi.config.EmiConfig.clearSearch.setBinds(
			EmiBind.ModifiedKey.of(GLFW.GLFW_KEY_D, EmiInput.CONTROL_MASK)
		);
		dev.emi.emi.config.EmiConfig.viewRecipes.setToDefault();
		dev.emi.emi.config.EmiConfig.viewUses.setToDefault();
		dev.emi.emi.config.EmiConfig.favorite.setToDefault();
		dev.emi.emi.config.EmiConfig.viewStackTree.setToDefault();
		dev.emi.emi.config.EmiConfig.viewTree.setBinds(
			EmiBind.ModifiedKey.of(GLFW.GLFW_KEY_C, 0)
		);
		dev.emi.emi.config.EmiConfig.back.setToDefault();
		dev.emi.emi.config.EmiConfig.craftOne.setToDefault();
		dev.emi.emi.config.EmiConfig.craftAll.setBinds();
		dev.emi.emi.config.EmiConfig.craftOneToInventory.setBinds(
			new EmiBind.ModifiedKey(InputConstants.Type.MOUSE.getOrCreate(1), EmiInput.SHIFT_MASK)
		);
		dev.emi.emi.config.EmiConfig.craftAllToInventory.setBinds(
			new EmiBind.ModifiedKey(InputConstants.Type.MOUSE.getOrCreate(0), EmiInput.SHIFT_MASK)
		);
		dev.emi.emi.config.EmiConfig.showCraft.setToDefault();
		dev.emi.emi.config.EmiConfig.cheatOneToInventory.setToDefault();
		dev.emi.emi.config.EmiConfig.cheatStackToInventory.setToDefault();
		dev.emi.emi.config.EmiConfig.cheatOneToCursor.setToDefault();
		dev.emi.emi.config.EmiConfig.cheatStackToCursor.setToDefault();
	};

	// Resets

	@ConfigGroup("presets.restore")
	@Comment("Restore all settings to backup from most recently launch")
	@ConfigValue("presets.backup")
	public static Runnable backup = () -> {
		dev.emi.emi.config.EmiConfig.loadConfig(QDCSS.load("backup", dev.emi.emi.config.EmiConfig.startupConfig));
	};

	@Comment("Restore all default settings")
	@ConfigValue("presets.defaults")
	public static Runnable defaults = () -> {
		dev.emi.emi.config.EmiConfig.loadConfig(QDCSS.load("defaults", EmiConfig.DEFAULT_CONFIG));
	};

	private static void setPages(dev.emi.emi.config.SidebarPages pages, List<SidebarPages.SidebarPage> list) {
		pages.pages.clear();
		pages.pages.addAll(list);
	}
}
