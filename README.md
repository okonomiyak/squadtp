*[日本語 README](README.ja.md)*

# Squad Teleport (squadtp)

A squad (party) mod for Minecraft 1.20.1 / Forge 47.x.
Create a squad of up to 4 players (size configurable) and teleport to a member's current location or a shared rally point.
If JourneyMap (5.9.x–5.10.x) is installed, member locations and the rally point are shown as waypoints on the map (**all core features work fully without JourneyMap**).

## License

GNU General Public License v3.0 (GPL-3.0-only). See [`LICENSE`](LICENSE) for the full text.

    squadtp — a squad (party) teleport mod for Minecraft
    Copyright (C) 2026 squadtp contributors

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

## Commands

| Command | Description | Permission |
|---|---|---|
| `/squad create` | Create a squad (the creator becomes leader) | - |
| `/squad invite <player>` | Invite a player (they can respond via the [Join]/[Decline] chat buttons) | Leader |
| `/squad accept` / `/squad deny` | Respond to an invite | Invitee |
| `/squad join <player>` | Request to join that player's squad | Not in a squad |
| `/squad approve <name>` / `/squad reject <name>` | Respond to a join request | Leader |
| `/squad leave` | Leave (leadership auto-transfers to the longest-standing member if the leader leaves) | - |
| `/squad kick <member>` | Kick (offline members can be targeted by name) | Leader |
| `/squad promote <member>` | Transfer leadership | Leader |
| `/squad disband` | Disband the squad | Leader |
| `/squad info` | Show members, online status, and the rally point | - |
| `/squad tp <member>` | Teleport to a member's current location | - |
| `/squad setrally` | Set the rally point to your current location | Leader |
| `/squad rally` | Teleport to the rally point | - |
| `/squad beacon` | Teleport to the respawn beacon (same cooldown/cost as regular TP) | - |
| `/squad admin` | Show current feature switches and the revive cast time | OP (level 2+) |
| `/squad admin enable\|disable <feature>` | Toggle a feature server-wide | OP (level 2+) |
| `/squad admin revivetime <seconds>` | Change the revive cast time at runtime (no restart needed, persisted to the world) | OP (level 2+) |
| `/squad admin revivetime reset` | Reset the cast time back to the config default | OP (level 2+) |

### Admin feature switches

`/squad admin disable <feature>` lets you turn individual features off server-wide at runtime (persisted to world data, survives restarts).
Available features: `create` (squad creation) / `invite` (invites) / `join` (join requests) / `tp` (member teleport) / `rally` (rally point - only the anytime `/squad setrally`/`/squad rally`) / `beacon` (beacon placement and the anytime `/squad beacon`) / `respawn` (**all respawn-time teleporting**: the respawn-chooser screen, automatic rally respawn via `rallyRespawnEnabled`, and `/squad respawn rally|member|beacon`) / `positions` (position sharing / map display) / `dummy` (test dummy block).
All checks happen on the server at the point of execution, so there is no way to bypass them via the GUI or chat buttons. Disabling `positions` immediately clears client-side position displays too.

**Respawn-time teleporting and everyday teleporting toggle independently**: disabling `respawn` still leaves `/squad rally` and `/squad beacon` (everyday travel) working, and disabling `rally`/`beacon` has no effect on respawning at the rally point or beacon from the respawn chooser (`tp`, member teleport, was already independent from the respawn flow).

The revive cast time (default 5 seconds, `reviveCastSeconds`) can be **changed at runtime without a server restart** via `/squad admin revivetime <1-60>`, and the value is persisted to world data (`/squad admin revivetime reset` restores the config default).

## GUI

Press **K** (rebindable under the "Squad Teleport" key category) to open the squad screen. If content doesn't fit, scroll with the mouse wheel (a scrollbar appears on the right edge).

The screen is split into 4 tabs (the "Locations" tab is hidden while not in a squad):

- **Squad**: while not in a squad, a Create Squad button and any pending invite ("X invited you" + Accept/Decline). While in one, the member list (leader ★, coordinates, online status, auto-refreshing every second), a [TP] button per member, [Kick]/[Promote] for the leader, and [Leave]/[Disband]
- **Locations** (in a squad only): the rally point display / [Go] / [Set Here], and the respawn beacon display / [Go] / remaining uses
- **Recruit**: the leader sees a "Join Requests" list ([Approve]/[Reject]) and an "Invitable Players" list (from the online tab list) with an [Invite] button. Anyone can request to join another online player's squad from the "Request to Join" section
- **Settings**: the approach-alert bell sound ON/OFF toggle

**There are two ways to join a squad**: ① the leader invites you and you accept, ② you request to join and the leader approves. Both are available from the GUI and from clickable chat buttons.

