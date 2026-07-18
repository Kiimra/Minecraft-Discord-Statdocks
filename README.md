# DiscordStatdockUpdater

A production-ready Paper plugin that keeps Discord **voice / stage / category**
channel names updated with live server info - player count, uptime, TPS and
more - using only the **Discord REST API**. No heavyweight Discord library is
bundled and, by default, **no persistent connection is opened**, so it costs
almost nothing to run.

```
🟢│Online 1/20 (2h 10m)      ← at least one player online
🌙│Zzz... 0/20 (8d 23h)       ← running, nobody online (shares the same uptime)
🔴│Offline                    ← set right before the server stops
```

## Features

- **REST-only by default** - the bot never connects to the Discord gateway, so
  it uses negligible resources. (It therefore shows as *offline* in Discord's
  member list, which is purely cosmetic - see Presence below.)
- **Reuse an existing bot** - because everything goes through the Discord REST
  API, you don't need to create a dedicated bot: you can drop in the token of a
  bot you already have, even one that is currently running elsewhere. REST
  calls don't interfere with that bot's gateway session, so both can operate at
  the same time.
- **Multiple channels**, each with its own update interval and optional
  per-channel templates. Works across any number of Discord servers, since
  channels are addressed by ID.
- **Five states**, all with configurable text/emoji: `online`, `idle`,
  `offline`, `lag` (🟡, below a TPS threshold) and `maintenance` (🟣, toggled by
  command).
- **Immediate updates** - the 🟢 ↔ 🌙 switch happens the moment a player joins
  or leaves, bypassing the normal cycle. A channel is never renamed to a name it
  already shows, so no edits are wasted.
- **Rate-limit aware** - respects Discord's ~2 renames / 10 min per channel with
  a client-side budget and honours `429` responses, all off the main thread.
- **Optional presence** - the bot's status text (e.g. *Watching 5/20 players*),
  cycling through multiple messages. Disabled by default; enabling it opens one
  minimal gateway connection (identify + heartbeat only, no event listening).
- **Optional record tracking** - the `{record}` peak-players placeholder, which
  can also persist across restarts. Off by default.
- **Robust** - invalid token / missing permission is retried periodically, a
  deleted channel is warned about and skipped, and network errors never block
  the server.

## Placeholders

Usable in any channel template and in presence messages:

| Placeholder | Meaning |
|-------------|---------|
| `{online}`  | players currently online |
| `{max}`     | max players |
| `{uptime}`  | session uptime - `45m`, `2h 10m`, `8d 23h` |
| `{tps}`     | current TPS (one decimal, capped at 20.0) |
| `{ip}`      | server address (see the `ip` config section) |
| `{port}`    | server port |
| `{version}` | Minecraft version |
| `{record}`  | peak online players (needs `record.enabled`) |
| `{players}` | comma-separated list of online player names |

## Setup

1. Create a bot at the [Discord Developer Portal](https://discord.com/developers/applications)
   and copy its **token** into `token` in `config.yml` - or **reuse the token
   of a bot you already have**. The plugin only talks to Discord through the
   REST API, so an existing bot keeps working normally (even while it is
   running somewhere else) while this plugin renames channels with the same
   token. The only caveat: if you enable the optional `presence` feature, this
   plugin opens its own gateway session, which may override the status shown by
   the other instance of the bot.
2. Invite the bot to your server with the **Manage Channels** permission.
3. Enable **Developer Mode** in Discord, right-click a voice/stage/category
   channel → **Copy Channel ID**, and put it under `channels:` in the config.
4. Start the server, then run `/statdock status` in-game to confirm it works.

## Commands

`/statdock` (aliases `/statdocks`, `/dsu`) - permission `statdock.admin` (default: op)

| Subcommand | Description |
|------------|-------------|
| `reload` | Reload config, channels and token (hot-swaps the token) |
| `forceupdate` | Update every channel now (still rate-limited) |
| `status` | Show connection state, resolved IP, uptime and per-channel info |
| `maintenance <on\|off>` | Toggle maintenance mode (🟣) |

## Notes & limitations

- **Discord rename limit:** a channel can be renamed only ~2 times per 10
  minutes. Keep `interval-seconds` at 300 or higher (values below are clamped).
  Under heavy join/leave churn a rename may be delayed a few minutes until the
  budget allows it - this is a Discord limit, not a bug.
- **Counters don't persist:** uptime and player counts reset on every server
  start, by design. Only `{record}` can optionally persist.
- **Hard crashes:** if the server process is killed without a clean shutdown,
  the plugin cannot set the channel to 🔴 Offline, so it stays on the last name
  until the server starts again. This is an accepted, documented limitation.

## Building

Requires JDK 25 - if you don't have it installed, Gradle downloads a matching
JDK automatically via the Foojay toolchain resolver. The build compiles
directly against the real Paper API, so it needs network access to
`repo.papermc.io` (the Paper API version is declared in `build.gradle.kts`).

```bash
./gradlew build
```

The jar is produced in `build/libs/`. Only `com.google.code.gson` is used on the
Discord side and it is **provided by Paper at runtime**, so nothing is shaded.

## License

[MIT](LICENSE)
