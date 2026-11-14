package fr.citizen.quetes;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Citizen Quêtes : toutes les 1h30, un messager du château (PNJ EasyNPC) marche vers un joueur
 * choisi au hasard et lui propose une quête liée au royaume.
 *  - Accepter : le messager repart vers le château, la quête s'affiche à droite (tableau latéral).
 *  - Refuser  : le messager s'éloigne de 50 blocs puis disparaît.
 * Quête réussie : virement Citizen Economy sur le compte du joueur.
 *
 * Toute la logique tourne sur le serveur ; le menu (coffre) et le HUD (tableau latéral) sont vanilla,
 * donc compatibles PC et console (Geyser). Côté PC, le mod sert seulement à fournir les 60 skins.
 */
@Mod(CitizenQuetes.MOD_ID)
public class CitizenQuetes {
    public static final String MOD_ID = "citizenquetes";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CitizenQuetes(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(QuestCommands::register);
        NeoForge.EVENT_BUS.addListener(QuestEvents::onServerStarted);
        NeoForge.EVENT_BUS.addListener(QuestEvents::onServerStopping);
        NeoForge.EVENT_BUS.addListener(QuestEvents::onTick);
        NeoForge.EVENT_BUS.addListener(QuestEvents::onLogin);
        NeoForge.EVENT_BUS.addListener(QuestEvents::onLogout);
        NeoForge.EVENT_BUS.addListener(QuestEvents::onDeath);
        NeoForge.EVENT_BUS.addListener(QuestEvents::onInteract);
        NeoForge.EVENT_BUS.addListener(QuestEvents::onInteractSpecific);
        NeoForge.EVENT_BUS.addListener(QuestEvents::onEntityJoin);
    }
}
