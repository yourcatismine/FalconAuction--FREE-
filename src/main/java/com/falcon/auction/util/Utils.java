package com.falcon.auction.util;

import com.falcon.auction.FalconAuction;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

public final class Utils {
  public static String formatDuration(long seconds) {
    long value = Math.max(0, seconds);
    return String.format(
        "%dd %dh %dm %ds", value / 86400, (value % 86400) / 3600, (value % 3600) / 60, value % 60);
  }

  private static final Pattern HEX_PATTERN = Pattern.compile("&?#([A-Fa-f0-9]{6})");
  private static final Pattern THEMED_LEGACY_COLOR =
      Pattern.compile("(?i)&#E58214|(?<!&)#E58214|&[bd]");

  public static java.util.Map<String, String> defaultTheme() {
    return java.util.Map.of(
        "primary", "&#E58214", "secondary", "&7", "accents", "&f",
        "countdowns", "&#E58214", "gui-titles", "&#E58214", "messages", "&#E58214");
  }

  public static String formatColors(String input) {
    if (input == null) return null;
    java.util.Map<String, String> colors = new java.util.HashMap<>(defaultTheme());
    FalconAuction instance = FalconAuction.getInstance();
    if (instance != null) colors.putAll(instance.getThemeManager().getThemeColors());
    return formatColors(input, colors);
  }

