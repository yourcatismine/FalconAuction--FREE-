package com.falcon.auction.storage;

import com.falcon.auction.auction.AuctionItem;
import com.falcon.auction.auction.AuctionManager;
import com.falcon.auction.economy.EconomyHandler;
import com.falcon.auction.FalconAuction;
import com.falcon.auction.transaction.Transaction;

import java.io.*;
import java.util.*;
import java.util.logging.Level;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.*;

public class AuctionStorage {
  private final FalconAuction plugin;
  private final File auctionListingsFile, auctionTransactionsFolder, auctionPendingFolder;

  public AuctionStorage(FalconAuction plugin) {
    this.plugin = plugin;
    File folder = new File(plugin.getDataFolder(), "data/server/auctions");
    folder.mkdirs();
    auctionListingsFile = new File(folder, "listings.yml");
    auctionTransactionsFolder = new File(folder, "transactions");
    auctionTransactionsFolder.mkdirs();
    auctionPendingFolder = new File(folder, "pending");
    auctionPendingFolder.mkdirs();
  }

  private FileConfiguration loadYaml(File f) {
    try {
      YamlConfiguration c = new YamlConfiguration();
      if (f.exists()) c.load(f);
      return c;
    } catch (Exception e) {
      throw new IllegalStateException("Cannot read " + f, e);
    }
  }

  private void saveYaml(FileConfiguration c, File f) {
    try {
      AtomicFiles.save(c, f);
    } catch (Exception e) {
      throw new IllegalStateException("Cannot save " + f, e);
    }
  }

  public void saveAuctionItemAsync(AuctionItem i) {
    saveAuctionItem(i);
  }

  public void deleteAuctionItemAsync(UUID id) {
    deleteAuctionItem(id);
  }

  public void deleteAuctionItems(Collection<UUID> ids) {
    deleteAuctionItemsNow(ids);
  }

  public void addAuctionTransactionAsync(UUID id, Transaction tx) {
    addAuctionTransaction(id, tx);
  }

  public synchronized void addAuctionPendingPayment(
      UUID uuid, double amount, String buyerName, String itemName) {
    {
      File file = new File(auctionPendingFolder, uuid.toString() + ".yml");
      FileConfiguration cfg = loadYaml(file);
      String key = UUID.randomUUID().toString();
      cfg.set(key + ".amount", amount);
      cfg.set(key + ".buyer_name", buyerName);
      cfg.set(key + ".item_name", itemName);
      saveYaml(cfg, file);
    }
  }

  public synchronized List<AuctionManager.OfflineSale> settlePendingSales(
      org.bukkit.entity.Player player) {
    UUID uuid = player.getUniqueId();
    File file = new File(auctionPendingFolder, uuid + ".yml");
    List<AuctionManager.OfflineSale> sales = new ArrayList<>();
    if (!file.exists() || !player.isOnline()) return sales;
    FileConfiguration cfg = loadYaml(file);
    for (String key : new ArrayList<>(cfg.getKeys(false))) {
      var sec = cfg.getConfigurationSection(key);
      if (sec == null) continue;
      double amount = sec.getDouble("amount");
      if (!Double.isFinite(amount) || amount < 0)
        throw new IllegalStateException("Invalid pending payment for " + uuid);
      if ("paying".equals(sec.getString("payout-state")) )
        throw new IllegalStateException("Payout requires reconciliation: " + uuid + " / " + key);
      sec.set("payout-state", "paying");
      saveYaml(cfg, file);
      EconomyHandler.payPending(player, amount);
      sales.add(
          new AuctionManager.OfflineSale(
              sec.getString("buyer_name", "Unknown"),
              sec.getString("item_name", "Unknown"),
              amount));
      cfg.set(key, null);
      saveYaml(cfg, file);
    }
    return sales;
  }

  public synchronized void addAuctionTransaction(UUID playerUuid, Transaction tx) {
    {
      File file = new File(auctionTransactionsFolder, playerUuid.toString() + ".yml");
      FileConfiguration cfg = loadYaml(file);
      String key = "t_" + UUID.randomUUID();
      try {
        String itemData =
            ItemSerializationManager.itemStackArrayToBase64(
                new org.bukkit.inventory.ItemStack[] {tx.getItem()});
        cfg.set(key + ".item_data", itemData);
        cfg.set(key + ".price", tx.getPrice());
        cfg.set(key + ".buyer_name", tx.getBuyer());
        cfg.set(key + ".seller_name", tx.getSeller());
        cfg.set(key + ".timestamp", tx.getTimestamp());
        cfg.set(key + ".is_sale", tx.isSale());
        saveYaml(cfg, file);
      } catch (Exception e) {
        plugin
            .getLogger()
            .log(Level.SEVERE, "Failed to save auction transaction for " + playerUuid, e);
      }
    }
  }

