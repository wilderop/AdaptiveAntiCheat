# AdaptiveAntiCheat

Adaptive anti-cheat for **Paper** (Minecraft 26.2+).

It uses **trusted high-playtime players** as ground truth.  
When a trusted player triggers a check, the system treats it as a confirmed false positive and automatically loosens that check’s threshold over time.

## Core idea

1. Players with ≥ **500 hours** of playtime are automatically trusted (configurable).
2. You can also manually `/ac trust` or `/ac untrust` anyone.
3. Every violation from a trusted player feeds the adaptive system.
4. After a configurable number of trusted false positives, the tolerance multiplier for that check is raised slightly.
5. Multipliers are persisted across restarts.

This makes the anti-cheat get *better* the longer it runs on your specific server (especially useful for anarchy / high-chaos environments with boats, pigs, crystal PvP, lag, etc.).

## Features (v1.0)

- Automatic trust by playtime + manual trust/untrust
- Adaptive threshold system (starts simple, improves automatically)
- Speed check with vehicle & elytra multipliers + ping compensation
- Violation logging (console + `violations.log`)
- Clean `/ac` admin commands
- Fully configurable

## Building

```bash
mvn clean package
```

The jar will be in `target/AdaptiveAntiCheat-1.0.0-SNAPSHOT.jar`.

Requires **Java 21+** and a Paper 26.2+ server.

## Commands

| Command | Description |
|---------|-------------|
| `/ac trust <player>` | Manually mark a player as trusted |
| `/ac untrust <player>` | Manually mark a player as untrusted |
| `/ac info <player>` | Show playtime + trust status |
| `/ac status` | Show current adaptive multipliers and FP progress |
| `/ac reload` | Reload config |

Permission: `adaptiveac.admin` (default: op)

## Configuration highlights

See `config.yml` for all options. Key ones:

- `trust.auto-trust-hours: 500`
- `adaptive.false-positives-per-adjustment: 8`
- `adaptive.adjustment-amount: 0.03` (3% each time)
- `checks.speed.base-max-speed: 0.42`

## Future plans

- More checks (reach, CPS, bad packets, scaffolding, etc.)
- Per-player trust scores / personal multipliers
- Optional lightweight machine learning layer
- Configurable punishments once confidence is high
- Better vehicle / explosion / knockback handling

## License

MIT (or whatever you prefer — feel free to change).

---

Built for the server **A Zombie Pigman Broke My Door** (lawlessmc.com).
