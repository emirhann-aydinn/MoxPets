package com.mox.moxpets;

import com.mox.moxpets.commands.PetCommand;
import com.mox.moxpets.gui.PetGUI;
import com.mox.moxpets.listeners.PetProtectionListener;
import com.mox.moxpets.listeners.InteractListener;
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

public class MyPets extends JavaPlugin implements Listener {

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private PetManager petManager;
    private EconomyManager economyManager;
    private PetGUI petGUI;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        configManager.loadConfigs();

        databaseManager = new DatabaseManager(this);
        economyManager = new EconomyManager(this);
        petManager = new PetManager(this);
        petGUI = new PetGUI(this);

        getCommand("moxpets").setExecutor(new PetCommand(this));

        getServer().getPluginManager().registerEvents(petGUI, this);
        getServer().getPluginManager().registerEvents(new PetProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new InteractListener(this), this);
        getServer().getPluginManager().registerEvents(this, this);

        Bukkit.getScheduler().runTaskTimer(this, () -> petManager.updatePets(), 0L, 2L);

        getLogger().info("MoxPets aktif! (Paket yollari %100 uyarlandi)");
    }

    @Override
    public void onDisable() {
        Bukkit.getOnlinePlayers().forEach(p -> petManager.savePlayerData(p));

        if (petManager != null) petManager.removeAllPets();
        if (databaseManager != null) databaseManager.close();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        petManager.loadPlayerDataOnJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        petManager.savePlayerData(event.getPlayer());
    }

    public ConfigManager getConfigManager() { return configManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public PetManager getPetManager() { return petManager; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public PetGUI getPetGUI() { return petGUI; }
}