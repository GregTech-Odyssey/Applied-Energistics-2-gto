package appeng.items.misc;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import appeng.items.AEBaseItem;

import com.gto.ae.core.localization.ExtendedLangs;

public class MeteoriteCompassItem extends AEBaseItem {
    public MeteoriteCompassItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(ExtendedLangs.MeteoritesDisabled.text());
    }
}
