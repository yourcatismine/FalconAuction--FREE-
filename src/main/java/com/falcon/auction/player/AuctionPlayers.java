package com.falcon.auction.player;

import com.falcon.auction.FalconAuction;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class AuctionPlayers {
  private final FalconAuction plugin;
  private final Map<UUID, AuctionPlayerData> players = new ConcurrentHashMap<>();

  public AuctionPlayers(FalconAuction p) {
    plugin = p;
  }

  public AuctionPlayerData get(UUID id) {
    return players.computeIfAbsent(id, k -> new AuctionPlayerData(plugin, k));
  }

  public void savePlayerAsync(UUID id) {
    get(id).save();
  }
}
