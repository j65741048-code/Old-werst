# Infinite Rails: Villager Traffic (NeoForge 1.21.1)

Addon for Create + Create: Loconautics (+ Sable). **Not compiled or run yet** - written against the
Create 6.0.10 and Loconautics sources you uploaded. Expect a few compile fixes on first build.

## What it does
- **Infinite railway**: a worldgen structure (`infiniterails:rail_network`) placed every 192 blocks, so 192x192 tiles join
  seamlessly in every direction across the Overworld. Rails sit at Y=90 (`rail_y` in
  `data/infiniterails/worldgen/structure/rail_network.json`); it tunnels through hills and bridges valleys.
- **2 lanes**: each axis has one lane per direction (x/z = +-3), signalled one way.
- **Switches**: curves branching off the lanes (Create makes these switches) at the tile centre, plus diamond crossings.
- **Signals**: before the junction and around every branch line.
- **Branch lines**: every lane has a passing-loop siding with its own station ("Siding ..."), and the inbound halves get
  platform stations ("Main ...").
- **AI traffic**: Loconautics locomotive (`boiler_cart`) + N passenger carts, driven by a **villager conductor**
  (same mechanism as giving a schedule to a seated mob), looping "Main *" -> "Siding *".
- **Spread out**: trains spawn on random lane cells 64-160 blocks from a player, at least `minTrainSpacing` (96) from other AI trains.
  They are removed when 288+ blocks from every player.

## Config (server config, Create-style screen)
| Option | Meaning |
|---|---|
| `maxAiTrains` | -1 automatic, 0 none, N cap, 2147483647 infinite |
| `deviceClass` | 0 detect, 1 mobile, 2 PC |
| `mobileTrainCap` | automatic cap on mobile (12) |
| `adaptiveLimit` / `targetMspt` | live cap rises/falls to keep ticks under the target, so "infinite" on PC = what the machine can carry |

## Build
1. Build Create-Loconautics-indev (`./gradlew build`) and copy `build/libs/loconautics-*.jar` into `libs/`.
2. `./gradlew build` here. Run with `./gradlew runClient` (needs Create, Sable, Simulated resolved from the Maven repos).

## Known risks
- Mobile detection is a heuristic (launcher env vars). Override with `deviceClass`.
- Seating the villager inside a Sable sub-level and the schedule hand-off copy Loconautics' private code paths; unverified.
- `BaseConfigScreen.withSpecs(...)` signature in `ClientInit` is from memory of Create 6; if it fails, swap to NeoForge's `ConfigurationScreen`.
- Branch lines are passing loops, not dead-end stubs, so AI trains never need to reverse.
- Signal/siding layout may need tuning once you watch trains run (deadlocks are possible).
