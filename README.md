# Create: Aerospace Engineering

**English** | [日本語](README.ja.md) · [Changelog](CHANGELOG.md) · [Specifications (Japanese)](docs/README.md)

Launch rockets from a pad, put satellites into orbit, find and command them from a ground control console, and watch them cross
the night sky. Needs [Create](https://modrinth.com/mod/create); fuels from
[Create: Refinery 'n' Reactors](https://github.com/Chemi-Create-Team/chemicreate-sub) are used when it is installed.

Everything happens in the normal world. There is no trip to space.

## Launching

1. **Launch Pad**: fill it with fuel, 8000 mB at most, by bucket or pipe. A launch burns what the rocket needs (4000 mB for CAE's rocket). Lava always works; naphtha,
   methanol, low-sulfur diesel and hydrogen work with Refinery 'n' Reactors (tag `create_aerospace_engineering:rocket_fuel`).
2. Use a **Rocket** on the pad: it stands up on it (an ordinary entity with a 3D model). The standard rocket is 60 m tall (60 blocks) and needs a clear 10 × 60 block space above the pad.
3. Use **Satellites** on the rocket to load them. Every payload has a **mass** and every rocket a **maximum payload**: CAE's rocket
   lifts 1000 kg and a satellite weighs 500 kg, so it carries two (the tooltips show both numbers). Rename a satellite on an anvil
   to name it. Right-click the pad with an empty hand to read fuel and loading; sneak-right-click with an empty hand to take the
   rocket and its payloads back.
4. Give the pad a **redstone signal** (a new one: a pad that is already powered does not fire when you fill it later). The rocket climbs for a few seconds, releases every payload as its own satellite and is gone. If the pad is
   broken before launch, the rocket and the satellites drop as items.

![Standard 60 m rocket on the launch pad](docs/images/rocket-60m.jpg)

Besides CAE's own rocket there is the **H3** (JAXA / Mitsubishi Heavy Industries) at half scale in its three flown
configurations: **H3-30S** (3 LE-9, no boosters, 4000 kg), **H3-22S** (2 SRB-3, 4800 kg) and **H3-24W** (4 SRB-3, wide fairing,
6500 kg, 31.5 blocks tall). Its launch shows the steam cloud, the SRB-3 flame and white trail, the nearly clear hydrogen flame of
the LE-9 with shock diamonds, and the SRB-3 and fairing separations. See [docs/launch.md](docs/launch.md) (Japanese).

The satellite is released into **geostationary orbit** over the equator at the launch longitude, like a communications
satellite: it hangs over one spot of the world and never moves. Step it down with the altitude dial and tilt it with the
inclination dial to have it cross the sky instead.

## Ground Control Console

Place it so the screen faces away from you. Like the reactor console in Refinery 'n' Reactors, the controls are on its top; click
the one you want.

- Row of four: **previous**, **next** (choose a satellite), **map**, **mode** (standby, beacon, flash).
- Two dials, **altitude** and **inclination**: click the left half to step down, the right half to step up. A change
  keeps the satellite where it is and sets it on the new orbit.
- **Deorbit** is under a cover. The first click lifts the cover, a second click within ten seconds removes the satellite.
- The screen shows the selected satellite's latitude and longitude, **the block coordinates under it**, orbit, mode and
  whether it is above your horizon. The **map** opens a mission control screen: the Earth as a sphere of cubes with every satellite over it as a small voxel model and the orbit of the selected one (yellow) drawn in 3D (drag to turn the Earth), the list of satellites on the left and the readout of the selected one on the right.

Only the owner of a satellite (or an operator) can change it. Anyone can read it. A **Display Link** pointed at the console
shows the selected satellite's position on any display.

## Where is "where"

The world is a map of the Earth in which one degree is 1000 blocks: x is longitude (east is plus) and -z is latitude (north is
minus z). A satellite at 12.0N 45.0E is above x = 45000, z = -12000. The Earth turns once per Minecraft day.

Orbit periods are scaled to the game: the lowest orbit takes 2 minutes, the highest, a whole day (hangs over one place).

## The sky

At night every satellite above the horizon is a bright dot (a trail too, when it moves); **beacon** is steady, **flash** blinks, **standby**
is dim. You see them from the spot where you stand. A geostationary satellite stays at one place in the sky; a low one crosses it in about ten seconds.

## Control Computer (Create: Frontier Science)

If [Create: Frontier Science](https://github.com/Chemi-Create-Team/Create-Frontier-Science) 0.3.26 or newer is installed, the ground
console is also an app of its Control Computer: open **Extra apps** on a display board and pick **Satellites**. It is optional;
without Frontier Science nothing changes.

- **Controls** tab: the satellite the computer has selected (name, owner, latitude and longitude, the block coordinates under it,
  altitude, inclination, period, mode, and whether it is above the horizon of the board) and the console's buttons: **previous**,
  **next**, **mode**, altitude and inclination (**-** and **+**), and **deorbit** (the first press asks for confirmation, the second
  within ten seconds deorbits).
- **Map** tab: the same cube Earth with the satellites over it, seen from over the board; **previous** / **next** and **turn left** / **turn right** (45 degrees a step).
- It does not need a Ground Control Console block. Each computer keeps its own view and selection (forgotten on a restart). Only the owner of a
  satellite (or an operator) can change it, as on the console.

## Satellites and rockets of other mods (API)

Other mods can register their own item as a satellite payload. Used on a Launch Pad, it rides a CAE rocket to orbit and becomes a
satellite of that mod, which reads its position and keeps its own data on it (for a GPS, a relay for a channel network, ...). Both sides
stay optional. The ground console shows the payload as **PAYLOAD**. A payload sets its own mass.

Other mods can also add **rockets**: their own item stands a rocket of their own on the pad, with its own maximum payload, fuel,
climb, speed and size, drawn however they like (any model, texture, animation and exhaust). See [docs/api.md](docs/api.md) (Japanese) and
`dist/create-aerospace-engineering-api-*.jar`.

## Commands (operators)

`/satellite list`, `/satellite add <name> [tier 0-3]` (geostationary unless a lower tier is given), `/satellite remove <name>`.

## Building

`./gradlew build`. `./gradlew runGameTestServer` runs the tests.

## Credits

The land of the cube Earth is taken from [Natural Earth](https://www.naturalearthdata.com/) 1:50m land and lakes, public domain.
