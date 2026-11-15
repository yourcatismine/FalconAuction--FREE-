package com.falcon.auction.economy;

import java.util.Objects;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

final class VaultBackend implements EconomyHandler.Backend {
  private final Economy provider;

  VaultBackend() {
    this(Bukkit.getServicesManager().getRegistrations(Economy.class).stream()
        .filter(r -> r.getPlugin().getName().equalsIgnoreCase("Essentials"))
        .map(r -> r.getProvider()).findFirst()
        .orElseThrow(() -> new IllegalStateException("EssentialsX Vault provider is missing")));
  }

  VaultBackend(Economy provider) {
    this.provider = Objects.requireNonNull(provider);
  }

  public boolean change(OfflinePlayer player, double amount, String action) {
    if (!Double.isFinite(amount) || amount < 0) return false;
    if (action.equals("remove")) {
      // Some providers permit overdrafts for administrators/loan permissions.
      // An auction purchase must still be fully covered by the current wallet.
      double balance = provider.getBalance(player);
      if (!Double.isFinite(balance) || balance < amount) return false;
      EconomyResponse result = provider.withdrawPlayer(player, amount);
      if (result == null || !result.transactionSuccess()) return false;
      if (!Double.isFinite(result.balance) || result.balance < 0) {
        // An external debit may have raced the pre-check. Reject and undo this withdrawal.
        EconomyResponse refund = provider.depositPlayer(player, amount);
        if (refund == null || !refund.transactionSuccess()) {
          throw new IllegalStateException("Auction withdrawal would overdraw the wallet and refund failed for " + player.getUniqueId());
        }
        return false;
      }
      return true;
    }
    if (!action.equals("give")) return false;
    EconomyResponse result = provider.depositPlayer(player, amount);
    return result != null && result.transactionSuccess();
  }
}
