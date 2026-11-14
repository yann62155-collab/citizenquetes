package fr.citizen.quetes;

import net.neoforged.fml.ModList;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Point d'entrée des récompenses : n'accède à Citizen Economy que s'il est bien installé. */
final class Rewards {
    private Rewards() { }

    static boolean economyInstalled() { return ModList.get().isLoaded("citizeneconomy"); }

    static CompletableFuture<Void> pay(UUID player, String name, long amount, UUID txId, String title) {
        if (!economyInstalled())
            return CompletableFuture.failedFuture(new IllegalStateException("Citizen Economy n'est pas installé sur le serveur"));
        try {
            return EconomyBridge.pay(player, name, amount, txId, "Récompense de quête : " + title,
                    QuestConfig.settings().payerDepuisTresorRoyal);
        } catch (Throwable t) { // version de Citizen Economy incompatible, etc.
            return CompletableFuture.failedFuture(t);
        }
    }
}
