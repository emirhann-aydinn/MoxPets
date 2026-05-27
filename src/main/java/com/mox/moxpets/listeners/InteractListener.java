package com.mox.moxpets.listeners;

import com.mox.moxpets.MyPets;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.util.Vector; // BU IMPORT EKLENDİ

public class InteractListener implements Listener {

    private final MyPets plugin;

    public InteractListener(MyPets plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onFKey(PlayerSwapHandItemsEvent event) {
        // Config kontrolü
        if (!plugin.getConfig().getBoolean("interaction.f-key-menu", true)) return;

        Player player = event.getPlayer();
        ArmorStand activePet = plugin.getPetManager().getActivePetEntity(player);

        // Pet yoksa işlem yapma
        if (activePet == null || !activePet.isValid()) return;

        double range = plugin.getConfig().getDouble("interaction.f-key-range", 5.0);

        // Oyuncunun etrafındaki varlıkları tara
        for (Entity entity : player.getNearbyEntities(range, range, range)) {
            if (entity.equals(activePet)) {
                // Bakış açısı kontrolü (Oyuncu pete bakıyor mu?)
                Vector dir = player.getLocation().getDirection().normalize();
                Vector target = activePet.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();

                // Dot product 0.7 üzeri ise bakıyordur
                if (dir.dot(target) > 0.7) {
                    event.setCancelled(true); // Eşya değiştirmeyi engelle
                    plugin.getPetGUI().openSettingsMenu(player); // Ayarlar menüsünü aç
                    return;
                }
            }
        }
    }
}