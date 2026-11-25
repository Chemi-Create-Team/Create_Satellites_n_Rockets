# Create: Aerospace Engineering

Rockets, satellites and space stations for [Sable](https://modrinth.com/mod/sable) contraptions, built to sit next to
[Create: Refinery 'n' Reactors](https://github.com/Chemi-Create-Team/chemicreate-sub).

It is **not** a realistic space sim. Fly high enough and your contraption moves into the *Orbit* dimension: a black sky with
stars, where the only things in sight are the Earth below you, the Moon and the Sun. Nothing orbits; the sky is a picture.

NeoForge 1.21.1, Create 6.0.10+, Sable 2.0.6+. Create: Refinery 'n' Reactors is optional.

## Playing

| Block | What it does |
| --- | --- |
| Launch Pad | Ground that stays behind when a rocket lifts off. Stone, dirt, sand and gravel also stay behind. |
| Rocket Engine | Burns fuel for thrust, opposite to the nozzle. Fill it by hand with a bucket or by pipe (8000 mB). Redstone fires it. |
| Launch Controller | Right-click: everything connected to it (up to 4096 blocks) becomes one Sable contraption and its downward-pointing engines ignite. Right-click again on the flying ship to cut the engines off or light them again. Engines pointing any other way (braking engines) only answer to redstone. |
| Satellite Core | Gives a redstone signal while its contraption is in orbit. |
| Station Core | Reaction wheels: cancels spin and drift. Only works in zero gravity. |

Fuel is anything in the `create_satellites_n_rockets:rocket_fuel` fluid tag: lava, `#c:fuel`, `#c:diesel`, and, when Refinery 'n'
Reactors is installed, naphtha, methanol and low-sulfur diesel. Hydrogen gas is in `rocket_fuel_strong` and gives double thrust.

Above Y 512 a contraption (and the players standing in it) moves to the Orbit dimension at the same X/Z. Orbit has zero gravity and
a little drag, so unpowered things coast to a stop. Fall below Y -200 in orbit and you re-enter above the same spot on Earth.
You can also go there directly: `/execute in create_satellites_n_rockets:orbit run tp @s 0 100 0`.

## Known limits

- A contraption keeps the plot slot Sable gave it, because block entities save absolute plot positions. If that slot is taken in the
  other dimension the move is refused and retried every half second. Many ships in orbit make this more likely.
- Like any Sable contraption, one with no chunks loaded around it is stored until someone comes near.
- The Orbit dimension type copies the overworld build height on purpose; sections are saved by index.
- Sky rendering is drawn with the vanilla tesselator; shader packs that replace the sky may ignore it.

## Development

```
./gradlew runGameTestServer     # headless tests, needs a clean run/world between runs
./gradlew runClient -PquickPlay=<world folder in run/saves>
```
