package com.falcon.auction.config;
import java.io.File;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
/** Read every file before publishing a reload; bad YAML leaves the live configuration intact. */
public record AuctionConfiguration(YamlConfiguration ui,YamlConfiguration messages,YamlConfiguration filter) {
  public static AuctionConfiguration load(File folder) throws Exception {
    YamlConfiguration root=read(new File(folder,"config.yml"));
    YamlConfiguration ui=read(new File(folder,"economy/auction/config.yml"));
    YamlConfiguration messages=read(new File(folder,"messages/economy/auction.yml"));
    YamlConfiguration filter=read(new File(folder,"economy/auction/filter.yml"));
    merge(ui,messages,Set.of());
    // Also accept GUI/settings/message overrides in the plugin's main config.
    merge(ui,root,Set.of("economy","theme"));
    return new AuctionConfiguration(ui,ui,filter);
  }
  private static YamlConfiguration read(File file)throws Exception {
    YamlConfiguration result=new YamlConfiguration();
    if(file.exists())result.load(file);
    return result;
  }
  private static void merge(YamlConfiguration target,YamlConfiguration source,Set<String> excluded) {
    for(String key:source.getKeys(true)) {
      if(!source.isConfigurationSection(key)&&!excluded.contains(key.contains(".") ? key.substring(0,key.indexOf('.')) : key))target.set(key,source.get(key));
    }
  }
}
