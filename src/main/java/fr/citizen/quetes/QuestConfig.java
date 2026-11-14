package fr.citizen.quetes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Fichiers de configuration (dossier config/citizenquetes/ du serveur) :
 *  - reglages.json : fréquence, entités, délais…
 *  - quetes.json   : la liste des quêtes (modifiable sans recompiler, puis /quetes recharger).
 */
public final class QuestConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Réglages généraux. Les noms de champs sont ceux du fichier JSON. */
    public static final class Settings {
        public int intervalleMinutes = 90;
        public int joueursMinimum = 1;
        public String entitePnj = "easy_npc:humanoid";
        public String entitePnjConsole = "minecraft:wandering_trader";
        public String prefixePseudoConsole = ".";
        public boolean skinsDifferents = true;
        public int nombreSkins = 60;
        public int distanceApparition = 22;
        public int delaiReponseSecondes = 60;
        public int distanceDepartRefus = 50;
        public int dureeQueteMinutes = 180;
        public boolean payerDepuisTresorRoyal = false;
        public double vitesseMarche = 0.55;
        public List<String> dimensions = new ArrayList<>(List.of("minecraft:overworld"));
        public List<String> nomsPnj = new ArrayList<>(List.of("Messager du château", "Héraut du château", "Coursier du château", "Envoyé du château"));
    }

    /** Une quête possible. type = objets | tuer | ronde. */
    public static final class Template {
        public String id;
        public String titre;
        public String type;
        /** Objet (minecraft:wheat), tag d'objets (#minecraft:logs), entité (minecraft:zombie) ou tag d'entités. Inutile pour une ronde. */
        public String cible = "";
        /** Nom affiché dans le HUD et les dialogues ("Blé", "Zombies"…). */
        public String nom = "";
        public int min = 1;
        public int max = 1;
        public int recompenseMin = 20;
        public int recompenseMax = 60;
        public int poids = 10;
        /** Phrases du messager. Variables : {joueur} {quantite} {nom} {lieu} {recompense}. */
        public List<String> dialogue = new ArrayList<>();
    }

    public static final class QuestFile {
        public List<Template> quetes = new ArrayList<>();
    }

    private static Settings settings = new Settings();
    private static List<Template> templates = new ArrayList<>();

    private QuestConfig() { }

    public static Settings settings() { return settings; }
    public static List<Template> templates() { return templates; }

    private static Path dir() { return FMLPaths.CONFIGDIR.get().resolve(CitizenQuetes.MOD_ID); }

    /** Charge (ou crée) les deux fichiers. Renvoie un petit résumé pour l'admin. */
    public static String load() {
        StringBuilder report = new StringBuilder();
        try {
            Files.createDirectories(dir());
            Path s = dir().resolve("reglages.json");
            if (!Files.exists(s)) write(s, new Settings());
            Settings loaded = read(s, Settings.class);
            settings = loaded == null ? new Settings() : loaded;
            write(s, settings); // ajoute les nouveaux réglages éventuels au fichier

            Path q = dir().resolve("quetes.json");
            if (!Files.exists(q)) write(q, DefaultQuests.create());
            QuestFile file = read(q, QuestFile.class);
            List<Template> valid = new ArrayList<>();
            if (file != null && file.quetes != null) {
                for (Template t : file.quetes) {
                    String err = validate(t);
                    if (err == null) valid.add(t);
                    else {
                        CitizenQuetes.LOGGER.warn("Citizen Quêtes : quête ignorée ({}) : {}", t == null ? "?" : t.id, err);
                        report.append("Ignorée : ").append(t == null ? "?" : t.id).append(" (").append(err).append(")\n");
                    }
                }
            }
            templates = valid;
            report.append(valid.size()).append(" quête(s) chargée(s).");
        } catch (IOException | JsonParseException e) {
            CitizenQuetes.LOGGER.error("Citizen Quêtes : lecture de la configuration impossible", e);
            report.append("Erreur de lecture : ").append(e.getMessage());
        }
        return report.toString();
    }

    private static String validate(Template t) {
        if (t == null || t.id == null || t.id.isBlank()) return "id manquant";
        if (t.titre == null || t.titre.isBlank()) return "titre manquant";
        if (t.type == null) return "type manquant";
        if (t.min < 1 || t.max < t.min) return "min/max invalides";
        if (t.recompenseMin < 1 || t.recompenseMax < t.recompenseMin) return "récompense invalide";
        if (t.poids < 1) return "poids invalide";
        if (t.dialogue == null || t.dialogue.isEmpty()) return "dialogue vide";
        switch (t.type) {
            case "objets" -> {
                if (t.cible == null || t.cible.isBlank()) return "cible manquante";
                if (!t.cible.startsWith("#")) {
                    ResourceLocation rl = ResourceLocation.tryParse(t.cible);
                    if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) return "objet inconnu " + t.cible;
                }
            }
            case "tuer" -> {
                if (t.cible == null || t.cible.isBlank()) return "cible manquante";
                if (!t.cible.startsWith("#")) {
                    ResourceLocation rl = ResourceLocation.tryParse(t.cible);
                    if (rl == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(rl)) return "entité inconnue " + t.cible;
                }
            }
            case "ronde" -> { }
            default -> { return "type inconnu " + t.type + " (objets, tuer ou ronde)"; }
        }
        return null;
    }

    private static <T> T read(Path p, Class<T> type) throws IOException {
        try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            return GSON.fromJson(r, type);
        }
    }

    private static void write(Path p, Object o) throws IOException {
        try (Writer w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
            GSON.toJson(o, w);
        }
    }
}
