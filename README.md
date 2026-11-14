# Citizen Quêtes (citizenquetes) — NeoForge 21.1.215 / MC 1.21.1 / Java 21

Toutes les **1h30**, un **messager du château** (PNJ EasyNPC) apparaît à une vingtaine de blocs d'un joueur choisi au hasard, marche jusqu'à lui et lui propose une quête liée au royaume (affaibli par la guerre).

- **Accepter** : le messager repart vers le château. Le joueur n'a pas besoin de le suivre. La quête s'affiche **à droite de l'écran** avec sa progression.
- **Refuser**, ou pas de réponse après 60 s : le messager s'éloigne de **50 blocs**, puis il est supprimé.
- **Quête réussie** : elle se valide toute seule. Les objets sont retirés de l'inventaire, puis un **virement Citizen Economy** arrive sur le compte du joueur. Le HUD affiche « Quête terminée ! Virement de X PO effectué sur votre compte ».

Il n'y a aucune commande à taper : tout est automatique.

## Compatibilité console (Xbox / Bedrock via Geyser)
- Le menu Accepter/Refuser est un **coffre vanilla**, et le HUD est le **tableau latéral vanilla**. Les deux s'affichent sur console.
- Les joueurs console reçoivent un **marchand ambulant** comme messager, parce que les entités EasyNPC ne sont pas visibles sur Bedrock. Réglage : `entitePnjConsole`.
- Un joueur console est reconnu par le préfixe de pseudo Geyser (`.` par défaut) ou par un UUID Floodgate.

## Installation
- **Serveur** : à mettre dans `mods/`, avec EasyNPC (`easy_npc_bundle-6.0.21-neoforge-1.21.1.jar`) et Citizen Economy.
- **Joueurs PC** : le mod doit aussi être chez eux, via Citizen Updater. C'est lui qui fournit les **60 skins** de messagers. Sans le mod, le messager s'affiche avec une texture manquante.

## Commandes
Joueurs : `/quete` (voir sa quête), `/quete abandonner`.

Admin (OP niveau 2) :
- `/quetes chateau` : le château est enregistré à ta position. **À faire en premier.**
- `/quetes lieu ajouter <nom>` : enregistre un lieu pour les quêtes de ronde, par exemple *L'Avant-garde* ou *La Tour libre*. Il faut se placer sur le lieu.
- `/quetes lieu retirer <nom>` · `/quetes lieu liste`
- `/quetes envoyer [joueur]` : envoie un messager tout de suite, pratique pour tester.
- `/quetes prochain` · `/quetes annuler <joueur>` · `/quetes recharger`

## Configuration (`config/citizenquetes/`)
- `reglages.json` : `intervalleMinutes` (90), `joueursMinimum`, `delaiReponseSecondes` (60), `distanceDepartRefus` (50), `dureeQueteMinutes` (180), `payerDepuisTresorRoyal` (false = le royaume crée l'argent ; true = payé par la trésorerie royale, avec création si elle est vide), `dimensions`, `nomsPnj`, `entitePnj`, `entitePnjConsole`…
- `quetes.json` : la liste des quêtes, de type `objets`, `tuer` ou `ronde`. Tu peux en ajouter ou en modifier, puis taper `/quetes recharger`.

Dans les dialogues, tu peux utiliser `{joueur}`, `{quantite}`, `{nom}`, `{lieu}` et `{recompense}`.

Règles du lore respectées dans les quêtes par défaut : le royaume est affaibli par la guerre, aucune saison n'est mentionnée, aucun fait officiel n'est inventé, et rien ne parle des Traqueurs.

## Compilation
Chaque push sur GitHub déclenche GitHub Actions. Le workflow télécharge le .jar de Citizen Economy (pour les virements), compile le mod et publie le .jar dans une Release.
