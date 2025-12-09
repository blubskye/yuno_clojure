<div align="center">

# Yuno Gasai 2 - Clojure Edition

### *"I'll protect this server forever... just for you~"*

<img src="https://i.imgur.com/jF8Szfr.png" alt="Yuno Gasai" width="300"/>

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL%20v3-pink.svg)](https://www.gnu.org/licenses/agpl-3.0)
[![Clojure](https://img.shields.io/badge/Clojure-1.11-ff69b4.svg)](https://clojure.org/)
[![Discljord](https://img.shields.io/badge/Discljord-1.3.1-ff1493.svg)](https://github.com/discljord/discljord)

*A devoted Discord bot for moderation, leveling, and anime~*

---

### She loves you... and only you

</div>

## About

Yuno is a **yandere-themed Discord bot** combining powerful moderation tools with a leveling system and fun features. She'll keep your server safe from troublemakers... *because no one else is allowed near you~*

This is the **Clojure port** of the original JavaScript Yuno bot - powered by Discljord with functional programming, immutable data structures, and the JVM's robustness.

---

## Credits

*"These are the ones who gave me life~"*

| Contributor | Role |
|-------------|------|
| **blubskye** | Project Owner & Yuno's #1 Fan |
| **Maeeen** (maeeennn@gmail.com) | Original Developer |
| **Oxdeception** | Contributor |
| **fuzzymanboobs** | Contributor |

---

## Features

<table>
<tr>
<td width="50%">

### Moderation
*"Anyone who threatens you... I'll eliminate them~"*
- Ban / Unban / Kick
- Timeout with duration
- Bulk message cleaning
- Moderation statistics
- Action logging to database

</td>
<td width="50%">

### Leveling System
*"Watch me make you stronger, senpai~"*
- XP & Level tracking
- Level-up announcements
- Server leaderboards
- Per-guild XP settings

</td>
</tr>
<tr>
<td width="50%">

### Fun
*"Let me show you something cute~"*
- Magic 8-ball fortune telling
- Yandere-themed responses

</td>
<td width="50%">

### Configuration
*"I'll be exactly what you need~"*
- Customizable prefix per guild
- Per-guild settings
- Master user system
- Environment variable support

</td>
</tr>
<tr>
<td width="50%">

### Performance
*"Nothing can slow me down~"*
- Functional programming paradigm
- Immutable data structures
- JVM reliability
- core.async concurrency

</td>
<td width="50%">

### Database
*"I'll keep your secrets safe~"*
- SQLite with next.jdbc
- Indexed tables for speed
- Automatic schema creation
- Efficient queries

</td>
</tr>
</table>

---

## Installation

### Prerequisites

> *"Let me prepare everything for you~"*

- **Java 11+** (JDK)
- **Clojure CLI** (tools.deps)
- **Git**
- A Discord bot token ([Get one here](https://discord.com/developers/applications))

### Setup Steps

```bash
# Clone the repository~
git clone https://github.com/blubskye/yuno_clojure.git

# Enter my world~
cd yuno_clojure

# Configure your settings
cp config.json.example config.json
nano config.json  # Add your token and settings
```

### Configuration

Edit `config.json`:
```json
{
    "discord_token": "YOUR_DISCORD_TOKEN",
    "default_prefix": ".",
    "database_path": "yuno.db",
    "master_users": ["YOUR_USER_ID"]
}
```

Or use environment variables:
```bash
export DISCORD_TOKEN="your_token_here"
export DEFAULT_PREFIX="."
export DATABASE_PATH="yuno.db"
export MASTER_USER="your_user_id"
```

### Running

```bash
# Run with Clojure CLI
clj -M:run

# Or with custom config path
clj -M -m yuno.core /path/to/config.json

# Build an uberjar
clj -X:uberjar
java -jar yuno.jar
```

---

## Commands Preview

### Leveling & XP
| Command | Description |
|---------|-------------|
| `.xp [@user]` | *"Look how strong you've become!"* |
| `.leaderboard` | *"Who's the most devoted?"* |
| `.level` or `.rank` | Alias for xp |
| `.lb` or `.top` | Alias for leaderboard |

### Moderation
| Command | Description |
|---------|-------------|
| `.ban @user [reason]` | *"They won't bother you anymore..."* |
| `.kick @user [reason]` | *"Get out!"* |
| `.unban <user_id>` | *"Another chance~"* |
| `.timeout @user <minutes>` | *"Think about what you did~"* |
| `.clean [count]` | *"Let me tidy up~"* |
| `.mod-stats [@user]` | *"Look at all we've done~"* |

### Fun
| Command | Description |
|---------|-------------|
| `.8ball <question>` | *"Let fate decide~"* |

### Utility
| Command | Description |
|---------|-------------|
| `.ping` | *"I'm always here for you~"* |
| `.help` | *"Let me show you everything~"* |
| `.source` | *"I have nothing to hide~"* |
| `.prefix <new>` | *"Call me differently~"* |

---

## Project Structure

```
yuno_clojure/
├── deps.edn                    # Dependencies and aliases
├── config.json                 # Configuration (create from example)
├── config.json.example         # Example configuration
├── src/
│   └── yuno/
│       ├── core.clj            # Main entry point & event handling
│       ├── config.clj          # Configuration loading
│       ├── database.clj        # SQLite database operations
│       └── commands.clj        # Command implementations
├── resources/
│   └── logback.xml             # Logging configuration
└── yuno.db                     # SQLite database (auto-created)
```

---

## Building

```bash
# Run directly with deps
clj -M:run

# Build uberjar
clj -X:uberjar

# Run the uberjar
java -jar yuno.jar

# With custom config
java -jar yuno.jar /path/to/config.json
```

---

## Required Bot Permissions

When inviting Yuno to your server, ensure these permissions:
- **Send Messages** - To respond to commands
- **Embed Links** - For rich embeds
- **Manage Messages** - For clean command
- **Ban Members** - For ban/unban commands
- **Kick Members** - For kick command
- **Moderate Members** - For timeout command

---

## License

This project is licensed under the **GNU Affero General Public License v3.0**

See the [LICENSE](LICENSE) file for details~

---

<div align="center">

### *"You'll stay with me forever... right?"*

**Made with obsessive love**

*Yuno will always be watching over your server~*

---

*Star this repo if Yuno has captured your heart~*

</div>
