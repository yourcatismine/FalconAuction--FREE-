package com.falcon.auction.command;

import com.falcon.auction.auction.AuctionController;
import com.falcon.auction.gui.GUIHandler;
import com.falcon.auction.util.Utils;

import java.util.Collections;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public class AdminCommand implements CommandExecutor, TabCompleter {
  private final AuctionController controller;

  public AdminCommand(AuctionController controller) {
    this.controller = controller;
  }

  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (args.length == 2 && args[0].equalsIgnoreCase("player")) {
      if (!(sender instanceof org.bukkit.entity.Player player)
          || !sender.hasPermission("auction.admin")) {
        sender.sendMessage(
            Utils.formatColors("&cYou do not have permission to open auction administration."));
        return true;
      }
      controller.getPlugin().openAdmin(player, args[1]);
      return true;
    }
    if (!(sender.hasPermission("donutauction.reload") || sender.hasPermission("auction.reload"))) {
      sender.sendMessage(
          Utils.formatColors("&#ff4444You do not have permission to use this command."));
      return true;
    }
    if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
      try {
        this.controller.reloadAllConfigs();
      } catch (RuntimeException error) {
        controller.getPlugin().getLogger().log(java.util.logging.Level.WARNING, "Auction reload failed", error);
        sender.sendMessage(Utils.formatColors("&cReload failed. Check the YAML files and server log. Previous settings remain active."));
        return true;
      }
      for (var player : Bukkit.getOnlinePlayers()) {
        controller.getPlugin().getSchedulerAdapter().runEntityTask(player, () -> {
          var holder = player.getOpenInventory().getTopInventory().getHolder();
          if (holder != null && holder.getClass().getEnclosingClass() == GUIHandler.class) player.closeInventory();
        });
      }
      String msg = Utils.formatColors("&#44ff44Auction system reloaded.");
      sender.sendMessage(msg);
      return true;
    }
    sender.sendMessage(
        Utils.formatColors("&#ff4444Usage: /auction reload | /auction player <name>"));
    return true;
  }

  public List<String> onTabComplete(
      CommandSender sender, Command command, String alias, String[] args) {
    if (args.length == 1 && (sender.hasPermission("donutauction.reload") || sender.hasPermission("auction.reload"))) {
      return Collections.singletonList("reload");
    }
    return Collections.emptyList();
  }
}
