package com.mox.moxpets;

import com.mox.moxpets.commands.PetCommand;
import com.mox.moxpets.gui.PetGUI;
import com.mox.moxpets.listeners.InteractListener;
import com.mox.moxpets.listeners.PetProtectionListener;
import com.mox.moxpets.managers.ConfigManager;
import com.mox.moxpets.managers.DatabaseManager;
import com.mox.moxpets.managers.EconomyManager;
import com.mox.moxpets.managers.PetManager;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class MyPets extends JavaPlugin implements Listener { // Listener eklendi

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private PetManager petManager;
    private EconomyManager economyManager;
    private PetGUI petGUI;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        configManager.loadConfigs();

        databaseManager = new DatabaseManager(this); // SQL Başlatıldı

        petManager = new PetManager(this);
        economyManager = new EconomyManager(this);
        petGUI = new PetGUI(this);

        getCommand("moxpets").setExecutor(new PetCommand(this));

        getServer().getPluginManager().registerEvents(petGUI, this);
        getServer().getPluginManager().registerEvents(new PetProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new InteractListener(this), this);
        getServer().getPluginManager().registerEvents(this, this); // Bu sınıfı listener olarak kaydet

        Bukkit.getScheduler().runTaskTimer(this, () -> petManager.updatePets(), 0L, 2L);

        getLogger().info("MoxPets (SQL) aktif!");
    }

    @Override
    public void onDisable() {
        // Sunucu kapanırken tüm oyuncuların verisini kaydet
        Bukkit.getOnlinePlayers().forEach(p -> petManager.savePlayerData(p));

        if (petManager != null) petManager.removeAllPets();
        if (databaseManager != null) databaseManager.close();
    }

    // --- SQL YÜKLEME/KAYDETME EVENTLERİ ---

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Oyuncu girince verilerini SQL'den çekip Cache'e at
        petManager.loadPlayerDataOnJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Oyuncu çıkınca Cache'deki veriyi SQL'e yaz
        petManager.savePlayerData(event.getPlayer());
    }

    // Getters
    public ConfigManager getConfigManager() { return configManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public PetManager getPetManager() { return petManager; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public PetGUI getPetGUI() { return petGUI; }
}