# DisMineBridge

DisMineBridge is a comprehensive management and moderation plugin for **Minecraft Java Edition (Paper 1.21.8)** that bridges your Minecraft server with Discord, providing unified communication, moderation, and administration capabilities.

-----

## Overview

**DisMineBridge delivers a complete, synchronized moderation system that works seamlessly across both Minecraft and Discord platforms.**

The plugin addresses a critical gap in server management: the lack of unified moderation tools across platforms. Whether you’re managing chat violations, voice abuse, or general misconduct, DisMineBridge ensures your moderation actions remain consistent and synchronized.

-----

## Core Features

### Unified Moderation System

**Temporary Bans and Mutes**

- Full synchronization with Simple Voice Chat integration
- Voice mutes and text chat mutes remain perfectly aligned
- Time-based restrictions with flexible duration syntax

**Warning and Kick System**

- Progressive discipline tracking
- Automated escalation options
- Complete action history

### Administrative Tools

**Advanced Admin Mode**

- Automatic inventory backup to database
- Seamless Creative Mode switching for administrative tasks
- Perfect restoration of inventory, location, and game mode upon exit
- Zero risk of item loss or state corruption

**Professional Vanish System**

- Complete invisibility to regular players
- Hidden join and quit messages
- Silent movement and interaction
- Ideal for monitoring and investigation

**Maintenance Mode**

- Server access restriction during updates or configuration
- Permission-based allowlist for staff
- Customizable maintenance messages

**Integrated Whitelist Management**

- Built-in whitelist system
- No external plugin dependencies
- Simple command-based control

**MOTD Configuration**

- Direct MOTD management through plugin configuration
- No manual server.properties editing required
- Dynamic message updates without server restart

-----

## Technical Specifications

### Requirements

**Platform:** Minecraft Java Edition (Paper 1.21.8)

**Dependencies:**

- Simple Voice Chat (currently required)

**Language:** Java

### Command Reference

```
/adminmode          Toggle administrative mode
/vanish             Toggle visibility status
/warn <player> <reason>                Issue a warning
/kick <player> <reason>                Remove player from server
/ban <player> <reason> <duration>      Temporary or permanent ban
/mute <player> <reason> <duration>     Restrict chat and voice
/maintenance        Toggle maintenance mode
/whitelist          Manage server whitelist
```

**Duration Syntax:** Flexible time formats (e.g., `1h30m`, `2d`, `45m`)

-----

## Installation

1. Download the latest DisMineBridge release
1. Install Simple Voice Chat on your server
1. Place DisMineBridge.jar in your plugins folder
1. Restart your server
1. Configure the plugin using the generated config files

**Note:** Future releases will make Simple Voice Chat optional rather than mandatory.

-----

## Use Cases

DisMineBridge is designed for:

- Servers of any size operating alongside a Discord community
- Communities requiring synchronized moderation across platforms
- Administrators seeking professional-grade management tools
- Teams needing reliable inventory and state management during administrative work

-----
>[!IMPORTANT]
>## Development Status

>**Current Phase:** Active Development

### Known Limitations

- Discord integration is not yet implemented
- Simple Voice Chat is currently a hard dependency

### Roadmap

**Upcoming Features:**

- Handshaker Mod integration for enhanced client-server communication
- Built-in anticheat system with customizable detection rules
- Web-based interface for remote moderation and server management
- Optional Simple Voice Chat dependency
- Full Discord bot integration with bidirectional communication

-----

## Project Structure

```
DisMineBridge/
├── Core moderation engine
├── Database management layer
├── Admin mode inventory system
├── Vanish implementation
├── Maintenance mode controller
└── Configuration system
```

-----

## Contributing

Contributions, bug reports, and feature requests are welcome. Please visit the GitHub repository to get involved.

-----

## License

[Include your license information here]

-----

## Author

**MaxBrassLoud**

Explore more projects: [GitHub Profile](https://github.com/MaxBrassLoud)

-----

## Support

For issues, questions, or feature requests, please use the GitHub issue tracker or contact the developer through the repository.