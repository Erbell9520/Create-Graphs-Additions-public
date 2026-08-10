# Create Stats & Numbers

Create Stats & Numbers is a NeoForge 1.21.1 addon for Create on Minecraft 1.21.1. It adds factory displays, an FE power grid, operator tools, and vehicle systems.

> **Note:** this mod was built with AI used as a development tool, and most in-source comments and explanations (see `NOTES.md`) were written by it. Feel free to ignore this, it has no effect on how the mod works.

## Features

**Power & wiring**
Surface cable, cable tray, bus duct, and wire terminals for point-to-point runs; capacitor banks for buffering; three grades of FE dynamo and FE motor; two grades of FE fluid pump; and the Power Distribution Unit, a priority-tiered load shedding panel with an Active Auto mode that sheds low-priority devices automatically when supply runs short.

**Displays & monitoring**
Stats display panels, holographic projectors, stats recorders, alarms, mini displays, data dials, production detectors, and a battle screen / damage observer suite for section-based damage monitoring.

**Lighting & switches**
Stage lights and status tower lights, a set of static fixtures (bulkhead, high bay, strip, dome, sconce, panel), and a matching family of wall switches (rocker, toggle, double, isolator, push button).

**Logistics & control**
Factory Logic Controller, Dispatch Board, Load Balancer, Calculator, Freight Manifest Clipboard, Flow Meter, Overflow Gate.

**Drivetrain control**
Safety Clutch, Auto Transmission Clutch, Control Clutch, Smart Gearshift, Kinetic Flywheel.

**Vehicle & flight control**
Flight Control Computer, RPM Governor, VCC Mk II cockpit console, and a Path Scanner / Pathfinder Computer pair for route guidance.

**Heating**
Induction Heater and Induction Range, FE-powered cooking appliances that plug into the same power grid.

**Antennas**
Longer-range and special-dimension signal links for wireless devices.

## Main Gameplay Loops

### Power Management
Wire generators and machines into a Power Distribution Unit, set a priority tier per device, then let Active Auto shed the lowest-priority loads first when generation can't cover everything.

### Factory Displays
Place a display target, feed it with a Display Link, then open the screen to choose what you want to see.

### Battle Monitoring
Place Damage Observers on important ship or machine sections, link them into a Battle Screen Display, then use the screen as the damage overview.

### Vehicle Routing
Let a Path Scanner read the loaded terrain ahead, send that scan to a Pathfinder Computer, then forward route guidance from Pathfinder to the Flight Control Computer.

### Flight Control
Place RPM Governors on the shafts you want controlled, bind them in the Flight Control Computer, then enable the mode you want to test.

## Target Versions

- Minecraft 1.21.1
- NeoForge 21.1.219
- Create 6.0.9-215

## Building

Requires JDK 21.

```powershell
.\gradlew.bat build
```

The built jar is written to build/libs.

## Optional Compatibility

Some integration paths are enabled only when their companion mods are present, including Create Addition and Simulated or Aeronautics companion support.

## License

MIT with attribution. See LICENSE.

## Credits

- CobanF8
- Built on Create by the Create team
- NeoForge modding framework
