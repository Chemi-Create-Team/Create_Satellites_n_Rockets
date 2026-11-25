package net.createsatellites.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import java.util.List;

/** A satellite ready to fly. It is loaded onto a launch pad by using it on the pad, which sets up a rocket there. */
public class SatelliteItem extends Item {
    public SatelliteItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.create_satellites_n_rockets.satellite.tooltip").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
