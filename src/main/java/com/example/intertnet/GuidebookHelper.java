package com.example.intertnet;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.List;

public class GuidebookHelper {

    public static ItemStack createSignedGuidebook() {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);

        // Vanilla written books fit max 14 lines (~114px wide) per page.
        // Every page below is carefully kept under 11 wrapped lines so nothing ever cuts off!
        List<Filterable<Component>> pages = List.of(
                page(Component.literal("INTERTNET MANUAL\n")
                        .withStyle(ChatFormatting.DARK_BLUE, ChatFormatting.BOLD)
                        .append(Component.literal("By The Internet Maker\n\n").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC))
                        .append(Component.literal("Normal server chat is disabled!\n\nFlip pages below to learn about Phones, SIMs, Towers & Satellites.")
                                .withStyle(ChatFormatting.BLACK))),

                page(Component.literal("1. Getting a Phone\n")
                        .withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD)
                        .append(Component.literal("\nLook for a fenced Open World tower in plains, meadows, or savannas.\n\nIts stall sells phones for 2 emeralds and Open World SIMs for 5.").withStyle(ChatFormatting.BLACK))),

                page(Component.literal("2. Using Your Phone\n")
                        .withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD)
                        .append(Component.literal("\nRight-click while holding a Phone to open Intertnet OS.\n\nWhen a Phone is anywhere in your inventory, live Signal Bars appear in the top-right corner!").withStyle(ChatFormatting.BLACK))),

                page(Component.literal("3. Cell Towers\n")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                        .append(Component.literal("\nPhones need a fueled Cell Tower in range.\n\n• ").withStyle(ChatFormatting.BLACK))
                        .append(Component.literal("Craft: ").withStyle(ChatFormatting.BOLD))
                        .append(Component.literal("Cobblestone, Iron Ingots, Iron Bars & Redstone Torch.\n\nRight-click a placed Tower to add fuel.").withStyle(ChatFormatting.BLACK))),

                page(Component.literal("4. SIM Cards!\n")
                        .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD)
                        .append(Component.literal("\nOne Open World SIM works at every public tower.\n\nFor your own network, use [Print SIM] at your tower with 1 paper + 1 gold nugget.\n\nUse [Insert SIM] on your phone.").withStyle(ChatFormatting.BLACK))),

                page(Component.literal("5. Satellite Unit\n")
                        .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD)
                        .append(Component.literal("\nCraft a ").withStyle(ChatFormatting.BLACK))
                        .append(Component.literal("Satellite Connector").withStyle(ChatFormatting.BOLD))
                        .append(Component.literal(" (4 Copper + 1 Redstone + 1 Cobblestone) for 64m reception.\n• Runs on copper\n• 1 free SIM\n• Texts, Delivery, Rideshare").withStyle(ChatFormatting.BLACK))),

                page(Component.literal("6. Fuel & Range\n")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                        .append(Component.literal("\nTower fuel = bigger radius:\n• Copper/Redstone: 64m\n• Gold/Amethyst: 96m\n• Emerald: 128m\n• Emerald Blk: 160m\n• Diamond: 192m\n• Diamond Blk: 224m\n• Netherite/Star: 256m").withStyle(ChatFormatting.BLACK))),

                page(Component.literal("7. Line of Sight\n")
                        .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD)
                        .append(Component.literal("\nSignal travels in a straight line from the Tower to you.\n\nEach solid block in the way cuts 4m of range. Press F3 in Creative to see 3D signal rings!").withStyle(ChatFormatting.BLACK))),

                page(Component.literal("8. Wireless Linking\n")
                        .withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD)
                        .append(Component.literal("\nNo wires needed!\n\n1. Open a Tower UI and click ").withStyle(ChatFormatting.BLACK))
                        .append(Component.literal("[Net]").withStyle(ChatFormatting.DARK_BLUE, ChatFormatting.BOLD))
                        .append(Component.literal(".\n2. Click any tower in radius to link them.\n3. One SIM works across the whole linked chain!").withStyle(ChatFormatting.BLACK))),

                page(Component.literal("9. Global Messages\n")
                        .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD)
                        .append(Component.literal("\nOpen the ").withStyle(ChatFormatting.BLACK))
                        .append(Component.literal("[Messages]").withStyle(ChatFormatting.BOLD))
                        .append(Component.literal(" tab on your Phone to text anyone in the world who has signal on ANY carrier or Satellite!").withStyle(ChatFormatting.BLACK))),

                page(Component.literal("10. Help Call (1/2)\n")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                        .append(Component.literal("\nConnected to a full Cell Tower?\nOpen ").withStyle(ChatFormatting.BLACK))
                        .append(Component.literal("[Help Call]").withStyle(ChatFormatting.BOLD))
                        .append(Component.literal(" and click ").withStyle(ChatFormatting.BLACK))
                        .append(Component.literal("CALL FOR HELP").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
                        .append(Component.literal(".\n\nA Villager in a 2-Minecart train drives out to you!").withStyle(ChatFormatting.BLACK))),

                page(Component.literal("11. Help Call (2/2)\n")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                        .append(Component.literal("\nWhen the 2-Minecart train arrives:\n\nRight-click the ").withStyle(ChatFormatting.BLACK))
                        .append(Component.literal("back Minecart").withStyle(ChatFormatting.BOLD))
                        .append(Component.literal(" to hop in!\n\nThe Villager will drive you off-road back to the nearest Village!").withStyle(ChatFormatting.BLACK))),

                page(Component.literal("12. Restaurants\n")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                        .append(Component.literal("\nTalk to the chef and pay in emeralds. Stay in the dining room while your meat cooks.\n\nWith reception, open Delivery to order a meal to your pickup spot.").withStyle(ChatFormatting.BLACK))),

                page(Component.literal("13. Rideshare\n")
                        .withStyle(ChatFormatting.DARK_BLUE, ChatFormatting.BOLD)
                        .append(Component.literal("\nChoose a destination in the Rideshare app. Fares go up with distance.\n\nWait at your pickup spot and right-click the rear cart to board.").withStyle(ChatFormatting.BLACK)))
        );

        WrittenBookContent content = new WrittenBookContent(
                Filterable.passThrough("Intertnet Manual"),
                "The Internet Maker",
                0,
                pages,
                true
        );
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, content);
        return book;
    }

    private static Filterable<Component> page(Component component) {
        return Filterable.passThrough(component);
    }
}
