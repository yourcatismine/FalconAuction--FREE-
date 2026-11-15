package com.falcon.auction.economy;

import com.falcon.auction.FalconAuction;
import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
/** Explicitly selected provider; balances are always held by the installed economy plugin. */
public final class EconomyHandler {
  interface Backend {boolean change(OfflinePlayer player,double amount,String action);}
  private static Backend backend;
  private static EconomyProvider provider;
  public static void setup(FalconAuction plugin) {
    EconomyProvider next=EconomyProvider.parse(plugin.getConfig().getString("economy.provider","ESSENTIALSX"));
    Backend selected;
    switch(next) {
      case ESSENTIALSX -> {require("Essentials");require("Vault");selected=new VaultBackend();}
      case COINSENGINE -> {
        Plugin coins=require("CoinsEngine");String currency=plugin.getConfig().getString("economy.coinsengine-currency","coins");
        try {Class<?> api=Class.forName("su.nightexpress.coinsengine.api.CoinsEngineAPI",true,coins.getClass().getClassLoader());
          Object selectedCurrency=invoke(api,"getCurrency",currency);
          if(selectedCurrency==null)throw new IllegalStateException("CoinsEngine currency does not exist: "+currency);
          selected=(player,amount,action)->{
            double balance=((Number)invoke(api,"getBalance",player.getUniqueId(),selectedCurrency)).doubleValue();
            if(action.equals("remove")&&balance<amount)return false;
            return Boolean.TRUE.equals(invoke(api,action.equals("remove")?"removeBalance":"addBalance",player.getUniqueId(),selectedCurrency,amount));
          };
        }catch(ClassNotFoundException error){throw new IllegalStateException("Unsupported CoinsEngine API",error);}
      }
      default -> throw new IllegalStateException("Unsupported economy provider");
    }
    backend=selected;provider=next;plugin.getLogger().info("Auction economy: "+next);
  }
  private static Plugin require(String name) {Plugin p=Bukkit.getPluginManager().getPlugin(name);if(p==null||!p.isEnabled())throw new IllegalStateException(name+" must be installed and enabled");return p;}
  private static Object invoke(Class<?> api,String method,Object...args) {
    try {
      for(Method m:api.getMethods()) {
        if(!m.getName().equals(method)||m.getParameterCount()!=args.length)continue;
        Class<?>[] types=m.getParameterTypes();boolean matches=true;
        for(int i=0;i<args.length;i++)if(!types[i].isInstance(args[i])&&!(types[i]==double.class&&args[i] instanceof Double))matches=false;
        if(matches)return m.invoke(null,args);
      }
      throw new NoSuchMethodException(method);
    }catch(ReflectiveOperationException error){throw new IllegalStateException("CoinsEngine call failed: "+method,error);}
  }
  private static boolean change(OfflinePlayer p,double amount,String action) {if(!Double.isFinite(amount)||amount<0)return false;return backend.change(p,amount,action);}
  public static void payPending(Player p,double amount){depositPlayer(p,amount,"Offline Auction Earnings");}
  public static boolean chargePlayer(Player p,double amount){return chargePlayer(p,amount,"Auction purchase");}
  public static boolean chargePlayer(Player p,double amount,String source){return change(p,amount,"remove");}
  public static void depositPlayer(Player p,double amount,String source){if(!change(p,amount,"give"))throw new IllegalStateException("Seller payment rejected");}
  public static void depositPlayer(Player p,double amount){depositPlayer(p,amount,"Auction sale");}
  public static boolean depositByName(String name,double amount,String source){return change(Bukkit.getOfflinePlayer(name),amount,"give");}
  public static boolean depositByName(String name,double amount){return depositByName(name,amount,"Auction sale");}
  public static boolean usingVault(){return provider==EconomyProvider.ESSENTIALSX;}
}
