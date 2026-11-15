package com.falcon.auction.player;

import com.falcon.auction.storage.AtomicFiles;
import com.falcon.auction.FalconAuction;

import java.io.*;
import java.util.*;
import org.bukkit.configuration.file.*;

public final class AuctionPlayerData {
  private final File file;
  private final YamlConfiguration cfg;

  public AuctionPlayerData(FalconAuction p, UUID id) {
    file = new File(p.getDataFolder(), "players/" + id + ".yml");
    cfg = YamlConfiguration.loadConfiguration(file);
  }

  synchronized void save() {
    try {
      AtomicFiles.save(cfg, file);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public String getAuctionSortOrder() {
    return cfg.getString("settings.auction_sort", "Highest Price");
  }

  public void setAuctionSortOrder(String s) {
    cfg.set("settings.auction_sort", s);
    save();
  }

  public String getAuctionFilter() {
    return cfg.getString("settings.auction_filter", "");
  }

  public void setAuctionFilter(String s) {
    cfg.set("settings.auction_filter", s);
    save();
  }

  public String getAuctionCategory() {
    return cfg.getString("settings.auction_category", "All");
  }

  public void setAuctionCategory(String s) {
    cfg.set("settings.auction_category", s);
    save();
  }

  public boolean isQuickAuctionBuy() {
    return cfg.getBoolean("settings.quick_auction_buy", false);
  }

  public void addHistory(String s) {
    List<String> h = cfg.getStringList("auction-history");
    h.add(s);
    if (h.size() > 500) h.remove(0);
    cfg.set("auction-history", h);
    save();
  }
}
