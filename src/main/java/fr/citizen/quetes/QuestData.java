package fr.citizen.quetes;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Données sauvegardées dans le monde (data/citizenquetes.dat) : château, lieux de ronde, quêtes en cours. */
public final class QuestData extends SavedData {
    private static final String FILE = "citizenquetes";

    /** Une position nommée dans une dimension. */
    public record Place(String name, String dim, int x, int y, int z) {
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("name", name);
            t.putString("dim", dim);
            t.putInt("x", x);
            t.putInt("y", y);
            t.putInt("z", z);
            return t;
        }

        static Place load(CompoundTag t) {
            return new Place(t.getString("name"), t.getString("dim"), t.getInt("x"), t.getInt("y"), t.getInt("z"));
        }
    }

    /** Quête acceptée par un joueur. */
    public static final class ActiveQuest {
        public UUID player;
        public String playerName = "";
        public String templateId = "";
        public String title = "";
        public String type = "";
        public String target = "";
        public String targetName = "";
        public int amount = 1;
        public int progress;
        public long reward;
        public long startedAt;
        public long endsAt;
        public UUID txId = UUID.randomUUID();
        public Place place;            // pour une ronde
        public boolean completed;      // objectif atteint, virement en cours ou en attente

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("player", player);
            t.putString("playerName", playerName);
            t.putString("templateId", templateId);
            t.putString("title", title);
            t.putString("type", type);
            t.putString("target", target);
            t.putString("targetName", targetName);
            t.putInt("amount", amount);
            t.putInt("progress", progress);
            t.putLong("reward", reward);
            t.putLong("startedAt", startedAt);
            t.putLong("endsAt", endsAt);
            t.putUUID("txId", txId);
            if (place != null) t.put("place", place.save());
            t.putBoolean("completed", completed);
            return t;
        }

        static ActiveQuest load(CompoundTag t) {
            ActiveQuest q = new ActiveQuest();
            q.player = t.getUUID("player");
            q.playerName = t.getString("playerName");
            q.templateId = t.getString("templateId");
            q.title = t.getString("title");
            q.type = t.getString("type");
            q.target = t.getString("target");
            q.targetName = t.getString("targetName");
            q.amount = t.getInt("amount");
            q.progress = t.getInt("progress");
            q.reward = t.getLong("reward");
            q.startedAt = t.getLong("startedAt");
            q.endsAt = t.getLong("endsAt");
            q.txId = t.getUUID("txId");
            if (t.contains("place", Tag.TAG_COMPOUND)) q.place = Place.load(t.getCompound("place"));
            q.completed = t.getBoolean("completed");
            return q;
        }
    }

    public Place castle;
    public final Map<String, Place> places = new LinkedHashMap<>();
    public final Map<UUID, ActiveQuest> quests = new LinkedHashMap<>();
    /** Heure (ms) du prochain messager. 0 = pas encore planifié. */
    public long nextMessenger;

    public static QuestData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(QuestData::new, QuestData::load, null), FILE);
    }

    private static QuestData load(CompoundTag tag, HolderLookup.Provider registries) {
        QuestData d = new QuestData();
        if (tag.contains("castle", Tag.TAG_COMPOUND)) d.castle = Place.load(tag.getCompound("castle"));
        ListTag pl = tag.getList("places", Tag.TAG_COMPOUND);
        for (int i = 0; i < pl.size(); i++) {
            Place p = Place.load(pl.getCompound(i));
            d.places.put(p.name().toLowerCase(java.util.Locale.ROOT), p);
        }
        ListTag ql = tag.getList("quests", Tag.TAG_COMPOUND);
        for (int i = 0; i < ql.size(); i++) {
            try {
                ActiveQuest q = ActiveQuest.load(ql.getCompound(i));
                d.quests.put(q.player, q);
            } catch (RuntimeException e) {
                CitizenQuetes.LOGGER.warn("Citizen Quêtes : quête sauvegardée illisible ignorée", e);
            }
        }
        d.nextMessenger = tag.getLong("nextMessenger");
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (castle != null) tag.put("castle", castle.save());
        ListTag pl = new ListTag();
        places.values().forEach(p -> pl.add(p.save()));
        tag.put("places", pl);
        ListTag ql = new ListTag();
        quests.values().forEach(q -> ql.add(q.save()));
        tag.put("quests", ql);
        tag.putLong("nextMessenger", nextMessenger);
        return tag;
    }
}
