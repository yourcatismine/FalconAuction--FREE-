package com.falcon.auction.gui;

import com.falcon.auction.auction.AuctionController;
import com.falcon.auction.auction.AuctionItem;
import com.falcon.auction.auction.AuctionManager;
import com.falcon.auction.player.AuctionPlayerData;
import com.falcon.auction.economy.EconomyHandler;
import com.falcon.auction.FalconAuction;
import com.falcon.auction.transaction.Transaction;
import com.falcon.auction.util.Utils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;

public class GUIListener implements Listener {
  private final AuctionController controller;
  private final java.util.Map<UUID, List<AuctionItem>> filteredCache = new java.util.HashMap<>();
  private final java.util.Map<UUID, Long> cacheTimestamp = new java.util.HashMap<>();
  private final java.util.Map<String, List<String>> categoryCache = new java.util.HashMap<>();

  public GUIListener(AuctionController controller) {
    this.controller = controller;
  }

  @EventHandler
  public void onInventoryClick(InventoryClickEvent event) {
    if (!(event.getWhoClicked() instanceof Player)) {
      return;
    }
    Player p = (Player) event.getWhoClicked();
    FileConfiguration cfg = this.controller.getConfig();
    InventoryHolder top = event.getView().getTopInventory().getHolder();
    Sound prev = Sound.valueOf((String) cfg.getString("sounds.prev-page"));
    Sound next = Sound.valueOf((String) cfg.getString("sounds.next-page"));
    Sound refresh = Sound.valueOf((String) cfg.getString("sounds.refresh"));
    Sound def = Sound.valueOf((String) cfg.getString("sounds.default-button"));
    Sound search = Sound.valueOf((String) cfg.getString("sounds.search"));
    Sound confirm = Sound.valueOf((String) cfg.getString("sounds.confirm-sell"));
    Sound no = Sound.valueOf((String) cfg.getString("sounds.villager-no"));
    if (top instanceof GUIHandler.MainHolder) {

      if (event.getClickedInventory() == null) return;
      if (event.getClickedInventory().equals(event.getView().getTopInventory())) {
        event.setCancelled(true);
      } else {
        if (event.isShiftClick()) {
          event.setCancelled(true);
        }
        return;
      }

      int perPage = cfg.getInt("main-gui.items-per-page", 45);
      int slot = event.getRawSlot();
      int page = p.getMetadata("ah-page").stream().findFirst().map(MetadataValue::asInt).orElse(1);
      if (slot == cfg.getInt("main-gui.items.previous-page.slot")) {
        if (page > 1
            && event.getCurrentItem() != null
            && event.getCurrentItem().getType() != Material.AIR) {
          p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
          GUIHandler.openMainGUI(p, Math.max(1, page - 1), this.controller, perPage);
        }
        return;
      }
      if (slot == cfg.getInt("main-gui.items.sort.slot")) {
        p.playSound(p.getLocation(), def, 1.0f, 1.0f);
        String nextMode =
            this.getNextSortMode(
                this.controller.getAuctionManager().getPlayerSort(p.getUniqueId()));
        this.controller.getAuctionManager().setPlayerSort(p.getUniqueId(), nextMode);
        GUIHandler.openMainGUI(p, page, this.controller, perPage);
        return;
      }
      if (slot == cfg.getInt("main-gui.items.search.slot")) {
        p.playSound(p.getLocation(), Sound.BLOCK_CHERRY_WOOD_BUTTON_CLICK_ON, 0.3f, 0.6f);
        p.setMetadata("ah-switching", new FixedMetadataValue(this.controller.getPlugin(), true));
        p.closeInventory();
        ((com.falcon.auction.FalconAuction) this.controller.getPlugin())
            .getSignInput()
            .getSearchInput(
                p,
                input -> {
                  String term = input.trim().toLowerCase();
                  p.setMetadata(
                      "ah-filter", new FixedMetadataValue(this.controller.getPlugin(), term));
                  this.controller.getAuctionManager().setPlayerFilter(p.getUniqueId(), term);
                  GUIHandler.openMainGUI(p, 1, this.controller, perPage);
                });
        return;
      }
      if (slot == 48) {
        p.playSound(p.getLocation(), Sound.UI_LOOM_SELECT_PATTERN, 1.0f, 1.0f);
        String current =
            p.hasMetadata("ah-cat")
                ? ((MetadataValue) p.getMetadata("ah-cat").get(0)).asString()
                : "All";
        String nextCat = GUIHandler.getNextCategory(this.controller, current);
        p.setMetadata(
            "ah-cat",
            (MetadataValue)
                new FixedMetadataValue((Plugin) this.controller.getPlugin(), (Object) nextCat));
        this.controller.getAuctionManager().setPlayerCategory(p.getUniqueId(), nextCat);
        GUIHandler.openMainGUI(p, page, this.controller, perPage);
        return;
      }
      if (slot == cfg.getInt("main-gui.items.refresh.slot")) {
        p.playSound(p.getLocation(), refresh, 1.0f, 1.0f);
        GUIHandler.openMainGUI(p, page, this.controller, perPage);
        return;
      }
      if (slot == cfg.getInt("main-gui.items.your-items.slot")) {
        p.playSound(p.getLocation(), Sound.BLOCK_CHERRY_WOOD_BUTTON_CLICK_ON, 0.3f, 0.6f);
        GUIHandler.openYourItemsGUI(p, this.controller);
        return;
      }
      if (slot == cfg.getInt("main-gui.items.next-page.slot")) {
        if (event.getCurrentItem() != null && event.getCurrentItem().getType() != Material.AIR) {
          p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
          GUIHandler.openMainGUI(p, page + 1, this.controller, perPage);
        }
        return;
      }

      if (slot >= 0 && slot < perPage) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) {
          return;
        }

        ItemMeta clickedMeta = clickedItem.getItemMeta();
        org.bukkit.persistence.PersistentDataContainer pdc =
            clickedMeta.getPersistentDataContainer();
        org.bukkit.NamespacedKey itemIdKey =
            new org.bukkit.NamespacedKey(this.controller.getPlugin(), "auction-item-id");

        if (!pdc.has(itemIdKey, org.bukkit.persistence.PersistentDataType.STRING)) {
          return;
        }

        String auctionItemId = pdc.get(itemIdKey, org.bukkit.persistence.PersistentDataType.STRING);

        Optional<AuctionItem> optionalItem =
            this.controller.getAuctionManager().getActiveItems().stream()
                .filter(ai -> ai.getId().toString().equals(auctionItemId))
                .findFirst();

        if (!optionalItem.isPresent()) {
          p.sendMessage(
              Utils.formatColors(
                  this.controller
                      .getConfig()
                      .getString(
                          "messages.item-not-available", "&cThis item is no longer available!")));
          p.playSound(p.getLocation(), no, 1.0f, 1.0f);
          GUIHandler.openMainGUI(p, page, this.controller, perPage);
          return;
        }

        p.playSound(p.getLocation(), def, 1.0f, 1.0f);
        AuctionItem ai2 = optionalItem.get();
        if (p.hasMetadata("ah-admin-view")) {
          GUIHandler.openAdminPlayerDetailsGUI(p, ai2.getSeller(), this.controller);
          return;
        }
        if (ai2.getSeller().equals(p.getName())) {
          p.playSound(p.getLocation(), no, 1.0f, 1.0f);
        } else {
          com.falcon.auction.FalconAuction plugin =
              (com.falcon.auction.FalconAuction) this.controller.getPlugin();
          com.falcon.auction.player.AuctionPlayerData data =
              plugin.getPlayerDataManager().get(p.getUniqueId());
          boolean quickBuy = data != null && data.isQuickAuctionBuy();
          boolean hasPerm = p.hasPermission("auction.quickbuy");

          if (quickBuy && hasPerm) {
            purchaseItem(p, ai2);
          } else {
            GUIHandler.openBuyConfirm(p, ai2, this.controller);
          }
        }
      }
      return;
    }
    if (top instanceof GUIHandler.FilterHolder) {

      if (event.getClickedInventory() == null) return;
      if (event.getClickedInventory().equals(event.getView().getTopInventory())) {
        event.setCancelled(true);
      } else {
        if (event.isShiftClick()) {
          event.setCancelled(true);
        }
        return;
      }

      if (event.getCurrentItem() == null) {
        return;
      }
      String chosen =
          ChatColor.stripColor((String) event.getCurrentItem().getItemMeta().getDisplayName());
      p.setMetadata(
          "ah-cat",
          (MetadataValue)
              new FixedMetadataValue((Plugin) this.controller.getPlugin(), (Object) chosen));
      this.controller.getAuctionManager().setPlayerCategory(p.getUniqueId(), chosen);
      GUIHandler.openMainGUI(p, 1, this.controller, GUIHandler.ITEMS_PER_PAGE);
      return;
    }
    if (top instanceof GUIHandler.SellConfirmHolder) {

      if (event.getClickedInventory() == null) return;
      if (event.getClickedInventory().equals(event.getView().getTopInventory())) {
        event.setCancelled(true);
      } else {
        if (event.isShiftClick()) {
          event.setCancelled(true);
        }
        return;
      }

      int slot = event.getRawSlot();
      if (slot == this.controller.getConfig().getInt("sell-confirm-gui.decline-button.slot")) {
        p.playSound(p.getLocation(), def, 1.0f, 1.0f);
        p.closeInventory();
        return;
      }
      if (slot == this.controller.getConfig().getInt("sell-confirm-gui.confirm-button.slot")) {
        p.playSound(p.getLocation(), confirm, 1.0f, 1.0f);
        List<MetadataValue> pm = p.getMetadata("ah-sell-price");
        double price = pm.isEmpty() ? 0.0 : ((MetadataValue) pm.get(0)).asDouble();
        if (!Utils.isValidPrice(price)) {
          p.sendMessage(Utils.formatColors(controller.getMessage("invalid-price", "&cPrice must be greater than 0.")));
          p.closeInventory();
          return;
        }
        ItemStack held = p.getInventory().getItemInMainHand();
        if (held == null || held.getType() == Material.AIR) {
          p.sendMessage(
              Utils.formatColors(
                  this.controller.getConfig().getString("messages.item-not-available")));
          p.playSound(p.getLocation(), no, 1.0f, 1.0f);
          p.closeInventory();
          return;
        }
        ItemStack toSell = held.clone();

        p.getInventory().setItemInMainHand(null);

        AuctionItem auctionItem =
            new AuctionItem(
                UUID.randomUUID(),
                p.getName(),
                toSell,
                price,
                System.currentTimeMillis(),
                this.controller.getAuctionManager().getDefaultTime());
        this.controller.getAuctionManager().addItem(auctionItem);


        String itemName = Utils.prettifyMaterialName(held.getType());
        String dateTime =
            LocalDateTime.now(ZoneId.of("UTC"))
                .format(DateTimeFormatter.ofPattern("dd/MM HH:mm:ss"));
        com.falcon.auction.player.AuctionPlayerData pdListing =
            ((com.falcon.auction.FalconAuction) this.controller.getPlugin())
                .getPlayerDataManager()
                .get(p.getUniqueId());
        if (pdListing != null) {
          pdListing.addHistory(
              dateTime
                  + " - AH Listing\nListed "
                  + itemName
                  + " x"
                  + held.getAmount()
                  + " for $"
                  + Utils.formatNumber(price));
          ((com.falcon.auction.FalconAuction) this.controller.getPlugin())
              .getPlayerDataManager()
              .savePlayerAsync(p.getUniqueId());
        }

        p.sendMessage(
            Utils.formatColors(
                this.controller
                    .getConfig()
                    .getString(
                        "messages.listed-item",
                        "&#34ee80Your item has been listed on the auction house!")
                    .replace("{item}", itemName)
                    .replace("{priceFormatted}", Utils.formatNumber(price))));
        p.closeInventory();
      }
      return;
    }
    if (top instanceof GUIHandler.BuyConfirmHolder) {

      if (event.getClickedInventory() == null) return;
      if (event.getClickedInventory().equals(event.getView().getTopInventory())) {
        event.setCancelled(true);
      } else {
        if (event.isShiftClick()) {
          event.setCancelled(true);
        }
        return;
      }

      int slot = event.getRawSlot();
      if (slot == this.controller.getConfig().getInt("purchase-confirm-gui.decline-button.slot")) {
        p.playSound(p.getLocation(), def, 1.0f, 1.0f);
        int page =
            p.getMetadata("ah-page").stream().findFirst().map(MetadataValue::asInt).orElse(1);
        GUIHandler.openMainGUI(p, page, this.controller, GUIHandler.ITEMS_PER_PAGE);
        return;
      }
      if (slot == this.controller.getConfig().getInt("purchase-confirm-gui.confirm-button.slot")) {
        p.playSound(p.getLocation(), def, 1.0f, 1.0f);
        List<MetadataValue> bm = p.getMetadata("ah-buy-item");
        if (bm.isEmpty()) {
          return;
        }
        Optional<AuctionItem> opt =
            this.controller.getAuctionManager().getItems().stream()
                .filter(ai -> ai.getId().toString().equals(((MetadataValue) bm.get(0)).asString()))
                .findFirst();
        if (!opt.isPresent()) {
          p.sendMessage(
              Utils.formatColors(
                  this.controller.getConfig().getString("messages.item-not-available")));
          p.playSound(p.getLocation(), no, 1.0f, 1.0f);
          p.closeInventory();
          return;
        }
        AuctionItem ai4 = opt.get();

        purchaseItem(p, ai4);
      }
      return;
    }
    if (top instanceof GUIHandler.YourItemsHolder) {

      if (event.getClickedInventory() == null)
        return;
      if (event.getClickedInventory().equals(event.getView().getTopInventory())) {
        event.setCancelled(true);
      } else {
        if (event.isShiftClick()) {
          event.setCancelled(true);
        }
        return;
      }

      int slot = event.getRawSlot();
      if (slot == this.controller.getConfig().getInt("your-items-gui.back-button.slot")) {
        p.playSound(p.getLocation(), def, 1.0f, 1.0f);
        GUIHandler.openMainGUI(p, 1, this.controller, GUIHandler.ITEMS_PER_PAGE);
        return;
      }
      if (slot == this.controller.getConfig().getInt("your-items-gui.transactions-button.slot")) {
        p.playSound(p.getLocation(), def, 1.0f, 1.0f);
        GUIHandler.openTransactionsGUI(p, 1, this.controller);
        return;
      }
      if (slot >= 0 && slot < 18 || slot >= 19 && slot < 26) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String listingId = clicked.getItemMeta().getPersistentDataContainer().get(
            new org.bukkit.NamespacedKey(controller.getPlugin(), "auction-item-id"),
            org.bukkit.persistence.PersistentDataType.STRING);
        Optional<AuctionItem> listing = controller.getAuctionManager().getItems().stream()
            .filter(ai -> ai.getSeller().equals(p.getName()) && ai.getId().toString().equals(listingId))
            .findFirst();
        if (listing.isEmpty()) {
          p.sendMessage(Utils.formatColors("&cThis item is no longer available."));
          GUIHandler.openYourItemsGUI(p, controller);
          return;
        }
        p.playSound(p.getLocation(), def, 1.0f, 1.0f);
        AuctionItem ai5 = listing.get();
        if (p.getInventory().firstEmpty() == -1) {
          p.sendMessage(
              Utils.formatColors(this.controller.getConfig().getString("messages.inventory-full")));
          p.playSound(p.getLocation(), no, 1.0f, 1.0f);
          return;
        }
        if (!this.controller.getAuctionManager().removeItem(ai5)) {
          p.sendMessage(Utils.formatColors("&cThis item is no longer available."));
          GUIHandler.openYourItemsGUI(p, controller);
          return;
        }
        ItemStack finalItem = this.controller.getAuctionManager().getFinalItem(ai5);
        p.getInventory().addItem(new ItemStack[] { finalItem });
        p.sendMessage(Utils.formatColors(this.controller.getConfig().getString("messages.returned-item")));
        GUIHandler.openYourItemsGUI(p, this.controller);
      }
      return;
    }
    if (top instanceof GUIHandler.TransactionsHolder) {

      if (event.getClickedInventory() == null) return;
      if (event.getClickedInventory().equals(event.getView().getTopInventory())) {
        event.setCancelled(true);
      } else {
        if (event.isShiftClick()) {
          event.setCancelled(true);
        }
        return;
      }

      int slot = event.getRawSlot();
      int page = p.getMetadata("tx-page").stream().findFirst().map(MetadataValue::asInt).orElse(1);
      if (slot == cfg.getInt("transactions-gui.items.previous-page.slot")) {
        if (page > 1
            && event.getCurrentItem() != null
            && event.getCurrentItem().getType() != Material.AIR) {
          p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
          GUIHandler.openTransactionsGUI(p, Math.max(1, page - 1), this.controller);
        }
        return;
      }
      if (slot == cfg.getInt("transactions-gui.items.refresh.slot")) {
        p.playSound(p.getLocation(), refresh, 1.0f, 1.0f);
        p.removeMetadata("tx-filter", (Plugin) this.controller.getPlugin());
        p.removeMetadata("awaiting-tx-search", (Plugin) this.controller.getPlugin());
        GUIHandler.openTransactionsGUI(p, 1, this.controller);
        return;
      }
      if (slot == cfg.getInt("transactions-gui.items.search.slot")) {
        p.playSound(p.getLocation(), Sound.BLOCK_CHERRY_WOOD_BUTTON_CLICK_ON, 0.3f, 0.6f);
        p.setMetadata("ah-switching", new FixedMetadataValue(this.controller.getPlugin(), true));
        p.closeInventory();
        ((com.falcon.auction.FalconAuction) this.controller.getPlugin())
            .getSignInput()
            .getSearchInput(
                p,
                input -> {
                  String term = input.trim().toLowerCase();
                  p.setMetadata(
                      "tx-filter", new FixedMetadataValue(this.controller.getPlugin(), term));
                  GUIHandler.openTransactionsGUI(p, 1, this.controller);
                });
        return;
      }
      if (slot == cfg.getInt("transactions-gui.items.next-page.slot")) {
        if (event.getCurrentItem() != null && event.getCurrentItem().getType() != Material.AIR) {
          p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
          GUIHandler.openTransactionsGUI(p, page + 1, this.controller);
        }
        return;
      }
    }
    if (top instanceof GUIHandler.AdminPlayerDetailsHolder) {

      if (event.getClickedInventory() == null) return;
      if (event.getClickedInventory().equals(event.getView().getTopInventory())) {
        event.setCancelled(true);
      } else {
        if (event.isShiftClick()) {
          event.setCancelled(true);
        }
        return;
      }

      int slot = event.getRawSlot();
      if (slot >= 0 && slot < 27) {
        if (event.getCurrentItem() != null && event.getCurrentItem().getType() != Material.AIR) {
          String target = p.getMetadata("ah-admin-target").get(0).asString();
          List<AuctionItem> items =
              this.controller.getAuctionManager().getItems().stream()
                  .filter(ai -> ai.getSeller().equalsIgnoreCase(target))
                  .collect(Collectors.toList());
          if (slot < items.size()) {
            AuctionItem ai = items.get(slot);
            p.playSound(p.getLocation(), def, 1.0f, 1.0f);
            GUIHandler.openItemManagementGUI(p, ai, this.controller);
          }
        }
        return;
      }
      if (slot == 48) {
        String target = p.getMetadata("ah-admin-target").get(0).asString();
        GUIHandler.openAdminDeleteConfirmGUI(p, target, this.controller);
        return;
      }
      if (slot == 49) {
        p.playSound(p.getLocation(), Sound.BLOCK_CHERRY_WOOD_BUTTON_CLICK_ON, 0.3f, 0.6f);
        p.setMetadata("ah-switching", new FixedMetadataValue(this.controller.getPlugin(), true));
        p.closeInventory();
        ((com.falcon.auction.FalconAuction) this.controller.getPlugin())
            .getSignInput()
            .getSearchInput(
                p,
                input -> {
                  String term = input.trim().toLowerCase();
                  p.setMetadata(
                      "ah-filter", new FixedMetadataValue(this.controller.getPlugin(), term));
                  GUIHandler.openMainGUI(p, 1, this.controller, GUIHandler.ITEMS_PER_PAGE);
                });
        return;
      }
      if (slot == 50) {
        p.playSound(p.getLocation(), def, 1.0f, 1.0f);
        String target = p.getMetadata("ah-admin-target").get(0).asString();
        p.removeMetadata("tx-filter", (Plugin) this.controller.getPlugin());
        GUIHandler.openAdminTransactionsGUI(p, Bukkit.getOfflinePlayer(target), 1, this.controller);
        return;
      }
      if (slot == 45) {
        p.playSound(p.getLocation(), def, 1.0f, 1.0f);
        GUIHandler.openAdminPlayerListGUI(p, 1, this.controller);
        return;
      }
    }
    if (top instanceof GUIHandler.AdminPlayerListHolder) {

      if (event.getClickedInventory() == null) return;
      if (event.getClickedInventory().equals(event.getView().getTopInventory())) {
        event.setCancelled(true);
      } else {
        if (event.isShiftClick()) {
          event.setCancelled(true);
        }
        return;
      }

      int slot = event.getRawSlot();
      int page =
          p.getMetadata("ah-admin-list-page").stream()
              .findFirst()
              .map(MetadataValue::asInt)
              .orElse(1);

      if (slot == 45 && page > 1) {
        if (event.getCurrentItem() != null && event.getCurrentItem().getType() != Material.AIR) {
          p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
          GUIHandler.openAdminPlayerListGUI(p, page - 1, this.controller);
        }
        return;
      }
      if (slot == 48) {
        p.playSound(p.getLocation(), Sound.BLOCK_CHERRY_WOOD_BUTTON_CLICK_ON, 0.3f, 0.6f);
        p.setMetadata("ah-switching", new FixedMetadataValue(this.controller.getPlugin(), true));
        p.closeInventory();
        ((com.falcon.auction.FalconAuction) this.controller.getPlugin())
            .getSignInput()
            .getSearchInput(
                p,
                input -> {
                  String term = input.trim().toLowerCase();
                  p.setMetadata(
                      "ah-admin-player-filter",
                      new FixedMetadataValue(this.controller.getPlugin(), term));
                  GUIHandler.openAdminPlayerListGUI(p, 1, this.controller);
                });
        return;
      }
      if (slot == 49) {
        p.playSound(p.getLocation(), refresh, 1.0f, 1.0f);
        GUIHandler.openAdminPlayerListGUI(p, page, this.controller);
        return;
      }
      if (slot == 53) {
        if (event.getCurrentItem() != null && event.getCurrentItem().getType() != Material.AIR) {
          p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
          GUIHandler.openAdminPlayerListGUI(p, page + 1, this.controller);
        }
        return;
      }

      if (slot < 45
          && event.getCurrentItem() != null
          && event.getCurrentItem().getType() == Material.PLAYER_HEAD) {
        ItemMeta meta = event.getCurrentItem().getItemMeta();
        org.bukkit.persistence.PersistentDataContainer pdc = meta.getPersistentDataContainer();
        org.bukkit.NamespacedKey key =
            new org.bukkit.NamespacedKey(this.controller.getPlugin(), "admin-head-target");
        if (pdc.has(key, org.bukkit.persistence.PersistentDataType.STRING)) {
          String target = pdc.get(key, org.bukkit.persistence.PersistentDataType.STRING);
          p.playSound(p.getLocation(), def, 1.0f, 1.0f);
          GUIHandler.openAdminPlayerDetailsGUI(p, target, this.controller);
        }
      }
      return;
    }

    if (top instanceof GUIHandler.AdminTransactionsHolder) {

      if (event.getClickedInventory() == null) return;
      if (event.getClickedInventory().equals(event.getView().getTopInventory())) {
        event.setCancelled(true);
      } else {
        if (event.isShiftClick()) {
          event.setCancelled(true);
        }
        return;
      }

      int slot = event.getRawSlot();
      int page = p.getMetadata("tx-page").stream().findFirst().map(MetadataValue::asInt).orElse(1);
      String target = p.getMetadata("ah-admin-target").get(0).asString();

      if (slot == 45) {
        if (page > 1) {
          if (event.getCurrentItem() != null && event.getCurrentItem().getType() != Material.AIR) {
            p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
            GUIHandler.openAdminTransactionsGUI(
                p, Bukkit.getOfflinePlayer(target), page - 1, this.controller);
          }
        } else {
          p.playSound(p.getLocation(), def, 1.0f, 1.0f);
          GUIHandler.openAdminPlayerDetailsGUI(p, target, this.controller);
        }
        return;
      }
      if (slot == 49) {
        p.playSound(p.getLocation(), refresh, 1.0f, 1.0f);
        p.removeMetadata("tx-filter", (Plugin) this.controller.getPlugin());
        GUIHandler.openAdminTransactionsGUI(
            p, Bukkit.getOfflinePlayer(target), page, this.controller);
        return;
      }
      if (slot == 50) {
        p.playSound(p.getLocation(), Sound.BLOCK_CHERRY_WOOD_BUTTON_CLICK_ON, 0.3f, 0.6f);
        p.setMetadata("ah-switching", new FixedMetadataValue(this.controller.getPlugin(), true));
        p.closeInventory();
        ((com.falcon.auction.FalconAuction) this.controller.getPlugin())
            .getSignInput()
            .getSearchInput(
                p,
                input -> {
                  if (!p.hasMetadata("ah-admin-target")) return;
                  String targetName = p.getMetadata("ah-admin-target").get(0).asString();
                  String term = input.trim().toLowerCase();
                  p.setMetadata(
                      "tx-filter", new FixedMetadataValue(this.controller.getPlugin(), term));
                  GUIHandler.openAdminTransactionsGUI(
                      p, Bukkit.getOfflinePlayer(targetName), 1, this.controller);
                });
        return;
      }
      if (slot == 53) {
        if (event.getCurrentItem() != null && event.getCurrentItem().getType() != Material.AIR) {
          p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
          GUIHandler.openAdminTransactionsGUI(
              p, Bukkit.getOfflinePlayer(target), page + 1, this.controller);
        }
        return;
      }

      int perPage = cfg.getInt("transactions-gui.items-per-page", 45);
      if (slot >= 0 && slot < perPage) {
        p.playSound(p.getLocation(), def, 1.0f, 1.0f);
        CompletableFuture.runAsync(() -> {
          List<Transaction> fullTx =
              this.controller
                  .getTransactionManager()
                  .getPlayerTransactions(Bukkit.getOfflinePlayer(target).getUniqueId());

          String txFilter =
              p.hasMetadata("tx-filter")
                  ? p.getMetadata("tx-filter").get(0).asString().toLowerCase()
                  : "";

          List<Transaction> filtered =
              fullTx.stream()
                  .filter(tx -> {
                    if (txFilter.isEmpty()) return true;
                    String itemName =
                        Utils.prettifyMaterialName(tx.getItem().getType()).toLowerCase();
                    String buyer = tx.getBuyer().toLowerCase();
                    String seller = tx.getSeller().toLowerCase();
                    String priceStr = String.valueOf(tx.getPrice());
                    String priceFmt = Utils.formatNumber(tx.getPrice()).toLowerCase();

                    return itemName.contains(txFilter)
                        || buyer.contains(txFilter)
                        || seller.contains(txFilter)
                        || priceStr.contains(txFilter)
                        || priceFmt.contains(txFilter);
                  })
                  .collect(Collectors.toList());

          int idx = (page - 1) * perPage + slot;
          if (idx < filtered.size()) {
            Transaction tx = filtered.get(idx);
            this.controller
                .getPlugin()
                .getSchedulerAdapter()
                .runTask(() -> {
                  if (!p.isOnline()) return;
                  GUIHandler.openTransactionManagementGUI(p, tx, this.controller);
                });
          }
        });
      }
    }

    if (top instanceof GUIHandler.TransactionManagementHolder) {
      if (event.getClickedInventory() == null) return;
      if (event.getClickedInventory().equals(event.getView().getTopInventory())) {
        event.setCancelled(true);
      } else {
        if (event.isShiftClick()) event.setCancelled(true);
        return;
      }

      int slot = event.getRawSlot();
      if (!p.hasMetadata("ah-admin-tx-focus") || !p.hasMetadata("ah-admin-target")) {
        p.closeInventory();
        return;
      }
      long timestamp = p.getMetadata("ah-admin-tx-focus").get(0).asLong();
      String targetName = p.getMetadata("ah-admin-target").get(0).asString();
      UUID targetUuid = Bukkit.getOfflinePlayer(targetName).getUniqueId();

      if (slot == 11) {
        Optional<Transaction> opt =
            this.controller.getTransactionManager().getPlayerTransactions(targetUuid).stream()
                .filter(tx -> tx.getTimestamp() == timestamp)
                .findFirst();
        if (opt.isPresent()) {
          Transaction tx = opt.get();
          this.controller.getTransactionManager().deleteTransaction(tx);
          p.sendMessage(
              Utils.formatColors("&#34ee80Transaction record deleted and stats updated."));
          p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1.0f, 1.0f);
          GUIHandler.openAdminTransactionsGUI(
              p, Bukkit.getOfflinePlayer(targetName), 1, this.controller);
        }
        return;
      }

      if (slot == 15) {
        Optional<Transaction> opt =
            this.controller.getTransactionManager().getPlayerTransactions(targetUuid).stream()
                .filter(tx -> tx.getTimestamp() == timestamp)
                .findFirst();
        if (opt.isPresent()) {
          Transaction tx = opt.get();
          if (p.getInventory().firstEmpty() == -1) {
            p.sendMessage(Utils.formatColors("&#ff4444Inventory is full!"));
            p.playSound(p.getLocation(), no, 1.0f, 1.0f);
            return;
          }
          p.getInventory().addItem(new ItemStack[] {tx.getItem().clone()});
          p.sendMessage(Utils.formatColors("&#34ee80Received a copy of the transacted item."));
          p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
        }
        return;
      }

      if (slot == 22) {
        p.playSound(p.getLocation(), def, 1.0f, 1.0f);
        GUIHandler.openAdminTransactionsGUI(
            p, Bukkit.getOfflinePlayer(targetName), 1, this.controller);
        return;
      }
    }
    if (top instanceof GUIHandler.ItemManagementHolder) {

      if (event.getClickedInventory() == null) return;
      if (event.getClickedInventory().equals(event.getView().getTopInventory())) {
        event.setCancelled(true);
      } else {
        if (event.isShiftClick()) {
          event.setCancelled(true);
        }
        return;
      }

      int slot = event.getRawSlot();
      if (!p.hasMetadata("ah-manage-item")) {
        p.closeInventory();
        return;
      }
      String itemId = p.getMetadata("ah-manage-item").get(0).asString();

      if (slot == 11) {
        p.playSound(p.getLocation(), def, 1.0f, 1.0f);
        p.setMetadata("ah-switching", new FixedMetadataValue(this.controller.getPlugin(), true));
        p.closeInventory();
        p.setMetadata(
            "ah-price-edit-id", new FixedMetadataValue(this.controller.getPlugin(), itemId));
        ((com.falcon.auction.FalconAuction) this.controller.getPlugin())
            .getSignInput()
            .getSearchInput(
                p,
                input -> {
                  String term = input.trim().toLowerCase();
                  if (!p.hasMetadata("ah-price-edit-id")) return;
                  String idStr = p.getMetadata("ah-price-edit-id").get(0).asString();
                  p.removeMetadata("ah-price-edit-id", this.controller.getPlugin());

                  double newPrice = Utils.parsePrice(term);
                  if (!Utils.isValidPrice(newPrice)) {
                    p.sendMessage(
                        Utils.formatColors("&#ff4444Price must be greater than 0. Use numbers or k/m/b/t."));
                  } else if (newPrice > 1_000_000_000_000.0) {
                    p.sendMessage(Utils.formatColors("&#ff4444Price cannot exceed 1T!"));
                  } else {
                    try {
                      UUID auctionId = UUID.fromString(idStr);
                      this.controller.getAuctionManager().updatePrice(auctionId, newPrice);
                      p.sendMessage(Utils.formatColors("&#34ee80Price updated!"));
                    } catch (Exception e) {
                    }
                  }

                  if (p.hasMetadata("ah-admin-target")) {
                    String target = p.getMetadata("ah-admin-target").get(0).asString();
                    GUIHandler.openAdminPlayerDetailsGUI(p, target, this.controller);
                  } else {
                    GUIHandler.openMainGUI(p, 1, this.controller, GUIHandler.ITEMS_PER_PAGE);
                  }
                });
        return;
      }

      if (slot == 12) {
        Optional<AuctionItem> opt =
            this.controller.getAuctionManager().getItems().stream()
                .filter(ai -> ai.getId().toString().equals(itemId))
                .findFirst();
        if (opt.isPresent()) {
          AuctionItem item = opt.get();
          if (p.getInventory().firstEmpty() == -1) {
            p.sendMessage(Utils.formatColors("&#ff4444Inventory is full!"));
            p.playSound(p.getLocation(), no, 1.0f, 1.0f);
            return;
          }
          if (!this.controller.getAuctionManager().removeItem(item)) {
            p.sendMessage(Utils.formatColors("&cThis item is no longer available."));
            p.closeInventory();
            return;
          }
          ItemStack finalItem = this.controller.getAuctionManager().getFinalItem(item);
          p.getInventory().addItem(finalItem);
          p.sendMessage(Utils.formatColors("&#34ee80Item taken from auction!"));
          p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
        }
        if (p.hasMetadata("ah-admin-target")) {
          String target = p.getMetadata("ah-admin-target").get(0).asString();
          GUIHandler.openAdminPlayerDetailsGUI(p, target, this.controller);
        } else {
          GUIHandler.openMainGUI(p, 1, this.controller, GUIHandler.ITEMS_PER_PAGE);
        }
        return;
      }

      if (slot == 14) {
        Optional<AuctionItem> opt =
            this.controller.getAuctionManager().getItems().stream()
                .filter(ai -> ai.getId().toString().equals(itemId))
                .findFirst();
        if (opt.isPresent()) {
          AuctionItem item = opt.get();
          if (p.getInventory().firstEmpty() == -1) {
            p.sendMessage(Utils.formatColors("&#ff4444Inventory is full!"));
            p.playSound(p.getLocation(), no, 1.0f, 1.0f);
            return;
          }
          ItemStack finalItem = this.controller.getAuctionManager().getFinalItem(item);
          p.getInventory().addItem(finalItem);
          p.sendMessage(Utils.formatColors("&#34ee80Item copied from auction!"));
          p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
        }
        if (p.hasMetadata("ah-admin-target")) {
          String target = p.getMetadata("ah-admin-target").get(0).asString();
          GUIHandler.openAdminPlayerDetailsGUI(p, target, this.controller);
        } else {
          GUIHandler.openMainGUI(p, 1, this.controller, GUIHandler.ITEMS_PER_PAGE);
        }
        return;
      }

      if (slot == 15) {
        Optional<AuctionItem> opt =
            this.controller.getAuctionManager().getItems().stream()
                .filter(ai -> ai.getId().toString().equals(itemId))
                .findFirst();
        if (opt.isPresent()) {
          AuctionItem item = opt.get();
          this.controller.getAuctionManager().removeItem(item);
          try {
            p.playSound(p.getLocation(), Sound.UI_STONECUTTER_TAKE_RESULT, 1.0f, 1.0f);
          } catch (Exception e) {
            p.playSound(p.getLocation(), def, 1.0f, 1.0f);
          }
        }
        if (p.hasMetadata("ah-admin-target")) {
          String target = p.getMetadata("ah-admin-target").get(0).asString();
          GUIHandler.openAdminPlayerDetailsGUI(p, target, this.controller);
        } else {
          GUIHandler.openMainGUI(p, 1, this.controller, GUIHandler.ITEMS_PER_PAGE);
        }
        return;
      }
    }

    if (top instanceof GUIHandler.AdminDeleteConfirmHolder) {

      if (event.getClickedInventory() == null) return;
      if (event.getClickedInventory().equals(event.getView().getTopInventory())) {
        event.setCancelled(true);
      } else {
        if (event.isShiftClick()) {
          event.setCancelled(true);
        }
        return;
      }

      int slot = event.getRawSlot();
      String target = p.getMetadata("ah-admin-target").get(0).asString();

      if (slot == 11) {
        p.playSound(p.getLocation(), def, 1.0f, 1.0f);
        GUIHandler.openAdminPlayerDetailsGUI(p, target, this.controller);
        return;
      }
      if (slot == 15) {
        this.controller.getAuctionManager().removeAllItems(target);
        try {
          p.playSound(p.getLocation(), Sound.UI_STONECUTTER_TAKE_RESULT, 1.0f, 1.0f);
        } catch (Exception e) {
          p.playSound(p.getLocation(), def, 1.0f, 1.0f);
        }
        GUIHandler.openAdminPlayerDetailsGUI(p, target, this.controller);
        return;
      }
    }
  }

  @EventHandler
  public void onInventoryClose(InventoryCloseEvent event) {
    if (!(event.getPlayer() instanceof Player)) {
      return;
    }
    Player p = (Player) event.getPlayer();
    InventoryHolder top = event.getView().getTopInventory().getHolder();

    if (top instanceof GUIHandler.AdminDeleteConfirmHolder) {
      if (!p.hasMetadata("ah-switching")) {
        if (p.hasMetadata("ah-admin-target")) {
          String target = p.getMetadata("ah-admin-target").get(0).asString();
          this.controller
              .getPlugin()
              .getSchedulerAdapter()
              .runTask(() -> {
                GUIHandler.openAdminPlayerDetailsGUI(p, target, this.controller);
              });
        }
      }
    }

    if (top instanceof GUIHandler.ItemManagementHolder) {
      if (!p.hasMetadata("ah-switching")) {
        if (p.hasMetadata("ah-admin-target")) {
          String target = p.getMetadata("ah-admin-target").get(0).asString();
          this.controller
              .getPlugin()
              .getSchedulerAdapter()
              .runTask(() -> {
                GUIHandler.openAdminPlayerDetailsGUI(p, target, this.controller);
              });
        }
      }
    }

    this.controller.stopUpdateTask(p);

    if (p.hasMetadata("ah-switching")) {
      p.removeMetadata("ah-switching", (Plugin) this.controller.getPlugin());
      return;
    }

    if (top instanceof GUIHandler.MainHolder) {
      p.removeMetadata("ah-filter", (Plugin) this.controller.getPlugin());
      this.controller.getAuctionManager().setPlayerFilter(p.getUniqueId(), "");
    } else if (top instanceof GUIHandler.TransactionsHolder) {
      p.removeMetadata("tx-filter", (Plugin) this.controller.getPlugin());
    }

    this.controller
        .getPlugin()
        .getSchedulerAdapter()
        .runEntityTaskLater(
            p,
            () -> {
              if (top instanceof GUIHandler.TransactionsHolder) {
                GUIHandler.openYourItemsGUI(p, this.controller);
              } else if (top instanceof GUIHandler.YourItemsHolder) {
                GUIHandler.openMainGUI(p, 1, this.controller, GUIHandler.ITEMS_PER_PAGE);
              } else if (top instanceof GUIHandler.AdminTransactionsHolder) {
                String target = p.getMetadata("ah-admin-target").get(0).asString();
                GUIHandler.openAdminPlayerDetailsGUI(p, target, this.controller);
              } else if (top instanceof GUIHandler.AdminPlayerDetailsHolder) {
                if (p.hasMetadata("ah-admin-view")) {
                  GUIHandler.openAdminPlayerListGUI(p, 1, this.controller);
                } else {
                  GUIHandler.openMainGUI(p, 1, this.controller, GUIHandler.ITEMS_PER_PAGE);
                }
              }
            },
            1L);
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent event) {
    Player p = event.getPlayer();
    this.controller
        .getPlugin()
        .getSchedulerAdapter()
        .runEntityTaskLater(
            p,
            () -> {
              if (!p.isOnline() || !this.controller.getPlugin().isEnabled()) return;

              List<AuctionManager.OfflineSale> sales =
                  this.controller.getPlugin().getDatabaseManager().settlePendingSales(p);

              if (!sales.isEmpty()) {
                this.controller
                    .getPlugin()
                    .getSchedulerAdapter()
                    .runTask(() -> {
                      if (!p.isOnline()) return;

                      double totalMoney = sales.stream().mapToDouble(s -> s.price).sum();

                      String dateTimeJoin =
                          LocalDateTime.now(ZoneId.of("UTC"))
                              .format(DateTimeFormatter.ofPattern("dd/MM HH:mm:ss"));
                      com.falcon.auction.player.AuctionPlayerData pdJoin =
                          ((com.falcon.auction.FalconAuction) this.controller.getPlugin())
                              .getPlayerDataManager()
                              .get(p.getUniqueId());
                      if (pdJoin != null) {
                        for (AuctionManager.OfflineSale sale : sales) {
                          pdJoin.addHistory(
                              dateTimeJoin
                                  + " - AH Sale (Success/Offline)\nSold "
                                  + sale.item
                                  + " to "
                                  + sale.buyer
                                  + " for $"
                                  + Utils.formatNumber(sale.price));
                        }
                        ((com.falcon.auction.FalconAuction) this.controller.getPlugin())
                            .getPlayerDataManager()
                            .savePlayerAsync(p.getUniqueId());
                      }

                      if (sales.size() == 1) {
                        AuctionManager.OfflineSale sale = sales.get(0);
                        String msg =
                            this.controller
                                .getConfig()
                                .getString("messages.sold-notify-offline")
                                .replace("{buyer}", sale.buyer)
                                .replace("{item}", sale.item)
                                .replace("{priceFormatted}", Utils.formatNumber(sale.price));
                        String c = Utils.formatColors(msg);
                        p.sendMessage(c);
                        p.spigot()
                            .sendMessage(
                                ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(c));
                      } else {
                        String msg =
                            this.controller
                                .getConfig()
                                .getString("messages.sold-notify-offline-multi")
                                .replace("{priceFormatted}", Utils.formatNumber(totalMoney));
                        String c = Utils.formatColors(msg);
                        p.sendMessage(c);
                        p.spigot()
                            .sendMessage(
                                ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(c));

                        p.sendMessage(
                            Utils.formatColors("&#34ee80Detailed summary of offline sales:"));
                        for (AuctionManager.OfflineSale sale : sales) {
                          p.sendMessage(
                              Utils.formatColors(
                                  "&7- &f"
                                      + sale.item
                                      + " &7sold to &f"
                                      + sale.buyer
                                      + " &7for &a$"
                                      + Utils.formatNumber(sale.price)));
                        }
                      }

                      try {
                        Sound notifySound =
                            Sound.valueOf(
                                this.controller
                                    .getConfig()
                                    .getString(
                                        "sounds.sale-notify", "ENTITY_EXPERIENCE_ORB_PICKUP"));
                        p.playSound(p.getLocation(), notifySound, 1.0f, 1.0f);
                      } catch (Exception ignored) {
                      }
                    });
              }
            },
            40L);
  }

  @EventHandler
  public void onInventoryDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
    InventoryHolder top = event.getView().getTopInventory().getHolder();
    if (top instanceof GUIHandler.MainHolder
        || top instanceof GUIHandler.FilterHolder
        || top instanceof GUIHandler.SellConfirmHolder
        || top instanceof GUIHandler.BuyConfirmHolder
        || top instanceof GUIHandler.YourItemsHolder
        || top instanceof GUIHandler.TransactionsHolder
        || top instanceof GUIHandler.AdminPlayerDetailsHolder
        || top instanceof GUIHandler.AdminPlayerListHolder
        || top instanceof GUIHandler.AdminTransactionsHolder
        || top instanceof GUIHandler.ItemManagementHolder
        || top instanceof GUIHandler.AdminDeleteConfirmHolder) {

      for (int slot : event.getRawSlots()) {
        if (slot < event.getView().getTopInventory().getSize()) {
          event.setCancelled(true);
          return;
        }
      }
    }
  }

  private String getNextSortMode(String current) {
    switch (current) {
      case "Highest Price":
        {
          return "Lowest Price";
        }
      case "Lowest Price":
        {
          return "Last Listed";
        }
      case "Last Listed":
        {
          return "Recently Listed";
        }
      case "Recently Listed":
        {
          return "Highest Price";
        }
    }
    return "Highest Price";
  }

  private void purchaseItem(Player p, AuctionItem ai) {
    if (!Utils.isValidPrice(ai.getPrice())) {
      p.sendMessage(Utils.formatColors(controller.getMessage("invalid-price", "&cPrice must be greater than 0.")));
      p.closeInventory();
      return;
    }
    if (!this.controller.getAuctionManager().removeItem(ai)) {
      String dateTime =
          LocalDateTime.now(ZoneId.of("UTC")).format(DateTimeFormatter.ofPattern("dd/MM HH:mm:ss"));
      com.falcon.auction.player.AuctionPlayerData pdBuy =
          ((com.falcon.auction.FalconAuction) this.controller.getPlugin())
              .getPlayerDataManager()
              .get(p.getUniqueId());
      if (pdBuy != null) {
        pdBuy.addHistory(
            dateTime + " - AH Purchase (Failed)\nFailed to buy item (Reason: No longer available)");
      }

      p.sendMessage(
          Utils.formatColors(this.controller.getConfig().getString("messages.item-not-available")));
      p.playSound(
          p.getLocation(),
          Sound.valueOf((String) this.controller.getConfig().getString("sounds.villager-no")),
          1.0f,
          1.0f);
      p.closeInventory();
      int page = p.getMetadata("ah-page").stream().findFirst().map(MetadataValue::asInt).orElse(1);
      GUIHandler.openMainGUI(p, page, this.controller, GUIHandler.ITEMS_PER_PAGE);
      return;
    }

    if (this.controller.getAuctionManager().isExpired(ai)) {
      this.controller.getAuctionManager().addItem(ai);
      p.sendMessage(Utils.formatColors("&cThis listing has expired."));
      p.closeInventory();
      return;
    }

    if (p.getInventory().firstEmpty() == -1) {
      this.controller.getAuctionManager().addItem(ai);
      String dateTime =
          LocalDateTime.now(ZoneId.of("UTC")).format(DateTimeFormatter.ofPattern("dd/MM HH:mm:ss"));
      String itemNameFail = Utils.prettifyMaterialName(ai.getItemStack().getType());
      com.falcon.auction.player.AuctionPlayerData pdBuy =
          ((com.falcon.auction.FalconAuction) this.controller.getPlugin())
              .getPlayerDataManager()
              .get(p.getUniqueId());
      if (pdBuy != null) {
        pdBuy.addHistory(
            dateTime
                + " - AH Purchase (Failed)\nFailed to buy "
                + itemNameFail
                + " (Reason: Inventory full)");
      }

      p.sendMessage(
          Utils.formatColors(this.controller.getConfig().getString("messages.inventory-full")));
      p.playSound(
          p.getLocation(),
          Sound.valueOf((String) this.controller.getConfig().getString("sounds.villager-no")),
          1.0f,
          1.0f);
      p.closeInventory();
      return;
    }

    boolean charged;
    try {
      charged = EconomyHandler.chargePlayer(p, ai.getPrice());
    } catch (RuntimeException error) {
      charged = false;
      controller.getPlugin().getLogger().log(java.util.logging.Level.WARNING,
          "Auction payment failed for buyer " + p.getUniqueId() + " and listing " + ai.getId(), error);
    }
    if (!charged) {
      this.controller.getAuctionManager().addItem(ai);
      String dateTime =
          LocalDateTime.now(ZoneId.of("UTC")).format(DateTimeFormatter.ofPattern("dd/MM HH:mm:ss"));
      String itemNameFail = Utils.prettifyMaterialName(ai.getItemStack().getType());
      com.falcon.auction.player.AuctionPlayerData pdBuy =
          ((com.falcon.auction.FalconAuction) this.controller.getPlugin())
              .getPlayerDataManager()
              .get(p.getUniqueId());
      if (pdBuy != null) {
        pdBuy.addHistory(
            dateTime
                + " - AH Purchase (Failed)\nFailed to buy "
                + itemNameFail
                + " (Reason: Insufficient funds)");
      }
      p.sendMessage(
          Utils.formatColors(this.controller.getConfig().getString("messages.insufficient-funds")));
      p.playSound(
          p.getLocation(),
          Sound.valueOf((String) this.controller.getConfig().getString("sounds.villager-no")),
          1.0f,
          1.0f);
      p.closeInventory();
      return;
    }

    String sellerName = ai.getSeller();
    ItemStack finalItem = this.controller.getAuctionManager().getFinalItem(ai);
    p.getInventory().addItem(new ItemStack[] {finalItem});
    this.controller
        .getTransactionManager()
        .recordSale(finalItem, ai.getPrice(), ai.getSeller(), p.getName());


    String dateTime =
        LocalDateTime.now(ZoneId.of("UTC")).format(DateTimeFormatter.ofPattern("dd/MM HH:mm:ss"));
    String boughtItemName = Utils.prettifyMaterialName(finalItem.getType());
    com.falcon.auction.player.AuctionPlayerData pdBuy =
        ((com.falcon.auction.FalconAuction) this.controller.getPlugin())
            .getPlayerDataManager()
            .get(p.getUniqueId());
    if (pdBuy != null) {
      pdBuy.addHistory(
          dateTime
              + " - AH Purchase (Success)\nBought "
              + boughtItemName
              + " x"
              + finalItem.getAmount()
              + " from "
              + ai.getSeller()
              + " for $"
              + Utils.formatNumber(ai.getPrice()));
      ((com.falcon.auction.FalconAuction) this.controller.getPlugin())
          .getPlayerDataManager()
          .savePlayerAsync(p.getUniqueId());
    }
    String itemName = Utils.prettifyMaterialName(finalItem.getType());
    p.sendMessage(
        Utils.formatColors(
            this.controller
                .getConfig()
                .getString("messages.purchase-success")
                .replace("{priceFormatted}", Utils.formatNumber(ai.getPrice()))
                .replace("{seller}", sellerName)
                .replace("{item}", itemName)));
    try {
      Sound notifySound =
          Sound.valueOf(
              this.controller
                  .getConfig()
                  .getString("sounds.sale-notify", "ENTITY_EXPERIENCE_ORB_PICKUP"));
      p.playSound(p.getLocation(), notifySound, 1.0f, 1.0f);
    } catch (Exception ignored) {
    }
    Player seller = Bukkit.getPlayerExact(sellerName);
    if (seller != null && seller.isOnline()) {
      try {
        EconomyHandler.depositPlayer(seller, ai.getPrice(), "Auction Sale");
      } catch (RuntimeException error) {
        this.controller
            .getPlugin()
            .getDatabaseManager()
            .addAuctionPendingPayment(seller.getUniqueId(), ai.getPrice(), p.getName(), itemName);
        this.controller
            .getPlugin()
            .getLogger()
            .log(java.util.logging.Level.WARNING, "Seller payout deferred", error);
        return;
      }

      com.falcon.auction.player.AuctionPlayerData pdSale =
          ((com.falcon.auction.FalconAuction) this.controller.getPlugin())
              .getPlayerDataManager()
              .get(seller.getUniqueId());
      if (pdSale != null) {
        pdSale.addHistory(
            dateTime
                + " - AH Sale (Success)\nSold "
                + boughtItemName
                + " x"
                + finalItem.getAmount()
                + " to "
                + p.getName()
                + " for $"
                + Utils.formatNumber(ai.getPrice()));
        ((com.falcon.auction.FalconAuction) this.controller.getPlugin())
            .getPlayerDataManager()
            .savePlayerAsync(seller.getUniqueId());
      }
      String soldFmt =
          this.controller
              .getConfig()
              .getString("messages.sold-notify")
              .replace("{item}", itemName)
              .replace("{buyer}", p.getName())
              .replace("{priceFormatted}", Utils.formatNumber(ai.getPrice()));
      String soldC = Utils.formatColors(soldFmt);
      seller.sendMessage(soldC);
      seller
          .spigot()
          .sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText((String) soldC));

      try {
        Sound notifySound =
            Sound.valueOf(
                this.controller
                    .getConfig()
                    .getString("sounds.sale-notify", "ENTITY_EXPERIENCE_ORB_PICKUP"));
        seller.playSound(seller.getLocation(), notifySound, 1.0f, 1.0f);
      } catch (Exception ignored) {
      }
    } else {
      double price = ai.getPrice();
      String buyerName = p.getName();
      this.controller
          .getPlugin()
          .getSchedulerAdapter()
          .runTaskAsync(() -> {
            UUID sellerUUID = Bukkit.getOfflinePlayer(sellerName).getUniqueId();
            this.controller
                .getPlugin()
                .getDatabaseManager()
                .addAuctionPendingPayment(sellerUUID, price, buyerName, itemName);
          });
    }

    p.closeInventory();
  }
}
