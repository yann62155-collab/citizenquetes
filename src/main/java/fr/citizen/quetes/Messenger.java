package fr.citizen.quetes;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Un messager du château et son comportement :
 * APPROCHE (marche vers le joueur) → ATTENTE (menu Accepter/Refuser) → CHÂTEAU (accepté) ou DÉPART (refusé, 50 blocs).
 * Les messagers ne sont jamais sauvegardés : au redémarrage ils sont supprimés.
 */
public final class Messenger {
    public static final String TAG = "citizenquetes_messager";

    /** Ce que le messager propose. */
    public record Offer(String title, List<String> dialogue, String objective, long reward, int minutes, QuestData.ActiveQuest quest) { }

    enum State { APPROACH, WAITING, TO_CASTLE, LEAVING }

    private static final Map<UUID, Messenger> ACTIVE = new HashMap<>();
    private static final Random RANDOM = new Random();
    private static int lastSkin = -1;

    private final UUID entityId;
    private final ServerLevel level;
    private final UUID target;
    private final String name;
    private final Offer offer;
    private State state = State.APPROACH;
    private int stateTicks;
    private Vec3 goal;
    private Vec3 leaveFrom;
    private Vec3 lastCheckPos;
    private int stuckTicks;

    private Messenger(PathfinderMob mob, ServerLevel level, ServerPlayer target, String name, Offer offer) {
        this.entityId = mob.getUUID();
        this.level = level;
        this.target = target.getUUID();
        this.name = name;
        this.offer = offer;
        this.lastCheckPos = mob.position();
    }

    // ------------------------------------------------------------------ registre

    public static int count() { return ACTIVE.size(); }

    public static boolean isKnown(UUID entity) { return ACTIVE.containsKey(entity); }

    public static boolean targets(UUID player) {
        for (Messenger m : ACTIVE.values()) if (m.target.equals(player)) return true;
        return false;
    }

    public static void removeAll() {
        for (Messenger m : new ArrayList<>(ACTIVE.values())) m.discard();
        ACTIVE.clear();
    }

    public static void tickAll(MinecraftServer server) {
        for (Messenger m : new ArrayList<>(ACTIVE.values())) {
            try {
                m.tick(server);
            } catch (RuntimeException e) {
                CitizenQuetes.LOGGER.error("Citizen Quêtes : erreur du messager, suppression", e);
                m.discard();
            }
        }
    }

    /** Joueur console (Geyser/Floodgate) : pseudo avec préfixe, ou UUID Floodgate (commence par 00000000-0000-0000). */
    public static boolean isConsole(ServerPlayer p) {
        String prefix = QuestConfig.settings().prefixePseudoConsole;
        if (prefix != null && !prefix.isEmpty() && p.getGameProfile().getName().startsWith(prefix)) return true;
        return p.getUUID().getMostSignificantBits() == 0L;
    }

    // ------------------------------------------------------------------ apparition

    /** Fait apparaître un messager près du joueur. Renvoie null si OK, sinon la raison. */
    public static String spawnFor(ServerPlayer player) {
        if (targets(player.getUUID())) return "un messager est déjà en route vers ce joueur";
        Offer offer = QuestManager.createOffer(player);
        if (offer == null) return "aucune quête disponible (voir quetes.json)";
        ServerLevel level = player.serverLevel();
        QuestConfig.Settings s = QuestConfig.settings();

        int dist = Math.max(6, s.distanceApparition);
        BlockPos spot = findSpot(level, player.blockPosition(), dist - 4, dist + 4);
        if (spot == null) spot = findSpot(level, player.blockPosition(), 4, 10);
        if (spot == null) return "aucun endroit libre autour du joueur";

        PathfinderMob mob = create(level, isConsole(player));
        String name = s.nomsPnj == null || s.nomsPnj.isEmpty() ? "Messager du château" : s.nomsPnj.get(RANDOM.nextInt(s.nomsPnj.size()));
        float yaw = (float) Math.toDegrees(Math.atan2(player.getZ() - spot.getZ() - 0.5, player.getX() - spot.getX() - 0.5)) - 90f;
        mob.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, yaw, 0f);
        mob.setCustomName(Component.literal(name).withStyle(ChatFormatting.GOLD));
        mob.setCustomNameVisible(true);
        mob.setInvulnerable(true);
        mob.setPersistenceRequired();
        mob.addTag(TAG);
        // Plus aucune IA "maison" : c'est ce mod qui le fait marcher (sinon il flâne, boit des potions, etc.).
        clearGoals(mob);
        if (mob instanceof WanderingTrader trader) trader.setDespawnDelay(0);

