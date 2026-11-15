package com.falcon.auction.auction;

import com.falcon.auction.command.AdminCommand;
import com.falcon.auction.command.AHCommand;
import com.falcon.auction.config.AuctionConfiguration;
import com.falcon.auction.economy.EconomyHandler;
import com.falcon.auction.FalconAuction;
import com.falcon.auction.util.FormatUtils;
import com.falcon.auction.gui.GUIHandler;
import com.falcon.auction.gui.GUIListener;
import com.falcon.auction.transaction.TransactionManager;
import com.falcon.auction.util.Utils;

import java.io.File;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;

public class AuctionController {
  private final FalconAuction plugin;
  private AuctionManager auctionManager;
  private TransactionManager transactionManager;
  private GUIListener guiListener;
  private File filterFile;
  private FileConfiguration filterConfig;
  private File configFile;
  private FileConfiguration config;
  private File messagesFile;
  private FileConfiguration messagesConfig;

  public AuctionController(FalconAuction plugin) {
    this.plugin = plugin;
  }

  public void enable() {
    this.loadConfig();
    this.loadMessagesConfig();
    this.setupFilterFile();
    this.reloadAllConfigs();
    this.auctionManager = new AuctionManager(this);
    this.transactionManager = new TransactionManager(this);
    EconomyHandler.setup(plugin);
    this.auctionManager.loadFromConfig();
    this.transactionManager.loadFromConfig();
    AHCommand ahCmd = new AHCommand(this);
    plugin.getCommand("ah").setExecutor(ahCmd);
    plugin.getCommand("ah").setTabCompleter(ahCmd);
    AdminCommand adminCmd = new AdminCommand(this);
    if (plugin.getCommand("auction") != null) {
      plugin.getCommand("auction").setExecutor((CommandExecutor) adminCmd);
      plugin.getCommand("auction").setTabCompleter((TabCompleter) adminCmd);
    } else {
      plugin.getLogger().severe("Command 'auction' not found in plugin.yml!");
    }
    this.guiListener = new GUIListener(this);
    plugin.getServer().getPluginManager().registerEvents((Listener) this.guiListener, plugin);
    this.startAutoSaveTask();
    plugin.getLogger().info("Auction system enabled.");
  }

  public void disable() {
    this.saveAllData();
    plugin.getLogger().info("Auction system disabled.");
  }

  private void loadConfig() {
    this.configFile = new File(plugin.getDataFolder(), "economy/auction/config.yml");
    if (!this.configFile.exists()) {
      this.configFile.getParentFile().mkdirs();
      plugin.saveResource("economy/auction/config.yml", false);
    }
    this.config = YamlConfiguration.loadConfiguration(this.configFile);
  }

  public void loadMessagesConfig() {
    this.messagesFile = new File(plugin.getDataFolder(), "messages/economy/auction.yml");
    if (!this.messagesFile.exists()) {
      this.messagesFile.getParentFile().mkdirs();
      plugin.saveResource("messages/economy/auction.yml", false);
    }
    this.messagesConfig = YamlConfiguration.loadConfiguration(this.messagesFile);
    if (this.config != null && this.messagesConfig != null) {
      this.config.setDefaults(this.messagesConfig);
    }
  }

  public FileConfiguration getMessagesConfig() {
    if (this.messagesConfig == null) {
      loadMessagesConfig();
    }
    return this.messagesConfig;
  }

  public String getMessage(String path, String def) {
    FileConfiguration msgCfg = getMessagesConfig();
    if (msgCfg != null && msgCfg.contains("messages." + path)) {
      return msgCfg.getString("messages." + path);
    }
    FileConfiguration mainCfg = getConfig();
    if (mainCfg != null && mainCfg.contains("messages." + path)) {
      return mainCfg.getString("messages." + path);
    }
    return def;
  }

  public String getMessage(String path) {
    return getMessage(path, "");
  }

  public String getSoundName(String path, String def) {
    FileConfiguration msgCfg = getMessagesConfig();
    if (msgCfg != null && msgCfg.contains("sounds." + path)) {
      return msgCfg.getString("sounds." + path);
    }
    FileConfiguration mainCfg = getConfig();
    if (mainCfg != null && mainCfg.contains("sounds." + path)) {
      return mainCfg.getString("sounds." + path);
    }
    return def;
  }

  public FileConfiguration getConfig() {
    if (this.config == null) {
      loadConfig();
    }
    return this.config;
  }

  public void reloadConfig() {
    reloadAllConfigs();
  }



  private void setupFilterFile() {
    this.filterFile = new File(plugin.getDataFolder(), "economy/auction/filter.yml");
    if (!this.filterFile.exists()) {
      this.filterFile.getParentFile().mkdirs();
      plugin.saveResource("economy/auction/filter.yml", false);
    }
    this.filterConfig = YamlConfiguration.loadConfiguration(this.filterFile);
  }

  public void reloadAllConfigs() {
    try {
      AuctionConfiguration next = AuctionConfiguration.load(plugin.getDataFolder());
      plugin.reloadConfig();
      this.config = next.ui();
      this.messagesConfig = next.messages();
      this.filterConfig = next.filter();
    } catch (Exception error) {
      throw new IllegalStateException("Could not reload auction configuration: " + error.getMessage(), error);
    }
  }

  public FileConfiguration getFilterConfig() {
    return this.filterConfig;
  }

