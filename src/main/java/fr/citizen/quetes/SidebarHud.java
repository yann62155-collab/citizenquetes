package fr.citizen.quetes;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * HUD de droite : tableau latéral (sidebar) envoyé à UN seul joueur par paquets.
 * Le tableau latéral est vanilla : il s'affiche sur PC et sur console (Geyser le traduit).
 * Les lignes utilisent les codes couleur §, compris par Java et Bedrock.
 */
public final class SidebarHud {
    private static final String OBJECTIVE = "citizenquete";
    private static final Scoreboard DUMMY = new Scoreboard();
    private static final Map<UUID, List<String>> SHOWN = new HashMap<>();

    private SidebarHud() { }

    public static void show(ServerPlayer player, String title, List<String> lines) {
        List<String> unique = uniquify(lines);
        List<String> previous = SHOWN.get(player.getUUID());
        Objective obj = new Objective(DUMMY, OBJECTIVE, ObjectiveCriteria.DUMMY, Component.literal(title),
                ObjectiveCriteria.RenderType.INTEGER, false, BlankFormat.INSTANCE);
        if (previous == null) {
            player.connection.send(new ClientboundSetObjectivePacket(obj, ClientboundSetObjectivePacket.METHOD_ADD));
        } else {
            player.connection.send(new ClientboundSetObjectivePacket(obj, ClientboundSetObjectivePacket.METHOD_CHANGE));
            for (String old : previous) {
                if (!unique.contains(old)) player.connection.send(new ClientboundResetScorePacket(old, OBJECTIVE));
            }
        }
        player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, obj));
        int score = unique.size();
        for (String line : unique) {
            player.connection.send(new ClientboundSetScorePacket(line, OBJECTIVE, score--, Optional.empty(), Optional.empty()));
        }
        SHOWN.put(player.getUUID(), unique);
    }

    public static void clear(ServerPlayer player) {
        if (SHOWN.remove(player.getUUID()) == null) return;
        Objective obj = new Objective(DUMMY, OBJECTIVE, ObjectiveCriteria.DUMMY, Component.empty(),
                ObjectiveCriteria.RenderType.INTEGER, false, BlankFormat.INSTANCE);
        player.connection.send(new ClientboundSetObjectivePacket(obj, ClientboundSetObjectivePacket.METHOD_REMOVE));
        // On remet le tableau latéral "normal" du serveur s'il y en a un.
        Objective server = player.server.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
        if (server != null) player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, server));
    }

    /** À la déconnexion : le client oublie tout, on oublie aussi. */
    public static void forget(UUID player) { SHOWN.remove(player); }

    public static boolean isShown(UUID player) { return SHOWN.containsKey(player); }

    /** Deux lignes identiques s'écraseraient : on les rend uniques avec des codes invisibles. */
    private static List<String> uniquify(List<String> lines) {
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String l : lines) {
            String s = l;
            while (!seen.add(s)) s = s + "§r";
            out.add(s);
        }
        return out;
    }
}
