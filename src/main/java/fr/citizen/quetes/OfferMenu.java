package fr.citizen.quetes;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

/**
 * Menu "Accepter / Refuser" : un simple coffre vanilla de 3 lignes, donc affiché aussi sur console
 * (Geyser traduit les coffres). Rien ne peut être pris : chaque clic est intercepté.
 */
public final class OfferMenu extends ChestMenu {
    public static final int SLOT_ACCEPT = 11;
    public static final int SLOT_INFO = 13;
    public static final int SLOT_REFUSE = 15;

    public interface Choice {
        void accept();
        void refuse();
        void closed();
    }

    private final Choice choice;
    private boolean decided;

    public OfferMenu(int id, Inventory inv, Messenger.Offer offer, Choice choice) {
        super(MenuType.GENERIC_9x3, id, inv, build(offer), 3);
        this.choice = choice;
    }

    private static SimpleContainer build(Messenger.Offer o) {
        SimpleContainer c = new SimpleContainer(27);
        ItemStack filler = named(Items.GRAY_STAINED_GLASS_PANE, Component.literal(" "), List.of());
        for (int i = 0; i < 27; i++) c.setItem(i, filler.copy());

        List<Component> info = new ArrayList<>();
        for (String line : o.dialogue()) {
            for (String part : wrap(line, 38)) info.add(text(part, ChatFormatting.GRAY, true));
        }
        info.add(Component.empty());
        info.add(text("Objectif : " + o.objective(), ChatFormatting.YELLOW, false));
        info.add(text("Récompense : " + o.reward() + " PO (virement)", ChatFormatting.GOLD, false));
        info.add(text("Temps : " + o.minutes() / 60 + "h" + String.format("%02d", o.minutes() % 60), ChatFormatting.GRAY, false));
        c.setItem(SLOT_INFO, named(Items.WRITABLE_BOOK, text(o.title(), ChatFormatting.GOLD, false).copy().withStyle(ChatFormatting.BOLD), info));

        c.setItem(SLOT_ACCEPT, named(Items.LIME_CONCRETE, text("✔ Accepter la quête", ChatFormatting.GREEN, false).copy().withStyle(ChatFormatting.BOLD),
                List.of(text("Le messager repart au château.", ChatFormatting.GRAY, false),
                        text("La quête s'affiche à droite de l'écran.", ChatFormatting.GRAY, false))));
        c.setItem(SLOT_REFUSE, named(Items.RED_CONCRETE, text("✘ Refuser", ChatFormatting.RED, false).copy().withStyle(ChatFormatting.BOLD),
                List.of(text("Le messager s'en va.", ChatFormatting.GRAY, false))));
        return c;
    }

    private static Component text(String s, ChatFormatting color, boolean italic) {
        return Component.literal(s).withStyle(Style.EMPTY.withColor(color).withItalic(italic));
    }

    private static ItemStack named(Item item, Component name, List<Component> lore) {
        ItemStack s = new ItemStack(item);
        s.set(DataComponents.CUSTOM_NAME, name.copy().withStyle(st -> st.withItalic(false)));
        if (!lore.isEmpty()) s.set(DataComponents.LORE, new ItemLore(lore));
        return s;
    }

    static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() > 0 && line.length() + word.length() + 1 > width) {
                out.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (!decided && slotId == SLOT_ACCEPT) {
            decided = true;
            choice.accept();
        } else if (!decided && slotId == SLOT_REFUSE) {
            decided = true;
            choice.refuse();
        } else {
            // Annule l'éventuelle prédiction du client (objet "pris" dans le curseur).
            this.sendAllDataToRemote();
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override
    public boolean stillValid(Player player) { return true; }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!decided) choice.closed();
    }
}
