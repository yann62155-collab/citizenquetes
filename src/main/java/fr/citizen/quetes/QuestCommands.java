package fr.citizen.quetes;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Locale;

/**
 * /quete            : voir sa quête (tout le monde)
 * /quete abandonner : abandonner sa quête
 * /quetes ...       : administration (OP niveau 2)
 */
public final class QuestCommands {
    private QuestCommands() { }

    static void register(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();

        d.register(Commands.literal("quete")
                .executes(QuestCommands::show)
                .then(Commands.literal("abandonner").executes(QuestCommands::abandon)));

        d.register(Commands.literal("quetes")
                .requires(s -> s.hasPermission(2))
                .executes(QuestCommands::help)
                .then(Commands.literal("chateau").executes(QuestCommands::setCastle))
                .then(Commands.literal("lieu")
                        .then(Commands.literal("ajouter").then(Commands.argument("nom", StringArgumentType.greedyString()).executes(QuestCommands::addPlace)))
                        .then(Commands.literal("retirer").then(Commands.argument("nom", StringArgumentType.greedyString())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        QuestData.get(c.getSource().getServer()).places.values().stream().map(QuestData.Place::name), b))
                                .executes(QuestCommands::removePlace)))
                        .then(Commands.literal("liste").executes(QuestCommands::listPlaces)))
                .then(Commands.literal("envoyer")
                        .executes(c -> send(c, c.getSource().getPlayerOrException()))
                        .then(Commands.argument("joueur", EntityArgument.player()).executes(c -> send(c, EntityArgument.getPlayer(c, "joueur")))))
                .then(Commands.literal("prochain").executes(QuestCommands::next))
                .then(Commands.literal("annuler").then(Commands.argument("joueur", EntityArgument.player()).executes(QuestCommands::cancel)))
                .then(Commands.literal("recharger").executes(QuestCommands::reload)));
    }

    private static void ok(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendSuccess(() -> Component.literal(msg).withStyle(ChatFormatting.GOLD), true);
    }

    private static int help(CommandContext<CommandSourceStack> c) {
        c.getSource().sendSuccess(() -> Component.literal("""
                Citizen Quêtes — commandes admin :
                /quetes chateau : le château = ta position actuelle
                /quetes lieu ajouter <nom> : lieu de ronde à ta position
                /quetes lieu retirer <nom> | /quetes lieu liste
                /quetes envoyer [joueur] : envoyer un messager tout de suite
                /quetes prochain : heure du prochain messager
                /quetes annuler <joueur> : annuler la quête d'un joueur
                /quetes recharger : relire config/citizenquetes/*.json""").withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int setCastle(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        QuestData data = QuestData.get(c.getSource().getServer());
        data.castle = new QuestData.Place("Château", p.level().dimension().location().toString(), p.getBlockX(), p.getBlockY(), p.getBlockZ());
        data.setDirty();
        ok(c, "Château enregistré en " + p.getBlockX() + " " + p.getBlockY() + " " + p.getBlockZ() + ". Les messagers y retourneront.");
        return 1;
    }

    private static int addPlace(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(c, "nom").trim();
        QuestData data = QuestData.get(c.getSource().getServer());
        data.places.put(name.toLowerCase(Locale.ROOT), new QuestData.Place(name, p.level().dimension().location().toString(),
                p.getBlockX(), p.getBlockY(), p.getBlockZ()));
        data.setDirty();
        ok(c, "Lieu de ronde « " + name + " » enregistré ici.");
        return 1;
    }

    private static int removePlace(CommandContext<CommandSourceStack> c) {
        String name = StringArgumentType.getString(c, "nom").trim();
        QuestData data = QuestData.get(c.getSource().getServer());
        if (data.places.remove(name.toLowerCase(Locale.ROOT)) == null) {
            c.getSource().sendFailure(Component.literal("Lieu inconnu : " + name));
            return 0;
        }
        data.setDirty();
        ok(c, "Lieu « " + name + " » retiré.");
        return 1;
    }

    private static int listPlaces(CommandContext<CommandSourceStack> c) {
        QuestData data = QuestData.get(c.getSource().getServer());
        StringBuilder sb = new StringBuilder("Château : ");
        sb.append(data.castle == null ? "non défini (/quetes chateau)" : data.castle.x() + " " + data.castle.y() + " " + data.castle.z());
        sb.append("\nLieux de ronde (").append(data.places.size()).append(") :");
        for (QuestData.Place p : data.places.values())
            sb.append("\n - ").append(p.name()).append(" : ").append(p.x()).append(' ').append(p.y()).append(' ').append(p.z()).append(" (").append(p.dim()).append(')');
        String msg = sb.toString();
        c.getSource().sendSuccess(() -> Component.literal(msg).withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int send(CommandContext<CommandSourceStack> c, ServerPlayer target) {
        if (QuestData.get(c.getSource().getServer()).quests.containsKey(target.getUUID())) {
            c.getSource().sendFailure(Component.literal(target.getGameProfile().getName() + " a déjà une quête en cours."));
            return 0;
        }
        String err = Messenger.spawnFor(target);
        if (err != null) {
            c.getSource().sendFailure(Component.literal("Impossible : " + err));
            return 0;
        }
        ok(c, "Messager envoyé vers " + target.getGameProfile().getName() + ".");
        return 1;
    }

    private static int next(CommandContext<CommandSourceStack> c) {
        QuestData data = QuestData.get(c.getSource().getServer());
        long min = Math.max(0, data.nextMessenger - System.currentTimeMillis()) / 60_000L;
        ok(c, "Prochain messager dans environ " + min + " min (toutes les " + QuestConfig.settings().intervalleMinutes + " min). "
                + "Messager(s) en route : " + Messenger.count() + ".");
        return 1;
    }

    private static int cancel(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(c, "joueur");
        QuestData data = QuestData.get(c.getSource().getServer());
        if (data.quests.remove(p.getUUID()) == null) {
            c.getSource().sendFailure(Component.literal(p.getGameProfile().getName() + " n'a pas de quête."));
            return 0;
        }
        data.setDirty();
        SidebarHud.clear(p);
        ok(c, "Quête de " + p.getGameProfile().getName() + " annulée.");
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> c) {
        String report = QuestConfig.load();
        ok(c, "Configuration rechargée. " + report);
        return 1;
    }

    // ------------------------------------------------------------------ joueurs

    private static int show(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        QuestData.ActiveQuest q = QuestData.get(c.getSource().getServer()).quests.get(p.getUUID());
        if (q == null) {
            c.getSource().sendSuccess(() -> Component.literal("Tu n'as pas de quête en cours. Un messager du château viendra peut-être te trouver…")
                    .withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        String objective = switch (q.type) {
            case "objets" -> "Rapporter " + q.amount + " " + q.targetName + " (les objets sont pris automatiquement dans ton inventaire)";
            case "tuer" -> "Éliminer " + q.amount + " " + q.targetName + " (" + q.progress + "/" + q.amount + ")";
            default -> "Aller jusqu'à " + q.targetName + (q.place != null ? " (X " + q.place.x() + ", Z " + q.place.z() + ")" : "");
        };
        long left = Math.max(0, q.endsAt - System.currentTimeMillis()) / 60_000L;
        String msg = "✦ " + q.title + "\nObjectif : " + objective + "\nRécompense : " + q.reward + " PO (virement sur ton compte)"
                + "\nTemps restant : " + left / 60 + "h" + String.format("%02d", left % 60)
                + (q.completed ? "\nObjectif atteint : virement en cours." : "\n/quete abandonner pour y renoncer.");
        c.getSource().sendSuccess(() -> Component.literal(msg).withStyle(ChatFormatting.YELLOW), false);
        QuestManager.refreshHud(p, q);
        return 1;
    }

    private static int abandon(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        QuestData.ActiveQuest q = QuestData.get(c.getSource().getServer()).quests.get(p.getUUID());
        if (q == null || q.completed) {
            c.getSource().sendFailure(Component.literal("Tu n'as pas de quête à abandonner."));
            return 0;
        }
        QuestManager.abandon(p);
        c.getSource().sendSuccess(() -> Component.literal("Quête abandonnée : " + q.title).withStyle(ChatFormatting.GRAY), false);
        return 1;
    }
}
