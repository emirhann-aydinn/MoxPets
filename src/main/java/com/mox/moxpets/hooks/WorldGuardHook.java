package com.mox.moxpets.hooks;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;

public class WorldGuardHook {

    private final boolean enabled;

    public WorldGuardHook() {
        // Sunucuda WorldGuard var mı kontrol et
        this.enabled = Bukkit.getPluginManager().getPlugin("WorldGuard") != null;
    }

    public boolean isInBlacklistedRegion(Player player, List<String> blacklist) {
        if (!enabled || blacklist == null || blacklist.isEmpty()) return false;

        try {
            Location loc = player.getLocation();
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionQuery query = container.createQuery();
            ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(loc));

            for (ProtectedRegion region : set) {
                if (blacklist.contains(region.getId())) {
                    return true; // Yasaklı bölgede!
                }
            }
        } catch (Exception ignored) {
            // Hata olursa (WG sürüm farkı vb.) yoksay
        }
        return false;
    }
}