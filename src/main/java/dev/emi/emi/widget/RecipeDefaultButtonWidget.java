package dev.emi.emi.widget;

import com.google.common.collect.Lists;
import dev.emi.emi.EmiPort;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.bom.BoM;
import dev.emi.emi.bom.BoM.DefaultStatus;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.config.HelpLevel;
import dev.emi.emi.planner.PlannerText;
import dev.emi.emi.runtime.EmiHistory;
import dev.emi.emi.screen.RecipeDefaultOutputScreen;
import dev.emi.emi.screen.RecipeScreen;
import dev.emi.emi.screen.tooltip.IngredientTooltipComponent;
import dev.emi.emi.widget.RecipeButtonWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

import java.util.List;

public class RecipeDefaultButtonWidget extends RecipeButtonWidget {

    public RecipeDefaultButtonWidget(int x, int y, EmiRecipe recipe) {
        super(x, y, 48, 0, recipe);
    }

    @Override
    public int getTextureOffset(int mouseX, int mouseY) {
        int v = super.getTextureOffset(mouseX, mouseY);
        v += switch (BoM.getRecipeStatus(recipe)) {
            case EMPTY -> 0;
            case PARTIAL -> 60;
            case FULL -> 36;
        };
        return v;
    }

    @Override
    public List<ClientTooltipComponent> getTooltip(int mouseX, int mouseY) {
        List<ClientTooltipComponent> list = Lists.newArrayList();
        long outputCount = recipe.getOutputs().stream().filter(stack -> !stack.isEmpty()).count();
        if (outputCount > 1) {
            list.add(ClientTooltipComponent.create(EmiPort.ordered(EmiPort.literal(PlannerText.tr("recipe_default.choose_outputs", "Choose default recipe outputs")))));
            List<EmiStack> stacks = Lists.newArrayList();
            for (EmiStack stack : recipe.getOutputs()) {
                if (!stack.isEmpty() && BoM.getRecipe(stack) == recipe) {
                    stacks.add(stack);
                }
            }
            if (!stacks.isEmpty()) {
                list.add(ClientTooltipComponent.create(EmiPort.ordered(EmiPort.translatable("tooltip.emi.current_defaults"))));
                list.add(new IngredientTooltipComponent(stacks));
            }
            if (EmiConfig.helpLevel.has(HelpLevel.NORMAL) && EmiConfig.defaultStack.isBound()) {
                list.add(ClientTooltipComponent.create(EmiPort.ordered(EmiPort.translatable("tooltip.emi.set_default_stack", EmiConfig.defaultStack.getBindText()))));
            }
            return list;
        }
        switch(BoM.getRecipeStatus(recipe)) {
            case PARTIAL:
                List<EmiStack> stacks = Lists.newArrayList();
                for (EmiStack stack : recipe.getOutputs()) {
                    if (BoM.getRecipe(stack) == recipe) {
                        stacks.add(stack);
                    }
                }
                if (stacks.size() > 0) {
                    list.add(ClientTooltipComponent.create(EmiPort.ordered(EmiPort.translatable("tooltip.emi.set_default"))));
                    list.add(ClientTooltipComponent.create(EmiPort.ordered(EmiPort.translatable("tooltip.emi.current_defaults"))));
                    list.add(new IngredientTooltipComponent(stacks));
                    break;
                }
            case EMPTY:
                list.add(ClientTooltipComponent.create(EmiPort.ordered(EmiPort.translatable("tooltip.emi.set_default"))));
                break;
            case FULL:
                list.add(ClientTooltipComponent.create(EmiPort.ordered(EmiPort.translatable("tooltip.emi.unset_default"))));
                break;
        }
        return list;
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        long outputCount = recipe.getOutputs().stream().filter(stack -> !stack.isEmpty()).count();
        if (outputCount > 1) {
            Minecraft client = Minecraft.getInstance();
            Screen parent = client.screen;
            client.setScreen(new RecipeDefaultOutputScreen(parent, recipe));
            this.playButtonSound();
            return true;
        }
        if (BoM.getRecipeStatus(recipe) == DefaultStatus.FULL) {
            BoM.removeRecipe(recipe);
        } else {
            BoM.addRecipe(recipe);
        }
        this.playButtonSound();
        if (RecipeScreen.resolve != null) {
            EmiHistory.pop();
        }
        return true;
    }
}
