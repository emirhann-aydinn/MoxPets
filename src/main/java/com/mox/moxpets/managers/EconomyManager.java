package com.mox.moxpets.managers;

import com.mox.moxpets.MyPets; // Doğru import
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public class EconomyManager {

    private final MyPets plugin;
    private Economy economy = null;

    public EconomyManager(MyPets plugin) {
        this.plugin = plugin;
    }

    // BURASI "private" İDİ, "public" YAPILDI
    public boolean setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    public boolean hasMoney(Player player, double amount) {
        if (economy == null) return true; // Ekonomi yoksa bedava varsay
        return economy.has(player, amount);
    }

    public void withdrawPlayer(Player player, double amount) {
        if (economy != null) {
            economy.withdrawPlayer(player, amount);
        }
    }
}