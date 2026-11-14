package fr.citizen.quetes;

import fr.citizen.economy.domain.AccountRef;
import fr.citizen.economy.domain.Actor;
import fr.citizen.economy.domain.TxRequest;
import fr.citizen.economy.domain.TxType;
import fr.citizen.economy.platform.EconomyRuntime;
import fr.citizen.economy.service.Economy;
import fr.citizen.economy.service.TransactionManager;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Virement Citizen Economy sur le compte personnel du joueur.
 * Cette classe n'est chargée que si citizeneconomy est installé (voir {@link Rewards}).
 * L'identifiant de transaction est celui de la quête : même rejoué après un crash,
 * le virement n'est jamais versé deux fois (idempotence de Citizen Economy).
 */
final class EconomyBridge {
    private EconomyBridge() { }

    static CompletableFuture<Void> pay(UUID player, String name, long amount, UUID txId, String description, boolean fromTreasury) {
        EconomyRuntime rt = EconomyRuntime.current();
        if (rt == null) return CompletableFuture.failedFuture(new IllegalStateException("Citizen Economy n'est pas démarré"));
        Economy eco = rt.economy();
        String desc = description.length() > 190 ? description.substring(0, 190) : description;
        return eco.players.touch(new Actor(player, name, false)).thenCompose(v -> {
            if (!fromTreasury) return created(eco, player, amount, txId, desc);
            CompletableFuture<Void> treasury = eco.db.write(c -> TransactionManager.apply(c, new TxRequest(txId, TxType.ROYAL_EXPENSE,
                    amount, AccountRef.treasury(), AccountRef.personal(player), desc, player))).thenApply(r -> null);
            // Trésor royal vide : le royaume paie quand même (création monétaire), avec un autre identifiant.
            UUID fallback = UUID.nameUUIDFromBytes((txId + ":creation").getBytes(StandardCharsets.UTF_8));
            return treasury.exceptionallyCompose(err -> created(eco, player, amount, fallback, desc));
        });
    }

    private static CompletableFuture<Void> created(Economy eco, UUID player, long amount, UUID txId, String desc) {
        return eco.db.write(c -> TransactionManager.apply(c, new TxRequest(txId, TxType.ADJUSTMENT,
                amount, AccountRef.cash(), AccountRef.personal(player), desc, player))).thenApply(r -> null);
    }
}
