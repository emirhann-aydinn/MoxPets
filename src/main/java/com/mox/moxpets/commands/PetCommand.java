package com.mox.moxpets.commands;

import com.mox.moxpets.MyPets;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PetCommand implements CommandExecutor {

    private final MyPets plugin;

    public PetCommand(MyPets plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player) {
                plugin.getPetGUI().openMainMenu((Player) sender);
            } else {
                sender.sendMessage("Bu komut sadece oyuncular içindir.");
            }
            return true;
        }

        // --- ADMIN KOMUTLARI ---
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("moxpets.admin")) {
                sender.sendMessage(plugin.getConfigManager().getMessage("no-permission-command"));
                return true;
            }
            plugin.getConfigManager().loadConfigs();
            plugin.getPetManager().loadPetsFromConfig();
            sender.sendMessage(plugin.getConfigManager().getMessage("reload-success"));
            return true;
        }

        if (args[0].equalsIgnoreCase("removeall")) {
            if (!sender.hasPermission("moxpets.admin")) return true;
            int count = plugin.getPetManager().getActivePetCount();
            plugin.getPetManager().removeAllPets();
            sender.sendMessage("Tum petler silindi (" + count + " adet).");
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("moxpets.admin")) return true;
            if (args.length < 3) {
                sender.sendMessage("Kullanim: /pets give <oyuncu> <petID>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage("Oyuncu bulunamadi.");
                return true;
            }
            plugin.getPetManager().spawnPet(target, args[2]);
            sender.sendMessage("Pet verildi.");
            return true;
        }

        return false;
    }
}