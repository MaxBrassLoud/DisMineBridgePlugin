package de.MaxBrassLoud.disMineBridge.managers;

import de.MaxBrassLoud.disMineBridge.DisMineBridge;
import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.PlayerStateChangedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verwaltet Mutes für Text-Chat und Voice-Chat
 * Aktualisiert für Simple Voice Chat API 2.6.0
 */
public class MuteManager implements VoicechatPlugin {

    private final DisMineBridge plugin;
    private final Map<UUID, Long> mutedPlayers;
    private VoicechatServerApi voicechatApi;
    private boolean voicechatEnabled = false;

    public MuteManager(DisMineBridge plugin) {
        this.plugin = plugin;
        this.mutedPlayers = new ConcurrentHashMap<>();

        // Versuche Voice-Chat zu initialisieren
        initializeVoiceChat();

        // Starte Mute-Checker Task
        startMuteChecker();
    }

    /**
     * Initialisiert Voice-Chat Integration
     */
    private void initializeVoiceChat() {
        try {
            BukkitVoicechatService service = plugin.getServer().getServicesManager()
                    .load(BukkitVoicechatService.class);

            if (service != null) {
                service.registerPlugin(this);
                plugin.getLogger().info("✓ Simple Voice Chat Integration aktiviert!");
                voicechatEnabled = true;
            } else {
                plugin.getLogger().info("Simple Voice Chat nicht gefunden - nur Text-Mute verfügbar");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Voice Chat konnte nicht initialisiert werden: " + e.getMessage());
            voicechatEnabled = false;
        }
    }

    @Override
    public String getPluginId() {
        return plugin.getDescription().getName().toLowerCase();
    }

    /**
     * Wird aufgerufen wenn die Voice Chat API initialisiert wird
     */
    //@Override
    public void initialize(VoicechatServerApi api) {
        this.voicechatApi = api;
        plugin.getLogger().info("Voice Chat API 2.6.0 initialisiert!");
    }

    /**
     * Registriert Event-Listener für Voice Chat
     */
    @Override
    public void registerEvents(EventRegistration registration) {
        // Server Started Event - um gemutete Spieler initial zu setzen
        registration.registerEvent(VoicechatServerStartedEvent.class, this::onVoicechatServerStarted);

        // Player State Changed Event - um Mute-Status zu überwachen
        registration.registerEvent(PlayerStateChangedEvent.class, this::onPlayerStateChanged);
    }

    /**
     * Wird aufgerufen wenn der Voice-Chat Server startet
     */
    private void onVoicechatServerStarted(VoicechatServerStartedEvent event) {
        plugin.getLogger().info("Voice Chat Server gestartet - synchronisiere Mute-Status...");

        // Synchronisiere alle gemuteten Spieler
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (UUID uuid : mutedPlayers.keySet()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    applyVoiceMute(uuid, true);
                }
            }
        }, 20L); // 1 Sekunde warten
    }

    /**
     * Wird aufgerufen wenn sich der Player-State ändert
     */
    private void onPlayerStateChanged(PlayerStateChangedEvent event) {
        // Null-Check: Connection kann null sein wenn Spieler disconnected
        @Nullable VoicechatConnection connection = event.getConnection();
        if (connection == null) {
            return;
        }

        // Prüfe ob der Spieler gemutet ist und ob er versucht den Voice-Chat zu aktivieren
        UUID playerUuid = connection.getPlayer().getUuid();

        if (isMuted(playerUuid)) {
            // Wenn der Spieler nicht mehr disabled ist, disabled ihn wieder
            if (!event.isDisabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    applyVoiceMute(playerUuid, true);
                });
            }
        }
    }

    /**
     * Mutet einen Spieler (Text + Voice)
     */
    public void mutePlayer(UUID playerUuid, long expireTime) {
        mutedPlayers.put(playerUuid, expireTime);

        // Voice-Chat Mute anwenden
        if (voicechatEnabled && voicechatApi != null) {
            applyVoiceMute(playerUuid, true);

            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null) {
                plugin.getLogger().info("Voice-Chat Mute aktiviert für: " + player.getName());
            }
        }
    }

    /**
     * Entmutet einen Spieler
     */
    public void unmutePlayer(UUID playerUuid) {
        mutedPlayers.remove(playerUuid);

        // Voice-Chat Mute entfernen
        if (voicechatEnabled && voicechatApi != null) {
            applyVoiceMute(playerUuid, false);

            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null) {
                plugin.getLogger().info("Voice-Chat Mute deaktiviert für: " + player.getName());
            }
        }
    }

    /**
     * Wendet Voice-Mute an/entfernt ihn
     *
     * @param playerUuid UUID des Spielers
     * @param mute true = muten, false = unmuten
     */
    private void applyVoiceMute(UUID playerUuid, boolean mute) {
        if (voicechatApi == null) {
            return;
        }

        try {
            // Hole die VoicechatConnection für den Spieler
            @Nullable VoicechatConnection connection = voicechatApi.getConnectionOf(playerUuid);

            if (connection != null) {
                // Setze den Disabled-Status
                connection.setDisabled(mute);

                if (mute) {
                    plugin.getLogger().fine("Voice-Chat disabled für UUID: " + playerUuid);
                } else {
                    plugin.getLogger().fine("Voice-Chat enabled für UUID: " + playerUuid);
                }
            } else {
                // Connection existiert noch nicht - warte kurz und versuche es erneut
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    @Nullable VoicechatConnection retryConnection = voicechatApi.getConnectionOf(playerUuid);
                    if (retryConnection != null) {
                        retryConnection.setDisabled(mute);
                    } else {
                        plugin.getLogger().fine("Voice-Chat Connection noch nicht verfügbar für: " + playerUuid);
                    }
                }, 20L);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Fehler beim Anwenden des Voice-Mutes: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Prüft ob ein Spieler gemutet ist
     */
    public boolean isMuted(UUID playerUuid) {
        Long expireTime = mutedPlayers.get(playerUuid);

        if (expireTime == null) {
            return false;
        }

        // Prüfe ob Mute abgelaufen ist
        if (System.currentTimeMillis() >= expireTime) {
            unmutePlayer(playerUuid);
            return false;
        }

        return true;
    }

    /**
     * Holt Ablaufzeit eines Mutes
     */
    public long getMuteExpireTime(UUID playerUuid) {
        return mutedPlayers.getOrDefault(playerUuid, 0L);
    }

    /**
     * Lädt aktive Mutes aus der Datenbank beim Server-Start
     */
    public void loadActiveMutes() {
        try {
            Map<UUID, Long> activeMutes = plugin.getDatabaseManager().getActiveMutes();
            mutedPlayers.putAll(activeMutes);

            plugin.getLogger().info("✓ " + activeMutes.size() + " aktive Mutes geladen");

            // Synchronisiere Voice-Mutes nach kurzer Verzögerung
            if (voicechatEnabled && !activeMutes.isEmpty()) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    for (UUID uuid : activeMutes.keySet()) {
                        Player player = Bukkit.getPlayer(uuid);
                        if (player != null && player.isOnline()) {
                            applyVoiceMute(uuid, true);
                        }
                    }
                    plugin.getLogger().info("Voice-Mutes synchronisiert für " + activeMutes.size() + " Spieler");
                }, 40L); // 2 Sekunden warten
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Fehler beim Laden der Mutes: " + e.getMessage());
        }
    }

    /**
     * Startet regelmäßige Überprüfung abgelaufener Mutes
     */
    private void startMuteChecker() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long now = System.currentTimeMillis();

            mutedPlayers.entrySet().removeIf(entry -> {
                if (now >= entry.getValue()) {
                    UUID uuid = entry.getKey();
                    Player player = Bukkit.getPlayer(uuid);

                    if (player != null) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            // Entferne Voice-Mute
                            applyVoiceMute(uuid, false);

                            // Sende Nachricht
                            player.sendMessage(plugin.getLanguageManager()
                                    .getMessage("minecraft.mute.expired"));
                        });
                    }

                    plugin.getLogger().info("Mute abgelaufen für: " + uuid);
                    return true;
                }
                return false;
            });

        }, 20L * 10, 20L * 10); // Alle 10 Sekunden
    }

    /**
     * Event-Handler für Spieler-Join
     * Stelle sicher dass gemutete Spieler auch im Voice-Chat gemutet sind
     */
    public void onPlayerJoin(Player player) {
        UUID uuid = player.getUniqueId();

        if (isMuted(uuid) && voicechatEnabled) {
            // Warte kurz bis der Spieler vollständig geladen ist
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                applyVoiceMute(uuid, true);
                plugin.getLogger().info("Voice-Mute bei Join angewendet für: " + player.getName());
            }, 20L);
        }
    }

    /**
     * Gibt zurück ob Voice-Chat verfügbar ist
     */
    public boolean isVoiceChatEnabled() {
        return voicechatEnabled;
    }

    /**
     * Gibt die Voice-Chat API zurück (falls verfügbar)
     */
    @Nullable
    public VoicechatServerApi getVoicechatApi() {
        return voicechatApi;
    }

    /**
     * Gibt Anzahl der gemuteten Spieler zurück
     */
    public int getMutedPlayerCount() {
        return mutedPlayers.size();
    }

    /**
     * Cleanup beim Plugin-Disable
     */
    public void shutdown() {
        // Entmute alle Spieler im Voice-Chat vor dem Shutdown
        if (voicechatEnabled && voicechatApi != null) {
            for (UUID uuid : mutedPlayers.keySet()) {
                applyVoiceMute(uuid, false);
            }
        }

        mutedPlayers.clear();
        voicechatApi = null;
    }
}