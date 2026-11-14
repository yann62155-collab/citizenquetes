package fr.citizen.quetes;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/** Quêtes des joueurs : création, suivi (HUD), validation automatique et virement. */
public final class QuestManager {
    private static final Random RANDOM = new Random();
    /** Virements en cours (évite de lancer deux fois le même en même temps). */
    private static final Set<UUID> PAYING = new HashSet<>();
    /** Messages temporaires du HUD (quête terminée / expirée) : heure de fin. */
    private static final Map<UUID, Long> TEMP_HUD = new HashMap<>();
    private static long lastRetry;

    private QuestManager() { }

    // ------------------------------------------------------------------ création

    static Messenger.Offer createOffer(ServerPlayer player) {
        QuestData data = QuestData.get(player.server);
        List<QuestConfig.Template> pool = new ArrayList<>();
        int total = 0;
        for (QuestConfig.Template t : QuestConfig.templates()) {
            if ("ronde".equals(t.type) && placesFor(data, player).isEmpty()) continue;
            pool.add(t);
            total += t.poids;
        }
        if (pool.isEmpty()) return null;
        int roll = RANDOM.nextInt(total);
        QuestConfig.Template t = pool.get(0);
        for (QuestConfig.Template c : pool) {
            roll -= c.poids;
            if (roll < 0) { t = c; break; }
        }

        int qty = t.min + (t.max > t.min ? RANDOM.nextInt(t.max - t.min + 1) : 0);
        double ratio = t.max > t.min ? (qty - t.min) / (double) (t.max - t.min) : RANDOM.nextDouble();
        long reward = Math.round(t.recompenseMin + ratio * (t.recompenseMax - t.recompenseMin));
        reward = Math.max(5, Math.round(reward / 5.0) * 5);

        QuestData.ActiveQuest q = new QuestData.ActiveQuest();
        q.player = player.getUUID();
        q.playerName = player.getGameProfile().getName();
        q.templateId = t.id;
        q.title = t.titre;
        q.type = t.type;
        q.target = t.cible == null ? "" : t.cible;
        q.targetName = t.nom == null ? "" : t.nom;
        q.amount = "ronde".equals(t.type) ? 1 : qty;
        q.reward = reward;
        String placeName = "";
        if ("ronde".equals(t.type)) {
            List<QuestData.Place> places = placesFor(data, player);
            q.place = places.get(RANDOM.nextInt(places.size()));
            placeName = q.place.name();
            q.targetName = placeName;
        }

        List<String> lines = new ArrayList<>();
        for (String l : t.dialogue) {
            lines.add(l.replace("{joueur}", q.playerName).replace("{quantite}", String.valueOf(q.amount))
                    .replace("{nom}", q.targetName).replace("{lieu}", placeName).replace("{recompense}", String.valueOf(reward)));
        }
        String objective = switch (t.type) {
            case "objets" -> "rapporter " + q.amount + " " + q.targetName;
            case "tuer" -> "éliminer " + q.amount + " " + q.targetName;
            default -> "aller jusqu'à " + placeName;
        };
        int minutes = Math.max(10, QuestConfig.settings().dureeQueteMinutes);
        return new Messenger.Offer(t.titre, lines, objective, reward, minutes, q);
    }

    private static List<QuestData.Place> placesFor(QuestData data, ServerPlayer player) {
        String dim = player.level().dimension().location().toString();
        List<QuestData.Place> out = new ArrayList<>();
        for (QuestData.Place p : data.places.values()) if (p.dim().equals(dim)) out.add(p);
        return out;
    }

