# Force Client Anticheat

**Require the [Client Anticheat](https://modrinth.com/mod/aegisanticheat) client mod to play on your server.**
Players who join without it get kicked.

## Features

- **Hard requirement** — joining without Client Anticheat gets you kicked, cleanly.
- **Configurable** — set the grace period, customise the kick message (colour codes + line breaks), toggle console logging.
- **Bypass permission** — `forceaegisac.bypass` lets staff or trusted clients skip the check.
- **Lightweight** — no dependencies, no database, tiny jar. Stable APIs only.

## Compatibility

- **Minecraft 1.21.1 → latest** — one jar for all of it, no per-version downloads.
- **Paper** and its forks (Purpur, Pufferfish, Folia-family, etc.). Works on Spigot too, though kick-message formatting is nicer on Paper.

## Commands & permissions

| Command | Permission | Description |
| --- | --- | --- |
| `/forceaegisac reload` (`/faac`) | `forceaegisac.admin` (op) | Reload the config. |

| Permission | Default | Effect |
| --- | --- | --- |
| `forceaegisac.bypass` | `false` | Never kicked for missing the mod. |
| `forceaegisac.admin` | op | Allows `/forceaegisac reload`. |

### Handshake compatibility (Client Anticheat 2.1)

Force Client Anticheat 1.1.0 requires Client Anticheat 2.1 on every supported
Minecraft version. Replace both the server plugin and the client mod JARs together.
The new `clientanticheat:handshake_v2` channel validates protocol revision 2 and
the exact client release before accepting a handshake; older and malformed
messages do not satisfy the join check. The existing bypass permission still applies.
Existing server configurations keep their custom kick message.

This checks release compatibility; it does not authenticate an unmodified client.
