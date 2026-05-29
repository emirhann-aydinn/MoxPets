package com.mox.moxpets.listeners;

import com.mox.moxpets.MyPets;
import com.mox.moxpets.managers.PetManager;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class PetProtectionListener implements Listener {

    private final MyPets plugin;

    public PetProtectionListener(MyPets plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (isPet(event.getEntity())) {
            event.setCancelled(true);
        } else if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            String activeId = plugin.getPetManager().getActivePetId(player);
            if (activeId != null) {
                PetManager.PetSaveData data = plugin.getPetManager().getPetData(player, activeId);
                if (data != null && data.defenseMode) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (isPet(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player) {
            Player player = (Player) event.getTarget();
            String activeId = plugin.getPetManager().getActivePetId(player);
            if (activeId != null) {
                PetManager.PetSaveData data = plugin.getPetManager().getPetData(player, activeId);
                if (data != null && data.defenseMode) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        String activeId = plugin.getPetManager().getActivePetId(player);
        if (activeId != null) {
            PetManager.PetSaveData data = plugin.getPetManager().getPetData(player, activeId);
            if (data != null && data.defenseMode) {
                Location from = event.getFrom();
                Location to = event.getTo();
                if (to != null && (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ())) {
                    player.teleport(from);
                }
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractAtEntityEvent event) {
        if (isPet(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        plugin.getPetManager().removePet(player, false);

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
        return ((ArmorStand) entity).isSmall() && !((ArmorStand) entity).isVisible();
    }
}