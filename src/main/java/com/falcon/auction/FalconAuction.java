package com.falcon.auction;

import com.falcon.auction.auction.AuctionController;
import com.falcon.auction.auction.AuctionItem;
import com.falcon.auction.player.AuctionPlayers;
import com.falcon.auction.storage.AuctionStorage;
import com.falcon.auction.gui.GUIHandler;
import com.falcon.auction.scheduler.SchedulerAdapter;
import com.falcon.auction.gui.SignInput;
import com.falcon.auction.config.ThemeSettings;

import org.bukkit.plugin.java.JavaPlugin;

public final class FalconAuction extends JavaPlugin {
  private static FalconAuction instance;
  private boolean started;
  private SchedulerAdapter scheduler;
  private AuctionStorage storage;
  private AuctionPlayers players;
  private SignInput sign;
  private AuctionController controller;
  private final ThemeSettings theme = new ThemeSettings(this);

  public static FalconAuction getInstance() {
    return instance;
  }

  public void onEnable() {
    instance = this;
    saveDefaultConfig();
    try {
      scheduler = new SchedulerAdapter(this);
      storage = new AuctionStorage(this);
      players = new AuctionPlayers(this);
      sign = new SignInput(this);
      getServer().getPluginManager().registerEvents(sign, this);
      controller = new AuctionController(this);
      controller.enable();
      started = true;
      getLogger().info("Falcon Auction enabled.");
    } catch (Exception e) {
      getLogger().log(java.util.logging.Level.SEVERE, "Falcon Auction could not start safely", e);
      getServer().getPluginManager().disablePlugin(this);
    }
  }

  public void onDisable() {
    if (started && controller != null) controller.disable();
    if (scheduler != null) scheduler.shutdown();
  }

  public java.util.List<Object[]> listingSnapshot() {
    return controller.getAuctionManager().getItems().stream()
        .map(a ->
            new Object[] {
              a.getId(),
              a.getSeller(),
              a.getItemStack().clone(),
              a.getPrice(),
              a.getListedAt(),
              a.getDuration()
            })
        .toList();
  }

  public void remapSeller(String oldName, String newName) {
    controller.getAuctionManager().remapSellerName(oldName, newName);
    for (AuctionItem a : controller.getAuctionManager().getItems()) storage.saveAuctionItem(a);
  }

  public boolean removeListing(java.util.UUID id) {
    for (AuctionItem a : controller.getAuctionManager().getItems())
      if (a.getId().equals(id)) return controller.getAuctionManager().removeItem(a);
    return false;
  }

  public void addListing(
      java.util.UUID id,
      String seller,
      org.bukkit.inventory.ItemStack item,
      double price,
      long time,
      int duration) {
    controller
        .getAuctionManager()
        .addItem(new AuctionItem(id, seller, item, price, time, duration));
  }

  public void openAdmin(org.bukkit.entity.Player p, String target) {
    if (!p.hasPermission("auction.admin")) return;
    p.setMetadata("ah-admin-view", new org.bukkit.metadata.FixedMetadataValue(this, true));
    GUIHandler.openAdminPlayerDetailsGUI(p, target, controller);
  }

  public java.util.List<Object[]> transactionSnapshot(java.util.UUID id) {
    return storage.getAuctionTransactions(id).stream()
        .map(t ->
            new Object[] {
              t.getItem().clone(),
              t.getPrice(),
              t.getBuyer(),
              t.getSeller(),
              t.getTimestamp(),
              t.isSale()
            })
        .toList();
  }

  public void deleteTransaction(String seller, String buyer, long time, double price) {
    storage.deleteAuctionTransaction(
        getServer().getOfflinePlayer(seller).getUniqueId(), time, price);
    storage.deleteAuctionTransaction(
        getServer().getOfflinePlayer(buyer).getUniqueId(), time, price);
  }

  public void wipeTransactions(java.util.UUID id) {
    storage.wipeAuctionTransactions(id);
  }

  public SchedulerAdapter getSchedulerAdapter() {
    return scheduler;
  }

  public AuctionStorage getDatabaseManager() {
    return storage;
  }

  public AuctionPlayers getPlayerDataManager() {
    return players;
  }

  public SignInput getSignInput() {
    return sign;
  }







  public ThemeSettings getThemeManager() { return theme; }
}
