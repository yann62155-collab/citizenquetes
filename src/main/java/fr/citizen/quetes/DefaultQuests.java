package fr.citizen.quetes;

import java.util.List;

/**
 * Quêtes par défaut, écrites dans config/citizenquetes/quetes.json au premier démarrage.
 * Règles du lore respectées : royaume affaibli par la guerre, aucune mention des saisons,
 * aucun fait historique inventé, rien sur les Traqueurs, l'homme encapuchonné ou le Sceptre Maudit.
 */
final class DefaultQuests {
    private DefaultQuests() { }

    private static QuestConfig.Template t(String id, String titre, String type, String cible, String nom,
                                          int min, int max, int rMin, int rMax, int poids, String... dialogue) {
        QuestConfig.Template q = new QuestConfig.Template();
        q.id = id;
        q.titre = titre;
        q.type = type;
        q.cible = cible;
        q.nom = nom;
        q.min = min;
        q.max = max;
        q.recompenseMin = rMin;
        q.recompenseMax = rMax;
        q.poids = poids;
        q.dialogue = new java.util.ArrayList<>(List.of(dialogue));
        return q;
    }

    static QuestConfig.QuestFile create() {
        QuestConfig.QuestFile f = new QuestConfig.QuestFile();
        f.quetes.addAll(List.of(
                // ---------------- ravitaillement
                t("ble", "Greniers vides", "objets", "minecraft:wheat", "Blé", 24, 48, 40, 90, 10,
                        "Bien le bonjour, {joueur}.",
                        "Depuis la guerre, les greniers du château sont presque vides.",
                        "Le château a besoin de {quantite} {nom}. Le royaume saura s'en souvenir."),
                t("pain", "Pain pour la garnison", "objets", "minecraft:bread", "Pain", 12, 24, 45, 100, 10,
                        "Salutations, {joueur}.",
                        "La garnison a faim et les fours du château tournent au ralenti.",
                        "Rassemble {quantite} {nom} pour les soldats."),
                t("viande", "Vivres pour le château", "objets", "minecraft:cooked_beef", "Steak cuit", 8, 16, 50, 110, 8,
                        "{joueur}, un instant je te prie.",
                        "Les soldats qui gardent encore les murs ont besoin de forces.",
                        "Le château demande {quantite} {nom}."),
                t("pommes_terre", "Réserves pour l'hiver", "objets", "minecraft:potato", "Pommes de terre", 24, 48, 35, 80, 8,
                        "Bonjour, {joueur}.",
                        "Le royaume doit refaire ses réserves avant les jours difficiles.",
                        "Apporte {quantite} {nom}, le château les attend."),
                t("poisson", "La pêche du jour", "objets", "minecraft:cod", "Morues", 8, 16, 45, 100, 7,
                        "Ah, {joueur} ! On te cherchait.",
                        "Les cuisines du château manquent de poisson frais.",
                        "Pêche {quantite} {nom} pour nourrir les gens du château."),
                // ---------------- reconstruction
                t("bois", "Réparer les palissades", "objets", "#minecraft:logs", "Bûches", 32, 64, 50, 120, 10,
                        "Bonjour, {joueur}.",
                        "La guerre a laissé les palissades en piteux état.",
                        "Il faudrait {quantite} {nom} pour commencer les réparations."),
                t("pierre", "Relever les murailles", "objets", "minecraft:cobblestone", "Pierre taillée", 64, 128, 50, 120, 10,
                        "{joueur}, le château a besoin de bras.",
                        "Des pans de murailles se sont effondrés pendant les combats.",
                        "Rapporte {quantite} {nom} pour les maçons."),
                t("charbon", "Rallumer les forges", "objets", "minecraft:coal", "Charbon", 16, 40, 45, 110, 8,
                        "Salutations, {joueur}.",
                        "Les forges du château manquent de combustible.",
                        "Apporte {quantite} {nom} pour qu'elles reprennent le travail."),
                t("fer", "Fer pour l'armurerie", "objets", "minecraft:iron_ingot", "Lingots de fer", 8, 24, 70, 170, 8,
                        "{joueur}, écoute-moi bien.",
                        "L'armurerie a été vidée par la guerre.",
                        "Le château a besoin de {quantite} {nom}."),
                // ---------------- armement / soins
                t("epees", "Armer les nouvelles recrues", "objets", "minecraft:iron_sword", "Épées en fer", 2, 4, 90, 200, 6,
                        "Bonjour, {joueur}.",
                        "De nouvelles recrues rejoignent la garde, mais elles n'ont pas d'armes.",
                        "Forge ou trouve {quantite} {nom} pour elles."),
                t("plastrons", "Protéger la garde", "objets", "minecraft:iron_chestplate", "Plastrons en fer", 1, 2, 110, 220, 5,
                        "{joueur}, un mot si tu veux bien.",
                        "Beaucoup de gardes montent la garde sans protection.",
                        "Apporte {quantite} {nom} au château."),
                t("fleches", "Remplir les carquois", "objets", "minecraft:arrow", "Flèches", 32, 64, 45, 110, 8,
                        "Salutations, {joueur}.",
                        "Les archers des remparts n'ont presque plus de flèches.",
                        "Le château demande {quantite} {nom}."),
                t("pommes_or", "Remèdes pour les blessés", "objets", "minecraft:golden_apple", "Pommes dorées", 1, 3, 120, 250, 4,
                        "{joueur}… les guérisseurs ont besoin d'aide.",
                        "Des blessés de la guerre sont encore soignés au château.",
                        "Trouve {quantite} {nom} pour les remettre sur pied."),
                t("laine", "Couvertures pour les blessés", "objets", "#minecraft:wool", "Laine", 8, 20, 40, 95, 7,
                        "Bonjour, {joueur}.",
                        "Les nuits sont froides pour ceux qui ont tout perdu pendant la guerre.",
                        "Apporte {quantite} {nom} pour leur faire des couvertures."),
                t("cuir", "Cuir pour le sellier", "objets", "minecraft:leather", "Cuir", 8, 20, 50, 120, 6,
                        "{joueur}, le sellier du château te fait demander.",
                        "Selles, ceinturons et sacoches : tout est à refaire.",
                        "Il lui faut {quantite} {nom}."),
                // ---------------- sécurité des routes
                t("zombies", "Nettoyer les routes", "tuer", "minecraft:zombie", "Zombies", 8, 15, 60, 140, 9,
                        "{joueur}, les routes ne sont plus sûres.",
                        "Depuis que la garde est réduite, les morts-vivants rôdent la nuit.",
                        "Élimine {quantite} {nom} pour protéger les voyageurs."),
                t("squelettes", "Les archers de la nuit", "tuer", "minecraft:skeleton", "Squelettes", 6, 12, 65, 150, 8,
                        "Salutations, {joueur}.",
                        "Des squelettes tirent sur les convois qui ravitaillent le château.",
                        "Abats-en {quantite} et les routes respireront."),
                t("araignees", "Chasse aux araignées", "tuer", "minecraft:spider", "Araignées", 6, 12, 55, 130, 7,
                        "{joueur}, un instant.",
                        "Des araignées envahissent les chemins autour du royaume.",
                        "Débarrasse-nous de {quantite} {nom}."),
                t("creepers", "Protéger les chantiers", "tuer", "minecraft:creeper", "Creepers", 3, 6, 70, 160, 6,
                        "{joueur}, les maçons ont peur de travailler.",
                        "Des creepers ont déjà détruit une partie des réparations.",
                        "Élimine {quantite} {nom} avant qu'ils ne recommencent."),
                t("pillards", "Pillards aux frontières", "tuer", "minecraft:pillager", "Pillards", 3, 6, 120, 250, 4,
                        "{joueur}, la situation est sérieuse.",
                        "Des pillards profitent de la faiblesse du royaume pour attaquer les fermes isolées.",
                        "Repousse-les : élimine {quantite} {nom}."),
                // ---------------- rondes (seulement si l'admin a ajouté des lieux avec /quetes lieu ajouter)
                t("ronde", "Ronde de surveillance", "ronde", "", "", 1, 1, 60, 140, 8,
                        "{joueur}, la garde manque d'hommes.",
                        "Le château aimerait savoir si tout est calme du côté de : {lieu}.",
                        "Va jusque là-bas, et le château te paiera pour cette ronde.")
        ));
        return f;
    }
}
