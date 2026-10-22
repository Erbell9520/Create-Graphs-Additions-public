# Create: Stats & Power

A NeoForge addon for Create on Minecraft 1.21.1. It started as a few stat panels and kind of got out of hand: there's a whole FE power grid now, displays for everything, a security system, vehicles that drive themselves, and a coffee machine.

> **Note:** this mod was built with AI used as a development tool, and most in-source comments were written by it. Feel free to ignore this, it has no effect on how the mod works.

## What's in it

**Power**
Surface cable, cable trays, bus ducts, indoor cable, copper wire with connectors, bores through walls. Dynamos, biofuel burners, solar panels, capacitor banks, the Starter Capacitor. The Power Distribution Unit sheds low priority loads when supply runs short, the Battery Controller manages a reserve between banks and boards, and the Power Meter, Power Limiter and Main Breaker sit inline where you want them. The Universal Power Coupler trades FE with other mods through their own cables and connectors: Create: New Age, Create Crafts & Additions, Mekanism, Immersive Engineering, Ender IO and Powah.

**Displays**
Stats Display Mk III wall panels and consoles (live graphs, alerts, a compose editor, special pages for stress, power, storage, trains, diesel, Refined Storage, flight and seismic surveys), holographic projectors, e-ink panels, data dials, the Inventory Display, dispatch boards and a railway map.

**Home & security**
Lamps, dimmers and switches, a Smart Home Panel with a floor plan of your house, powered doors, garage doors, blast shutters, sensors, lockers, the Security Relay, the Mob Repeller and the Storm Coil. Kitchen stuff too: oven, toaster, air fryer, coffee machine, water cooler, bread slicer.

**Vehicles**
The VCC Mk IV for ground vehicles and the VCC Mk IV Aerial for airships (Sable / Create Aeronautics), trailers, vehicle lights, driver and aviator helmets, the Surveyor's Staff for drawing routes vehicles follow on their own, the Cine Camera for filming them.

**Machines and tools**
Induction heater, range and smelter, arc furnace, bulk washer, vacuum machines, Seismic Scanner, Spark Crown chunk loader, Power Track for electric trains, clutches and gearshifts, topology scanners, the Battle Bridge for ships with guns.

There's an in-game Student's Manual and Ponder scenes for a lot of it.

## Versions

- Minecraft 1.21.1
- NeoForge 21.1.219
- Create 6.0.9 (up to 6.1)

## Optional mods

Picked up when installed, nothing breaks without them: Create Aeronautics / Sable, Create Crafts & Additions, Create: New Age, Create Big Cannons, Create Propulsion, Offroad, Refined Storage 2, JEI, GeckoLib, Redstone Link Tablet, Electro Energetics.

## Building

JDK 21.

```powershell
.\gradlew.bat build
```

The jar ends up in `build/libs`.

## License

MIT with attribution. See LICENSE.

## Credits

- CobanF8
- Built on Create by the Create team
- NeoForge modding framework