  static String formatColors(String input, java.util.Map<String, String> colors) {
    if (input == null) return null;
    java.util.Map<String, String> theme = new java.util.HashMap<>(defaultTheme());
    colors.forEach((key, value) -> { if (value != null) theme.put(key, value); });
    for (var entry : theme.entrySet()) {
      input = input.replace("{" + entry.getKey() + "}", entry.getValue());
    }
    // Replace legacy theme colors in one pass: a replacement must never be processed again.
    String countdown = theme.get("countdowns");
    input = THEMED_LEGACY_COLOR.matcher(input).replaceAll(Matcher.quoteReplacement(countdown));
    Matcher matcher = HEX_PATTERN.matcher(input);
    StringBuffer buffer = new StringBuffer(input.length() + 32);
    while (matcher.find()) {
      StringBuilder replacement = new StringBuilder("\u00a7x");
      for (char c : matcher.group(1).toCharArray()) replacement.append('\u00a7').append(c);
      matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement.toString()));
    }
    matcher.appendTail(buffer);
    return ChatColor.translateAlternateColorCodes('&', buffer.toString());
  }

  public static List<String> formatColors(List<String> lines) {
    if (lines == null) {
      return new ArrayList<>();
    }
    return lines.stream().map(Utils::formatColors).collect(Collectors.toList());
  }

  public static String prettifyMaterialName(Material mat) {
    String name = mat.name().toLowerCase().replace('_', ' ');
    StringBuilder out = new StringBuilder();
    for (String word : name.split(" ")) {
      if (word.length() <= 0) continue;
      out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
    }
    return out.toString().trim();
  }

  public static boolean isValidPrice(double price) {
    return Double.isFinite(price) && price > 0;
  }

  public static double parsePrice(String input) {
    if (input == null || input.isEmpty()) {
      return -1.0;
    }
    input = input.trim().replace(",", "");
    if (input.isEmpty()) return -1.0;
    char last = input.charAt(input.length() - 1);
    double multiplier = 1.0;
    String numberPart = input;
    switch (Character.toLowerCase(last)) {
      case 'k':
        {
          multiplier = 1000.0;
          numberPart = input.substring(0, input.length() - 1);
          break;
        }
      case 'm':
        {
          multiplier = 1000000.0;
          numberPart = input.substring(0, input.length() - 1);
          break;
        }
      case 'b':
        {
          multiplier = 1.0E9;
          numberPart = input.substring(0, input.length() - 1);
          break;
        }
      case 't':
        {
          multiplier = 1.0E12;
          numberPart = input.substring(0, input.length() - 1);
        }
    }
    try {
      double value = Double.parseDouble(numberPart);
      double result = value * multiplier;
      if (!isValidPrice(result)) {
        return -1.0;
      }
      return result;
    } catch (NumberFormatException ex) {
      return -1.0;
    }
  }

  public static String formatNumber(double raw) {
    DecimalFormat oneDecimal = new DecimalFormat("#.#");
    if (raw >= 1.0E12) {
      return oneDecimal.format(raw / 1.0E12) + "T";
    }
    if (raw >= 1.0E9) {
      return oneDecimal.format(raw / 1.0E9) + "B";
    }
    if (raw >= 1000000.0) {
      return oneDecimal.format(raw / 1000000.0) + "M";
    }
    if (raw >= 1000.0) {
      return oneDecimal.format(raw / 1000.0) + "K";
    }
    if (Math.floor(raw) == raw) {
      return String.valueOf((long) raw);
    }
    return oneDecimal.format(raw);
  }

  public static List<String> buildSortLore(String currentMode, FileConfiguration cfg) {
    List template = cfg.getStringList("main-gui.items.sort.lore");
    String currentColor = cfg.getString("main-gui.sort-colors.current");
    if (currentColor == null || currentColor.isEmpty()) {
      currentColor = "{accents}";
    }
    String notCurrentColor = cfg.getString("main-gui.sort-colors.not-current");
    if (notCurrentColor == null || notCurrentColor.isEmpty()) {
      notCurrentColor = "{secondary}";
    }
    ArrayList<String> out = new ArrayList<String>();
    for (Object lineObj : template) {
      String processedLine = (String) lineObj;
      String prefix;
      String suffix;
      String modeText;
      String color;
      if (processedLine.contains("{mode\u2010highest}")) {
        prefix = processedLine.split("\\{mode\u2010highest\\}", 2)[0];
        suffix =
            processedLine.split("\\{mode\u2010highest\\}", 2).length > 1
                ? processedLine.split("\\{mode\u2010highest\\}", 2)[1]
                : "";
        modeText = "Highest Price";
        color = currentMode.equals("Highest Price") ? currentColor : notCurrentColor;
        processedLine = color + prefix + modeText + suffix;
      } else if (processedLine.contains("{mode\u2010lowest}")) {
        prefix = processedLine.split("\\{mode\u2010lowest\\}", 2)[0];
        suffix =
            processedLine.split("\\{mode\u2010lowest\\}", 2).length > 1
                ? processedLine.split("\\{mode\u2010lowest\\}", 2)[1]
                : "";
        modeText = "Lowest Price";
        color = currentMode.equals("Lowest Price") ? currentColor : notCurrentColor;
        processedLine = color + prefix + modeText + suffix;
      } else if (processedLine.contains("{mode\u2010lastlisted}")) {
        prefix = processedLine.split("\\{mode\u2010lastlisted\\}", 2)[0];
        suffix =
            processedLine.split("\\{mode\u2010lastlisted\\}", 2).length > 1
                ? processedLine.split("\\{mode\u2010lastlisted\\}", 2)[1]
                : "";
        modeText = "Last Listed";
        color = currentMode.equals("Last Listed") ? currentColor : notCurrentColor;
        processedLine = color + prefix + modeText + suffix;
      } else if (processedLine.contains("{mode\u2010recentlylisted}")) {
        prefix = processedLine.split("\\{mode\u2010recentlylisted\\}", 2)[0];
        suffix =
            processedLine.split("\\{mode\u2010recentlylisted\\}", 2).length > 1
                ? processedLine.split("\\{mode\u2010recentlylisted\\}", 2)[1]
                : "";
        modeText = "Recently Listed";
        color = currentMode.equals("Recently Listed") ? currentColor : notCurrentColor;
        processedLine = color + prefix + modeText + suffix;
      }
      out.add(Utils.formatColors(processedLine));
    }
    return out;
  }

  public static String toSmallCaps(String input) {
    String normal = "abcdefghijklmnopqrstuvwxyz";
    String smallCaps = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘꞯʀꜱᴛᴜᴠᴡxʏᴢ";
    StringBuilder sb = new StringBuilder();
    for (char c : input.toLowerCase().toCharArray()) {
      int index = normal.indexOf(c);
      if (index != -1) {
        sb.append(smallCaps.charAt(index));
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }
}
