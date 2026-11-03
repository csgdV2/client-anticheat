# Force Client Anticheat

Requires players to send a valid Client Anticheat handshake before they can stay
on the server. Plugin version 1.3.0 accepts Client Anticheat 2.2 and newer releases
that use handshake protocol 2. Client 2.3 includes the latest shield behavior checks.

## Install

1. Stop the server.
2. Remove the previous Force Client Anticheat plugin JAR.
3. Put `ForceClientAnticheat-1.3.0.jar` in the server's `plugins` folder.
4. Start the server.
5. Give players the Client Anticheat JAR for their Minecraft version.

The current client builds cover Minecraft 1.21.1, 1.21.4, 1.21.8, 1.21.11, and 26.2.
The plugin is built against Paper 1.21.1 and compiled for Java 21. Use the Java
version required by your server. Folia support is not implemented.

## Commands

| Command | Permission | Purpose |
| --- | --- | --- |
| `/forceclientanticheat reload` | `forceclientanticheat.admin` | Reload the server plugin config. |
| `/fcac reload` | `forceclientanticheat.admin` | Short alias for the same command. |

The previous `/forceaegisac reload` and `/faac reload` commands remain aliases.
Operators have the admin permission by default. The server console can also run
the reload command without the leading slash.

## Permissions

| Permission | Default | Purpose |
| --- | --- | --- |
| `forceclientanticheat.admin` | Operators | Allows config reloads. |
| `forceclientanticheat.bypass` | Nobody | Exempts the player from the mod requirement. |

The legacy `forceaegisac.admin` and `forceaegisac.bypass` permissions grant their
corresponding new permissions. Existing permission groups continue to work.

## Server config

The file is `plugins/ForceClientAnticheat/config.yml`.

```yaml
grace-period-seconds: 5
kick-message: "&c&lClient Anticheat update required\n\n&7Install &fClient Anticheat 2.2 or newer&7 to join.\n&bhttps://clientanticheat.netlify.app"
log-checks: true
```

`grace-period-seconds` is the wait after joining before the player is kicked if no
valid handshake arrives. The minimum is one second. Reloading does not reschedule
checks that are already pending.

`kick-message` supports ampersand color codes and new lines. Existing installations
keep their custom message when the plugin JAR is updated.

`log-checks` controls verification, timeout, and bypass messages in the server log.

These are server settings. Players do not get a config to turn off client detection.

## What the handshake checks

The plugin accepts protocol revision 2 on `clientanticheat:handshake_v2` and a client
release of at least 2.2. Old channels, older releases, invalid version strings,
and malformed messages do not satisfy the join check. Newer compatible releases,
including 2.3, can join without another plugin update.

The handshake enforces release compatibility. A modified client can imitate this
public protocol, so it does not prove that the client is unmodified.

## Reports

The mod uploads recorded findings to [the website](https://clientanticheat.netlify.app).
This plugin enforces the join requirement and does not ban players based on reports.
Behavioral Evidence is not definitive proof of cheating.

## Build

From this plugin directory, run:

```text
mvn verify
```

The JAR is written to `target/ForceClientAnticheat-1.3.0.jar`.