  public synchronized List<Transaction> getAuctionTransactions(UUID playerUuid) {
    List<Transaction> list = new ArrayList<>();
    File file = new File(auctionTransactionsFolder, playerUuid.toString() + ".yml");
    if (!file.exists()) return list;
    FileConfiguration cfg = loadYaml(file);
    List<Map.Entry<String, Long>> entries = new ArrayList<>();
    for (String key : cfg.getKeys(false)) {
      entries.add(new AbstractMap.SimpleEntry<>(key, cfg.getLong(key + ".timestamp", 0)));
    }
    entries.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
    int count = 0;
    for (Map.Entry<String, Long> entry : entries) {
      if (count++ >= 50) break;
      ConfigurationSection sec = cfg.getConfigurationSection(entry.getKey());
      if (sec == null) continue;
      try {
        String itemData = sec.getString("item_data");
        org.bukkit.inventory.ItemStack[] items =
            ItemSerializationManager.itemStackArrayFromBase64(itemData);
        if (items.length > 0) {
          list.add(
              new Transaction(
                  items[0],
                  sec.getDouble("price"),
                  sec.getString("buyer_name"),
                  sec.getString("seller_name"),
                  sec.getLong("timestamp"),
                  sec.getBoolean("is_sale")));
        }
      } catch (Exception ignored) {
      }
    }
    return list;
  }

  public synchronized void deleteAuctionTransaction(UUID playerUuid, long timestamp, double price) {
    {
      File file = new File(auctionTransactionsFolder, playerUuid.toString() + ".yml");
      if (!file.exists()) return;
      FileConfiguration cfg = loadYaml(file);
      List<String> toRemove = new ArrayList<>();
      for (String key : cfg.getKeys(false)) {
        if (cfg.getLong(key + ".timestamp") == timestamp
            && cfg.getDouble(key + ".price") == price) {
          toRemove.add(key);
        }
      }
      for (String key : toRemove) cfg.set(key, null);
      if (!toRemove.isEmpty()) saveYaml(cfg, file);
    }
  }

  public synchronized List<AuctionItem> loadAllAuctionItems() {
    List<AuctionItem> items = new ArrayList<>();
    FileConfiguration cfg = loadYaml(auctionListingsFile);
    for (String key : cfg.getKeys(false)) {
      try {
        ConfigurationSection sec = cfg.getConfigurationSection(key);
        if (sec == null) continue;
        UUID id = UUID.fromString(sec.getString("id"));
        String seller = sec.getString("seller");
        String itemBase64 = sec.getString("item_stack");
        double price = sec.getDouble("price");
        long listedAt = sec.getLong("listed_at");
        int duration = sec.getInt("duration");
        org.bukkit.inventory.ItemStack itemStack =
            ItemSerializationManager.itemStackArrayFromBase64(itemBase64)[0];
        items.add(new AuctionItem(id, seller, itemStack, price, listedAt, duration));
      } catch (Exception ignored) {
      }
    }
    return items;
  }

  public synchronized void saveAuctionItem(AuctionItem item) {
    {
      FileConfiguration cfg = loadYaml(auctionListingsFile);
      String key = item.getId().toString();
      cfg.set(key + ".id", item.getId().toString());
      cfg.set(key + ".seller", item.getSeller());
      try {
        String itemBase64 =
            ItemSerializationManager.itemStackArrayToBase64(
                new org.bukkit.inventory.ItemStack[] {item.getItemStack()});
        cfg.set(key + ".item_stack", itemBase64);
      } catch (Exception e) {
        plugin.getLogger().log(Level.SEVERE, "Failed to serialize auction item", e);
        return;
      }
      cfg.set(key + ".price", item.getPrice());
      cfg.set(key + ".listed_at", item.getListedAt());
      cfg.set(key + ".duration", item.getDuration());
      saveYaml(cfg, auctionListingsFile);
    }
  }

  public synchronized void deleteAuctionItem(UUID itemId) {
    {
      FileConfiguration cfg = loadYaml(auctionListingsFile);
      cfg.set(itemId.toString(), null);
      saveYaml(cfg, auctionListingsFile);
    }
  }

  public synchronized void deleteAuctionItemsNow(Collection<UUID> itemIds) {
    if (itemIds == null || itemIds.isEmpty()) return;
    FileConfiguration cfg = loadYaml(auctionListingsFile);
    for (UUID itemId : itemIds) if (itemId != null) cfg.set(itemId.toString(), null);
    saveYaml(cfg, auctionListingsFile);
  }

  public synchronized void wipeAuctionTransactions(UUID playerUuid) {
    {
      File file = new File(auctionTransactionsFolder, playerUuid.toString() + ".yml");
      if (file.exists()) file.delete();
    }
  }
}