        Messenger m = new Messenger(mob, level, player, name, offer);
        ACTIVE.put(mob.getUUID(), m); // avant l'ajout : sinon l'événement d'arrivée le supprimerait
        if (!level.addFreshEntity(mob)) {
            ACTIVE.remove(mob.getUUID());
            return "le monde a refusé l'apparition du PNJ";
        }
        player.displayClientMessage(Component.literal("Un messager du château approche…").withStyle(ChatFormatting.GOLD), true);
        CitizenQuetes.LOGGER.info("Citizen Quêtes : messager envoyé à {} ({})", player.getGameProfile().getName(), offer.title());
        return null;
    }

    private static void clearGoals(Mob mob) {
        for (String field : new String[] {"goalSelector", "targetSelector"}) {
            try {
                Field f = Mob.class.getDeclaredField(field);
                f.setAccessible(true);
                ((GoalSelector) f.get(mob)).removeAllGoals(g -> true);
            } catch (ReflectiveOperationException | RuntimeException e) {
                CitizenQuetes.LOGGER.warn("Citizen Quêtes : impossible de retirer l'IA du PNJ ({})", field);
            }
        }
    }

    private static PathfinderMob create(ServerLevel level, boolean console) {
        QuestConfig.Settings s = QuestConfig.settings();
        String id = console ? s.entitePnjConsole : s.entitePnj;
        Entity e = null;
        ResourceLocation rl = id == null ? null : ResourceLocation.tryParse(id);
        if (rl != null) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(rl).orElse(null);
            if (type != null) e = type.create(level);
        }
        if (!(e instanceof PathfinderMob)) {
            if (e != null) e.discard();
            CitizenQuetes.LOGGER.warn("Citizen Quêtes : entité {} indisponible, marchand ambulant utilisé à la place", id);
            e = EntityType.WANDERING_TRADER.create(level);
        }
        PathfinderMob mob = (PathfinderMob) e;
        if (rl != null && "easy_npc".equals(rl.getNamespace()) && s.skinsDifferents) applySkin(mob, s.nombreSkins);
        return mob;
    }

    /** Skin EasyNPC choisi au hasard parmi les skins fournis par le mod (jamais deux fois de suite le même). */
    private static void applySkin(PathfinderMob mob, int count) {
        int n = Math.max(1, Math.min(60, count));
        int skin = RANDOM.nextInt(n) + 1;
        if (n > 1 && skin == lastSkin) skin = skin % n + 1;
        lastSkin = skin;
        try {
            CompoundTag tag = new CompoundTag();
            mob.saveWithoutId(tag);
            CompoundTag skinTag = new CompoundTag();
            skinTag.putString("Type", "RESOURCE_LOCATION");
            skinTag.putString("Texture", String.format("%s:textures/entity/messagers/messager_%02d.png", CitizenQuetes.MOD_ID, skin));
            skinTag.putLong("Timestamp", System.currentTimeMillis());
            tag.put("SkinData", skinTag);
            mob.load(tag);
        } catch (RuntimeException e) {
            CitizenQuetes.LOGGER.warn("Citizen Quêtes : impossible d'appliquer le skin EasyNPC", e);
        }
    }

    // ------------------------------------------------------------------ comportement

    private void tick(MinecraftServer server) {
        Entity e = level.getEntity(entityId);
        if (!(e instanceof PathfinderMob mob) || e.isRemoved()) {
            ACTIVE.remove(entityId);
            return;
        }
        stateTicks++;
        ServerPlayer p = server.getPlayerList().getPlayer(target);
        boolean present = p != null && p.isAlive() && p.level() == level;
        QuestConfig.Settings s = QuestConfig.settings();

        switch (state) {
            case APPROACH -> {
                if (!present) { leave(mob, null); return; }
                double d = mob.distanceTo(p);
                if (d <= 3.0) { arrive(mob, p); return; }
                if (stateTicks > 20 * 90 || d > 96) { // trop long : il "rattrape" le joueur
                    BlockPos spot = findSpot(level, p.blockPosition(), 2, 4);
                    if (spot != null) mob.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
                    arrive(mob, p);
                    return;
                }
                if (stateTicks % 10 == 1) walk(mob, p.position(), s.vitesseMarche * 1.3);
                if (isStuck(mob, 100) && d > 6) {
                    BlockPos spot = findSpot(level, p.blockPosition(), 5, 8);
                    if (spot != null) mob.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
                }
            }
            case WAITING -> {
                mob.getNavigation().stop();
                if (!present) { leave(mob, null); return; }
                mob.getLookControl().setLookAt(p, 30f, 30f);
                if (stateTicks > 20 * Math.max(10, s.delaiReponseSecondes)) {
                    say(p, "Je ne peux pas attendre plus longtemps. Bonne route.");
                    if (p.containerMenu instanceof OfferMenu) p.closeContainer();
                    leave(mob, p);
                }
            }
            case TO_CASTLE -> {
                if (stateTicks % 10 == 1) walk(mob, goal, s.vitesseMarche);
                boolean far = !present || mob.distanceTo(p) > 40;
                if (horizontal(mob.position(), goal) < 4 || stateTicks > 20 * 90 || (far && stateTicks > 20 * 10)
                        || (isStuck(mob, 100) && stateTicks > 160)) discard();
            }
            case LEAVING -> {
                if (stateTicks % 10 == 1) walk(mob, goal, s.vitesseMarche);
                if (horizontal(mob.position(), leaveFrom) >= Math.max(10, s.distanceDepartRefus) || horizontal(mob.position(), goal) < 3
                        || stateTicks > 20 * 90 || (isStuck(mob, 100) && stateTicks > 160)) discard();
            }
        }
    }

    private void setState(State st) {
        state = st;
        stateTicks = 0;
        stuckTicks = 0;
    }

    private void arrive(PathfinderMob mob, ServerPlayer p) {
        setState(State.WAITING);
        mob.getNavigation().stop();
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 1f, 1f);
        for (String line : offer.dialogue()) say(p, line);
        if (p.containerMenu == p.inventoryMenu) openMenu(p);
        else p.sendSystemMessage(Component.literal("Fais clic droit sur le messager pour voir sa proposition.").withStyle(ChatFormatting.GRAY));
    }

    /** Clic droit d'un joueur sur ce messager. */
    public static void interact(ServerPlayer player, Entity entity) {
        Messenger m = ACTIVE.get(entity.getUUID());
        if (m == null) return;
        if (!m.target.equals(player.getUUID())) {
            player.displayClientMessage(Component.literal("Ce messager ne s'adresse pas à toi.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (m.state == State.APPROACH && entity instanceof PathfinderMob mob) m.arrive(mob, player);
        else if (m.state == State.WAITING) m.openMenu(player);
    }

    private void openMenu(ServerPlayer p) {
        MinecraftServer server = p.server;
        OfferMenu.Choice choice = new OfferMenu.Choice() {
            @Override public void accept() { server.execute(() -> onAccept(p)); }
            @Override public void refuse() { server.execute(() -> onRefuse(p)); }
            @Override public void closed() {
                if (state == State.WAITING)
                    p.displayClientMessage(Component.literal("Clic droit sur le messager pour revoir sa proposition.").withStyle(ChatFormatting.YELLOW), true);
            }
        };
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new OfferMenu(id, inv, offer, choice), Component.literal(name)));
    }

    private void onAccept(ServerPlayer p) {
        if (state != State.WAITING) return;
        p.closeContainer();
        Entity e = level.getEntity(entityId);
        if (!(e instanceof PathfinderMob mob)) return;
        if (!QuestManager.start(p, offer.quest())) {
            say(p, "Tu as déjà une mission en cours. Reviens me voir une autre fois.");
            leave(mob, p);
            return;
        }
        say(p, "Merci, " + p.getGameProfile().getName() + ". Je retourne prévenir le château.");
        setState(State.TO_CASTLE);
        QuestData.Place castle = QuestData.get(p.server).castle;
        if (castle != null && castle.dim().equals(level.dimension().location().toString())) {
            goal = new Vec3(castle.x() + 0.5, castle.y(), castle.z() + 0.5);
        } else { // château non défini ou dans une autre dimension : il part simplement au loin
            double a = RANDOM.nextDouble() * Math.PI * 2;
            goal = mob.position().add(Math.cos(a) * 80, 0, Math.sin(a) * 80);
        }
    }

    private void onRefuse(ServerPlayer p) {
        if (state != State.WAITING) return;
        p.closeContainer();
        Entity e = level.getEntity(entityId);
        if (!(e instanceof PathfinderMob mob)) return;
        say(p, "Très bien. Je ne t'importunerai pas davantage.");
        leave(mob, p);
    }

    /** Part à au moins 50 blocs du joueur, à l'opposé de lui, puis disparaît. */
    private void leave(PathfinderMob mob, ServerPlayer p) {
        setState(State.LEAVING);
        leaveFrom = p != null ? p.position() : mob.position();
        Vec3 dir = mob.position().subtract(leaveFrom);
        dir = new Vec3(dir.x, 0, dir.z);
        if (dir.lengthSqr() < 0.01) {
            double a = RANDOM.nextDouble() * Math.PI * 2;
            dir = new Vec3(Math.cos(a), 0, Math.sin(a));
        }
        int distance = Math.max(10, QuestConfig.settings().distanceDepartRefus);
        goal = leaveFrom.add(dir.normalize().scale(distance + 6));
        if (p == null) discard(); // joueur parti : inutile de marcher pour personne
    }

    private void discard() {
        ACTIVE.remove(entityId);
        Entity e = level.getEntity(entityId);
        if (e != null) e.discard();
    }

    private void say(ServerPlayer p, String text) {
        p.sendSystemMessage(Component.literal("[" + name + "] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.WHITE)));
    }

    // ------------------------------------------------------------------ déplacement

    private boolean isStuck(PathfinderMob mob, int window) {
        stuckTicks++;
        if (stuckTicks < window) return false;
        stuckTicks = 0;
        boolean stuck = mob.position().distanceTo(lastCheckPos) < 1.0;
        lastCheckPos = mob.position();
        return stuck;
    }

    /** Marche vers un but lointain par étapes de 14 blocs (le pathfinding vanilla est limité en distance). */
    private void walk(PathfinderMob mob, Vec3 target, double speed) {
        Vec3 pos = mob.position();
        Vec3 flat = new Vec3(target.x - pos.x, 0, target.z - pos.z);
        Vec3 wp = target;
        if (flat.length() > 14) {
            Vec3 step = pos.add(flat.normalize().scale(14));
            BlockPos ground = findGround(level, BlockPos.containing(step.x, pos.y, step.z));
            wp = ground != null ? Vec3.atBottomCenterOf(ground) : step;
        }
        if (!mob.getNavigation().moveTo(wp.x, wp.y, wp.z, speed)) {
            // Étape impossible (falaise, eau…) : on essaie un point un peu à côté.
            Vec3 side = pos.add(flat.normalize().yRot((float) (RANDOM.nextBoolean() ? 0.8 : -0.8)).scale(8));
            BlockPos ground = findGround(level, BlockPos.containing(side.x, pos.y, side.z));
            if (ground != null) mob.getNavigation().moveTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, speed);
        }
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    static boolean standable(ServerLevel l, BlockPos pos) {
        if (!l.isLoaded(pos) || !l.isLoaded(pos.below())) return false;
        BlockPos below = pos.below();
        return l.getBlockState(below).isFaceSturdy(l, below, Direction.UP)
                && l.getBlockState(pos).getCollisionShape(l, pos).isEmpty()
                && l.getBlockState(pos.above()).getCollisionShape(l, pos.above()).isEmpty()
                && l.getFluidState(pos).isEmpty()
                && l.getFluidState(below).isEmpty();
    }

    /** Sol praticable à cette colonne, proche de la hauteur donnée. */
    static BlockPos findGround(ServerLevel l, BlockPos around) {
        for (int dy = 0; dy <= 8; dy++) {
            BlockPos up = around.above(dy);
            if (standable(l, up)) return up;
            BlockPos down = around.below(dy);
            if (dy > 0 && standable(l, down)) return down;
        }
        if (!l.isLoaded(around)) return null;
        int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, around.getX(), around.getZ());
        BlockPos top = new BlockPos(around.getX(), y, around.getZ());
        return Math.abs(y - around.getY()) <= 12 && standable(l, top) ? top : null;
    }

    /** Endroit libre entre min et max blocs du centre. */
    static BlockPos findSpot(ServerLevel l, BlockPos center, int min, int max) {
        for (int i = 0; i < 40; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2;
            double d = min + RANDOM.nextDouble() * Math.max(1, max - min);
            BlockPos column = BlockPos.containing(center.getX() + 0.5 + Math.cos(a) * d, center.getY(), center.getZ() + 0.5 + Math.sin(a) * d);
            BlockPos g = findGround(l, column);
            if (g != null) return g;
        }
        return null;
    }
}
