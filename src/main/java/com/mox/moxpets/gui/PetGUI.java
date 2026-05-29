package com.mox.moxpets.gui;

import com.mox.moxpets.MyPets;
import com.mox.moxpets.managers.PetManager;
import com.mox.moxpets.utils.ColorUtil;
import com.mox.moxpets.utils.SkullUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class PetGUI implements Listener {

    private final MyPets plugin;
    private final Map<UUID, String> pendingPurchase = new HashMap<>();
    private final NamespacedKey petIdKey;

    private final String GREEN_CHECK_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDMxMmNhNDYzMmRlZjVmZmFmMmViMGQ5ZDdjYzdiNTVhNTBjNGUzOTIwZDkwMzcyYWFiMTQwNzgxZjVkZmJjNCJ9fX0=";

    public PetGUI(MyPets plugin) {
        this.plugin = plugin;
        this.petIdKey = new NamespacedKey(plugin.getBukkitPlugin(), "pet_id");
    }

    public void openMainMenu(Player player) {
        buildAndOpenMenu(player, plugin.getConfigManager().getMainMenuConfig(), null, 0, "MAIN");
    }

    public void openOwnedMenu(Player player, int page) {
        List<PetManager.PetData> ownedPets = new ArrayList<>();
        for (PetManager.PetData pet : plugin.getPetManager().getLoadedPets()) {
            if (player.hasPermission(pet.permission)) {
                ownedPets.add(pet);
            }
        }
        buildAndOpenMenu(player, plugin.getConfigManager().getOwnedMenuConfig(), ownedPets, page, "OWNED");
    }

    public void openFavoritesMenu(Player player, int page) {
        List<PetManager.PetData> favPets = new ArrayList<>();
        for (PetManager.PetData pet : plugin.getPetManager().getLoadedPets()) {
            if (player.hasPermission(pet.permission)) {
                PetManager.PetSaveData data = plugin.getPetManager().getPetData(player, pet.id);
                if (data.isFavorite) {
                    favPets.add(pet);
                }
            }
        }
        buildAndOpenMenu(player, plugin.getConfigManager().getFavoritesMenuConfig(), favPets, page, "FAVORITES");
    }

    public void openSettingsMenu(Player player) {
        String petId = plugin.getPetManager().getActivePetId(player);
        if (petId == null) {
            player.sendMessage(ColorUtil.colorize("&cBu menüyü açmak için önce bir pet çağırmalısın!"));
            return;
        }
        buildAndOpenMenu(player, plugin.getConfigManager().getUpgradesMenuConfig(), null, 0, "SETTINGS");
    }

    public void openBuffsMenu(Player player) {
        String petId = plugin.getPetManager().getActivePetId(player);
        if (petId == null) {
            player.sendMessage(ColorUtil.colorize("&cÖnce bir pet çağırmalısın!"));
            return;
        }
        buildAndOpenMenu(player, plugin.getConfigManager().getBuffsMenuConfig(), null, 0, "BUFFS");
    }

    public void openShopMenu(Player player, int page) {
        List<PetManager.PetData> allPets = plugin.getPetManager().getLoadedPets();
        buildAndOpenMenu(player, plugin.getConfigManager().getShopMenuConfig(), allPets, page, "SHOP");
    }

    public void openTrailsMenu(Player player) {
        buildAndOpenMenu(player, plugin.getConfigManager().getTrailsMenuConfig(), null, 0, "TRAILS");
    }

    public void openWardrobeMenu(Player player) {
        if (!player.hasPermission("moxpets.wardrobe")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission", "%perm%", "moxpets.wardrobe"));
            return;
        }
        buildAndOpenMenu(player, plugin.getConfigManager().getWardrobeMenuConfig(), null, 0, "WARDROBE");
    }

    public void openConfirmationMenu(Player player, PetManager.PetData pet) {
        pendingPurchase.put(player.getUniqueId(), pet.id);
        FileConfiguration config = plugin.getConfigManager().getConfirmMenuConfig();
        int size = config.getInt("menu.size", 27);
        String title = ColorUtil.colorize(config.getString("menu.title", "Onaylıyor musun?"));

        Inventory gui = Bukkit.createInventory(new PetMenuHolder("CONFIRM"), size, title);
        List<String> pattern = config.getStringList("menu.pattern");
        int slotCounter = 0;

        for (String line : pattern) {
            String processedLine = parsePatternLine(line);
            for (int i = 0; i < 9; i++) {
                if (slotCounter >= gui.getSize()) break;
                char c = processedLine.charAt(i);

                if (c == ' ') {
                    gui.setItem(slotCounter, createFillerGlass());
                } else if (c == 'P') {
                    gui.setItem(slotCounter, createPetItem(player, pet, config, "CONFIRM"));
                } else {
                    ConfigurationSection itemSec = config.getConfigurationSection("menu.items." + c);
                    if (itemSec != null) {
                        gui.setItem(slotCounter, createItem(itemSec));
                    } else {
                        gui.setItem(slotCounter, createFillerGlass());
                    }
                }
                slotCounter++;
            }
        }
        player.openInventory(gui);
    }

    private void buildAndOpenMenu(Player player, FileConfiguration config, List<PetManager.PetData> petsToList, int page, String menuType) {
        int size = config.getInt("menu.size", 54);
        List<String> pattern = config.getStringList("menu.pattern");

        String activePetId = plugin.getPetManager().getActivePetId(player);
        String petName = (activePetId != null) ? plugin.getPetManager().getPetNameOnly(player) : "Pet";

        int petSlotsCount = 0;
        for (String line : pattern) {
            for (char c : line.replace(" ", "").toCharArray()) {
                if (c == '@' || c == '#') petSlotsCount++;
            }
        }

        int totalPages = 1;
        if (petsToList != null && !petsToList.isEmpty() && petSlotsCount > 0) {
            totalPages = (int) Math.ceil((double) petsToList.size() / petSlotsCount);
        }
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        String titleRaw = config.getString("menu.title", "MoxPets");
        String titleFinal = ColorUtil.colorize(titleRaw
                .replace("%page%", String.valueOf(page + 1))
                .replace("%max%", String.valueOf(totalPages))
                .replace("%pet%", petName));

        Inventory gui = Bukkit.createInventory(new PetMenuHolder(menuType), size, titleFinal);

        int slotCounter = 0;
        int currentPetIndex = 0;
        int petIndexStart = page * petSlotsCount;

        List<String> activeBuffs = new ArrayList<>();
        if (menuType.equals("BUFFS") && activePetId != null) {
            PetManager.PetData pData = plugin.getPetManager().getLoadedPets().stream()
                    .filter(p -> p.id.equals(activePetId)).findFirst().orElse(null);
            if (pData != null && pData.buffNames != null) {
                activeBuffs = pData.buffNames;
            }
        }
        int buffIndex = 0;

        for (String line : pattern) {
            String processedLine = parsePatternLine(line);

            for (int i = 0; i < 9; i++) {
                if (slotCounter >= size) break;
                char c = processedLine.charAt(i);

                if (c == ' ') {
                    gui.setItem(slotCounter, createFillerGlass());
                }
                else if ((c == '@' || c == '#') && (menuType.equals("OWNED") || menuType.equals("SHOP") || menuType.equals("FAVORITES")) && petsToList != null) {
                    if (petIndexStart + currentPetIndex < petsToList.size()) {
                        PetManager.PetData petData = petsToList.get(petIndexStart + currentPetIndex);
                        gui.setItem(slotCounter, createPetItem(player, petData, config, menuType));
                        currentPetIndex++;
                    } else {
                        gui.setItem(slotCounter, createFillerGlass());
                    }
                }
                else if (c == '@' && menuType.equals("BUFFS")) {
                    if (buffIndex < activeBuffs.size()) {
                        gui.setItem(slotCounter, createBuffItem(player, activeBuffs.get(buffIndex), config));
                        buffIndex++;
                    } else if (buffIndex == 0 && activeBuffs.isEmpty()) {
                        ConfigurationSection noBuff = config.getConfigurationSection("menu.items.no-buff-item");
                        if (noBuff != null) gui.setItem(slotCounter, createItem(noBuff));
                        buffIndex++;
                    } else {
                        gui.setItem(slotCounter, createFillerGlass());
                    }
                }
                else {
                    ConfigurationSection itemSec = config.getConfigurationSection("menu.items." + c);
                    if (itemSec != null) {
                        String action = itemSec.getString("action", "");
                        boolean shouldShow = true;
                        if (action.equals("PREVIOUS_PAGE") && page == 0) shouldShow = false;
                        if (action.equals("NEXT_PAGE") && page >= totalPages - 1) shouldShow = false;

                        if (shouldShow) {
                            ItemStack item;
                            if (menuType.equals("SETTINGS")) {
                                item = createSettingsItem(player, itemSec);
                            }
                            else if (menuType.equals("WARDROBE") && itemSec.contains("color")) {
                                item = createArmorItem(player, itemSec);
                            }
                            else if (menuType.equals("TRAILS") && itemSec.contains("value")) {
                                item = createItem(itemSec);
                                String val = itemSec.getString("value");
                                String activeTrail = plugin.getPetManager().getActiveTrailId(player);
                                if (activeTrail != null && activeTrail.equals(val)) {
                                    String label = plugin.getConfigManager().getMessage("menu-label-selected");
                                    item = SkullUtil.getCustomSkull(GREEN_CHECK_TEXTURE, "&a" + itemSec.getString("name") + " " + label);
                                }
                            }
                            else {
                                item = createItem(itemSec);
                            }
                            gui.setItem(slotCounter, item);
                        } else {
                            gui.setItem(slotCounter, createFillerGlass());
                        }
                    } else {
                        gui.setItem(slotCounter, createFillerGlass());
                    }
                }
                slotCounter++;
            }
        }
        player.openInventory(gui);
    }

    private String parsePatternLine(String line) {
        String processedLine = line;
        if (processedLine.length() > 9 && processedLine.contains(" ")) {
            processedLine = processedLine.replace(" ", "");
        }
        while (processedLine.length() < 9) {
            processedLine += " ";
        }
        return processedLine;
    }

    private ItemStack createSettingsItem(Player player, ConfigurationSection sec) {
        if (sec.getName().equals("P")) {
            String activePetId = plugin.getPetManager().getActivePetId(player);
            if (activePetId != null) {
                PetManager.PetData pData = plugin.getPetManager().getLoadedPets().stream()
                        .filter(p -> p.id.equals(activePetId)).findFirst().orElse(null);
                if (pData != null) return createPetItem(player, pData, plugin.getConfigManager().getUpgradesMenuConfig(), "SETTINGS");
            }
        }

        if (sec.getName().equals("G")) {
            boolean isGlowing = plugin.getPetManager().isGlowing(player);
            ItemStack item = createItem(sec);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                if (meta.hasLore()) {
                    List<String> newLore = new ArrayList<>();
                    for (String line : meta.getLore()) {
                        newLore.add(line.replace("%glow_status%", isGlowing ? "&aAÇIK" : "&cKAPALI"));
                    }
                    meta.setLore(newLore);
                }
                if (isGlowing) {
                    Enchantment dur = Enchantment.getByName("DURABILITY");
                    if (dur != null) meta.addEnchant(dur, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }
                item.setItemMeta(meta);
            }
            return item;
        }

        if (sec.getName().equals("S")) {
            String activePetId = plugin.getPetManager().getActivePetId(player);
            boolean isDefense = false;
            if (activePetId != null) isDefense = plugin.getPetManager().getPetData(player, activePetId).defenseMode;
            ItemStack item = createItem(sec);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                if (meta.hasLore()) {
                    List<String> newLore = new ArrayList<>();
                    for (String line : meta.getLore()) {
                        newLore.add(line.replace("%status%", isDefense ? "&aAÇIK" : "&cKAPALI"));
                    }
                    meta.setLore(newLore);
                }
                if (isDefense) {
                    Enchantment dur = Enchantment.getByName("DURABILITY");
                    if (dur != null) meta.addEnchant(dur, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }
                item.setItemMeta(meta);
            }
            return item;
        }

        return createItem(sec);
    }

    private ItemStack createBuffItem(Player player, String buffName, FileConfiguration config) {
        int reqLevel = plugin.getPetManager().getBuffRequiredLevel(buffName);
        int currentLevel = plugin.getPetManager().getPetLevel(player);
        boolean isLocked = currentLevel < reqLevel;
        boolean isDisabled = plugin.getPetManager().isBuffDisabled(player, buffName);

        String path;
        if (isLocked) path = "buff-template.locked";
        else if (isDisabled) path = "buff-template.deactived";
        else path = "buff-template.actived";

        ConfigurationSection sec = config.getConfigurationSection("menu.items." + path);
        if (sec == null) sec = config.getConfigurationSection("menu.items.buff-template.locked");
        if (sec == null) return new ItemStack(Material.STONE);

        String matName = sec.getString("material", "POTION");
        Material mat = Material.getMaterial(matName);
        if (mat == null) mat = Material.POTION;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        String displayName = plugin.getPetManager().getBuffDisplayName(buffName);
        if (sec.contains("name")) {
            meta.setDisplayName(ColorUtil.colorize(sec.getString("name").replace("%buff_name%", displayName)));
        }

        if (!isLocked && !isDisabled && item.getType() == Material.POTION) {
            ((PotionMeta) meta).setColor(Color.AQUA);
            try { meta.addItemFlags(ItemFlag.valueOf("HIDE_POTION_EFFECTS")); } catch (Exception e) {}
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        List<String> lore = sec.getStringList("lore");
        List<String> finalLore = new ArrayList<>();

        String statusText;
        if (isLocked) statusText = "&cKİLİTLİ 🔒";
        else if (isDisabled) statusText = "&cKAPALI ✖";
        else statusText = "&aAÇIK ✔";

        for (String line : lore) {
            finalLore.add(ColorUtil.colorize(line
                    .replace("%buff_name%", displayName)
                    .replace("%status%", statusText)
                    .replace("%required_level%", String.valueOf(reqLevel))
                    .replace("%level%", String.valueOf(currentLevel))
            ));
        }
        meta.setLore(finalLore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPetItem(Player player, PetManager.PetData pet, FileConfiguration config, String menuType) {
        ItemStack item;
        boolean isActive = pet.id.equals(plugin.getPetManager().getActivePetId(player));
        boolean hasPerm = player.hasPermission(pet.permission);

        String labelSelected = plugin.getConfigManager().getMessage("menu-label-selected");
        String labelOwned = plugin.getConfigManager().getMessage("menu-label-owned");

        if (isActive && (menuType.equals("OWNED") || menuType.equals("FAVORITES"))) {
            item = SkullUtil.getCustomSkull(GREEN_CHECK_TEXTURE, "&a" + pet.name + " " + labelSelected);
        } else if (menuType.equals("SHOP") && hasPerm) {
            item = SkullUtil.getCustomSkull(GREEN_CHECK_TEXTURE, "&a" + pet.name + " " + labelOwned);
        } else {
            item = pet.icon.clone();
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(petIdKey, PersistentDataType.STRING, pet.id);

            List<String> loreFormat = new ArrayList<>();
            if (menuType.equals("OWNED") || menuType.equals("FAVORITES")) {
                loreFormat = config.getStringList("menu.items.pet-template.lore");
            }
            else if (menuType.equals("SETTINGS")) {
                ConfigurationSection pSec = config.getConfigurationSection("menu.items.P");
                if (pSec != null) loreFormat = pSec.getStringList("lore");
            }
            else if (menuType.equals("SHOP")) {
                if (hasPerm) loreFormat = config.getStringList("menu.items.pet-template.lore-owned");
                else loreFormat = config.getStringList("menu.items.pet-template.lore");
            }

            String status = isActive ? plugin.getConfigManager().getMessage("menu-status-active") : plugin.getConfigManager().getMessage("menu-status-passive");
            String action = isActive ? plugin.getConfigManager().getMessage("menu-click-despawn") : plugin.getConfigManager().getMessage("menu-click-spawn");

            int level = isActive ? plugin.getPetManager().getPetLevel(player) : 1;
            double curXp = isActive ? plugin.getPetManager().getPetExp(player) : 0;
            double reqXp = plugin.getPetManager().getRequiredExp(level);
            int maxLevel = plugin.getConfig().getInt("leveling.max-level", 50);

            String customName = isActive ? plugin.getPetManager().getPetNameOnly(player) : pet.nameTag;
            String timeLeft = isActive ? plugin.getPetManager().getTimeLeftString(player) : "---";

            String progressBar;
            String percentage;
            String curXpStr = String.format("%.0f", curXp);
            String reqXpStr = String.format("%.0f", reqXp);

            if (level >= maxLevel) {
                StringBuilder bar = new StringBuilder("&b");
                for(int i=0; i<20; i++) bar.append("|");
                progressBar = bar.toString();
                percentage = "MAX";
                reqXpStr = "MAX";
                timeLeft = "&bMAX SEVİYE";
            } else {
                int totalBars = 20;
                int filledBars = 0;
                if (reqXp > 0) filledBars = (int) ((curXp / reqXp) * totalBars);
                if (filledBars > 20) filledBars = 20;

                StringBuilder bar = new StringBuilder("&a");
                for(int i=0; i<filledBars; i++) bar.append("|");
                bar.append("&7");
                for(int i=filledBars; i<totalBars; i++) bar.append("|");

                progressBar = bar.toString();
                percentage = (reqXp > 0) ? String.valueOf((int) ((curXp / reqXp) * 100)) : "0";
            }

            List<String> finalLore = new ArrayList<>();
            for (String line : loreFormat) {
                finalLore.add(ColorUtil.colorize(line
                        .replace("%pet_name%", pet.name)
                        .replace("%custom_name%", customName)
                        .replace("%time_left%", timeLeft)
                        .replace("%price%", String.valueOf(pet.price))
                        .replace("%status%", status)
                        .replace("%action%", action)
                        .replace("%level%", (level >= maxLevel ? "MAX" : String.valueOf(level)))
                        .replace("%current_xp%", curXpStr)
                        .replace("%xp_current%", curXpStr)
                        .replace("%req_xp%", reqXpStr)
                        .replace("%xp_req%", reqXpStr)
                        .replace("%progress_bar%", progressBar)
                        .replace("%percentage%", percentage)));
            }

            if (menuType.equals("SHOP") && pet.buffNames != null && !pet.buffNames.isEmpty()) {
                finalLore.add("");
                finalLore.add(ColorUtil.colorize("&7Sahip Olduğu Özellikler:"));
                for (String bName : pet.buffNames) {
                    finalLore.add(ColorUtil.colorize("&8- &a" + plugin.getPetManager().getBuffDisplayName(bName)));
                }
            }

            if (menuType.equals("OWNED") || menuType.equals("FAVORITES")) {
                finalLore.add("");
                boolean isFav = plugin.getPetManager().getPetData(player, pet.id).isFavorite;
                if (menuType.equals("OWNED")) {
                    finalLore.add(ColorUtil.colorize(isFav ? "&e[MMB - Tekerlek] &fFavorilerden Çıkar" : "&e[MMB - Tekerlek] &fFavorilere Ekle"));
                } else {
                    finalLore.add(ColorUtil.colorize("&c[Q Tuşu] &fFavorilerden Çıkar"));
                }
            }

            meta.setLore(finalLore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createItem(ConfigurationSection sec) {
        if (sec.getString("material").equals("AIR")) return new ItemStack(Material.AIR);
        String matName = sec.getString("material", "STONE");
        Material mat = Material.getMaterial(matName);
        if (mat == null) mat = Material.STONE;

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (sec.contains("name")) meta.setDisplayName(ColorUtil.colorize(sec.getString("name")));
            if (sec.contains("lore")) {
                List<String> l = new ArrayList<>();
                for (String s : sec.getStringList("lore")) l.add(ColorUtil.colorize(s));
                meta.setLore(l);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createArmorItem(Player player, ConfigurationSection sec) {
        String[] rgb = sec.getString("color").split(",");
        Color itemColor = Color.fromRGB(Integer.parseInt(rgb[0]), Integer.parseInt(rgb[1]), Integer.parseInt(rgb[2]));
        Color activeColor = plugin.getPetManager().getPetArmorColor(player);

        if (activeColor != null && activeColor.equals(itemColor)) {
            String label = plugin.getConfigManager().getMessage("menu-label-selected");
            return SkullUtil.getCustomSkull(GREEN_CHECK_TEXTURE, "&a" + sec.getString("name") + " " + label);
        }

        ItemStack item = new ItemStack(Material.LEATHER_CHESTPLATE);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        if (meta != null) {
            meta.setColor(itemColor);
            meta.setDisplayName(ColorUtil.colorize(sec.getString("name")));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_DYE);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createFillerGlass() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof PetMenuHolder)) return;
        event.setCancelled(true);

        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        Player player = (Player) event.getWhoClicked();
        PetMenuHolder holder = (PetMenuHolder) event.getInventory().getHolder();
        String menuType = holder.getMenuType();

        if (item.getItemMeta().getPersistentDataContainer().has(petIdKey, PersistentDataType.STRING)) {
            String petId = item.getItemMeta().getPersistentDataContainer().get(petIdKey, PersistentDataType.STRING);
            PetManager.PetData petData = plugin.getPetManager().getLoadedPets().stream()
                    .filter(p -> p.id.equals(petId)).findFirst().orElse(null);

            if (menuType.equals("OWNED") || menuType.equals("FAVORITES")) {
                if (event.getClick() == ClickType.MIDDLE) {
                    if (menuType.equals("OWNED")) {
                        plugin.getPetManager().toggleFavorite(player, petId);
                        openOwnedMenu(player, getCurrentPage(event.getView().getTitle()));
                    }
                } else if (event.getClick() == ClickType.DROP) {
                    if (menuType.equals("FAVORITES")) {
                        plugin.getPetManager().toggleFavorite(player, petId);
                        openFavoritesMenu(player, getCurrentPage(event.getView().getTitle()));
                    }
                } else if (event.getClick() == ClickType.RIGHT) {
                    if (!petId.equals(plugin.getPetManager().getActivePetId(player))) {
                        if (!plugin.getPetManager().checkCooldown(player)) {
                            player.sendMessage(plugin.getConfigManager().getMessage("cooldown"));
                            return;
                        }
                        plugin.getPetManager().spawnPet(player, petId);
                    }
                    openSettingsMenu(player);
                } else {
                    handlePetClick(player, petData, menuType);
                }
            } else {
                handlePetClick(player, petData, menuType);
            }
            return;
        }

        if (menuType.equals("BUFFS")) {
            FileConfiguration config = plugin.getConfigManager().getBuffsMenuConfig();
            List<String> pattern = config.getStringList("menu.pattern");
            int slot = event.getSlot();
            if (slot / 9 < pattern.size()) {
                String processedLine = parsePatternLine(pattern.get(slot / 9));
                char symbol = processedLine.charAt(slot % 9);
                if (symbol == '@') {
                    int clickedIndex = -1;
                    int currentScanIndex = 0;
                    for(int i=0; i<pattern.size(); i++) {
                        String pLine = parsePatternLine(pattern.get(i));
                        for(int j=0; j<9; j++) {
                            if(pLine.charAt(j) == '@') {
                                if ((i * 9 + j) == slot) {
                                    clickedIndex = currentScanIndex;
                                    break;
                                }
                                currentScanIndex++;
                            }
                        }
                        if (clickedIndex != -1) break;
                    }
                    String activePetId = plugin.getPetManager().getActivePetId(player);
                    if (activePetId != null) {
                        PetManager.PetData pData = plugin.getPetManager().getLoadedPets().stream().filter(p -> p.id.equals(activePetId)).findFirst().orElse(null);
                        if (pData != null && pData.buffNames != null && clickedIndex < pData.buffNames.size()) {
                            String buffName = pData.buffNames.get(clickedIndex);
                            int req = plugin.getPetManager().getBuffRequiredLevel(buffName);
                            int lvl = plugin.getPetManager().getPetLevel(player);

                            if (lvl >= req) {
                                plugin.getPetManager().toggleBuff(player, buffName);
                                openBuffsMenu(player);
                                player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1, 1);
                            } else {
                                player.sendMessage(ColorUtil.colorize("&cBu özellik için Level " + req + " gerekli!"));
                                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_PLACE, 1, 1);
                            }
                        }
                    }
                    return;
                }
            }
        }

        FileConfiguration currentConfig = getConfigByType(menuType);
        List<String> pattern = currentConfig.getStringList("menu.pattern");
        int slot = event.getSlot();
        if (slot / 9 >= pattern.size()) return;

        String processedLine = parsePatternLine(pattern.get(slot / 9));
        char symbol = processedLine.charAt(slot % 9);
        ConfigurationSection itemSec = currentConfig.getConfigurationSection("menu.items." + symbol);

        if (itemSec != null && itemSec.contains("action")) {
            String action = itemSec.getString("action");
            switch (action) {
                case "OPEN_OWNED": openOwnedMenu(player, 0); break;
                case "OPEN_FAVORITES": openFavoritesMenu(player, 0); break;
                case "OPEN_SHOP": openShopMenu(player, 0); break;
                case "OPEN_MAIN": openMainMenu(player); break;
                case "OPEN_TRAILS": openTrailsMenu(player); break;
                case "OPEN_WARDROBE": openWardrobeMenu(player); break;
                case "OPEN_BUFFS": openBuffsMenu(player); break;
                case "OPEN_SETTINGS": openSettingsMenu(player); break;

                case "TOGGLE_GLOW":
                    plugin.getPetManager().toggleGlow(player);
                    openSettingsMenu(player);
                    break;

                case "TOGGLE_DEFENSE":
                    plugin.getPetManager().toggleDefenseMode(player);
                    openSettingsMenu(player);
                    break;

                case "RIDE_PET":
                    plugin.getPetManager().ridePet(player);
                    player.closeInventory();
                    break;

                case "REMOVE_PET":
                    if (!plugin.getPetManager().checkCooldown(player)) {
                        player.sendMessage(plugin.getConfigManager().getMessage("cooldown"));
                        return;
                    }
                    plugin.getPetManager().removePet(player, true);
                    player.closeInventory();
                    break;

                case "CLOSE": player.closeInventory(); break;

                case "SET_TRAIL":
                    String particleName = itemSec.getString("value");
                    String displayName = itemSec.getString("name");
                    String perm = "moxpets.trails." + particleName.toLowerCase();
                    if (player.hasPermission(perm) || player.hasPermission("moxpets.trails.*") || player.isOp()) {
                        plugin.getPetManager().setTrail(player, particleName, displayName);
                        openTrailsMenu(player);
                    } else {
                        player.sendMessage(plugin.getConfigManager().getMessage("no-permission", "%perm%", perm));
                    }
                    break;

                case "REMOVE_TRAIL":
                    plugin.getPetManager().removeTrail(player);
                    openTrailsMenu(player);
                    break;

                case "SET_ARMOR":
                    String[] rgb = itemSec.getString("color").split(",");
                    plugin.getPetManager().setPetArmor(player, Color.fromRGB(Integer.parseInt(rgb[0]), Integer.parseInt(rgb[1]), Integer.parseInt(rgb[2])));
                    openWardrobeMenu(player);
                    break;

                case "REMOVE_ARMOR":
                    plugin.getPetManager().removePetArmor(player);
                    openWardrobeMenu(player);
                    break;

                case "CONFIRM_BUY": handlePurchase(player); break;
                case "CANCEL_BUY":
                    pendingPurchase.remove(player.getUniqueId());
                    openShopMenu(player, 0);
                    break;

                case "NEXT_PAGE":
                    if(menuType.equals("OWNED")) openOwnedMenu(player, getCurrentPage(event.getView().getTitle()) + 1);
                    else if (menuType.equals("FAVORITES")) openFavoritesMenu(player, getCurrentPage(event.getView().getTitle()) + 1);
                    else openShopMenu(player, getCurrentPage(event.getView().getTitle()) + 1);
                    break;

                case "PREVIOUS_PAGE":
                    if(menuType.equals("OWNED")) openOwnedMenu(player, getCurrentPage(event.getView().getTitle()) - 1);
                    else if (menuType.equals("FAVORITES")) openFavoritesMenu(player, getCurrentPage(event.getView().getTitle()) - 1);
                    else openShopMenu(player, getCurrentPage(event.getView().getTitle()) - 1);
                    break;
            }
        }
    }

    private FileConfiguration getConfigByType(String type) {
        switch (type) {
            case "MAIN": return plugin.getConfigManager().getMainMenuConfig();
            case "OWNED": return plugin.getConfigManager().getOwnedMenuConfig();
            case "FAVORITES": return plugin.getConfigManager().getFavoritesMenuConfig();
            case "SHOP": return plugin.getConfigManager().getShopMenuConfig();
            case "CONFIRM": return plugin.getConfigManager().getConfirmMenuConfig();
            case "TRAILS": return plugin.getConfigManager().getTrailsMenuConfig();
            case "WARDROBE": return plugin.getConfigManager().getWardrobeMenuConfig();
            case "BUFFS": return plugin.getConfigManager().getBuffsMenuConfig();
            case "SETTINGS": return plugin.getConfigManager().getUpgradesMenuConfig();
            default: return null;
        }
    }

    private void handlePetClick(Player player, PetManager.PetData pet, String menuType) {
        if (!plugin.getPetManager().checkCooldown(player)) {
            player.sendMessage(plugin.getConfigManager().getMessage("cooldown"));
            return;
        }

        if (menuType.equals("OWNED") || menuType.equals("FAVORITES")) {
            if (pet.id.equals(plugin.getPetManager().getActivePetId(player))) {
                plugin.getPetManager().removePet(player, true);
            } else {
                plugin.getPetManager().spawnPet(player, pet.id);
            }
            if (menuType.equals("OWNED")) openOwnedMenu(player, getCurrentPage(player.getOpenInventory().getTitle()));
            else openFavoritesMenu(player, getCurrentPage(player.getOpenInventory().getTitle()));

        } else if (menuType.equals("SHOP")) {
            if (player.hasPermission(pet.permission)) {
                player.sendMessage(plugin.getConfigManager().getMessage("already-owned"));
            } else {
                openConfirmationMenu(player, pet);
            }
        }
    }

    private void handlePurchase(Player player) {
        if (!pendingPurchase.containsKey(player.getUniqueId())) { player.closeInventory(); return; }
        String petId = pendingPurchase.get(player.getUniqueId());
        PetManager.PetData pet = plugin.getPetManager().getLoadedPets().stream().filter(p->p.id.equals(petId)).findFirst().orElse(null);

        if (pet != null) {
            if (plugin.getConfig().getBoolean("economy.enabled") && plugin.getEconomyManager() != null) {
                if (!plugin.getEconomyManager().hasMoney(player, pet.price)) {
                    player.sendMessage(plugin.getConfigManager().getMessage("insufficient-funds", "%price%", String.valueOf(pet.price)));
                    player.closeInventory();
                    return;
                }
                plugin.getEconomyManager().withdrawPlayer(player, pet.price);
                player.sendMessage(plugin.getConfigManager().getMessage("money-withdrawn", "%amount%", String.valueOf(pet.price)));
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + player.getName() + " permission set " + pet.permission + " true");
            player.sendMessage(plugin.getConfigManager().getMessage("purchased", "%pet%", pet.name));
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
        }
        pendingPurchase.remove(player.getUniqueId());
        player.closeInventory();
    }

    private int getCurrentPage(String title) {
        try {
            int slashIndex = title.lastIndexOf("/");
            int openParenIndex = title.lastIndexOf("(", slashIndex);
            if (slashIndex != -1 && openParenIndex != -1) {
                String pageNum = title.substring(openParenIndex + 1, slashIndex);
                pageNum = pageNum.replaceAll("§.", "");
                return Integer.parseInt(pageNum) - 1;
            }
        } catch (Exception e) { }
        return 0;
    }
}