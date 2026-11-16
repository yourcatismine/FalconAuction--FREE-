package com.falcon.auction.auction;

import com.falcon.auction.player.AuctionPlayerData;
import com.falcon.auction.FalconAuction;
import com.falcon.auction.util.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.inventory.ItemStack;

public class AuctionManager {
  private final AuctionController controller;
  private final List<AuctionItem> items;
  private volatile long lastUpdate = System.currentTimeMillis();

  public AuctionManager(AuctionController controller) {
    this.controller = controller;
    this.items = new CopyOnWriteArrayList<>();
  }

  public synchronized void addItem(AuctionItem item) {
    if (!Utils.isValidPrice(item.getPrice())) throw new IllegalArgumentException("Price must be greater than 0");

    FalconAuction.getInstance().getDatabaseManager().saveAuctionItemAsync(item);
    this.items.add(item);
    this.lastUpdate = System.currentTimeMillis();
  }

  public synchronized boolean removeItem(AuctionItem item) {
    if (!this.items.contains(item)) return false;
    FalconAuction.getInstance().getDatabaseManager().deleteAuctionItemAsync(item.getId());
    boolean removed = this.items.remove(item);
    if (removed) {
      this.lastUpdate = System.currentTimeMillis();
    }
    return removed && !isClaimExpired(item);
  }

  public boolean isExpired(AuctionItem item) {
    long now = System.currentTimeMillis();
    return now - item.getListedAt() >= (long) item.getDuration() * 1000L;
  }

  public List<AuctionItem> getItems() {
    return this.items.stream().filter(item -> !isClaimExpired(item)).toList();
  }

  /** Update cached seller names after an account rename. */
  public synchronized void remapSellerName(String oldName, String newName) {
    if (oldName == null
        || newName == null
        || oldName.isBlank()
        || newName.isBlank()
        || oldName.equalsIgnoreCase(newName)) return;
    for (AuctionItem item : items) {
      if (item.getSeller().equalsIgnoreCase(oldName)) {
        AuctionItem migrated =
            new AuctionItem(
                item.getId(),
                newName,
                item.getItemStack(),
                item.getPrice(),
                item.getListedAt(),
                item.getDuration());
        items.set(items.indexOf(item), migrated);
      }
    }
    this.lastUpdate = System.currentTimeMillis();
  }

  public List<AuctionItem> getActiveItems() {
    long now = System.currentTimeMillis();
    ArrayList<AuctionItem> out = new ArrayList<AuctionItem>();
    for (AuctionItem it : getItems()) {
      if (now - it.getListedAt() >= (long) it.getDuration() * 1000L) continue;
      out.add(it);
    }
    return Collections.unmodifiableList(out);
  }

  public void clearPendingSales(UUID seller) {}

  public synchronized void updatePrice(UUID auctionId, double newPrice) {
    if (!Utils.isValidPrice(newPrice)) throw new IllegalArgumentException("Price must be greater than 0");
    for (AuctionItem ai : this.items) {
      if (ai.getId().equals(auctionId) && !isClaimExpired(ai)) {
        ai.setPrice(newPrice);
        FalconAuction.getInstance().getDatabaseManager().saveAuctionItemAsync(ai);
        return;
      }
    }
  }

  public synchronized void removeAllItems(String sellerName) {
    if (sellerName == null || sellerName.isBlank()) return;
    List<AuctionItem> removed = new ArrayList<>();
    this.items.removeIf(item -> {
      if (!item.getSeller().equalsIgnoreCase(sellerName)) return false;
      removed.add(item);
      return true;
    });
    if (!removed.isEmpty()) {
      FalconAuction.getInstance()
          .getDatabaseManager()
          .deleteAuctionItems(removed.stream().map(AuctionItem::getId).toList());
      this.lastUpdate = System.currentTimeMillis();
    }
  }

  public synchronized void loadFromConfig() {
    this.items.clear();
    this.items.addAll(FalconAuction.getInstance().getDatabaseManager().loadAllAuctionItems());
    cleanupExpired();
  }

