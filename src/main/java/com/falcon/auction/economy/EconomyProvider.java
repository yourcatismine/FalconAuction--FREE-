package com.falcon.auction.economy;
import java.util.Locale;
/** Only the public economy integrations supported by this plugin. */
enum EconomyProvider {
  ESSENTIALSX, COINSENGINE;
  static EconomyProvider parse(String input) {
    String normalized=input.trim().toUpperCase(Locale.ROOT);
    if(normalized.equals("ESSENTIALX")||normalized.equals("ESSENTIALS"))normalized="ESSENTIALSX";
    try{return valueOf(normalized);}catch(IllegalArgumentException error){throw new IllegalArgumentException("economy.provider must be ESSENTIALSX or COINSENGINE",error);}
  }
}
