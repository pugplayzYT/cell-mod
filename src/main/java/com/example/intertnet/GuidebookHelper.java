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
                        .append(Component.literal("\nPick up iron bars to learn the Cell Tower recipe.\n\nRight-click your tower to add fuel. Put an antenna in the lower slot for more range. Public towers have a high tier antenna built in.").withStyle(ChatFormatting.BLACK))),

                page(Component.literal("4. SIM Cards!\n")
                        .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD)
                        .append(Component.literal("\nOne Open World SIM works at every public tower.\n\nFor your own network, use [Print SIM] at your tower with 1 paper + 1 gold nugget.\n\nUse [Insert SIM] on your phone.").withStyle(ChatFormatting.BLACK))),

                page(Component.literal("5. Satellite Unit\n")
                        .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD)
                        .append(Component.literal("\nCraft a ").withStyle(ChatFormatting.BLACK))
                        .append(Component.literal("Satellite Connector").withStyle(ChatFormatting.BOLD))
                        .append(Component.literal(" (4 Copper + 1 Redstone + 1 Cobblestone) for 64m reception.\n• Runs on copper\n• 1 free SIM\n• Supports all phone apps").withStyle(ChatFormatting.BLACK))),

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

                page(Component.literal("10. Call Medics\n")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                        .append(Component.literal("\nOpen SOS with cell or satellite reception. Calling is free.\n\nBoard the ambulance's rear cart. At the clinic, medics seat you and use healing arrows. The doors open when treatment ends.").withStyle(ChatFormatting.BLACK))),

                page(Component.literal("11. Call Police\n")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                        .append(Component.literal("\nCall Police for free in SOS. Four officers and two ravagers drive out.\n\nThey clear up to ten hostile mobs near your pickup and then leave.").withStyle(ChatFormatting.BLACK))),

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
