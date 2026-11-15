package com.falcon.auction.config;

import com.falcon.auction.FalconAuction;
import com.falcon.auction.util.Utils;
import java.util.HashMap;
import java.util.Map;
/** Theme colors belong to this plugin and are refreshed by /auction reload. */
public final class ThemeSettings {
  private final FalconAuction plugin;
  public ThemeSettings(FalconAuction plugin) { this.plugin=plugin; }
  public Map<String,String> getThemeColors() {
    Map<String,String> colors=new HashMap<>(Utils.defaultTheme());
    var section=plugin.getConfig().getConfigurationSection("theme");
    if(section!=null)for(String key:section.getKeys(false)) {
      String color=section.getString(key);if(color!=null)colors.put(key,color);
    }
    return colors;
  }
  public String getColor(String key) {return getThemeColors().get(key);}
}
