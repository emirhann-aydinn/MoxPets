package com.mox.moxpets.listeners;

import com.mox.moxpets.MyPets;
import org.bukkit.event.Listener;

public class InteractListener implements Listener {
    private final MyPets plugin;

    public InteractListener(MyPets plugin) {
        this.plugin = plugin;
    }
}