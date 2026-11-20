package fr.citizen.quetes;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Événements du jeu + planification automatique des messagers. */
public final class QuestEvents {
    private static final Random RANDOM = new Random();
    private static int ticks;

    private QuestEvents() { }

    static void onServerStarted(ServerStartedEvent e) {
        CitizenQuetes.LOGGER.info("Citizen Quêtes : {}", QuestConfig.load().replace('\n', ' '));
        if (!Rewards.economyInstalled())
            CitizenQuetes.LOGGER.warn("Citizen Quêtes : Citizen Economy absent, les virements resteront en attente.");
        QuestData data = QuestData.get(e.getServer());
        if (data.nextMessenger == 0) {
            data.nextMessenger = System.currentTimeMillis() + intervalMs();
            data.setDirty();
        }
    }

    static void onServerStopping(ServerStoppingEvent e) {
        Messenger.removeAll();
    }

    static void onTick(ServerTickEvent.Post e) {
        MinecraftServer server = e.getServer();
        ticks++;
        Messenger.tickAll(server);
        if (ticks % 20 == 0) {
            try {
                QuestManager.tick(server);
                schedule(server);
            } catch (RuntimeException ex) {
                CitizenQuetes.LOGGER.error("Citizen Quêtes : erreur pendant le suivi des quêtes", ex);
            }
        }
    }

    static long intervalMs() { return Math.max(1, QuestConfig.settings().intervalleMinutes) * 60_000L; }

    /** 100 % automatique : toutes les X minutes, un messager part vers un joueur au hasard. */
    private static void schedule(MinecraftServer server) {
        QuestData data = QuestData.get(server);
        long now = System.currentTimeMillis();
        if (data.nextMessenger == 0) { data.nextMessenger = now + intervalMs(); data.setDirty(); }
        if (now < data.nextMessenger || Messenger.count() > 0) return;

        List<ServerPlayer> online = server.getPlayerList().getPlayers();
        List<ServerPlayer> eligible = new ArrayList<>();
        for (ServerPlayer p : online) if (eligible(data, p)) eligible.add(p);
        if (online.size() < Math.max(1, QuestConfig.settings().joueursMinimum) || eligible.isEmpty()) {
            data.nextMessenger = now + 60_000L; // personne de disponible : on réessaie dans 1 minute
            data.setDirty();
            return;
        }
        // On essaie plusieurs joueurs au cas où l'un d'eux serait dans un endroit impossible (grotte inondée…).
        java.util.Collections.shuffle(eligible, RANDOM);
        for (ServerPlayer p : eligible) {
            String err = Messenger.spawnFor(p);
            if (err == null) {
                data.nextMessenger = now + intervalMs();
                data.setDirty();
                return;
            }
            CitizenQuetes.LOGGER.debug("Citizen Quêtes : messager impossible pour {} : {}", p.getGameProfile().getName(), err);
        }
        data.nextMessenger = now + 60_000L;
        data.setDirty();
    }

    static boolean eligible(QuestData data, ServerPlayer p) {
        if (!p.isAlive() || p.isSpectator() || p.isCreative()) return false;
        if (data.quests.containsKey(p.getUUID()) || Messenger.targets(p.getUUID())) return false;
        List<String> dims = QuestConfig.settings().dimensions;
        return dims == null || dims.isEmpty() || dims.contains(p.level().dimension().location().toString());
    }

    static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) QuestManager.onLogin(p);
    }

    static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        QuestManager.onLogout(e.getEntity().getUUID());
    }

    static void onDeath(LivingDeathEvent e) {
        if (e.getEntity().level().isClientSide()) return;
        if (e.getSource().getEntity() instanceof ServerPlayer p) QuestManager.onKill(p, e.getEntity());
    }

    static void onInteract(PlayerInteractEvent.EntityInteract e) {
        handleInteract(e, e.getTarget());
    }

    static void onInteractSpecific(PlayerInteractEvent.EntityInteractSpecific e) {
        handleInteract(e, e.getTarget());
    }

    /** Clic droit sur un messager : jamais le menu EasyNPC ni le commerce, toujours notre proposition. */
    private static void handleInteract(PlayerInteractEvent e, Entity target) {
        if (!target.getTags().contains(Messenger.TAG)) return;
        if (e instanceof net.neoforged.bus.api.ICancellableEvent c) c.setCanceled(true);
        if (e instanceof PlayerInteractEvent.EntityInteract ei) ei.setCancellationResult(InteractionResult.SUCCESS);
        if (e instanceof PlayerInteractEvent.EntityInteractSpecific es) es.setCancellationResult(InteractionResult.SUCCESS);
        if (e.getLevel().isClientSide() || e.getHand() != InteractionHand.MAIN_HAND) return;
        if (e.getEntity() instanceof ServerPlayer p) {
            if (Messenger.isKnown(target.getUUID())) Messenger.interact(p, target);
            else p.displayClientMessage(Component.literal("Ce messager est sur le départ.").withStyle(ChatFormatting.GRAY), true);
        }
    }

    /** Passe au-dessus des protections de zones : un messager du mod a toujours le droit d'apparaître. */
    static void forceMessengerJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !e.isCanceled()) return;
        Entity entity = e.getEntity();
        if (entity.getTags().contains(Messenger.TAG) && Messenger.isKnown(entity.getUUID())) e.setCanceled(false);
    }

    /** Un ancien messager (après un crash, par exemple) qui réapparaît : on le supprime. */
    static void onEntityJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide()) return;
        Entity entity = e.getEntity();
        if (entity.getTags().contains(Messenger.TAG) && !Messenger.isKnown(entity.getUUID())) e.setCanceled(true);
    }
}