  public long getClaimDeadline(AuctionItem item) {
      return AuctionRetention.deleteAt(item.getListedAt(), item.getDuration());
  }

  public boolean isClaimExpired(AuctionItem item) {
      return AuctionRetention.isClaimExpired(item.getListedAt(), item.getDuration(), System.currentTimeMillis());
  }

  /** Runs on startup and periodically, even when no players open the auction. */
  public synchronized void cleanupExpired() {
      long now = System.currentTimeMillis();
      List<AuctionItem> expired = this.items.stream()
              .filter(item -> AuctionRetention.isClaimExpired(item.getListedAt(), item.getDuration(), now))
              .toList();
      if (expired.isEmpty()) return;
      FalconAuction.getInstance().getDatabaseManager().deleteAuctionItems(
              expired.stream().map(AuctionItem::getId).toList());
      this.items.removeAll(expired);
      this.lastUpdate = now;
  }

  public void saveToConfig() { /* Listing mutations are persisted immediately. */ }

  public int getDefaultTime() {
    return controller.getConfig().getInt("settings.item-time", 86400);
  }

  public long getLastUpdate() {
    return this.lastUpdate;
  }

  public void setPlayerSort(UUID playerUUID, String mode) {
    com.falcon.auction.FalconAuction plugin = com.falcon.auction.FalconAuction.getInstance();
    if (plugin == null || plugin.getPlayerDataManager() == null) return;
    com.falcon.auction.player.AuctionPlayerData data = plugin.getPlayerDataManager().get(playerUUID);
    if (data != null) data.setAuctionSortOrder(mode);
  }

  public String getPlayerSort(UUID playerUUID) {
    com.falcon.auction.FalconAuction plugin = com.falcon.auction.FalconAuction.getInstance();
    if (plugin == null || plugin.getPlayerDataManager() == null) return "Highest Price";
    com.falcon.auction.player.AuctionPlayerData data = plugin.getPlayerDataManager().get(playerUUID);
    if (data != null) return data.getAuctionSortOrder();
    return "Highest Price";
  }

  public void setPlayerFilter(UUID playerUUID, String filter) {
    com.falcon.auction.FalconAuction plugin = com.falcon.auction.FalconAuction.getInstance();
    if (plugin == null || plugin.getPlayerDataManager() == null) return;
    com.falcon.auction.player.AuctionPlayerData data = plugin.getPlayerDataManager().get(playerUUID);
    if (data != null) data.setAuctionFilter(filter);
  }

  public String getPlayerFilter(UUID playerUUID) {
    com.falcon.auction.FalconAuction plugin = com.falcon.auction.FalconAuction.getInstance();
    if (plugin == null || plugin.getPlayerDataManager() == null) return "";
    com.falcon.auction.player.AuctionPlayerData data = plugin.getPlayerDataManager().get(playerUUID);
    if (data != null) return data.getAuctionFilter();
    return "";
  }

  public void setPlayerCategory(UUID playerUUID, String category) {
    com.falcon.auction.FalconAuction plugin = com.falcon.auction.FalconAuction.getInstance();
    if (plugin == null || plugin.getPlayerDataManager() == null) return;
    com.falcon.auction.player.AuctionPlayerData data = plugin.getPlayerDataManager().get(playerUUID);
    if (data != null) data.setAuctionCategory(category);
  }

  public String getPlayerCategory(UUID playerUUID) {
    com.falcon.auction.FalconAuction plugin = com.falcon.auction.FalconAuction.getInstance();
    if (plugin == null || plugin.getPlayerDataManager() == null) return "All";
    com.falcon.auction.player.AuctionPlayerData data = plugin.getPlayerDataManager().get(playerUUID);
    if (data != null) return data.getAuctionCategory();
    return "All";
  }

  public ItemStack getFinalItem(AuctionItem item) { return item.getItemStack(); }

  public static class OfflineSale {
    public final String buyer;
    public final String item;
    public final double price;

    public OfflineSale(String buyer, String item, double price) {
      this.buyer = buyer;
      this.item = item;
      this.price = price;
    }
  }
}
