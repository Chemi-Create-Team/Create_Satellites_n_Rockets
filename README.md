# Create: Aerospace Engineering

Launch rockets from a pad, put satellites into orbit, find and command them from a ground control console, and watch them cross
the night sky. Needs [Create](https://modrinth.com/mod/create); fuels from
[Create: Refinery 'n' Reactors](https://github.com/Chemi-Create-Team/chemicreate-sub) are used when it is installed.

Everything happens in the normal world. There is no trip to space.

## Launching

1. **Launch Pad**: fill it with fuel, 8000 mB at most, by bucket or pipe. A launch burns 4000 mB. Lava always works; naphtha,
   methanol, low-sulfur diesel and hydrogen work with Refinery 'n' Reactors (tag `create_satellites_n_rockets:rocket_fuel`).
2. Use a **Satellite** on the pad: a rocket (an ordinary entity with a 3D model) stands up on it. Rename the satellite on an
   anvil to name it. Right-click the pad with an empty hand to read fuel and loading.
3. Give the pad a **redstone signal** (a new one: a pad that is already powered does not fire when you fill it later). The rocket climbs for a few seconds, releases the satellite and is gone. If the pad is
   broken before launch, the satellite drops as an item.

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
  whether it is above your horizon. The **map** shows every satellite on a world map with the selected one's track.

Only the owner of a satellite (or an operator) can change it. Anyone can read it. A **Display Link** pointed at the console
shows the selected satellite's position on any display.

## Where is "where"

The world is a map of the Earth in which one degree is 1000 blocks: x is longitude (east is plus) and -z is latitude (north is
minus z). A satellite at 12.0N 45.0E is above x = 45000, z = -12000. The Earth turns once per Minecraft day.

Orbit periods are scaled to the game: the lowest orbit takes 2 minutes, the highest, a whole day (hangs over one place).

## The sky

At night every satellite above the horizon is a bright dot (a trail too, when it moves); **beacon** is steady, **flash** blinks, **standby**
is dim. You see them from the spot where you stand. A geostationary satellite stays at one place in the sky; a low one crosses it in about ten seconds.

## Commands (operators)

`/satellite list`, `/satellite add <name> [tier 0-3]` (geostationary unless a lower tier is given), `/satellite remove <name>`.

## Building

`./gradlew build`. `./gradlew runGameTestServer` runs the tests.
