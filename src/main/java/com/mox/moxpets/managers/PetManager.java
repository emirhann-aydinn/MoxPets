package com.mox.moxpets.managers;

import com.mox.moxpets.MyPets;
import com.mox.moxpets.utils.ColorUtil;
import com.mox.moxpets.utils.SkullUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PetManager {

    private final MyPets plugin;

    private final Map<UUID, ArmorStand> activePets = new HashMap<>();
    private final Map<UUID, ArmorStand> activeNameTags = new HashMap<>();
    private final Map<UUID, String> playerActivePetId = new HashMap<>();
    private final Map<UUID, String> hiddenPets = new HashMap<>();

    private final Map<UUID, Particle> activeTrails = new HashMap<>();
    private final Map<UUID, String> activeTrailNames = new HashMap<>();
    private final Map<UUID, Color> petArmorColors = new HashMap<>();
    private final Map<UUID, Long> lastXpGainTime = new HashMap<>();
    private final Map<UUID, Long> actionCooldowns = new HashMap<>();

    private final Map<UUID, Map<String, PetSaveData>> dataCache = new ConcurrentHashMap<>();

    private final List<PetData> loadedPets = new ArrayList<>();
    private final Map<String, PetData> petDataMap = new HashMap<>();

    private final double NAMETAG_HEIGHT = 1.1;
    private boolean isLagging = false;
    private final NamespacedKey petEntityKey;

    public PetManager(MyPets plugin) {
        this.plugin = plugin;
        this.petEntityKey = new NamespacedKey(plugin.getBukkitPlugin(), "moxpet_entity");
        loadPetsFromConfig();
    }

    public boolean checkCooldown(Player player) {
        long now = System.currentTimeMillis();
        long last = actionCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 1000) return false;
        actionCooldowns.put(player.getUniqueId(), now);
        return true;
    }

    public void loadPlayerDataOnJoin(Player player) {
        UUID uid = player.getUniqueId();
        dataCache.put(uid, new HashMap<>());
        String lastActive = plugin.getDatabaseManager().loadActivePet(uid);
        if (lastActive != null && petDataMap.containsKey(lastActive)) {
            PetSaveData data = plugin.getDatabaseManager().loadPetData(uid, lastActive);
            if (data != null) dataCache.get(uid).put(lastActive, data);
            Bukkit.getScheduler().runTask(plugin.getBukkitPlugin(), () -> spawnPet(player, lastActive));
        }
    }

    public void savePlayerData(Player player) {
        UUID uid = player.getUniqueId();
        if (!dataCache.containsKey(uid)) return;
        Map<String, PetSaveData> pets = dataCache.get(uid);
        for (Map.Entry<String, PetSaveData> entry : pets.entrySet()) {
            PetSaveData data = entry.getValue();
            plugin.getDatabaseManager().savePetData(uid, entry.getKey(), data.customName, data.level, data.exp, data.trail, data.armorColor, data.glow, data.disabledBuffs, data.isFavorite, data.defenseMode);
        }
        String active = playerActivePetId.get(uid);
        plugin.getDatabaseManager().saveActivePet(uid, active);
        dataCache.remove(uid);
        activePets.remove(uid);
        playerActivePetId.remove(uid);
    }

    public PetSaveData getPetData(Player player, String petId) {
        UUID uid = player.getUniqueId();
        dataCache.computeIfAbsent(uid, k -> new HashMap<>());
        if (!dataCache.get(uid).containsKey(petId)) {
            PetSaveData data = plugin.getDatabaseManager().loadPetData(uid, petId);
            if (data == null) {
                String defName = petDataMap.get(petId).nameTag;
                data = new PetSaveData(defName, 1, 0.0, null, null, false, new HashSet<>(), false, false);
            }
            dataCache.get(uid).put(petId, data);
        }
        return dataCache.get(uid).get(petId);
    }

    public void loadPetsFromConfig() {
        loadedPets.clear();
        petDataMap.clear();
        ConfigurationSection section = plugin.getConfigManager().getPetsConfig().getConfigurationSection("pets");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            String rawName = section.getString(key + ".display_name", "Pet");
            String coloredName = ColorUtil.colorize(rawName);
            String petNameTag = section.getString(key + ".pet_name", rawName);
            String texture = section.getString(key + ".texture");
            String perm = section.getString(key + ".permission");
            double price = section.getDouble(key + ".price");
            List<String> buffNames = section.getStringList("buffs");
            double offX = section.getDouble(key + ".placement.x", 0.0);
            double offY = section.getDouble(key + ".placement.y", 0.0);
            double offZ = section.getDouble(key + ".placement.z", 0.0);
            ItemStack icon = SkullUtil.getCustomSkull(texture, coloredName);
            PetData data = new PetData(key, coloredName, petNameTag, icon, perm, price, offX, offY, offZ, buffNames, texture);
            loadedPets.add(data);
            petDataMap.put(key, data);
        }
    }

    public boolean isBuffDisabled(Player player, String buffName) {
        String petId = getActivePetId(player);
        if (petId == null) return false;
        return getPetData(player, petId).disabledBuffs.contains(buffName);
    }

    public void toggleBuff(Player player, String buffName) {
        String petId = getActivePetId(player);
        if (petId == null) return;

        PetSaveData data = getPetData(player, petId);
        if (data.disabledBuffs.contains(buffName)) {
            data.disabledBuffs.remove(buffName);
            player.sendMessage(plugin.getConfigManager().getMessage("buff-enabled", "%buff%", getBuffDisplayName(buffName)));
        } else {
            data.disabledBuffs.add(buffName);
            player.sendMessage(plugin.getConfigManager().getMessage("buff-disabled", "%buff%", getBuffDisplayName(buffName)));
        }
        saveAsync(player, petId);
    }

    public void toggleDefenseMode(Player player) {
        String petId = getActivePetId(player);
        if (petId == null) return;
        PetSaveData data = getPetData(player, petId);
        data.defenseMode = !data.defenseMode;
        if (!data.defenseMode) {
            player.setGravity(true);
        }
        player.sendMessage(ColorUtil.colorize(data.defenseMode ? "&aSavunma Modu Aktif! (Kubbe Koruması Açık)" : "&cSavunma Modu Kapalı."));
        saveAsync(player, petId);
    }

    public void toggleFavorite(Player player, String petId) {
        PetSaveData data = getPetData(player, petId);
        data.isFavorite = !data.isFavorite;
        player.sendMessage(ColorUtil.colorize(data.isFavorite ? "&aPet favorilere eklendi!" : "&cPet favorilerden çıkarıldı."));
        saveAsync(player, petId);
    }

    public boolean isGlowing(Player player) {
        String petId = getActivePetId(player);
        return petId != null && getPetData(player, petId).glow;
    }

    public void toggleGlow(Player player) {
        String petId = getActivePetId(player);
        if (petId == null) return;
        PetSaveData data = getPetData(player, petId);
        data.glow = !data.glow;
        if (activePets.containsKey(player.getUniqueId())) activePets.get(player.getUniqueId()).setGlowing(data.glow);
        player.sendMessage(ColorUtil.colorize(data.glow ? "&aParıldama açıldı!" : "&eParıldama kapatıldı."));
        saveAsync(player, petId);
    }

    public void setTrail(Player player, String particleName, String displayName) {
        String petId = getActivePetId(player);
        if (petId == null) return;
        try {
            Particle.valueOf(particleName);
            PetSaveData data = getPetData(player, petId);
            data.trail = particleName;
            activeTrails.put(player.getUniqueId(), Particle.valueOf(particleName));
            activeTrailNames.put(player.getUniqueId(), particleName);
            player.sendMessage(plugin.getConfigManager().getMessage("trail-selected", "%trail%", displayName));
            saveAsync(player, petId);
        } catch (Exception e) {}
    }

    public void removeTrail(Player player) {
        String petId = getActivePetId(player);
        if (petId == null) return;
        PetSaveData data = getPetData(player, petId);
        data.trail = null;
        activeTrails.remove(player.getUniqueId());
        activeTrailNames.remove(player.getUniqueId());
        player.sendMessage(plugin.getConfigManager().getMessage("trail-removed"));
        saveAsync(player, petId);
    }

    public void setPetArmor(Player player, Color color) {
        String petId = getActivePetId(player);
        if (petId == null) return;
        PetSaveData data = getPetData(player, petId);
        data.armorColor = color.asRGB();
        petArmorColors.put(player.getUniqueId(), color);
        spawnPet(player, petId);
        player.sendMessage(plugin.getConfigManager().getMessage("wardrobe-updated"));
        saveAsync(player, petId);
    }

    public void removePetArmor(Player player) {
        String petId = getActivePetId(player);
        if (petId == null) return;
        PetSaveData data = getPetData(player, petId);
        data.armorColor = null;
        petArmorColors.remove(player.getUniqueId());
        if (activePets.containsKey(player.getUniqueId())) activePets.get(player.getUniqueId()).getEquipment().setChestplate(null);
        player.sendMessage(plugin.getConfigManager().getMessage("wardrobe-cleared"));
        saveAsync(player, petId);
    }

    public void spawnPet(Player player, String petId) {
        if (isLagging || !isAllowed(player)) return;
        hiddenPets.remove(player.getUniqueId());
        removePet(player, false);
        UUID uid = player.getUniqueId();
        PetData baseData = petDataMap.get(petId);
        PetSaveData saveData = getPetData(player, petId);

        if (saveData.trail != null) {
            try {
                activeTrails.put(uid, Particle.valueOf(saveData.trail));
                activeTrailNames.put(uid, saveData.trail);
            } catch (Exception ignored) {}
        }
        if (saveData.armorColor != null) petArmorColors.put(uid, Color.fromRGB(saveData.armorColor));

        Location spawnLoc = player.getLocation().add(0, 1, 0);
        ArmorStand stand = player.getWorld().spawn(spawnLoc, ArmorStand.class, as -> {
            as.setVisible(false); as.setGravity(false); as.setSmall(true); as.setMarker(true);
            as.getPersistentDataContainer().set(petEntityKey, PersistentDataType.STRING, "pet");
            String texture = plugin.getConfigManager().getPetsConfig().getString("pets." + petId + ".texture");
            as.getEquipment().setHelmet(SkullUtil.getCustomSkull(texture, baseData.name));
            if (saveData.armorColor != null) {
                ItemStack armor = new ItemStack(Material.LEATHER_CHESTPLATE);
                LeatherArmorMeta meta = (LeatherArmorMeta) armor.getItemMeta();
                meta.setColor(Color.fromRGB(saveData.armorColor));
                armor.setItemMeta(meta);
                as.getEquipment().setChestplate(armor);
            }
            if (saveData.glow) as.setGlowing(true);
        });

        ArmorStand nameTag = player.getWorld().spawn(spawnLoc.clone().add(0, NAMETAG_HEIGHT, 0), ArmorStand.class, as -> {
            as.setVisible(false); as.setGravity(false); as.setMarker(true); as.setSmall(true); as.setCustomNameVisible(true);
            as.getPersistentDataContainer().set(petEntityKey, PersistentDataType.STRING, "pet");
        });

        activePets.put(uid, stand);
        activeNameTags.put(uid, nameTag);
        playerActivePetId.put(uid, petId);
        updateNameTag(player);
    }

    public void updatePets() {
        long now = System.currentTimeMillis();
        String mode = plugin.getConfig().getString("settings.placement.mode", "left-shoulder");
        double speed = plugin.getConfig().getDouble("settings.follow-speed", 0.2);
        double tpDist = plugin.getConfig().getDouble("settings.teleport-distance", 10.0);
        double configUp = plugin.getConfig().getDouble("settings.placement.shoulder.distance-up", 1.6);
        double finalUp = configUp - 0.9;
        double bobbing = Math.sin(System.currentTimeMillis() / 700.0) * 0.15;

        for (Map.Entry<UUID, ArmorStand> entry : new HashMap<>(activePets).entrySet()) {
            UUID uid = entry.getKey();
            Player player = Bukkit.getPlayer(uid);
            ArmorStand pet = entry.getValue();

            if (player == null || !player.isOnline() || pet == null || !pet.isValid()) {
                removePet(player, false); continue;
            }

            String petId = playerActivePetId.get(uid);
            PetSaveData saveData = getPetData(player, petId);

            if (saveData.glow && !pet.isGlowing()) pet.setGlowing(true);

            if (pet.getPassengers().contains(player)) {
                if (activeNameTags.containsKey(uid)) activeNameTags.get(uid).teleport(player.getLocation().add(0, 2.2, 0));
                continue;
            }

            PetData data = petDataMap.get(petId);

            if (data != null && data.buffNames != null) {
                int currentLevel = saveData.level;
                for (String buffName : data.buffNames) {
                    if (saveData.disabledBuffs.contains(buffName)) continue;
                    if (currentLevel >= getBuffRequiredLevel(buffName)) {
                        String effect = resolveBuffEffect(buffName);
                        if (effect != null) {
                            try {
                                String[] split = effect.split(":");
                                PotionEffectType type = PotionEffectType.getByName(split[0]);
                                int amp = Integer.parseInt(split[1]);
                                if (type != null) player.addPotionEffect(new PotionEffect(type, 60, amp, false, false, true));
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }

            if (plugin.getConfig().getBoolean("leveling.enabled")) {
                if (!lastXpGainTime.containsKey(uid) || (now - lastXpGainTime.get(uid) > 60000)) {
                    giveExp(player, plugin.getConfig().getDouble("leveling.xp-per-minute", 1.0));
                    lastXpGainTime.put(uid, now);
                }
            }

            if (!isAllowed(player)) { hiddenPets.put(uid, petId); removePet(player, false); continue; }

            Location target;
            Location pLoc = player.getLocation();

            if (saveData.defenseMode) {
                player.setGravity(false);
                player.setVelocity(new Vector(0, 0, 0));
                player.setFallDistance(0);

                double radius = 2.0;
                for (double y = 0; y <= Math.PI; y += Math.PI / 10) {
                    double r = Math.sin(y) * radius;
                    double h = Math.cos(y) * radius;
                    for (double a = 0; a < Math.PI * 2; a += Math.PI / 10) {
                        double x = Math.cos(a) * r;
                        double z = Math.sin(a) * r;
                        player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, pLoc.clone().add(x, h + 1, z), 1, 0, 0, 0, 0);
                    }
                }

                target = pLoc.clone().add(0, 2.5 + bobbing + data.offsetY, 0);

                Location petLoc = pet.getLocation().add(0, 0.5, 0);
                Location center = pLoc.clone().add(0, 1, 0);
                Vector dir = center.toVector().subtract(petLoc.toVector());
                double dist = dir.length();
                if (dist > 0.1) {
                    dir.normalize();
                    for (double i = 0; i < dist; i += 0.2) {
                        player.getWorld().spawnParticle(Particle.ENCHANT, petLoc.clone().add(dir.clone().multiply(i)), 1, 0, 0, 0, 0);
                    }
                }

                for (Entity entity : player.getNearbyEntities(2.5, 2.5, 2.5)) {
                    if (entity instanceof LivingEntity && !(entity instanceof Player) && !(entity instanceof ArmorStand)) {
                        Vector pushDirection = entity.getLocation().toVector().subtract(player.getLocation().toVector());
                        if (pushDirection.lengthSquared() < 0.01) {
                            pushDirection = new Vector(Math.random() - 0.5, 0, Math.random() - 0.5);
                        }
                        pushDirection = pushDirection.normalize().multiply(1.5).setY(0.6);
                        entity.setVelocity(pushDirection);
                    }
                }

            } else {
                if (!player.hasGravity()) player.setGravity(true);

                Vector dir = pLoc.getDirection().setY(0).normalize();
                if (mode.equals("left-shoulder") || mode.equals("right-shoulder")) {
                    int sideMultiplier = mode.equals("left-shoulder") ? 1 : -1;
                    Vector side = new Vector(dir.getZ(), 0, -dir.getX()).normalize().multiply(0.75 * sideMultiplier);
                    Vector back = dir.clone().multiply(-0.3);
                    target = pLoc.clone().add(side).add(back).add(0, finalUp + bobbing + data.offsetY, 0);
                    target.setYaw(pLoc.getYaw());
                } else {
                    target = pLoc.clone().add(pLoc.getDirection().multiply(-1.5)).add(0, 1.5 + bobbing + data.offsetY, 0);
                }
            }

            if (!saveData.defenseMode && (!player.getWorld().equals(pet.getWorld()) || pet.getLocation().distance(player.getLocation()) > tpDist)) {
                pet.teleport(player.getLocation());
                if (activeNameTags.containsKey(uid)) activeNameTags.get(uid).teleport(player.getLocation().add(0, NAMETAG_HEIGHT, 0));
            } else {
                Location current = pet.getLocation();
                Vector velocity = target.toVector().subtract(current.toVector()).multiply(speed);
                Location newLoc = current.add(velocity);
                newLoc.setYaw(player.getLocation().getYaw());

                if (velocity.length() > 0.05 && activeTrails.containsKey(uid)) {
                    pet.getWorld().spawnParticle(activeTrails.get(uid), pet.getLocation().add(0, 0.5, 0), 1, 0,0,0,0);
                }

                pet.teleport(newLoc);
                if (activeNameTags.containsKey(uid)) activeNameTags.get(uid).teleport(newLoc.clone().add(0, NAMETAG_HEIGHT, 0));
            }
        }
    }

    public void giveExp(Player player, double amount) {
        String petId = getActivePetId(player);
        if (petId == null) return;
        PetSaveData data = getPetData(player, petId);
        int maxLevel = plugin.getConfig().getInt("leveling.max-level", 50);
        if (data.level >= maxLevel) return;
        data.exp += amount;
        double reqExp = getRequiredExp(data.level);
        if (data.exp >= reqExp) {
            data.exp -= reqExp;
            data.level++;
            if (data.level > maxLevel) data.level = maxLevel;
            player.sendMessage(ColorUtil.colorize("&aTebrikler! Petin &eLevel " + data.level + " &aoldu!"));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
            updateNameTag(player);
        }
        saveAsync(player, petId);
    }

    private void updateNameTag(Player player) {
        if (!activeNameTags.containsKey(player.getUniqueId())) return;
        String petId = getActivePetId(player);
        PetSaveData data = getPetData(player, petId);
        String format = plugin.getConfig().getString("leveling.name-format", "%name% &e(Lvl. %level%)");
        String finalName = ColorUtil.colorize(format.replace("%name%", data.customName).replace("%level%", String.valueOf(data.level)));
        ArmorStand nt = activeNameTags.get(player.getUniqueId());
        if (nt != null && !nt.isDead()) nt.setCustomName(finalName);
    }

    private void saveAsync(Player player, String petId) {
        PetSaveData data = getPetData(player, petId);
        Bukkit.getScheduler().runTaskAsynchronously(plugin.getBukkitPlugin(), () -> {
            plugin.getDatabaseManager().savePetData(player.getUniqueId(), petId, data.customName, data.level, data.exp, data.trail, data.armorColor, data.glow, data.disabledBuffs, data.isFavorite, data.defenseMode);
        });
    }

    public void removePet(Player player, boolean clearData) {
        UUID uid = player.getUniqueId();
        if(activePets.containsKey(uid)) { activePets.get(uid).remove(); activePets.remove(uid); }
        if(activeNameTags.containsKey(uid)) { activeNameTags.get(uid).remove(); activeNameTags.remove(uid); }
        String pId = playerActivePetId.remove(uid);
        activeTrails.remove(uid); activeTrailNames.remove(uid); petArmorColors.remove(uid);
        player.setGravity(true);
        if(clearData && pId != null) { hiddenPets.remove(uid); player.sendMessage(plugin.getConfigManager().getMessage("despawned", "%pet%", "Pet")); saveAsync(player, pId); }
    }

    public ArmorStand getActivePetEntity(Player player) { return activePets.get(player.getUniqueId()); }
    public String getActivePetId(Player player) { return playerActivePetId.get(player.getUniqueId()); }
    public int getPetLevel(Player player) { String pid = getActivePetId(player); return pid != null ? getPetData(player, pid).level : 1; }
    public double getPetExp(Player player) { String pid = getActivePetId(player); return pid != null ? getPetData(player, pid).exp : 0.0; }
    public String getPetNameOnly(Player player) { String pid = getActivePetId(player); return pid != null ? getPetData(player, pid).customName : "Pet"; }
    public double getRequiredExp(int level) { int minutes = plugin.getConfig().getInt("leveling.level-minutes." + level, -1); return (minutes != -1) ? minutes * plugin.getConfig().getDouble("leveling.xp-per-minute", 1.0) : ((level * 10) + 30) * plugin.getConfig().getDouble("leveling.xp-per-minute", 1.0); }
    public String getTimeLeftString(Player player) { int level = getPetLevel(player); int max = plugin.getConfig().getInt("leveling.max-level", 50); if (level >= max) return "MAX"; int mins = (int) Math.ceil((getRequiredExp(level) - getPetExp(player)) / plugin.getConfig().getDouble("leveling.xp-per-minute", 1.0)); return mins + " Dk"; }
    public List<PetData> getLoadedPets() { return loadedPets; }
    public String getActiveTrailId(Player player) { return activeTrailNames.get(player.getUniqueId()); }
    public int getActivePetCount() { return activePets.size(); }
    public Color getPetArmorColor(Player player) { return petArmorColors.get(player.getUniqueId()); }

    public void removeAllPets() {
        activePets.keySet().forEach(uid -> {
            Player p = Bukkit.getPlayer(uid);
            if (p != null && p.isOnline()) p.setGravity(true);
        });
        activePets.values().forEach(ArmorStand::remove);
        activeNameTags.values().forEach(ArmorStand::remove);
        activePets.clear();
        activeNameTags.clear();

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(ArmorStand.class)) {
                if (entity.getPersistentDataContainer().has(petEntityKey, PersistentDataType.STRING)) {
                    entity.remove();
                }
            }
        }
    }

    public boolean isAllowed(Player player) { return true; }
    public void ridePet(Player player) { if (!activePets.containsKey(player.getUniqueId())) { player.sendMessage(ColorUtil.colorize("&cPetin yok!")); return; } ArmorStand pet = activePets.get(player.getUniqueId()); if (pet.getPassengers().contains(player)) pet.removePassenger(player); else { pet.addPassenger(player); player.sendMessage(plugin.getConfigManager().getMessage("ride-success")); } }

    public int getBuffRequiredLevel(String b) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("buff-definitions");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                if (key.equalsIgnoreCase(b)) {
                    return plugin.getConfig().getInt("buff-definitions." + key + ".level_required", 0);
                }
            }
        }
        return 0;
    }

    public String resolveBuffEffect(String b) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("buff-definitions");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                if (key.equalsIgnoreCase(b)) {
                    return plugin.getConfig().getString("buff-definitions." + key + ".effect");
                }
            }
        }
        return null;
    }

    public String getBuffDisplayName(String b) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("buff-definitions");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                if (key.equalsIgnoreCase(b)) {
                    String n = plugin.getConfig().getString("buff-definitions." + key + ".display");
                    return ColorUtil.colorize(n != null ? n : b);
                }
            }
        }
        return ColorUtil.colorize(b);
    }

    public static class PetSaveData {
        public String customName; public int level; public double exp; public String trail; public Integer armorColor; public boolean glow; public Set<String> disabledBuffs; public boolean isFavorite; public boolean defenseMode;
        public PetSaveData(String name, int lvl, double xp, String tr, Integer armor, boolean gl, Set<String> buffs, boolean fav, boolean def) { this.customName = name; this.level = lvl; this.exp = xp; this.trail = tr; this.armorColor = armor; this.glow = gl; this.disabledBuffs = buffs; this.isFavorite = fav; this.defenseMode = def; }
    }
    public static class PetData {
        public String id, name, nameTag, permission, texture; public ItemStack icon; public double price, offsetX, offsetY, offsetZ; public List<String> buffNames;
        public PetData(String id, String name, String nameTag, ItemStack icon, String permission, double price, double x, double y, double z, List<String> buffNames, String texture) { this.id = id; this.name = name; this.nameTag = nameTag; this.icon = icon; this.permission = permission; this.price = price; this.offsetX = x; this.offsetY = y; this.offsetZ = z; this.buffNames = buffNames; this.texture = texture; }
    }
}