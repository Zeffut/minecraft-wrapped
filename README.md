# Minecraft Wrapped

> **Spotify Wrapped, but for Minecraft.** Every 1st of the month, a button appears on your title screen — click it to relive your month with a cinematic animated recap.

A **Fabric client-side mod** for **Minecraft 1.21.11**. Zero server changes, zero account, zero network calls. Your data never leaves your machine.

## Features

### A cinematic monthly recap

12+ animated cards that reveal in sequence, each with its own visual signature:

- **Intro** — the month name fades in with a green accent sweep and dropping letters.
- **Time Spent** — a clock dial spins toward your total play time.
- **Longest Session** — your single longest play streak, broken down across sessions in the month.
- **When You Play** — a 24-bar histogram of your peak hours (night owl? lunch raider?).
- **Top World** — your most-played world or server, with a traced gold frame and share bar.
- **Dimensions Explored** — Overworld vs Nether vs End, ranked by ticks.
- **Social** — players met, messages sent, solo-vs-server split bar.
- **Distance Covered** — total kilometres + breakdown by walking, elytra, boat, horse, swim, fly.
- **Top Blocks Mined** — top 3 with real block textures.
- **Top Mob Killed** — spawn-egg of your most-killed enemy, rotating.
- **Top Crafted** — top 3 items you crafted.
- **Death Recap** — your deaths and the most embarrassing cause (or *Perfect Streak* if zero).
- **Archetype** — drumroll then card flip reveals your personality among 15 archetypes.
- **Final** — recap tiles + Save Image / Copy / Close buttons.

### Multiplayer-aware

The mod tracks your time on each server (client-side tick counter + vanilla server stats via REQUEST_STATS), counts unique players you cross paths with, chat messages sent, and servers visited. The recap mixes solo worlds and servers in one timeline — because that's what playing Minecraft actually looks like.

### Image export

Hit **Save Image** in the final card and the mod renders a 1080×1920 PNG (story format) into `screenshots/wrapped/`. Or **Copy** to put it directly on your clipboard.

### Smart filtering

The recap only includes cards that are relevant to your data. Played 5 minutes? Just the basics. Spent 100 hours on three servers and crafted 600 items? You get the full ride.

### Privacy first

- Your Wrapped data lives in `<game>/wrapped/`. Delete it any time.
- Anonymous usage telemetry helps improve the mod. It's **opt-out** — see below.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for your target Minecraft version (1.21.1 – 1.21.11).
2. Drop **[Fabric API](https://modrinth.com/mod/fabric-api)** into your `mods/` folder.
3. Grab the matching `minecraft-wrapped-*.jar` from the [latest release](https://github.com/Zeffut/minecraft-wrapped/releases) (see the version table below) and drop it in the same `mods/` folder.
4. Launch the game.

### Supported Minecraft versions

All versions are tested and published — grab the jar matching your Minecraft version from the [releases page](https://github.com/Zeffut/minecraft-wrapped/releases).

| MC version | Branch | Jar |
|---|---|---|
| **1.21.11** | `main` | `minecraft-wrapped-1.0.0.jar` |
| 1.21.10 | `1.21.10` | `minecraft-wrapped-1.0.0+1.21.10.jar` |
| 1.21.8 | `1.21.8` | `minecraft-wrapped-1.0.0+1.21.8.jar` |
| 1.21.6 | `1.21.6` | `minecraft-wrapped-1.0.0+1.21.6.jar` |
| 1.21.4 | `1.21.4` | `minecraft-wrapped-1.0.0+1.21.4.jar` |
| 1.21.1 | `1.21.1` | `minecraft-wrapped-1.0.0+1.21.1.jar` |

### Loader compatibility

| Loader | Status | How |
|---|---|---|
| **Fabric** | ✅ Native | install as above |
| **Quilt** | ✅ Native | drop the same jar in Quilt's `mods/` folder. Quilt loads Fabric mods directly — install [QSL](https://modrinth.com/mod/qsl) (Quilted Fabric API) instead of Fabric API |
| **NeoForge** | ✅ Via [Sinytra Connector](https://modrinth.com/mod/connector) | install Sinytra Connector + Forgified Fabric API on NeoForge, then drop the Wrapped jar in `mods/`. All features work. |
| **Forge** | ❌ Not supported | NeoForge + Sinytra is the modern path |

The mod runs silently in the background. On the **first day of every month**, when you launch the game, a *Your X Wrapped is ready* button appears on the title screen.

## Commands

| Command | What it does |
|---|---|
| `/wrapped` | Replay the latest finalized Wrapped |
| `/wrapped history` | Browse all your past Wrappeds |
| `/wrapped telemetry on\|off\|status` | Turn anonymous telemetry on/off, or check its state |

## Telemetry & privacy

Minecraft Wrapped sends **anonymous** usage telemetry to help improve the mod (which cards
people watch, which features get used, errors). It is **enabled by default** and you can turn
it off at any time:

- In-game: `/wrapped telemetry off`
- Config screen (ModMenu → Minecraft Wrapped → Privacy): the **Telemetry** toggle

**What is sent:** mod / Minecraft / Fabric versions, OS, language, which cards you view and for
how long, feature usage (export / save / copy / commands / config changes), coarse gameplay
buckets (e.g. playtime `10-50h`, not exact numbers), your archetype, and mod errors.

**What is never sent:** your username, world names, server addresses, file paths, chat, or exact
stat values. IP addresses are anonymized. Telemetry is processed via [PostHog](https://posthog.com).

## How it works

- At every game launch, the mod scans `saves/*/stats/<uuid>.json` (your singleplayer worlds), pulls server-side vanilla stats via the `REQUEST_STATS` packet, and aggregates them into a cumulative monthly snapshot at `wrapped/snapshot-YYYY-MM.json`.
- When the calendar rolls over, the mod computes the delta against last month's snapshot and writes a finalized `wrapped-YYYY-MM.json`.
- That finalized file drives every card in the sequence.

Side trackers persist in `wrapped/`:
- `server-play-time.json` — ticks per server you connect to
- `server-stats.json` — full vanilla stats from servers that respond to REQUEST_STATS
- `multiplayer-data.json` — UUIDs of players seen, chat counter, commands counter
- `sessions.json` — start/end timestamps of every session
- `play-by-hour.json` — ticks per hour of day, per month
- `play-by-dimension.json` — ticks per dimension, per month

## Build from source

```bash
./gradlew build
```

The jar lands in `build/libs/`.

## Run in dev

```bash
./gradlew runClient
```

## License

MIT — see [LICENSE](LICENSE).

---

Made by [Zeffut](https://github.com/Zeffut).
