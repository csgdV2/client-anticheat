# External input and Behavioral Evidence

Client Anticheat 2.3 records two separate kinds of evidence. Neither kind establishes
that a player cheated.

Windows input observations count events that Windows marks as injected while
Minecraft gameplay has focus. Mouse button presses, key presses, pointer movement,
and scrolling have separate counters. The counters do not identify the originating
program. Accessibility tools and legitimate remappers may generate these events.
The monitor does not record typed text, key codes, or mouse positions in reports.
It does not suppress input. Input observations expire if the game stops refreshing
its active state.

Behavioral Evidence is not definitive proof of cheating. The experimental checks
use repeated samples and report their measurements. They do not kick or ban players.
They do not identify whether a pattern came from an external cheat, another mod,
hardware, or legitimate play.

## Included checks

The regular clicking and repeated click cycle checks have been removed. Historical
findings from those checks are hidden in the website presentation without deleting
the original stored report data.

Axe swaps aligned with shield raises require 24 independent blocking starts.
An opponent must first be observed not blocking for at least 250 milliseconds
while the local player holds a sword. At least 22 starts must be followed by an
axe swap within 800 milliseconds, with a mean observed delay no greater than
150 milliseconds and standard deviation no greater than 30 milliseconds.
Simultaneous observations are reported as occurring in the same sampled frame.
They do not establish that the shield raise caused the swap. Already raised
shields, target changes, and missing frames cannot create a fresh reaction.

Shield specific axe use requires at least 24 sword attacks against nonblocking
opponents as comparison data, plus 24 separate sword to axe episodes. At least
22 episodes must start against an actively blocking opponent and include a
charged shield attack without a recorded air miss or another kind of attack.
Blocking must then stop within 750 milliseconds after at least 22 of those
episodes. Simply carrying a shield does not count as blocking. Holding an axe
throughout a fight does not create repeated equip episodes. Lowering a shield
is not server confirmation that it was broken, and other players or voluntary
input can cause the same observation.

Target acquisition timing requires 40 independent acquisitions with a fresh click,
a charged attack, and recent frame samples. Target absence and an acquisition
cooldown prevent repeated attacks on the same continuously tracked player from
counting as independent reactions. Frames over 25 milliseconds cannot start an
eligible acquisition. A finding requires an observed mean click delay no greater
than 50 milliseconds and standard deviation no greater than 5 milliseconds.
These are delays from the sampled game raycast, not measurements of biological
reaction time. Frame timing can affect the result.

Shield breaker sequences require 24 observed sword, axe, blocking target attack,
and sword sequences. A finding requires a mean sequence duration no greater than
350 milliseconds and standard deviation no greater than 10 milliseconds.
The observation is an attack attempt against a blocking player. It does not
confirm a shield disable on the server. Item changes observed between frames
can be missed or have limited timing precision.

Precise aim corrections require at least 24 corrections over 600 eligible frames.
Each correction must move from at least 15 degrees away from a candidate player's
centre to within 0.15 degrees, with a matching camera rotation and very little
variation in the final error. Candidate changes and frame stalls reset the sample
chain. This is an experimental pattern check and needs legitimate gameplay
calibration before drawing conclusions.

The behavioral review score combines findings and halves their contributions each
minute. It is a review priority, not a probability of cheating. Session evidence
remains in the report even after its score decays. Opening a menu, losing focus,
leaving the session, or a large position discontinuity breaks the sample chain.
Creative mode and spectator mode are excluded.

## Detection controls

All supported checks run automatically. There is no player settings file and no
player option to disable detection. Any configuration file from a development
build is ignored. Windows input checks depend on Windows support and hook
availability. A modified client can still tamper with its own code or reports.

## Coverage and limits

Windows hooks are available only on Windows. Their status appears in the detailed
report. Behavioral checks remain available on other operating systems. Hook
installation does not guarantee full coverage. Windows can remove unresponsive
hooks and some input paths do not carry an injected flag.

Hardware macros, virtual devices, driver level input, external visual overlays,
and modified clients may evade these checks. Client reports and the public
handshake can be imitated by a modified client. No finding is a guarantee that
cheating occurred. No findings is not a guarantee that the client is free of cheats.
Jump Reset and Combo Timing remain removed.

## Installation

Replace the client mod with the JAR for the correct Minecraft version. Supported
targets are 1.21.1, 1.21.4, 1.21.8, 1.21.11, and 26.2.

Install Force Client Anticheat 1.2.0 on the server and remove the previous plugin
JAR. The plugin accepts Client Anticheat 2.2 and newer versions that use protocol 2.
It rejects older releases and malformed handshakes. The existing bypass permission
still exempts permitted players. Existing configuration files keep their custom
kick message.

Deploy the updated `relay` website source to show the evidence filters and new
report layout. Older stored reports retain their original embedded HTML. They do
not gain new measurements. The public list remains compatible with old metadata.

## Validation

Automated tests cover sample minimums, variable legitimate input, focus breaks,
frame stalls, repeated sequences, score decay, report escaping, proof disclaimers,
version compatibility, malformed packets, evidence sanitization, list filters,
sorting, and mixed evidence cards.

Passing these tests verifies the implemented rules. It does not measure accuracy
against real cheats or establish a false positive rate. Real gameplay calibration
and testing in an actual game session are still required.