**Switching squads**: both invites and join requests work **even if you are already in a different squad**. The moment you accept/get approved, you automatically leave your old squad before joining the new one (all validation - squad-full checks, team restrictions, etc. - runs *before* the switch, so a failed join never leaves you squadless). Your old squad is notified ("X left the squad" / "X is now the leader"). The GUI's "Request to Join" section stays visible even while you're in a squad (your own squad's members are excluded from the list).

Every GUI action simply sends the corresponding `/squad` command from the client, so permission checks remain entirely server-side as before (the GUI adds no new attack surface).

## Test Dummy Block

A "stand-in for a player" block for testing squad features solo (in the "Functional Blocks" creative tab, pumpkin-textured).

- Gets a unique name when placed (e.g. `Dummy_a1b2`)
- **The squad leader right-clicks it** to toggle its membership in their squad
- While a member, it's treated like an online player: you can verify position sync, JourneyMap waypoints, `/squad tp Dummy_xxxx`, and the GUI's TP button entirely solo
- Breaking the block automatically removes it from the squad; while its chunk is unloaded it counts as "offline"
- A dummy can never be leader (`/squad promote` is rejected and it's excluded from automatic leadership succession; if only dummies remain after the leader leaves, the squad disbands)

## Respawn Beacon

Right-clicking the "Respawn Beacon" item (Functional Blocks creative tab) places a dedicated 1-HP entity. It acts as a **third respawn destination** for the squad, alongside the rally point and members.

- Any squad member can place one. At most one active beacon per squad at a time (placing a new one removes the old one)
- Has a limited number of uses (default 4); each use notifies the squad of the remaining count, and it's removed once depleted
- **Fixed at 1 HP**. Immune to damage from squad members and players on the same scoreboard team, but **destroyed by a single hit from a hostile mob or a player on another team** - a forward spawn point with real risk to defend
- Teleportable via both `/squad beacon` (anytime, same cooldown/cost as `/squad tp`) and `/squad respawn beacon` (free, only inside the post-death respawn-choice window)
- Shown in the squad GUI, the respawn-chooser screen (with a map marker), and as a JourneyMap waypoint
- Can be disabled server-wide with `/squad admin disable beacon`. The use count is set via `beaconUses` (default 4)

## Revive (Downed) System

When a squad member takes lethal damage, instead of dying they enter a **downed state** (health pinned at 1, heavily slowed, "DOWNED" shown on screen).

- If not revived within the timeout (default 30s), they proceed to a real death. **Hold G** (rebindable) or run `/squad giveup` to give up immediately instead of waiting
- While downed you get a **prone pose (swimming animation) + a glowing outline**, so squadmates can spot you at a glance, even through walls. **The only things you can do are crawl, chat, and give up** (jumping / attacking / mining / using items or blocks / dropping items / swapping hands / opening inventory-like screens / any teleport are all blocked)
- **TACZ (gun mod) integration** (optional, soft dependency): if TACZ is installed, guns cannot be fired while downed (`GunFireEvent`/`GunShootEvent` are canceled). No effect at all if TACZ isn't installed
- **SuperbWarfare (gun mod) integration** (optional, soft dependency): while downed, the projectile itself (vanilla `Projectile` type, `superbwarfare` namespace) is prevented from ever spawning, effectively disabling firing. This is a workaround for the fact that SuperbWarfare, unlike TACZ, has no cancelable "about to fire" event (the gun still animates, plays its sound, and consumes ammo, but no bullet appears and no damage is dealt). Has zero direct dependency on any SuperbWarfare class - only the vanilla base type and a namespace string check
- **Approach alert**: while downed, when a squad member comes within `approachAlertRadius` (default 24 blocks), an action-bar message ("X is approaching (Nm)") plus a sound plays once (won't repeat until they leave and come back). The bell sound can be toggled per-player from the "Settings" section at the bottom of the squad GUI (the message itself always shows)
- A squad member can **hold right-click** (default 5s) on a downed player to revive them. A progress gauge shows on screen. **Moving more than 4 blocks away, or releasing the click, cancels the channel**
- On a successful revive: a portion of max health is restored (default 30%) plus brief invulnerability (default 3s)
- **Solo players (not in a squad) skip the downed state entirely and die immediately**. Instant-death sources (`/kill`, the void, etc.) bypass the downed state too
- Teleporting is disabled while downed. Logging out while downed counts as an immediate death (anti combat-logging)
- Can be disabled server-wide with `/squad admin disable revive`

Config (`revive` section): `downedTimeoutSeconds` (30) / `reviveCastSeconds` (2.5, fractional seconds allowed, overridable at runtime via `/squad admin revivetime`) / `reviveHealPercent` (30) / `reviveInvulnSeconds` (3) / `allowNonSquadRevive` (false = only squadmates can revive) / `approachAlertEnabled` (true, server config) / `approachAlertRadius` (24, server config) / `giveUpHoldTicks` (60 = 3s, how long the give-up key must be held while downed; 20 ticks = 1 second. Kept close to the revive cast time (default 5s) so giving up isn't a strictly faster escape hatch than waiting to be revived)

The **bell sound** alone is a personal preference stored in a client-side config (`config/squadtp-client.toml`, `bellSoundEnabled`), toggleable anytime via the GUI's [ON]/[OFF] button (independent of any server or world).

## Configuration (`world/serverconfig/squadtp-server.toml`)

- `squad.maxSquadSize` (default 4) / `squad.inviteExpirySeconds` (default 120)
- `squad.requireSameTeam` (default true) — when vanilla scoreboard teams (`/team`) are in use, **only players on the same team as the squad leader can join** (fine if neither has a team; blocked if only one does). Enforced across invites, accepts, join requests, and approvals
- `teleport.tpCooldownSeconds` (default 60, 0 disables)
- `teleport.tpCostMode` = `NONE` / `XP` / `ITEM` (default NONE)
  - `tpCostXpLevels` (default 3) / `tpCostItem` (default ender_pearl) / `tpCostItemCount` (default 1)
- `teleport.combatBlockSeconds` (default 15, 0 disables) — after a member takes damage, `/squad tp` to them and respawn-chooser spawning near them are blocked for this many seconds (combat tag)
- `teleport.allowCrossDimensionTp` (default true)
- `teleport.rallyRespawnEnabled` (default false) — when enabled, automatically teleports to the rally point after a death respawn (takes precedence over the chooser below)
- `teleport.respawnChoiceEnabled` (default true) — shows a "choose your respawn point" screen after a death respawn (rally point / near an online member / stay here)
- `teleport.respawnChoiceWindowSeconds` (default 60) — how long the choice stays valid; the server only allows `/squad respawn` right after a respawn, so it can't be abused as a regular teleport
- `teleport.spawnDangerRadius` (default 4, 0 disables) — blocks respawn-chooser spawning if a **hostile mob or a player from another team** is within this radius of the destination
- `beacon.beaconUses` (default 4) — number of uses per respawn beacon
- `sync.posUpdateIntervalTicks` (default 20) — interval between position sync broadcasts

## Design Notes

- **Server-authoritative**: squad data lives in `SquadManager` (a `SavedData`) persisted to the overworld. Every action goes through a command (i.e. runs server-side), so there is no client→server custom packet to spoof.
- **Sync**: only two S2C packet types exist — `SquadSyncPacket` (on membership/config changes) and `SquadMemberPosPacket` (periodic position broadcast, squad-only).
- **JourneyMap integration**: `compat/JourneyMapCompat` is the sole entry point. Classes under `compat/journeymap/` (which reference the API) are only classloaded when `ModList.isLoaded("journeymap")` is true. The plugin itself, `SquadJmPlugin`, is discovered and instantiated by JourneyMap via the `@ClientPlugin` annotation.
- Targets **JourneyMap API v1.9** (implemented by JourneyMap 1.20.1-5.9.x through 5.10.3).

## Building & Running

Requirement: JDK 21 for running Gradle (set via `org.gradle.java.home` in `gradle.properties`; compilation uses the Java 17 toolchain).

```
gradlew build        # -> build/libs/squadtp-<version>.jar (for distribution)
gradlew runClient    # dev client 1 (username Dev1, run/)
gradlew runClient2   # dev client 2 (username Dev2, run2/)
gradlew runServer    # dev server (run-server/, online-mode=false already set)
```

### Two-player test procedure

1. In three terminals (or IDE run configs), start `runServer`, then `runClient`, then `runClient2`
2. On both clients: Multiplayer → add server `localhost` → connect
3. On Dev1: `/squad create` → in the K-key GUI, [Invite] Dev2 → Dev2 accepts from their GUI or chat
   (the reverse direction: Dev2, while squadless, uses [Request to Join] from their GUI → Dev1 [Approve]s)
4. Verify mutual TP, position display, respawn chooser, etc.

JourneyMap dev-environment testing: `build.gradle`'s `modRuntimeOnly 'curse.maven:journeymap-32274:5789363'` pulls in a remapped (de-obfuscated) copy of JourneyMap 5.10.3 automatically into `runClient`.
TACZ integration is likewise auto-installed into the dev environment via `modRuntimeOnly 'curse.maven:timeless-and-classics-zero-1028108:8141310'`.
**Note: never place the distributed JourneyMap/TACZ jar directly into `run/mods/`** (it stays SRG-obfuscated, so its Mixins fail and the game won't launch). To remove an integration from the dev environment, comment out the corresponding line in `build.gradle`.

## Manual Verification Steps

1. Run `gradlew runClient` twice, or `runServer` + two client connections, to get two accounts
2. A: `/squad create` → `/squad invite B` → B: click [Join] in chat
3. `/squad info` confirms both members and the leader marking
4. Move apart, then `/squad tp <other player>` → also verify the cooldown message on the second attempt
5. A: `/squad setrally` → B: `/squad rally`
6. With JourneyMap installed: confirm member waypoints (colored) and the rally waypoint (gold) track on the fullscreen map roughly every second
7. Confirm the squad survives a server restart (SavedData)
