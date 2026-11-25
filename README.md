# Create: Aerospace Engineering

**English** | [日本語](README.ja.md)

Launch rockets from a pad, put satellites into orbit, find and command them from a ground control console, and watch them cross
the night sky. Needs [Create](https://modrinth.com/mod/create); fuels from
[Create: Refinery 'n' Reactors](https://github.com/Chemi-Create-Team/chemicreate-sub) are used when it is installed.

Everything happens in the normal world. There is no trip to space.

## Launching

1. **Launch Pad**: fill it with fuel, 8000 mB at most, by bucket or pipe. A launch burns 4000 mB. Lava always works; naphtha,
   methanol, low-sulfur diesel and hydrogen work with Refinery 'n' Reactors (tag `create_aerospace_engineering:rocket_fuel`).
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

## Assembly line

A GUI-free robot-arm assembly system. Everything is done by right-clicking, wrenching and feeding blocks with hoppers.

- **Assembly Jig** – the plate the arms build on. Its work area is up to 3x3x3 blocks above it. A wrench switches the recipe (before work starts), an empty hand shows progress. Pipes can fill its 1000 mB tank (used by the spray nozzle).
- **Industrial Arm** – a yellow-tinted Create mechanical arm that runs on rotation (16 rpm is the recipe speed, faster is quicker; 4 SU impact). Use a tool on it to fit it, an empty hand takes it off, a tool in hand swaps it. Hoppers, funnels, chutes and belts can reach its one-slot tool inventory; pipes fill its own 1000 mB tank.
- **Tools** (stack size 1): Gripper (places blocks), Welding Torch (seam welds), Laser Cutter (perimeter cut), Spray Nozzle (full spray, uses fluid), Precision Wiring Head (6x6 wire grid with ferrite cores).
- An arm looks for a jig within 5 blocks, takes the nearest open step of the current stage that its tool can do, fetches the material from chests, depots or belts within 5 blocks, moves and works. 2 to 4 arms can share a jig; a step goes to one arm only, steps of one stage run in parallel and the next stage waits.
- The work is drawn live: a weld bead cooling from white to silver, wires and ferrite cores, the laser groove, the paint film. When the last step is done the temporary blocks vanish, the product drops and steam rises.

Recipes are data: `data/<namespace>/assembly/<name>.json`. Samples: `welded_iron_beam` (two iron blocks, seam weld) and `magnetic_core_memory` (iron frame, wiring grid, weld the ends).

```json
{
  "size": [3, 1, 3],
  "stages": [
    [ { "pos": [1, 0, 1], "action": "place", "tool": "gripper", "block": "minecraft:iron_block" } ],
    [ { "pos": [1, 0, 1], "action": "process", "tool": "wiring_head", "pattern": "grid_wire", "consume": "minecraft:copper_ingot", "time": 120 } ]
  ],
  "result": { "id": "create_aerospace_engineering:magnetic_core_memory", "count": 1 }
}
```

`pos` is counted from the block above the jig. Patterns: `seam_weld`, `perimeter_cut`, `grid_wire`, `full_spray` (needs `"fluid": {"id": "minecraft:water", "amount": 100}`). `time` is ticks at 16 rpm.

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
