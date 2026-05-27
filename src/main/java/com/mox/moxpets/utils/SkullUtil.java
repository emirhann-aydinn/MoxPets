package com.mox.moxpets.utils;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

public class SkullUtil {

    public static ItemStack getCustomSkull(String texture, String displayName) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();

        if (meta != null) {
            // İsimdeki renk kodlarını düzelt
            if (displayName != null) {
                meta.setDisplayName(displayName.replace("&", "§"));
            }

            if (texture != null && !texture.isEmpty()) {
                try {
                    String urlString = null;
                    // Tüm boşlukları temizle (Hata önleyici)
                    String cleanTexture = texture.replaceAll("\\s", "").trim();

                    // 1. Durum: Direkt URL girildiyse (http://textures.minecraft.net/...)
                    if (cleanTexture.startsWith("http")) {
                        urlString = cleanTexture;
                    }
                    // 2. Durum: Base64 kodu girildiyse (eyJ...)
                    else {
                        try {
                            // Base64'ü çözüyoruz
                            String decoded = new String(Base64.getDecoder().decode(cleanTexture), StandardCharsets.UTF_8);

                            // İçinden sadece URL kısmını cımbızla çekiyoruz
                            // JSON parse etmek yerine string arıyoruz, bu asla çökmez.
                            if (decoded.contains("textures.minecraft.net")) {
                                int start = decoded.indexOf("http");
                                int end = decoded.indexOf("\"", start);
                                if (end == -1) end = decoded.length();

                                urlString = decoded.substring(start, end);
                            }
                        } catch (Exception e) {
                            // Eğer decode edilemezse sessiz kal
                        }
                    }

                    // 3. Durum: URL bulunduysa kafaya işle
                    if (urlString != null && !urlString.isEmpty()) {
                        // URL'den sabit bir UUID üretiyoruz (Steve sorununu çözer)
                        UUID uuid = UUID.nameUUIDFromBytes(urlString.getBytes(StandardCharsets.UTF_8));

                        PlayerProfile profile = Bukkit.createPlayerProfile(uuid, "MoxPet");
                        PlayerTextures textures = profile.getTextures();
                        textures.setSkin(new URL(urlString));
                        profile.setTextures(textures);
                        meta.setOwnerProfile(profile);
                    }

                } catch (Exception e) {
                    Bukkit.getLogger().warning("[MoxPets] Texture hatası: " + displayName);
                }
            }
            skull.setItemMeta(meta);
        }
        return skull;
    }
}