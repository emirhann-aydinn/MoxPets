package com.mox.moxpets.utils;

import org.bukkit.ChatColor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public static String colorize(String message) {
        if (message == null) return "";

        // HEX Renklerini Çevir (&#FF0000 -> Renk)
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            try {
                String hexCode = matcher.group(1);
                // 1.16+ Spigot API için hex çevirimi
                matcher.appendReplacement(buffer, net.md_5.bungee.api.ChatColor.of("#" + hexCode).toString());
            } catch (NoSuchMethodError e) {
                // Eski sürümse (1.15 ve altı) hex'i yoksay veya en yakın renge yuvarla
                matcher.appendReplacement(buffer, "");
            }
        }
        matcher.appendTail(buffer);

        // Klasik Renk Kodlarını Çevir (&a -> Yeşil)
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }
}