    /** Le joueur accepte : la quête démarre. false s'il en a déjà une. */
    static boolean start(ServerPlayer player, QuestData.ActiveQuest q) {
        QuestData data = QuestData.get(player.server);
        if (data.quests.containsKey(player.getUUID())) return false;
        q.startedAt = System.currentTimeMillis();
        q.endsAt = q.startedAt + Math.max(10, QuestConfig.settings().dureeQueteMinutes) * 60_000L;
        q.progress = 0;
        q.completed = false;
        q.txId = UUID.randomUUID();
        data.quests.put(player.getUUID(), q);
        data.setDirty();
        TEMP_HUD.remove(player.getUUID());
        player.sendSystemMessage(Component.literal("✦ Nouvelle quête : ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(q.title).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" — suis ta progression à droite de l'écran. (/quete pour les détails)").withStyle(ChatFormatting.GRAY)));
        player.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1f);
        refreshHud(player, q);
        return true;
    }

    public static void abandon(ServerPlayer player) {
        QuestData data = QuestData.get(player.server);
        QuestData.ActiveQuest q = data.quests.get(player.getUUID());
        if (q == null || q.completed) return;
        data.quests.remove(player.getUUID());
        data.setDirty();
        SidebarHud.clear(player);
    }

    // ------------------------------------------------------------------ suivi

    /** Appelé chaque seconde. */
    static void tick(MinecraftServer server) {
        QuestData data = QuestData.get(server);
        long now = System.currentTimeMillis();

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Long until = TEMP_HUD.get(p.getUUID());
            if (until != null && now >= until) {
                TEMP_HUD.remove(p.getUUID());
                QuestData.ActiveQuest q = data.quests.get(p.getUUID());
                if (q == null) SidebarHud.clear(p); else refreshHud(p, q);
            }
        }

        for (QuestData.ActiveQuest q : new ArrayList<>(data.quests.values())) {
            ServerPlayer p = server.getPlayerList().getPlayer(q.player);
            if (q.completed) {
                if (now - lastRetry > 60_000L && !PAYING.contains(q.player)) pay(server, q);
                continue;
            }
            if (now > q.endsAt) {
                data.quests.remove(q.player);
                data.setDirty();
                if (p != null) {
                    p.sendSystemMessage(Component.literal("✘ Quête expirée : " + q.title).withStyle(ChatFormatting.RED));
                    showTemp(p, "§c✘ Quête expirée", List.of("§f" + q.title, "§7Un autre messager", "§7viendra plus tard."), 8);
                }
                continue;
            }
            if (p == null) continue;
            switch (q.type) {
                case "objets" -> {
                    int have = countItems(p, itemMatcher(q.target));
                    if (have >= q.amount) {
                        removeItems(p, itemMatcher(q.target), q.amount);
                        q.progress = q.amount;
                        complete(server, p, q);
                        continue;
                    }
                    q.progress = have;
                }
                case "ronde" -> {
                    QuestData.Place pl = q.place;
                    if (pl != null && pl.dim().equals(p.level().dimension().location().toString())) {
                        double dx = p.getX() - (pl.x() + 0.5), dz = p.getZ() - (pl.z() + 0.5);
                        if (dx * dx + dz * dz <= 10 * 10) {
                            q.progress = 1;
                            complete(server, p, q);
                            continue;
                        }
                    }
                }
                default -> { }
            }
            if (!TEMP_HUD.containsKey(p.getUUID())) refreshHud(p, q);
        }
        if (now - lastRetry > 60_000L) lastRetry = now;
    }

    /** Un joueur a tué une créature. */
    static void onKill(ServerPlayer player, Entity killed) {
        QuestData data = QuestData.get(player.server);
        QuestData.ActiveQuest q = data.quests.get(player.getUUID());
        if (q == null || q.completed || !"tuer".equals(q.type) || !entityMatches(q.target, killed)) return;
        q.progress++;
        data.setDirty();
        if (q.progress >= q.amount) complete(player.server, player, q);
        else {
            player.displayClientMessage(Component.literal(q.targetName + " : " + q.progress + "/" + q.amount).withStyle(ChatFormatting.YELLOW), true);
            refreshHud(player, q);
        }
    }

    static void onLogin(ServerPlayer player) {
        QuestData.ActiveQuest q = QuestData.get(player.server).quests.get(player.getUUID());
        if (q == null) return;
        refreshHud(player, q);
        if (q.completed && !PAYING.contains(q.player)) pay(player.server, q);
    }

    // ------------------------------------------------------------------ fin de quête et virement

