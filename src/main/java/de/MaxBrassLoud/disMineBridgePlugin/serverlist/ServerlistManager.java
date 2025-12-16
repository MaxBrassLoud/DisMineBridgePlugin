package de.MaxBrassLoud.disMineBridgePlugin.serverlist;

import org.bukkit.Bukkit;
import de.MaxBrassLoud.disMineBridgePlugin.utils.MessageManager;

import java.util.Objects;

import static de.MaxBrassLoud.disMineBridgePlugin.utils.MessageManager.config;

public class ServerlistManager {
    static String maintenance_on;
    public static void setCurrentMOTD() {
        maintenance_on = config.getString("maintenance.enabled");
        if(Objects.equals(maintenance_on, "true")) {
            setMOTD(config.getString("maintenance.motd"));
            return;
        } else {
            setMOTD(config.getString("servermotd.motd"));
            return;
        }



    };
    public static String getMessage() {
        return config.getString("servermotd.motd");
    };

    public static void setMOTD(String motd) {
        Bukkit.getServer().setMotd(motd);
    }
}
