package com.luminus.labs.noland.plugins;

import com.luminus.labs.noland.plugins.AppShortcut.AppShortcutIsland;

import java.util.ArrayList;

public class ExportedPlugins {
    public static ArrayList<BasePlugin> getPlugins() {
        ArrayList<BasePlugin> plugins = new ArrayList<>();
        plugins.add(new AppShortcutIsland());
        return plugins;
    }
}
