# AdaptiveAntiCheat

Adaptive anti-cheat for **Paper** (Minecraft 26.2+). **Log-only in 1.1** — no kicks, no inventory wipes, no enforcement.

It uses **trusted high-playtime players** as ground truth, but only **per movement context** and only **once per session**. Ice boats and elytra must never train the ground threshold.

## What 1.1 changed

v1 logged every over-threshold move as if it were a cheat, used one global speed multiplier, and maxed that multiplier in seconds off a trusted elytra flight. 1.1:

- Measures **blocks per server tick** (distance / elapsed ticks), not distance per `PlayerMoveEvent`
- Skips teleports, world changes, and lag gaps
- Splits speed into contexts: ground, ice, soul speed, speed pots, vehicles, elytra, firework, riptide, wind burst
- Writes **session summaries** (`sessions.log`) plus rate-limited samples
- Adds log-only **timer** and **fly/hover** heuristics
- Adds log-only **ore-find** (diamond / ancient debris rate + enclosed/beeline/axis path), with optional CoreProtect audit after a flag
- Console logging off by default

## Log files (`plugins/AdaptiveAntiCheat/`)

| File | Contents |
|------|----------|
| `sessions.log` | One line per over-threshold speed session (use this first) |
| `samples.log` | At most ~1 Hz over-threshold samples with full context |
| `skips.log` | Teleports / tick gaps / world changes (rate-limited) |
| `timer.log` | Extra move events vs server ticks |
| `fly.log` | Sustained air time / hover without elytra/vehicle |
| `xray-sessions.log` | Ore-find flags (waste-per-ore + path shape) and CoreProtect audits |

Old `violations.log` is unused. CoreProtect is a softdepend: if it is missing, live ore-find still logs and the audit line is skipped.

## Building

```bash
mvn clean package
```

Jar: `target/AdaptiveAntiCheat-1.1.0-SNAPSHOT.jar`. Java 21+, Paper 26.2+.

On first 1.1 boot the v1 `config.yml` is renamed to `config.yml.bak-v1` and the global `multipliers.speed` value is cleared.

## Commands

| Command | Description |
|---------|-------------|
| `/ac trust <player>` | Manually mark a player as trusted |
| `/ac untrust <player>` | Manually mark a player as untrusted |
| `/ac info <player>` | Playtime + trust |
| `/ac status` | Per-context adaptive multipliers |
| `/ac reload` | Reload config |

Permission: `adaptiveac.admin` (default: op)

## License

MIT

Built for **A Zombie Pigman Broke My Door**.
