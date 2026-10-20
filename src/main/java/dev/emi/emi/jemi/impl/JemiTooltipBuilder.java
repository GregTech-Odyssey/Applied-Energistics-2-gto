package dev.emi.emi.jemi.impl;

import com.google.common.collect.Lists;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

import java.util.Collection;
import java.util.List;

public class JemiTooltipBuilder implements ITooltipBuilder {
	public final List<ClientTooltipComponent> tooltip = Lists.newArrayList();
	private final List<Component> legacyText = Lists.newArrayList();

	@Override
	public void add(FormattedText component) {
		// JEI allows non-text StringVisitable... Minecraft's methods don't easily
		if (component instanceof Component text) {
			tooltip.add(ClientTooltipComponent.create(text.getVisualOrderText()));
			legacyText.add(text);
		}
	}

	@Override
	public void addAll(Collection<? extends FormattedText> components) {
		for (FormattedText v : components) {
			add(v);
		}
	}

	@Override
	public void add(TooltipComponent data) {
		try {
			tooltip.add(ClientTooltipComponent.create(data));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void setIngredient(ITypedIngredient<?> typedIngredient) {
		// EMI's methods bypass the vanilla tooltip render which accepts a stack, so this will do nothing
	}

	@Override
	public List<Component> toLegacyToComponents() {
		return legacyText;
	}

	@Override
	public void removeAll(List<Component> components) {
		// EMI does not support tooltip removeal
	}
}
