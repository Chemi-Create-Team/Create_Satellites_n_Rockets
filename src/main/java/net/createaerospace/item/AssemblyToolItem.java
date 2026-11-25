package net.createaerospace.item;

import java.util.List;
import net.createaerospace.assembly.ToolType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** An end effector for the industrial arm; use it on the arm to fit it. */
public class AssemblyToolItem extends Item {
    public final ToolType type;

    public AssemblyToolItem(ToolType type, Properties properties) {
        super(properties.stacksTo(1));
        this.type = type;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.create_aerospace_engineering." + type.id() + ".tooltip").withStyle(ChatFormatting.GRAY));
    }
}