  public AuctionManager getAuctionManager() {
    return this.auctionManager;
  }

  public TransactionManager getTransactionManager() {
    return this.transactionManager;
  }





  public void startAutoSaveTask() {
    plugin
        .getSchedulerAdapter()
        .runTaskTimerAsync(
            () -> {
              this.saveAllData();
            },
            12000L,
            12000L);
  }

  private final java.util.Map<java.util.UUID, org.bukkit.scheduler.BukkitTask> activeTasks =
      new java.util.concurrent.ConcurrentHashMap<>();

  public void startUpdateTask(org.bukkit.entity.Player player) {
    if (activeTasks.containsKey(player.getUniqueId())) return;

    org.bukkit.NamespacedKey auctionKey = new org.bukkit.NamespacedKey(plugin, "auction-expire");
    org.bukkit.NamespacedKey txKey = new org.bukkit.NamespacedKey(plugin, "transaction-timestamp");

    org.bukkit.scheduler.BukkitTask task =
        plugin
            .getSchedulerAdapter()
            .runEntityTaskTimer(
                player,
                () -> {
                  if (!player.isOnline()) {
                    stopUpdateTask(player);
                    return;
                  }
                  org.bukkit.inventory.InventoryView view = player.getOpenInventory();
                  if (view == null) return;
                  org.bukkit.inventory.Inventory top = view.getTopInventory();
                  org.bukkit.inventory.InventoryHolder holder = top.getHolder();

                  if (!(holder instanceof GUIHandler.MainHolder
                      || holder instanceof GUIHandler.YourItemsHolder
                      || holder instanceof GUIHandler.TransactionsHolder
                      || holder instanceof GUIHandler.AdminPlayerDetailsHolder
                      || holder instanceof GUIHandler.AdminTransactionsHolder)) {
                    return;
                  }

                  for (int i = 0; i < top.getSize(); i++) {
                    org.bukkit.inventory.ItemStack item = top.getItem(i);
                    if (item == null || !item.hasItemMeta()) continue;
                    org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                    org.bukkit.persistence.PersistentDataContainer pdc =
                        meta.getPersistentDataContainer();

                    boolean changed = false;

                    if (pdc.has(auctionKey, org.bukkit.persistence.PersistentDataType.LONG)) {
                      long expireTime =
                          pdc.get(auctionKey, org.bukkit.persistence.PersistentDataType.LONG);
                      long remaining = expireTime - System.currentTimeMillis();

                      if (remaining <= 0) {
                        if (holder instanceof GUIHandler.MainHolder
                            || holder instanceof GUIHandler.AdminPlayerDetailsHolder) {
                          top.setItem(i, null);
                          continue;
                        }
                      }

                      String timeStr =
                          remaining <= 0
                              ? "&#ff4444Expired"
                              : FormatUtils.formatTime((int) (remaining / 1000));
                      if (meta.hasLore()) {
                        java.util.List<String> lore = new java.util.ArrayList<>(meta.getLore());
                        for (int j = 0; j < lore.size(); ++j) {
                          String line = lore.get(j);
                          String plainLine = org.bukkit.ChatColor.stripColor(line);
                          if (plainLine.contains("Time left:")) {
                            String newLine =
                                Utils.formatColors(
                                    "{secondary}Time left: "
                                        + (remaining <= 0 ? "&#ff4444" : "{primary}")
                                        + timeStr);
                            lore.set(j, newLine);
                            changed = true;
                            break;
                          }
                        }
                        if (changed) {
                          meta.setLore(lore);
                        }
                      }
                    }

                    if (pdc.has(txKey, org.bukkit.persistence.PersistentDataType.LONG)) {
                      long soldTime =
                          pdc.get(txKey, org.bukkit.persistence.PersistentDataType.LONG);
                      long elapsedSeconds = (System.currentTimeMillis() - soldTime) / 1000L;
                      String timeAgo = FormatUtils.formatTime((int) elapsedSeconds);
                      if (meta.hasLore()) {
                        java.util.List<String> lore = meta.getLore();
                        for (int j = 0; j < lore.size(); ++j) {
                          String check = lore.get(j);
                          if (check.contains("Sold:")) {
                            String newLine = Utils.formatColors("&fSold: &7" + timeAgo + " ago");
                            if (!check.equals(newLine)) {
                              lore.set(j, newLine);
                              changed = true;
                            }
                          } else if (check.endsWith(" ago")) {
                            String newLine = Utils.formatColors("&a" + timeAgo + " ago");
                            if (!check.equals(newLine)) {
                              lore.set(j, newLine);
                              changed = true;
                            }
                          }
                        }
                      }
                    }

                    if (changed) {
                      item.setItemMeta(meta);
                      top.setItem(i, item);
                    }
                  }
                },
                20L,
                20L);

    activeTasks.put(player.getUniqueId(), task);
  }

  public void stopUpdateTask(org.bukkit.entity.Player player) {
    org.bukkit.scheduler.BukkitTask task = activeTasks.remove(player.getUniqueId());
    if (task != null) {
      task.cancel();
    }
  }

  public void saveAllData() {
    if (this.auctionManager != null) {
      this.auctionManager.saveToConfig();
    }
    if (this.transactionManager != null) {
      this.transactionManager.saveToConfig();
    }
  }

  public FalconAuction getPlugin() {
    return plugin;
  }
}
