package com.mox.moxpets.managers;

import com.mox.moxpets.MyPets;
import com.mox.moxpets.utils.ColorUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class ConfigManager {

    private final MyPets plugin;

    // Config Dosyaları
    private FileConfiguration petsConfig;
    private FileConfiguration langConfig;

    // Menü Dosyaları
    private FileConfiguration mainMenuConfig;
    private FileConfiguration ownedMenuConfig;
    private FileConfiguration shopMenuConfig;
    private FileConfiguration confirmMenuConfig;
    private FileConfiguration trailsMenuConfig;
    private FileConfiguration wardrobeMenuConfig;
    private FileConfiguration buffsMenuConfig;
    private FileConfiguration upgradesMenuConfig; // settings.yml buraya yüklenecek

    public ConfigManager(MyPets plugin) {
        this.plugin = plugin;
    }

    public void loadConfigs() {
        // Ana Config
        plugin.saveDefaultConfig();
        plugin.reloadConfig();

        // Veri Dosyaları
        petsConfig = loadFile("pets.yml");
        langConfig = loadFile("lang.yml");

        // Menüler
        mainMenuConfig = loadFile("menus/main.yml");
        ownedMenuConfig = loadFile("menus/owned_pets.yml");
        shopMenuConfig = loadFile("menus/shop.yml");
        confirmMenuConfig = loadFile("menus/confirmation.yml");
        trailsMenuConfig = loadFile("menus/trails.yml");
        wardrobeMenuConfig = loadFile("menus/wardrobe.yml");
        buffsMenuConfig = loadFile("menus/pet_buff.yml");

        // Settings Menüsü (Kod içinde upgradesMenuConfig olarak geçiyor)
        upgradesMenuConfig = loadFile("menus/settings.yml");
    }

    private FileConfiguration loadFile(String path) {
        File file = new File(plugin.getDataFolder(), path);
        if (!file.exists()) {
            try {
                plugin.saveResource(path, false);
            } catch (Exception e) {
                plugin.getLogger().warning(path + " oluşturulamadı!");
            }
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    public String getMessage(String key, String... placeholders) {
        if (langConfig == null) return key;

        String msg = langConfig.getString(key);
        if (msg == null) return "Mesaj bulunamadı: " + key;

        String prefix = langConfig.getString("prefix", "&8[&6MoxPets&8] ");
        msg = msg.replace("%prefix%", prefix);

        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 < placeholders.length) {
                msg = msg.replace(placeholders[i], placeholders[i + 1]);
            }
        }
        return ColorUtil.colorize(msg);
    }

    // --- GETTER METODLARI ---
    public FileConfiguration getPetsConfig() { return petsConfig; }
    public FileConfiguration getLangConfig() { return langConfig; }
    public FileConfiguration getMainMenuConfig() { return mainMenuConfig; }
    public FileConfiguration getOwnedMenuConfig() { return ownedMenuConfig; }
    public FileConfiguration getShopMenuConfig() { return shopMenuConfig; }
    public FileConfiguration getConfirmMenuConfig() { return confirmMenuConfig; }
    public FileConfiguration getTrailsMenuConfig() { return trailsMenuConfig; }
    public FileConfiguration getWardrobeMenuConfig() { return wardrobeMenuConfig; }
    public FileConfiguration getBuffsMenuConfig() { return buffsMenuConfig; }
    public FileConfiguration getUpgradesMenuConfig() { return upgradesMenuConfig; } // BU METOD EKLENDİ
}