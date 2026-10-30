![FoKillstreak cover](docs/assets/cover.png)

# FoKillstreak

[![Modrinth](https://img.shields.io/badge/Download-Modrinth-1bd96a?logo=modrinth&logoColor=white)](https://modrinth.com/plugin/fokillstreak) [![Versions](https://img.shields.io/badge/Supported_versions-Modrinth-1bd96a)](https://modrinth.com/plugin/fokillstreak/versions) [![Discord](https://img.shields.io/badge/Support-Discord-5865f2?logo=discord&logoColor=white)](https://discord.gg/kEF77QCfu7)

Persistent PvP killstreaks with rewards, leaderboards, and anti-farming controls.

*Developed by Carrotio.*

## Features

- Tracks current and best PvP killstreaks persistently.
- Provides a player leaderboard and administrator editor.
- Runs configurable milestone rewards and broadcasts.
- Includes same-victim cooldown protection against farming.
- Exposes killstreak, best-streak, and rank placeholders.

## Download and dependencies

Download the latest compatible version from [Modrinth](https://modrinth.com/plugin/fokillstreak). Check the [versions page](https://modrinth.com/plugin/fokillstreak/versions) for supported Minecraft versions and server platforms.

- **Required:** None
- **Optional integrations:** `PlaceholderAPI`

## Screenshots

![Configure FoKillstreak.](docs/assets/screenshots/fokillstreak-admin-tight.png)

*Configure FoKillstreak.*

![View the player leaderboard.](docs/assets/screenshots/fokillstreak-leaderboard-tight.png)

*View the player leaderboard.*

![Edit a milestone reward.](docs/assets/screenshots/fokillstreak-reward-edit-tight.png)

*Edit a milestone reward.*

![Browse configured rewards.](docs/assets/screenshots/fokillstreak-rewards-browser.png)

*Browse configured rewards.*

## Support

For help or issue reports, join the [Carrotio Discord server](https://discord.gg/kEF77QCfu7).

<details>
<summary><strong>Commands and permissions</strong></summary>

### Commands

- `/fokillstreakadmin <version|reload|editor|set>` — Manage FoKillstreak. Aliases: `/killstreakadmin`, `/ksadmin`.
- `/killstreak` — Open the FoKillstreak leaderboard. Aliases: `/ks`.

### Permissions

- `fokillstreak.admin` — Allows access to FoKillstreak admin tools. Default: `op`.
- `fokillstreak.use` — Allows players to open the FoKillstreak leaderboard. Default: `true`.

</details>

<details>
<summary><strong>PlaceholderAPI</strong></summary>

Requires PlaceholderAPI.

- `%fokillstreak_killstreak% / %fokillstreak_killstreak_raw%` — Current killstreak.
- `%fokillstreak_best% / %fokillstreak_best_raw%` — Best recorded killstreak.
- `%fokillstreak_rank%` — Current-streak leaderboard rank.

</details>

<details>
<summary><strong>Configuration</strong></summary>

- `config.yml` — Main behavior and feature settings.
- `messages.yml` — Player-facing messages and interface text.
- `sounds.yml` — Configurable interaction sounds.
- `guis/*.yml` — GUI layouts, slots, labels, and lore.

Main config areas: `death-reset`, `same-victim-cooldown`, `native-dialogs`, `broadcasts`, `rewards`.

</details>

<details>
<summary><strong>Developer API</strong></summary>

Compile against the matching jar (`provided` in Maven, `compileOnly` in Gradle); install it locally if needed. Declare `FoKillstreak` in your plugin metadata (`softdepend` for an optional integration).

```xml
<dependency>
  <groupId>me.foesio</groupId>
  <artifactId>FoKillstreak</artifactId>
  <version>2.1</version>
  <scope>provided</scope>
</dependency>
```

```java
import me.foesio.foKillstreak.api.KillstreakService;
import me.foesio.foKillstreak.api.event.KillCreditPreEvent;

KillstreakService api = Bukkit.getServicesManager().load(KillstreakService.class);
if (api != null) api.getStreak(player.getUniqueId());

@EventHandler
public void onKillCreditPreEvent(KillCreditPreEvent event) {
    Bukkit.getLogger().info(event.getClass().getSimpleName());
}
```

**API methods** (overloads grouped):

- `KillstreakService`: `getStreak`, `getTop`, `getRank`, `setStreak`, `resetCurrent`, `creditKill`.

**Other API types:** `ChangeCause`, `ChangeResult`, `CreditResult`, `StreakLeaderboardEntry`, `StreakSnapshot`, `StreakType`.

**Events:** `KillCreditPreEvent`, `KillstreakChangeEvent`.

[Browse API source](src/main/java/me/foesio/foKillstreak/api) for signatures and event details.

</details>
