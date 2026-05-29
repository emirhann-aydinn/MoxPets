package com.mox.moxpets;

import com.mox.moxpets.commands.PetCommand;
import com.mox.moxpets.gui.PetGUI;
import com.mox.moxpets.listeners.PetProtectionListener;
import com.mox.moxpets.listeners.InteractListener;
import com.mox.moxpets.managers.ConfigManager;
import com.mox.moxpets.managers.DatabaseManager;
import com.mox.moxpets.managers.EconomyManager;
import com.mox.moxpets.managers.PetManager;
import com.mox.moxbox.api.MoxAddon;
import com.mox.moxbox.api.MoxBoxAPI;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

public class MyPets extends MoxAddon implements Listener {

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private PetManager petManager;
    private EconomyManager economyManager;
    private PetGUI petGUI;

    // MoxBox eklentisini Bukkit Plugin olarak donduren yardimci metot
    public Plugin getBukkitPlugin() {
        return Bukkit.getPluginManager().getPlugin("MoxBox");
    }

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        configManager.loadConfigs();

        databaseManager = new DatabaseManager(this);
        economyManager = new EconomyManager(this);
        petManager = new PetManager(this);
        petGUI = new PetGUI(this);

        // Komutu ve tüm kısayollarını (aliases) MoxBox sistemine kaydediyoruz
        PetCommand petCommand = new PetCommand(this);
        MoxBoxAPI.registerCommand(this, "moxpets", petCommand);
        MoxBoxAPI.registerCommand(this, "pets", petCommand);
        MoxBoxAPI.registerCommand(this, "pet", petCommand);

        MoxBoxAPI.registerEvents(this, petGUI);
        MoxBoxAPI.registerEvents(this, new PetProtectionListener(this));
        MoxBoxAPI.registerEvents(this, new InteractListener(this));
        MoxBoxAPI.registerEvents(this, this);

        Bukkit.getScheduler().runTaskTimer(getBukkitPlugin(), () -> petManager.updatePets(), 0L, 2L);

        getLogger().info("MoxPets, MoxBox uzerinde aktif! (Kısayollar Eklendi)");
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