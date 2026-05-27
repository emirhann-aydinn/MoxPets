package com.mox.moxpets.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class PetMenuHolder implements InventoryHolder {

    private final String menuType; // "MAIN", "OWNED", "SHOP", "CONFIRM"

    public PetMenuHolder(String menuType) {
        this.menuType = menuType;
    }

    public String getMenuType() {
        return menuType;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}