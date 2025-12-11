package de.MaxBrassLoud.disMineBridgePlugin.serverlist;

import org.bukkit.Bukkit;

public class ServerlistManager {

    private static String message = "&aWillkommen auf dem Server!";

    public static void setMessage(String msg) {
        message = msg;
    }

    public static String getMessage() {
        return message;
    }

    public static void setMOTD(String motd) {
        Bukkit.getServer().setMotd(motd);
    }
}
