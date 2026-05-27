package com.mox.moxpets.listeners;

import com.mox.moxpets.MyPets;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class PetProtectionListener implements Listener {

    private final MyPets plugin;

    public PetProtectionListener(MyPets plugin) {
        this.plugin = plugin;
    }

    // --- KORUMA EVENTLERİ ---
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (isPet(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (isPet(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractAtEntityEvent event) {
        if (isPet(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    // --- YÖNETİM EVENTLERİ (GİRİŞ/ÇIKIŞ/DÜNYA) ---

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // SQL'den veri çek ve spawn et
        plugin.getPetManager().loadPlayerDataOnJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Veriyi kaydet ve peti sil
        plugin.getPetManager().savePlayerData(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        plugin.getPetManager().removePet(player, false); // Sadece entity'i sil

        // Gecikmeli tekrar doğur (Dünya yüklenmesi için)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                String activeId = plugin.getPetManager().getActivePetId(player);
                if (activeId != null) {
                    plugin.getPetManager().spawnPet(player, activeId);
                }
            }
        }, 10L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                String activeId = plugin.getPetManager().getActivePetId(player);
                if (activeId != null) {
                    plugin.getPetManager().spawnPet(player, activeId);
                }
            }
        }, 10L);
    }

    private boolean isPet(Entity entity) {
        if (!(entity instanceof ArmorStand)) return false;
        // Basit kontrol: Görünmez ve küçükse potansiyel pet
        return ((ArmorStand) entity).isSmall() && !((ArmorStand) entity).isVisible();
    }
}