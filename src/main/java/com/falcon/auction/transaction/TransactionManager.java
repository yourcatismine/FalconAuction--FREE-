package com.falcon.auction.transaction;

import com.falcon.auction.auction.AuctionController;

import java.util.List;
import java.util.UUID;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;

public class TransactionManager {
  private final AuctionController controller;

  public TransactionManager(AuctionController controller) {
    this.controller = controller;
  }

  public void recordSale(ItemStack item, double price, String seller, String buyer) {
    long now = System.currentTimeMillis();
    Transaction txSeller = new Transaction(item.clone(), price, buyer, seller, now, true);
    Transaction txBuyer = new Transaction(item.clone(), price, buyer, seller, now, false);

    OfflinePlayer sellerOp = controller.getPlugin().getServer().getOfflinePlayer(seller);
    if (sellerOp != null && sellerOp.getUniqueId() != null) {
      controller
          .getPlugin()
          .getDatabaseManager()
          .addAuctionTransactionAsync(sellerOp.getUniqueId(), txSeller);
    }

    OfflinePlayer buyerOp = controller.getPlugin().getServer().getOfflinePlayer(buyer);
    if (buyerOp != null && buyerOp.getUniqueId() != null) {
      controller
          .getPlugin()
          .getDatabaseManager()
          .addAuctionTransactionAsync(buyerOp.getUniqueId(), txBuyer);
    }
  }

  public List<Transaction> getPlayerTransactions(UUID uuid) {
    if (uuid == null) return new java.util.ArrayList<>();
    return controller.getPlugin().getDatabaseManager().getAuctionTransactions(uuid);
  }

  public double getTotalSpent(UUID uuid) {
    return getTotalSpent(this.getPlayerTransactions(uuid));
  }

  public double getTotalSpent(List<Transaction> transactions) {
    double sum = 0.0;
    if (transactions == null) return sum;
    for (Transaction tx : transactions) {
      if (tx.isSale()) continue;
      sum += tx.getPrice();
    }
    return sum;
  }

  public double getTotalMade(UUID uuid) {
    return getTotalMade(this.getPlayerTransactions(uuid));
  }

  public double getTotalMade(List<Transaction> transactions) {
    double sum = 0.0;
    if (transactions == null) return sum;
    for (Transaction tx : transactions) {
      if (!tx.isSale()) continue;
      sum += tx.getPrice();
    }
    return sum;
  }

  public void loadFromConfig() {}

  public void saveToConfig() {}

  public void deleteTransaction(Transaction tx) {
    if (tx == null) return;
    OfflinePlayer sellerOp =
        this.controller.getPlugin().getServer().getOfflinePlayer(tx.getSeller());
    if (sellerOp != null && sellerOp.getUniqueId() != null) {
      this.controller
          .getPlugin()
          .getDatabaseManager()
          .deleteAuctionTransaction(sellerOp.getUniqueId(), tx.getTimestamp(), tx.getPrice());
    }
    OfflinePlayer buyerOp = this.controller.getPlugin().getServer().getOfflinePlayer(tx.getBuyer());
    if (buyerOp != null && buyerOp.getUniqueId() != null) {
      this.controller
          .getPlugin()
          .getDatabaseManager()
          .deleteAuctionTransaction(buyerOp.getUniqueId(), tx.getTimestamp(), tx.getPrice());
    }
  }

  public void wipeTransactions(UUID uuid) {
    if (uuid == null) return;
    this.controller.getPlugin().getDatabaseManager().wipeAuctionTransactions(uuid);
  }
}