    private static void complete(MinecraftServer server, ServerPlayer p, QuestData.ActiveQuest q) {
        q.completed = true;
        QuestData.get(server).setDirty();
        p.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 1f);
        refreshHud(p, q);
        pay(server, q);
    }

    private static void pay(MinecraftServer server, QuestData.ActiveQuest q) {
        if (!PAYING.add(q.player)) return;
        Rewards.pay(q.player, q.playerName, q.reward, q.txId, q.title).whenComplete((ok, err) -> server.execute(() -> {
            PAYING.remove(q.player);
            QuestData data = QuestData.get(server);
            ServerPlayer p = server.getPlayerList().getPlayer(q.player);
            if (err == null) {
                if (data.quests.get(q.player) == q) data.quests.remove(q.player);
                data.setDirty();
                CitizenQuetes.LOGGER.info("Citizen Quêtes : {} a terminé « {} », virement de {} PO", q.playerName, q.title, q.reward);
                if (p != null) {
                    p.sendSystemMessage(Component.literal("✔ Quête terminée : ").withStyle(ChatFormatting.GREEN)
                            .append(Component.literal(q.title).withStyle(ChatFormatting.YELLOW))
                            .append(Component.literal(". Virement de " + q.reward + " PO effectué sur votre compte.").withStyle(ChatFormatting.GREEN)));
                    p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1f);
                    showTemp(p, "§a✔ Quête terminée !", List.of("§f" + q.title, "§aVirement de §e" + q.reward + " PO", "§aeffectué sur votre compte"), 12);
                }
            } else {
                CitizenQuetes.LOGGER.warn("Citizen Quêtes : virement de {} PO à {} impossible pour le moment ({}) — nouvel essai dans 1 min",
                        q.reward, q.playerName, err.getMessage());
                if (p != null) refreshHud(p, q);
            }
        }));
    }

    // ------------------------------------------------------------------ HUD

    static void refreshHud(ServerPlayer p, QuestData.ActiveQuest q) {
        if (TEMP_HUD.containsKey(p.getUUID())) return;
        List<String> lines = new ArrayList<>();
        if (q.completed) {
            lines.add("§aObjectif atteint !");
            lines.add("§7Virement en cours…");
            lines.add("§6Récompense : §e" + q.reward + " PO");
            SidebarHud.show(p, "§6✦ " + q.title, lines);
            return;
        }
        lines.add("§7Objectif :");
        switch (q.type) {
            case "objets" -> lines.add("§f" + q.targetName + " : §e" + Math.min(q.progress, q.amount) + "/" + q.amount);
            case "tuer" -> lines.add("§f" + q.targetName + " : §e" + q.progress + "/" + q.amount);
            default -> {
                lines.add("§fAller à : §e" + q.targetName);
                if (q.place != null) lines.add("§7X " + q.place.x() + "  Z " + q.place.z());
            }
        }
        lines.add("§6Récompense : §e" + q.reward + " PO");
        long left = Math.max(0, q.endsAt - System.currentTimeMillis()) / 60_000L;
        lines.add("§7Temps : §f" + left / 60 + "h" + String.format("%02d", left % 60));
        SidebarHud.show(p, "§6✦ " + q.title, lines);
    }

    private static void showTemp(ServerPlayer p, String title, List<String> lines, int seconds) {
        TEMP_HUD.remove(p.getUUID());
        SidebarHud.show(p, title, lines);
        TEMP_HUD.put(p.getUUID(), System.currentTimeMillis() + seconds * 1000L);
    }

    static void onLogout(UUID player) {
        TEMP_HUD.remove(player);
        SidebarHud.forget(player);
    }

    // ------------------------------------------------------------------ objets et créatures

    static Predicate<ItemStack> itemMatcher(String target) {
        if (target.startsWith("#")) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, ResourceLocation.parse(target.substring(1)));
            return s -> s.is(tag);
        }
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(target));
        return s -> s.is(item);
    }

    private static boolean entityMatches(String target, Entity e) {
        if (target.startsWith("#")) {
            TagKey<EntityType<?>> tag = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse(target.substring(1)));
            return e.getType().is(tag);
        }
        return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().equals(target);
    }

    static int countItems(ServerPlayer p, Predicate<ItemStack> match) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (!s.isEmpty() && match.test(s)) n += s.getCount();
        for (ItemStack s : p.getInventory().offhand) if (!s.isEmpty() && match.test(s)) n += s.getCount();
        return n;
    }

    private static void removeItems(ServerPlayer p, Predicate<ItemStack> match, int amount) {
        int left = amount;
        for (List<ItemStack> list : List.of(p.getInventory().items, p.getInventory().offhand)) {
            for (ItemStack s : list) {
                if (left <= 0) break;
                if (s.isEmpty() || !match.test(s)) continue;
                int take = Math.min(left, s.getCount());
                s.shrink(take);
                left -= take;
            }
        }
        p.getInventory().setChanged();
        p.containerMenu.broadcastChanges();
    }
}
