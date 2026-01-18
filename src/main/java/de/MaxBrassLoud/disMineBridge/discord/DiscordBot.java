package de.MaxBrassLoud.disMineBridge.discord;

import de.MaxBrassLoud.disMineBridge.DisMineBridge;
import de.MaxBrassLoud.disMineBridge.discord.commands.TicketSetupCommand;
import de.MaxBrassLoud.disMineBridge.discord.listeners.*;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

public class DiscordBot {

    private final DisMineBridge plugin;
    private final String token;
    private JDA jda;

    public DiscordBot(DisMineBridge plugin, String token) {
        this.plugin = plugin;
        this.token = token;
    }

    public boolean start() {
        try {
            jda = JDABuilder.createDefault(token)
                    .enableIntents(
                            GatewayIntent.GUILD_MEMBERS,
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT,
                            GatewayIntent.DIRECT_MESSAGES
                    )
                    .setMemberCachePolicy(MemberCachePolicy.ALL)
                    .setChunkingFilter(ChunkingFilter.ALL)
                    .setActivity(Activity.playing("Minecraft"))
                    .build();

            jda.awaitReady();

            // Commands registrieren
            registerCommands();

            // Event Listeners registrieren
            registerListeners();

            plugin.getLogger().info("Discord Bot erfolgreich gestartet!");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void registerCommands() {
        jda.updateCommands().addCommands(
                TicketSetupCommand.getCommandData()
        ).queue();
    }

    private void registerListeners() {
        jda.addEventListener(new SlashCommandListener(plugin));
        jda.addEventListener(new ButtonInteractionListener(plugin));
        jda.addEventListener(new ModalInteractionListener(plugin));
        jda.addEventListener(new SelectMenuListener(plugin));
        jda.addEventListener(new GuildMemberLeaveListener(plugin));
        jda.addEventListener(new MessageListener(plugin));
    }

    public void shutdown() {
        if (jda != null) {
            jda.shutdown();
        }
    }

    public JDA getJDA() {
        return jda;
    }
}