package de.MaxBrassLoud.disMineBridge.discord.commands;

import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

public class TicketSetupCommand {

    public static CommandData getCommandData() {
        return Commands.slash("ticketsetup", "Richte das Ticket-System ein");
    }
}