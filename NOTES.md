# Design notes

Numbered reference notes pulled out of the source. Each `[N]` marker in the code
corresponds to an entry below, numbered per-file.


## main/java/com/createstats/antenna/AntennaLinkHelper.java


**[1]** Mirror the server-authority same-dimension reach test exactly so the placement preview never disagrees with the real link decision. With no cached tier yet we assume the most permissive (TRT) reach for the preview.


## main/java/com/createstats/antenna/AntennaRange.java


**[1]** Nether coordinate compression: 1 Nether block spans 8 Overworld blocks. The TRT antenna's reach represents a fixed physical (Overworld-equivalent) distance, so inside the Nether its local-block multiplier is divided by 8 (e.g. 32x Overworld -> 4x Nether) to keep the same real-world coverage. Scoped to TRT only; the shorter tiers keep their raw local multiplier so they never drop below their own base range.


## main/java/com/createstats/antenna/AntennaTier.java


**[1]** Config-tunable balance lever (like inductionHeaterStressImpact). The enum stays the identity/ serialization source; only the returned multiplier is config-driven, with the enum value as the fallback while the config is not yet loaded.


## main/java/com/createstats/arm/InductionHeaterArmPointType.java


**[1]** Lets a Create Mechanical Arm treat the Induction Heater as an input/output target.

The arm only targets blocks that register an `ArmInteractionPointType`; exposing an item capability is not enough on its own (that path is what makes funnels and chutes work). The base `ArmInteractionPoint` already routes insert/extract through the block's item handler capability, which for this block is the plate handler (raw food in / cooked food out, with the coil slot hidden), so a plain point over that capability is all that is needed here.


## main/java/com/createstats/battle/BattleLightSnapshot.java


**[1]** Per-observer snapshot of the Stage Lights inside a Damage Observer's watched volume. Rides inside `BattleObserverSnapshot` over the existing Display Link transport so the Battle Screen can show and (later) control the lights, mirroring how `BattleWeaponSnapshot` surfaces weapons.


## main/java/com/createstats/battle/DamageObserverVoxelMap.java


**[1]** Snapshot of a Damage Observer's scan volume in a form the client can render as voxels. Per-position cells encode one of four states:
- baseline=1 paletteIdx=0  -> MISSING  (was baseline, now empty)
- baseline=1 paletteIdx>0  -> PRESENT  (render with paletteState as-is)
- baseline=0 paletteIdx>0  -> NEW      (render with paletteState + blue tint)
- baseline=0 paletteIdx=0  -> EMPTY    (skip)
Built server-side on demand (see `DamageObserverBE#buildVoxelMap`) and shipped to the client via `ObserverVoxelMapPayloadPacket`.


**[2]** Optional assembled-cannon model in contraption-LOCAL coordinates. Present only for weapons observers watching an assembled cannon, whose barrel lives on a moving contraption entity rather than in the world; lets the Battle Bridge holo show the real cannon. Null/empty when absent.


## main/java/com/createstats/block/CableBlock.java


**[1]** Surface run cable: cable that lies on the faces of blocks and turns corners, as opposed to the Copper Wire's catenary, which hangs through open air between two connectors. one block holds several faces because at an inside corner, where a wall meets a ceiling, the cable has to be on both the wall face and the ceiling face of the **same** block position. only allowing one face per block would need two blocks in one space (impossible), and stepping the run diagonally instead leaves a visible gap right at the corner. so this carries a boolean per face, same as vanilla Glow Lichen. connections aren't blockstate properties because they can't be - 6 faces x 4 in-plane directions is 24 booleans, 16 million blockstates, and Minecraft allocates an object per state. so the state only carries _which faces are occupied_ (64 states), and which way the cable actually runs on each face gets worked out from the neighbours at render time. that's what the block entity's for - no ticker, does nothing but exist so the renderer and grid have somewhere to hang.


**[2]** A cable of the same grade merges into this one by taking a face it does not already carry.

One cable per block, so there is nothing else to merge: a position that already carries this face is full, and a second circuit down the same corridor is a second cable in the next position, in its own colour, where you can see it.


**[3]** Drops any face whose support has gone.

Per face rather than all or nothing: mining the wall out from under a corner should take the wall's cable and leave the ceiling's alone, because that is what the player just did.


**[4]** Right-click with a dye to recolour the whole connected run. Colour is the circuit, so this is not decoration: it is how a run is split off from the one it is touching, or joined to it.

The **whole run**, not one block, and that matters more than it sounds. A run is laid in one gesture and read as one object, so recolouring it a block at a time would be tedious in exact proportion to how long the run is, and half way through you would have two circuits where you meant to have one. Painting is therefore all or nothing, from wherever you clicked, out to every block that is currently on that same circuit.


**[5]** Gathered first, painted second. Repainting as the flood walks would change the colour it is following, so the run would repaint the first block and then decide the second was a different circuit and stop: a dye click that recoloured exactly one block.


**[6]** Every segment ticks, but only the first each tick does any work.

`FeWireNetwork.solveOnce` early-outs for the rest, so the per-segment cost is a couple of field reads. That is what keeps a few hundred blocks of cable affordable, and it is the same arrangement the Power Connector already uses.


**[7]** Adapts a level to what `com.createstats.power.CableLinks` needs.

One adapter shared by the renderer and the FE grid, because they must never disagree about which pieces of cable are joined. Two adapters would be two chances to answer differently, and the symptom of that is wiring that looks connected and carries nothing.


**[8]** The circuit colour here, straight off the blockstate.

The blockstate rather than the block entity, deliberately. Colour decides what is joined to what, so it is read during chunk load and during rendering, and a blockstate is always there while a block entity briefly is not. Reading it from a block entity would mean a half loaded chunk could answer "no colour" and quietly split a run.


## main/java/com/createstats/block/CapacitorBankBlock.java


**[1]** FE storage with the charge level written on the outside.

`LEVEL` is the number of lit LED segments, 0 to 8, driven from the block's own charge by `CapacitorBankBE.tick()`. Because a pool of touching banks fills from the bottom up, a stack of these reads as one big gauge: the lit blocks are the charge and the dark ones are the headroom, legible across a base without opening anything.

The block also emits light in proportion, so a charged bank glows and a flat one does not.


**[2]** Where a block sits in its merged bank's vertical run. Selects the connected-texture variant, so a merged column drops its internal horizontal seams and the LED well reads as one continuous bar running the full height of the structure.


## main/java/com/createstats/block/ConnectorAnchor.java


**[1]** Where a Power Connector sits within its mounting face. needed at all because a connector occupies the whole block next to whatever it's attached to, and its model draws in the middle of the shared face. that's correct against a full cube and wrong against almost everything else this mod ships - a Stats Display Panel is a three pixel slab hugging one side of its block space, so clicking a connector onto the panel's edge put the mounting plate in the centre of a face the panel doesn't occupy: it hung in mid air, bolted to nothing. so the connector can shift toward one of four directions in its mounting plane, far enough to land on a wall panel's slab, and `forSupport` picks the shift from the support block's real collision shape. `CENTER` is the ordinary case, what a full cube always gets. values are world directions rather than face-local ones on purpose - keeps the generated models, the `VoxelShape` and the wire attachment point all reasoning in the same frame, the kind of thing that silently drifts apart otherwise.


## main/java/com/createstats/block/FactoryLogicControllerBlock.java


**[1]** CRT terminal silhouette: full-width base, keyboard shelf, monitor+bezel, CRT bulge, vent crown Geometry matches model: base [0-16], shelf [0-16 x 0-6z], monitor [0-16 x 2-12z], tube bulge [2-14 x 12-16z], crown [0-16 x 5-12z]


## main/java/com/createstats/block/FeMotorBlock.java


**[1]** The FE Motor: the inverse of the `KineticDynamoBlock`. Forge Energy in through the built-in connector, rotation out of the shaft on `FACING`. It is a kinetic _source_, sourcing both a speed and a stress capacity onto the network it drives, paid for in FE. See `FeMotorBE`. faces: `FACING` is the output shaft (drawn by the block entity). power's wired to the two copper studs on the terminal box, exactly like the FE Fluid Pump - the block is its own Power Connector.


**[2]** The housing is roughly a third of a block cube, so without these it collided and selected as a full block around a model nowhere near that size. One literal shape per facing rather than a runtime rotation, matching PowerConnectorBlock: unlike the Kinetic Dynamo, whose shaft is always DOWN, this block turns through all six FACING values, so a single shape would be wrong on five of them. Generated by `python gen_fe_motor_model.py --java` from the same numbers the model is built from; rerun it rather than editing these by hand if the geometry changes.


**[3]** Hugs the housing on whichever way it is turned. The output shaft itself is not in here: it is drawn by the block entity, and Create's shafts are never part of a source block's shape.


## main/java/com/createstats/block/FlowMeterBlock.java


**[1]** Flow Meter: a drop-in fluid pipe that transports fluid exactly like a Create copper pipe (it extends `AxisPipeBlock`) while its `FlowMeterBE` samples the throughput for the Stats Display. The default `AxisPipeBlock` interactions that would convert the block into a plain/encased pipe are suppressed so the meter is never silently lost.

It is waterloggable so a flowing vanilla liquid fills around it instead of washing it away (like redstone), and it auto-orients on placement to line up with an adjacent pipe or fluid handler instead of the clicked face.


## main/java/com/createstats/block/FluidPumpBlock.java


**[1]** The FE Fluid Pump: a Create pipe segment that pushes fluid down the run it sits in, driven by Forge Energy and a power dial rather than by a rotating shaft. behaves like a Create pump - a Mechanical Pump applies a pressure equal to its rotation speed, and the pipe moves `max(1, pressure / 2)` mB/t. this block does exactly the same thing, except the pressure comes from its `com.createstats.block.FluidPumpValueBoxTransform` (zero to the grade's ceiling) and it burns FE to hold it. see `FluidPumpBE`. faces: `FACING` is the output - fluid pushes out the front and pulls in through the back, along that one axis, so the block drops straight into a pipe run. power's wired to the built-in connector on its side, which accepts FE from any face a wire or cable lands on.


**[2]** Line the pump up with the run it is being spliced into: prefer an axis whose two ends both reach a pipe or a fluid handler, so the common case of dropping it between a tank and a pipe just works without the player rotating it.


## main/java/com/createstats/block/FluidPumpValueBoxTransform.java


**[1]** Places the FE Fluid Pump's power dial on the top face of the housing, where it clears the pipe stubs on the front and back and reads the same from any facing. The dial is the "click, hold and slide" power control: Create's own value box, driven by a `ScrollValueBehaviour`.


**[2]** Dead centre on top, above the cooling fan and just under the block's top face so Create's value-box ray reaches it (a dial sunk more than ~3px sits below the full-cube hitbox and cannot be clicked). Centre keeps it the same spot at every horizontal facing.


## main/java/com/createstats/block/FlywheelGearValueBoxTransform.java


**[1]** Places a value box on the Kinetic Flywheel's rear face (the side opposite the shaft output), which is the only flat surface free of the spinning disc. Two boxes are stacked on that one face: the upper one configures the charge gear, the lower one the discharge gear.


## main/java/com/createstats/block/HolographicProjectorBlock.java


**[1]** The projector is a sensor-type block with no defined output face; it provides only weak (indirect) signal via getSignal().  Returning a non-zero strong signal here would incorrectly strongly-power every adjacent solid block.


## main/java/com/createstats/block/InductionHeaterBlock.java


**[1]** A kinetic replacement for the Blaze Burner: rotation in, heat out.

Reuses `BlazeBurnerBlock.HEAT_LEVEL` as its blockstate property on purpose. Create resolves heat generically through that property in two places, so both integrations come for free, with no mixins:

- Boilers: we register Create's own `BoilerHeater.BLAZE_BURNER` lambda for this block, which reads `HEAT_LEVEL` straight off the state.

- Basins: `BasinBlockEntity.getHeatLevelOf` checks `state.hasProperty(BlazeBurnerBlock.HEAT_LEVEL)` before anything else.

Heat tiers map to RPM bands (see `InductionHeaterBE`): SMOULDERING at 64, KINDLED at 128, SEETHING at 256 with an Induction Coil installed. FADING is unused but must exist in the state definition since the enum owns it.


**[2]** Sneak + empty hand pulls the coil (original behavior); plain empty hand clears the plate - cooked or raw mid-cook, the player gets the whole pile back. Pulling early simply loses the progress, like lifting a pan off the stove.


**[3]** The cook plate is exposed metal, not a sealed hotplate - Kindled and Seething both hurt whatever walks across it, same hazard shape as Magma Block. Seething burns hot enough to also set the entity alight, matching the white-hot texture. Warm/Smouldering is deliberately left safe since that tier is meant to read as ambient warmth, not an active heating element.


## main/java/com/createstats/block/InductionRangeBlock.java


**[1]** The Induction Range: an electric hob with a dial, and the FE counterpart to the Induction Heater.

the state set, and why it's shaped this way:

- `HEAT_LEVEL` is Create's own property, holding the heat the range is **actually** managing. Reusing it rather than inventing one is what gives basins and boilers working integration for free, with no mixins: `BasinBlockEntity.getHeatLevelOf` tests for this exact property, and `BoilerHeater.BLAZE_BURNER` reads it straight off the state.

- `FACING` puts the control panel on the side the player was standing when they placed it, because a hob with its dial against the wall is a hob you cannot use.

- `STARVED` says the grid could not hold the dial setting. It is a separate flag rather than a lower heat level because "running at medium" and "you asked for high and this is all there is" are different facts, and only one of them means you should go and look at your generators.

setting itself is deliberately _not_ a blockstate on purpose. it changes nothing about how the block's drawn that the achieved heat doesn't already say, and putting it in the state would double the count just to carry info the player already typed in themselves.


**[2]** Bare hand turns the dial; sneak clears the hob.

That way round because turning the heat up is the thing you do constantly and taking food off is the thing you do once a batch. The rarer action gets the modifier.


**[3]** A hot hob burns, on the same terms as the Induction Heater's plate and vanilla magma.

Low is left safe on purpose: that setting is meant to read as keeping something warm, and a block that hurts you at its gentlest setting teaches players to never place it indoors.


## main/java/com/createstats/block/KineticDynamoBlock.java


**[1]** The Kinetic Dynamo: rotation in through the bottom shaft, Forge Energy out of every other face. The mod's built-in generator so FE Dependent Mode is playable without any external FE mod installed. See `KineticDynamoBE` for the generation model.


**[2]** Hitboxes, one per grade, matching that grade's model. kept next to the model instead of shared - the shapes really are different (8-wide can vs 14-wide open frame), sharing one hitbox just makes mining feel wrong for no obvious reason


**[3]** Sparks off the terminals, at a rate that follows the shaft.

The old version was a flat one-in-three coin flip whenever the shaft moved at all, so a dynamo ticking over at 8 RPM sparked exactly as hard as one screaming at 256. The whole value of an idle animation is that it tells you the state of the machine without opening anything, and a constant one tells you only that the machine exists.

Above three quarters of top speed it also smokes, which is the point where a player should feel they are running the thing hard rather than merely running it.


## main/java/com/createstats/block/KineticFlywheelBlock.java


**[1]** Kinetic Flywheel (Inertia Bank), a heavy spinning mass spliced onto a Create drivetrain that banks rotational momentum and sustains the network through power gaps.

**Topology:** a single end-of-line shaft connection on `FACING`. You branch a shaft into it; the big wheel spools up. It is a `com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity` whose role flips with its simulation mode (see `KineticFlywheelBE`):

- **CHARGING / HOLDING**, generates nothing (not a source) but presents a real stress impact, so charging costs SU. The external source on the net powers it.

- **DISCHARGING**, becomes the sole source (only when the net would otherwise stall), generating its stored RPM and coasting down on friction.

Because it only ever generates when no external source is present, there is no two-source network contention.

**VISUAL_STATE** drives the cosmetic blockstate (idle / charging / discharging) used by the model variants. The spinning wheel itself is renderer-driven from the BE's stored RPM so it visibly coasts down during discharge.


## main/java/com/createstats/block/LampBlock.java


**[1]** A mountable lamp. One class serves the whole set; the shape and light level come from the entry that registers it. mounting: `FACING` is the direction the lamp points, away from whatever it's stuck to, so a lamp on a ceiling points DOWN and one on a floor points UP. model's authored once pointing up and the blockstate rotates it, why there's two models per lamp instead of twelve. colour: dye's a blockstate property read by a colour handler that tints the diffuser. sixteen dyes cost one model instead of sixteen, and only the diffuser's tinted - a lamp whose steel bracket turned magenta would read as a bug, not a choice. power: lit by redstone, always. FE's an **additional** requirement, only while FE Dependent Mode is on, matching Stage Light and Status Tower Light. making the lamps FE-only would've meant they did nothing at all for most players, since that config defaults to off.


**[2]** The lamp points out of the face that was clicked, so clicking a ceiling hangs it downward and clicking a wall makes it stick out horizontally, which is what a player expects.


## main/java/com/createstats/block/LampShapes.java


**[1]** Shapes for the lamp set, and the rotation that carries one authored shape onto all six faces.

Each lamp's hitbox is authored once in the canonical upward-pointing orientation, exactly like its model, then rotated to match `FACING`. Authoring six boxes per lamp by hand is how a hitbox ends up quietly disagreeing with the model on one face, which is invisible until a player cannot click the thing they can plainly see.

Rotations are cached per shape and direction: `getShape` is called constantly, and the rotation is pure geometry that never changes for a given input.


**[2]** Rotates a shape from pointing UP to pointing `facing`, box by box.

The mapping mirrors the blockstate rotations in `gen_lamps.py`: UP is the identity, DOWN is a half turn about X, and the four horizontals are a quarter turn about X followed by a turn about Y. Keeping the two in step is the whole point of writing this out explicitly.


## main/java/com/createstats/block/LoadBalancerBlock.java


**[1]** Input and front/back shafts run along the horizontal facing axis (Z for N/S, X for E/W). Output shafts (left/right) are perpendicular; the SplitShaft mechanism handles the conversion.


## main/java/com/createstats/block/OverflowGateBlock.java


**[1]** Delegate to the parent so BrassTunnelBlock can clean up neighbouring tunnel connections before the block entity is removed.  Calling IBE.onRemove directly here skips that step and leaves stale connection state in adjacent tunnels.


## main/java/com/createstats/block/PowerConnectorBlock.java


**[1]** A small face-mounted FE node (functional parity with Create Crafts and Additions' connectors). Attach it to a generator, battery, or FE consumer, then span Copper Wire between connectors to move energy point to point. `FACING` points away from the supporting block.


**[2]** Outer bounds of the model, which is 6 px across the face and 12 px proud of it. Kept in step with gen_power_connector.py: the generator prints these figures when it runs.


**[3]** Mount over whatever the support block actually is, not over the middle of a face it may only partly occupy. Almost every screen and panel in this mod is a thin slab.


## main/java/com/createstats/block/PowerDistributionUnitBlock.java


**[1]** The Power Distribution Unit. Faces the player when placed: the instrument screen is on the front, FE goes in through either side boss, and each of the eight studs on the bus bar takes a Copper Wire out to a machine's Power Connector, which is the leg the panel then serves by priority.


**[2]** A conductor in hand means wiring, not reading.

`useWithoutItem` runs even with something in your hand, because the default `useItemOn` answers TRY_WITH_EMPTY_HAND and vanilla falls through to it. So the screen opened on every right click and there was no way to reach an output stud at all: the panel ate the click before the item ever saw it. Holding a conductor is an unambiguous statement about what you are trying to do, so the panel steps aside and lets the item act.

Copper Wire is what wires the eight studs now. Cable is here too, unchanged, so that a cable click on the panel places a cable exactly as it would against any other block rather than opening a screen the player did not ask for.


**[3]** Wire is only special on the bus bar. A wire click on the front is a click on the front, so the screen still opens with a coil in hand: the studs are the only terminals, and nowhere else on the block should quietly swallow a right click.


**[4]** A broken panel returns the wires strung to its studs, and unlinks the connectors at the far end.

Only on a real block change, never on unload: `setRemoved` on the block entity fires both, and using that would quietly delete a base's wiring every time its chunk left memory.


## main/java/com/createstats/block/ProductionDetectorBlock.java


**[1]** In creative mode, silently remove the lower half when the player breaks the upper half so only one item would have been dropped (creative suppresses drops anyway, but we must remove the orphaned block).


## main/java/com/createstats/block/ProjectedLightBlock.java


**[1]** Internal, never-craftable invisible light carrier.

Stage Lights place one of these at the spot their beam lands so the illumination is _real_ vanilla block light: it stops mob spawning, lights like a torch or lantern, and is occluded correctly by walls under any shaderpack (it is just block light, the one input Sodium and Iris always honour).

It has no model (rendered invisible), no collision, no drops, and is freely replaceable, so it can never block, break, or overwrite a player build: the manager only ever places it into air and removes exactly the positions it owns.


## main/java/com/createstats/block/StageLightBlock.java


**[1]** Stage Light, a mount-on-any-face moving-head spotlight (concert / stage light).

FACING is the mounted face outward normal (set from the clicked face), so it stands on a floor pointing up, hangs from a ceiling pointing down, or sits on a wall pointing out. The head pans/tilts within a cone; the two aim angles are set Create-style with hover-and-scroll value boxes on the head (see StageLightBE / StageLightValueBoxTransform) and can be overridden by a redstone differential gimbal on the four sides. LIT drives the fixture's own glow.


## main/java/com/createstats/block/StageLightMk2Block.java


**[1]** Stage Light MK II, the upgraded brass/gold moving-head fixture. Identical handling to the base `StageLightBlock` (GUI, colour filter, redstone gimbal, battle bridge), with a Beacon upgrade: right-click with a Beacon to slot it (doubles the reach again); a wrench pops it back out.


## main/java/com/createstats/block/StatsDisplayPanelBlock.java


**[1]** Opens the configuration screen for this panel on the client. Subclasses (for example the Mk II panel) override this to open their own studio screen while reusing all the shared block behaviour.


## main/java/com/createstats/block/StatsRecorderBlock.java


**[1]** The recorder model is inset 2px on all horizontal sides (body 2..14), only 12px tall, with a small 0.6px front readout protrusion. A 1px-margin box safely bounds the device for every facing, replacing the default full-cube outline/collision.


## main/java/com/createstats/block/SwitchBlock.java


**[1]** A wall-mountable switch. One class serves the whole set; behaviour comes from the entry that registers it. they all latch: every plate in the set stays where you put it, like a real light switch - `useWithoutItem` flips the gang and nothing schedules it back. that includes the push button, modelled on the latching push-button light switch rather than a vanilla stone button. if a momentary pulse's ever wanted it has to be built here deliberately, with a scheduled tick to release the gang; until then, don't describe any of these as momentary in tooltips or guides. two gangs, two circuits: a double gang switch is two independent switches sharing a plate, so it has to drive two circuits or it's only a wide rocker. each gang powers its own side of the block - left gang feeds the side clockwise of the plate, right gang the side anticlockwise. both still feed the block behind, so a run of cable or dust behind the wall is driven by either. redstone shape: modelled on the vanilla lever - a weak signal to everything around it, a strong signal into the block it's mounted on, so a switch on a wall powers what's on the other side of that wall. anything else would make switches useless right where people put them.


**[2]** Which half of a two gang plate was clicked.

Compares the hit against the plate's own left-right axis rather than a world axis, so the halves stay where they look no matter which way the switch is facing. Using a world axis would make the gangs swap over when you walked round to the other side of a wall.


**[3]** The contacts moved, so the grid on both sides is no longer what it was.

This is the moment the topology actually changes. Without forcing a rebuild here the run would keep conducting through a switch that is now open, until something unrelated happened to invalidate the network.


## main/java/com/createstats/blockentity/AutoTransmissionClutchBE.java


**[1]** Output face. Clamp the modifier LIVE against the current source speed so the conveyed output can never exceed the RPM ceiling, even if currentModifier is momentarily stale (e.g. a Kinetic Flywheel begins back-driving this output before the governor re-runs). This guarantees Create's propagator never sees an overspeed here, so it never destroys us.


**[2]** The face the block is currently driven from. A split shaft has no hardware-fixed input; the input is wherever the source is. Preferring the live `getSourceFacing()` keeps the input/output orientation correct when the source side changes (e.g. the original input is removed and a backup source drives the old output side), preventing Create's propagator from computing a conflicting speed and destroying the block.


**[3]** Genuine network-stop handling (debounced). onSpeedChanged no longer resets on a zero, because a neighbouring split-shaft's re-propagation produces a 1-tick zero that would otherwise wipe the governor. By the time we tick, such a transient has already restored our source, so a still-absent source here means the network really stopped: settle to neutral and idle.


**[4]** Track the input direction from Create's source detection. Update whenever a source is present (not gated by speed) so a flip to the opposite side is detected even at 0 RPM.


**[5]** Source side flipped: re-orient and let the governor re-evaluate for the new input/output. The ratio itself is direction-agnostic, so getRotationSpeedModifier (now live-source aware) already applies it to the correct face.


**[6]** Single-step downregulation: jump directly to target. Downregulation's many small handleAdded cycles are what accumulate flickerScore and create speed-mismatch windows. One big jump is safer because Create only checks final speed.


**[7]** Schedules a re-propagation of the kinetic network after the governor changes its output ratio. The actual rebuild is deferred out of network callbacks (see `performModifierRebuild()`), then performed with Create's own detach/attach sequence so the network stays consistent.


**[8]** Canonical Create re-propagation for a split shaft whose ratio changed. detachKinetics() -> RotationPropagator.handleRemoved cleanly zeroes the output subtree (gauges included, alternate sources rediscovered); attachKinetics() re-propagates with the new modifier. This mirrors Create's own SpeedControllerBlockEntity.updateTargetRotation() and the sibling SmartGearshiftBE, and avoids the same-network speed-mismatch windows that made RotationPropagator call world.destroyBlock(pos, true) (the self-break + item duplication glitch).


**[9]** Speed hit zero. This can be a genuine network stop OR a 1-tick transient because a neighbouring split-shaft is re-propagating (it momentarily removes our source then restores it in the same tick). Do NOT reset the governor here: defer to the tick loop, which only resets once the source is gone for real (checked after propagation settles). Without this, chained governors knocked each other back to neutral on every step.


## main/java/com/createstats/blockentity/BattleScreenDisplayBE.java


**[1]** forward-simulation ballistic intercept solver CBC integrates each shell with semi-implicit Euler under gravity + optional drag (see AbstractCannonProjectile#tick). We reproduce that exact integration to solve the launch direction and flight time that put a shell through the target's predicted future position, instead of the old crude "raise aim by 0.5*g*t^2" hack which ignored the real launch elevation and drag.


**[2]** ADVANCED PREDICT multiplies the response slider ceiling by this (autocannon 5x -> 10x, big cannon 1x -> 2x). denominator stays the base span on purpose so a multiplier above the normal cap pushes governor gain / RPM ceiling proportionally higher (up to 2x) instead of just rescaling the same authority.


**[3]** Perf: getBattleFeedSnapshots() is called every tick by StatsDisplayPanelBE source resolution, the BattleCompactTargetHandler, and the BattleScreenDisplaySource. The underlying linkedObservers map only mutates on link/unlink events and on the 20-tick refresh sweep, so we cache an immutable snapshot list and invalidate it any time linkedObservers content changes (via syncLinkedObservers / NBT load).


**[4]** Storage / transport clamp: bound to the widest possible range (advanced ceiling) so a value set while ADVANCED was on (e.g. 10x) is NOT collapsed to 5x on save/load or snapshot deserialization. The weapon-kind + advanced aware clamp re-narrows the value at read time.


**[5]** The advanced boost raises only the RPM saturation ceiling (maxRpm), NOT the proportional gain slope: pushing near-target gain past the normal cap drives a limit-cycle oscillation (the cannon overshoots and reverses every tick). Gain / settling dynamics therefore stay identical to the non-advanced maximum, while large angular errors are allowed to slew faster against the higher ceiling -> quicker target acquisition without losing stability.


**[6]** Idempotency guard: the firing solution is computed twice per tick (once to drive the mount, once to build the UI snapshot). Both passes share the velocity/accel tracking maps, so without this cache they desync and the lead jitters between passes. Reuse the first pass's result for the same source + same tick + same target so both passes are identical.


**[7]** Robust target velocity & acceleration via WINDOWED POSITION DIFFERENCING. The target's world position (aim point) is reliable every tick, unlike the physics-engine reads exposed through our bridge: the Sable COM velocity intermittently drops to zero (velLen oscillated real<->0 in testing) and the gravity vector reads zero entirely. So we estimate motion purely from how the tracked point moves over a short multi-tick window:
- constant-velocity target -> clean, steady velocity (fixes "can't hit a steady target");
- ballistic arc            -> the windowed velocity changes over time and its rate of
change yields the deceleration, so the parabola is captured without needing the (unavailable) engine gravity vector.


**[8]** PREDICT mode gate: Basic PREDICT leads with constant velocity only (linear intercept); ADVANCED PREDICT additionally applies the position-derived acceleration term so a thrown / falling body is led along its parabola. Both modes share the robust windowed velocity above.


**[9]** Weapon-aware muzzle velocity: autocannons read the material's per-barrel speed, while big cannons read the real loaded propellant charge power (= chargesUsed = blocks/tick) so ADVANCED PREDICT leads heavy shells along the correct flight time instead of getting no ballistics at all.


**[10]** FORWARD-SIMULATION BALLISTIC INTERCEPT SOLVER. Reproduces CBC's exact per-tick shell integration (semi-implicit Euler under gravity + optional drag) to find the launch DIRECTION and flight time that put a shell through the target's predicted future position. The launch direction encodes BOTH the horizontal/vertical lead AND the upward ballistic arc (drop compensation), so the turret is pointed exactly where it must fire. Basic PREDICT solves a constant-velocity intercept; ADVANCED PREDICT additionally feeds the measured target acceleration into the future-position estimate. Even a stationary far target is solved (zero lead, full arc), so big cannons now elevate correctly for range.


**[11]** Closest-approach result of a forward shell simulation: the simulated position nearest the target, the (fractional) tick at which it occurred (= flight time), and that miss distance. The tick is a double so the segment-interpolated sub-tick closest approach is preserved (a shell can move several blocks per tick, so the true nearest point usually falls between integer ticks).


**[12]** Resolves the ballistic parameters of the weapon's loaded munition. CBC munitions are data-driven and keyed by the spawned projectile entity type, which is not reliably reachable before firing, so we use CBC's documented `BallisticPropertiesComponent` defaults (gravity -0.05, no drag) which match the stock big-cannon and autocannon shells. The solver itself simulates the EXACT CBC integration, so the trajectory arc is physically correct for these standard munitions.


**[13]** Coupled fixed-point ballistic intercept solver.

Outer loop converges the flight time T: the target's predicted future position depends on T, and the actual flight time produced by the shell simulation feeds back into T. Inner loop solves the launch direction for a fixed future target position by the classic aim-point iteration: fire straight at the predicted point, measure how far the simulated shell misses (it falls short under gravity/drag), raise the aim point by that miss, and re-simulate until it converges. The shell is integrated with CBC's exact semi-implicit Euler scheme, so the solved direction includes the real ballistic arc.


**[14]** Forward-simulates a shell from `origin` at `initialVelocity` using CBC's exact per-tick integration and returns its closest approach to `target`. CBC each tick computes `a = -drag(|v|)*v̂ + (0, gravity, 0)` then `x += v + 0.5*a` (position uses the old velocity plus half the new acceleration) and `v += a`.


**[15]** Sub-tick closest approach: the shell moves several blocks per tick, so test the closest point on this tick's travel SEGMENT [prevPos -> pos] instead of only the discrete endpoint. This removes the temporal quantization that otherwise left a systematic short miss.


**[16]** Resolve the physics sub-level Object backing a target candidate, so ADVANCED PREDICT can pull its rigid-body state. Entities riding a contraption resolve through their containing sub-level; pure sub-level targets resolve by id.


**[17]** Resolves a direct velocity (blocks/tick) for an auto-aim target when a trustworthy source exists. Sable's instantaneous `velocityAt` reading proved far too noisy for sub-levels (it swings wildly frame-to-frame and blows the intercept solver up), so ships/contraptions are left to the caller's smoothed finite-difference tracking by returning `null` here. Only loose entities with a real, non-zero server motion vector report directly. Returns `null` when no trustworthy direct source is available.


## main/java/com/createstats/blockentity/CableBE.java


**[1]** One segment of a surface cable run, and a full member of the FE grid. one block, one circuit. used to carry up to eight, on separate channels sharing the position, which is why almost every method here used to take a channel index and why the block had a second, invisible identity beside its blockstate. capacity now comes from the grade you laid (`CableTier`) and separate circuits are just separate cables, told apart by their dye. everything that made this class hard to reason about came from the bundle, so the bundle's what went. a cable's a node, not an edge, because modelling it as a pure connection between machines would've meant teaching the grid solver about edges, which it's never had - it pools every node's buffer and hands the total out to sinks. giving each cable segment a small buffer instead means the existing, tested arithmetic works on cable with no changes at all. no transport delay either, since the solve pools the whole network in one tick - the buffer's bookkeeping, not a bucket brigade. what it feeds: machines are reached by `com.createstats.power.FeAttachment` nodes the flood emits, one per machine touched by the run, so a run powers everything it passes with no connector needed. `attachedStorage()` is deliberately null - a segment that answered with the first machine it found on any side used to power exactly one of two neighbours, chosen by iteration order.


**[2]** Buffer per segment. Tiny on purpose: it is accounting, not storage.

A run is pooled and solved in one tick, so this never has to hold a delivery in transit; all it does is give the solver somewhere to put a remainder. At the old four hundred a modest run was quietly a battery worth several capacitor blocks, and a player could unplug the generator and watch the lights stay on. Twenty five is enough to round with and too little to store.


**[3]** How hard this segment is working, 0 to `LOAD_STEPS`, as the client sees it.

A byte rather than the raw figure, and that is a bandwidth decision rather than a stylistic one. There can be several hundred cable block entities in view, and the renderer only needs enough resolution to speed up a pulse. Sixteen steps is more than the eye resolves in a moving highlight, and it means a segment only sends anything when its load moves by a sixteenth.


**[4]** Staggered by position so a chunk full of cable does not heartbeat in one lump every two seconds. Five hundred segments firing on the same tick is a visible hitch; the same five hundred spread across forty ticks is a dozen packets a tick and nobody notices.


**[5]** Ticks the run.

Every segment ticks, but only the first one each tick does any work: `FeWireNetwork.solveOnce` early-outs for the rest. That keeps the per-segment cost to a couple of field reads, which is what makes a few hundred blocks of cable affordable.


**[6]** Walks the run once, after a rebuild, for the two figures the goggles need.

Only after a rebuild, never per tick. Both numbers are properties of the run's **shape**, so they change when the run is cut, extended or upgraded and at no other time, and the rebuild is exactly the event that says the shape moved.


**[7]** Sends the load byte when it actually moves, or once every two seconds regardless.

Throttled rather than per tick, because there can be hundreds of these in a base and a packet each per tick from every one of them is real bandwidth for a highlight nobody is staring at. The shape figures ride the same signature so a rebuilt run refreshes its goggle text at once.


**[8]** Goggle readout: what this segment is carrying, against what it can, and what the run's own weakest point is.

The bottleneck line is the one worth walking over for. A run is only as good as its thinnest grade, and a single Surface Cable left in the middle of a Bus Duct trunk is invisible from any distance and throttles the entire run to a sixth of what the player thinks they built.


**[9]** Nothing. Cable is pure conduit.

It used to return the first machine it found on any side, which meant a segment lying across a machine while passing a lamp powered exactly one of them, chosen by iteration order. Machines are now reached by `com.createstats.power.FeAttachment` nodes that the flood emits, one per machine, so a run feeds everything it touches instead of the first thing it looked at.


**[10]** How the run on `face` leaves in the direction `towards`.

The rules live in `CableLinks` so they can be tested without a world; this only adapts the level to them. See that class for why the answer is a kind rather than a boolean, and why colour is part of the answer.


**[11]** How far past the block boundary an arm has to run to actually touch the machine it feeds, in block units.

Machine bodies do not start at their block boundary. A dome lamp begins four pixels in, a bulkhead three, a switch plate two and a half. An arm that stopped at the boundary therefore left a visible gap at _every_ connection, which reads as the cable failing to reach. So the arm is run to wherever the neighbour's own shape begins.

Never less than a pixel, even against a full block that answers zero. An arm finishing exactly on the boundary would put its end face in the same plane as the machine's own face, and two surfaces at one depth is the z-fight this whole rewrite exists to remove. A pixel of overlap buries the end inside the machine, where nobody can see it.


**[12]** Capped short of the block centre: past that the cable would be crossing the machine rather than arriving at it, and a machine with a deeply inset body wants a stub, not a skewer.


**[13]** A cable saved as a bundle carried its charge per strand. Whatever the lowest conductor held is what this one circuit inherits, which is the honest conversion: the position is one circuit now, and the rest of that FE never had anywhere to go.


**[14]** Without this, `sendBlockUpdated` moves nothing.

The default returns null, so a block entity that only overrides `getUpdateTag` is synced when its chunk loads and never again. The load byte behind the flow pulse lives here, so without this pair every cable in the world would animate at whatever load it happened to be carrying the moment its chunk loaded, forever.


## main/java/com/createstats/blockentity/CalculatorBE.java


**[1]** Handles a face-button press. Returns `true` when the token was actually processed by the controller, `false` when it was dropped (stale member whose controller BE is gone, or empty token).


## main/java/com/createstats/blockentity/CapacitorBankBE.java


**[1]** FE storage. Two-way, so `FeGridSolver` classifies it as storage: it is discharged only to cover a real machine shortfall and charged only from genuine surplus.

**Banks pool.** Any group of touching Capacitor Banks behaves as one battery whose capacity is the sum of its blocks, reachable from any face of any member. That means a wall of banks needs one connector, not one per block, which is the same lesson the Stats Display multiblock taught: a player who builds a bigger version of a thing expects one device, not N of them.

**And it fills bottom up.** Charge lands in the lowest block first and drains from the highest, so a tower of banks physically fills and empties like a gauge. That is the whole reason energy is kept per block rather than in the group: the distribution _is_ the readout. Each block drives its own LED level from its own charge, so a half-charged stack of six is three lit blocks under three dark ones, readable across a base at a glance.


**[2]** The connected set of touching banks, ordered bottom up. Held by every member so one flood fill serves the whole pool, and stamped with the tick it was built so topology changes are picked up without needing a neighbour-change hook on six faces.


**[3]** Total FE this merged bank holds. Superlinear in block count, so merging pays a bonus rather than merely adding up: see `FeBankMerge.capacity`.

On the client this sums the synced per-block shares instead of recomputing from config. The mod's config is `ModConfig.Type.COMMON`, which NeoForge loads on both sides but does not sync, so a client recomputing it would be reading its own file and could draw a completely wrong charge level on the face.


**[4]** Floods the whole touching cluster, splits it into merged banks, and hands each bank to its own members.

The partition runs over the full cluster rather than growing outward from whoever asked, because `FeBankMerge.partition` is only deterministic if every block feeds it the same input. Two blocks disagreeing about which bank they are in would turn one shared buffer into two that fight each other.


**[5]** True for the one block that draws the structure's face. The lowest corner by the merge order, so it is stable and every member agrees on it.

Only the controller renders, because the readout spans the whole structure: a gauge whose length follows the build and whose content follows the charge cannot live in a baked block texture, which is why the shell texture is now plain and everything legible is drawn by `CapacitorBankRenderer`.


**[6]** True when the merged structure is a solid box with no gaps. Only then is it safe to draw one face across the whole thing; an L-shaped cluster gets per-block treatment instead.


**[7]** Where this block sits in its bank's vertical run, which selects the connected-texture variant. A merged column loses its internal horizontal seams so the LED well reads as one continuous bar up the whole structure.


**[8]** The only work this block does per tick: keep the blockstate LED level in step with the charge. Nine buckets, so the state changes at most eight times over a full charge cycle rather than continuously.


**[9]** The only work this block does per tick: keep the blockstate in step with the charge and with the shape of the merged bank. Both are bucketed, so the state changes a handful of times over a charge cycle rather than continuously.


**[10]** Throughput, measured rather than inferred, so the face can show a real FE/t and a real time to full or empty. Only the block that draws the face bothers to track it.


**[11]** The face gauge is drawn from per-block charge, so the client needs it at a finer resolution than the eight blockstate steps below. Throttled on a 1/32 bucket with a minimum interval: only the one or two blocks at the fill front are moving at any time, because the pool fills bottom up, so this stays cheap even on a 54 block bank.


**[12]** Glow follows the whole bank, not this block, so a merged structure lights evenly instead of being bright at the bottom and dark at the top.

The deadband matters more than it looks. A charge hovering on a rounding boundary would otherwise flip this property every tick, and each flip is a setBlock, which re-meshes the whole chunk section: in game that reads as the bank strobing.


**[13]** Sends the goggle readout when it has actually moved, or once a second regardless.

Throttled rather than per tick because a large grid has hundreds of these and a packet each per tick from every one of them is real bandwidth for a number nobody is looking at. The bucket scales with the value, so small readings update precisely and large ones do not spam on noise. The heartbeat is what stops a value that drifts slowly from never being sent at all.


**[14]** Smoothed FE per tick across the bank. Positive charging, negative discharging.

Delegates to the controller, because only that block measures it. A Display Link or Stats Display pointed at any other block of the bank was reading that block's untouched zero and reporting HOLDING at +0 FE/t while the bank was visibly draining.


**[15]** Goggle readout. Reports the **pool** rather than this one block, because the pool is the battery: a player looking at the third block of a stack wants to know how the stack is doing.


## main/java/com/createstats/blockentity/DamageObserverBE.java


**[1]** Server-side: build a `com.createstats.battle.DamageObserverVoxelMap` for this observer's current scan volume. Captures palette + per-position baseline mask + current block states fresh from the world. Cheap enough to call on demand (every few seconds when a client is viewing the Tactical V2 surface).


**[2]** Fill currently-present block states. For WEAPONS observers, also assemble a holo model of the whole weapon (in WORLD coordinates): the kinetic governor that drives it, the cannon mount, every static Create Big Cannons block, and the assembled cannon's blocks transformed to world space (its barrel lives on a moving contraption entity, not in the world grid).


**[3]** Perf: cargo capability sweep is only meaningful for CARGO sensors. Skip the per-BE capability probing on HULL/LIFT/POWER/WEAPONS/CONTROL sensors, the values are never displayed for those categories and the lookups iterate three NeoForge capabilities per BE in the volume.


**[4]** Fix H: pin governor identity. If the same set of governors is still detected, keep the existing stored order so yaw/pitch assignment never silently swaps due to sublevel re-anchoring changing the sort key. Only re-seed (sort) when the set actually changes (block added/removed).


## main/java/com/createstats/blockentity/DataDialBE.java


**[1]** Vault controllers expose the full combined multiblock inventory via the item capability. getInventoryOfBlock() only returns one component block's storage and under-reports fill. The shared helper also handles vanilla chests (including double-chests) via ChestBlock.getContainer.


## main/java/com/createstats/blockentity/DispatchBoardBE.java


**[1]** The buffer that actually powers this board: **the controller's**, when this is a member.

Only the controller ever drained, which was already correct, but every member still advertised its own buffer as a capability. Wiring a member therefore charged something that was never spent, and its GUI reported a buffer that had nothing to do with whether the board worked. Routing members here makes any panel a valid connection point for the one bank.


**[2]** Returns the current tier-aware board snapshot. Always assembles from the controller's raw state; for members, delegates to the controller BE. Safe to call from both server and client (uses whatever data is available).


## main/java/com/createstats/blockentity/FactoryLogicControllerBE.java


**[1]** Kinetic Flywheel rows stand alone: no machine card or area scan. The FLC holds the flywheel armed (acting as a backup source) by default; a manual Pause disarms/holds it (handled by the manualStopLatched branch above, which re-applies the stop each pass).


**[2]** A flywheel row has no machine condition: the FLC simply holds the linked Kinetic Flywheel armed (able to back up the network). Manual Pause disarms/holds it (handled in the manualStopLatched branch of `evaluateRow`); this default keeps it armed and running.


**[3]** Arm (FLC "Run") or disarm (FLC "Pause") a flywheel's backup-source role. Only writes when the state actually changes, and does NOT clear overstress holds, so the flywheel's own overstress protection keeps working and the controller does not spam settings syncs every pass.


**[4]** Bank coordination pass: groups the running flywheel rows by their kinetic network and applies the controller's `BankStrategy`. Parallel arms every member (Create shares the load by capacity); Sequential keeps one member active at a time and holds the rest on standby until the active unit is drained. Members not on a network are simply armed standalone.


**[5]** Sequential and Smart both run one unit at a time (size 1 = that lone unit is the active one); Smart additionally commands the active unit to hold the target RPM until it browns out.


## main/java/com/createstats/blockentity/FeMotorBE.java


**[1]** The Electric Drive's brain: a Create kinetic _source_ that spends Forge Energy to turn its shaft. inverse of the dynamo - where `KineticDynamoBE` reads a shaft speed and mints FE, this reads its FE buffer and sources a shaft speed. extends `GeneratingKineticBlockEntity`, so it hands Create both a generated speed (`getGeneratedSpeed()`) and a stress capacity (`calculateAddedStressCapacity()`), exactly like the Creative Motor, except the rotation is gated on having the energy to pay for it. FE side is the pump's - power reaches the motor exactly like it reaches the FE Fluid Pump: the block is its own Power Connector, wired at the two studs on its terminal box, joins whatever grid touches it. it's an `FeManagedLoad` for the same reason the pump is, so a Power Distribution Unit can prioritise and shed it, and a motor with nothing wired to it just doesn't spin.


**[2]** A shed instruction lapses the moment no panel is driving this branch, the same rule the pump follows, so a motor shed just before its panel broke does not stay off forever.


**[3]** Decide whether the motor turns this tick and pay for it. Redstone is a cut-out and the panel's authority sits beside it, the same rule the pump and the lamps follow. When the state of the rotation actually changes, and only then, the kinetic network is told.


**[4]** FE per tick this node may pull from the grid, sized off the tier's own rated draw rather than off `WireTier`: unlike a Power Connector, a motor never resells what it receives, so there is nothing for a wire grade to meaningfully throttle here.

Twice the rated draw rather than exactly it, so the buffer can actually climb back up between demand spikes instead of living on the breakeven edge, where a single under-delivered tick stalls the shaft. This used to be a flat 512 regardless of tier, which quietly capped every grade at MK I's own rated draw: MK I broke even by coincidence and MK II could never take in enough to run, no matter what it was wired with.


## main/java/com/createstats/blockentity/FlowMeterBE.java


**[1]** Block entity for the Flow Meter, a drop-in fluid pipe segment that measures the throughput passing through it and exposes it to the Stats Display via Display Link.

The block behaves exactly like a vanilla Create axis pipe (it registers a `FluidTransportBehaviour`); each server tick this entity samples its own pipe connections, reading the active flow's fluid, direction and driving pressure, then reproduces Create's own transfer rate (`max(1, pressure / 2)` mB per tick) so the reported number matches what the network actually moves.


**[2]** Create keeps the pipe pressurized and animated even when the destination cannot accept fluid (e.g. a full tank downstream). Detect that common case by probing the neighbour in the flow direction: if its fluid handler rejects the fluid, nothing is actually moving.


**[3]** Push the sampled reading (fluid, rate, direction) to the client when it meaningfully changes, throttled so a fluctuating rate does not spam packets. This is what actually drives the animated renderer and the GUI's live "Flow now" read, which both run client-side.


## main/java/com/createstats/blockentity/FluidPumpBE.java


**[1]** The FE Fluid Pump's brain: a Create pipe segment that drives fluid down its run with a pressure it pays for in Forge Energy, at whatever strength the player dialled. works like a Create pump because a Mechanical Pump sets a pressure equal to its rotation speed on the pipe connections either side of it, and the pipe then moves `max(1, pressure / 2)` mB/t. this does the same thing - every server tick it writes the same pressure onto its own two connections, pull on the back, push on the front, exactly like `PumpBlockEntity`. only difference is where the pressure comes from: instead of `Math.abs(getSpeed())` it's the power dial, capped by the grade and by what the FE buffer can actually pay for this tick. it's an `FeManagedLoad`, not an `FeConsumer`, for the same reason as the Induction Range and the lamps - `FeConsumer`'s gated behind FE Dependent Mode, off by default, so a pump built on it would run dry with nothing wired to it. a pump that pumps without power isn't a pump. sits with the built-in power infrastructure, needs FE no matter what that toggle says, and the Power Distribution Unit can prioritise and shed it.


**[2]** A shed instruction lapses the moment no panel is driving this branch, or a pump shed just before its panel was broken would stay off forever. The lock is the player's and does not.


**[3]** Redstone is a cut-out and the panel's authority sits beside it, the same rule the lamps and the range follow: a signal, a force-off or a shed, and the pump makes no pressure this tick.


**[4]** The pump's own two connections are re-asserted every tick, pull on the back and push on the front, exactly as PumpFluidTransferBehaviour does, so whatever sits directly on the pipe is always driven even between floods.


**[5]** The rest of the run is flooded on a change, and refreshed on an interval so a pipe edited downstream is picked back up without the player having to nudge the dial.


**[6]** Writes the served pressure onto the pump's own two connections, pull on the back, push on the front, overwriting whatever was there. This is exactly what `PumpFluidTransferBehaviour` does each tick, using only the public connection API.


**[7]** Pushes pressure down both pipe runs, the way `PumpBlockEntity.updatePressureChange` plus `distributePressureTo` do: wipe the runs, re-assert this pump's own connections, then flood each side out to the pump range.

Ported from Create's open source. A foreign block cannot call the pump's own `protected` flood method, but every helper it is built from is public, so the algorithm itself is copied here and driven by the dialled pressure instead of a shaft speed.


**[8]** One side's flood, ported from `PumpBlockEntity.distributePressureTo`. Walks the pipe run out to the pump range, records the graph, resolves which faces actually lead to an endpoint, and stamps the pressure onto them, split across parallel branches.


**[9]** FE per tick this node may pull from the grid, sized off this tier's own worst case draw (full pressure, every mB costing `feCostPerMb()`) rather than off `WireTier`: a pump never resells what it receives, so there is nothing for a wire grade to meaningfully throttle here.

Twice the worst case rather than exactly it, so the buffer can climb back up between demand rather than living on the breakeven edge. This used to be a flat 512 regardless of tier, which quietly capped every grade at MK I's own full-pressure draw: MK II could never sustain full flow, no matter what it was wired with. See `FeMotorBE.transferRate()` for the same fix on the FE Motor, its matched pair.


**[10]** Wrap both ends together so the interpolation never runs backwards across 360 (which read as the fan snapping back to the start and re-spinning). The per-tick step is well under 360, so a single subtraction is enough.


**[11]** The goggle readout: what the pump is actually moving, what the dial asks for, what that costs, and how full the buffer is. Everything here is client-safe: `shownFlow()` and the dial value are synced, the buffer is synced, and the draw is the flow times the per-mB cost.


## main/java/com/createstats/blockentity/GearScrollOptionBehaviour.java


**[1]** A `ScrollOptionBehaviour` that carries its own `BehaviourType` and NBT key.

`SmartBlockEntity` stores behaviours in a map keyed by their `BehaviourType`, and all stock scroll behaviours share a single static `TYPE` and the NBT key `"ScrollValue"`. Attaching two of them to one block entity therefore makes the second overwrite the first (both in the behaviour map and in saved data). Giving each value box a unique type and key lets the Kinetic Flywheel host two independent in-world selectors (charge gear and discharge gear) on its rear face.


**[2]** Routes value-box edits to the correct box. Create's `ValueSettingsPacket` resolves the target behaviour by `netId()` (NOT by type), and the stock default is `0` for every scroll behaviour. Two boxes sharing `netId 0` therefore both apply to whichever appears first in `getAllBehaviours()`, so the charge and discharge boxes get cross-wired. A unique id per box fixes the routing on both client (packet build) and server (packet apply).


## main/java/com/createstats/blockentity/InductionHeaterBE.java


**[1]** Kinetic heat source: converts sustained RPM into Blaze-Burner-equivalent heat. Balance model (energy conservation) The stress draw is a flat coefficient of `STRESS_IMPACT` SU per RPM, applied only from `RPM_WARM` RPM upward. That yields the agreed anchor points: 64 RPM &rarr; 2048 SU (passive warmth), 128 RPM &rarr; 4096 SU (Kindled), 256 RPM &rarr; 8192 SU (Seething, coil required) - deliberately steeper than the 512 SU a real Seething burner tile is worth to Create's steam economy, so this is a convenience/endgame trade, never free energy. Thermal ramp An internal charge accumulator gains |RPM| units per tick toward the cap its RPM band allows, and bleeds `CHARGE_DECAY` per tick above it. Tier thresholds are sized so that driving exactly at a band's floor RPM reaches that band's heat in 16 seconds; overshooting within a band arrives proportionally faster at proportionally higher SU cost. Output heat is `min(tierFromCharge, tierFromRpm)`: ramp-up has thermal lag, ramp-down follows RPM honestly (no pulse-cycling exploit), while the retained charge makes re-upshifts after brief dips quick.


**[2]** The goggle overlay reads the client-side BE, so charge AND coil wear have to reach it. A coarse 1-in-10-tick sync keeps chatter negligible; the coil-damage term is essential because while Superheated the charge sits pinned at its cap, so without it the coil bar would freeze and only jump the moment an RPM change finally nudged the charge.


**[3]** The Induction Coil is the fuel: with no coil the block can only ever idle at passive Warm, no matter how hard it is driven. A coil unlocks (and is consumed by) Kindled and Superheated.


**[4]** heat tier comes from `charge` alone, never live RPM. charge already carries the thermal lag (bleeds slowly at `CHARGE_DECAY`/tick), so one tick where the network misreports 0 RPM (happens for a tick or two after `RotationPropagator` reattaches - chunk load, a neighbor update) doesn't snap the glow to NONE and back. coil's still checked directly though, running out of coil is a real instant change, not a glitch.


**[5]** bulk stove-top cooking. whole plate shares ONE timer, converts atomically (64 raw -> 64 cooked same instant) but target time scales with batch size (see `effectiveCookTicks()`). rate multiplier re-reads live heat every tick (Warm 1x, Kindled 2x, Seething ~3.3x) so ramping RPM mid-cook actually shortens time left. no heat = progress just pauses, unlike vanilla Campfire it never un-cooks.


**[6]** Bulk-cooking time scale: a square-root discount so big batches cost more, but not linearly. Anchored to ~10x at 12 items, ~15x at 24, ~20x at 36; a single item stays at 1x.


**[7]** Puts raw food on the plate, taking as much of the offered stack as fits (up to a full stack). Returns the number of items actually accepted. Topping up an ALREADY-COOKING pile of the same item is allowed, but the timer dilutes proportionally (`progress *= old/(old+added)`) so late additions cannot ride a nearly-finished batch to a free instant cook.


**[8]** No manual progress dilution now that the cook target scales with batch size (countScale): growing the stack already stretches the remaining time, while the base-ticks already applied are kept, so late additions still cannot ride a nearly-finished batch to a free cook.


**[9]** The network caches each member's coefficient, so crossing the 64-RPM floor (where the impact snaps 0 &harr; `STRESS_IMPACT`) has to be pushed explicitly - same approach as the flywheel.

`updateStressFor` re-aggregates and syncs the whole network, and that sync calls `onSpeedChanged` straight back on us - which would push again, recursing until the stack overflows. The `pushingStress` guard drops that nested call: the coefficient the outer push already handed the network is identical, so skipping it is a no-op, not a change in behaviour. Mirrors the `inNetworkCallback` guards on the clutch/gearshift BEs.


**[10]** Estimated real seconds until the installed coil burns out at the CURRENT heat tier. Returns 0 when no coil is present and -1 when the coil is not wearing right now (idle/Warm), so telemetry can show a "holds" state instead of a countdown.


**[11]** Estimated real seconds until the current batch finishes at the CURRENT heat tier. Returns 0 when nothing is cooking (or it is already done) and -1 when cooking is paused for lack of heat.


**[12]** Automation face, two logical slots. Slot 0 - coil: accepts a single coil when free, hidden and never extractable (unchanged behavior from before the cooking station existed). Slot 1 - food: accepts raw campfire-cookable items (topping up an in-progress raw pile of the same item is allowed, with proportional timer dilution). Extraction is permitted ONLY once the stack has fully cooked: a hopper below would otherwise yank raw food back out the instant a hopper above inserted it (insert-extract ping-pong), and unlike a deliberate manual grab, automation cannot "intend" an early pull. Gating on cooked keeps belt/hopper loops lossless and self-regulating - the plate itself becomes the filter.


## main/java/com/createstats/blockentity/InductionRangeBE.java


**[1]** An electric range: Forge Energy in, heat out, at whatever setting the player dialled. changes how heating works: the Induction Heater's temperature is whatever its shaft speed happens to give it. fine for a kinetic block, where speed is what you build around, but it means heat's a side effect of a decision made somewhere else entirely. electricity doesn't work that way, and neither should this - the range has a **setting**, the player chooses it, and the block draws the power that setting costs. difference between a fire and a hob. everything else follows from that. if the grid can't supply the setting, the range doesn't lie about it or silently sit at the setting anyway - it falls to the highest level the supply will actually hold and raises `InductionRangeBlock.STARVED`, so an underfed kitchen is visible from across the room, not just in a tooltip. doesn't use FeConsumer for the same reason `LampBE` doesn't - that's gated behind FE Dependent Mode, off by default, so a range built on it would heat happily with nothing wired to it. a cooker that cooks without power isn't a cooker. sits with the dynamos, connectors, cable and lamps as built-in power infrastructure that works no matter what that toggle says. free Create integration: the blockstate carries Create's own `HEAT_LEVEL`, so basins read it through `BasinBlockEntity.getHeatLevelOf` and boilers through the `BoilerHeater` registry, both with no mixins. that's the whole reason to reuse the property instead of inventing one.


**[2]** FE per tick at the top setting. The two lower settings are a quarter and a sixteenth of it, so the dial is four times the heat for four times the power at every step and a player can reason about the cost of turning it up without consulting a table.


**[3]** About thirty seconds of medium, or eight of high.

Sized so a brief grid dip never drops a cook in progress, and so the range visibly coasts down through its settings when the supply goes away instead of snapping cold. It is not meant to be a battery: at high it empties in under ten seconds.


**[4]** What the setting costs to hold, whether or not the buffer could pay for it this tick.

Sampled in `tick()` rather than derived on demand, because the Power Distribution Unit asks every device on the branch for this once per tick and the redstone cut behind it is a neighbour signal lookup. Recomputing it per query would put that lookup on the PDU's hot path for no gain: the answer only changes when this block ticks anyway.


**[5]** The hob as an inventory, so belts, funnels and Mechanical Arms can work it.

Insert only accepts what this setting can actually process, and extract only ever gives back a **finished** item. That asymmetry is what makes a funnel on each side into a working machine rather than a device that pulls its own half-cooked food off the heat: an output funnel simply waits, because there is nothing to take until the batch is done.


**[6]** A range is a node on the circuit, and it reaches every range within one block of it.

Diagonals included, which is the point: a three by three of ranges is one bank, so wiring any single unit on the edge powers the middle one that has no free face a cable could reach. Without it the centre of a slab of ranges is unwireable without tearing a hole in your own build, which is the sort of thing that quietly stops people building the shape they wanted.

The node carries no buffer of its own. The real one sits behind `energyHandle()`, and reporting it here as well would have the pooled solve count it once as grid storage and again as a sink to fill.


**[7]** The heat this element is currently asking for, which is not always the heat it gets.

The dial, unless something is holding the element off: a redstone cut, or the panel. Reading the dial alone made a switched off range report itself starved, because it "wanted" SEETHING and had achieved NONE, so the starved texture appeared on a block that was behaving perfectly. Off and underfed are different states and the block has to be able to tell them apart.


**[8]** A redstone signal is a cut-out, not a switch, matching the lamps: wiring is what powers a thing in this mod and redstone is what interrupts it. One rule for both is worth more than either rule is on its own.


**[9]** See LampBE: a shed instruction lapses as soon as no panel is driving this branch, or a range shed just before its panel was broken or unloaded would stay cold permanently. The lock is the player's instruction and does not lapse; only the automatic decision does.


**[10]** The panel's authority sits alongside the redstone cut rather than under it: switched off by the player, or shed by the controller, and the element is cold on this tick. Waiting for the two thousand FE buffer to drain would leave a locked off range cooking for another eight seconds, which is indistinguishable from the lock not working.


**[11]** What the PDU is asked to budget for. The setting the player asked for, not the one the buffer could afford this tick: a starved element still costs its setting to bring back, and costing it at what it managed to spend while starved would admit it, feed it, and shed it again.


**[12]** Spend down from the requested setting until the buffer can actually pay for a tick. The block therefore browns out through its own settings rather than flicking between full heat and cold, which is both truer to an underfed circuit and far easier to read.


**[13]** What this hob can do at a given setting, matched to how Create's own heat sources behave.

- **Low** is a campfire: campfire recipes only. In Create's terms this is a lit Blaze Burner with no fuel boost, below the `HEATED` threshold, so it will not drive a basin recipe either.

- **Medium** is `KINDLED`, which is Create's **Heated**: it satisfies `HeatCondition.HEATED` for basins, and here it also does furnace smelting.

- **High** is `SEETHING`, Create's **Superheated**: it satisfies `HeatCondition.SUPERHEATED`, and does blast-furnace recipes.

Those three rungs are not invented. `HeatCondition.testBlazeLevel` accepts KINDLED and above for Heated and only SEETHING for Superheated, so a range set to Medium drives exactly the recipes a fuelled Blaze Burner drives and one set to High drives exactly what a blaze cake drives. Matching the block everyone already understands is worth more than any figure I could pick on my own.


**[14]** The goggle readout: what is going in, what is being spent, and whether that is the setting.

Intake and draw are shown as two figures rather than one, because they answer different questions and are only equal when everything is fine. Draw is what the element is consuming; intake is what the grid is actually delivering. Intake below draw is the buffer being eaten into, which is the reading that tells you a brown-out is coming before the block starts dropping settings.


## main/java/com/createstats/blockentity/KineticDynamoBE.java


**[1]** The mod's built-in rotation-to-FE generator (functional parity with Create Crafts and Additions' Alternator): shaft below, Forge Energy out. Spins against a configurable stress impact, fills a small internal buffer, and both pushes FE to its side and top neighbors and answers extract requests (so Power Connectors and third-party cables can pull from it).

Deliberately always functional, independent of `power.feDependentMode`, so packs can use it to feed any FE mod even with the consumption mode off.


**[2]** The FE this dynamo could produce at its current speed, **ignoring** whether its own buffer has room. Unlike `lastGeneratedFePerTick()`, which falls to zero once the buffer is full and generation has throttled to match demand, this is the true generation capacity, so a Power Distribution Unit can size its branch to real supply rather than to the demand following figure that steady state hides.


**[3]** Sends the goggle readout when it has actually moved, or once a second regardless.

Throttled rather than per tick because a large grid has hundreds of these and a packet each per tick from every one of them is real bandwidth for a number nobody is looking at. The bucket scales with the value, so small readings update precisely and large ones do not spam on noise. The heartbeat is what stops a value that drifts slowly from never being sent at all.


**[4]** Output per RPM for this grade.

Read off the block rather than stored, so a grade cannot end up disagreeing with the block it is inside after a world edit or a chunk load ordering surprise.


## main/java/com/createstats/blockentity/KineticFlywheelSettings.java


**[1]** Panel-editable settings for a `KineticFlywheelBE`.

- `dischargeArmed`, master policy: when disarmed the bank still charges and holds its reserve but never acts as a backup source (never enters DISCHARGING).

- `lowReservePct`, charge percent below which the bank is flagged as a low reserve (drives the status light / alert thresholds).


## main/java/com/createstats/blockentity/LampBE.java


**[1]** A lamp's power. It lights when cable is feeding it, and not otherwise. doesn't use `FeConsumer` because that's gated by FE Dependent Mode, which is off by default - a lamp built on it would light regardless of whether anything was wired to it, the exact opposite of the point. lamps sit with the Kinetic Dynamo and connectors instead, as parts of the power system that work no matter what that toggle says. no cable = just a dark lamp.

three things can darken it:

- **No power.** The buffer ran dry, because nothing is feeding it or a switch upstream is open and its half of the grid has no source.

- **Gated.** A wirelessly linked switch is open. The lamp is still being fed; it is simply being told not to light.

- **Redstone.** A signal force-kills it, so vanilla logic can sit on top of the wiring without redstone becoming an alternative way to power a lamp.

buffer holds several seconds of running on purpose - grid output wobbles as loads come and go, and a lamp flickering every time supply dipped for a tick would be unusable.


**[2]** FE per tick while lit.

Deliberately tiny. A lamp is not a machine: a room full of them should be something you light without thinking about it, and the cost of a build's lighting should never be what decides the size of its generator. At one tick a lamp, a single maxed alternator carries a few hundred of them.


**[3]** A lamp is a node on the circuit, not just something hanging off one.

That is what lets a row of fittings light from a single cable landing on any one of them: the flood carries on through each housing into the next, the way luminaires are looped in on a real lighting circuit. It also means the Power Distribution Unit sees every lamp individually rather than one entry for whichever the cable happened to touch first.

The node carries no buffer of its own. The lamp's real buffer sits behind `attachedStorage()`, and reporting it here as well would have the pooled solve count it once as grid storage and again as a sink to fill.


**[4]** A shed instruction only means anything while a panel is actually driving this branch. Let it lapse the moment one is not, or a fitting shed just before its panel was broken, unloaded or rewired stays dark forever with nothing in the world able to explain it. The lock is the player's instruction and deliberately does not lapse; only the automatic decision does.


**[5]** Suppressed covers both halves of the panel's authority: switched off by the player, and shed by the controller. Either way the fitting is dark on this tick rather than coasting for the five seconds its buffer would otherwise carry it.


**[6]** Rated is what it costs to be allowed to run; measured is what it managed to spend. They differ exactly when the fitting is unlit for want of supply, which is the case the panel has to be able to show, so the PDU is given both rather than inferring one from the other.


## main/java/com/createstats/blockentity/LoadBalancerBE.java


**[1]** Output faces: clamp LIVE against the current source speed so a stale boost can never overspeed a branch (e.g. while a back-driving Kinetic Flywheel is still inside the side-input debounce).


**[2]** Canonical Create re-propagation for a split shaft whose ratio changed. detachKinetics() -> RotationPropagator.handleRemoved cleanly zeroes BOTH output branches (propagateMissingSource follows real kinetic connections, includes gauges, and rediscovers alternate sources); attachKinetics() re-propagates with the new left/right modifiers. Mirrors Create's SpeedControllerBlockEntity.updateTargetRotation() and the sibling SmartGearshiftBE, and avoids the same-network speed-mismatch windows that made RotationPropagator call world.destroyBlock(pos, true) (self-break + dupe).


## main/java/com/createstats/blockentity/PowerConnectorBE.java


**[1]** The wire node behind `PowerConnectorBlock`. Point-to-point FE transport with a small internal buffer:

- Pull from the attached block when it can extract (a source such as the Kinetic Dynamo).

- Equalize along every copper-wire link toward the emptier connector.

- Push into the attached block when it can receive (any FE consumer), unless energy was pulled from it this tick (prevents churn against two-way batteries).

The node also exposes a full receive-and-extract `IEnergyStorage` capability so third-party cables and machines can interface with the wire network directly. Always functional, independent of `power.feDependentMode`.


**[2]** The grade of each wire, keyed by the packed position of the connector at the far end.

Kept beside `links` rather than replacing it, because `links()` is walked every frame by the renderer and every rebuild by the flood fill, and neither should pay for building a wrapper list. A link with no entry here is `WireTier.COPPER`, which is exactly what a world saved before wire tiers existed should read as.


**[3]** Signed FE moved last solve between the grid and whatever this connector is mounted on: negative when energy left that block (a generator, or a battery discharging), positive when energy entered it (a machine being powered, or a battery charging). Written by `FeWireNetwork.solveOnce`, read by the goggle tooltip below.


**[4]** One readout sync, not two. This block already had syncFlowIfDue for its flow figure, but it watched only that field, so the buffer number beside it in the same tooltip went stale until a wiring change happened to push an update. Both values ride one signature now.


**[5]** Pushes `lastFlow` to tracking clients when it changes, with a heartbeat as a backstop.

This block never called `sendBlockUpdated` for anything but a wiring change (see `syncLinks()`), so its own `energy` field already only reached clients on relink or chunk load, not per tick. A goggle readout built on that would read as permanently stale: a value nothing ever pushes is not "synced", it just happens to be right the first time.


**[6]** The direction of the block this connector is bolted to, which is the one it serves.

Exposed so the grid can tell that a machine already has a connector on it and not hand it a second delivery through a length of cable lying across the same block.


**[7]** Records a wire running from this connector to one of a panel's output studs.

The far end is a Power Distribution Unit rather than another connector, and it is kept in the same `links` list on purpose: this node's rate is capped by the best wire on it whatever is at the other end, the strip gesture has to return that wire like any other, and the flood fill has one list to consult rather than two. What the flood does **not** do is walk into the panel, because a panel is not a member of the grid it serves: it pushes into it, from outside.

Which stud is the panel's business, not this node's. Only one wire per panel is allowed here (see `PowerDistributionUnitBE.isLinkedToConnector`), so the position alone identifies the span and the grade beside it is unambiguous.


**[8]** Sends the goggle readout when it has actually moved, or once a second regardless.

Throttled rather than per tick because a large grid has hundreds of these and a packet each per tick from every one of them is real bandwidth for a number nobody is looking at. The bucket scales with the value, so small readings update precisely and large ones do not spam on noise. The heartbeat is what stops a value that drifts slowly from never being sent at all.


**[9]** Rides the same update-tag pathway as Energy and Links, so a client that just started tracking this chunk gets the last known flow immediately instead of a flash of zero.


**[10]** FE per tick this node may move, which is the weakest link in its own hop.

A hop is only as good as its worst part, so the connector's own grade is capped by the best wire strung to it. Upgrading one without the other buys nothing, which is the whole point of splitting the upgrade across a block and an item.


## main/java/com/createstats/blockentity/PowerDistributionUnitBE.java


**[1]** The Power Distribution Unit: an inline panel that serves the branch wired to its output by priority tier, shedding the tiers its buffer can't reach. it's a block and not a global rule because shedding is a property of the PDU, not the grid - upstream `FeGridSolver.solve` stays flat and untouched, the plain connector stays a dumb wire, and a grid with no PDU behaves exactly like it always did. you install a distribution panel to manage power, which is both the better fiction and the lower-risk change.

the pipeline:

- **upstream, unchanged.** the two side bosses register a receive-only `Capabilities.EnergyStorage.BLOCK`, so the ordinary flat solve fills its buffer like any other load. top face is reserved, or the managed branch could feed the panel serving it.

- **claim each leg.** every one of the eight top studs is a Copper Wire terminal - a wire runs from stud N to a Power Connector on a machine, that machine's now on leg N. the grid each stud reaches gets stamped with `FeWireNetwork.markPduManaged` every tick, which suppresses the branch's own flat solve. without that the connectors would serve the branch evenly before priorities ever applied, and shedding would silently do nothing.

- **serve by tier.** `FeGridSolver.serveByPriority` hands out the buffer from CRITICAL down, marks the rest shed, leg by leg, each against its own wire's rating.

surface cable isn't part of this. the studs used to read a cable strand each, not anymore - the panel's output is wire, point to point, and cable's just general wiring that happens to continue a leg once a wire's landed on a connector. nothing here reads a channel.

**Shedding is soft.** Nothing here sets `powered`. A shed device keeps coasting on its own reserve and only goes dark when that runs out, reading amber LOAD SHED rather than red NO POWER.


**[2]** Nine legs: the eight Copper Wire studs on the bus bar, and the cable boss on the back.

The back is a leg like any other rather than a hole in the side of the system. Everything the panel exists to do, the breaker, the priority order, the shedding, the row on the screen, is done per leg, so an output that was not a leg would be an output the panel cannot meter, cannot isolate and cannot show you. There is no version of "give power out over here as well" that is worth having if it comes with none of that.


**[3]** Active Auto tuning, all read from `[power.pdu.auto]`.

Note what is **not** throttled. The decision runs every tick, because the reserve-floor safety layer has to be able to trip the moment the buffer crosses the floor, and because the decision is pure arithmetic once the per-device costs are in hand. Only `supplyReadIntervalTicks()` throttles anything: the walk of the whole upstream network, with a block entity lookup per member, to sum dynamo potential and read the capacitor-bank charge sign. Generation does not move fast enough to be worth measuring twenty times a second, and the smoothing EMA is still fed every tick, so its time constant is unchanged: it just sees a staircase.


**[4]** Why each managed device is in its current state, keyed by connector node. Rebuilt every tick and folded into the roster, so the panel can explain a shed rather than just announce one.


**[5]** Buffer capacity and reserve floor **as the server sees them**, refreshed each server tick and carried in the update tag.

The mod's config is `ModConfig.Type.COMMON`, which NeoForge loads on both sides but never syncs, so a client reading it directly is reading its own file rather than the server's. That is the same rule `FePowerSnapshot` exists to enforce for consumers. Without this the buffer bar, the fill percentage and the reserve-floor tick would all silently misreport on any multiplayer server whose tuning differs from the player's local copy.


**[6]** The back boss, as an energy terminal: extract only, breaker gated, and rated at the leg.

This exists for two reasons, and the second is not cosmetic. A foreign pipe or another mod's conduit has no idea what a leg is, and this is the only way it can take power from the panel at all; and `CableLinks` decides whether a run should reach into a block by asking whether it has an energy capability on that side, so without this a run landing on the boss would be drawn stopping a pixel short of the terminal it is plugged into.

**everything the panel promises still holds here.** the breaker gates it, so opening the back leg actually isolates the back leg, not just the parts the panel happens to serve itself, and the leg's own rating caps it so the boss can't pull more through a Surface Cable than one carries. our own cable never comes through here: a run the panel manages has its flat solve suppressed and is served by push, so this handle is only ever touched by wiring the panel does not own.


**[7]** Wires first: a leg's rating, its members and its rows are all read from this list, so a span whose far end has been mined has to be gone before any of them are computed.


**[8]** Everything on the branch, including machines the player has switched off. The roster is built from this list rather than the served one, because a locked-off device that vanished from the panel could never be unlocked from it: the row is the only control.


**[9]** Force OFF applies in every mode, not just Active Auto. The player has said this machine does not run, which is not a statement about scarcity, so it is taken off the branch before any solver sees it rather than being offered to one that would happily serve it.


**[10]** Only Active Auto sheds. Leaving the flags standing through a mode change would strand every device that happened to be shed at that moment: nothing in OFF or MANUAL ever clears them, and for built-in loads the flag is what actually holds the device off, so a lamp would stay dark under a panel reading OFF with nothing anywhere explaining why.


**[11]** Every load on every leg, tagged by leg in `legOfNode`.

Each leg is walked separately because each is its own circuit, and every one of them has to be marked managed. Marking only some would let an unmarked leg run its own flat solve and bypass the tier ordering entirely, which looks like the panel ignoring priorities on one branch and nowhere else.

A leg can be more than one grid. Two wires off the same stud reaching two machines that are not otherwise joined really are two separate components, and taking only the first would leave the second unmanaged and unserved while the panel cheerfully listed the leg as wired.


**[12]** The conduit on a wire branch is the connectors themselves, each holding a real buffer, plus any cable a leg happens to continue into. Both are counted, because the Wiring card is reporting what the branch is holding, not what it is made of.


**[13]** Never this panel. A leg wired back to a connector on our own input boss would have the board serve itself: the buffer would be handed to the buffer, and the roster would list the panel as one of its own loads.


**[14]** The face the output studs are on: the bus bar, where the model has drawn all eight of them since long before they did anything. Nothing may be fed in here, which is the only reason the rest of the block still needs to know which face it is.


**[15]** The face the cable boss is on: the back, opposite the instrument screen.

Behind the panel on purpose. A trunk leaving the back of a distribution board is where a trunk goes in every real install, it keeps the cable out of the way of the screen the block exists to be read from, and it means the eight wire studs on top and the one cable boss at the back can never be confused for each other by aim.


**[16]** What the back boss can push, before the cable on it is taken into account.

Twice a stud, because this is the trunk terminal: it is the one output built to feed a whole wing off a single run rather than one machine off one wire. It is exactly the rating of the heaviest cable grade, so a Bus Duct on the back is not throttled by the panel, which is the whole reason to lay one.


**[17]** The face the input stud is drawn on, opposite the output.

Cosmetic and advisory only: the energy capability accepts a feed on every face except the output, and `readUpstream()` reads all of them, so this is where the art suggests you wire the feed rather than where you must.


**[18]** The sustainable supply available to the branch: the summed generation **capacity** of every dynamo on the upstream grid (buffer independent, so hidden headroom is visible), or the observed input peak when that is larger, which is how a third party source with no readable potential (Create Crafts and Additions, Powah) is counted.

Reads the cached upstream walk, refreshed every `supplyReadIntervalTicks()`, so this is cheap to call. The observed peak is folded in live, because it is a local field.


**[19]** One walk of the feeding grid (or grids), filling `rawGeneration` and `rawBankCharging`.

**Two fixes live here.**

First, cost. This used to be two separate methods walking the same member list twice, every tick, and each hop costs a block entity lookup, which made it the single most expensive thing the PDU did. Merging them and sampling on `supplyReadIntervalTicks()` takes it from forty walks a second to two.

Second, correctness. It used to read only the connector on the one canonical input stud, but the energy capability accepts FE on _every_ face except the output, and the block tooltip, the config comment and the ponder scene all tell the player so. Wiring the feed to any other face therefore left Active Auto unable to see a single dynamo, silently falling back to the decaying observed-input peak and quietly getting worse at its job, with nothing on screen to say why. So every non-output face is scanned now. Distinct grids are summed, because two separate feeds really are two sources; the same grid reached through two faces is counted once, which is what the identity check is for.


**[20]** Only the two side bosses take a feed, which is what the capability actually allows. The old rule was "anything that is not the output", so a connector on the back or underside was counted as generation the panel can see and can never be given, and Active Auto budgeted against supply that could not arrive.


**[21]** What one output stud can push.

The stud is a built in connector at the best grade a connector reaches, so the panel is never the reason an upgraded run is capped. What actually limits a leg is whatever is strung from it, which is why `legCeiling` takes the lower of the two.


**[22]** This leg's real rating: the stud, capped by the wire actually strung to it.

Zero when nothing is on the stud, and that zero is load bearing: it is what every other reading of "is this leg wired" is derived from, so falling back to the stud's own rating here would have the panel report eight healthy legs on a panel with no wires on it at all.


**[23]** The boss, capped by whatever grade of cable is actually on it. No cable is no leg, the same zero a bare stud reports, and for the same reason: it is what "not wired" means.


**[24]** Every grid one stud's wires reach.

The stud is the source these legs are served through, exactly as the cable strand above it used to be: the wires strung from it are what decides membership, and the serve pushes the panel's buffer straight into the machines it finds. Built here rather than waited for, because a connector only rebuilds its own component inside its tick, and a leg must not go dark for a tick after a rewire.


**[25]** One cable, one circuit, one leg. Whatever run is touching the boss is this leg, and the colour rule decides where that run ends, exactly as it does anywhere else in the world.


**[26]** By identity: two wires off one stud into the same grid are one circuit, and walking it twice would double count every buffer on it and serve every machine on it twice.


**[27]** True when anything at all leaves this panel: a wire on any stud, or cable on the back boss.

One question with one answer, because the screen asks it in four places and asking it four ways is how a panel ends up reporting "nothing is wired" over a working branch. Both sides can answer it: the wire list and the leg rating are both synced.


**[28]** True when this panel already has a wire to that connector, on any stud.

Any stud rather than this one, deliberately. A connector reached from two studs would sit on whichever leg the tick happened to walk first, so the second wire would be a span the player paid for that quietly does nothing. One wire per panel per connector keeps the leg a machine is on a thing you can see.


**[29]** Drops every wire on one stud and returns them as items. The far connectors lose their side of each span too, so nothing is left drawing a wire to a terminal that no longer answers.


**[30]** Drops any wire whose far end is provably no longer a connector wired back to this panel.

Provably is the operative word: an unloaded chunk is not evidence, so a link into one is left alone and rechecked when it loads. What this catches is the connector that was mined, replaced, or world edited away, and any disagreement between the two ends, so the connector's own link list is the single record of what is strung and the panel can never keep drawing a wire to nothing.


**[31]** True when a wire is strung to this stud.

The wire alone, not the wire plus a machine. A leg used to have to be carrying something before the panel would admit it existed, which was defensible when the leg was a length of cable you could see from the doorway. A wire span you have just run and not yet plugged anything into is exactly the state a player is in while wiring, and reading it as "not wired" tells them the span failed when it did not.


**[32]** What a device costs to run and what it is actually taking, which are two different numbers.

This is the one place either is decided, so the controller's budget and the panel's rows cannot drift apart. They did: the budget used buffer headroom while the rows used headroom sampled _after_ the serve, so a healthy branch reported a large demand and a per device draw of zero in the same frame. See `com.createstats.power.FeLoadCost` for the three cases and `com.createstats.power.FeRatedLoad` for why headroom was the wrong number to begin with.

The rated check is on the node rather than on its storage because a rated block is always its own grid node: `FeWireNetwork.collectAttachments` skips any neighbour that is already an `com.createstats.power.FeGridMember`, so a lamp or a range is never wrapped in an `com.createstats.power.FeAttachment`.


**[33]** Built-in infrastructure holds a real buffer, so being dropped from the served set is not on its own enough to stop it: a shed range would cook for another eight seconds on what it had banked. It has to be told.


**[34]** Off and Manual, served leg by leg.

Even without the Active Auto controller, a leg cannot carry more than its own conductor, so each is served against its own ceiling rather than against one board wide pool. Sharing a single pool would let a hungry leg spend capacity that physically belongs to another one, and the machines on the starved leg would go dark with the panel reporting plenty of headroom.


**[35]** The Active Auto step: read supply, decide who runs, then serve them. the actual decision lives in `PduAutoController` (kept pure/unit-tested for the same reason `FeGridSolver` is - flapping across the admit boundary, the reserve floor keeping CRITICAL alive through an outage, staged restoration pacing, none of that's checkable without driving a hundred steps with no world). what's left here is just what actually needs the level: reading the upstream grid, costing each device through its capability, handing out the pool.


**[36]** Cost every device exactly once. This used to be recomputed inside the sort comparator as well as the admit loop and the telemetry loop, and every call is a capability lookup, so a twenty device branch was doing a few hundred of them per tick.


**[37]** Capacity first, supply second, and the order is the whole point. Anything a leg's own conductor cannot carry is impossible regardless of generation, so it is removed before the budget is computed: leaving it in would have the board reserve budget for a machine its wire was never going to reach, spend it on nothing, and shed a machine that could have run.


**[38]** A leg asked for more than it can carry. Trip it after a sustained overload rather than instantly, so a one tick spike does not throw a breaker a player then has to walk to.


**[39]** The upstream walk is the expensive half, so it is sampled on an interval. The EMA below is still fed every tick, so its time constant is unchanged: it just sees a staircase input.


**[40]** A healthy buffer is real supply, not a rainy-day fund to hoard. While it is above the restore mark the branch runs and admits everything its wiring can carry, funded by the reserve, so a full buffer actually powers the load a player built it for instead of sitting at 100 percent while machines are shed for want of instantaneous generation. Once the buffer falls under the restore mark the panel switches to the conservative pair: keep running on generation plus the slowly spent reserve, but admit only what generation alone sustains, so the reserve is drawn down gently. The floor still trips Critical-only near empty, and admit never exceeds run, so a device cannot flap.


**[41]** is the wire the binding constraint, or the generator? panel has to know which - opposite fixes, and a player can't tell them apart from outside. has to follow the budget actually used: while the buffer's healthy the budget is the wire's rating outright, so the wire's only to blame when the branch actually wants more than it can carry. comparing against generation there blamed the wire on a rig where it had thousands of FE/t to spare - sent the player off to upgrade the wrong thing.


**[42]** The screen's verdicts override the controller's. A device its own conductor cannot reach is not competing for supply at all, so reporting it as short of generation would send the player off to build the wrong thing.


**[43]** The rows and the device banner show the controller's dwell-stable decision, not a single lean tick: a device the controller admitted reads as running even if this one tick's pool fell short.


**[44]** How much wiring is on the branch and what it is holding.

Wiring is conduit rather than load, so it gets one summary line instead of a row each: forty conductors listed forty times would bury the machines the panel exists to show. `segments` counts conductors, meaning the connectors a leg's wires land on plus any cable strand a leg continues into, not the wires themselves. The wires are counted by `wiresOnStuds()`.


**[45]** Sticky per device numbers, so fifteen identical machines are tellable apart and stay tellable apart across a resync. Server side only: the resolved number rides along on each roster entry, so the client never needs the table itself.


**[46]** history: server side rings plus the open screen subscribers Three ranges, each 300 samples at its own cadence, so 1m, 5m and 15m are each at genuine resolution rather than one buffer downsampled on read.


**[47]** One device's panel settings, whichever of the two kinds of load it happens to be. every control on this panel used to be written as `instanceof IFeConsumer` in six separate places (two cycles, two bulk sets, lighting master, auto assign). that interface's the FE Dependent Mode path, and the blocks this panel actually manages (Induction Range, Strip Lamps) deliberately aren't on it (see `com.createstats.power.FeManagedLoad`). so on a real branch every button was a silent no-op - packet arrives, check fails, nothing happens, row resyncs to where it started, looks exactly like a dead button. six copies of a rule is six chances to add a seventh load type and forget one, so the rule lives here now and every control goes through `settingsAt(BlockPos)`.


**[48]** sets the tier of every named device actually on this branch, from the screen's explicit and bulk tier controls. single and bulk share this path, a single set is just a list of one. every position's validated against the live roster first, so a crafted/stale packet can't re-tier a device on another grid. one sync at the end, never per-device, since the update tag carries the whole roster.


**[49]** Sets the tier of every tierable light on the branch at once, from the LIGHTING master control.

No client target list to validate: the server walks its own roster for the lighting consumers, so a crafted value can only ever re-tier this branch's own lights, never reach off it. Lamps are lit infrastructure rather than consumers, so they are counted as lighting but skipped here, since they have no tier to set.


**[50]** Assigns a sensible tier to every managed device by what it is: flight and navigation gear is what you least want to lose, decorative lighting is what you least mind losing. All of it is overridable by clicking a row, this is only a starting point.


**[51]** Default tier for one block, keyed on its **registry id**.

This used to match substrings of the localized display name, which is wrong twice over. It reads whatever language the server happens to be running in, so on a non-English server every device silently landed on NORMAL and the ASSIGN button did nothing useful. And the substring rules overlap in ways that are invisible until they bite: `"flight"` contains `"light"`, so a Flight anything that did not also match the earlier `"flight control"` rule would have been filed as decorative lighting. A registry id is stable, unique and locale-proof.

Third-party blocks fall through to `FePriority.DEFAULT`, which is the honest answer: the panel has no opinion about a machine it has never heard of, and the player can set it.


**[52]** The default tier of every FE consumer this mod ships, by registry path.

The shape of the opinion: what flies or navigates the craft is what you least want to lose, what watches for damage or scans ahead is next, instrumentation and logic sit in the middle, and decoration goes first. Every one of these is a starting point the player overrides by clicking a row.


**[53]** Why one device is in its current state.

Active Auto has a real answer from the controller. The other two modes do not run a controller at all, so the honest answer there is the simple one: a lock, the tier rule, or nothing wrong. Reporting an Active Auto reason in Manual would be inventing detail the panel does not actually have.


**[54]** Whether a block belongs on the Lighting tab: the basic Lamp, and the decorative lights a player manages as a set. Keyed on the block entity or the registry id, so it holds on a server running in any language and cannot be fooled by a resource pack.


**[55]** Names first, in their own pass, because numbering needs to know the whole branch before it can hand out a number: which names repeat and therefore need one, and which positions are present so a number in use is never given to a second device.


**[56]** Everything else that takes power and is not an FeConsumer: lamps, the Induction Range, and any future block that is built-in power infrastructure rather than an FE Dependent Mode consumer. Those cannot use FeConsumer, because it is gated behind a toggle that is off by default and they have to be dead without power regardless. They are still real load on the branch, so they are listed on their own terms rather than being invisible to the panel that is supposed to manage the branch.


**[57]** What it actually spent, not how much room is left in it. Sampled here, after the serve, headroom is zero for every well fed device, which is why the load balance bar read "no load on the branch" while eighteen machines were running.


**[58]** The tier and lock the device is actually holding. These were hardcoded to DEFAULT and NONE, so even once a control did reach the block the row resynced to the old value a tick later and the button appeared to spring back.


**[59]** The reason comes from the controller's real decision, the same source the FeConsumer rows use, not from whether the block can take power this instant. A cooking range can always take more, so the old draw-based guess labelled every busy range "waiting for supply" even while it was being served, and counted it as shed. Outside Active Auto there is no decision to read, so it reads as running.


**[60]** A lock outranks the controller in every mode. Force off is the one state that has to read correctly with distribution switched off entirely, because that is exactly when a player uses it, and the controller has no opinion to report there.


**[61]** This device's number, or 0 when it is the only one of its kind and needs none.

Numbering a lone Power Distribution Unit "ID:01" is noise, so a name that appears once on the branch stays bare. The assignment itself still happens, so a second one arriving later does not renumber the first.


**[62]** Fingerprint of the branch **state**, so a change of state reaches the screen the next tick.

**Only discrete state belongs here, never the numeric readouts.** Anything in this signature forces a full block entity resync the tick it moves, and the update tag carries the entire roster as an NBT list. Generation, demand, budget and per-device draw all move under normal operation, so including them meant a whole-BE resync at up to twenty hertz per panel: the exact pattern the FE hardening pass removed from the consumers, for the exact reason recorded there. Coarsening the buckets only lowered the rate, it did not change the shape.

Leaving the numerics out costs nothing visible. The screen eases every one of those bars toward its target rather than snapping to it, so it consumes a value that arrives on the one second heartbeat exactly as smoothly as one that arrives every tick, and a raw figure that changed twenty times a second was unreadable anyway. What must be prompt is state: the mode, the served and shed counts, each device's tier and shed flag, and the surplus lamp, because those drive colour and layout rather than a bar length.


**[63]** Discrete only, per the rule above: state, never the numbers. A breaker throwing has to reach the panel on the tick it happens, because it is the one thing here a player cannot see coming and has to walk somewhere to fix.


**[64]** How many wires are on each stud, so the diagram is rebuilt when a leg is rewired. A wire to a connector with nothing on it yet changes no device row at all, and without this the WIRING view would keep showing the branch as it stood before the span was run.


**[65]** The wiring changed with the roster, so refresh the diagram tree before it is written to the tag. A switch opening or closing counts, because it changes which devices are on the branch, which is what moves the signature in the first place.


**[66]** Records one sample into each range whose cadence fires this tick, and sends deltas to watchers.

The rings run whether or not anyone is watching, which is what lets history survive the screen being closed and reopened: it lives here, not in the screen. Supply is the generation capacity in Active Auto, where it is measured, and the real intake otherwise, because a full buffer draws nothing and raw intake alone would read as zero supply.


**[67]** Sweeps the watcher list and sends a delta to each survivor whose range advanced this tick.

The sweep is the whole cleanup, and it needs no cooperation from the client: it drops a watcher that has logged out (no server player for the UUID), changed dimension (a different level), walked out of range, or gone quiet past the timeout. An explicit close handles the common case promptly; this catches everything else. Removal of the block entity clears the map outright in `setRemoved()`.


**[68]** Rebuilds the branch's wiring as a spanning tree, gathered from the live networks and handed to the pure `PduTopology` to root and order.

The root's own links are the eight studs' wires, which is what attaches the tree to the panel at all: the connectors a leg starts at are not touching the block, they hang off it on a wire, so without them the walk would find the panel alone and the diagram would show an unwired board beside a working branch.

An open switch is not a network member, the flood fill stops at it, so it is picked up here as a leaf touching a member and marked open, which is what lets the diagram draw the break rather than simply ending the run at nothing.


**[69]** The back run touches the block, so the face neighbours would find it anyway. Listing it explicitly costs nothing and means the diagram is still right if the boss ever moves.


**[70]** How many managed devices sit at each tier, and how many of them are being served, indexed by `FePriority` ordinal.

**Read from the synced roster, deliberately.** The previous version of this walked the output connector's wire network, which meant it always returned zeroes on the client: a connector's `network` is only ever built inside its `tick()`, and that returns early client-side, so `network()` is permanently null there. Its only caller is the in-world renderer, which runs client-side, so every tier lamp on the block face was reading "unpopulated" and sitting dark in all five slots no matter what the branch was doing. The roster is already synced in the update tag for the screen, so it is both correct on both sides and free.


**[71]** Cached, because the caller is a block entity renderer: this is asked for once per visible panel per frame, while the roster behind it only moves on a sync. Invalidated wherever the roster is rebuilt or loaded.


**[72]** A machine switched off on purpose is not a device the tier is failing to serve. Counting it would leave the lamp permanently short of a full tier, which reads as a problem the player cannot fix without undoing their own deliberate choice.


**[73]** config: Active Auto tuning Every one of these is wrapped, because the config is not loaded during datagen or in a unit test JVM, and a distribution panel that throws while the config is absent is worse than one that runs on the documented defaults. The fallbacks are the defaults from ModConfiguration.


## main/java/com/createstats/blockentity/SmartGearshiftBE.java


**[1]** Orient from the LIVE source facing first, so the ratio always lands on the real output even the instant a source flips to the opposite side. The cached facing is only a fallback for the brief propagation windows where no live source is assigned yet.


**[2]** Clamp LIVE against the current source speed so a stale stage modifier can never overspeed the output (e.g. during a source-side flip before the stage is recomputed for the new side).


**[3]** The face the block is currently driven from. A split shaft has no hardware-fixed input; the input is wherever the source is. Preferring the live `getSourceFacing()` keeps the input/output orientation correct when the source side changes (e.g. the original input is removed and a backup source drives the old output side), which prevents Create's propagator from computing a conflicting speed and destroying the block.


## main/java/com/createstats/blockentity/StageLightBE.java


**[1]** Block entity for the Stage Light (a mount-on-any-face moving-head stage/concert spotlight).

The head aim is two angles, `pan` (horizontal) and `tilt` (vertical), each in [-MAX_TILT_DEG, +MAX_TILT_DEG]. Aim plus `range` and `brightness` are configured in a GUI screen (opened by right-clicking the block, works on the ground and on Aeronautics ships), and the aim can be overridden by a redstone differential gimbal on the four surrounding sides. The mounted face (`StageLightBlock.FACING`) plus the two angles give the beam direction; range and brightness shape the projected pool (see the LambDynamicLights driver).


**[2]** Beam-width bounds (max radius in blocks). `beamWidth` caps the VISIBLE cone shaft (cosmetic), `lightSpread` caps the LIT pool (the real LambDynamicLights illumination, whose cost grows with the square of the radius). Default matches the previous single hardcoded cap so existing lights are unchanged until a player adjusts them.


**[3]** Beam effect programs. The server stores only (effect, speed); every animated frame is computed deterministically from game time on each client (and by the server for the projected pool), so effects cost zero per-tick network traffic and stay in sync everywhere.


**[4]** Block-light level emitted by the fixture itself while lit. A slight glow at the source so the lamp reads as "on" up close; the real illumination is the projected cone down the beam (LambDynamicLights).


**[5]** Client-only world-space occlusion reach for the projected (LambDynamicLights) cone light, so the gameplay light stops at the wall the beam hits instead of shining through it. Cached/throttled separately from the visual beam above (that one clips in the block's local space).


**[6]** Apply a configuration from the GUI: pan, tilt, range, brightness (all clamped). Persists and syncs. Aim values are also stored as the hand aim so redstone can hand control back to them.


**[7]** Brightness multiplier of an effect at a moment in time. Pure function of game time so every client and the server agree with no sync: strobe is a hard on/off square wave, pulse a smooth 0.35..1 breath, both sped up by `speed`.


**[8]** Map a block-local direction back into the FACING=up reference frame, i.e. the inverse of the renderer's facing rotation. The renderer builds the head in the up-reference and then rotates the whole fixture onto the mounted face, so the moving-head animation must decompose the beam direction in this same frame to point exactly where the beam (and the projected light) actually go.


**[9]** The falloff radius of the projected pool. Area scales linearly with range (area(16)=0.5*area(32), area(64)=2*area(32)), so the radius scales with sqrt(range / PEAK_RANGE).


**[10]** projected real light (server) A real, invisible light block placed at the spot the beam lands, so the illumination is genuine vanilla block light: it stops mob spawning, lights like a torch or lantern, and is occluded perfectly by walls under any shaderpack. Only world-placed lights use it; a light mounted on a flying Aeronautics craft keeps the per-frame LambDynamicLights cone instead, because moving a real block every tick as the craft flies would thrash the light engine.


**[11]** PULSE breathes the real pool at the recalc throttle. STROBE deliberately keeps it solid: toggling real block light every few ticks would thrash the light engine, and the visible beam carries the strobe. RAINBOW never changes luminance.


## main/java/com/createstats/blockentity/StageLightMk2BE.java


**[1]** Block entity for the Stage Light MK II, an upgraded, more elegant moving-head fixture.

It reuses every system of the base `StageLightBE` (beam, GUI, colour filter, battle-bridge control and target tracking) but doubles the reach: full brightness out to `MK2_PEAK_RANGE` and a maximum of `MK2_MAX_RANGE` blocks, with a `MK2_TRACKING_RANGE`-block tracking radius. Slotting a Beacon upgrade pushes those to `BEACON_PEAK_RANGE` / `BEACON_MAX_RANGE` / `BEACON_TRACKING_RANGE`.


## main/java/com/createstats/blockentity/StatsDisplayPanelBE.java


**[1]** FE Dependent Mode buffer, shared by the Stats Display Mk2 (which inherits this tick). Receive-only; exposed as a block capability via FeCapabilities.

Capacity and drain scale with the multiblock this panel belongs to, and in an assembly only the controller's instance is live: see `feConsumer()`.


**[2]** The buffer that actually powers this panel: **the controller's**, when this panel is a member of a multiblock.

This is what makes an assembled display one electrical device instead of N of them. The capability registration, both GUIs, the renderer and the sync all route through here, so wiring any single panel charges the whole assembly, and the whole assembly boots, browns out and dies together.

Previously every panel kept its own buffer and its own idle drain. A 3x3 therefore cost nine buffers, drew a member's idle drain eight extra times, and had to be wired nine separate times, because consumers are receive-only and physically cannot feed each other. Worse, a member that ran dry returned early from its tick while its neighbours kept going, so the display could sit there half lit.


**[3]** Live sources feeding this panel, used as both the FE activity signal and the load-scaling count. Uses the same active-source definition the GUI shows, which the panel's own idle-timeout pruning already maintains.


**[4]** FE upkeep for one panel, returning whether it may run this tick.

In an assembly only the controller drains, against the shared bank whose capacity covers the whole display. Members contribute their own neighbours as charging points, so a connector or dynamo touching _any_ panel feeds the display, and otherwise simply follow the controller's powered state.


**[5]** feConsumer() now resolves to a different object for this position, so the cached energy capability has to be dropped or connectors would keep feeding the old, now dormant, per-panel buffer.


**[6]** Pool every member's charge into the new controller. Member buffers go dormant behind the shared bank the moment they stop being controllers, so without this a merge would strand whatever each panel had been charged with.


**[7]** Renderer dispatch mode selected from the Display Link GUI configuration widget on the Battle Bridge source. 0 = V1 strategic board, 1 = V2 tactical console. Default 0 preserves legacy behaviour for existing worlds.


## main/java/com/createstats/blockentity/SwitchBE.java


**[1]** A switch, as a segment of cable that can refuse to conduct. grid's discovered by flooding node to node. closed = ordinary member, flood passes straight through. open = not a member at all, flood stops dead - network splits in two and the downstream half has no source, so its lamps go dark cause nothing to draw from, not cause anything told them to. that's literally how a real light switch works: breaks the circuit, doesn't signal the lamp. also why two switches in series both need closed, two in parallel need either one, and a cable routed around a switch just bypasses it - none of that's special cased, it's just what flood fill does once a block can say no. wireless links: a switch can also hold a list of lamps it gates without touching them. those lamps are still fed by cable, exactly like the wired ones - the switch just sets a flag that stops them lighting. wireless changes who decides, never where the power comes from.


**[2]** The switch as an ordinary piece of wiring, from the outside.

Exposed for two reasons. Cable decides where to draw its arms by asking whether the neighbour takes power, so without this a run beside a switch drew nothing and appeared to stop short of it even though it was conducting perfectly well. And it lets a foreign FE network feed a run through a switch, which is what a player would expect of something that looks like an isolator.

Both directions refuse while the contacts are open. That is not the gate, which is the flood fill declining to pass through; it is what stops anything routing _around_ the gate by treating the open switch as a machine with a buffer to fill and drain.


**[3]** Forces the grid to be rediscovered.

Called every time the contacts move, because that is precisely when the topology changes: the network on either side has to be rebuilt, and without this a thrown switch would keep conducting until something else happened to invalidate the grid.


**[4]** Rebuilds the grid on both sides of the contacts.

Invalidates this switch and every neighbouring wire. The neighbours are the point: an open switch is a member of no network, so it holds none of its own to dirty, and the old code that only touched `this.network` did nothing at all when a switch was closed from the open state. That was the bug: the run would not light until some unrelated block update happened to dirty a neighbour by chance. Dirtying them here means both halves rediscover the moment the contacts move.


**[5]** Tells every linked lamp whether it is currently gated shut.

Pushed from the switch rather than polled by the lamp: a lamp has no idea who might be gating it, and having every lamp scan for switches would be both slower and wrong when the switch is out of range or unloaded.


## main/java/com/createstats/client/battle/BattleTacticalV2PanelHologram.java


**[1]** Phase C of the Battle Tactical V2 redesign, a rotating 3D voxel hologram baked onto the front face of the physical `StatsDisplayPanelBE` block when its active source is a `battle.bridge` link in V2 (3D) mode.

This is the "at-a-glance from across the room" companion to the full GUI viewport (`BattleTacticalV2Viewport3D`). It always auto-rotates, has no controls, and floats slightly in front of the panel face. Rendered from `StatsDisplayPanelRenderer.render(...)` immediately after the textured quad pass.


**[2]** 1. Orient ourselves to the panel's front face. Same transform shape as the existing textured-quad code in StatsDisplayPanelRenderer, but our final translate puts us OUT in front of the face instead of just inside it.


**[3]** 2. Center on the multiblock face. Standoff is intentionally tiny so the hologram appears AT the panel, like a holographic projection emanating from the screen, not as a separate object floating somewhere out in the world. Pushing it ~0.4 blocks out keeps it just clear of the recessed texture surface so block models don't z-fight with the quad. Negative Z in face-local frame is "out toward the viewer".


**[4]** 4. Render every voxel using the shared core renderer. Do NOT call Lighting.setupFor3DItems() here: this runs inside the WORLD block-entity pass, and switching to item lighting without restoring it leaks flat lighting onto every chest / sign / entity drawn afterwards (the "chests and NPCs look odd" regression). The level's diffuse lighting is already correct; LightTexture.FULL_BRIGHT keeps the hologram reading as an emissive projection.


**[5]** No Lighting.setupFor3DItems(), see render(): changing lighting in the world pass without restoring it corrupts the global illumination of every block entity / entity drawn after the hologram this frame. FULL_BRIGHT keeps the voxels glowing.


**[6]** Surface-occlusion culling: a PRESENT solid voxel that is fully enclosed by opaque neighbours on all six faces is never visible from outside the rotating hologram, so skip it. This drops the entire interior hull of a large craft , the bulk of the draw calls, while all MISSING (red) and NEW (blue) markers, glass, and the visible shell still render.


**[7]** True if the voxel cell at the given LOCAL coordinates is occupied by an opaque, full-cube block that hides the adjacent face. Out-of-bounds, empty and MISSING/translucent cells return false so the neighbouring face stays visible. Used for surface-only occlusion culling.


## main/java/com/createstats/client/battle/BattleTacticalV2Viewport3D.java


**[1]** Phase B of the Battle Tactical V2 redesign, full 3D voxel viewport rendered inside the right-click Stats Display screen. Pulls cached `DamageObserverVoxelMap`s for every Battle Observer linked to the active Battle Bridge source, merges them into a single world-space scene, and renders:
- PRESENT baseline voxels  -> their real `BlockState` via the vanilla
`BlockRenderDispatcher` (real textures).
- MISSING baseline voxels  -> `red_stained_glass` default state
(translucent red cube).
- NEW voxels (not baseline)-> real state + a `light_blue_stained_glass`
overlay cube for the requested blue tint. Camera auto-rotates around the merged bounding-box centroid. Mouse / keyboard interactivity (drag-rotate, zoom, pan, sensor toggle, clip plane) is the Phase B2 follow-up; this iteration only verifies the 3D rendering pipeline.


**[2]** Client-only view preference: when a panel pos maps to `true`, the V2 page shows the flat 2D battle console instead of this 3D reconstruction. Toggled by the on-screen 2D/3D switch; not persisted (purely a local viewing choice).


**[3]** Renders the V2 3D viewport into the given screen rect. Returns true if rendering occurred (caller may skip its fallback path); false if there is no data to show (caller should render its own placeholder).


**[4]** Remember the viewport rect so input handlers (mouse drag / scroll / right-click) can hit-test against the actual on-screen area without the screen needing to know the renderer's layout decisions.


**[5]** Fit-to-viewport scale: take the smaller of the viewport dimensions and make the block diagonal of the scene fill ~55% of it, then apply the user's mouse-wheel zoom multiplier.


**[6]** CRITICAL: clear the depth buffer *inside the scissor rect* before our 3D pass. The depth buffer still holds stale world-rendering values from the frame's earlier 3D pass (terrain, particles, etc). With depth-test enabled, only voxels that happen to be in front of those stale world Z values render -- and because the world depth values change every frame (camera bob, particles, entity motion), DIFFERENT voxels survive every frame, producing visible gaps that change multiple times per second. glClear honors the scissor test, so this only resets depth inside our viewport rect.


**[7]** NOTE: Do NOT translate by (-cx, -cy, -cz) here. At far world positions (e.g. structures placed at world coords near 2e7), absolute world coords exceed float precision (~1 unit step lost above 1.6e7). Instead we apply the centroid offset PER VOXEL in double-precision below before passing to pose.translate(double, double, double), so the value handed to the matrix is always a small centroid-relative offset.


**[8]** Bind the block atlas as shader texture 0 BEFORE iterating voxels. Without this, the first voxel can render with whatever shader-texture-0 the GUI left bound (font atlas, GUI sprite atlas, etc.), producing scrambled-looking blocks. This matches the canonical Catnip GuiGameElement pattern.


**[9]** Use the GuiGraphics' own buffer source, NOT the world render buffer. The world buffer (Minecraft.getInstance().renderBuffers().bufferSource()) is shared with terrain/entity rendering and flushing it here mixes our pose stack with their pending vertices. graphics.bufferSource() is the GUI-private buffer used by Catnip's GuiBlockModelRenderBuilder for exactly this case.


**[10]** Centroid-relative translation in DOUBLE precision. We subtract the centroid BEFORE the values shrink into float range, so the float matrix never sees huge magnitudes. This is essential for structures at far world coords (>~1.6e7) where direct world- coord translates collapse adjacent voxels onto the same float value, producing the "every other block missing" pattern.


**[11]** Block-entity pass: chests, barrels, signs, etc. need their BER on top of the baked model. The baked model alone gives an empty chest base with no lid; BER draws lid/decoration in the GUI too.


**[12]** Renders the toolbar (top-right: Freeze, Reset), the Y-clip slider on the right edge, and per-sensor visibility toggles along the bottom-left. All rect coordinates are cached into `CameraState` so input handlers can hit-test exactly the same regions next frame without recomputing layout.


**[13]** Render a single block state into the GUI buffer using the canonical Catnip pattern: route through ModelBlockRenderer.renderModel(...) with vertices going into `Sheets.cutoutBlockSheet()` or `Sheets.translucentCullBlockSheet()` depending on the state's vanilla render type.

Why this and not `BlockRenderDispatcher.renderSingleBlock(...)`? That overload routes through `RenderType.solid()` which is built for the chunk VBO pipeline. The GUI's `MultiBufferSource.BufferSource` doesn't pre-register the `RenderType.solid()` buffer, so half the vertices land in a fallback buffer with format mismatches and silently drop, producing "every other block missing" artifacts in X/Z neighbours. The Sheets-based render types are GUI-safe, they are exactly what the vanilla item renderer and Catnip's `GuiBlockModelRenderBuilder` use for the same purpose.


**[14]** If the given state has a `BlockEntity` (chest, barrel, sign, etc.), lazily instantiate one and invoke its `BlockEntityRenderer` so the GUI shows the same custom geometry the world does (chest lid, barrel hinge, etc.). The instance is cached per-state and reused. Failure is swallowed silently , not every BER is GUI-safe (some require ticking/level state), and a missing decoration is preferable to a crash.


**[15]** Refresh the level every call, when the GUI first opens before the player has fully joined a level the cached instance may have been built with a null level. Setting it idempotently is cheap and keeps the renderer happy.


**[16]** Any BER that requires more context than we can give (e.g. neighbour scan for double-chest detection) will be silently skipped. The baked model is still drawn, which is acceptable visual fallback.


**[17]** When the user has switched this panel to the flat 2D console the viewport no longer renders, but its last-rendered rect (cam.lastW/lastH) stays cached. Bail out so those stale bounds can't swallow clicks meant for the 2D dashboard.


## main/java/com/createstats/client/battle/ClientObserverVoxelCache.java


**[1]** Client-side cache of `DamageObserverVoxelMap`s, keyed by the observer `BlockPos`. The Battle Tactical V2 panel and the future 3D GUI viewport pull from this cache. When a slot is missing or stale, `ensureFresh` fires a `RequestObserverVoxelMapPacket` (rate-limited per observer).


**[2]** Call from render code each frame the V2 surface is visible. If we have no cache for this observer, or the cache is older than the TTL, asynchronously fire off a request to the server. Throttled per observer so we never spam.


## main/java/com/createstats/client/battle/WeaponCannonHologram.java


**[1]** Compact 3D holographic render of a single weapon observer's scanned structure (the cannon), drawn into a corner rect of the Battle Bridge weapons screen.

This reuses the existing voxel data pipeline (`DamageObserverVoxelMap` via `ClientObserverVoxelCache`) and the canonical in-GUI 3D render pattern proven by `BattleTacticalV2Viewport3D`. It is deliberately self-contained (no camera controls, no toolbar, always auto-rotating) so it does not disturb the delicate shared ship viewport. See the Battle Tactical V2 viewport for the full rationale behind the scissor + depth-clear + per-voxel Sheets pipeline and the far-world double-precision centroid offset.


**[2]** Assembled cannon: its blocks live on a moving contraption (captured separately in local space). Prefer that model so the holo shows the real cannon (barrel + breech), not just the static mount.


**[3]** Prefer to show ONLY the cannon hardware: when any Create Big Cannons block is captured in the scan volume, hide the surrounding deck / machinery so the holo reads as the weapon itself. If the volume holds no CBC blocks at all (e.g. a fully assembled cannon whose barrel is an entity), fall back to showing every captured block so the inset is never blank.


**[4]** Renders the captured assembled-cannon contraption model (contraption-local coordinates) as a clean rotating hologram. Used when the selected cannon is assembled on a mount, so its barrel is not in the world block grid. The frame / background / label are drawn by `render`; this only adds the 3D pass plus the holographic overlay.


## main/java/com/createstats/client/CableRoutePreview.java


**[1]** The holographic preview of a pending cable run. route's computed on the client because the preview has to follow the cursor, which would need a packet every time the player's aim moved a pixel. instead the client runs the same `CableRoute` search over its own copy of the world, identical for this purpose - the router only asks whether blocks are solid or empty, and the client knows that. server still routes independently when the run's committed, so a desynced client can mispredict a preview but can never place anything the server didn't agree to. reading the hologram: cyan = good and affordable. amber = longer than the cable in hand, and the ghost stops where the cable runs out so you can see exactly how much further you could get. nothing at all = no route, deliberately quieter than an error - usually just means aiming at a face round a corner with no surface path yet. under the crosshair, the same run in numbers: length, cost, what you're holding. the ghost alone answers "where will it go" and can't answer "can I afford it" without counting translucent slabs down a corridor, which nobody actually does.


**[2]** The run the ghost is currently showing, for the readout under the crosshair.

Held in a field rather than recomputed, because the world render pass and the GUI pass are two different events and routing twice a frame to say the same thing twice would be exactly twice the work for no extra truth. Cleared whenever there is nothing to show, so the readout can never outlive the ghost it describes.


**[3]** The readout, drawn under the crosshair while a run is pending.

Under the crosshair rather than in the action bar, and deliberately: this is a live figure that changes as the player sweeps their aim, and the action bar is where one-off confirmations go. Putting a number that moves every frame there would bury the messages that matter.


**[4]** Draws a translucent slab on each step's face.

Slabs on the actual mounting faces rather than full block outlines, because the whole point the player needs to read is _which face_ the cable will land on. A cube outline at a corner would be ambiguous about exactly the thing that matters there.


## main/java/com/createstats/client/CableScenes.java


**[1]** Ponder chapters for the surface wiring layer: the three cable grades, and the rated terminals and wires that a point-to-point hop is made of. the two transport systems aren't the same thing - surface cable lies on block faces and turns corners, for wiring inside a build. Copper Wire is a catenary strung through open air between two Power Connectors, for spanning a gap. these scenes teach the first, then the rating rule that governs the second, since that rule's the part players get wrong: a hop moves only as much as its weakest part. grade's animated by count, not speed - a heavier grade carries **more**, not **sooner**: the grid's solved whole every tick, so power reaches the far end of any run immediately. animating grade as a faster pulse would teach latency that doesn't exist. so the three runs carry one, three and six simultaneous pulses, literally the multiplier, and reads as capacity rather than speed.


## main/java/com/createstats/client/ClientGraphPrefs.java


**[1]** Client-only visual preferences for the Stats Display graphs.

Backed by the client config for persistence, with an in-memory override so a GUI toggle takes effect immediately: a `ModConfigSpec` value's cache only refreshes on config reload, so writing then reading it in the same frame can return the stale value.


## main/java/com/createstats/client/CreateStatsPonderPlugin.java


**[1]** FE power chain. These scenes build their own layout with setBlock on a cleared base plate, so they reuse an existing schematic purely as a host and ship no new structures.


## main/java/com/createstats/client/CustomScreenStudioScreen.java


**[1]** Floor of 12 keeps single-cell modules legible; the previous floor of 10 produced unreadable thumbnails. Stays below typical natural cell size (>=18) so we never force overflow in normal layouts.


## main/java/com/createstats/client/FeMachineScenes.java


**[1]** Ponder chapters for the FE-driven machines: the Electric Drive, the FE Fluid Pump, the Induction Range, and the Flow Meter that measures what the pump moved. the one thing every scene here has to say: the Drive and the Pump are **their own Power Connector**. neither takes a separate connector block on top the way a dynamo or a display does - the wire lands on the two studs of the machine's own terminal box. a player who just learned the connector from `PowerScenes` will otherwise spend a while trying to mount one on a motor that doesn't accept it.


**[2]** The electric hob.

The claim worth making carefully is the equivalence: the range writes Create's own `HEAT_LEVEL` property, so Medium is exactly Create's Heated and High exactly Superheated. That is not an approximation of a Blaze Burner, it is the same property basins and boilers already read, which is why they need no special handling.


## main/java/com/createstats/client/FlywheelSoundInstance.java


**[1]** Client-side looping hum for an active Kinetic Flywheel. Pitch follows the wheel's speed and volume the activity; it fades out and stops itself when the flywheel goes idle, is removed, or the player walks away. One instance per block position, guarded by `ACTIVE` so the renderer can call `startIfNeeded(KineticFlywheelBE)` every frame without stacking duplicates.


**[2]** Must start NON-ZERO: the sound engine culls an instance whose initial volume is 0, which is why the hum was silent. Start audible, then ease toward the per-state target in tick().


**[3]** Pitch tracks the wheel speed: stored RPM while discharging (it coasts down), the network speed while charging (it spins up with the drive). Map 0..256 to a 0.55..1.45 pitch.


## main/java/com/createstats/client/hud/CommercialHud.java


**[1]** "COMMERCIAL" pilot-helmet HUD, a calm, read-only airliner glass-cockpit overlay.

Surfaces every plane-related Flight Control Computer control / motor state plus live flight telemetry as transparent Boeing-style symbology: a Flight Mode Annunciator, airspeed and altitude tapes, an attitude indicator, a vertical-speed indicator, a heading tape, an EICAS engine stack (one column per bound propeller), a systems annunciator panel (sensors / links / redstone / autopilot), and a navigation strip.

The widget layout is edge-anchored so the centre of vision stays clear, matching the helmet HUD design rule. All needles / bars / tapes are smoothed per-frame so the cockpit feels responsive regardless of the 200 ms telemetry cadence.


**[2]** per-frame smoothing state (single helmet per client) Primary flight values use the shared critically-damped spring from HudAnim (needle-style motion with real velocity), replacing plain exponential smoothing. Each spring carries a one-slot velocity holder.


**[3]** Curated: only controls that are currently OFF-NEUTRAL get a row (max 5). Enumerating every configured control made this the busiest panel on the busiest HUD; a calm glass cockpit shows what the pilot is doing, not the whole cockpit inventory.


**[4]** Curated: up to 4 rows. Small craft show every engine; bigger stacks show the 4 most abnormal (unresolved first, then largest commanded-vs-actual mismatch) plus a summary line, instead of stacking 8 near-identical bars. Abnormality is computed from the RAW telemetry so the chosen set is stable, with row index as the tiebreak.


**[5]** Caution stack (severity-ordered multi-warning). Shows the top few concurrent problems instead of only the single highest-priority one, so two faults at once both surface. WARNING-or-worse states also flash the panel rim (shared HudEffects pulse, wall-clock driven so it keeps breathing while the game is paused).


**[6]** The GO_N/S/E/W autonomous heading modes are permanently disabled, so no heading bug is ever selected. Kept as a stub (the caller already treats a negative result as "no bug").


## main/java/com/createstats/client/hud/HelmetFuelSnapshot.java


**[1]** The underlying panel data only refreshes at the 5 Hz telemetry cadence, so re-reading (and re-sorting) the channel list on every render frame was pure waste. One cached snapshot per linked position, refreshed at most every 200 ms.


**[2]** Aggregate fuel by linked SOURCE, not by raw channel. Channel ids are "src__", and a single diesel engine exposes the SAME physical supply through several FLUID_MB channels (e.g. "Fuel Amount (mB)" plus a " Reserve (mB)" mirror) that classify to different kinds and carry different ids. Summing every channel double-counts, that is the "a 64000 mB / 16000 filled tank reads as 128000 / 32000" bug. So we bucket by source and keep ONE representative content value (the largest) and ONE capacity (the largest) per source, then sum across DISTINCT sources so a genuine multi-engine craft still totals correctly.


**[3]** Reject channels that share fuel keywords but describe tank metadata rather than fluid contents: capacity readings, fill percentages, mismatch flags, reserve-link counters. These are mB-typed on the panel side but must not be summed into the gauge total.


**[4]** Extracts the source bucket key from a channel id of the form `src__`. All channels from one linked source share this prefix, so bucketing by it lets us treat an engine's fuel tank and its reserve mirror as a single pool instead of double-counting them. Falls back to the id (or label) when unprefixed.


## main/java/com/createstats/client/hud/HudAnim.java


**[1]** Small allocation-free animation helpers for HUD widgets.

All easings take a normalised time `t in [0,1]` and return a normalised progress in `[0,1]`. `pulse(long,` returns a deterministic sine pulse driven by wall-clock time so animations continue while the game is paused. `spring(float,` returns a damped approach value for needle-style interpolation.


## main/java/com/createstats/client/hud/HudEffects.java


**[1]** Deterministic, allocation-free HUD effect primitives used across V3 widgets.

P5.4, these are plain static draw helpers driven by `gameTime + partialTick` (or wall-clock for paused-game animations). They do not allocate per-frame and never spawn particles. Each effect has a bounded cost (single rectangle/scissor region of fills + a small loop count) so it is safe to call inside a HUD render pass.


**[2]** Heartbeat waveform, draws a 1-px-thick polyline scrolling right-to-left inside (x,y,w,h). The waveform amplitude is modulated by `amplitude01` and its mid-line by a small spike timed at `beatPeriodTicks`.


## main/java/com/createstats/client/hud/HudInterpolation.java


**[1]** P3.3 / P7.1, client-only sparse interpolation cache.

Stores per-key smoothed scalar values and their velocities for critically-damped spring interpolation between server-driven targets. Used to silk-smooth gauge fills, percentages, and any other numeric readout that would otherwise step abruptly when its source channel updates.

Cache is keyed by an arbitrary string (typically widget instance id + ":" + metric key) and is cleared automatically: stale entries (untouched for `STALE_MS`) are swept on every mutation. The render thread is the only consumer, so the underlying `HashMap`s do not need synchronisation.

Allocation profile: a single `float[1]` velocity array is created the first time a key is seen; subsequent frames reuse it. `Entry` objects are pooled by the map but not recycled.


**[2]** Per-entry dt so order-of-call within a single render frame does not starve later widgets of timestep. Cap at 100 ms to prevent spring blow-up on long pauses (loading screens, focus loss, world reload, etc.).


## main/java/com/createstats/client/hud/HudMicro.java


**[1]** Micro-interaction state and helpers (P5.5).

Tracks per-key hover/press timers so callers can render quick ease-out raises, compress/rebound press effects, add/remove transitions, and snap-pop flashes without keeping per-button fields. State is keyed by an arbitrary string id and is cleaned up automatically as it ages out (~3 s after last touch).

All getters return values clamped to `[0,1]` suitable for direct use with `HudAnim` blends. None of the helpers allocate per frame.


## main/java/com/createstats/client/hud/HudWarnings.java


**[1]** Shared, severity-ordered warning collector for the flight HUD modes. Replaces the old single-slot "show only the highest-priority caution" behaviour so two real problems at once (for example no yaw authority AND a missing sensor) are both surfaced instead of hiding one.

All entries come from state the backend already computes and ships in `FccGuiSnapshot` (readiness bitflags, sensor availability, the FCC warning line, armed mode). Telemetry loss (NO SIGNAL) is handled by each HUD's own banner and is intentionally not collected here.


**[2]** FE Dependent Mode: hard cutoff is an ALARM, a draining buffer a CAUTION. When the mode is off the snapshot always reports powered at 100 percent, so neither entry can appear.


**[3]** Render a compact stacked caution list top-down at (x, y). Each row is drop-shadowed and colored by severity; a "+N" row indicates additional warnings beyond maxRows. Returns the Y below the last drawn row. A small filled square before each row is a non-color cue for colorblind reading.


## main/java/com/createstats/client/hud/OtomobilHud.java


**[1]** Driver-style "OTOMOBIL" HUD rendered by the pilot helmet overlay.

All widgets share a unified neon-amber/cyan palette and use per-anchor scaling so the layout breathes naturally at any HUD scale factor.

State is held in a couple of static smoothing slots, the helmet HUD is single-instance per client, so this avoids per-frame allocations and lets widgets animate independently from the telemetry packet cadence.


**[2]** Per-wheel traction-loss intensity in 0..1. Two failure modes per wheel: wheelspin: |actual| RPM far exceeds ground-speed equivalent (driving wheel) wheel-lock/stall: commanded > 0 but actual is tiny (engine starved / no grip)


**[3]** Traction loss % for the readout + warning = the worst wheel's spin-or-lock intensity. This is REAL slip (ground-speed-vs-RPM wheelspin and stall/lock), unlike the old commanded-minus-actual shortfall which missed wheelspin entirely.


**[4]** Speed shown in m/s (telemetry vel is blocks/sec ≈ m/s). GROUND speed (horizontal velocity magnitude): vertical motion on slopes/jumps no longer inflates the speedo, and the same value feeds trip/top/average speed below so the live readout can never exceed the logged records. NaN when the telemetry stream is missing or stale.


**[5]** Gear (FWD/REV/IDLE) reflects PHYSICAL DIRECTION OF MOTION (velocity sensor), falling back to driver input when the car is essentially stationary so the chip flips to D/R the instant throttle is applied even before wheels turn.


**[6]** Derive a single "fuel bar percent" from the dominant kind's heuristic capacity. We don't know exact panel capacities client-side, so we anchor the bar to a rolling peak (acts as a soft full reference). Fuel bar fill: prefer the panel-reported capacity when known (true tank %); fall back to the legacy rolling-peak heuristic when capacity is missing.


**[7]** Stats panel: stacked directly under the FUEL block on the left column so the HUD's left edge reads top-to-bottom as RPM → FUEL → STATS. Width matches the fuel block above to keep visual rhythm.


**[8]** Tank-trim replacement for the steering wheel: per-side track power bars derived from the left/right wheel rows (FL/ML/RL vs FR/MR/RR telemetry indices). Signed fill grows up (forward) or down (reverse) from the axle line, with a white tick for the commanded value, so differential skid-steer reads at a glance. A chevron between the bars points in the turn direction whenever the tracks split.


**[9]** Bottom-right HUD block showing session+all-time top speed, session average speed, session+total drive time, live IRL+MC clock, and live longitudinal/ lateral g-force. Visual rhythm matches the RPM and FUEL blocks (same border treatment, header strip, neon-amber accents). Animations:
- `topSpeedBump` (0..1) is the easing tail of a "new record"
celebration; while active, the session top-speed value flashes amber\u2192cyan and scales by up to 12%.
- The accel/g-lat bars expand from centre with colour ramping from cyan
(gentle) through amber (sporty) to red (extreme), so heavy braking and hard cornering read instantly without staring at digits.
- The current-speed pip in the header is green while moving, dim grey otherwise.


**[10]** Compact duration formatter. Short form (`compact = false`) gives `MM:SS` up to 59:59 then rolls to `HH:MM`. Compact form (`compact = true`) adds a "`Nd`" prefix once days &gt; 0 so the "total" cell can hold months of playtime without overflowing the row.


**[11]** Collect every active vehicle warning, severity-ordered. Vehicle-specific faults (fuel, traction, signal loss) are combined with the FCC's own warning line and armed state, so two concurrent problems (say, low fuel AND wheel slip) both show instead of one hiding the other. Airship-specific readiness bits (yaw authority, control matrix) are intentionally not collected here; they describe flight craft, not ground vehicles.


## main/java/com/createstats/client/HudStudioEntryButton.java


**[1]** Fancy fold-out tab launcher for the HUD Studio. Replaces the plain "HUD" text button.

- Mechanical fold-out tab silhouette pinned to the panel header.

- Glyph: drafting compass + tiny HUD crosshair frame, drawn with theme accent.

- Idle: gentle bevel glint cycling every ~3 s.

- Hover: tab extends 2 px outward, glyph brightens, accent glow.

- Press: depresses 1 px, brief darken.

- Unsaved indicator: amber rivet in the corner when `unsavedSupplier` returns true.


## main/java/com/createstats/client/KineticMultimeterRenderer.java


**[1]** BEWLR for the Kinetic Multimeter (design plan sec.28). Renders the static body plus three live layers: a direction-true spinning cog in the throat window, a deflecting RPM needle on the dial, a glass glare, and an emissive health tint that washes the whole instrument green/amber/red.

All layers are flat textured quads drawn in the item's centered unit space. Animation is driven off `System.nanoTime()` and the `KineticMultimeterData.Reading` stamped on the stack, so it is frame-rate independent and needs no block-entity.


**[2]** freshness: hold the reading, then ease the meter back toward idle over ~10 s (200 ticks) after the last probe if it is not re-read (design plan sec.23 sticky decay).


## main/java/com/createstats/client/LampColors.java


**[1]** Tints lamp diffusers by their dye colour.

This is what makes sixteen dyes cost one model instead of sixteen: the diffuser texture is authored near white with `tintindex 0`, and this multiplies it by the dye at render time. Only the diffuser carries the tint index, so the housing stays neutral metal whatever the colour.

A lit lamp is tinted brighter than an unlit one. Multiplying a dark texture by a dark dye gives mud, so the unlit state is pulled toward grey rather than being shown at full saturation: an unpowered blue lamp should read as "off", not as a blue block.


## main/java/com/createstats/client/LightingScenes.java


**[1]** Ponder chapters for the lighting suite: the six fixed fittings, and the two aimable stage lights. built with `setBlock` on a cleared base plate and shipping no schematics, the pattern `PowerScenes` set. pacing and the custom animations come from `PonderFx`. what these scenes must not get wrong: a lamp lights when `LampBE` has energy, isn't gated by a linked switch, isn't shed by a Power Distribution Unit, and has **no** redstone signal on it. redstone's a kill line, not a supply, and the fittings need cable power whether or not FE Dependent Mode's switched on. both points are the opposite of what a player assumes from vanilla lamps, so both get said out loud here instead of left to be discovered.


## main/java/com/createstats/client/Mk2Scenes.java


**[1]** Ponder chapters for the two Mk II consoles. one file instead of folded into the Mk I chapters, because they're not similar cases. Stats Display Mk II really is just the Mk I block with a different screen, so its chapter is short and points at the eight Mk I chapters for everything else. VCC Mk II isn't the Flight Control Computer at all - separate, self-contained, shares no code with `com.createstats.flight`, and right now it's a look-first milestone with no interface built yet. its scene says exactly that instead of borrowing the FCC's, which would teach controls that don't exist on this block.


## main/java/com/createstats/client/ModKeyMappings.java


**[1]** Client-side keybindings for the Create Stats mod.

Keybinds are registered through `RegisterKeyMappingsEvent` so they appear in the vanilla Controls screen under their own category, are remappable by the player, and respect conflict contexts (e.g. NV toggle is suppressed while a screen is open so typing "g" into a text field doesn't flip night vision).

To rebind, players open _Options \u2192 Controls \u2192 Key Binds_ and look for the **Create Stats** category.


**[2]** Toggle Pilot Helmet night vision. Default `G` (unbound in vanilla Minecraft 1.21). Limited to `KeyConflictContext.IN_GAME` so it does not fire while a screen, sign-edit, or chat is open.


**[3]** Manually deploy / retract the Pilot Helmet visor (only visible with GeckoLib installed for the animated model; harmless otherwise). Forces the visor down regardless of helmet mode. Default `V` (unbound in vanilla 1.21). Limited to `KeyConflictContext.IN_GAME`.


## main/java/com/createstats/client/PanelCapabilities.java


**[1]** Read-only capability model for the Stats Display configuration right panel.

Given the focused source's channels, this classifies each channel into a `ChannelKind` (derived from its `MetricType` plus text detection) and aggregates a source-level `SourceCapabilities` set. The panel uses this to show only the controls that make sense for the selected source, Inspector-style, instead of the identical generic form for every source.

Pure client-side: it never touches the block entity state or the network.


## main/java/com/createstats/client/PduHistoryClient.java


**[1]** The client side landing for a Power Distribution Unit history send. Reached only through the reflection guarded `StatsClientHooks`, so nothing in common code references a screen class and a dedicated server never loads this.

Forwards to the open PDU screen if there is one and it belongs to this block; a send that arrives after the player has closed or switched away is simply dropped, which is correct.


## main/java/com/createstats/client/PduStudHighlight.java


**[1]** Outlines the output stud a player is pointing at on a Power Distribution Unit. drawn instead of shaped - obvious implementation is making the panel's `VoxelShape` eight small boxes and letting vanilla's own block outline follow them for free. that'd also reshape collision and mining, and the console has to stay one solid block you can't walk through or pick apart a terminal at a time. so the highlight draws on top and owns nothing - a targeting aid, block unchanged. shares the wire item's raycast on purpose - what's outlined is exactly what a Copper Wire click would land on, since both ask `PduStuds.pick` the same question about the same hit. two rules for "which stud is this" would let the panel highlight one terminal and wire another, and a player would have no way to tell which was lying. only shown while holding Copper Wire, so eight glowing boxes aren't permanently stuck to every panel in the base. the highlight answers a question, and if you're not holding a wire you're not asking it.


**[2]** Which leg this is, said in the same one based numbering the panel uses, and said once per stud you move onto rather than once per frame. Repeating it every frame would overwrite the item's own confirmation the instant it appeared, so clicking a stud would look like nothing happened while you were still pointing at it.


## main/java/com/createstats/client/PilotHelmetHudOverlay.java


**[1]** NV overlay is intentionally drawn even when no FIGHTER/OTOMOBIL context exists (e.g. player standing on the ground without a vehicle), since night vision is a worn-helmet capability independent of flight mode. It is suppressed while any screen is open to keep menus readable.


**[2]** Even without a resolvable HUD source, give clear feedback that the visor is down so the toggle is never a no-op: a small "VISOR DOWN" badge whenever it is deployed.


**[3]** A packet that stopped arriving is as untrustworthy as no packet: after this age the stream is treated as stale (dashes + SIGNAL STALE banner) instead of silently showing the last values forever.


**[4]** Craft-only fields must never fall back to the wearer's own body movement, and must not keep showing frozen values once the stream goes stale. Either way they read NaN so the HUD shows dashes plus a NO SIGNAL / SIGNAL STALE banner. The camera-based heading/pitch fallback is kept (it is legitimately the wearer's own view).


**[5]** Per-anchor HUD scaling: each element grows around its OWN anchor point so it stays in roughly the same screen region instead of zooming everything outward from screen center (which pushes corner elements off-screen at >1x). The vignette is excluded above (it's already a framebuffer-sized fullscreen mask). Battle symbology is excluded below because its target chevrons are projected from world space and must stay locked to the real-world target positions.


**[6]** Multi-warning caution stack (severity-ordered). FIGHTER previously had no warning surface at all; this now surfaces readiness bitflags, missing sensors, the FCC warning line, and armed state. NO SIGNAL is handled by the separate centre banner above.


**[7]** Minimal bottom-centre badge shown whenever the local visor is deployed but no HUD source is linked. Guarantees the V toggle is always visibly confirmed in-world (not just via the action-bar message), independent of any linked FCC / Battle Bridge.


**[8]** The HUD is shown exactly when the visor is deployed (down). Deployment is the synced visor flag (toggled by the V keybind) OR the Auto-if-seated convenience. This makes the visor and the HUD a single coupled toggle: flip the visor down, the HUD comes on; raise it, it goes off.


**[9]** Ground vehicles render if EITHER an FCC link OR a Stats Display (fuel/trip) link is available, so a brief FCC dropout doesn't blank the whole HUD while fuel/stats are still reachable.


**[10]** Second row: armed state, trim, and how many cockpit controls are off-neutral. This is GUI-only state made visible in flight (armed/trim/active controls were previously invisible outside the block's screen).


**[11]** Anything beyond ~85 degrees off boresight is behind the camera (or close enough that tan blows up and the result mirrors to the wrong side). Return null so the caller falls back to the off-screen chevron path.


**[12]** Apply the same dynamic FOV modifier the world renderer uses (sprint, flying, speed effects, fovEffectScale). Reading only the base option made projected markers drift a few pixels whenever the effective render FOV differed from the setting.


**[13]** Draw text scaled around its anchor for safety-critical emphasis. `align` is -1 for left-anchored, 0 for centred, 1 for right-anchored text. When `pulse` the alpha breathes at 2 Hz so an alarm state visibly flashes.


**[14]** CRITICAL: nearest-neighbour filtering. Without this MC upscales the texture from GUI space to framebuffer space with bilinear filtering, which bleeds the partially-transparent edge pixels across the entire screen (including any open GUI on top), producing a uniform blur over the world and the panel.


**[15]** ===== Night Vision overlay ===== The NV layer renders independently of FIGHTER/OTOMOBIL context so the player gets night vision even without a vehicle. Two presentation modes:
- CLASSIC: small animated logo top-left only; relies on the underlying
vanilla NIGHT_VISION effect (applied server-side) for the brightness.
- TACTICAL: classic logo plus a tactical-NVG look (faint green tint,
scanlines every other row, sparse animated noise speckle, soft circular vignette). All overlays are alpha-blended so the world is still clearly readable.


**[16]** Tactical-NVG full-screen overlay. The static layers (green tint, scanlines, tube vignette) are baked once per window size into a cached texture and drawn as ONE blit, the same technique the canopy vignette uses below. Only the animated noise speckle stays live per frame. This replaces roughly 870 fill() calls per frame with one blit plus the speckles.


**[17]** Bake the tint + scanline + vignette stack into one RGBA image. Each layer is composited with the standard "over" operator in the same order the old per-frame fills were drawn, so the blended result on screen is identical.


## main/java/com/createstats/client/PilotHelmetNightVisionClientHandler.java


**[1]** Client-side handler for the Pilot Helmet night-vision keybind.

Operates on the player's worn (head-slot) helmet via a dedicated toggle packet, so the player only has to **wear** the helmet for the key to work , no need to hold it in hand. If no Pilot Helmet is worn, the key is a silent no-op so it doesn't spam feedback when the same key happens to be bound for another use case in muscle memory.


## main/java/com/createstats/client/PilotHelmetVisorState.java


**[1]** Single source of truth for whether a pilot-helmet visor is deployed (down).

The manual visor state lives on the worn helmet's `PilotHelmetData` (synced via `CUSTOM_DATA`), so it is identical on every client and the server. This class only computes the _effective_ deployed state (manual flag OR the auto-if-seated convenience, derived live from the synced vehicle state) and issues the toggle request packet. It is GeckoLib-free so both the HUD overlay and the optional GeckoLib animation controller can call it.


**[2]** Effective deployed state for a wearer: the synced manual flag, or, when Auto-if-seated is on , whenever the wearer is riding a vehicle. Both inputs (the CUSTOM_DATA flag and the vehicle) are replicated to all clients, so this yields a consistent result for the local player and remote players alike (used to drive the visor animation for every viewer).


**[3]** Client-side: ask the server to flip the manual visor flag (bound to the visor keybind). Shows an immediate optimistic action-bar message; the authoritative state change arrives back through the synced helmet component a tick later.


## main/java/com/createstats/client/PinnedBattleTacticalV2HudHandler.java


**[1]** Keybind handler that toggles `PinnedBattleTacticalV2HudOverlay` for the Stats Display panel currently under the player's crosshair. If already pinned to that panel, the keybind unpins. Pressing the key while looking at a different (or no) panel re-pins to the new target.


## main/java/com/createstats/client/PinnedBattleTacticalV2HudOverlay.java


**[1]** Pinned HUD overlay for the Battle Tactical V2 Custom Screen module. Pins a Stats Display panel whose layout contains a BATTLE_TACTICAL_V2 module so the tactical readout is visible while playing.

Implementation rebakes a cached `NativeImage` via `SpecializedPanelRenderer.renderBattleTacticalV2Texture` on the same 5-tick cadence the panel uses, wraps it in a `DynamicTexture`, and blits to screen each frame. This matches the project rule: _bake once, blit per frame, never per-pixel fill loops_.


## main/java/com/createstats/client/PonderFx.java


**[1]** Shared animation kit for this mod's Ponder scenes. two halves. first is **pacing** - Create's own scenes follow rules that are easy to break by accident, encoding them once keeps fifteen scenes consistent. rules, taken from reading `PumpScenes`, `DetectorScenes` and `FactoryGaugeScenes`:

- Idle _longer_ than a text's own duration, so a line finishes and the scene breathes before the next thing happens. Text that is immediately trampled reads as a wall of words.

- `attachKeyFrame` only on the beats a player would scrub back to. Every line being a keyframe is the same as none of them being one.

- Reveal a build one piece at a time with a two to five tick gap, never in one lump.

- Pass `spawnParticles = true` whenever a block change is an _event_ rather than stage dressing. This is the single biggest difference between a scene that feels alive and one that feels like a slideshow, and it costs one boolean.

second half is **custom instructions**, which is where Create: Aeronautics gets its quality from - it ships `PropellerRotateInstruction`, `CustomGyroBearingTiltInstruction` and friends instead of composing stock calls only. the four below drive things the stock API can't express, written against this mod's real behaviour so a scene can't drift from the game: `brownout` reproduces the flicker curve from `FePowerVisuals`, `sweepAim` drives a Stage Light through its own `applyConfig`.


**[2]** A major line: keyframed, and followed by a real pause.

The extra ten ticks past the text's own duration are deliberate. Create leaves that gap everywhere and it is most of why their scenes feel unhurried.


**[3]** Re-issued every tick on purpose. The outliner *chases* its target rather than snapping to it, so feeding it a new block each step produces a box that slides down the run and eases into each corner. Issuing it once per step instead would make it blink along. Slightly inflated so it reads as a glow around the cable, not a wireframe on top of it.


**[4]** Eases a Stage Light's head from one aim to another.

Goes through the block entity's own `applyConfig` rather than poking fields, so the scene is driven by exactly the code the GUI drives and cannot show an aim the block could not actually hold. The ease is a smoothstep because a moving head accelerates and settles; a linear pan looks like a turret, not a lantern.


**[5]** Walks an integer blockstate from one value to another on an ease-out curve.

Used for the Capacitor Bank's charge LEDs. A flat loop of `modifyBlock` calls fills at a constant rate, which is wrong twice over: a real bank charges fast into an empty cell and tapers as it fills, and a constant rate gives the eye nothing to read. Easing out makes the last segment visibly the slowest, which is the part worth noticing.


## main/java/com/createstats/client/PowerScenes.java


**[1]** Ponder chapters for the FE power chain: generation, the three generator grades, wiring, storage, the optional FE Dependent Mode, and the Power Distribution Unit.

These scenes build their own layouts with `setBlock` on top of a cleared base plate rather than shipping bespoke schematics, so the whole chapter is data-free and a layout can be retuned by editing this file alone. Pacing and the custom animations come from `PonderFx`.


**[2]** Draws one output leg as a wire from the panel's studs to a machine's terminal.

A line through the air rather than cable on the floor, because that is what the mechanic actually is. Re-issuing the same leg in `PonderPalette.RED` is how a shed leg is shown: the wire is still there, it just is not carrying anything.


**[3]** Why there are three dynamos rather than one with a bigger number on it.

The figures come straight from `DynamoTier`: Open Frame at 0.75 power and 0.50 stress, Standard at 1.00 of both, Industrial at 1.50 power and 1.20 stress. The scene teaches the consequence rather than the multipliers, which is that the cheap grade wins on energy per unit of stress and is therefore not simply the one you outgrow.


**[4]** Bottom up, eased, so the taper at the top of each cell is visible. A flat loop of modifyBlock calls fills at a constant rate, which is both wrong and boring to watch.


**[5]** Drain the bank and flicker the light on the real curve at the same time, so the two are visibly the same event rather than two animations that happen to follow each other.


**[6]** The panel, taught as a machine room rather than a diagram. subject's a budget: generation's finite, machines aren't equally important, and the panel decides who eats when there's not enough. three things follow, and the first version of this scene had none of them.

- The **supply has to be on screen**. A dynamo that visibly slows down is the cause of everything else here. Without it, "supply runs short" is an assertion.

- There have to be **enough machines to rank**. Two cannot demonstrate five tiers.

- The budget itself has to be **visible**, which Ponder cannot normally do, so `PonderFx.budgetMeter` draws one.

legs are drawn as lines through the air rather than cable on the floor because that's literally what they are - a wire runs from one of the eight studs on top of the panel to a machine's connector.


**[7]** The second PDU chapter: the three modes, and what Active Auto actually decides.

Kept separate from `powerDistributionUnit` on purpose. That scene teaches the block and the tier idea, which is what a player needs the first time they place one. This one is about the controller, and it is built around the one comparison that explains it: Manual watches the **buffer** and therefore always reacts too late, while Active Auto watches **generation** and acts before the reserve is touched. Two runs of the same failure, with two meters, is the only way to show a difference that is entirely about timing.


## main/java/com/createstats/client/StatsDisplayMk2Screen.java


**[1]** Stats Display Mk II configuration studio.

A breathable, full-window, step-based configurator with a persistent live preview of the real panel on the right. The preview reuses `SpecializedPanelRenderer`'s GUI entry points, so it is pixel accurate with what the block shows in the world.

Phase 1 delivers the studio shell (header, step rail, adaptive preview dock) and a working Sources step. The Content / Style / Alerts / Live steps are introduced in later phases.


## main/java/com/createstats/client/StatsDisplayScreen.java


**[1]** When an Alert/Redstone editor is open it owns the whole panel region (its own widgets are built separately); skip the Data/Tools tabs + content so nothing renders on top of the editor.


**[2]** Only reserve list space when the panel is tall enough for the section header + rows + the control buttons below (header 54 + section label 11 + status 30 + conservative button reserve). On short panels / high GUI scale the list collapses so it never overlaps controls.


**[3]** Block 3: persistent, scrollable channel inspector list in the DATA tab. Clicking a row selects the channel (same path as the signal picker); the row shows the live value + alert state so multi-channel sources are scannable without opening the dropdown.


**[4]** Block 4: at-a-glance Triggers summary for the selected channel in the Tools tab. Alert is per-channel; redstone is the panel's single bound output. The existing Alert / Redstone editors remain the edit UI.


**[5]** Block 0 / visual overhaul: a themed option-panel button that renders in the same soft-pill style as the tab bar and dropdowns (accent border + hover glow) instead of the flat vanilla button, so the whole right panel reads as one designed surface.


**[6]** V2 3D viewport (Battle Tactical): drag-rotate, right-click freeze, MMB reset. Routed first so the viewport "owns" clicks inside its rect before the screen sends them to the chart / dashboard handlers behind it.


**[7]** Instrument-style markers: a cyan "bug" at the running average and a red tick at the session peak, both riding just outside the dial so the needle stays readable on top.


**[8]** The trace is baked into a cached texture (one blit per frame) instead of tens of thousands of per-pixel fill() calls. It only rebuilds when the data, range, size or reveal change.


**[9]** Flow Meters have no capacity, so they render in the FLUID section as amount-only rows: the fluid kind is the title, the all-time total is the amount, and live speed is on the subline.


**[10]** Draws the small 2D/3D view switch in the top-right of the tactical graph rect on Battle Bridge V2 pages. The label shows the action (the mode you would switch TO). Hit-tested in `mouseClicked`. Visible in both 2D and 3D modes.


**[11]** When the 3D viewport is active it owns a Reset+Freeze toolbar at the rect's top-right (~2 x 16px buttons + gaps starting 4px from the right edge). Tuck the switch just left of that cluster so nothing overlaps; in 2D mode it sits alone.


**[12]** Determines if a source should be visible in specialized panels (Production Detector, Flight Deck, Guidance, etc.) and in the navigation list that drives them. Follows the Home page binding model: shows the focused source plus any selected/bound source. Connected-but-unselected sources are intentionally hidden so specialized panels mirror the Home selection state.

The Home page itself continues to use `shouldShowSourceInNavigation(String,` to surface every connected source for browsing/selection.


**[13]** Phase B of the Battle Tactical V2 redesign: dispatch the 3D voxel viewport if the active source is a Battle Bridge AND the link is in V2 mode. Returns true if rendering happened so the caller skips its standard specialized panel path. Returns false otherwise.


## main/java/com/createstats/client/StressometerHudDataResolver.java


**[1]** P4.6: exact-match channel lookup. Tries direct id equality, then suffix match against `"_metricId"`, then exact (case-insensitive) label match. Does NOT fall back to label-substring; that's the legacy fuzzy path.


## main/java/com/createstats/client/StressometerHudSettingsScreen.java


**[1]** Subtitle removed (P9 polish): "Bound focus: …" was overlapping the toolbar buttons (undo/redo/snap/help/theme preview) and is redundant, the source button on the left panel already shows what is bound.


**[2]** Stressometer LOAD_GAUGE has the reactor variant, only when bound to a base Stressometer source (specialized LOAD_GAUGE variants like Safety Clutch / Load Balancer go through renderSourcePart which doesn't dispatch the reactor variant).


**[3]** P4.1: Reset entry point. Builds a source-aware default state for the active scope and applies it. If the prepared state ends up empty (e.g. a focused source has no defaults), shows an inline confirmation modal instead of silently wiping the canvas.


**[4]** P4.1 source-aware seed: add a representative card per scoped non-stressometer source (cap small to keep canvas usable). Mixed scopes thus get both the Stressometer defaults *and* a card per specialized source.


**[5]** P4.4: scope filter that whitelists synthetic Aero composite source ids (aero_flight_deck / aeroline_director) when scoped aero feeds are present , this is enforced inside `isWidgetInStudioScope(StressometerHudState.PartWidget)`. Pure helper that mutates only the supplied state, so callers can prepare a candidate state without committing it (used by P4.1 reset confirmation).


**[6]** P9 polish: in topology mode, the source-button on the left panel already shows what is bound; the scope-below-presets label was stacking on top of the "Widget groups" header. Skip drawing it there.


**[7]** Four rows of preset buttons (24 px each, last row ends ~92 px below presetsY) need ~28 px clearance for the "Widget groups" header to sit cleanly underneath , bumped from +96 to +124.


## main/java/com/createstats/client/SwitchScenes.java


**[1]** Ponder chapters for the five wall switches. built with `setBlock` on a cleared base plate the same way as `PowerScenes` and `LightingScenes`; pacing and animation come from `PonderFx`. ground truth these scenes are written against: every switch in the set **latches**, push button included - `SwitchBlock` flips `POWERED` on use and nothing schedules it back. a switch is also a real link in the cable run rather than a signal on the side of one, so throwing it rebuilds the grid on both sides and everything downstream of an open switch loses supply.


## main/java/com/createstats/compat/CreateBigCannonsReflectionAdapter.java


**[1]** Captures the blocks of an assembled cannon mounted on a Cannon Mount, transformed into WORLD coordinates via the contraption entity's own local-to-global transform, so the Battle Bridge holo can show the real cannon sitting in line with its static mount and governor. When a cannon is assembled its barrel lives on a moving contraption entity, not in the world block grid, so this is the only way to recover it. Returns an empty map when CBC is absent, the mount is not assembled, the transform method is unavailable, or the read fails.


**[2]** Create stores each contraption block local to the contraption's anchor, and sets contraption.anchor = the contraption entity's blockPosition() (the rotation pivot, which for a cannon mount is offset from the mount block itself). So anchorPos + localOffset is the cannon's true BUILD-pose world position: correctly seated relative to the mount/governor, compact, and independent of the current aim (which keeps the holo readable and tightly framed).


**[3]** Reads the total propellant charge power currently loaded in a mounted big cannon. In Create Big Cannons the muzzle velocity (blocks/tick) of a fired shot equals `propelCtx.chargesUsed`, which is the sum of every propellant block's `getChargePower(StructureBlockInfo)`. We walk the assembled contraption's present block entities, sum the charge power of each propellant cell and return it as the effective muzzle velocity. Returns 0 when CBC is absent, the cannon is not assembled, or no propellant is loaded.


**[4]** Count loaded munition cells before firing. CBC's fireShot() returns void and silently does nothing when the bore arrangement is invalid (no ignitable charge at the breech, or the projectile sits ahead of the charge) or the quickfiring breech is mid-cycle (open), all of which are common with an attached autoloader. So merely invoking the method is NOT proof the cannon fired; a genuine discharge consumes the loaded munition cells, which we verify below.


**[5]** Counts the loaded projectile/propellant cells in an assembled big cannon contraption by walking its present block entities and reading each cell's contained munition. Returns -1 when the contraption cannot be introspected, so callers can distinguish "empty" from "unknown".


**[6]** Only the Cannon Loader's "moving" property marks an active loading stroke. Ram Head and Worm Head are persistent structural blocks (modeled on a piston head, with no "moving" state), so their mere presence does NOT mean the cannon is loading. Flagging them here previously left any cannon with an attached autoloader permanently stuck reporting "LOADING".


**[7]** A loader head sitting inside a bore cell is loader hardware (often left behind when a load jams or the loader is broken), not an in-progress load. Treat it as inspected/unknown so it can't masquerade as a perpetual loading state; a genuine stroke is caught via "moving" above.


## main/java/com/createstats/compat/geckolib/GeckolibCompat.java


**[1]** Single entry point ("choke point") for the optional GeckoLib integration.

Every reference to a GeckoLib-touching class (`GeoPilotHelmetItem` and friends) is funneled through here, and this class is itself only _called_ from inside a `ModList.isLoaded("geckolib")` guard in `ModItems`. Because the JVM only loads a class when it is first actively used, a game without GeckoLib never triggers loading of this class's GeckoLib dependencies, so there is no `NoClassDefFoundError`.

The factory deliberately returns the `PilotHelmetItem` supertype so the registry entry's generic type stays `ItemEntry` and the rest of the codebase remains GeckoLib-agnostic.


**[2]** Register the pilot helmet as a GeckoLib synced singleton animatable so the visor animation state replicates correctly to all clients. Safe to call once at common setup. Only invoked from inside the `isLoaded("geckolib")` guard in `CreateStats#commonSetup`.


## main/java/com/createstats/compat/geckolib/GeoPilotHelmetItem.java


**[1]** GeckoLib-animated variant of `PilotHelmetItem`.

This class hard-references GeckoLib types, so it is constructed _only_ when GeckoLib is installed. `ModItems` chooses between this and the plain `PilotHelmetItem` at registration time via `ModList.isLoaded("geckolib")`; the single reference to this class lives behind that guard (in `GeckolibCompat`) so a GeckoLib-less game never loads it and therefore never hits a `NoClassDefFoundError`. The registry id is identical either way, so worlds move cleanly between "GeckoLib installed" and "not".

The visor deploy/retract is driven by `PilotHelmetVisorState`, which mirrors the existing HUD activation (Auto-if-seated / always-on) and adds the manual keybind override.


## main/java/com/createstats/compat/geckolib/PilotHelmetGeoArmorRenderer.java


**[1]** GeckoLib armor renderer for the animated pilot helmet.

In GeckoLib 4.8 a `GeoArmorRenderer` _is_ a `net.minecraft.client.model.HumanoidModel`, so the render provider can hand this straight back to vanilla's armor layer. Only the head segment is relevant for a helmet; GeckoLib hides the other body bones automatically based on the worn slot.

Two render fixes live here:

- **`RenderType.armorCutoutNoCull`**, forces a no-cull, alpha-cutout pass so the inner/rear faces of the open-front shell are never back-face culled and the visor glass cutout doesn't get alpha-discarded. This is the robust cure for "black/missing rear faces": both sides of every quad are drawn and lit.

- **`AutoGlowingGeoLayer`**, adds an emissive pass driven by the `pilot_helmet_geo_glowmask.png` mask, so the HUD lens, status dots and crest accent render full-bright regardless of ambient light.


**[2]** No-cull cutout: draw both sides of every face (open-front shell has no "inside" to hide) and alpha-cut the lens rather than alpha-blend/discard. Fixes black rear faces + dull look.


## main/java/com/createstats/compat/geckolib/PilotHelmetGeoModel.java


**[1]** GeoModel for the animated pilot helmet. Points GeckoLib at the geo model, texture and animation assets under the `create_stats` namespace.

Loaded only when GeckoLib is present (this whole package is guarded behind `ModList.isLoaded("geckolib")` at registration time), so referencing GeckoLib types here is safe.


## main/java/com/createstats/compat/jei/CreateStatsJeiPlugin.java


**[1]** JEI compat. Discovered by JEI's own bytecode scan for `@JeiPlugin`; nothing in this mod references this class, so without JEI installed it is simply never instantiated (hence no `ModList.isLoaded` guard - the direction is JEI calling into us, not the reverse).


## main/java/com/createstats/compat/lambdynlights/StageLightBeamBehavior.java


**[1]** A LambDynamicLights `DynamicLightBehavior` that lights a Stage Light's beam as a real CONE: it illuminates the whole path from the head out to `range`, getting wider with distance (like a stage spotlight), at full brightness up to the peak distance then dimming toward the far end.

Because this is real LambDynamicLights lighting, the cone is frame-perfect and correct under Sodium and every Iris shaderpack, and it works the same whether the Stage Light is a plain world block or mounted on a flying Aeronautics craft (the driver supplies the projected world source + direction). LambDynamicLights caps how far a source reaches, so a very long beam is truncated at that cap, but the widening-cone shape along the reachable path is preserved.


**[2]** Absolute safety ceiling on the lit cone radius (blocks). The actual cap is the per-light `lightSpread` setting (player-adjustable); this just bounds the EXPENSIVE dimension of the light volume (the cross-section) so even a maxed-out spread can't blow up the LambDynamicLights bounding box. The beam length is left at full range so the projected light still reaches far.


**[3]** Blocks over which the cone fades in at the head end and out at the far end. The cone is a volume cut by two planes perpendicular to the beam (at the head and at the reach). When the beam is tilted - e.g. a banked Aeronautics craft - those planes slice the ground along a straight line, which reads as a hard wall across the lit pool. Fading brightness across these end bands turns that razor edge into a soft gradient, so the pool always looks like a real spotlight splash instead of a clipped slab.


**[4]** Fraction of the cone radius over which the rim fades out. The inner part of the cone is full brightness and the outer `RIM_FADE` of the radius ramps down to zero at the edge, so the lit pool has a bright core and a soft border instead of a hard step.


**[5]** When the beam is stopped by a surface, the reach is extended this many blocks PAST the hit and the far brightness fades across that overshoot. The hit surface itself sits at the full-bright start of the fade (so the wall / floor still gets a bright splash), while the visible pool edge just beyond it softens into a gradient instead of a hard straight line. The overshoot mostly falls inside the struck block (out of sight), so it does not meaningfully light the far side of a wall.


**[6]** Cone radius (blocks) at a given along-beam distance; the spread scales with the focus setting, but is capped so a wide-focus long-range beam can't blow up the LambDynamicLights bounding box (the light pool stays a sane size even when the visible beam is huge).


**[7]** Soft edge toward the cone rim: a bright core, ramping down to zero only across the outer RIM_FADE of the radius (no brightness floor), so the pool border is a smooth fade rather than a hard step.


**[8]** Head end fades in over the first END_FADE blocks so the apex plane is not a hard wall on a tilted beam. The far end also fades, but over a different width depending on whether the beam ends in open air (END_FADE) or on a surface (OCCLUDED_OVERSHOOT): in the occluded case the reach was extended past the hit, so the struck surface lands at the full-bright start of the fade (bright splash) while the pool edge just beyond it dissolves into a gradient instead of a hard trapezoid line.


## main/java/com/createstats/compat/lambdynlights/StageLightDynamicLightClient.java


**[1]** Client-side driver that gives every lit Stage Light a LambDynamicLights beam light at its world beam target, updated each client tick so it tracks the aim (and, for ship-mounted lights, the craft's motion) frame-perfectly.

Stage Light block entities register themselves here when they load on the client and unregister when removed. Each tick we (re)position one `StageLightBeamBehavior` per lit light and drop the behavior when the light turns off or the block goes away. All LambDynamicLights access is client-only and reflection-free; if LambDynamicLights has not initialised yet we simply wait.


**[2]** Occlusion: stop the projected cone at the first solid block along the beam so the gameplay light no longer shines through the wall (or hull / floor) it hits, matching the visible shaft.


**[3]** The distance the projected cone may reach before something blocks it, from a throttled world-space raycast along the aim. Clamping the LambDynamicLights cone to this stops the gameplay light from shining through the wall / hull / floor the beam hits (the visible shaft already clips like this). Re-raycast only a few times a second so a long MK II / Beacon beam stays cheap.


**[4]** Extend the reach a few blocks past the surface so the lit cone covers the hit face with a bright splash and softens just beyond it; the overshoot falls inside the struck block, so the far side of the wall stays unlit (no light-through-walls).


**[5]** Compute the world-space beam source (the head) and unit direction for a Stage Light.

For a plain world block the source is the block centre and the direction is the aim. For a Stage Light mounted on a flying Aeronautics sub-level, the block's stored position is in the sub-level's internal plot space (millions of blocks away), so we project the head into the real world with `SableBridge.localToWorld` and rotate the aim by the sub-level's orientation, so the cone lights the real world from the craft and follows it as it flies.


## main/java/com/createstats/compat/lambdynlights/StageLightDynamicLights.java


**[1]** LambDynamicLights entrypoint for the mod.

The Stage Light projects a real, frame-perfect, shaderpack-correct light into the world at its beam target by adding a `dev.lambdaurora.lambdynlights.api.behavior.DynamicLightBehavior` to LambDynamicLights' behavior manager. This entrypoint simply captures that manager so the client-side `StageLightDynamicLightClient` can add/remove beam lights at runtime.

Declared via the `yumi:entrypoints -> lambdynlights:initializer` property in `neoforge.mods.toml`. LambDynamicLights is client-only, so this never runs on a dedicated server.


## main/java/com/createstats/CreateStats.java


**[1]** Teach Create that the Pilot Helmet counts as a pair of brass goggles for the purposes of `com.simibubi.create.content.equipment.goggles.GogglesItem.isWearingGoggles`.

This unlocks every existing goggle-gated overlay/tooltip for free: stress + RPM hover info, `com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation` extra lines on shafts, tanks, mixers, our own FCC/Pathfinder/Stats blocks, etc. Create itself documents this hook as the intended extension point for "custom armor, handheld alternatives, etc." so we are using the public API rather than mixing in.


**[2]** One source for all three power block types: generation, transport and storage are three views of the same system, so a Display Link pointed at any of them gives a power readout.


**[3]** Boilers resolve heat through Create's `BoilerHeater` registry. Because the Induction Heater deliberately reuses `BlazeBurnerBlock.HEAT_LEVEL` as its blockstate property, Create's own `BLAZE_BURNER` heater lambda reads it as-is: SEETHING = 2, KINDLED = 1, SMOULDERING = passive. Basins need no wiring at all - `BasinBlockEntity.getHeatLevelOf` checks for that property directly.


## main/java/com/createstats/dashboard/package-info.java


**[1]** Auto-Dashboard Mode for Create Stats and Numbers. Provides intelligent multi-zone layout for large multiblock display panels. Activates automatically when the panel is at least 3×2 blocks with 2+ channels.


## main/java/com/createstats/data/KineticMultimeterData.java


**[1]** Compact "last reading" stored on a Kinetic Multimeter stack (see the v4 design plan, sec.29). Written server-side when the player probes a kinetic block; read client-side by the renderer/tooltip.


## main/java/com/createstats/dispatch/BoardLayoutSpec.java


**[1]** Concrete layout geometry for a dispatch wall of a given size and tier. Tells the renderer and UI how many rows fit, how many history entries to show, and which optional regions are active, without either consumer needing to re-derive the tier logic itself.

Instances are cheap value objects; create one per refresh/frame as needed.


## main/java/com/createstats/dispatch/BoardTier.java


**[1]** Resolves the tier for a given wall dimension. Uses area as the primary discriminant; the tier thresholds are:

- area 1 → MINI

- area 2–3 → COMPACT

- area 4–8 → STANDARD

- area 9–15 → RICH

- area 16+ → FULL


## main/java/com/createstats/dispatch/DispatchBoardSnapshot.java


**[1]** Fully assembled, tier-aware board snapshot ready for renderer and UI consumption. Built once per refresh from the controller's raw state; consumers never need to re-derive tier logic, row states, or layout capacities.

For 4x4+ (FULL tier), includes station-specific train roster and visit history beyond the currently present train.


## main/java/com/createstats/dispatch/DispatchRowEntry.java


**[1]** A single dispatch row as consumed by the renderer and UI. Wraps the raw `DeliveryOrderLineView` with pre-derived `DispatchRowState` and convenience accessors so consumers never need to re-derive row state themselves.


## main/java/com/createstats/display/CargoLinkTargetHandler.java


**[1]** Handles Cargo Link target acceptance and constraint rules. Compact targets (DataDial, MiniDisplay, StatusTowerLight) receive only constrained cargo summaries: completion %, train present, manifest line count. Rich targets (StatsDisplayPanel, HolographicProjector) can consume fuller freight state. StatsRecorder rejects cargo data entirely.


## main/java/com/createstats/display/FlowMeterDisplaySource.java


**[1]** Display Link source for the Flow Meter. Streams the live fluid throughput (mB/t), the carried fluid, the flow direction and the cumulative volume moved so the Stats Display can render them.


**[2]** Keep pushing to the target on a steady cadence even when the reading is unchanged (idle flow). Without this, Create's Display Link stops refreshing an idle meter and the Stats Display prunes the source as stale, making it vanish. A ~1s passive refresh keeps the idle meter visible.


## main/java/com/createstats/display/HologramSettings.java


**[1]** NETWORK has no distinct renderer path; normalize it to LINE_3D on load so that old saves are migrated automatically and the UI never shows a mode that doesn't have a unique presentation.


## main/java/com/createstats/display/MetricContract.java


**[1]** Canonical per-metric display contract for the Stats Display.

The whole point: a given stat should be shown and alarmed the **same way regardless of the display settings** or which source happens to be primary. Today a gauge's range/direction is derived from rolling observed data plus source priority, so the same metric can render with a different (even inverted) scale depending on configuration. This table lifts the range and the sensible alarm defaults out of the renderers into one authoritative place keyed by `MetricType`, so every renderer reads the same contract.

Metrics with a well-defined range (percentages, redstone 0-15, kinetic RPM, booleans) get a fixed canonical range; everything open-ended (throughput, energy, absolute fluid, signed velocity) still auto-ranges from observed data, but only as a last resort, never for the known metrics.


**[2]** Health band positions as gauge fractions `[warnFrac, dangerFrac]` (0..1 along the dial), so a known stat always reads green → amber → red at sensible points, or `null` to fall back to the per-display zone settings. Fractions line up with `fixedRange` (e.g. for a 0..100 percent metric, 0.70 = 70%, 0.90 = 90%; for RPM 0..256, 0.83 ≈ 213 rpm).


## main/java/com/createstats/display/PowerDisplaySource.java


**[1]** Display Link source for the mod's power hardware: the Kinetic Dynamo, the Power Connector (which speaks for its whole grid) and the Capacitor Bank (which speaks for its whole merged structure).

One source class for all three rather than three classes, because a player pointing a Display Link at "the power stuff" wants a power readout, and the three block types are three views of the same system: generation, transport, storage.

The figures here are measured, not inferred. Everything the generic energy path could offer is derived by differencing a stored buffer over time, which reads zero exactly when a machine is comfortably supplied; these come from the per-tick solve.


**[2]** Power moves every tick, so a link that only refreshed on change would either spam or stall. A one second passive refresh also keeps an idle grid from being pruned as a stale source, which is the same reason the Flow Meter does it.


## main/java/com/createstats/display/StatsDisplayTarget.java


**[1]** Do NOT call removeSourceByPosition() here. Battle screens manage their own sourceRegistry entry inside registerOrUpdateBattleScreen(). Removing first would delete the source + link + all channels every 10 ticks, causing a new source ID on every Display Link cycle and constant syncSourceIds() churn.


**[2]** cargo Link target constraint rules When a CargoLinkSource is active, route through cargo-specific handling instead of the generic metric parsing pipeline. Compact targets get only constrained summaries; rich targets get fuller freight state.


**[3]** Tunnel / belt fallback: when the observed block exposes no inventory or fluid (e.g. Brass Tunnel, Andesite Tunnel, Belt segment), we still want the HUD to surface what the Smart Observer is watching. Try the tunnel filter first; if empty, scan the observer block itself and the belt directly under the observed position. Always emit a single "observer_active" metric so widgets can render a meaningful state instead of "no data".


## main/java/com/createstats/event/FePowerServerEvents.java


**[1]** FE Dependent Mode server events.

- The chunk-watch hook seeds a player's FE mirror the moment they start tracking a chunk. Power state no longer rides the vanilla block entity update tag, so without this a player walking into range would see default state until each consumer happened to change.

- A one-line world-join status telling the player whether FE Dependent Mode is on, so nobody has to guess why their electronics do or do not need power.


**[2]** One short line on join stating whether FE Dependent Mode is on. Deliberately a status, not a tutorial: the Ponder chapters teach the feature now, this only answers "is it on right now", which nothing in the world tells you at a glance. Suppressible via `power.showLoginReminder`.


## main/java/com/createstats/event/PilotHelmetServerEvents.java


**[1]** Server-side per-tick maintenance for the Pilot Helmet.

Responsibilities:

- Re-apply the `MobEffects.NIGHT_VISION` effect at a steady cadence while the player wears a Pilot Helmet whose NV flag is on.

- Auto-disable NV when the player puts on a non-helmet head item or no head item at all, the data on the stack is preserved, only the running effect is cleared.

- Auto-disable NV if an external (potion/beacon) night-vision effect lands on the player while the helmet was driving NV. This avoids the helmet "fighting" the longer external source.

NV is re-applied every `REFRESH_INTERVAL_TICKS` ticks for `REFRESH_DURATION_TICKS` ticks, giving a large margin while keeping the duration short enough that switching off feels instant. The short duration is also the key signal used to distinguish helmet-driven NV from external NV (see `hasExternalNightVision` in the packet handlers).


**[2]** Duration applied each refresh. Must stay well below the 280-tick threshold used to detect "external" night vision in the packet handlers, while also being large enough that a missed refresh doesn't blink the effect off.


**[3]** External NV preemption: if a long-duration NV effect appeared (potion or beacon), step back so we don't keep stomping the longer effect. Side-note: this also handles "player drank a potion mid-flight", the helmet politely yields and notifies the player.


## main/java/com/createstats/flight/block/FlightControlComputerBlock.java


**[1]** Block for the Flight Control Computer (FCC).

The `VisualState` property drives which block-model variant is displayed, so the screen texture changes with the actual flight mode without relying on time-based animation.


## main/java/com/createstats/flight/block/RpmGovernorBlock.java


**[1]** Flight Control Computer's actuator: a kinetic source the FCC commands with an exact signed RPM each tick. Inline along the kinetic line (shaft pokes out FACING and the opposite face) so it visually reads as a transmission element rather than a motor.

Sign convention: the BE applies `KineticBlockEntity.convertToDirection(rpm, FACING)` so a positive RPM always rotates "outward" along the FACING direction regardless of placement orientation.

Failsafe: if the FCC stops heart-beating for `GOVERNOR_FCC_MISSING_FAILSAFE_TICKS` ticks, the BE autonomously zeros its commanded RPM and releases ownership.

**VISUAL_STATE** property: cosmetic blockstate driven by the BE, IDLE when unowned or commanded RPM == 0, ACTIVE while the FCC is driving non-zero RPM. Used by the model variants (cyan dial-ring lit + amber LED row).


**[2]** Inline (double-sided) shaft: shaft attaches on FACING and on its opposite face, matching the Smart-Gearshift visual treatment. The kinetic source itself still outputs along FACING; the opposite face is a connection point only, Create's rotation propagator will detect any conflicting upstream source as overstress.


## main/java/com/createstats/flight/blockentity/FlightControlComputerBE.java


**[1]** Block entity for the Flight Control Computer. It is both the persistent configuration store and the server-tick control runtime for a craft: it holds NBT-persisted identity, the card-slot bindings, sensor links, the Devices/Groups/Controls/Routes workbench model, and the armed mode. Every server tick, when armed with controls present, it shapes player input, resolves each control's routes, and dispatches the results to hardware: RPM commands to linked `com.createstats.flight.blockentity.RpmGovernorBE`s, gyro tilt to Aeronautics bearings via `com.createstats.flight.bridge.SableBridge`, and signal/toggle/pulse outputs to redstone.

**UUID identity:** generated lazily on first server-side load if absent; persisted from then on. This UUID is what `com.createstats.flight.blockentity.RpmGovernorBE` uses for ownership in its `setCommand`/`heartbeat` contract.

**NBT schema versioning:** every compound is tagged with `FccDataVersion` so future format migrations remain possible; the diagram metadata carries its own `DiagramMetadataVersion` plus a one-time backfill migration for old saves.

**Transient telemetry:** live telemetry is intentionally NOT persisted. It is rebuilt every tick on the server and pushed to clients that have the GUI open or are subscribed (pinned HUD or a worn linked Pilot Helmet) via `FccTelemetryPacket` and `FccGuiSnapshotPacket`.


**[2]** Card-slot inventory, the SOLE source of truth for which propeller↔governor pairs are bound. Even slots = propeller cards, odd slots = governor cards. Slot index = row index, no compaction. (Phase 9 design: row-stable layout eliminates the row-shift bugs of a flat List model.)


**[3]** Live vehicle steering mode selected by the runtime this tick: -1 idle, else VehicleSteeringMode ordinal. Shipped in FccTelemetryPacket so the Operate/Helm LIVE badge shows the real running mode, not a GUI guess.


**[4]** Per-row last-bound governor pos so we can release ownership when a row's governor card is removed or replaced. `null` entry = nothing was bound in that row last refresh. Length = ROW_COUNT.


**[5]** Turn-capable floor: a heavy non-holonomic vehicle only yaws while it is rolling (yaw rate is roughly speed / turnRadius). The follower must keep at least this much speed through a turn instead of crawling to a stall, or the heading never comes around. Sized from the vehicle's own turning circle: v = sqrt(aLat * minTurnRadius), then clamped to a sane driving band.


**[6]** Returns the FCC's persistent UUID. Lazily generated and persisted if absent. Server-side only, clients should not rely on this value (not synced; not needed for any client-side logic in current scope).


**[7]** Recomputes `bindings` from the current contents of the card inventory.

For each row: read both card stacks, extract their linked positions (if the cards are linked), and emit a binding only when BOTH slots have a linked card AND both cards have non-null positions.

Also reconciles per-row governor ownership: if a row's previously-bound governor pos has changed (or that row no longer has a binding), release ownership on the old governor so it zeros RPM via the failsafe TTL contract.

BlockEntity-class validation (is the linked pos really a propeller / governor BE on the FCC's level?) is intentionally NOT done here, that check happens lazily during the server tick, because BEs may temporarily unload/reload around contraption assembly. The GUI surfaces "unresolved" badges for these rows.


**[8]** Dimension consistency: both card dims must match each other AND the FCC's level. Sub-level identity (same contraption) is checked at runtime during the server tick; we can't tell sub-level vs outer-world here without a class lookup.


**[9]** Mode safety gate: if the currently-selected mode lost its required sensors (validated through hysteresis), fall back to the safest mode that IS satisfied. GIMBAL gone → OFF. ALTITUDE/VELOCITY/NAV_TABLE gone → STANDBY (gimbal still ok). No-op if mode is OFF, already satisfied, or sensor discovery hasn't warmed up yet (suppressing avoids yanking a persisted active mode to OFF on the very first server tick after world load before chunks have streamed in). Plus: require the failure to persist for SENSOR_LOSS_HYSTERESIS_TICKS so that a single chunk-load / contraption-reassemble blip doesn't silently disarm the FCC.


**[10]** Tightest turn radius (blocks) the vehicle can hold, derived from wheelbase/track with a sane default and clamped to the same 3..8 driving range the planner smooths corners to, so the follower can actually track the paths the planner produces.


**[11]** Pure-pursuit steering for the Pathfinder route follower. The Pathfinder target is an elastic lookahead point a bounded distance ahead on the smoothed route, so the classic pure-pursuit curvature law kappa = 2 sin(alpha) / Ld applies directly. The math lives in the unit-tested PurePursuit helper. Nav Table steering is untouched (see the caller's source branch).


**[12]** The legacy err->crawl cap (below) slowed the vehicle to a stall exactly when a turn was needed, so it plowed nearly straight and the heading never came around: it spiralled away with the target bearing sweeping past it. For the Pathfinder follower we instead trust the planner's curvature speed (already sized for each corner) and, whenever we are not yet in the final approach, hold at least a turn-capable floor speed sized to the vehicle's own turning circle so it can actually rotate.


**[13]** getAppliedRpm() = governor's current ramped output value (what the wheel is actually being commanded to spin at, in RPM). We deliberately avoid getSpeed(), for governors on a Sable contraption the inherited KineticBlockEntity speed accessor often returns 0 because the kinetic network state is not propagated into world chunks.


**[14]** Ticks the current mode has been continuously unable to satisfy its sensor requirements. Demotion only fires once this exceeds the hysteresis threshold below, so a single chunk-load / contraption-rebuild blip cannot silently disarm the FCC.


**[15]** Server-side mode/param change request. Validates sensor capability and updates persisted state. If the requested mode is not satisfied by current sensors, the request is silently rejected (the next snapshot/telemetry round-trip will snap the GUI back to the actual state).


**[16]** Clears the current safety warning only if it still matches `expectedMsg`. This prevents accidentally wiping an unrelated warning that was set after the soft-cutoff warning (e.g. by anomaly detection in the same tick).


**[17]** Phase 14, Manual-override / stress-divergence handler. Switches to STANDBY (if available; falls through to OFF otherwise) and broadcasts a chat alert to the nearest player within 32 blocks. Throttled by SAFETY_ALERT_THROTTLE_TICKS to avoid spam if the player keeps fighting the FCC.


**[18]** Push a fresh snapshot to every player who has THIS FCC's menu open and to pinned HUD subscribers that are no longer inside the screen. offfffff sikicem aq Called on inventory change, sensor availability change, mode change.


**[19]** called by `FlightControlComputerBlock.onRemove` when the FCC's actually destroyed (NOT during contraption serialization). walks all bound governors and releases ownership so they zero RPM right away instead of waiting for the failsafe TTL. tolerates missing/unloaded BEs, the failsafe TTL handles those.


**[20]** Send initial snapshot directly to the opening player. The packet is sent after MenuProvider returns (i.e. after the OpenScreen packet), so the client screen will exist by the time it arrives.


**[21]** Delegated to SableBridge so the reach check is lenient when this BE is carried on a Sable / Aeronautics / Create Simulated ship sub-level, the BE's raw worldPosition stays at assembly coords and otherwise causes stillValid() to fail every tick ("screen flickers then disappears").


**[22]** v1 → v2 migration: legacy "Bindings" list is no longer the source of truth. Inventory cards take over; legacy data is silently dropped (player must re-bind by linking new cards). Logged once for visibility.


**[23]** v2→v3 sign convention change: SableBridge now negates raw getThrust() so that PropData.thrustSigned matches Sable's physical force direction. Any CommandK persisted under v<3 was learned with the inverted sign and would command the craft downward when climb is required. Drop stale calibration and force a recalibration; player will re-run the sweep.


## main/java/com/createstats/flight/blockentity/RpmGovernorBE.java


**[1]** Block entity for `RpmGovernorBlock`. Acts as an inline `SplitShaftBlockEntity`: the upstream kinetic network remains the source, while the FCC commands the output branch RPM through a signed speed modifier.

**Ownership model:**

- An FCC claims this Governor by being the first to call `setCommand` with its persistent UUID.

- Subsequent `setCommand` calls are accepted only from the same UUID, OR from a different UUID once the previous owner has stopped heart-beating (failsafe TTL elapsed).

- Heartbeats are identity-bearing, only the matching UUID resets the failsafe timer.

**Failsafe:** on world load and on FCC heartbeat loss, `commandedRpm` is zeroed. `commandedRpm` is intentionally NOT persisted to NBT, a reloaded world must wait for the FCC to reclaim and re-command before any thrust is produced. This avoids surprise movement on chunk load.


**[2]** Orient from the LIVE source facing first so the ratio always lands on the real output, even the instant the source flips to the opposite side (reverse drive). The cached facing is only a fallback for brief windows where no live source is assigned yet.


**[3]** Push cosmetic blockstate (IDLE / ACTIVE) so the model swaps to the cyan-lit / amber-LED variant when the FCC is driving non-zero RPM. Skipped on the client and on chunks that have no level.


## main/java/com/createstats/flight/bridge/HingeConstraintBridge.java


**[1]** Hinge constraint accessor, the WRITE side of Sable's native constraint solver, for the Aeronautics Hinge (v2).

Pure reflection against the full Sable engine (`dev.ryanhcode.sable.*`) so the mod compiles and loads with no Sable present. Every entry point is guarded by `isAvailable()`; when Sable is absent all methods are safe no-ops returning `null`/`false`.

Companion-side frame reads (pose, velocity, mass) stay in `SableBridge`; this class owns only the engine-side pipeline + constraint creation. Verified against `sable-neoforge-1.21.1-1.2.2`:

- `SubLevelPhysicsSystem.require(Level).getPipeline()` -> `PhysicsPipeline`

- `PhysicsPipeline.addConstraint(ServerSubLevel, ServerSubLevel, config)` -> handle

- `new RotaryConstraintConfiguration(pos1, pos2, normal1, normal2)` (all body-local)

- `PhysicsPipeline.applyImpulse(body, impulseWorld, pointWorld)` (body == ServerSubLevel)

- `SubLevelContainer.getContainer(ServerLevel).getAllSubLevels()` -> List&lt;ServerSubLevel&gt;

- `ServerSubLevel.logicalPose()` (frames) + `getUniqueId()` (persistence)


**[2]** Keep contacts ENABLED between the two hinged ships. Because the pivot now sits on the clicked seam edge (not inside the hulls), the hinge axis lies on the seam, so opening swings the bodies apart with no contact, while collision still gives a real "closed" stop and prevents the ships from passing through each other. (Disabling contacts let them free-spin and interpenetrate.)


**[3]** Apply a world-space impulse at a world-space point to a sub-level body. Used to pull two ships together: pass equal-and-opposite impulses to A and B so the lighter ship accelerates more.


**[4]** Run `action` with `entity` temporarily transformed into `serverSubLevel`'s local frame, so a normal block raycast/clip against the level hits that ship's blocks in local coordinates. Returns `true` if the local frame was actually pushed (i.e. the helper is available and the action ran inside it); `false` if unavailable (action not run). Always pops the frame, even on exception.


## main/java/com/createstats/flight/bridge/ReflectionAccessors.java


**[1]** Reflection lookup cache for the FCC bridge layer.

All lookups are cached by a string key. Misses are cached as `MISSING` sentinel so we don't re-scan classes that don't expose the member.

Class lookups are **passive** (`initialize=false`) for type checks, and **active** only when the caller needs to read a static field. This avoids triggering static initializers (which can cascade into NoClassDefFoundError on optional deps).

This helper is intentionally separate from `com.createstats.compat.SimulatedCompatHelper`, flight code stays decoupled so we can iterate without breaking the stats panel pipeline.


**[2]** Find the first public method named `name` on `owner` (including inherited methods via `getMethods()`) where each declared parameter type accepts an argument of the corresponding `argTypes[i]` class.

Use this when one or more parameter types come from an external classloader (e.g. Sable's `SubLevelAccess`) and we cannot construct a `Class[]` array up-front. Pass `null` in `argTypes` to match any type for that position.

Returns the first matching method with `setAccessible(true)` applied, or `null` if none found.


## main/java/com/createstats/flight/bridge/SableBridge.java


**[1]** Phase 3, Sable / Aeronautics data accessor layer.

Single source of truth for every read into Sable, sable-companion, Aeronautics, and Simulated. Pure reflection, the mod compiles and loads without any of those dependencies present. Each capability group bootstraps independently and logs once if unavailable. Capability groups

- **companion**, sub-level lookup + pose. Required for everything.

- **subLevelPhysics**, RigidBodyHandle (angular/linear velocity), gravity, MassTracker (mass + inertia tensor).

- **aeronauticsProps**, PropellerBearingBE, GyroscopicPropellerBearingBE.

- **navTable**, NavTableBlockEntity.

Frame conventions

- Reads from PropellerBE return data in **sub-level local** frame (Aeronautics' SmartPropeller stores thrust in the sublevel's logical frame , `subLevel.logicalPose().orientation().transformInverse(world)`). The bridge does NOT auto-rotate to world; callers do that explicitly via `bodyOrientation(Object)`.

- RigidBodyHandle linear/angular velocity is in **world** frame.

- MassTracker CoM is in **sub-level local** frame.

- Companion `logicalPose().position()` is the world-space pose anchor (normally world CoM). Do not treat it as a simple sub-level origin; use `localToWorld(Object,` for local block coordinates.

- Companion `logicalPose().rotationPoint()` is in **sub-level local** frame and is the point about which Sable rotates the structure.

Per-tick cache

Callers of expensive lookups should call `beginTick(long)` once per server tick so memoized values from prior ticks are evicted. Cache keys include world-pos coordinates so that multiple lookups on different points within one tick don't collide. Defensive copies

All Vector3d/Quaterniond/Quaternionf returns are **fresh instances**. Callers may mutate them freely.


**[2]** Use Sable.HELPER (same approach as SimulatedCompatHelper) instead of SableCompanion.INSTANCE. When sable-companion is JiJ'd into our mod AND into Sable itself, NeoForge can create two isolated class-loader copies of SableCompanion. Our copy's INSTANCE is never set by Sable's init code → SableCompanion.INSTANCE == null in our classloader. Sable.HELPER is a field on Sable's own class (always in Sable's classloader) and is always valid.


**[3]** Resolve getContaining(BlockEntity) from HELPER's actual runtime class. BlockEntity is from Minecraft's classloader, unambiguous across all mods. We find the method by matching param type compatibility, not by exact Class reference (avoids JiJ classloader split on any Sable overload types).


**[4]** Resolve the SubLevelAccess containing `be`, or `null` if `be` is not on a contraption sub-level (e.g. placed in the world directly).

Returned object is the raw `SubLevelAccess`, pass it back into other bridge methods. It is NOT defensive-copied (it's a long-lived handle).


**[5]** Shared `AbstractContainerMenu#stillValid` helper for any FCC-family BE whose block may be placed directly in the world OR carried on a Sable / Create Aeronautics / Create Simulated contraption ship.

**Bug history:** when an airship was "simulated" (Create Simulated converts the assembled contraption into a physics-driven ship), the FCC's right-click would open the screen for one frame and then close ("flickers then disappears"). Root cause: the BE's `worldPosition` stays pinned to the original assembly coordinates, but the visible block now floats wherever the ship has moved. A naive `player.distanceToSqr(getBlockPos().getCenter()) <= 64` check therefore fails every server tick, `stillValid` returns false, server force-closes the menu.

**Behaviour:**

- BE removed → false.

- BE on a sub-level (ship/contraption) we can resolve → project its local position to world space and compare against the player with a generous 32-block reach (1024 sqr), comfortably covers a player standing on the deck near the FCC block.

- BE on a sub-level we recognise but cannot project (NaN / missing pose) → trust the open. The player must have right-clicked the block to even reach this code, so refusing here would create the exact flicker bug we are fixing. Anti-grief is acceptable to lose on a ship the player is already physically on.

- Plain world block → keep the original `64` (8 block) check, matching vanilla container reach.


**[6]** Inertia tensor as a 9-element array in **JOML/column-major** layout: `[m00, m10, m20,  m01, m11, m21,  m02, m12, m22]`. For a physically valid (symmetric) inertia tensor the row-vs-column distinction is moot, but Phase 4 matrix code should treat it as column-major to match JOML conventions. Returns a fresh array; `null` if unavailable.


**[7]** Gravity vector in world frame, fresh copy. Primary path: `ServerSubLevel#getGravityVector()`. Fallback (dormant by default): `DimensionPhysicsData#getGravity(Level)` , enable by flipping `USE_DIMENSION_PHYSICS_GRAVITY_FALLBACK` to `true` if in-game testing reveals the primary path returns null.


**[8]** ===================================================================== OBJECT-BASED OVERLOADS (query a TARGET sub-level we only hold as Object)

The auto-aim code resolves an enemy contraption as the raw sub-level Object returned by SubLevelContainer#getAllSubLevels(). These overloads pull the same rigid-body physics (COM linear/angular velocity, gravity, mass) off that Object so we can forward-simulate the target. They unwrap to the underlying ServerSubLevel (the object exposing getMassTracker) when required. =====================================================================


**[9]** Resolve the underlying physics `ServerSubLevel` from an arbitrary sub-level handle. Returns the object itself if it already exposes `getMassTracker`; otherwise tries common accessor methods to unwrap it. `null` if not resolvable.


**[10]** Sable physics applies -getThrust() * airflowScaling * pressure as the actual force scalar along thrustDirection (see BlockEntityPropeller.getScaledThrust and BlockEntitySubLevelPropellerActor.applyForces in ryanhcode/sable). Negating here aligns PropData.thrustSigned with the physical force convention so downstream consumers (BMatrixBuilder, which feeds the GUI readiness diagnostic) get the correct sign. Using getScaledThrust() directly is unsafe, it includes airflow and pressure factors that collapse the gain at altitude/speed.


**[11]** Returns true if `be` is a Simulated GimbalSensorBlockEntity. Uses the resolved class token for a strict type check when available; falls back to Simulated registry-key matching when the class could not be resolved.


**[12]** Scan loaded chunks within `chunkRadius` of `centerPos` in `level` (FCC's level, for a Sable contraption this is the sub-level). Return the BlockPos of every BE that `beClass` `Class.isInstance(Object)` of.

Bounded scan keeps cost predictable on huge contraptions; default radius covers a 17×17 chunk window (~272 blocks across) which fits any practical airship.

Iterates only LOADED chunks (`getChunk(cx,cz,false)` returns null otherwise), matching the existing repo pattern in `ProductionDetectorBE`. This naturally handles partially-streamed sub-levels without throwing.

Returns `List.of()` on any unexpected error, never throws.


**[13]** Look up a BlockEntity at `pos` in `level` using the chunk source path.

Sable contraptions run inside LevelPlot chunks registered in the outer ServerLevel's chunk infrastructure. `Level.getBlockEntity(pos)` uses an internal `getChunkAt()` path that does not always serve plot chunks, but `getChunkSource().getChunk(cx, cz, false)` does, the same path used by `findBlocksOfType`. Use this method anywhere contraption BEs need to be looked up by stored position (governors, propellers, etc.).

Falls back gracefully to `null` on any error or unloaded chunk.


## main/java/com/createstats/flight/bridge/SubLevels.java


**[1]** Reflection helpers for enumerating and identifying Sable `ServerSubLevel`s (flying contraptions). All access is reflective so the mod still compiles and loads when Sable is absent, every method degrades to an empty/`null` result in that case.


## main/java/com/createstats/flight/client/FccClientState.java


**[1]** Both windows agree so there is no frozen gap between "stopped extrapolating" and "marked stale". The per-sample clamp in helmetTelemetryFrame() (never extrapolate further than one observed sample gap) remains the effective limiter at the normal 5 Hz cadence.


**[2]** Seconds since the last helmet telemetry packet arrived, or `POSITIVE_INFINITY` when no packet has been received for the current helmet link. Lets the HUD distinguish a live stream from a frozen one (server stopped sending) instead of displaying the last packet forever.


## main/java/com/createstats/flight/client/FccHud.java


**[1]** Flight Control Computer HUD widgets: artificial horizon, RPM bars, compass strip, telemetry mini-readouts. Pure draw helpers, no state. Caller passes theme colors so the widgets can adapt if a theme system is wired later.


**[2]** The old per-pixel drawAttitudeIndicator lived here, fully implemented but with zero call sites (its own comment flagged it as too expensive for a hot path without batching). It was deleted rather than left dormant; the helmet HUDs each draw their own attitude symbology.


**[3]** Horizontal commanded-vs-actual RPM bars, one row per binding (max 5). Commanded shows as a hollow outline; actual fills the bar. Color shifts to amber when actual deviates &gt;25% from commanded, red when offline (commanded != 0 but actual == 0 with binding present).


## main/java/com/createstats/flight/control/BMatrix.java


**[1]** Result of a B-matrix build pass. Carries the matrix itself plus the diagnostics needed by the prioritized force allocator (Phase 6) and failsafe (Phase 14).

All array fields are defensively copied; consumers receive snapshots and cannot mutate the underlying storage.


**[2]** Total absolute lift authority across all props at the given symmetric RPM limit. Equivalent to `maxRowAuthority(FY, maxAbsRpm)` when body-Y is gravity-up; otherwise prefer projecting along `-gravityUp_body` via row math.


**[3]** Conservative max authority along a single wrench row under symmetric, independent RPM box constraints `|rpm_i| ≤ maxAbsRpm`: `Σ |B[row, i]| * maxAbsRpm`. Uses the L1 sum of absolute coefficients, which is the correct upper bound for single-row authority when actuators can be commanded independently in either sign. (Row L2 norm, `rowNorms()`, under-estimates this for multi-prop rows.)


## main/java/com/createstats/flight/control/Matrix6xN.java


**[1]** Immutable column-major 6×N matrix used for the Flight Control Computer's control-effectiveness (B) matrix. Row indices are fixed by `FX`…`TZ`.

Storage layout: `data[col * 6 + row]`.

This class is intentionally minimal, only the operations needed by the B-matrix builder, the prioritized force allocator and diagnostics are exposed.


## main/java/com/createstats/flight/control/SensorDiscovery.java


**[1]** Sensor discovery, validation, and capability gating for the FCC. manual vs auto: the FCC can have a player-bound (manual) sensor position recorded in NBT, set via the GUI. when a manual position is set, it masks the auto-discovered position entirely, no fallback. to go back to auto, the player has to explicitly clear the manual binding through the GUI - keeps manual bindings predictable, the FCC won't silently switch to a different sensor if the user-chosen one gets removed. validation hysteresis: a single missed scan does NOT immediately mark a sensor unavailable, that'd cause mode flapping during chunk-load churn or contraption assembly. requires `MISS_THRESHOLD` consecutive failed scans before flagging a sensor as gone. initial scan grace period: on a freshly loaded FCC the `validated` set is empty until the first scan runs. to stop a persisted active mode getting yanked to OFF on tick #1 before any scan's even happened, the BE's mode-safety gate consults `isWarmedUp()` - suppressed while warming up. scan policy: auto-discovery runs every `SCAN_PERIOD_TICKS` ticks, scanning from `fcc.getLevel()` directly (its sub-level for in-contraption FCCs), chunk-bounded with radius `SCAN_CHUNK_RADIUS`. manual positions get revalidated every scan by directly reading the BE at that position, no full scan needed.


**[2]** Auto-discovery is skipped per sensor type when a manual binding is already set (manual masks auto). Using predicate matchers instead of raw Class<?> tokens so sensors are discoverable even when the Simulated class can't be resolved via reflection (falls back to registry-key matching in that case).


**[3]** Revalidate each requirement. For GIMBAL/ALTITUDE/VELOCITY, if Sable is active the physical-BE scan is bypassed entirely (handles both null pos and stale manually-set positions). NAV_TABLE always requires a physical block.


**[4]** Force the next `maybeScan` call to run immediately, bypassing cadence. Misses are pre-seeded to `MISS_THRESHOLD - 1` so that ONE forced scan immediately removes any sensor that is not found (instead of needing 3 more normal scans). Sensors that ARE found are still validated immediately (miss → 0).


**[5]** When Sable is providing this data natively, skip all physical-BE checks. This fixes both "pos=null (never auto-discovered)" and "pos≠null but BE not accessible in sub-level chunks" failure modes.


**[6]** Legacy single-class scan API. Retained because SableBridge.findBlocksOfType is also a public Bridge utility that other phases may use; the in-class scan path in maybeScan() bypasses this for efficiency.


**[7]** True once enough scans have completed that the hysteresis counters are meaningful. The mode-safety gate in `FlightControlComputerBE.serverTick()` should suppress mode-fallback while this returns false (e.g. on world reload before the first scan, or before chunks have streamed in around a freshly-loaded contraption). Returns true after `MISS_THRESHOLD + 1` scan ticks (`MISS_THRESHOLD`+1 × 1s by default).


## main/java/com/createstats/flight/FccBinding.java


**[1]** One propeller→governor pair. Both positions are in the host coordinate space of the FCC's owning level, when assembled into a contraption that is the sub-level local frame; before assembly it is the regular world frame. (Bindings should only be configurable while the FCC is in a sub-level, see Phase 2 acceptance test in plan.md.)


## main/java/com/createstats/flight/FlightConfig.java


**[1]** Configuration constants for the Flight Control Computer subsystem. Compile-time only; a few may eventually be exposed as server-side mod config. Constants tied to the removed control-loop, hover, calibration, and autonomous-navigation architecture have been pruned.


## main/java/com/createstats/flight/FlightMode.java


**[1]** NOTE: Legacy HOVER / HOLD_ALT / CLIMB / DESCEND modes were removed , they were artifacts of the original "smart-ESC" FCC concept that was abandoned. Old NBT saves with those mode names load as OFF via `safeValueOf(String,`.


## main/java/com/createstats/flight/hinge/HingeJoint.java


**[1]** One persistent hinge joint between two Aeronautics sub-levels.

Persistent fields (saved to `HingeJointManager` `SavedData`): the joint id, the two ships' stable sub-level ids, and the pivot point + hinge axis expressed in each ship's local frame. The live Sable constraint `handle` is transient, it is (re)created from these fields whenever both ships are loaded (see `HingeJointManager.ensureHandles`).


## main/java/com/createstats/flight/hinge/HingeJointManager.java


**[1]** Per-`ServerLevel` owner of all hinge joints, persisted via `SavedData`.

Stores the joint records (ship ids + local pivots/axes). The live Sable constraints are transient and (re)created by `ensureHandles(ServerLevel)` whenever both ships are loaded , called once per second from `CreateStats.onLevelTick`. Handles are torn down when a joint is removed or its ships disappear.


## main/java/com/createstats/flight/item/FlightCardData.java


**[1]** Shared link data carried by Propeller / Governor cards.

Holds the BlockPos that `useOn` captured at link time plus the dimension the card was linked in (used as a sanity gate when a card is placed in an FCC slot).

**Frame caveat (Phase 9 design note):** when the player right-clicks a block inside an Aeronautics contraption (a Sable sub-level) the `useOn` hit position may be either the OUTER world position (if Aeronautics doesn't forward sub-level coords through the vanilla raytrace) or the SUB-LEVEL local position (if it does). Phase 9 stores the raw clicked pos as-is. The FCC server-side derivation pass (`FlightControlComputerBE.refreshDerivedBindings`) tries the saved pos as if it were sub-level local first; if that fails to resolve to a valid BE on the FCC's own level, the row is reported "unresolved" in the GUI. Phase 10 GUI will add a fallback "scan and pick" picker that bypasses raycasting entirely.

**Encoding:** follows the existing `SafetyClutchLinkCardItem` pattern , lives under `DataComponents.CUSTOM_DATA` via the per-card tag key supplied by the card item class.


## main/java/com/createstats/flight/item/PropellerCardItem.java


**[1]** Propeller Card, slotted into the FCC's "propeller" slot of any binding row.

Linked by right-clicking an Aeronautics propeller bearing, wheel mount, propeller block, or Create bearing. Validation is intentionally reflection/name based so this item can load without hard imports from optional aircraft/bearing mods.

Sneak-right-click in air clears the link.


## main/java/com/createstats/flight/menu/FlightControlComputerMenu.java


**[1]** Phase 9 menu, exposes the FCC's card-slot inventory plus the player inventory.

Layout: `FlightControlComputerBE.ROW_COUNT` rows × 2 slots (propeller card + governor card), then 27 main inventory + 9 hotbar slots.

Slot type enforcement is done both at the `Slot.mayPlace` level (rejects shift-click + manual click insertion of wrong items) AND at the `ItemStackHandler.isItemValid` level inside the BE (rejects programmatic item movement). Out-of-row indices and unbound positions are tolerated and surfaced in the GUI as "unresolved" rather than blocked at insertion, players may legitimately temporarily desynchronize cards while reorganizing.


## main/java/com/createstats/flight/network/FccOverrideCommandPacket.java


**[1]** Client→Server override command packet for E-Manuel manual control. Sent when user toggles override fuse or adjusts RPM slider for a binding. Each binding row can be put into override mode, bypassing normal cockpit controls. Arrays are length-prefixed so they can vary per packet.


## main/java/com/createstats/flight/network/FccSensorBindPacket.java


**[1]** C→S packet: manually bind or clear a sensor position on the FCC.

- `pos == null`, clear the manual binding for `sensor`, falling back to auto-discovery. Also triggers an immediate forceScanNow().

- `pos != null`, set a manual binding. Server validates that the level matches and the position is loaded before accepting.

Passing `sensor == null` with `pos == null` is a "Rescan" request: clears all manual bindings is NOT done; only forceScanNow() is called.


## main/java/com/createstats/flight/network/FccTelemetryPacket.java


**[1]** Live telemetry packet (server→client, 200ms cadence) for the FCC GUI's avionics widgets and E-Manuel propeller diagnostics. Per-binding arrays are sized to the BE's binding row count and may be empty when no bindings exist.


## main/java/com/createstats/flight/network/FccWorkbenchActionPacket.java


**[1]** Minimal per-player flood guard. Normal use (including slider drags at a few Hz) stays well under this budget; it only trips on a runaway or malicious client spamming hundreds of actions/second.


## main/java/com/createstats/flight/NoseAxis.java


**[1]** Player-configured "forward" direction of a contraption, expressed as a body-frame cardinal axis. Used by GO N/S/E/W and NAV TARGET modes to map cardinal commands to body-frame thrust requests.


## main/java/com/createstats/flight/runtime/PurePursuit.java


**[1]** Pure-pursuit lateral controller math for the ground-vehicle autopilot, factored out as a pure function so it can be unit tested without a live world or block entity.

Given the heading error to a lookahead point a bounded distance ahead on the route, it returns a normalized steering command in [-1, 1] via the classic pure-pursuit curvature law `kappa = 2 sin(alpha) / Ld`, mapped to the steering input through the vehicle's tightest turn radius. Once the target passes 90 degrees off the nose (abeam or behind) it commands the tightest turn toward the target, because the raw sine term shrinks past 90 degrees and would under-steer exactly when the strongest correction is needed.


## main/java/com/createstats/flight/runtime/WheelMountSteeringOverrides.java


**[1]** Per-tick steering override registry for Offroad wheel mounts. FCC writes desired left/right steering signal strengths for a wheel mount position, and the wheel-mount mixin consumes the exact-position value during getSteeringSignal().


## main/java/com/createstats/flight/screen/FlightControlComputerScreen.java


**[1]** Helm instrument springs (critically damped, from the shared HudAnim toolkit) so the wheel, throttle lever, and trim needle move like physical instruments instead of teleporting when a value changes remotely or snaps from a template.


**[2]** Runs once when the screen first opens (guarded against re-init on window resize). A brand-new, unconfigured FCC always lands on the guided Setup/Starter flow; a configured one reopens at the per-session last-used view, falling back to Operate/Helm.


**[3]** Never tear down a widget the player is typing in or dragging. The rebuild is queued and re-run from render() once the interaction ends, so the suppressed update is not lost (the signature was already recorded above).


**[4]** True while the player is mid-drag on any slider (Test control sliders, Nav Table speed, Pathfinder smoothness, Linked Controller interval). Dragging changes the workbench value, which triggers a snapshot broadcast back to this client; without this guard onSnapshot would rebuild the view and recreate the very slider being dragged every ~0.02 units of travel.


**[5]** Console-wide caution: an active FCC warning flashes the outer frame (same shared pulse as the in-flight HUD panels), so a fault is visible no matter which tab is open.


**[6]** Surfaces the server-computed readiness bitflags (no bindings / control matrix / yaw authority / prop spin), sensor warm-up, and calibration gain count. When everything is clear it collapses to a single "Ready to fly" line rather than enumerating every satisfied condition.


**[7]** Animated signal-flow dots along a schematic wire (same 3-segment path drawSchematicWire uses), drawn only while the FCC is armed and the control is off-neutral. Dot speed and brightness scale with the deflection magnitude, so a nudge trickles and full lock streams.


**[8]** Live FLIGHT DATA instrument cluster for the airship helm, driven by the same 5 Hz telemetry stream the in-flight HUD uses: compass strip, ALT/CLM/SPD/ATT rows, sensor LEDs, and the vertical/horizontal thrust sums. Shows a NO SIGNAL state with dashes when telemetry is absent, never plausible frozen numbers. Reuses the FccHud draw helpers.


**[9]** Prefer the real steering mode the server's vehicle runtime picked this tick (shipped in telemetry). It is only trustworthy while armed; when disarmed the runtime does not run, so fall back to inferring the mode from the cockpit switch positions in the snapshot.


**[10]** Signed commanded activity for an airship layout zone in [-1, 1]: the sum, over every route whose target group maps to this zone, of the owning control's signed deflection oriented by the route's scale sign and invert flag. This is what the cockpit is COMMANDING right now; per-binding actual RPM cannot be mapped to zones client-side (ground zones use real telemetry instead, which IS per-zone).


**[11]** Live activity cue on a layout zone: a centre-anchored signed bar along the zone's top inside edge (green forward, amber reverse) plus an armed-gated glow border scaled by the activity magnitude. Disarmed-but-deflected draws only a dimmed bar: a preview of what arming would command, clearly distinct from a live zone.


**[12]** Notices are set from many call sites via plain assignment. Rather than touch each site, detect a change centrally each frame and auto-expire the footer notice after NOTICE_TTL_MS so a one-time confirmation ("Device rename sent") cannot mask the live status text indefinitely.


## main/java/com/createstats/flight/SensorRequirement.java


**[1]** Sensor capabilities a flight mode may require. Each `FlightMode` declares its required set; the GUI greys out modes whose requirements are not met by the currently linked / on-contraption sensor configuration.


## main/java/com/createstats/gui/BattleScreenDisplayScreen.java


**[1]** Returns a 0..1 muzzle-flash intensity that spikes to 1 when a weapon's shot/ammo count drops (a discharge) and decays over ~350ms. Pure client-side heuristic, no server signal needed.


## main/java/com/createstats/gui/DispatchBoardScreen.java


**[1]** Operations-first dispatch board screen. Presents a continuous layout with:

- Header bar: station name, berth state indicator, train info, completion summary

- Main body: scrollable dispatch rows with state-colored indicators and operator actions

- Bottom panels: cargo summary (left) + delivery history (right)

- Action bar: bulk operator buttons gated by canEdit permission

No tabs, the old three-tab duplication pattern is removed. Full manifest editing stays on the clipboard.


**[2]** The centerpiece: a live, plain-language electronic status banner. It states exactly what the station is doing right now (docked / loading / awaiting / complete), shows aggregate progress with an animated bar, and a directional cargo-flow lane so the activity is unmistakable at a glance.


## main/java/com/createstats/gui/DispatchUiFx.java


**[1]** Shared animation and "live readout" drawing primitives for the freight Dispatch Board and Manifest Clipboard screens.

Everything here is stateless and frame-rate independent: callers pass a monotonic animation clock (game-time + partialTick, in ticks) and a frame delta (also in ticks) so the same motion looks identical at 30 or 300 FPS. The goal is smoothness and clarity, never decoration for its own sake: every helper communicates a concrete state (progress, activity, liveness, motion).


**[2]** A live progress bar with a recessed track, eased fill, a soft top highlight, and (when `active`) a marching diagonal sheen so the player can instantly tell the bar is filling right now versus stalled.


## main/java/com/createstats/gui/FactoryManualScreen.java


**[1]** Draws a paper card hugging the bottom of the content area holding a fully word-wrapped paragraph (never truncated). Returns the card's top Y so the diagram can use the space above.


## main/java/com/createstats/gui/pdu/Ease.java


**[1]** The one place the PDU screen's animation lives: a critically damped approach that every eased value runs through, plus the reduced motion escape hatch.

Kept clear of any client only import on purpose, so the convergence behaviour is unit testable without a running game. The old screen eased with a bare `shown += (target - shown) * 0.2f`, which glides faster on a faster machine; scaling the step by the real frame time is what makes the same animation take the same wall clock time at 30 fps and at 300.


**[2]** Whether the player has asked for no motion. Read from the client config, guarded because that config is not loaded in a datagen or unit test JVM, and a screen that threw there would be worse than one that simply animates.


## main/java/com/createstats/gui/pdu/PduBalanceBar.java


**[1]** The balance strip: one horizontal bar of the branch's whole demand, split into tier bands from Critical on the left to Optional on the right, with a vertical rule at the point supply runs out. Everything to the right of the rule is the part being shed.

This is the widget that turns "why is that machine off" from a five row read into a glance. The bands are drawn in serve order, so a player sees directly that Critical and High fit inside supply and Normal spills past it. The served part of each band is its bright tier colour; the shed part is dimmed and hatched, so the cut survives at any GUI scale and for a colour blind player, and the rule is a hard non colour boundary on top of that.

Derived entirely from the roster's per tier draws, so it is meaningful in every mode, not only Active Auto: the rule sits where the served draw ends, which is the supply limit in Auto, the buffer's reach in Manual, and the full width in Off.


**[2]** The empty state, which has to tell two very different situations apart.

Nothing wired up and eighteen machines sitting idle both sum to zero draw, and calling both of them "no load on the branch" is how a panel loses a player's trust: they can see the machines. The device count is passed in for exactly this, so an idle branch says it is idle rather than denying that anything is connected.


## main/java/com/createstats/gui/pdu/PduCard.java


**[1]** A bordered surface with an optional heading. The screen composes its whole layout from these rather than filling rectangles inline, which is the specific habit that let the previous screen grow to 787 lines of interleaved geometry and colour.


**[2]** A section heading: uppercase, dim, and letterspaced by one pixel. The letterspacing is drawn character by character because the vanilla font has none, and a heading that is merely uppercase still reads as a shout rather than as a quiet label.


## main/java/com/createstats/gui/pdu/PduChart.java


**[1]** The history chart: supply as a filled area, demand as a line over it, and a shed event rail beneath.

The rail is the point of the whole view. A mark under every sample where a device was shed turns "I brown out sometimes" into "I brown out every time the crusher starts", which no instantaneous readout can show. The two series share one auto ranged y axis so the shape of supply against demand is legible whether the grid is carrying fifty FE per tick or five thousand.


## main/java/com/createstats/gui/pdu/PduChip.java


**[1]** A small pill carrying a short label in a tint: a tier name, a status word. Clickable where the screen wires it to an action, such as cycling a device's tier, and inert where it is only a badge.

Stateless by design. The label and colour are decided by the caller each frame from synced state, so there is nothing here to hold; the screen keeps whatever bounds it needs for hit testing.


## main/java/com/createstats/gui/pdu/PduDerivation.java


**[1]** The panel's working, shown. every number the Active Auto decision rests on, in the order it's actually computed, with the arithmetic linking one line to the next made explicit. shows the working because the most expensive failure this system ever had wasn't a machine going off - it was a player who couldn't tell if a machine was off because the panel decided so, or because the panel was lying. four totals with no visible relationship can't answer "why", and why is the only reason anyone opens this screen. pure, only from synced values - no `GuiGraphics`, no block entity, no config. every input is a field the server already synced, and that's not a style choice: ModConfiguration is COMMON, NeoForge loads it on both sides and never syncs it, so a client reading it straight reads its own file and can print numbers the server never actually used. anything not on the wire isn't shown, and where a term can't be broken out, it's derived from two that are.


**[2]** The same working with the explanatory notes stripped, for when the pane is too short for them.

The notes are what make the numbers teachable, so they go last rather than first, but a derivation that runs off the bottom of the card is worse than a terse one: the verdict is the most important line and it is the one at the end. Dropping notes roughly halves the height and loses no number.


**[3]** The reserve's contribution is not synced on its own, so it is recovered from two values that are. Showing it matters: it is the difference between "the branch is living off generation" and "the branch is quietly eating its buffer", which look identical from any single number.


## main/java/com/createstats/gui/pdu/PduDiagram.java


**[1]** The one line diagram: the branch drawn top down from the PDU, in the style of a single line schematic rather than a map.

Wire, cable and connector runs are conduit, not decisions, so they collapse into the bus edges between the nodes a player actually reasons about: the PDU, the switches, and the loads. A thick edge is a Cable Tray run, a thin one is Surface Cable, a dashed one leads to a shed device, and a switch draws as the break it is when open. Clicking a device cross highlights its row in the table, which is why this holds each node's hit box.


**[2]** Collapse each meaningful node to its nearest meaningful ancestor, remembering whether the collapsed run passed through a tray (a thicker bus). Parents precede children in the list, so one forward pass settles both the logical parent and the depth.


**[3]** Either heavy grade draws a heavy edge. What the line is saying is "this leg was built for load", and a Bus Duct says that more emphatically than a Tray, not less.


## main/java/com/createstats/gui/pdu/PduDrawer.java


**[1]** The inspector: a right hand drawer that slides in over the table when a single device is selected, and gives that device explicit controls rather than the cycle by one the row offers.

Explicit on purpose. Five tiers make a cycle control tedious, so the drawer lays all five out as buttons and lets a player jump straight to the one they want; likewise the three locks. It overlays the table rather than reflowing it, because a list that reshuffled every time you clicked a row would be disorienting.


## main/java/com/createstats/gui/pdu/PduGauge.java


**[1]** A horizontal fill gauge with a marked threshold notch, for the buffer card.

The notch is the whole point. The reserve floor is the number that decides shedding, and it has been invisible to the player until now; a bright downward marker over the bar puts it exactly where it bites, so "the buffer is above or below the floor" becomes a glance rather than arithmetic.


## main/java/com/createstats/gui/pdu/PduGlyphs.java


**[1]** The small stateful glyphs the table and inspector share: the status pip and the lock padlock, plus the tier colour ramp. Kept in one place so a row, a bulk bar and the inspector all draw the same shapes rather than three slightly different ones.

Every glyph carries its state by shape as much as by colour, because at GUI scale 1 the colour of a single pixel cluster is unreliable and to a colour blind player it is nothing.


**[2]** The status pip: filled when running, hollow when shed, a bar when switched off, split while the anti-flap timer holds, and `INFO` filled when pinned on. Returns the pip colour so the row's reason text can be drawn to match.


**[3]** The tier colour ramp, hottest at the top. CRITICAL borrows the crit red not as an alarm but as "this is the one you protect"; OPTIONAL fades toward faint because it is the first to go.


## main/java/com/createstats/gui/pdu/PduHistory.java


**[1]** Fixed-size 3-series history ring: supply/demand (short FE/t) + shed count, per sample. backs each range button (1m/5m/15m) at its own resolution instead of downsampling on read. pure, shared by both sides - server records every few ticks, client fills from a snapshot then appends deltas. split out mostly so the wraparound math is unit tested (an off-by-one here is invisible just by looking at it).

**FE per tick is stored as a short, so anything above `MAX_FE` clamps to it.** A branch carrying 32767 FE/t is far past anything the mod's generation can reach, so the clamp is a storage guard, not a real limit; it is asserted in the tests so it cannot silently regress into a negative short from an overflow.


## main/java/com/createstats/gui/pdu/PduIcons.java


**[1]** The five view glyphs for the left rail, drawn from fills rather than loaded from a texture sheet.

The spec suggested a 16 px icon sheet. Drawn glyphs won here for three reasons: they scale with the breakpoint instead of pinning one pixel size, they need no atlas registration or resource plumbing to go wrong, and the rest of the screen is already drawn rather than textured, so a lone sheet would be the one thing that could fail to load. Each glyph fits a 16 by 16 box at `(x, y)` and takes a single tint, so the rail can recolour it for hover and active.


## main/java/com/createstats/gui/pdu/PduLegRow.java


**[1]** One output leg as the panel presents it: what it is carrying, against what it can carry, and what its breaker is doing. pure, since the interesting part is the wording - almost nothing here is geometry. it's the decision about **what a leg's state is called**, and that's the whole reason this panel's worth opening: a leg that's off because its breaker tripped, a leg that's off because you opened it, a leg that's full, and a leg with nothing wired to it look identical from any single number, and call for four completely different responses. getting one of those labels wrong sends a player to rewire something that was never the problem, and that's not the sort of mistake a screenshot catches.


**[2]** Decides a leg's state.

Order is load bearing and is by **how much the player needs to know it**, not by how the fields happen to be laid out. Unwired first, because a stud with nothing on it must never be dressed up as a fault; then tripped, because it is the only state that happened on its own and needs walking to; then open, which the player did deliberately; then capacity, then healthy.


**[3]** The one line under a leg's name, which has to say the useful thing rather than the obvious one.

A leg at capacity says what to do about it, because "at capacity" on its own invites exactly the wrong fix: this is the state where more generation cannot help and only a fatter wire or a second leg will.


**[4]** A leg's name. One based, because the studs are things a player points at, not array indices.

The back boss is named rather than numbered. It is not the ninth of anything: it is the one cable output on a board whose other eight are wire, and calling it "Leg 9" would file the one row a player has to treat differently as more of the same.


## main/java/com/createstats/gui/pdu/PduRail.java


**[1]** The left icon rail: a permanent home for each view with an active indicator that slides between them. A rail rather than a top tab strip because it scales to more views without crowding the header, and it leaves the full width of the panel for content.

The active accent bar eases to the selected item rather than jumping, which is the cheap motion cue that makes the switch read as one control moving rather than two lamps blinking. Item tops are cached from `draw` for hit testing, the same draw then test pattern the rest of the screen uses.


## main/java/com/createstats/gui/pdu/PduRect.java


**[1]** An integer rectangle, the currency of the pdu widgets. A record rather than four loose ints because the old screen passed geometry around as `int[]` boxes, and a swapped index there is a bug you only find by clicking the wrong thing; a named field is checked by the compiler.

Containment is half open, `[x, x + w)` by `[y, y + h)`, so two rectangles sharing an edge never both claim the same pixel and a click on a seam resolves to exactly one control.


## main/java/com/createstats/gui/pdu/PduRing.java


**[1]** A fixed size rolling buffer of floats, oldest overwritten first. The live sparklines on the OVERVIEW cards are fed from these: the screen samples the already synced supply and demand once per client tick while it is open, which is all a live sparkline ever is.

Deliberately client side and short lived. It exists only while the panel is open and is not synced or saved; the server side history buffer in a later phase is what gives the full chart its persistence across a close and reopen. Kept pure so the wrap around indexing, which is the one thing here that is easy to get subtly wrong, is unit tested without a screen.


## main/java/com/createstats/gui/pdu/PduScene3D.java


**[1]** The branch rendered as real blocks in a real 3D viewport, not as drawn boxes. replaced the drawn version - the first attempt projected the branch isometrically and drew each block as three shaded rhombuses. cheap and looked it: a wall of identical flat lozenges where you couldn't tell a Power Connector from an Induction Range, defeating the whole point of showing the layout. this mod already renders real block models inside a GUI for the Battle Tactical viewport, so a machine here looks like the machine it is, own texture, own shape.

three things go wrong doing this:

- **stale depth.** the depth buffer still holds values from the frame's world pass, so with depth testing on, only blocks in front of last frame's terrain survive, and which ones those are changes every frame - looks like the model's flickering apart. clearing inside the scissor fixes it, `glClear` honours the scissor test so it only touches this viewport.

- **whatever texture the GUI left bound.** skip binding the block atlas first and the first block renders with the font atlas or GUI sprite sheet and comes out scrambled.

- **float precision at distance.** a branch at world coordinates in the millions loses more than a block of precision in a float matrix, adjacent blocks collapse onto each other. centroid gets subtracted in double precision before anything reaches the matrix.

all three are the same lessons the Battle Tactical viewport learned first.


**[2]** Tick based, not frame based, so the spin is the same speed at 30 fps and at 500, and it holds still when the game is paused rather than running on without the world.


**[3]** Runs the real block entity renderer for this position, if it has one. not optional - half the wiring in this mod has no baked model at all. Surface Cable's model file is a bare parent with a particle texture and nothing else; every visible part of it gets drawn procedurally by `CableRenderer` from the segment's own link state, same for the strands a Power Connector hangs. the first version of this scene drew the machines and left the wiring between them invisible, which is a strange thing for a diagram whose whole subject is what's wired to what.

the **real** block entity is used instead of a fresh instance of the right type (which is what Battle Tactical's viewport has to do, since its blocks are a recorded snapshot, not blocks in the world). here they're actually present and loaded a few metres from the player at the panel, and a cable's look is entirely a function of which neighbours it links to - a blank instance would draw a cable connected to nothing.

Any renderer that needs more context than a GUI can give is skipped rather than allowed to take down the screen. The baked model, where there is one, has already been drawn.


**[4]** A renderer that wants a camera, a frustum or a neighbour scan it cannot have in here is dropped quietly. Losing one block's detail is a far better outcome than losing the screen.


## main/java/com/createstats/gui/pdu/PduSearchField.java


**[1]** A single line text input with a placeholder, a clear button, and a focus ring, for filtering the device table live as the player types.

Deliberately a search box and not a general editor: the caret stays at the end, which is all a filter needs, and it keeps the widget small and its behaviour obvious. The spec's keyboard map reaches it with `/` and empties it with `Esc`; both are the screen's to route, the field only owns the text and its own clear button.


**[2]** A magnifier glyph from fills rather than an icon, so an empty field reads as "search" and not as a nameless box. One ring and a short tail is enough to be unmistakable at this size.


## main/java/com/createstats/gui/pdu/PduSegmented.java


**[1]** A segmented control: a strip of adjacent options with one active, for the LOADS toolbar's group by and sort choices. Holds only which segment is active; what a click means, whether it selects or reverses an already active sort, is the screen's decision, so this reports the segment and stops there.

The segment layout is cached from the last `draw` and reused for hit testing, the same draw then test pattern the roster rows already use, so a click resolves against exactly the pixels that were drawn rather than a recomputed guess that could disagree by a character's width.


## main/java/com/createstats/gui/pdu/PduSparkline.java


**[1]** A small line chart of a `PduRing`, as little as 40 by 12 px, for the SUPPLY and DEMAND cards. The y axis auto ranges to the largest sample held, so a quiet grid and a busy one both fill the height rather than one drawing a flat line along the floor.


## main/java/com/createstats/gui/pdu/PduStatCard.java


**[1]** A stat card: a heading, a prominent value with its unit, an optional sparkline, and a subtitle.

The value is drawn bold rather than scaled. The panel's font is fixed at 9 px by decision, so "big number" here means bold and bright against dim surroundings, not a larger glyph; that keeps one type size across the whole screen and still lands the eye on the number first.


## main/java/com/createstats/gui/pdu/PduStatusLine.java


**[1]** The plain language status line: everything the grid is doing collapsed to one sentence a player can act on. Pure, so every branch is unit tested, including the two the old screen got wrong by printing numbers that mean nothing in that state: distribution off, and no branch wired at all.

The branches are ordered by urgency, so the most important true thing is what gets said. The one that earns its place is the wire limit: `lastThroughputLimited()` has existed all along and a player had no way to see it, so a grid capped by its trunk connector looked exactly like one short of generation, and the fix for those two is opposite.


**[2]** The endurance readout for the buffer card: how long the reserve lasts, or that it is charging or holding steady. Charging is inferred from surplus supply plus a buffer that is not already full, because there is no separate charging flag synced.


## main/java/com/createstats/gui/pdu/PduTable.java


**[1]** The device table: a virtualised list with sticky group headers, a selection model, full keyboard navigation, and an auto hiding scrollbar. The grouping, sorting and filtering are the pure `PduTableModel`'s job; this owns everything that needs a screen, which is layout, drawing, selection and input.

Virtualised in earnest. The model is rebuilt only when the roster or the controls change, so there is no per frame allocation of the row list, and every frame draws only the rows inside the viewport plus a couple either side. A two hundred device branch costs the same to draw as a ten device one, which the tier lane list it replaces did not.

State is keyed on `BlockPos`, not on row index, because a sync rebuilds the row list and a device can change position in it; a cursor stored as an index would jump to whatever landed under it. Keyed on the device, the cursor and selection survive a resort.


**[2]** A conductor coloured pip before the name, so a row ties back to the strand in the world and to the same colour on the Legs view and the Flow diagram without costing a column.


**[3]** Tier chip and lock icon act on the row without changing the selection. Left click cycles forward, right click back, so a player can nudge either way in place without opening the drawer. The lock target is padded a little because its glyph is only a few pixels wide.


**[4]** The devices a tier or lock key should act on: the whole selection when more than one row is selected, otherwise just the row under the cursor. This is what makes bulk edits reachable from the keyboard, not only from the bulk bar.


## main/java/com/createstats/gui/pdu/PduTableModel.java


**[1]** The pure logic behind the LOADS table: filter a roster, group it, sort within each group, and flatten the result into an ordered list of header and device rows the table can virtualise over.

Pure on purpose. It takes `BranchDevice` records and the mod's own power enums in, and gives rows out, with no Minecraft rendering type anywhere near it, so every one of grouping, sorting, the filter predicate and sort stability is unit tested without a running game. This is the class most likely to be quietly wrong, a sort that is not stable or a group that reshuffles under the player, and those are exactly the bugs a test catches and an eye does not.

Group order is fixed per mode so the headers never dance: tiers in serve order, statuses attention first, types alphabetically. Within a group the chosen sort applies, reversible.


**[2]** A device's state, collapsed to the four things a player acts on, ordered attention first so a status grouping or sort surfaces the sheds rather than burying them under the healthy majority.


**[3]** The filter with a tier restriction on top, for the OVERVIEW tier roll call clicking through to LOADS narrowed to one tier. A null tier means all tiers, so the plain overload is this with no restriction.


**[4]** Matched against the label rather than the bare name, so typing "id:07" finds one machine out of fifteen identical ones. Searching for the name alone still works; the label starts with it.


**[5]** The default direction of each sort key, chosen so the un-reversed click is the useful one: biggest draw first, names A to Z, status attention first. The reverse flag flips whichever of these the player picked. Equal keys return zero, so the stable sort leaves them in roster order.


## main/java/com/createstats/gui/pdu/PduTheme.java


**[1]** The single source of truth for the Power Distribution Unit screen's visual language: every colour, every metric, and the responsive breakpoints. Nothing else in the `pdu` package hard codes a colour or a padding; they all come from here.

Why a theme class at all. The screen this replaces carried two dozen colour literals scattered through 787 lines, several of them subtly different greys that were meant to be the same grey. Pulling them here makes a palette change one edit, and it makes `STROKE` read as a decision rather than as `0xFF2E3742` appearing in nine places with one of them a shade off.


**[2]** palette Dark instrument, warm brass and copper accents, no saturated primaries, matching the mod's existing power art. Each colour has exactly one job. The old screen used amber for three different states and green for two, and telling those apart is precisely what these names fix.


**[3]** Three panel sizes, chosen by the space the window can spare, exactly like a responsive web layout. The screen picks one in `init` and clamps it to the window, so a small window at GUI scale 4 still gets a COMPACT panel shrunk to fit rather than a NORMAL panel drawn off the edge.


**[4]** Keep at least this clear around the panel at each edge, so the chrome never touches the window border. Under NORMAL's needs, COMPACT is chosen and the screen clamps it further.


**[5]** The keyboard focus ring: `STROKE_HOT`, 1 px, drawn 1 px outside the control so it reads as focus without shifting the control's own border. Never lean on hover for this; a player on the keyboard has no cursor to hover with.


## main/java/com/createstats/gui/pdu/PduToast.java


**[1]** Transient confirmations, stacked bottom right of the panel: a copied position, a committed action, a "do it again to confirm" prompt. Each slides up and fades out on its own, so a burst of actions reads as a short stack rather than a wall.

Slide and fade both run through `Ease`, so with reduced motion on they simply appear and vanish. The stack is capped, oldest dismissed first, because a toast is a note in passing, never a log.


## main/java/com/createstats/gui/pdu/PduTopology.java


**[1]** The pure spanning tree behind the WIRING diagram. Given the branch's wiring nodes as gathered from the world, it walks a breadth first tree from the PDU outward and hands back a flat list with each node's parent, capped, with a truncation flag.

Pure and tested, because a topology bug is a diagram with a device hung under the wrong branch, which reads as plausible and is invisible to the eye. Adjacency is two kinds, matching the grid itself: the six face neighbours for anything that physically touches, and a connector's explicit wire links, which cross open air. A node the walk never reaches is simply left out, which is the honest thing: it is not on this branch.


**[2]** The kinds a wiring node can be. The ordinal is the `byte kind` carried on the wire.

Appended to, never reordered: the ordinal travels in the update tag, so moving one would silently redraw every saved panel's diagram as the wrong shapes.


**[3]** Builds the spanning tree rooted at `rootPos`. Nodes come out in breadth first order, so a parent is always earlier in the list than its children, and `parentIndex` refers to that output position. If the root is not among the raws, or there are none, the result is empty.


## main/java/com/createstats/gui/PilotHelmetScreen.java


**[1]** NIGHT VISION: ON/OFF toggle + classic-vs-tactical visualization mode. Both buttons sit in one row directly under INTERFACE SCALE. The toggle label embeds the current keybind text so players always see which key flips NV without opening the Controls screen.


**[2]** Override renderBackground as a no-op so the vanilla blur shader (called via Screen.render's super chain in MC 1.21) never runs. This is the same pattern used by BattleScreenDisplayScreen / DamageObserverScreen / StatsHandheldScreen.


## main/java/com/createstats/gui/PowerDistributionUnitScreen.java


**[1]** The Power Distribution Unit control surface.

A permanent left rail carries the five views, a header names the unit and its live mode and totals, a footer states the grid's health in one line and lists the shortcuts. The LOADS and LIGHTING views are the device table: a toolbar of search, group, sort and a shed filter above a virtualised `PduTable`, an inspector `PduDrawer` that slides in over the table when one device is selected, and a bulk bar that replaces the toolbar when several are. OVERVIEW, WIRING and HISTORY arrive in later phases and show a calm placeholder until then.

Every action is reachable from the keyboard, with a visible focus ring on each focusable control. The screen is a plain `Screen`; there is no inventory to draw.


**[2]** Lighting is a view, not a table filter, so its filtered list is cached and only rebuilt on a sync; passing a fresh list every frame would make the table rebuild every frame.


**[3]** HISTORY: a client copy of the currently watched range, filled from the server's full send and extended by its deltas, plus the subscription bookkeeping. History lives on the server, so this is only a view of it; closing and reopening simply re-requests it.


**[4]** Sample the already synced supply and demand into the client side rings for the sparklines. Supply is the generation capacity in Active Auto, so a full buffer that draws nothing does not read as zero supply.


**[5]** An empty roster is a branch state, not a search miss; the table's own empty message covers the latter, so the branch states are handled here before the table would draw.


**[6]** supply reads as generation capacity in Auto, actual draw as the subtitle - a full buffer draws nothing so measured intake alone would sit at zero and look broken. branch actually dark = card goes CRIT, the one place a stat card carries an alarm.


**[7]** The buffer card carries the gauge rather than a sparkline, because the reserve floor notch on it is the whole reason this card exists: the floor is the number that decides shedding and it has been invisible until now.


**[8]** The Active Auto telemetry is meaningless in the other modes, so it is only fed to the status line in Auto; elsewhere it is passed as neutral so the derivation cannot print a stale number.


**[9]** Scrub: over the plot the header freezes on that sample; off it, it snaps straight back to live. No ease, because a number that drifts back while you move the mouse away reads as a bug.


**[10]** Two different counts, and both are worth a line: the wires leaving the panel are what the player strung, the conductors are what the branch turned out to be made of once each wire landed and whatever it landed on carried on.


**[11]** The eight output legs, one row each.

This is the view the eight studs earn. Everywhere else in the panel a leg is a column on something else; here it is the subject, because the questions a player actually arrives with are about a leg and not about a device: which circuit tripped, which one is full, which one has room for the machine I am about to build.


**[12]** Nine rows have to fit whatever panel size the window allowed, so the row shrinks before the page overflows. Below about two dozen pixels the detail line is dropped rather than drawn over the row under it: a leg's name, its state and its breaker are the part you cannot lose.


**[13]** The conductor colour as a solid tab down the left edge, which is what ties this row to the strand you can see in the world and to the same colour on the Flow diagram.


**[14]** The branch as it actually stands, in 3D, beside the working behind the decision. two panes, two questions - the scene answers "what's happening and where" (real block models at real positions, so a shed machine is a thing you can recognise and walk to, not a row in a list), the working answers "why" (every number the controller used, and the arithmetic between them). on crowding: first version put a three line legend, a truncation warning and a fully annotated derivation on top of the scene, and the result was unreadable. rule now is the scene carries no text at all except one hint line under it, and the working drops its explanatory notes the moment they don't fit. legible and partial beats complete and illegible, every time.


**[15]** The panel is the root of the branch and the thing a player orients by, so it belongs in the scene whether or not the topology walk happened to list it.


**[16]** Wires on the studs, not a non-empty topology: the topology always contains the panel itself, so it was never empty and the unwired verdict could never be reached. A wire is the thing whose absence the verdict is about.


**[17]** Measure before drawing. The verdict is the most valuable line and it is last, so a working that overflows loses exactly the wrong end of itself. Dropping the notes buys back about half the height and costs no number, which is a far better trade than truncating.


**[18]** The single chokepoint every lock request runs through, so the confirm on locking a Critical device off is enforced no matter which control asked. Arming leaves the lock unchanged, so the repeat that confirms recomputes the same target and commits it.


**[19]** Ctrl+A selects the whole visible table, so a keyboard user can reach the bulk edit and the lighting master without clicking every row. Plain A stays the assign shortcut below.


## main/java/com/createstats/gui/StageLightScreen.java


**[1]** Control screen for the Stage Light. Works on the ground and on Aeronautics ships (opened by right-clicking the block). Four draggable sliders, Pan, Tilt, Range, Brightness, plus an on/off button. Changes are sent to the server, which drives the projected light.

The Range slider is annotated to make the rule clear: full brightness holds out to the peak range, then the beam reaches further but dims and spreads over a larger area.


**[2]** Live schematic of this fixture's beam, mirroring the Battle Screen lighting diagram. Reads the screen's live slider fields so the cone (Beam Width) and ground pool (Light Spread) update as you drag. All geometry is clamped to the panel so wide tilt/spread never bleeds outside.


## main/java/com/createstats/item/CableItem.java


**[1]** The cable item, and the Cable Router that lays a run with it.

the gesture, in full:

- **click a surface** to anchor a run there. one cable goes down so you can see where you started, and from then on the ghost at your cursor is the route the run will take.

- **click again** anywhere in reach and the whole route lays at once, corners and all. far end becomes the new anchor, so a long run is a series of aims, not one impossible sight line.

- **click the machine** you're wiring and the run finishes there - last cable lands against it, powers it, router lets go.

- **sneak while running** to drop the run and keep what you've laid.

- **sneak-click an existing run**, no run in progress, to **rip it up** - every block on that circuit comes back to you at once.

anchor's the only state kept between clicks. old tool also held the output leg the run belonged to, and that was the source of every confusing thing cable ever did - the leg outlived the run and silently re-assigned ordinary cable placed minutes later. cable has no legs now. a circuit is a colour, visible right on the block, and the router holds nothing but where you started. all of it's server owned - client predicts success so the two sides never race, and the ghost comes from the same router the server lays from.


**[2]** Not running: this click starts one. Placing the first block here rather than merely remembering the spot is what makes the mode visible, and it means a single click on a wall is still just "put a cable there", exactly as it always was.


**[3]** Sneak: drop the run in progress, or rip up the run you clicked.

Two meanings for one gesture, decided by whether a run is in progress, and they never overlap in practice: you are either laying cable or looking at some. Cancelling wins when both could apply, because a player mid-run who sneaks is asking to stop, not to destroy the wiring they are standing next to.


**[4]** Takes a whole circuit back into the player's hands.

The run is gathered by `CableRun`, which walks the same joins the grid conducts along, so what comes up is exactly the circuit that was working: a run of another colour lying beside it is a different circuit and is left alone, which is the whole point of colour.

Blocks are dropped as items rather than put straight into the inventory, and dropped at the player rather than where they lay. A hundred cable falling in a corridor is a hundred entities to walk over; at the player's feet they are picked up on the spot.


**[5]** Each face is a block's worth of cable, so a corner position that carries two gives two back. Charging for both and refunding one would be a slow leak in the player's favour or against it, depending on which way they built.


**[6]** Anchors a run and lays its first block.

Clicking an existing cable picks that run up instead of placing a second one on top of it, which is how a run left on a wall yesterday is carried on today. Anywhere else, the click is an ordinary placement that happens to also become the anchor.


**[7]** Where the block actually landed. Usually one step out from the face that was clicked, but a click on something replaceable (tall grass, snow) puts it in that block's own position instead. Anchoring on the wrong one of those two would start the next route from a step that does not exist, and the router would refuse a run the player could see was possible.


**[8]** Runs the line on to where the player pointed. Reaching a machine finishes the line and powers it; reaching a plain surface lays cable and leaves the end there, so the next click carries on.


**[9]** Reaching a machine is the natural end of a run: the last cable is touching it and feeding it, so the run finishes here. Any other click leaves the end where it is, so the next click carries straight on from there.


**[10]** The colour a continued run is laid in: whatever the anchor is.

A run that changed colour half way along would be two circuits with one shape, and the break would be invisible until something downstream went dark. So the anchor's colour carries, and recolouring is a deliberate act with a dye.


**[11]** True when this block is something a run should terminate on: a machine or fitting that takes power, as opposed to more cable, which is the wiring rather than its end.


**[12]** Single placement puts cable on the face that was clicked.

`BlockItem` would otherwise ask the block for a state without knowing the click, and the cable's whole identity is which face it is stuck to.


## main/java/com/createstats/item/CopperWireItem.java


**[1]** Copper Wire spans terminals: right-click a first terminal to attach the wire, then a second within range to complete the link (one wire consumed per link). Sneak right-click a terminal to strip every wire off it (the wires drop back). Mirrors the pending two-click flow of the Hinge item. two kinds of terminal: a Power Connector's one. each of the eight output studs on a Power Distribution Unit is another, and that's how a panel's legs get wired now - a wire from stud N to a machine's connector puts that machine on leg N, under leg N's breaker. gesture's deliberately the same one, since from the player's side it's the same act, and the alternative was a second wiring tool with its own rules for the one block that most needs to be easy to wire.

Everything is decided server side. `PduStuds.pick` resolves which stud a click landed on and `PduStudWiring.check` judges the span, both pure and both tested, so the item here is only the gesture and the messages.


**[2]** A click on the panel: resolve which stud, then run the same two-click flow.

Only the bus bar on top is a set of terminals. A click on any other face is not an imprecise aim at a stud, it is a click on the block, so it passes rather than guessing.


**[3]** A connector's link list holds panel positions as well as connector ones, so one containment test answers both shapes of span: already wired to that connector, or already wired to that panel. One wire per panel per connector, so the position is enough to say.


## main/java/com/createstats/item/HingeItem.java


**[1]** The Aeronautics Hinge, a small joint you place between two flying contraptions.

Two-click flow (right-click while looking at a ship):

- First click on ship A → stores anchor A and the swing axis.

- Second click on ship B → creates a rotary hinge. The Sable constraint pulls the two pivot points together, so the ships draw in toward the shared pivot and then swing on the axis.

Axis control:

- Normal right-click → **door** swing (vertical axis).

- Sneak + right-click on a ship → **fold** swing (horizontal axis, perpendicular to the line between the two ships).

- Sneak + right-click on empty air → cancel the pending anchor.

Pivot picking is precise: the player is briefly transformed into each ship's local frame so a normal block raycast lands on the exact edge block being aimed at; that clicked point becomes the ship's anchor. If the precise raycast misses (sloppy aim), it falls back to an aim-based pick against the ship's centre so the feature still works.


**[2]** Removal is handled FIRST and unconditionally: sneak + aim at a ship that has a hinge tears it down, even if a half-finished link is pending. Uses the reliable aim-based pick so it never depends on a precise block raycast.


**[3]** SHARED pivot at the seam (midpoint of the two clicked points), expressed in each ship's own local frame. Because both anchors map back to the SAME world point at creation, the joint starts already satisfied -> there is NO violent pull and NO forced hull overlap. (Offset anchors made the rigid joint either fling the ships across the world or grind them into each other.) The two ships hinge cleanly about the seam between them; place them adjacent for a flush edge-to-edge hinge.


**[4]** Precise pick: inside each ship's local frame (via `withEntityLocal`, which redirects block lookups to that ship), run a normal block raycast along the player's look so the hit lands on the exact edge block aimed at, then convert the hit back to world. Returns the closest ship hit.


## main/java/com/createstats/item/InductionCoilItem.java


**[1]** Consumable heating element for the Induction Heater. Required (and worn down) while the heater sustains Kindled or Superheated; the heater maps its lifetime to real runtime via the `inductionCoilLifetimeSeconds` config.

Wear is tracked as a plain `Wear` value in the stack's custom data instead of vanilla durability, because a durability item is forced to a max stack of 1. This way FRESH coils (no wear data) stack to 16 for cheap bulk storage and automation, while any partly-used coil carries its wear component and therefore refuses to merge back into a fresh stack.


## main/java/com/createstats/item/KineticMultimeterItem.java


**[1]** Pocket Stressometer: right-click a kinetic block to read its shaft speed and its network's stress load. The result is stamped onto the stack (see `KineticMultimeterData`) so the animated renderer and tooltip can replay the last reading.


**[2]** Route through the mod's unified source reader so the meter inherits every kinetic block we already support: vanilla Create sources, Create Crafts & Additions alternators/motors, Create Diesel Generators engines (multiblock controller resolved internally), and the Aeronautics / Simulated portable engines & propeller bearings. Non-kinetic blocks (accumulators, tanks, vaults) report a non-kinetic kind and get the cold "no network" look.


## main/java/com/createstats/item/PilotHelmetData.java


**[1]** Night-vision visualization style.

- `CLASSIC`, plain vanilla `MobEffects.NIGHT_VISION`. No tint, no shader, no overlay. Maximum readability.

- `TACTICAL`, vanilla NV effect plus an animated client-side overlay: faint green tint, scanlines, and per-frame phosphor noise to sell the "real-life NVG tube" feel. No gameplay difference.


**[2]** Placeholder for future Forge-Energy integration.

mayyyybe for FE for powergrid and crafts??? Idea: a backpack battery block or a wireless powergrid network could top this up, and NV / HUD / Goggle-vision could consume it at a small rate per tick. For now this is a non-functional bookkeeping slot, it persists, exposes getters, and the keybind/effect handlers never consult it. Enable consumption later by wiring it into `PilotHelmetServerEvents.tick`.


## main/java/com/createstats/item/PilotHelmetItem.java


**[1]** Short, scannable intro of what the helmet does. Mirrors the layout that Create's own items use (one summary line in gold, then a small bulleted capability list in muted greys). Keeps tooltip noise low while telling a first-time crafter why this thing is worth the diamond helmet + goggles.


**[2]** Q5: nudge players toward the optional GeckoLib dependency for the animated visor, but only when GeckoLib is not installed (when it is, the helmet already animates so the hint is noise).


## main/java/com/createstats/item/SwitchLinkCardItem.java


**[1]** Pairs a switch to lamps it is not wired to. the gesture: click a switch to hold it, then click each lamp to link or unlink it. sneak-click anywhere to let go. same two-click shape as the Copper Wire and the cable run anchor, so it costs nothing to learn. what a wireless link actually does: nothing to do with power. a gated lamp's still fed by cable exactly like a wired one - the switch just tells it not to light. wireless changes who decides, never where the energy comes from, and a lamp with no cable stays dark no matter how many switches point at it. keeps one rule true everywhere: light comes from the wiring.


## main/java/com/createstats/manual/figure/FigureElement.java


**[1]** A single drawable element inside a `FigureSheet`. Coordinates are normalized (0..1) within the figure content area; the renderer maps them to pixels. Sealed with nested record implementations (permits clause omitted because all subtypes live in this file).


## main/java/com/createstats/manual/figure/FigurePalette.java


**[1]** Named ARGB colors for the data-driven Factory Manual figures. Values mirror the warm paper/brass art style already used by `FactoryManualScreen` so generic figures blend seamlessly with the two hand-coded lessons.


## main/java/com/createstats/manual/figure/ManualFigureRenderer.java


**[1]** Renders a single `FigureSheet` into a rectangle, reusing the warm paper/brass art style of `FactoryManualScreen`. Self-contained: holds its own copies of the small drawing helpers so the screen only needs to call `render`.


## main/java/com/createstats/manual/figure/ManualFigures.java


**[1]** Registry of data-driven `ManualFigure` lessons keyed by manual page id. Pages registered here render generic figure sheets via `ManualFigureRenderer` instead of (or in addition to) plain body text. The two hand-coded lessons (inventory display, vehicle control center) are NOT registered here, they keep their bespoke renderers.


## main/java/com/createstats/mixin/GuiGraphicsScissorMixin.java


**[1]** Keeps `GuiGraphics` scissor rectangles aligned with the local GUI-scale clamp used by createstats screens.

createstats screens render at a capped effective GUI scale of {@value} by inflating their virtual canvas in `Screen#init` and pushing an inverse pose scale during render (see `ScreenLocalGuiScaleMixin` / `CreateStatsGuiScale`). Pose-aware drawing (fill, blit, drawString, batched quads) honours that inverse scale automatically. `GuiGraphics.applyScissor`, however, converts its rectangle to framebuffer pixels using the raw `Window#getGuiScale()` and ignores the pose stack. At an actual GUI scale above the cap the scissor box therefore lands 1.5x-2x too far out and clips the panel content away ("less is rendered with each scale increase").

This redirect substitutes the clamped scale for the scissor maths whenever a createstats screen is the active screen, so the scissor rectangle matches where the pose-scaled content is actually drawn. All other screens (vanilla and other mods) are left untouched.


## main/java/com/createstats/ModConfiguration.java


**[1]** Clamp a block-entity renderer's own tuned view distance by the client-side global ceiling. Only ever lowers a renderer's default (never raises it), so per-display tuning is preserved while a player can still cut render distance for performance. Falls back to `ownDefault` if the client config has not loaded yet.


**[2]** Tuning follows one rule so retunes stay predictable: bufferCapacity = activeDrain x 160 ticks (8 seconds of active runtime), and idleDrain = activeDrain / 4 (so a full buffer idles for 32 seconds). Tier A, avionics brains.


**[3]** Tier C, displays and comms. The Stats Display multiplies its active draw by the source-count curve below, so this figure is the cost of a single linked source. The reference point is a Create Crafts and Additions alternator at 360 FE/t flat out: at four times this figure, a single linked source would cost more than a whole alternator, and the source-count curve would push a sixteen-link display to nearly twelve alternators for one screen. A display is an instrument, not a smelter, and it should read as one on the power budget.


## main/java/com/createstats/network/BattleScreenHologramTogglePacket.java


**[1]** Client -> server request to flip the in-world ship hologram of a `BattleScreenDisplayBE` on or off. The BE persists the flag and syncs it back to clients, so the toggle survives reloads and is shared between players watching the same bridge.


## main/java/com/createstats/network/FePowerSyncPacket.java


**[1]** Server to client mirror of one `FeConsumer`, as a `FePowerSnapshot`.

This replaces the previous approach of riding each block entity's vanilla update tag, which failed two ways. First, four consumers (Stats Display Panel and Mk2, Holographic Projector, Battle Screen Display, Damage Observer) build their update tag from a trimmed sync method that never wrote the FE keys, so their client mirror sat at zero forever and every screen and renderer showed a permanent fake brownout. Second, driving the sync from `sendBlockUpdated` with flag 3 re-serialized the whole block entity and fired neighbour updates roughly twelve times a second per active consumer, because the tuning rule `capacity = activeDrain x 160` makes the rounded fill percent change every 1.6 ticks.

Roughly thirty bytes, sent only when `FePowerSnapshot.syncSignature()` moves and at most once every few ticks. See `com.createstats.power.FePowerSync` for the send side.


**[2]** Appended rather than packed into the flags byte: bits 0 to 3 are the booleans and 4 to 6 are the priority, so there is no room left for a three-state value.


**[3]** A missing block entity is not an error: the chunk can unload between send and handle, and a snapshot in flight for a block the player just broke arrives with nothing to do.


## main/java/com/createstats/network/FlowMeterTriggerPacket.java


**[1]** Client to server: pushes the Flow Meter's redstone trigger configuration (edited in the Stats Display panel GUI) to the linked Flow Meter block entity so the pipe itself emits a signal.


## main/java/com/createstats/network/ObserverVoxelMapPayloadPacket.java


**[1]** Server -> Client. Carries a Damage Observer's voxel map snapshot. Wire format: BlockPos observerPos + NBT(VoxelMap). NBT framing already does the gzip-style compression internally; payload sizes for typical observer volumes (radius 4-16) stay comfortably under a few hundred KB.


## main/java/com/createstats/network/PduActionPacket.java


**[1]** Every action the Power Distribution Unit screen can take, in one payload: cycle a managed device's priority tier, cycle its player override lock, cycle the PDU mode, or set every device to a sensible default.

One packet rather than three because they share a target, a distance guard and a validation path. Modelled on `ProductionDetectorSourceModePacket`.


## main/java/com/createstats/network/PduHistoryPacket.java


**[1]** The server's history send: either a full buffer, once, when a player subscribes to a range, or a single sample delta after, one per new sample while they watch.

This is the whole reason history never touches the update tag. The tag already carries the entire roster and goes out on every roster signature change; folding 900 values into it would be a resync of everything, at up to twenty hertz, for a chart. A dedicated packet keeps the cost to one full send on open and one short sample per range cadence after.


## main/java/com/createstats/network/PduHistoryRequestPacket.java


**[1]** The client's request to watch a Power Distribution Unit's history: subscribe to a range and receive the full buffer, keep the subscription alive, or close it.

Only sent while the HISTORY view is actually open, so the server pushes deltas to a player who is looking and to no one else. The keepalive is the backstop for the subscriber cleanup: a client that stopped watching without a clean close, by switching views or crashing, stops sending keepalives and the server times the subscription out.


**[2]** Close and keepalive act only on this player's own subscription by UUID, so they are safe without a reach check; subscribing sends data, so it is reach guarded like every action.


## main/java/com/createstats/network/PduSetPacket.java


**[1]** The Power Distribution Unit's explicit and bulk set actions: set the tier or the lock of one named device, or of many at once.

Separate from `PduActionPacket` because those actions are cycle by one and target a single device, and five tiers make cycling painful; this carries an explicit ordinal and a list of targets, so the inspector's five tier buttons, the keyboard, and the bulk bar all reach the same one place. A single set is just a bulk set with one target.

**Every target is validated server side against the PDU's own live roster before anything is applied.** The list the client sends is never trusted; a crafted packet naming a device on another grid is dropped by `com.createstats.power.PduBranchGuard`, and the declared target count is capped so it cannot make the server allocate without bound.


**[2]** Open or close one output leg's breaker. `value` packs the leg in its low byte and the desired state above it; targets is ignored.

Packed rather than given its own packet because it is the same shape as everything else here: one small instruction the server validates before applying. The leg index is bounds checked server side, so a crafted value cannot index past the breaker array.


## main/java/com/createstats/network/PilotHelmetNightVisionTogglePacket.java


**[1]** Lightweight toggle packet for the Pilot Helmet night-vision feature.

Unlike `PilotHelmetSettingsPacket` which addresses a specific `net.minecraft.world.InteractionHand`, this packet always operates on the player's **head-slot** helmet. This is required so the keybind works while the player is wearing the helmet (the normal use case) without needing to hold it in hand.

Carries only the desired NV-enabled state. All other settings stay untouched. The same potion-conflict rule from `PilotHelmetSettingsPacket` applies and the player gets an action-bar message on rejection.


## main/java/com/createstats/network/PilotHelmetSettingsPacket.java


**[1]** Night-vision potion conflict rule: if the client requests NV ON and the player already has an externally-sourced NIGHT_VISION effect, reject the request and surface a short action-bar message.


**[2]** If NV just switched off, immediately remove our short-duration effect so the screen darkens right away, but only if the current effect remaining is short (< 250 ticks), which can only be the helmet's own refresh, never a real potion (vanilla 3min = 3600 ticks).


## main/java/com/createstats/network/StageLightConfigPacket.java


**[1]** Client to server: apply the Stage Light's control-screen settings (pan, tilt, range, brightness, and on/off). The server validates the player is near the block (generous radius so ship-mounted lights work) and writes them onto the `StageLightBE` and block state, which sync back to clients.


## main/java/com/createstats/network/StockpileThresholdPacket.java


**[1]** Client to server: pushes Stockpile Switch (Create Threshold Switch) on/off thresholds edited in the Stats Display panel GUI to the linked block, so the thresholds can be tuned without walking to the block. Thresholds are absolute item/fluid amounts (integers), matching Create's own fields.


## main/java/com/createstats/network/ToggleVisorPacket.java


**[1]** C→S request to flip the pilot helmet visor state.

The visor state is server-authoritative and stored on the worn helmet's `PilotHelmetData` (inside `DataComponents.CUSTOM_DATA`). Storing it on the equipped stack means vanilla equipment syncing replicates the new state to every client tracking the wearer, so both the GeckoLib visor animation and the local HUD react to one synced source of truth, no bespoke client/server desync handling required.


## main/java/com/createstats/pathfinder/planner/PathRoutePlanner.java


**[1]** Expansion budget for the whole plan (shared across target attempts). Raised alongside the larger scan window so a full long-range route can actually complete instead of truncating into a partial route (which is what caused the replan churn). Bounded and plans are infrequent.


**[2]** steep-climb cost. one step up costs SLOPE_STEP_BASE; each additional block of *sustained* climb (climbRun) adds SLOPE_STEP_ESCALATION, so a long 1:1 face gets progressively pricier than a short bump and the planner prefers going around. capped per block so a hill you actually need never goes fully impassable.


**[3]** Graduated berth around trees, trunks, buildings and other blocked cells: the closer a path passes an obstacle the more it costs, so routes give groves and structures a real margin instead of clipping their edge. Extends one block past the preferred-clearance ring.


**[4]** Home-stretch mode: when the nav target is within 32 blocks AND there is a clear, clearance respecting line of sight over drivable cells, skip A* and drive straight. The LOS gate is what keeps the straight shot from driving through a wall on the last chunk.


**[5]** Cache the graduated obstacle berth per cell across all target attempts. The berth depends only on a cell and its neighbourhood, not the path, so each cell's wide-neighbourhood scan runs at most once (lazily, only for cells A* actually visits) instead of on every relaxation.


**[6]** Extra cost for skimming obstacles: finds the nearest blocked/edge cell within the preferred clearance ring (plus one block) and charges more the closer it is, so trees and buildings get a real margin. Returns 0 when nothing blocked is nearby. Package-private for unit testing.


**[7]** Cost of a single climbing step given how many blocks of sustained climb preceded it. Each block of the rise is charged base + escalation * accumulated-run, capped, so long 1:1 faces balloon. Package-private for unit testing.


**[8]** ============================================================================================ Path post-processing: turn the blocky 8-connected A* cell path into a smooth, drivable centreline a car can actually follow. Three stages: 1. greedy line-of-sight string-pulling  -> removes the 45/90 degree staircase 2. curvature-limited corner fillets      -> quadratic Bezier arcs, trim bounded by the vehicle turn radius, every arc point re-validated against the drivability grid so a rounded corner can never cut into an obstacle 3. even-spacing resample                 -> keeps the follower's projection/lookahead stable ============================================================================================


**[9]** Trim factors tried per corner, largest (gentlest) first, so a corner that a full-radius arc cannot fit still gets rounded at a tighter radius instead of staying a hard vertex.


**[10]** Build and validate a single quadratic-Bezier fillet at trim distance t. Returns the arc points, or null if any point would leave a drivable cell (so the caller can try a tighter radius).


**[11]** Curvature-limited speed profile: cap each point to sqrt(a_lat * R) from the local turn radius so the vehicle slows into bends and runs fast on straights, then a backward pass enforces a comfortable deceleration so it starts braking BEFORE the corner instead of at it.


## main/java/com/createstats/pathfinder/settings/PathScanSettings.java


**[1]** Forward window only: this must match regionChunkKeys()/recalculateCoverageStats(), which count the forward corridor. The rearward margin is scanned and cached for the planner but is NOT part of the coverage gate (it can be unloaded behind the vehicle without stalling planning).


## main/java/com/createstats/power/CableLinks.java


**[1]** Decides, for one cable face, which way the run actually continues. needs more than "is there a connection here" - a flat run meets its neighbour at the block boundary, a concave-corner run meets it inside this block, a convex-wrap run meets it past the boundary, and a run entering a machine has to reach the machine's body which starts partway inside its block. four different geometries, so the classification has to survive the trip to the renderer instead of collapsing to a boolean. pure over `View`, same reason `CableRoute` is pure over SurfaceView - these are geometric rules, basically impossible to eyeball in game, only honest way to know they're right is to assert on them. rules have to be symmetric too: an asymmetric rule draws an arm A->B and none B->A, which looks exactly like a run stopping in mid air. colour is the circuit: two cables of different dye that touch do NOT join. replaced the old channel bundle (8 conductors sharing one position, invisible, needed their own tool mode). now one block is one circuit - 8 of them down a corridor is 8 cables side by side in 8 colours you can actually see, count, and trace. lives here in the shared classification, not the flood fill, since both renderer and FE grid ask this class what's joined - if colour split circuits for one and not the other you'd get an orange cable visibly feeding a blue one. rules mirror the router - `CableRoute.neighbours` can make exactly three moves, so cable-to-cable joins in exactly three ways and nothing else counts. that "nothing else" matters: cable exposes an energy capability of its own, so a rule that fell through to "does the neighbour take power" would join two runs that merely pass near each other and sprout rungs between them like a ladder.


**[2]** The circuit colour of the cable at `pos`, or `NO_COLOUR` where there is none.

A plain dye ordinal rather than the enum, so this interface stays free of Minecraft types and the rules stay unit testable over a hand-built world.


**[3]** How the cable on `face` of `pos` leaves in the direction `towards`.

Order is load bearing. Flat is tested first because it is the common case; concave before convex because a turn onto this block's own face is a nearer, stronger claim than a wrap onto a block two steps away; and device last, so wiring is always classified as wiring.

Every cable-to-cable answer is gated on the two ends being the same colour. A concave turn is exempt in practice rather than by special case: it lands on another face of this same block, so it is trivially the same colour as itself.


**[4]** Every block position the run in this block carries on into.

This is what the FE grid floods along, and it exists because the obvious flood is wrong. A grid built by walking the six orthogonal neighbours breaks at every **convex** corner: a run folding over the top of a wall lands on `pos + towards + face`, which is a diagonal step, so the flood stopped dead there while the renderer went on drawing the join. Cable over any outside edge looked perfectly connected and carried nothing.

Reusing the renderer's own rule is the point. Two rules for "are these joined" is how you get wiring that lies about itself; with one rule, what you can see is what conducts. Colour rides along inside `of` for exactly the same reason.

Concave turns are absent deliberately: they land on this same block, which is one block entity and therefore already one node. Devices are absent too, being loads rather than wiring.


**[5]** Which of two faces meeting at a corner draws the corner itself.

Both faces of an inside corner want to fill the little cube where their slabs cross, and both halves of an outside corner want to fill the cube just past the edge. If both do, the cube is drawn twice and the two copies z-fight; if neither does, there is a notch. So exactly one owns it, chosen by direction ordinal purely because that is an ordering both sides can compute without talking to each other, and both sides are looking at the same pair.


## main/java/com/createstats/power/CablePlacement.java


**[1]** The Cable Router: where a player's run starts, what it costs, how it gets laid. a run is a gesture, not fifteen placements - wiring a ceiling lamp from a floor dynamo is ~15 placements each needing the right block+face, exactly the tedium that makes a feature go unused. so a run is two clicks: anchor a start, look where it should go, whole route previews in ghost and lays at once. only the anchor is held between clicks, and only while the run's in progress. used to also hold the output leg the run belonged to - worst trap the tool ever had, since the leg outlived the run: touching a panel stud once silently assigned every cable placed after it to that leg, and opening that breaker later killed lights across the base with nothing on screen to say why. legs aren't cable's business at all now, and the anchor clears the moment a run ends, the player switches item, or logs out. all of it's server owned. client predicts success so the two sides never race, and the ghost the client draws comes from `route`, the same method the server lays from, so the preview can never promise a run the server then refuses.


**[2]** Concurrent because in single player the client thread (predicting an interaction) and the integrated-server thread (actually performing it) share this one static map. A plain HashMap here is a real, if intermittent, source of corruption and hard-to-reproduce crashes.


**[3]** Clears a player's pending run.

Called when a run finishes, when it is cancelled, when the player stops holding cable, and on logout. An anchor that outlives the gesture that made it is a pointer into a chunk that may not be loaded and a surprise the next time the player clicks anything.


**[4]** The cable step produced by clicking a block face.

Clicking the top of a floor puts cable in the air above it, stuck downward. So the occupied position is the block _outside_ the clicked face, and the mounting direction points back at what was clicked.


**[5]** Routes between two steps, returning an empty list when there is no run inside `MAX_RUN`.

Shared by the preview and the placement so the hologram cannot promise a route that the server then declines to build. Two implementations of the same search would drift apart, and the failure would look like the game randomly refusing a perfectly good run.


## main/java/com/createstats/power/CableRoute.java


**[1]** Finds the shortest run of surface cable between two points, over block faces rather than open air. exists because running cable from a connector, up a wall, across a ceiling and into a lamp is ~15 placements, each needing the right block _and_ face. doing that by hand is exactly the tedium that makes a feature go unused. so the player clicks a start, looks at a destination, and the whole route previews and lays in one go. this class decides the route.

the surface graph: a cable isn't free-floating, it's stuck to the face of a solid block. so a position here is a `Step` - "which empty block the cable occupies" paired with "which neighbouring solid block it's stuck to". from one step the cable can move three ways, and getting all three right is what makes a route look like real conduit instead of a staircase through the air:

- **Flat.** Carry on along the same surface into the next block.

- **Concave turn.** The run hits a wall, so it turns and climbs that wall. This lands on a different face of the _same_ block position, which is exactly why the cable block has to support several faces at once.

- **Convex wrap.** The surface runs out, so the cable folds around the outside edge and continues on the far side of the block it was already attached to.

every move costs the same, so plain BFS gives an actually-shortest route. no heuristic on purpose - A* over a surface graph with wraps and turns is easy to get subtly wrong, and at wiring distances (tens of blocks, hard capped) BFS is fast enough and obviously correct.

Pure over `SurfaceView` so it can be unit tested without a world, in the same spirit as `FeGridSolver` and `PduAutoController`. Routing bugs are geometric and nearly impossible to eyeball in game.


**[2]** Every step reachable from `from` in one move.

Exposed for tests, because the three move kinds are where the geometry is easy to get wrong and asserting on them directly is far clearer than inferring them from a finished route.


## main/java/com/createstats/power/CableRun.java


**[1]** One connected run of cable, gathered by exactly the rule that decides what conducts. not four flood fills because three separate features need "every block on this run": the dye brush, the rip-up gesture, and the goggle readout reporting a run's length and weakest grade. each used to be free to walk the world its own way, and the dye brush did - it walked the six face neighbours, so it painted cable that merely passed nearby and stopped at every convex corner the grid happily conducted across. player ended up with a run that was one colour to look at and two circuits to the electricity.

So there is one walk, over `CableLinks.continuations`, which is the same rule the renderer draws and the FE grid floods. If you can see it joined, it is on this list, it conducts, it gets painted, and it comes up when you rip the run out. Colour separates circuits inside that rule rather than beside it.

Pure over `CableLinks.View`, so it is unit tested over a hand-built world with no level at all, in the same spirit as `CableRoute` and `FeGridSolver`.


**[2]** The most blocks any one gesture will walk. a base can have several hundred in one run, and a dye click or rip-up can't become an unbounded scan on the server thread. hitting the cap truncates instead of failing - a half painted run looks odd but is obviously fixable, a refused click just looks broken.


**[3]** Every cable position joined to `origin`, including it, breadth first.

Breadth first so a truncated walk is a contiguous blob around where the player clicked rather than a thread wandering off down the longest corridor.


## main/java/com/createstats/power/CableTier.java


**[1]** The three grades of surface cable: what each carries, how fat it's drawn. width is the readout - a player has to tell a trunk from a spur across a room, no goggles, no menus. capacity and thickness rise together and are declared in one place: a grade carrying more without looking heavier is wiki-only knowledge, a grade looking heavier without carrying more is a lie in geometry. numbers are multipliers because everything electrical in this mod is a multiple of one plain connector on plain copper (`PowerConnectorBE.baseTransferRate()`, 8192 FE/t default, configurable). storing a multiplier instead of an absolute rate means retuning the base retunes the whole grid at once, cable included, instead of leaving cable pinned to a stale number. one cable block carries **one** circuit. the old bundle, where a single position held up to running two circuits down one corridor means two cables side by side, which is both what a real install looks like and something you can see and count.


## main/java/com/createstats/power/client/FePowerVisuals.java


**[1]** Client-side "living power state" for anything that renders like a screen: a CRT-style boot sequence when a consumer comes back online, an unstable flicker while the buffer is in brownout, and a near-black panel when power is gone.

Everything is derived from the synced `FeConsumer` mirror plus wall-clock time, so it needs no extra packets and keeps breathing while the game is paused. Fully self-gating: with FE Dependent Mode off it always returns full brightness, so nothing changes for players who do not use the mode.


**[2]** Screen brightness multiplier in 0..1 for the given consumer. 1.0 when healthy or when FE mode is off, a boot ramp just after power returns, an unstable flicker in brownout, and `DEAD_BRIGHTNESS` when the buffer is empty.


## main/java/com/createstats/power/client/FeStatusWidget.java


**[1]** The shared FE Dependent Mode status panel drawn inside block GUIs: powered state, a color-coded buffer bar, stored/capacity numbers, and the configured draw (what is using the power and how much). Entirely self-gating: draws nothing while `power.feDependentMode` is off, so every screen can call it unconditionally.

Reads the client-side `FeConsumer` mirror, which is fed entirely by `FePowerSyncPacket`. Every value shown, the tuning numbers included, is server truth: the mod's config is `ModConfig.Type.COMMON` and NeoForge does not sync that, so anything read locally would be this client's own file rather than the server's.


**[2]** Draw the panel with its top-left corner at (x, y), `width` pixels wide. Returns the Y coordinate just below the drawn panel (== y when nothing is drawn). Shown whenever FE Dependent Mode is on or the buffer holds charge, so wiring FE is visible even before the mode is enabled.


**[3]** A device the player locked off is not "shed": nothing failed and no tier lost a fight, it was switched off on purpose. Saying LOAD SHED there sends a player hunting a power problem that does not exist.


**[4]** The configured burst cost exceeds the whole buffer, so the operation this block exists to perform can never be afforded. Without this the block looks perfectly powered and silently does nothing, which is undiagnosable in game.


**[5]** Deliberately shed by a Power Distribution Unit to keep a higher tier alive. Amber, not the red of a real outage: the device is fine, it is simply lower priority than the power currently available. While it still has reserve it keeps running.


**[6]** Unmissable filled red bar with a pulsing brightness. While the buffer is climbing back toward the reboot threshold, say so and show the target, so a player watching a dark block knows it is recovering rather than broken.


## main/java/com/createstats/power/FeAttachment.java


**[1]** Always current. These are rebuilt from scratch on every flood, so one can never outlive the machine it points at; a stale attachment would mean the grid pushing energy into a block that is no longer there.


## main/java/com/createstats/power/FeBankMerge.java


**[1]** How a cluster of touching Capacitor Banks divides into merged structures, and what a merged structure is worth.

**The rule that matters: the partition must be identical no matter which block you ask.** Every member floods the same connected cluster and then runs this same deterministic split, so two blocks can never disagree about which bank they belong to. Anything less and a shared buffer becomes two half-buffers that fight each other.

Blocks are taken in bottom-up order and packed into a box no larger than the configured footprint and height. That ordering is what makes towers behave the way a player expects: a 1x1x10 column becomes a full six-block bank with a four-block bank stacked on top, rather than an arbitrary slice.


**[2]** Total FE a merged bank of `blocks` blocks holds.

Superlinear on purpose: `base x blocks ^ exponent`. Merging is a build decision with a cost in materials and space, so it pays a bonus rather than merely adding up. The default 1.1 keeps that bonus gentle, roughly 2.1x for two blocks and 4.6x for four, so a big bank is worth building without making small ones pointless.


## main/java/com/createstats/power/FeCapabilities.java


**[1]** Registers the receive-only `FeConsumer` of every FE Dependent Mode consumer as its block `Capabilities.EnergyStorage.BLOCK`, so any FE network (Create Crafts and Additions wires, Powah, Mekanism, Flux Networks, generic pipes) can charge the internal buffer from any side.

One listener for the whole roster, wired in the `CreateStats` constructor next to the existing item-handler capability listeners. Mk2 variants that inherit their base BE class (Stage Light Mk2, Stats Display Mk2) register their own BE type pointing at the inherited component.


**[2]** The range takes power on every face and is pure load, like a lamp: it is built-in power infrastructure rather than an FE Dependent Mode consumer, because a cooker that cooks with nothing wired to it would be no cooker at all.


**[3]** A switch is a length of cable that can refuse to conduct, so it presents the same face as cable does. Without this, cable had no way to tell a switch from a fence post and a run stopped visibly short of every switch it fed. The handle itself refuses while open.


**[4]** Three kinds of face, and nothing else is a terminal at all. The two side bosses take FE in. The back boss gives it out, breaker gated and rated at its own leg. The bus bar on top gives nothing: its eight studs are Copper Wire terminals and the panel pushes into the machines on each of them itself, so a handle there would be a way around the whole panel, draining the buffer flat past every breaker and every priority while the screen reported nothing wired.

Returning the input handle for every other face, as this used to, meant the back and the underside silently accepted a feed the model never suggested was there.


## main/java/com/createstats/power/FeConsumer.java


**[1]** The FE Dependent Mode consumer component. Composition, not inheritance: every consumer block entity owns one of these as a field, because the consumer roster straddles plain `BlockEntity` and Create's SmartBlockEntity and cannot share a base class.

Backs an internal Forge Energy buffer and exposes it as a **receive-only** `IEnergyStorage`: external FE networks can charge it, nothing can drain it from outside. The owning BE calls `serviceTick(boolean)` once per server tick; a `false` return is the hard cutoff, and the BE routes into its existing safe-idle path and skips its work.

When `power.feDependentMode` is off (the default), `serviceTick(boolean)` is a true no-op returning `true`, and behavior is identical to the pre-FE mod. two sides, one class - a server-side instance owns real state and drives it from `serviceTick`. a client-side instance is a **mirror**: it never ticks, every value it reports comes from the last `FePowerSnapshot` the server sent (see `applyClientSnapshot`). that includes the tuning values, since the mod's config is `ModConfig.Type.COMMON`, which NeoForge doesn't sync, so a client reading `FeProfiles` would be reading its own local file.


**[2]** Quiet period, in ticks, after which a power flip counts as a fresh event rather than part of an ongoing flap. Hysteresis already stops most flapping; this is the backstop for a pack that sets `rebootThresholdPct` to 0.


**[3]** Beats allowed per flapping burst. Two, so a cutoff and the recovery that follows it both play however fast the player fixes the supply, while a consumer that keeps cycling falls silent after the first pair instead of machine-gunning chimes and spark bursts.


**[4]** Standard wiring for a block entity owner. The owner reference drives dirty marking and the throttled client sync in `syncIfDue()`.

This deliberately does **not** call `sendBlockUpdated` any more. It used to, on every change of the rounded fill percent, and since the tuning rule is `capacity = activeDrain x 160` that is a change every 1.6 ticks: roughly twelve full block entity re-serializations per second per active consumer, plus twelve neighbour-update cascades, for state that four of the sixteen consumers did not even put in their update tag. The client mirror now travels as a thirty byte `FePowerSnapshot` instead, through `FePowerSync`.


**[5]** True when the configured burst cost is larger than the whole buffer, so the discrete operation this block exists to perform can never be afforded. Without this the block looks perfectly powered and silently does nothing forever, which is impossible to diagnose in game.


**[6]** Spend this tick's upkeep. Returns `true` when the block may run. With FE mode off this is always `true` and touches nothing. With FE mode on it drains `active ? activeDrain : idleDrain`; if the buffer cannot cover the drain it empties, the consumer goes unpowered, and the return is `false` (hard cutoff).


**[7]** Variant for consumers whose active load scales with how much they are handling (see `FeLoadCurve`). The multiplier applies to the **active** drain only and is clamped to a minimum of 1.0, so an idle block is never penalised for having many sources linked.


**[8]** Hysteresis. Coming back from a cutoff requires a real reserve, not one tick of headroom. Without it a marginal or bursty supply flips powered every few ticks, and the flip is not cosmetic: it stops and restarts flight governors, snaps stage lights, and implodes and rebuilds holograms. A cut-off block now charges like a capacitor before it wakes up.


**[9]** Pay for one discrete heavy operation (a scan cycle, a sweep, a summary refresh). Returns `false` when the buffer cannot cover it, in which case the caller skips the operation this tick without going fully unpowered. Always `true` when FE mode is off.


**[10]** A burst larger than the whole buffer is unaffordable at any charge level. Flag it so the GUI can say so, rather than leaving a fully charged block mysteriously doing nothing.


**[11]** Moves this buffer's whole charge into `other`, as far as it will fit. Used when blocks merge into a multiblock, so a panel's charge is not stranded in a buffer that is about to go dormant behind the assembly's shared bank.


**[12]** Buffer percent for HUD warning logic: reads 100 while FE mode is off, so cockpit HUDs never flag a power problem on a block that does not currently require power. GUI panels use the raw `fillPct()` so charging is still visible with the mode off.


**[13]** True while the consumer is cut off and charging back toward its reboot threshold. Distinct from a plain brownout: the block is not running, but it is on its way back.


**[14]** Returns and clears the pending power transition: +1 the tick power came back, -1 the tick it was lost, 0 otherwise. Lets owners play one boot or cutoff effect per flip.


**[15]** Several consumers build their client update tag from the same saveAdditional that writes disk state, so this runs client-side too. Once the server has sent a snapshot, that is the only truth: a block update arriving afterwards must not quietly overwrite it with values that carry no tuning and could be a tick stale.


**[16]** FeDrain, FeInput and FeMult were written by earlier builds. They are per-tick meters, not state, so they are read back only to avoid a visible zero on the first tick after load.


**[17]** Deliberately NOT gated on feModeEnabled(): a consumer always accepts and stores FE from any adjacent source, so wiring visibly works even before FE Dependent Mode is turned on. The mode only governs whether the block drains and cuts off (see serviceTick).


**[18]** The bucketed state signature, computed without building a snapshot. Called every tick per consumer, so it stays allocation free; `FePowerSnapshot.signature` holds the single definition of what counts as a change.


**[19]** Throttled client sync. Sends only when the bucketed signature moves, at most once every `MIN_SYNC_GAP_TICKS` ticks, with a power flip jumping the queue and a slow heartbeat as drift insurance. A steady, fully charged machine sends nothing at all.


## main/java/com/createstats/power/FeGridMember.java


**[1]** A node the grid can own: everything `FeGridSolver` needs, plus the lifecycle and reporting that `FeWireNetwork` needs to keep a live network honest. separate from `FeGridNode` because that one's deliberately the bare minimum the arithmetic needs, so the solver stays pure and unit testable over fake nodes. this adds the parts that only make sense for something actually in a world - whether it still exists, and where to report its measured flow. keeping them apart means the solver's tests never have to invent a level or a block entity. implemented by both wire node kinds. a Power Connector is a point on a hanging wire; a Surface Cable block is a segment of a run. different shapes, different ways of finding each other, but from the grid's point of view they're the same thing: somewhere energy can sit, something it can be handed to.


## main/java/com/createstats/power/FeGridNode.java


**[1]** The only thing `FeGridSolver` needs to know about a wire node.

Exists so the grid solve is pure arithmetic over an interface rather than logic welded to a block entity: energy transport is where duplication and voiding bugs live, and this is what makes it unit testable without a running game.


## main/java/com/createstats/power/FeGridSolver.java


**[1]** The per-tick grid solve, as pure arithmetic over `FeGridNode`.

This replaces pairwise diffusion, where each node handed half its surplus to each linked neighbour every tick. That model had four problems a player hits immediately: energy propagated one hop per tick and halved at each hop, so a long run delivered a fraction of what the generator made and delivered it in bursts; transfer was iteration-order dependent, so power visibly flowed faster one way along the same wire; every node parked a full buffer that was never usable; and links to broken connectors were never cleaned up.

The grid settles in one tick regardless of how many hops separate the generator from the machine:

- **Collect.** Pull from generators into one grid-wide pool, capped per node by its transfer rate and overall by the grid's own headroom.

- **Cover.** If the machines still want more than the grid holds, discharge storage by exactly the shortfall, so a battery is never drained to top up a wire buffer.

- **Distribute.** Serve the machines, in two fair passes.

- **Charge.** Only once the grid buffer is full, which is the one honest signal that generation exceeds demand, move surplus from generators to storage directly.

- **Settle.** Spread whatever is left evenly back across the node buffers.

**Conservation is the invariant.** Every transfer commits against a real return value rather than a simulated one, and the collect budget is capped at the grid's headroom so the pool can never exceed capacity and the settle can never have energy left over to lose. Making that structural rather than hoped for is the whole reason this class is separate from `FeWireNetwork`: `FeGridSolverTest` checks the FE in the world is unchanged after every solve in every scenario.


**[2]** FE per tick the machines asked for but did not get. The number that says a grid is under-provisioned **before** anything actually browns out, which is the whole reason demand is measured separately from delivery.


**[3]** Ticks of runtime left at the current net drain, or `Integer.MAX_VALUE` while the grid is holding steady or filling. This is what a readout turns into "collapse in 00:47".


**[4]** Same solve, additionally recording each node's signed net FE/t through its own attached block this tick: **negative** when energy left the attached block into the grid (a generator, or a battery discharging), **positive** when energy entered it from the grid (a machine being powered, or a battery charging). A node that moved nothing is simply absent from the map.

Purely additive instrumentation, for a goggle readout on `PowerConnectorBE`. Passing `null` (what the two-argument overload above does) skips every recording branch below, so this changes nothing about the arithmetic or about any existing caller or test.


**[5]** Classify by role, not by list position. A block that can only give is a generator, one that can only take is a machine, and one that does both is storage.


**[6]** How much may enter the pool this tick without any of it being lost at the settle: whatever the machines will take straight back out, plus whatever the wire itself still has room for.

The cap used to be the headroom alone, and that quietly made a grid's throughput equal to its total wire buffer. It went unnoticed while cable held 400 FE a segment, because four segments could then carry more than anything on the grid wanted. At 25 FE a segment it became the binding constraint: a Capacitor Bank could not run an Induction Range on High through a short run of tray, because eleven segments of cable would have been needed before 256 FE/t could even enter the pool. Demand is the right allowance, because demand is exactly the part that leaves again in phase 3 and never needs storing.


**[7]** Phase 2: if the machines still want more than the grid holds, discharge storage to cover exactly the shortfall. Never more, so a battery is not drained to top up a wire buffer.


**[8]** phase 4: charge storage, only once the grid buffer's full - the only honest signal that generation actually exceeds demand. moves generator -> battery directly instead of through the pool so there's no intermediate value to strand, and a battery discharged this tick is skipped so the two phases can't fight each other.


**[9]** Pulls up to `budget` FE out of the given nodes' attached blocks, capped per node by its transfer rate. Records which nodes actually gave energy in `touched` when supplied, and their signed flow (always negative here, energy is leaving the attached block) in `flowOut` when supplied.


**[10]** Moves energy straight from one handler to another without it ever being held in the pool, so there is no intermediate value that could be stranded. Simulates both halves and commits the agreed amount, in that order, so a source whose real extract yields less than its simulate can never mint FE.


**[11]** Serves a Power Distribution Unit's managed branch **tier by tier**, from `FePriority.CRITICAL` down, and sheds the tiers the pool cannot reach.

This is deliberately a separate path from the flat `distribute` the upstream grid uses, which is unchanged. Sorting the loads and reusing the flat body would not actually shed: its first pass hands every load `pool / size` regardless of tier, so a low-priority device would still get a slice while a critical one went short, and its `% size` cursor would start partway down the sorted list and wrap across tier boundaries.

Within a tier the existing two-pass fair share applies, with the rotating cursor kept **inside** the tier so equals still take turns.

**The shed rule.** A load is marked shed only when at least one load of a higher or equal tier was actually served this tick. A branch that delivered nothing at all (dead source, even the top tier starved) leaves every load unshed, so it reads as a plain outage rather than claiming the grid chose to drop it. Every visited load has its flag written every tick, so it can never go stale.


**[12]** Flat fair share over a branch, ignoring tiers. What a PDU uses when its shedding switch is off, so a player can put a managed branch back to the default behaviour without removing the panel. Clears every shed flag, because nothing was deliberately dropped.


**[13]** Priority of a load. Read through the attached handler, which for every one of our consumers **is** the `FeConsumer` itself (see `FeCapabilities`), so this needs no addition to `FeGridNode`. Third party machines are not `FeConsumer` and default to `FePriority.DEFAULT`, so they are neither privileged nor shed first.


**[14]** Hands out up to `pool` FE and returns how much was actually taken. Pass one gives an equal slice, so a greedy machine at the front cannot drink the whole grid while the others get nothing; pass two mops up what nobody claimed.


## main/java/com/createstats/power/FeLoadCost.java


**[1]** A device that publishes its own rating: the rating is the cost, full stop.

No fallback to the measured figure here, and that is deliberate. A rated device knows exactly what its setting costs, so a measured zero means "it was starved this tick", not "it is free". Treating a starved device as costing nothing would admit it, feed it, watch it cost its real draw, and shed it again on the next step.


**[2]** An FE Dependent Mode consumer, where the configured active drain is a nameplate and the measured figure is the truth once the machine has actually run. Preferring the measurement is what stops a machine that idles well under its rating from hogging budget it never spends.


**[3]** A block the PDU can't introspect, usually another mod behind a cable attachment. all we know is what it'll accept right now, so headroom stands in for both numbers. it's a bad cost model, see `FeRatedLoad` for why.


## main/java/com/createstats/power/FeLoadCurve.java


**[1]** Sub-linear load scaling for consumers whose work grows with how many things they are watching.

Each additional source costs progressively less than the last, so a wide panel plateaus instead of exploding: `multiplier = count ^ exponent`. With the default exponent of 0.85 one source is 1.0x, two are 1.8x, ten are 7.1x and twenty are 12.8x. An exponent of 1.0 makes it fully linear.

Currently applied to the Stats Display Panel and Mk2; kept here (rather than inline) so other multi-source blocks such as the Dispatch Board or Holographic Projector can adopt the same curve without duplicating the math.


## main/java/com/createstats/power/FeLock.java


**[1]** A player's override of the Power Distribution Unit's automatic decision for one device. tiers are relative priority, that's the right tool almost always. a lock is absolute, for the two things a tier can't say: "never drop this no matter the math" and "don't run this right now at all". pin the one machine you can't afford to lose, or mothball something hungry without unwiring it. FORCE_OFF wins in every mode, no exceptions - not about scarcity, so it holds even with power to spare. FORCE_ON only does anything in Active Auto (only mode with an optimizer to overrule) - nothing else sheds a device except actually running out of power, which no lock can fix anyway.

**Force on is absolute, including below the reserve floor.** That is deliberate: an explicit instruction from the player outranks a heuristic about what is worth protecting. It does mean a locked-on set that exceeds supply will drain the buffer to nothing, so the panel warns when the locked-on demand alone is more than measured generation.


## main/java/com/createstats/power/FeManagedLoad.java


**[1]** A load the PDU can actually control - holds a tier and a lock, obeys a shed instruction at once instead of coasting on its own buffer. every panel control used to gate on `instanceof IFeConsumer`: tier buttons, lock buttons, click cycles, bulk bar, lighting master, auto assign, all six. `FeConsumer` is the FE Dependent Mode path, and the built-in power blocks (Induction Range, Strip Lamps) deliberately don't use it since it's off by default and they have to be dead without power regardless. so on a branch of ranges and lamps (the branch the panel's actually built for), every control just silently did nothing - not an error, the server got the action, found no interface for it, returned, and the row resynced back to `DEFAULT`/`NONE` so the button visibly sprang back. looked like a broken button because it was one. device owns the setting for the same reason `FeConsumer` does - a tier describes the machine, not whatever panel's looking at it, so it lives in the machine's own saved data. rewire a range to a different branch, it keeps its tier; replace a broken PDU, it doesn't take the branch config with it. shed has to bite immediately - `setManagedShed(boolean)` isn't bookkeeping. these blocks hold real buffers (2000 FE for a range), so just declining to serve one leaves it running another eight seconds. a lock that takes 8 seconds to do anything is the same as a lock that does nothing. has to stop drawing on the tick it's told.


## main/java/com/createstats/power/FeManagedState.java


**[1]** The tier, lock and shed flag an `FeManagedLoad` carries, in one place so every block that is built in power infrastructure holds them identically instead of each growing its own copy.

Ordinals rather than tags on the save path, so this stays free of any Minecraft type and can be asserted on directly. The two blocks using it write two ints each; the round trip through a `CompoundTag` is three lines at the call site and not worth an abstraction.

The shed flag is deliberately **not** saved. It is a live decision by whichever panel is managing the branch this session, and persisting it would leave a machine switched off after a reload with nothing on screen explaining why. The lock is saved, because that one is the player's instruction and forgetting it would be losing their work.


**[2]** Setting any lock clears the shed flag.

Force on and force off both decide the device's state outright, and leaving a stale shed flag under them would have the row contradict the lock the player just set. Clearing on release is the same rule the `FeConsumer` path uses: the next control step decides properly.


## main/java/com/createstats/power/FePowerFx.java


**[1]** Per-tick FE grid service for consumer blocks: actively pulls energy from adjacent sources into the buffer (so pull-only sources such as the Create Crafts and Additions Creative Accumulator work by direct contact, not just push-based cables), and plays the power transition beats (a boot chime when a consumer comes online, a discharge with a spark burst on hard cutoff). Server-side, broadcast to nearby players, so it needs no client wiring.


**[2]** Call once per server tick from a consumer BE. Pulls FE from neighbors into the buffer, then plays any pending boot or cutoff beat. Cheap when the buffer is full and nothing transitions.


**[3]** Draw FE from every adjacent block that can be extracted from (our dynamo and connectors, plus any third-party FE source or cable). Fills the buffer up to its remaining space each tick.


**[4]** Simulate both halves, agree on an amount, then commit both. Order matters: an earlier version credited the buffer first and threw away the real extract's return value, so any source whose real extract yielded less than its simulate (rate-limited cables, multiblock controllers, networks that served another request in between) minted FE out of nothing. Never commit a receive you have not already proven the source can pay.


**[5]** Commit exactly what the source actually gave up. If the sink now takes less than that (it cannot, the simulate above proved otherwise, but capabilities are third-party code) the shortfall is dropped rather than duplicated.


## main/java/com/createstats/power/FePowerSnapshot.java


**[1]** An immutable, server-authoritative picture of one `FeConsumer` at a moment in time.

This is the only thing the client is allowed to believe about a consumer's power. It carries the tuning values (`capacity()`, `idleDrain()`, `activeDrain()`, `burstCost()`) as well as the live state, because the mod's config is registered as `ModConfig.Type.COMMON`: NeoForge loads that on both sides but never syncs it, so a multiplayer client reading `FeProfiles` directly would be reading its own local file rather than the server's.

Lives in the power package rather than the network package so nothing here depends on the packet layer; `FePowerSyncPacket` is a transport for this type, not its owner.


**[2]** Cheap change signal for sync throttling. Buckets the buffer to 2 percent and the throughput meters to 32 FE/t, so a steady machine stops generating packets entirely while a real state change still gets through on the next opportunity.


**[3]** The signature computation, on loose values. Exists so `FeConsumer` can test for a change every tick without allocating a snapshot it will usually throw away, while keeping a single definition of what "changed enough to tell the client" means.


## main/java/com/createstats/power/FePowerSync.java


**[1]** The send side of the FE client mirror.

Two entry points, and between them a client always has correct power state for every consumer it can see:

- `push` is called by `FeConsumer` itself when its throttle decides the state has moved enough to be worth a packet. Goes to everyone tracking the chunk.

- `sendChunkContents` is called when a player starts tracking a chunk, so a player walking into range gets every consumer's state immediately instead of waiting for each one to happen to change.

Nothing here runs on the client, and nothing here is reachable while the level is a client level, so the class is safe to touch from common block entity code.


**[2]** Send the current snapshot of every FE consumer in a chunk to one player. Called from the chunk-watch event, so this is once per chunk per player, not per tick.


## main/java/com/createstats/power/FePriority.java


**[1]** Load priority tier for an FE consumer served through a Power Distribution Unit (PDU).

Under scarcity the PDU serves its managed branch tier by tier, from `CRITICAL` down, and sheds the tiers its buffer cannot reach. So `CRITICAL` is effectively never shed and `OPTIONAL` is shed first. **Ordinal order is the serve order**: do not reorder the constants.

Modelled on `blockentity.StatusTowerLightSettings.SourceMode`: a display name plus a wrapping `next()` for a cycle control.


## main/java/com/createstats/power/FeProfiles.java


**[1]** Bridges `FeConsumer` tuning to the `[power]` config section. Every accessor is null-safe before config load: if the config is not available yet, FE mode reads as disabled and `FeConsumer.serviceTick(boolean)` no-ops, so world load order can never brick a block.


**[2]** Tuning for one consumer that serves a whole multiblock: capacity is pooled across the assembly, drain is not.

**Capacity scales linearly** with block count, because each block in the assembly physically contributes its buffer. That is the whole point of pooling: an assembled display is charged through any one of its panels and holds what all of them are worth.

**Drain deliberately does not scale.** What a display costs to run is driven by the data it polls, and that is already curved by source count at the call site. Panel count is screen area, which costs the server nothing. Scaling here as well would compound two curves: a 3x3 with four live sources would have jumped from about 2,000 FE/t to nearly 8,000, which would silently break every existing build for no simulation reason.

So a bigger display costs the same to run and is proportionally more resilient, which is the right incentive for a mod whose whole point is building nice big readouts.


## main/java/com/createstats/power/FeRatedLoad.java


**[1]** A load that can say what it actually costs to run, instead of the PDU guessing from headroom. used to cost every non-FeConsumer device as receiveEnergy() headroom - how much room's left, not how much it spends. NOT the same number: an Induction Range on LOW spends 16 FE/t but an empty one reports demand of 512 (its transfer rate). 32x off. and it ratchets - a served device tops off so its headroom shrinks and it looks cheap, a shed device stays empty so it looks expensive forever, and the optimizer sorts cheapest-first, so once something got shed it could never climb back even with power to spare. that was the "auto doesn't use all the supply" bug, and more generation never fixed it. so now a device that knows its cost just says so. headroom's still the fallback for other mods' blocks, where it's all the PDU can see. rated isn't measured - both numbers matter, for different consumers. `ratedDrawFePerTick()` is what the controller budgets against: the steady cost of letting this device run, has to stay stable whether or not it's well fed right now, or the budget flaps. `measuredDrawFePerTick()` is what it actually spent last tick, what the panel reports, since a row claiming a draw the block isn't taking is exactly what makes a readout untrustworthy. implemented by the block entity itself, not its energy handler, since the PDU sees these blocks as grid nodes in their own right: `FeWireNetwork.collectAttachments` skips any neighbour that's already an `FeGridMember`, so a range or lamp never gets wrapped in an `FeAttachment` and the node the PDU holds is always the block entity.


**[2]** The steady cost of running this device at its current setting, in FE per tick.

Zero when it is switched off, gated or cut by redstone, because a device that is not going to run is not load and must not hold budget that another device could use. Must not depend on how full its own buffer is.


## main/java/com/createstats/power/FeStackFill.java


**[1]** How a pooled stack of storage blocks distributes charge: from the bottom up, draining from the top down.

This is what turns a stack of Capacitor Banks into a readable gauge. Energy could just as easily be held in one number on the group, but then every block would show the same average and a tower would light and dim uniformly. Keeping it per block and filling in order means the boundary between lit and dark blocks _is_ the charge level, legible across a base without opening anything.

Pure arithmetic over an array so it can be tested without a running game, on the same reasoning as `FeGridSolver`: anything that moves FE is somewhere a duplication or voiding bug can hide. Index 0 is the bottom block.


**[2]** Places up to `amount` FE into the stack, lowest block first. Returns how much was actually placed, which is less than `amount` only when the stack filled up.

Capacities are per block rather than one shared number because the merge bonus does not divide evenly: see `FeBankMerge.shareCapacity`.


## main/java/com/createstats/power/FeWireNetwork.java


**[1]** One connected component of Power Connectors: the topology half of the grid. The arithmetic lives in `FeGridSolver`, which is pure and unit tested; this class owns discovery, invalidation and the once-per-tick guard.

Instances are reachable only from their member block entities, so a network becomes garbage as soon as its nodes unload. `markDirty()` forces every member to rebuild on its next tick.


**[2]** Builds (or rebuilds) the network containing `origin` by flooding along wire links, and assigns it to every member. Links pointing at something that is no longer a connector are dropped as they are found, so an exploded or world-edited node cannot leave a permanent dead link behind a rendered wire.


**[3]** Floods out from one node and assigns the resulting network to every member.

Two kinds of connection are followed, because the mod now has two kinds of wiring. A Power Connector reaches its partners through explicit copper wire links, which can span open air. A Surface Cable segment reaches whatever is physically touching it. Both end up in one member list, so a run of cable that meets a connector is simply one grid, and a player never has to think about which sort of wiring they are on.

Links pointing at something that is no longer a connector are dropped as they are found, so an exploded or world-edited node cannot leave a permanent dead link behind a rendered wire.


**[4]** Copper wire links: point to point, across open air. A wire may land on another connector or on a machine's built-in connector (the FE Fluid Pump); either is walked, a panel stud is driven from outside and skipped, and anything else is a dead link.


**[5]** An OPEN switch is not a member and is not walked through. That single fact is the entire mechanism: the flood stops here, so what was one network becomes two and the downstream half has no source left in it.


**[6]** A lamp is wiring as well as a load: power carries on through its housing into the next lamp touching it, so a row of fittings lights from one cable landing on any one of them, exactly the way a row of luminaires is looped in on a real circuit.


**[7]** A pump is a load with a built-in connector: it joins the grid touching it by face, and it chains onward through the Copper Wires strung to its own connector.


**[8]** A machine already served by a connector is dropped here rather than left to be served twice: the connector reaches the block it is mounted on directly, and a run of cable lying across that same block would otherwise hand it a second delivery in the same tick.


**[9]** Records every machine touching this cable segment.

Every side, not the first one that answers. Other wiring is skipped because it is already a member in its own right, and a lamp is skipped for the same reason now that lamps conduct.


**[10]** Walks the Copper Wire links off a terminal, a Power Connector or a machine's built-in connector, enqueuing whatever they land on. A panel stud is driven from outside and is not a member; a link to anything that is no longer a terminal is proven dead and dropped.


**[11]** Enqueues the adjacent wiring a node conducts into by face.

Connectors, switches, lamps and ranges are always followed. Cable is followed only when `includeCable` is set, which every one of those device kinds does: a run landing on a device is a real junction and has to be reachable from the device's side as well as the cable's, or the grid a flood finds would depend on which block it started from. A cable node passes `false`, because cable continues into cable by its own run geometry in `spreadAlongRun`, never by bare face adjacency, which both misses a convex corner and fuses two runs that merely pass beside each other.

Open switches are enqueued rather than filtered here, and refused when they come off the queue, so the open-or-closed rule lives in exactly one place.


**[12]** Enqueues the cable this run actually continues into, by the renderer's own rule.

See `CableLinks.continuations`: the flood has to follow the same three moves the router lays and the renderer draws, or the grid and the picture disagree about what is wired. Colour is inside that rule, so a run of a different dye is stepped over exactly like a stranger's wiring.


**[13]** Enqueues every range within one block, diagonals included.

The only place in the whole grid that reaches past a shared face, and it is deliberate. Ranges are meant to be built in slabs, and the middle of a slab has no face left to wire, so twenty six neighbours rather than six is exactly what lets a three by three run off one cable landing anywhere on its edge.


**[14]** Marks this grid as driven by a Power Distribution Unit for the current tick.

A managed grid must not run the flat solve, or the PDU's tier ordering would be silently bypassed: every connector ticks and would serve the branch evenly before the PDU ever applied priorities. The mark is a tick stamp rather than a boolean so it lapses on its own if the PDU is broken or unloaded, and the branch falls back to ordinary flat behaviour with no cleanup.


**[15]** Runs the grid solve, at most once per tick no matter how many member nodes call it. Whichever node ticks first that tick does the work and the rest fall through.


## main/java/com/createstats/power/FeWireTerminal.java


**[1]** A block a Copper Wire can land on and chain through: a `PowerConnectorBE`, or a machine with a built-in connector such as the FE Fluid Pump. It is the one thing the wire item, the grid flood and the wire renderer need so they can treat "a connector" and "a machine that is its own connector" as the same kind of terminal, rather than special-casing each machine.

Every method here already exists on `PowerConnectorBE` under these exact names, so the connector implements this for free; a machine gains a built-in connector simply by carrying the same tiny link list and answering these.


## main/java/com/createstats/power/IFeConsumer.java


**[1]** Implemented by every block entity that owns an `FeConsumer`. The component itself is held by composition (the roster straddles plain block entities and Create's SmartBlockEntity, so there is no shared base class), and this interface is the common handle onto it.

It exists so code that does not know the concrete block entity type can still reach the consumer: the chunk-watch initial sync in `FePowerSync`, the capability registration in `FeCapabilities`, and any future grid aggregation all iterate block entities and filter on this interface.


**[2]** Called on the tick a hard cutoff happens, so the block can route into its existing safe-idle path (stop governors, clear projected light, release outputs). Implementations override this; the default is a no-op for blocks whose tick already returns early on `false`.


## main/java/com/createstats/power/MotorTier.java


**[1]** The two grades of Electric Drive: the inverse of the `DynamoTier` generator. Where a dynamo turns rotation into Forge Energy, a motor spends Forge Energy to turn a shaft, sourcing both a speed and a matching stress capacity onto the kinetic network it feeds. pinned to the same two Create speeds as `PumpTier` pumps: `MK_I` sources 128 RPM, `MK_II` sources 256 RPM (Create's top speed), each with a rated stress capacity so it can actually drive machinery, not just spin free. not a free perpetual-motion loop - a `DynamoTier.STANDARD` dynamo makes about `1.4 FE` per RPM, a motor spends several times that per RPM to make the rotation in the first place, so feeding a motor's shaft into a dynamo always loses energy. it moves power across the kinetic world, never mints it. both tiers cost the same 4 FE per RPM on purpose, so the bill scales with speed and nothing else (512 FE/t at 128 RPM, 1024 FE/t at 256 RPM) - lines up exactly with `PumpTier` FE for FE at both grades since the two are presented everywhere as a matched pair. `MK_II` was briefly priced at 6 FE per RPM, which broke that symmetry, made it the one device in the mod to exceed what a connector hop is sized to carry, and meant no wire grade could keep it running - nobody had a reason to ever build it.


**[2]** The SU-per-RPM coefficient handed to Create. Create multiplies it by the absolute generated speed to get the actual capacity, so returning `capacity / rpm` keeps the delivered capacity a flat `stressCapacitySu()` at the rated speed.


## main/java/com/createstats/power/PduAutoController.java


**[1]** The Power Distribution Unit's ACTIVE AUTO decision: which managed devices are allowed to draw this control step, given a measured supply picture and a demand per device. same reason `FeGridSolver` is pure over `FeGridNode`: the interesting bugs in a load controller aren't "does it compile", they're "does it flap across the admit boundary", "does CRITICAL survive a total outage", "does it restore five devices at once and brown out". none of that's observable from a block entity that needs a `Level`, a wire network and real capability lookups to run one step. here the whole decision's a method over records, so a test can drive a hundred control steps in a millisecond and check the run set each time.

two layers:

- **Safety.** below the reserve floor only CRITICAL draws, immediately, ignoring the min switching time. this is the Min-SoC analog - guarantees CRITICAL rides through no matter what the optimizer wants.

- **Admit optimizer.** devices ordered tier first then cheapest first, admitted down the list while demand stays inside budget. a running device drops only when it's actually over budget; a shed device restores only when it fits with burst margin to spare, supply's non-negative, and nothing else restored this interval. both directions gated by the dwell.

drop test (against budget) vs restore test (against budget - burstMargin) is the deadband - stops a device sitting exactly on the boundary from flapping admit/drop every other step.

player overrides: `FeLock` sits above both layers. `FeLock.FORCE_ON` devices are admitted before the optimizer runs and charged against budget first, so the optimizer only sees what's actually left over. `FeLock.FORCE_OFF` devices are neither admitted nor counted as demand - a machine the player switched off isn't a load. both overrule everything, including the reserve floor: an explicit player instruction beats a heuristic.

This object owns the switching history, so it is stateful across steps but has no reference to the world. The history is transient, like the shed flags it drives: a reload starts every device admitted and lets the first control step sort them out.


**[2]** Tier ascending, then cheapest first inside a tier, so a maxed tier keeps the most devices alive and sheds the hungriest rather than dropping whichever happened to be listed last. The key is the final tiebreak so the order is total and the decision is reproducible.


**[3]** Locked-on devices are settled first, before a single byte of budget is handed out, because they are not competing for it: they are a fixed cost the optimizer has to work around. Any other order would let a merely-important device claim budget a must-run device then takes anyway, and the arithmetic below would be describing a branch that does not exist.


**[4]** `desired` is what the rules want; `target` is what actually happens after the dwell. Keeping them apart is what lets the panel distinguish "shed because supply ran out" from "held because the anti-flap timer has not elapsed", which look identical from outside and call for completely different responses from the player.


**[5]** Ride the reserve: drop only when even generation plus the reserve cannot cover it. No burst margin here, because applying it in both directions would shed a device the branch can demonstrably afford.


## main/java/com/createstats/power/PduBranchGuard.java


**[1]** Server-side guard for PDU bulk actions - keeps only positions actually on this PDU's branch. pure/standalone so it's testable without a world, and the block entity + packet share one "on the branch" check instead of two that drift apart. stops a crafted packet reaching into someone else's grid.


**[2]** The requested positions that are members of the branch, in the order they were requested, duplicates and all. A position not in `branch` is silently dropped rather than refused loudly, because the honest cause is almost always a stale client whose roster is a tick behind, not an attack, and the ones that are attacks deserve no acknowledgement either.


## main/java/com/createstats/power/PduDeviceIds.java


**[1]** Stable per-device numbers, so fifteen identical machines on one branch can be told apart. a roster of fifteen rows all saying "Induction Range" isn't a list, it's a wall - can't tell which one got shed, can't find it in the world, can't tell next sync if it's the same one or a different one shed now. numbers have to be sticky, not derived. numbering by position means inserting one machine renumbers everything after it; hashing the position gives numbers that never move but collide. so instead a number's assigned once and kept - lowest free number for that device name, handed out the first time it's seen, never reused while it's still around. insert = next free number, nothing else moves. leaves = number's free for the next one. scoped per display name (Induction Range ID:01-15, Strip Lamp ID:01-03, not one global counter), since the number answers "which of these", and "these" means the ones that look alike. pure, keyed only on positions + names, so assignment can run a hundred add/remove cycles in a test and check the one thing that actually matters: an untouched device never changes number.


**[2]** Above this many remembered devices, entries no longer on the branch are dropped.

Not pruned on every rebuild, deliberately. A switch opening takes every device beyond it off the roster for as long as it stays open, and pruning eagerly would free their numbers and hand them out again in a different order the moment the switch closed. Holding the assignments until there is an actual reason to reclaim memory means the ordinary case, wiring being switched about, never renumbers anything.


**[3]** Everything already spoken for under this name, whether or not it is on the branch this instant. Reusing a number held by a device behind an open switch is exactly the renumbering this class exists to avoid.


**[4]** Numbers everything on the branch that has none, lowest world position first. order's forced and nothing's saved because nothing here can persist - the block entity's save tag is also its update tag, so a thousand-position table would go to every client on every sync just to carry info the roster already carries per row. instead the assignment's made reproducible: numbering in world position order means a reload lands on the same numbers every time, no bytes stored. doing it in one pass up front instead of lazily during the roster walk is what makes that true - the roster's own order comes from a flood fill, and letting that decide numbering would tie the answer to a traversal order nobody promised to keep stable.


**[5]** How the number reads on a row: `"Induction Range ID:07"`.

Zero padded to two digits so the numbers form a column a player can scan down, rather than a ragged edge where ID:9 and ID:10 sit at different widths. A device with no number reads as its bare name, which is the right answer for a one of a kind block: numbering the only Power Distribution Unit on the branch "ID:01" is noise.


## main/java/com/createstats/power/PduLegPlan.java


**[1]** The per leg screening pass: what each of a panel's eight output legs can physically carry, decided before anything is asked about whether the board has the generation to run it. separate pass, not part of the controller, because a panel with eight legs has two constraints of completely different kinds, and the whole trustworthiness of the panel rests on telling them apart.

- **Per leg capacity.** Leg 3's wire carries what it carries. If the machines on leg 3 want more than that, some of them cannot run _no matter how much generation exists_. The fix is to run another leg, or upgrade this one's wire.

- **Board wide supply.** All eight legs draw on one buffer fed by two inputs. If the total exceeds what is coming in, something must give. The fix is more generation.

these want opposite answers and get judged on different sets. capacity's judged **within** a leg (a full leg 3 says nothing about leg 5). supply's judged **across** every leg at once, since a Critical machine on leg 7 has to outrank an Optional one on leg 1, and a controller run once per leg could never see that - it'd happily keep eight Optional machines alive on eight separate legs while shedding a Critical one. so capacity screens first, supply decides second. everything this pass drops is dropped for a reason generation can't fix, so removing it before the supply budget's computed isn't just an optimization - it stops the board reserving budget for a machine whose own wire was never going to carry it, which would then get spent on nothing while a machine that could've run gets shed for want of it. controller's untouched because `PduAutoController` is pure, tested, and the reason this power system is trustworthy. it takes one budget and one flat list, exactly right for the supply question, and can't express "leg 3's full but leg 5 has room". rather than teach it about groups and risk the flap/dwell/ride-through behaviour its tests pin down, this pass hands it a list that's already had the physically impossible removed. it never learns legs exist.


**[2]** Force on is charged against the leg like anything else. A player instruction can outrank the optimizer, but it cannot outrank the conductor: the wire carries what it carries, and pretending otherwise would have the panel promise power it cannot move.


**[3]** Locked on first, then tier, then cheapest, then key.

The key tiebreak is what makes the order total, and therefore the decision reproducible: two identical machines on one leg must not swap places between ticks, or the pair would take turns being the one that does not fit.


## main/java/com/createstats/power/PduMode.java


**[1]** How a Power Distribution Unit serves its managed branch.

- `OFF`: flat fair share, the branch behaves as if there were no PDU.

- `MANUAL`: the player's tiers are honoured and whole tiers are shed from the bottom when the buffer cannot reach them.

- `AUTO`: the supply aware controller runs the most important set of devices the measured generation can sustain, sheds the rest, and moves the line live.


## main/java/com/createstats/power/PduReason.java


**[1]** Why one managed device is in the state it's in, per the PDU. a bare "SHED" starts arguments - player sees it's off but not whether that's supply actually gone, reserve floor tripped, anti-flap dwell, or they forgot they switched it off. four different problems need four different answers. synced on the roster instead of recomputed client-side, since only the server makes this call.


**[2]** Shed: generation is fine, the branch wiring cannot carry any more.

Worth its own reason rather than being folded into `OVER_BUDGET`, because the two have opposite fixes. One says build more generation; this one says the trunk connector on the panel's output stud is saturated, and no number of extra dynamos will help. Reporting a wire limit as a supply shortage sends a player off to rebuild the wrong half of their base.


**[3]** Off because this output leg's breaker is open or has tripped.

Not counted as shed, for the same reason `LOCKED_OFF` is not: shedding is the panel running out of something, and an open breaker is a switch. Folding the two together would have a board with one leg deliberately isolated permanently reporting itself as short of supply, which is a panel crying wolf at its own owner.


## main/java/com/createstats/power/PduStuds.java


**[1]** The eight output studs on a Power Distribution Unit's bus bar: where each one is, and which one a player is pointing at. read straight from the model, not invented - every number here is lifted out of `assets/create_stats/models/block/power_distribution_unit.json`, where the studs have been drawn since long before they did anything: a 2x4 grid on a bus mounting pad, at x 2.75-4.25, 5.75-7.25, 8.75-10.25 and 11.75-13.25, on z 6.25-7.75 and 9.25-10.75. if the model ever moves, these move with it, and the highlight points at the wrong box until they do. rotation's the hard part - the blockstate draws one model and turns it (north 0, east 90, south 180, west 270), so a hit position in world space has to turn **back** into the model's own frame before it means anything, and getting that backwards is completely invisible in a screenshot from one angle. shows up as the highlight tracking right on a panel facing one way and mirrored on another - the kind of thing nobody notices until they've built two. pure and tested for exactly that reason.


**[2]** Eight, because the model draws eight.

This used to be defined as the cable channel count, back when a stud read one conductor out of a bundle. Cable carries one circuit per block now and the studs are Copper Wire terminals, so the number belongs to the art it came from and to nothing else.


**[3]** Turns a point in the block's own space back into the model's unrotated frame.

The inverse of what the blockstate does. North is the authored orientation and needs nothing; the other three undo their own rotation about the block centre.


## main/java/com/createstats/power/PduStudWiring.java


**[1]** The rules for stringing a Copper Wire to one of a Power Distribution Unit's eight output studs. pure and separate because a stud's now a wire terminal exactly like a Power Connector, so the two-click flow in `CopperWireItem` has two kinds of endpoint instead of one. everything deciding whether a proposed span's legal, and what a leg can carry once it's strung, is arithmetic over a handful of numbers - no level, no block entity, no side. kept here it's unit tested, and the item and the panel can't drift apart about what "wired" means, which is the failure that'd show up in game as a wire you can draw but that carries nothing. resolving _which_ stud a click landed on is `PduStuds.pick`, already pure and tested for the same reason. this is the layer above it: given a stud, may this wire exist, and what does it rate.


**[2]** True for a panel end, whatever index it carries.

Anything that is not exactly `CONNECTOR` is a stud, including a nonsense index. The alternative, treating a negative index as a connector, would quietly turn a click that resolved to no stud into a link between two connector ends, one of which is a panel: a span that passed every check and could never carry anything. Better to keep it a stud and let `check` refuse it by name.


**[3]** Two studs on one panel, or on two panels. Neither is a load, so a span between them would be a wire that can never carry anything, and on one panel it would also be a second path between two legs that are meant to be independent circuits.


**[4]** The best grade among a stud's wires, or null when it has none.

Best rather than worst, matching `WireTier`: adding a wire must never make a leg slower, or a player would be punished for stringing a spare cheap span off a well built stud.


## main/java/com/createstats/power/PumpTier.java


**[1]** The two grades of FE Fluid Pump, and the Create pump speed each one matches. a Create Mechanical Pump applies a pressure equal to its rotation speed, and a fluid pipe then moves `max(1, pressure / 2)` mB per tick (the exact figure the mod's own Flow Meter reproduces from `PipeConnection.getPressure()`). so a Create pump spun at 128 RPM moves 64 mB/t, one spun at 256 RPM (Create's top speed) moves 128 mB/t. the two pumps are pinned to those two numbers: `MK_I` tops out where a 128 RPM Create pump does, `MK_II` where a 256 RPM one does. matching a device players already understand is what makes the throughput mean anything, same as `DynamoTier` being pinned to the Crafts and Additions alternator.


**[2]** The most pressure this pump can put on the pipe it sits in, equal to the RPM a Create pump would need to match it. The power dial runs from zero to here.


**[3]** The most this pump can move, in mB per tick. Create moves `max(1, pressure / 2)`, so at full pressure this is simply half the pressure: 64 for MK I, 128 for MK II.


## main/java/com/createstats/power/WireTier.java


**[1]** The grade of one wire span, and of the connectors it is strung between. a tier actually changes throughput because a hop moves as much as its weakest part allows, so a connector runs at `min(its own tier, the best wire attached to it)`. upgrading only the wire, or only the connectors, buys nothing - both ends and the span between them have to be rated for it. that's the point of splitting the upgrade across two items instead of one. **best** wire rather than worst, on purpose. taking the worst would mean stringing one cheap wire off a well built node quietly throttles everything else attached to it, miserable to diagnose. taking the best means adding a wire can never make a grid slower, and the only way to be disappointed is to under-build, which the panel already reports.

The grid solve is pooled per node rather than per link (see `FeGridSolver`), so a tier lands on the node's rate rather than on an individual span. Storing it per link anyway is what lets a wire drop back as the right item and draw in its own colour.


## main/java/com/createstats/registry/ModArmorMaterials.java


**[1]** Pilot Helmet armor material, modeled after a Diamond Helmet with a small structural improvement to honour its heavy-end-game crafting cost:

- **Defense**: integer values must match a vanilla bucket, so we keep the helmet at 3 (same as Diamond / Netherite helmets).

- **Toughness**: bumped to 2.5F (Diamond's 2.0F + the user-requested +0.5 protection delta). Still below Netherite's 3.0F, so it sits cleanly between the two.

- **Knockback resistance**: matches Diamond at 0.0F; no free Netherite-style knockback immunity.

- **Enchantability**: 15 (Iron/Diamond tier, enchants well without breaking parity).


## main/java/com/createstats/registry/ModBlockEntities.java


**[1]** One block entity for all three grades: they differ only in output and stress, both of which are read off the block. Three types would mean three capability registrations and three chances for a grade to be quietly left out of one of them.


## main/java/com/createstats/registry/ModBlocks.java


**[1]** Three grades, one block class. Everything that differs between them is in CableTier, so a fourth is a line here and an entry there rather than a new block to keep in step.


## main/java/com/createstats/registry/ModCreativeTab.java


**[1]** Experimental, in-development items (VCC Mk II, Stats Display Mk2, Hinge) stay out of the creative menu unless the player opts in via config; they remain /give-able either way. Filtered by ITEM identity because a block's item is itself a tracked item entry in this tab, so the item pass below would re-add anything the block pass skipped by block identity alone - both passes must consult the same hidden set.


## main/java/com/createstats/registry/ModItems.java


**[1]** Build the pilot helmet, choosing the GeckoLib-animated variant when GeckoLib is installed and the plain vanilla-armor variant otherwise. The GeckoLib class is referenced ONLY inside the `isLoaded` branch so it is never class-loaded (and cannot crash) without GeckoLib.


## main/java/com/createstats/renderer/AntennaRendererHelper.java


**[1]** Structural parts sit in real world lighting so the antenna reads as solid and grounded instead of glowing uniformly (the old full-bright pass made it look floaty). Only the signal tips stay emissive (see FULL_BRIGHT usage in drawAntenna / drawTopStatusTab).


## main/java/com/createstats/renderer/BattleScreenDisplayRenderer.java


**[1]** Phase C+, voxel hologram floating above the bridge block, same data + look as the panel-face hologram. Rendered after the rings/pips so it sits above them visually; uses its own pushPose/popPose internally.


## main/java/com/createstats/renderer/CableRenderer.java


**[1]** Draws surface cable: a slim cord on each occupied face, running toward whichever neighbours the run actually continues into. drawn here instead of by a block model because which way a cable runs on a face depends on its neighbours, and neighbours can't be blockstate properties - 6 faces x 4 in-plane directions is 24 booleans, 16 million blockstates. so the state only carries which faces are occupied, and the run direction gets worked out here where the neighbours can just be looked at. one piece per direction of travel, never two in the same place. obvious way to build this is a hub at face-centre plus an arm to each neighbour - also wrong, and behind every complaint this cable ever got: hub and arms overlapped by a pixel, so a straight run was two boxes deep along a seam that z-fought, and an elbow drew the same corner cube twice. hub's square too, so the shading rule had no short side to pick and just guessed - turned 90 degrees at every hub, straight runs came out banded like they'd been chopped up. so the centre isn't its own piece anymore. each face emits **one bar** along whichever axis the run passes through (hub + both collinear arms merged in), plus a separate branch for any remaining direction. straight run = one box, no seam. elbow = two boxes that butt. nothing overlaps. corners are owned - where two faces meet, both want the little corner cube. `CableLinks.ownsCorner` gives it to exactly one, the difference between a corner that flickers (drawn twice) and one with a notch in it (drawn by neither). real geometry, not decals - every piece is a box standing off its mounting surface, textured and lit per face. an earlier version drew flat quads in the surface plane, a fundamentally different thing: no thickness so it read as paint on the wall, and coplanar with the block behind it so it z-fought. a cable needs a visible edge to look like a cable.


**[2]** The cord's half width is not a constant any more: it comes from the grade (CableTier), so a heavier cable is visibly heavier. Saddle clips used to be drawn on every arm at 5.4 px wide, which made them wider than the cord itself and read as a lumpy chain; they are gone from the block entirely and survive only on the item icon, where they have nothing to compete with.


**[3]** one circuit per block, one cord per face. dye = the circuit, drawn exactly as chosen - no tint, no default hue, nothing between the player and their color choice. load just warms it while it's actually working.


**[4]** The cord's colour, warmed by how hard the run is working.

An idle cable is exactly its dye. A busy one is lifted toward white, and one being asked for more than it can carry goes amber, which is the one state worth interrupting a player's build for. It is deliberately a shift of the colour they chose rather than a colour of its own: this has to read as "your orange cable is working hard", not as "some other cable is here".

The figure behind it is a synced byte, not a live network read. A client cannot see an `FeWireNetwork` at all (they are only ever built server side), so anything drawn from one would be a silent zero on every cable in the world.


**[5]** A band of light travelling along the run, at a speed set by how hard it is working. The phase is the block's own position summed, so neighbours along any axis are one step apart and the band marches along the cable rather than flashing the whole run at once. It is the cheapest possible way to show direction and effort: no per-vertex work, no extra geometry, and it costs nothing at all on an idle cable because that returns above.


**[6]** Prefer the axis the run passes straight through, so a straight run merges into one box and a tee keeps its through run unbroken with the branch butted onto its flank.


**[7]** The bar runs down the centre of the face at the grade's own width. There is one circuit per block, so there is nothing to offset it against and nothing to share the face with.


**[8]** The gland and the three fine conductors where a run enters a fitting.

Nothing here overlaps anything: the gland butts against the cord's end and covers it, so the cord draws no cap there, and the strands are spaced far enough apart that their half pixel widths cannot touch. The same discipline as everywhere else in this renderer, for the same reason.


**[9]** The collar. Its inner face is drawn and the cord's is not, so the two never share a plane. Always at least half a pixel fatter than the cord it grips: on the heavy grade a fixed collar would be narrower than the cable running through it, which reads as a pinch.


**[10]** Three conductors, thin in both directions across the run so they read as wires rather than as ribbons, and set into the middle of the slab so they stand clear of the surface.


**[11]** A branch off the flank of the bar.

It starts at the bar's side rather than at the centre, so the two never occupy the same space, and its inner end face is dropped: that face lies exactly in the bar's flank, where it would be a second surface at the same depth pointing the other way.


**[12]** How far the cable reaches in one direction and whether its end is visible, or null for nothing that way.

The four cases finish in four different places, which is the entire reason `CableLinks.Link` is a kind and not a boolean.


**[13]** The same rule the grid floods along, colour included: a run of another dye passing by is a different circuit, so the cord is drawn ending here, because here is where it stops conducting. What you can see is what conducts.


**[14]** Meets the neighbour's arm at the block boundary. Same grade, the two make one continuous bar so neither draws an end there (two coincident end faces is what z-fights). A thinner neighbour cannot fill this grade's end, so the heavier side caps it and the thin run plugs into the centre of that cap: a reducer, not a hole.


**[15]** Outside corner. The owner overhangs the edge by one thickness to fill the corner, and that overhang is the only end anyone can see here, so it keeps its cap. A non-owner into a thinner grade caps too, for the same reducer reason as a flat join.


**[16]** Into a machine, as far as its body, which is rarely at the block boundary. Where there is room, the last few pixels become a pigtail: three fine conductors leaving the fitting's terminal and gathering into the one cord, which is how a light fitting is actually wired and what makes the join read as a connection rather than as a cable that happens to touch a lamp.


**[17]** Emits a box given its extent along the mounting normal, along the run axis, and across.

Kept in terms of named axes rather than raw x/y/z because the same geometry has to work on all six faces, and hand-writing six coordinate permutations is how a renderer ends up correct on the floor and subtly wrong on the ceiling.


**[18]** Emits the faces of an axis-aligned box, less the ones in `skip`.

Faces are dropped rather than merely hidden. Anything lying against the mounting surface, or butted against the next piece of cable, is a surface nobody can ever see but which the depth buffer still has to arbitrate against whatever sits at the same depth behind it. Removing them is what makes a join silent.


**[19]** The cord texture is graded across its width and uniform along its length, so the SHORT side of each face has to be the side that samples the whole gradient. Scaling UVs by block size instead meant a 2 px cord sampled u,v in [0, 0.125]: the top-left two pixels of the texture, which is solid highlight. Every face came out one flat tone, which is why the cable read as a plain coloured bar with no shading at all.


**[20]** The jacket colour for a dye.

Not the dye itself. Vanilla's dye colours are each the loudest version of themselves, built to be read as a block of wool from across a room, and a run of cable painted in one at full strength was the brightest object in any scene it appeared in: traffic-cone orange laid across a field of muted greens and browns. It does not sit next to Create either, whose entire palette is weathered brass, gunmetal and copper going green.

So each channel is pulled a third of the way toward a warm dark grey and then held under full value. One operation applied uniformly, which matters more than any individually chosen colour would: the sixteen stay clearly distinguishable from each other, they stay in the same key as one another, and none of them shouts.


**[21]** Widened, because arms deliberately leave this block: into a machine to reach its body, and past an outside edge to fill the corner. At the default one-block box, both would blink out the moment the cable's own block left the frustum, which is a flicker along every wall you walk past.


## main/java/com/createstats/renderer/CapacitorBankRenderer.java


**[1]** Draws a merged Capacitor Bank's face as one instrument spanning the whole structure.

The block texture is a plain seamless shell. A gauge whose length follows how tall you built the bank, and whose content follows the charge, cannot be baked into block textures: that is horizontal position times vertical position times fill level, which explodes. Create's Item Vault sidesteps this because its connected textures carry only shell continuity and no data. The Create Crafts and Additions accumulator is the closer model, and it draws across the structure rather than tiling. So does this.

three presentations, by how much room there is to say something:

- **One block wide:** an LED column, continuous over the full height. There is no room for text on a 16 pixel face, so the bar is the whole readout.

- **Two wide:** a compact instrument. Headline percentage, charge bar, stored over capacity, and a live throughput line.

- **Three or more wide:** the full panel, adding a per-layer distribution graph showing where the charge physically sits in the stack, a time to full or empty, and the cell count.

two rendering traps this class exists to remember: coplanar quads stacked on each other z-fight, and no depth offset small enough to look flush survives depth precision at distance. so the face is **partitioned into disjoint rectangles** that tile it exactly once - borders are four strips, gauge gaps are their own cells, tick marks are gaps between segments. nothing overlaps. and `RenderType.leash()`, convenient as it is as an untextured colour format, is a `TRIANGLE_STRIP` - every four vertices chain into the previous rectangle and shred the face into crossing triangles. flat panels need `QUADS`, hence `RenderType.entityCutoutNoCull` against a white texture with per-vertex colour.


**[2]** Puts the pose at a face's bottom-left corner as seen from outside, with local +X to the right, +Y up and +Z out of the surface. Every face then draws with identical code, and text comes along for free, which direct vertex emission could not offer.


**[3]** An LED spine down the left, full height. It carries the charge at a glance from across a base, ties the wide face to the thin one, and means the readout module no longer has to stretch to fill the wall on its own.


**[4]** The casing beside the spine: a header, a fixed-size readout module sat at a readable height, and cooling louvers filling whatever is left.

The point of the louvers is that the instrument **does not have to fill the face**. Stretching one screen over a six block wall left more than half of it as flat black void, which read as unfinished. Real gear puts a modest display in a casing and gives the rest over to hardware, so the leftover space becomes deliberate rather than empty.


**[5]** readout module: a fixed, readable size, parked about a third of the way up so it lands near eye level on a tall bank instead of riding at the top of a six block wall.


**[6]** The screen is tiled by three disjoint horizontal bands rather than by a background with the bar painted over it. An earlier version filled the whole screen and then drew the bar on top at the same depth, which is exactly the coplanar overlap this class is supposed to avoid, and it showed up in game as a dithered shimmer crawling across the bar.


**[7]** One axis-aligned rectangle in face space. All rectangles here are coplanar and disjoint, so they tile without fighting. Four vertices, complete attribute set: `entityCutoutNoCull` silently corrupts the buffer if any of colour, uv, overlay, light or normal is missing.


**[8]** Draws text with its baseline bottom-left at (u, v) in face space. The pose is flipped in Y because the font draws downward, and `POLYGON_OFFSET` keeps glyphs off the panel behind them without needing a depth nudge of our own.


## main/java/com/createstats/renderer/DispatchBoardRenderer.java


**[1]** In-world renderer for the Dispatch Board multiblock wall. Only the controller renders; members skip rendering entirely.

Renders a dynamic texture quad on the board face that shows tier-aware station dispatch information: header, rows, cargo, history, train roster, and exception indicators. The texture content is managed by `DispatchBoardTextureManager`, which paints tier-specific layouts using `BitmapFont` and `TextureGraphRenderer` utilities.


**[2]** Rotate to face the correct direction. DispatchBoard FACING = display-pointing direction (NORTH means display faces north, block against south wall), which is the opposite convention from StatsDisplayPanel. Adding 180° compensates so that the same z-offset pushes the quad toward the viewer on the correct side.


**[3]** Push the quad in front of the model's display face. Model slab: z=11-16 (5px deep), front face at z=11/16. Clearance 0.03125 (= 0.5 model-pixel) matches StatsDisplayPanel.


## main/java/com/createstats/renderer/DispatchBoardTextureManager.java


**[1]** Manages dynamic textures for Dispatch Board multiblock walls. Renders tier-aware board content into NativeImage textures that are displayed as textured quads by `DispatchBoardRenderer`.

Follows the same lifecycle pattern as `FactoryLogicControllerTextureManager`: create-on-demand, periodic refresh, stale cleanup, world-unload disposal.


## main/java/com/createstats/renderer/FeMotorRenderer.java


**[1]** Draws the `FeMotorBE`'s moving parts on top of its static housing model:

- the output **shaft half** on the FACING side, spinning at the network speed the motor is itself sourcing, so it visibly turns whenever the motor is running;

- the **Copper Wires** strung to the built-in connector, drawn the same way a Power Connector and the FE Fluid Pump draw theirs.


**[2]** Where a wire meets this motor: the centre of the copper stud pair on its junction box, in block-local units. Mirrors `PowerConnectorRenderer.attachPoint`, including its fallback to the block centre for a null or unexpected state, which happens for a heartbeat when a link points at a block that has just been broken.

The stud pair sits off to one side of the housing rather than on its axis, so unlike the Power Connector's terminal this cannot be derived from the facing with a step vector: the whole point is rotated with the model. These six are generated by `python gen_fe_motor_model.py --java` from the stud geometry itself, so they move when the studs do.

Public so the other end of a wire can resolve this motor too: a cable is drawn once, by whichever end has the smaller packed position, and a Connector drawing one to a Motor has to land on the same studs the Motor would have drawn from.


## main/java/com/createstats/renderer/FlowMeterRenderer.java


**[1]** Brings the Flow Meter to life: an additive glow layer painted over the static copper/brass model. Everything is driven by the live reading, so it reads as a real instrument.

- **Sight-glass**: the four glass faces glow in the carried fluid's colour, brighter with throughput; when idle it settles to a slow techy "heartbeat" shimmer.

- **Flow pulses**: thin bright rings travel along the pipe in the direction of flow, faster and more frequent the higher the rate (the "data / energy moving through" read).

- **Gauge tick**: a small emissive nub on the housing pulses with the beat.

Rendered with `RenderType.lightning()` (additive, unlit, back-face culled) so it glows in the dark and layers cleanly over the solid model.


**[2]** local +Y maps to world +Y/+Z on the Y/Z axes, but to WEST (-X) on the X axis (Axis.ZP 90deg), so flip the travel sign there to keep rings following the real flow.


**[3]** RenderType.lightning() culls back-faces, so emit BOTH windings: whichever side faces the camera renders and the other is culled. They never render together, so there is no z-fight. (The flicker earlier came from a SECOND, separate coplanar band stacked on top - not from this.)


## main/java/com/createstats/renderer/FluidPumpRenderer.java


**[1]** Spins the FE Fluid Pump's rotor on top of its static housing, about the vertical axis, at a rate that follows the throughput. A pump making no flow, because the dial is at zero, the grid is empty or the panel shed it, sits dead still, so the moving part is an honest read of "is this pump actually doing anything" from across the room and from above.


**[2]** The Copper Wires strung to the pump's built-in connector, drawn the same way a Power Connector draws its own so a wire looks identical whichever terminal it lands on. Each pair is drawn once, by the terminal with the smaller packed position.


## main/java/com/createstats/renderer/HologramDisplayMode.java


**[1]** NETWORK is retained in the enum only so that HologramDisplayMode.valueOf("NETWORK") does not throw during NBT deserialization of old saves.  HologramSettings.load() immediately normalises it to LINE_3D, so NETWORK is never the active mode at runtime.


## main/java/com/createstats/renderer/HolographicProjectorRenderer.java


**[1]** rotationSpeed > 0: free spin at rotSpeed degrees/tick in world-space. Both faces of the quad are rendered, so front and back are visible during the cycle. The panel will appear edge-on briefly at ±90°, which is expected for a spinning hologram.


## main/java/com/createstats/renderer/InductionHeaterRenderer.java


**[1]** The heater only ever connects on its DOWN face (see `com.createstats.block.InductionHeaterBlock.hasShaftTowards`), so unlike a through-shaft block this renders exactly one spinning `SHAFT_HALF`, facing down, instead of iterating every direction on the rotation axis.

It also draws the cooking station's stack lying flat on the plate, vanilla CampfireRenderer-style (flat item, FIXED context), with one visual layer per 16 items so a full stack of 64 reads as a proper pile of food rather than a single lonely chop.


**[2]** Drop the shaft half a pixel so its end cap clears the heater's own bottom face; otherwise the two are coplanar and z-fight against the block's bottom texture. The block spins about the vertical axis, so a pure Y offset is unaffected by the rotation.


## main/java/com/createstats/renderer/InductionRangeRenderer.java


**[1]** Draws whatever is cooking, lying on the trivet. needed a trivet first because the Induction Heater lays its stack straight on a flat plate, which works since the plate's flat. this hob's element is nine solid bars standing proud of its plate, so an item at plate height would sit _inside_ the glowing geometry - half buried, z-fighting along every face it shared with a bar. so the model got a grill standing clear above the element, and food sits on that, with a visible pixel of air between it and the heat. same pile treatment as the heater, on purpose - two blocks doing the same thing should look like they do the same thing, and a player who's used one shouldn't have to learn the other.


**[2]** One visual layer per started three items, so a batch of eight reads as a pile rather than as one lonely chop. The hob holds eight, where the heater's plate holds a full stack, so the divisor is smaller to get the same sense of quantity from a smaller batch.


## main/java/com/createstats/renderer/KineticDynamoRenderer.java


**[1]** The parts of a dynamo that move. exists here instead of in the model because a generator visibly doing nothing while a shaft spins into it is the single loudest way for a machine to feel dead. Create's whole visual language is built on things you can watch work - cogs turn, belts run, the press comes down. a block that only changes a number in a tooltip doesn't belong in it. so the rotor and shaft stub are drawn here, at an angle taken from the actual shaft speed, and the static model's had those pieces removed. the two halves have to stay in step - draw a piece in both and it'll be there twice, once turning and once not. making rotation legible: a smooth drum spinning at 256 RPM looks exactly like a smooth drum standing still. every moving piece carries something that breaks its silhouette (ribs on the armature, blades on the fans) so there's an edge for the eye to follow round.


**[2]** Create's own shaft texture, not a copy of it.

The stub has to be indistinguishable from the shaft it meets, and the surest way to be indistinguishable from something is to be it. A hand-drawn near-match was visibly a different shade of grey against the real thing, which is worse than no shaft at all: it reads as two parts that do not fit rather than as one continuous drive.


**[3]** Create turns at `speed` RPM, which is three tenths of a degree per tick per RPM. Taking the angle from the world clock rather than accumulating it per frame means every dynamo on a network is in phase and none of them drift after a lag spike.


**[4]** The drive stub, protruding below the block so it visibly meets whatever is turning it.

**Four** pixels across, from 6 to 10, which is Create's shaft exactly: its model runs `[6,0,6]` to `[10,16,10]`. The first version of this was six across and stood visibly proud of the shaft it was supposed to continue, a step in the middle of a drive line. Anything meant to read as one part with a Create part has to take its dimensions from that part rather than from what looks about right.


**[5]** A four blade cooling fan: a hub with blades out along each axis.

Four boxes rather than one rotated blade repeated, because every piece here is already inside one rotation and nesting a second per blade buys nothing at this size.


**[6]** One box in model pixels, less an optional buried face.

UVs come from the box's own size so texel density holds whatever the piece: a long thin blade gets a long thin slice of its texture rather than one copy squashed into it.


**[7]** Guards the split between this class and gen_dynamo.py. If a grade gains a moving part, its static counterpart has to come out of the model in the same change.


## main/java/com/createstats/renderer/KineticFlywheelRenderer.java


**[1]** Renders the `KineticFlywheelBE`'s moving parts on top of its static housing model:

- the heavy spinning **wheel** (`ModPartialModels.FLYWHEEL_WHEEL`) rotating about the FACING axis at the BE's stored RPM, so it visibly coasts down while discharging;

- a connection **shaft half** on the FACING side spinning at the network speed.


**[2]** One disc per mass stage (count = tier + 1). The wheel model is ~0.5 blocks deep along its axle (z 4..12), so when stacked we FLATTEN each disc and spread it across that same 0.5 band: this reads as a clean row of plates instead of overlapping full-depth blobs that z-fight.


**[3]** Heavier assemblies read as slightly larger in diameter and glow a touch more. Kept small so even the max tier stays flush inside the block (the model's counterweights already near-fill it).


**[4]** Layer a soft state glow onto the block-light: discharging glows and DIMS as the reserve drains, charging BUILDS as it fills, idle adds nothing. Drives the "read charge across the room" feel.


**[5]** Active wheels self-illuminate so they clearly GLOW at night (a block-light boost alone reads as merely "lit"). Lift to a high, gently pulsing floor; the colour comes from the tint.


## main/java/com/createstats/renderer/PowerConnectorRenderer.java


**[1]** Renders the copper wires between linked Power Connectors as sagging 3D cables. Each cable is a cross-section tube (a horizontal plus a vertical ribbon) built with the leash render type, shaded copper with alternating strands so it reads as a twisted wire rather than a flat line. Each pair is drawn once, by the connector with the smaller packed position, to avoid double rendering.


**[2]** Both ends are resolved, so a wire lands on each connector's terminal even when the two face different ways or sit at different anchors. Drawing to a fixed block centre made every wire sprout from the middle of a block instead of from the copper knob.


**[3]** Where a wire meets this connector: the centre of its copper terminal, in block-local units.

Falls back to the block centre for anything that is not a connector, which happens for a heartbeat when a link points at a block that has just been broken.

Public because the Power Distribution Unit draws the wires on its own studs, and a wire that landed on the middle of a connector at one end and its terminal at the other would read as two different kinds of wire.

This is also the resolver every other terminal block uses for the far end of its wires (the FE Motor, the FE Fluid Pump, the panel), which is why the FE Motor is answered here rather than only inside its own renderer: a pair is drawn once, by whichever end has the smaller packed position, so a motor whose wire happens to be drawn by the connector still has to come back with the motor's studs.


**[4]** The buffer every wire is drawn into: a textured, lit, no-cull layer, so a wire reads as solid round cable and looks the same off a connector, a panel stud, or the pump.


**[5]** One taut span between two points, in the caller's block-local space, drawn as a textured, light-shaded cable: a small-slack catenary sampled into segments, each a crossed pair of quads carrying the grade's colour and the wire sprite. Replaces the old flat, unlit leash ribbons.

Public for the same reason `attachPoint` is: a panel's stud wires are the same wires, and drawing them a second way would make one grade of wire look like two.


## main/java/com/createstats/renderer/PowerDistributionUnitRenderer.java


**[1]** The PDU's front instrument face: buffer bar, one lamp per priority tier, and the served/shed roll-call.

Follows the same two rules the Capacitor Bank face settled on. Every rectangle is **disjoint**, so nothing z-fights, and quads go through `RenderType.entityCutoutNoCull` against a white texture rather than `RenderType.leash()`, which is a triangle strip and shreds coplanar panels.

Motion is reserved for faults, per ISA-101: a healthy branch is completely still, and the only thing that ever blinks is a shed lamp, slowly.


**[2]** The model's screen element is [3,6,4] -> [13,14,5] with the lit face on north, so the visible glass sits at z = 4/16. These are expressed relative to the block CENTRE, because the pose is rotated about the centre: an earlier version rotated about the corner, which put the whole panel at world z ~0.69 (buried inside the block) and mirrored it on X, so nothing was visible.


**[3]** Before the pose is turned to face the glass: the wires live in ordinary block-local space, and rotating them with the instrument face would swing every leg off its stud.


**[4]** Rotate about the block centre, then step out to the glass. Local +X is right as seen from outside, +Y up, +Z out of the face, and the origin is the centre of the screen.


**[5]** Everything below is laid out from the top down against this cursor, which is the top edge of the next element. The previous version mixed absolute offsets and ended up drawing the title about half a pixel above the glass, on the bezel.


**[6]** Title, with the mode right-aligned beside it. Which mode the panel is in is the first thing you want to know from across a room, and it was the one thing the face never said.


**[7]** One lamp per tier, CRITICAL on the left, labelled underneath. Green when the whole tier is served, steel when part of it is, amber and slowly blinking when none of it is, red when there is no input at all, and dark when no device sits at that tier.


**[8]** Roll-call, anchored to the bottom edge rather than flowed. The block above it is a different height in AUTO than in the other two modes, and a line that slides up and down the glass as the mode changes reads as a layout accident rather than a design.


**[9]** The wires strung from the eight output studs, drawn from the stud a player pointed at to the copper terminal of the connector at the far end.

Drawn here rather than by the connector, and the reason is the stud. A connector's link list holds only the panel's position, so from that end there is no way to tell which of the eight terminals a wire belongs to, and eight legs would all sprout from the middle of the block. The panel knows, so the panel draws, and `PowerConnectorRenderer` skips any link that points at one, which is what stops a span being drawn twice.


## main/java/com/createstats/renderer/ProductionDetectorRenderer.java


**[1]** 1.5 px-thick backing slab covering the full screen footprint. Keep the front face just behind the screen, then extend thickness farther away from the display so it reads as a real 3D support without overlapping the render quad.


## main/java/com/createstats/renderer/SignalTraceRenderer.java


**[1]** Professional-grade time-series trace renderer for the Stats Display graphs.

A stat graph is a single-valued function of time (one value per x), so this uses the per-column anti-aliased "function plot" technique: every pixel column gets a sub-pixel accurate y with fractional edge coverage, so a line reads as a smooth, crisp stroke instead of the jagged integer stair-stepping of a Bresenham fill. Sparse data is smoothed with a monotone cubic (Fritsch-Carlson) spline, the same curve family used by professional web charts such as D3's curveMonotoneX, which flows without the overshoot wobble of a plain Catmull-Rom. Dense data (more samples than columns) collapses to a min/max envelope per column, so transient spikes are never dropped.

The look is a "Live Signal Instrument": a phosphor style trace (bright core plus soft glow) that brightens toward the newest sample, a gradient area fill that is strongest under recent data, a comet scan head riding the latest value, plus a scientific graph-paper grid and corner brackets. It is baked into a `NativeImage` with pure CPU pixel writes so the caller can upload it once and draw the whole animated trace with a single `blit` per frame, instead of the tens of thousands of `GuiGraphics.fill` calls a per-pixel path would submit.


## main/java/com/createstats/renderer/SpecializedPanelRenderer.java


**[1]** In the full-screen GUI the user can flip a Battle Tactical V2 panel to a flat 2D console. The baked-texture tactical render shears badly at large GUI sizes, so when that toggle is active we fall back to the GUI-native battle board which draws cleanly at any scale. In-world / ponder rendering never sets force2D and keeps the original tactical console.


**[2]** everything between the headline and the cards is the distribution graph. Without it a big panel was mostly a black void, because the layout only ever placed fixed-height elements at the top and bottom and never filled what was left.


**[3]** Still unless the bank is actually in trouble. A charge front that pulses every frame is decoration, and ISA-101 reserves motion for abnormal states precisely so that motion means something. It also aliases badly at the rate this texture regenerates.


**[4]** Animated energy rail. Packets slide toward the bank while charging and away while draining, at a speed that tracks the real throughput, so the page shows direction and magnitude without the player having to read a sign.


**[5]** A charge curve projected forward from the present rate: where this bank is heading, not just where it is. Flat when idle, rising toward full while charging, falling toward empty while draining.


**[6]** Per-layer charge distribution for the texture panel: one column per block layer of the real structure, tallest at the bottom because the pool fills bottom up. Fills the space between the headline and the metric cards, which was previously left black.


**[7]** Families that actually have a specialized page. Kept beside the dispatch switches below and must be updated with them.

This exists because a bound source with no page makes the whole specialized render return false, and the panel then falls back to graphs. With several sources bound and only some of them having pages, the display spent most of its cycle showing graphs and looked as though the specialized view had never been implemented.


**[8]** Only cycle sources that can actually draw a page. Without this a single source with no specialized renderer drops the whole panel back to graphs for its slice of the cycle, which reads as the specialized view being missing entirely.


**[9]** Whether a source may be selected for a specialized render at all.

Driven by `SPECIALIZED_FAMILIES`, the same set the dispatch switches use, because this being a separate hand-maintained list is what silently broke the Capacitor Bank page: the family was registered, the dispatch case existed, and the metrics flowed, but `buildSelection` refused the source here and returned null, so the renderer returned false and the panel fell back to graphs before the switch was ever reached. One list now.


**[10]** Per-panel cache for `renderBattleTacticalV2Gui` so the screen-overlay GUI path can reuse the same bake-once-blit-per-frame strategy as the pinned HUD overlay. Keyed by panel BlockPos; each entry holds an immutable size and source-id.


**[11]** Battle Tactical V2, responsive, per-observer tactical readout. Renders into a Custom Screen macro module bound to a Battle Bridge source. Layout adapts to texture size:
- w <= 96  : micro layout (1x1 panel), worst-state observer hero + state dots
for any remaining observers, plus freshness indicator.
- else     : Phase 3+ placeholder until larger layouts land.


**[12]** Keep the client voxel snapshots warm for every linked observer so the composition strip (and the in-world hologram) always have fresh structural data to read. No on-screen debug tag, the dashboard visualises this directly.


**[13]** Screen-overlay GUI variant of `renderBattleTacticalV2Texture`. Bakes the V2 panel into a per-panel `DynamicTexture` (rebuilt every 5 game ticks to match the panel refresh cadence) and blits it. Avoids per-pixel `graphics.fill` loops which would be catastrophic for FPS.


**[14]** Battle Tactical V2, large in-world face. Reworked from a literal iso ship silhouette (which read as noise on a low-res world texture) into a stylized, fully animated battle console: a circular integrity gauge with a live radar sweep, a sensor ladder, an abstract structural-composition bar driven by the observer voxel snapshots, and a weapon dock, wrapped in HUD chrome (corner brackets, faint scanlines, and a pulsing alert frame when anything is critical).


**[15]** Layout: portrait stacks the gauge compartment over the sensor ladder; landscape places them side by side. Every section is its own bordered compartment so the space reads as a tidy console rather than an open wireframe.


**[16]** Abstract structural-composition bar. Aggregates the present / missing / new voxel counts across every linked observer snapshot and draws a stacked bar: INTACT (green) | LOST (red) | ADDED (accent). Falls back to a dim notice when no voxel data has arrived yet.


**[17]** Phase C, 2D voxel silhouette renderer that replaces the old XZ "dot map" damage map when the panel has live voxel data from a battle.bridge V2 source. Renders a slowly rotating isometric silhouette of the actual structure using the same `DamageObserverVoxelMap` data the GUI viewport and in-world hologram use.

Painter's algorithm (back-to-front depth sort), one ~2-pixel block per voxel. Returns `false` if no voxel data is available so the caller falls back to the legacy XZ dot map. Returns `true` when something was rendered.


## main/java/com/createstats/renderer/StageLightRenderer.java


**[1]** Renders the Stage Light as an animated moving head: a static base, a yoke that pans, and a head that tilts at the pivot hubs, all easing smoothly toward the configured Pan/Tilt so it sweeps like a real motorised concert mover. The whole assembly is oriented onto the mounted face.


**[2]** Decompose the eased aim into a yoke azimuth + head elevation. We orient the head along the SAME facing-frame direction the beam and the projected light use, then map it back into the up-reference the fixture is built in, so the head visibly points exactly where it illuminates (no mirror).


**[3]** Yaw that, composed with the elevation tilt, makes the head's +Y rest axis point exactly along `aim` (yoke pans about +Y, head tilts about +X, so dir = (sin e*sin a, cos e, sin e*cos a)). The earlier atan2(aim.z, aim.x) - 90 was the NEGATIVE of this, which mirrored the head and beam cone for any combined pan+tilt (most visible when auto-aiming or aiming on a ship); the light itself never used this decomposition, so it was always correct.


**[4]** Pan drive cog: a brass gear seated in the bearing that visibly spins as the head pans, so the mover reads as a real motorised, geared mechanism (Create-style), on both tiers.


**[5]** Glowing lens: tinted by the colour filter (or the effect's animated colour), lit bright when on; a strobe's off phase drops the lens back to world light so the flash reads crisply.


**[6]** Render the visible colour beam as an additive cone from the lens to the first solid block, plus a soft splash disc where it lands. This is rendered in the head's local frame, so `+Y` is the beam axis (it already follows pan, tilt and the mounted face). Length is clamped by a raycast so the shaft stops at walls and the splash sits on the lit surface.


**[7]** Cache the occlusion raycast: re-clip a few times a second instead of every frame, so a long MK II / Beacon beam doesn't cast a 256-block voxel ray on every rendered frame.


**[8]** Visible cone width: same widening rate as the lit pool, capped at the per-light Beam Width setting (independent of the Light Spread cap so the shaft and the lit pool can be sized separately).


**[9]** Critically-damped spring smoothing (Unity-style SmoothDamp). Returns `[newValue, newVelocity]`; gives a natural ease-in / ease-out with no overshoot, so the head ramps up to speed and eases to a stop.


## main/java/com/createstats/renderer/StatsDisplayPanelRenderer.java


**[1]** FE Dependent Mode: the screen boots, flickers in brownout, and goes dark with no power. Returns 1.0 (unchanged) whenever the mode is off. Keyed on the controller, not this panel, so every tile of an assembled display runs the same boot curve and the same brownout flicker instead of shimmering independently.


**[2]** Phase C of Battle Tactical V2: if this panel hosts a battle.bridge source in V2 (3D) mode, paint a rotating voxel hologram floating in front of the face as the "at-a-glance from across the room" companion to the right-click GUI.


## main/java/com/createstats/statsmk2/StatsDisplayMk2Block.java


**[1]** Stats Display Mk II block. Reuses all of the Mk I block behaviour (placement, multiblock, antenna, redstone output, shift-to-cycle view) by extending `StatsDisplayPanelBlock`, and only diverges in three places: it hosts the Mk II block entity, it ticks that entity type, and it opens the new studio screen instead of the Mk I screen.


## main/java/com/createstats/statsmk2/StatsDisplayMk2BlockEntity.java


**[1]** Block entity for the Stats Display Mk II.

Mk II reuses the entire proven Mk I data backend by extending `StatsDisplayPanelBE`: source discovery, channels, metric formatting, redstone, multiblock and client sync all come for free, so the Mk II panel is instantly compatible with every machine the Mk I panel supports. The only thing that differs is the player-facing studio screen (opened by `StatsDisplayMk2Block`); the in-world block face reuses the same renderer.

Mk I and Mk II never cross-merge into one multiblock: the shared merge logic in `StatsDisplayPanelBE.detectAndFormMultiblock` now also requires the neighbour to be the exact same block, which partitions the two panel types cleanly.


## main/java/com/createstats/train/manifest/StationTrainRoster.java


**[1]** Composite roster view for all trains relevant to a specific station. Assembled periodically by the dispatch board controller; not persisted (transient).

Contains:

- `presentTrain`, Snapshot of the train currently docked at the station (if any)

- `associatedTrains`, Snapshots of trains whose schedules include this station but are not currently present

- `recentVisits`, Copy of the controller's persisted visit history (most recent first)

The roster is rebuilt every `ROSTER_REFRESH_INTERVAL_TICKS` by scanning `Create.RAILWAYS.trains` for trains that are present at or scheduled to visit the monitored station. Schedule matching uses the same wildcard logic as Create's station filters (via `TrainMapData.matchesStationFilter`).


## main/java/com/createstats/train/manifest/TrainSummarySnapshot.java


**[1]** Lightweight point-in-time snapshot of a train's identity and operational state. Used in `StationTrainRoster` for both the currently present train and schedule-associated trains. Captures enough data for the dispatch wall to display train metadata without holding a reference to the live Create Train entity.


## main/java/com/createstats/train/manifest/TrainVisitRecord.java


**[1]** Records a single train visit to a station. Persisted in the dispatch board controller's NBT as part of visit history.

A departure tick of -1 indicates the train is still present at the station. The controller detects arrivals and departures by comparing the present train UUID across refresh ticks and updates these records accordingly.


## main/java/com/createstats/util/CreateStatsPowerMetrics.java


**[1]** Metrics for the mod's own power hardware.

Every FE block here already resolved through the generic energy fall-through in `InstrumentSourceHelper`, because `FeCapabilities` registers them all. That path works, but it infers FE per tick by **differencing a stored buffer over time**, which is an estimate, and which reads exactly zero in the most common case of all: a machine whose supply matches its draw sits at a pinned buffer, so the delta is zero and the graph claims nothing is happening while it pulls 600 FE/t. Our own blocks measure the real figure every tick, so this class hands over the truth instead.

Two more things the generic path could not do. A Power Connector reported its own node's share of the pool, which since the grid rewrite is roughly `gridTotal / nodeCount` and therefore meaningless; it now reports the grid. And nothing anywhere computed **endurance**, the seconds of runtime left at the current drain, which is the number a readout turns into "collapse in 00:47" and which cannot be derived from a noisy estimate.

Key names are deliberately the ones that already exist. `network_buffer`, `network_pulled` and friends were defined for Create Crafts and Additions wire nodes, and blocks like the Production Detector already read them as fallbacks, so emitting the same names lights up every existing display without touching one of them.


**[2]** Endurance is reported as 0 when nothing is draining, rather than as a huge number, so a graph of it does not spike off the top of the axis every time a machine is comfortably supplied. A readout should treat 0 with a healthy net rate as "indefinite".


## main/java/com/createstats/util/InstrumentSourceHelper.java


**[1]** Our own power hardware first. It resolves through the generic energy branch below too, but that path can only infer FE/t by differencing a buffer over time, which reads zero exactly when a machine is comfortably supplied. These blocks measure the real figure.


## main/java/com/createstats/util/InventoryLookupHelper.java


**[1]** Shared helper for resolving an `IItemHandler` at a world position. Vanilla chests (including trapped chests and double-chests) do not always expose `Capabilities.ItemHandler` reliably via `Level.getCapability`, so callers must fall back to `ChestBlock.getContainer` to obtain the combined container and wrap it in an `InvWrapper`.


## main/java/com/createstats/util/StageLightColors.java


**[1]** Beam-filter colour mapping for the Stage Light. Maps Minecraft `DyeColor`s (from stained glass) to a vivid 0xRRGGBB tint used both for the in-world lens/beam colour and the GUI colour swatches.


## main/java/com/createstats/vccmk2/VccMk2Block.java


**[1]** VCC Mk II block, the polished gold/white successor to the legacy Vehicle Control Computer. This class is part of a brand-new, self-contained package and must never share code with `com.createstats.flight.*`.

Look-first milestone: the block is placeable and carries the live CRT renderer. Right-clicking with an empty hand temporarily cycles `VISUAL_STATE` so all four CRT colour themes can be inspected in-world. The real GUI arrives later.


## main/java/com/createstats/vccmk2/VccMk2BlockEntity.java


**[1]** Block entity for the VCC Mk II. Carries the live CRT renderer, the menu, and the lightweight state the Hangar + Bind stations need: the chosen `VccMk2CraftProfile`, an armed/mode flag pair, and a rack of propeller + governor cards.

STRICTLY SEPARATE from the legacy FlightControlComputer block entity. It reuses only the shared card items, `FlightCardData`, and `SableBridge` (read-only helpers), never the old control logic.


**[2]** Per engine row: false = kinetic (driven by a governor card -> RPM), true = redstone (the VCC transmits a 0-15 signal on a frequency pair to drive hot air / jet style parts). Both kinds carry a Mixer role and behave identically; only the output resolution differs.


**[3]** True once the player has touched a cockpit control this session. Saved demands are held across a GUI reopen, but on a fresh world load they stay inert until a fresh input (so the craft never lurches), unless the config opts into persisting them.


**[4]** Server tick entry point (driven by the block ticker). When ARMED and on a contraption it mixes the cockpit axis demands by role and commands the bound actuators; when disarmed it releases everything so the craft is safe. Mode by mode: Otomobil (ground vehicle) is wired first. STRICTLY separate from the legacy FCC, it only talks to shared hardware APIs (RPM Governor, the wheel-mount steering bus).


**[5]** Braking: command the drive wheels to HOLD 0 RPM. The RPM governor is a stiff servo, so holding 0 makes it generate torque opposing any rotation, i.e. an opposing "RPM force" against whatever way the craft is moving (and Park / handbrake holds it stopped). 0 is a stable setpoint, so unlike commanding reverse RPM it neither overshoots/oscillates nor drives the craft along in its initial direction.


**[6]** Periodically sum the fuel of every diesel engine on the contraption (the same Create Diesel Generators fuel logic the Stats Display's diesel page uses) for the cockpit fuel gauge. Groups are de-duplicated by their multiblock bounds so a huge / modular engine is not counted twice.


**[7]** Auto-assign sensible roles to every bound engine. Aircraft roles come from each propeller's thrust direction (vertical -> Lift, horizontal -> Thrust) read live off the contraption; ground vehicles get Drive, with the front-most axle also marked Steer (so a front wheel both drives and steers). Best-effort: anything unreadable falls back to a profile default. Read-only with respect to the craft.


## main/java/com/createstats/vccmk2/VccMk2CockpitArt.java


**[1]** Animated cockpit instrument art for the VCC Mk II. Pure drawing helpers (static), kept apart from `VccMk2Screen` so the flight deck stays readable. Every instrument is profile-aware and animated from a continuous clock; nothing here drives the craft, this is presentation only.

All drawing is scanline / segment level (one fill per row or per tick), never per-pixel loops, so a full cluster stays cheap to redraw each frame.


**[2]** A rotary selector knob. The pointer rotates to the selected detent (0..count-1); detent ticks ring the knob and the active label is lit. Use for drive / steer / diff style multi-state picks.


**[3]** Compact animated craft glyph for the Hangar profile cards. Draws the silhouette only (no panel, no caption), sized to fit a small tile. `animate` drives the motion for the selected card; idle cards pass a frozen clock so the Hangar reads calm, not busy.


## main/java/com/createstats/vccmk2/VccMk2CraftProfile.java


**[1]** The kind of craft a VCC Mk II is set up for. Chosen on the Hangar station; decides which control axes matter and seeds the auto-mix defaults. Replaces Mk I's single "Apply Zeppelin" template with an explicit, friendly choice.

Self-contained to the `vccmk2` package.


## main/java/com/createstats/vccmk2/VccMk2DisplayTarget.java


**[1]** Display target that lets a Gimbal / Altitude / Velocity / Nav Table sensor be linked to the VCC Mk II with the Create Display Link tool: aim the link from the sensor (source) to the VCC (target) and the sensor's position is stored on the block entity.

STRICTLY SEPARATE from the legacy FCC display target: this is a self-contained fork that reuses only the shared, read-only `SableBridge` sensor matchers.


**[2]** Display link broken or removed: keep the sensor binding. The VCC detects the sensor going offline through the scan hysteresis instead (Create transiently calls reset() even while a link is healthy).


## main/java/com/createstats/vccmk2/VccMk2Menu.java


**[1]** Menu (container) for the VCC Mk II. Hosts REAL inventory slots so the Bind station behaves like a normal container: the player drags / shift-clicks propeller + governor cards and redstone frequency items, and sees their own inventory in a dock.

The console is full-screen and multi-station, so every slot is only **active** while the Bind station is showing (a client-side flag the screen sets each frame, mirrored to "always on" server-side for interaction validation). Per row, the governor slot is active only for kinetic engines and the two frequency slots only for redstone engines. The screen repositions all slots in `init()` to match the responsive layout.

Self-contained: part of the `vccmk2` package, never the legacy control menu.


**[2]** Fixed Bind-station slot layout (the console is full-screen with a fixed top-left, so slot coordinates are deterministic and can be baked in at construction; Slot x/y are final).


## main/java/com/createstats/vccmk2/VccMk2Renderer.java


**[1]** Draws the live CRT screen onto the VCC Mk II block face. Mirrors the proven in-world screen-quad technique but is part of the self-contained vccmk2 package and pulls its texture from `VccMk2ScreenTextureManager`.


## main/java/com/createstats/vccmk2/VccMk2Role.java


**[1]** A control role an actuator (propeller / wheel) can serve in the Mixer station. An actuator can hold **several** roles at once (e.g. a front wheel that both Drives and Steers), so engines store a bitmask of roles rather than a single value. Roles are universal across every craft type so the one Mixer screen handles airships, multirotors, planes, VTOLs, cars and tanks.

Self-contained to the `vccmk2` package.


## main/java/com/createstats/vccmk2/VccMk2Screen.java


**[1]** Full-screen, fully adaptive "console" GUI for the VCC Mk II. Fills the whole screen on a dark backdrop (no blur). Hangar + Bind are fully wired; the card rack is drawn and clicked by the screen (one-click auto-bind from inventory) so the layout is free-form. Self-contained.


**[2]** Indicative craft-local push (right, fwd, up) an engine exerts when a test axis is pressed. Derived from the engine's geometry around the console origin, the nose direction and its role, so the Mixer can show which way each engine shoves the craft. Presentation only.


**[3]** Reflect the server's persisted set-and-hold demand on a pilot control, so closing and reopening the console keeps throttle / steering / brake where you left them. Once the player touches a control this session it becomes client-owned and we stop seeding it (no drag fight).


## main/java/com/createstats/vccmk2/VccMk2ScreenTextureManager.java


**[1]** Live CRT-face texture engine for the VCC Mk II. Bakes an animated aviation glass-cockpit readout into a per-block `DynamicTexture` (artificial horizon, heading tape, telemetry, mode badge), tinted by `VccMk2VisualState`.

Self-contained: shares only the generic `BitmapFont`/`TextureGraphRenderer` drawing utilities. It does NOT touch the legacy Flight Control Computer code.

Performance: one `NativeImage` per block, redrawn every few ticks and uploaded once (never per-pixel `graphics.fill`). NEAREST filtering keeps the pixels crisp.


## main/java/com/createstats/vccmk2/VccMk2Sensors.java


**[1]** Server-side sensor discovery and connection hysteresis for the VCC Mk II.

Sensors are bound primarily by aiming a Create Display Link from the sensor to the VCC (see `VccMk2DisplayTarget`); a cadenced chunk scan also auto-discovers the nearest unbound sensor of each kind for convenience. Either way the resolved position is revalidated every scan, and a short miss streak is required before a live connection flips to LOST, so chunk-load churn or a re-anchoring sub-level does not make the status flicker.

STRICTLY SEPARATE from the legacy FCC: this reuses only the shared, read-only `SableBridge` sensor matchers, never any FCC control code.


## main/java/com/createstats/vccmk2/VccMk2VisualState.java


**[1]** Visual state for the VCC Mk II block. Drives the colour theme of the live CRT face (see `VccMk2ScreenTextureManager`). Kept intentionally separate from the legacy Flight Control Computer's own state enum: the two systems must never share code.


## test/java/com/createstats/antenna/AntennaRangeTest.java


**[1]** Pure-logic tests for `AntennaRange`. These exercise the extracted, world-free reach math so the antenna "working system" is pinned against regressions:

- tier range scaling per dimension (incl. the Nether 8:1 compression on TRT),

- same-dimension squared-distance reach boundaries,

- cross-dimension eligibility (TRT across the Overworld/Nether pair only).


## test/java/com/createstats/block/LampFacingTest.java


**[1]** Checks that a lamp or switch is drawn on the same face of its block that its hitbox is on. needs a test because the hitbox and the model get rotated onto a face by two entirely separate mechanisms that never meet: the hitbox by `LampShapes.rotate` in Java, the model by `x`/`y` rotations in a generated blockstate, applied by Minecraft. nothing forces them to agree, and when they disagree there's no error of any kind. every horizontal facing of all eleven lamps and switches shipped with the model a half turn out from the hitbox - mounted on a wall, the fitting drew on the far side of its own block from the outline you were pointing at. rather than assert the numbers in the table match the table, both sides get worked out from first principles and compared against the meaning of the property: FACING is the direction the fitting points, so its back is against `facing.getOpposite()`, and both the shape and the model have to end up there.


**[2]** The face a rotated shape is stuck to: the one it touches and is thin against.

Touching alone is not enough. The strip lamp spans its block end to end, so it touches four boundaries; only the one it is also thin against is the surface it is mounted on.


**[3]** Where a blockstate rotation puts the base of a model authored sitting on the floor.

Minecraft composes a variant's rotation as Ry(-y) after Rx(-x), about the block centre. The base of an upward-pointing model faces DOWN, so pushing DOWN through that rotation gives the face the model ends up lying against.


## test/java/com/createstats/flight/runtime/PurePursuitTest.java


**[1]** Unit tests for the ground-vehicle pure-pursuit steering law. These pin the properties the vehicle cannot be driven to verify: deadzone, sign, monotonicity, saturation, the lookahead relationship, and the abeam/behind full-lock behaviour that fixes the raw sine law's under-steer past 90 degrees.


## test/java/com/createstats/gui/pdu/EaseTest.java


**[1]** Tests for the animation helper's convergence.

Easing is invisible when it works and maddening when it does not: a value that overshoots, or one that never quite arrives and leaves a bar shimmering forever. Both are single line arithmetic bugs, and both are checkable without a game, which is the whole reason `Ease` carries no client import. With the config absent in this JVM `Ease.reduceMotion()` defaults to false, so these exercise the animating path.


**[2]** Frame rate independence: a long frame (bigger partialTick) must cover more ground than a short one, or the animation runs slower on a slower machine, which is the bug this replaces.


## test/java/com/createstats/gui/pdu/PduBalanceBarTest.java


**[1]** The empty state, which is the only part of the balance bar that is not geometry.

It earns a test because it shipped saying the wrong thing: "No load on the branch" appeared under a header reading "18/18 served", which is the panel contradicting itself in one frame.


## test/java/com/createstats/gui/pdu/PduHistoryTest.java


**[1]** Tests for the server history ring.

Two things are load bearing and invisible to the eye: the wrap around, so a full buffer keeps reading oldest to newest across the storage seam rather than in raw slot order, and the short clamp, so a large FE per tick stores as `PduHistory#MAX_FE` and never overflows into a negative short. Both are pinned here.


## test/java/com/createstats/gui/pdu/PduLegRowTest.java


**[1]** How a leg's state is named.

Four different reasons a leg can be dark look identical from any single number and call for four different responses. These tests pin which one wins when several are true at once, because that is the part that decides whether the panel sends a player to fix the right thing.


## test/java/com/createstats/gui/pdu/PduRectTest.java


**[1]** Tests for the widget geometry primitive.

`PduRect` carries every hit test on the screen, so its containment rule is load bearing: half open on the right and bottom edges, so two cards sharing a seam never both answer a click on it. That off by one is the classic source of a control that seems to respond one pixel late, and it is trivially pinnable here.


## test/java/com/createstats/gui/pdu/PduRingTest.java


**[1]** Tests for the client side rolling buffer behind the sparklines.

The wrap around is the only thing here worth pinning: that once full the oldest sample is the one overwritten, and that `PduRing.at` still reports oldest to newest across the seam.


## test/java/com/createstats/gui/pdu/PduStatusLineTest.java


**[1]** Tests for the plain language status line, one per branch.

The two the brief singled out are here by name: `offModeSaysSoRatherThanPrintingAutoNumbers` and `noBranchIsCaughtBeforeAnyDerivedNumber`. The rest walk the urgency ladder so a change to the ordering cannot silently start saying the wrong true thing.


## test/java/com/createstats/gui/pdu/PduTableModelTest.java


**[1]** Tests for the pure table model: grouping, sorting, the filter predicate, and sort stability.

This is the class the phase brief singled out as most likely to be quietly wrong, so the cases are deliberately exhaustive: every group mode and sort key, the empty and single and two hundred device rosters, both search misses and case folded hits, and the stability guarantee that equal keys keep their roster order in both directions.


## test/java/com/createstats/gui/pdu/PduTablePerfTest.java


**[1]** A micro benchmark for the table's per frame cost at two hundred devices.

The render loop cannot be timed from a unit test, there is no `GuiGraphics`, but the two things that could make it O(roster) rather than O(visible) can: rebuilding the model, and the per frame `PduTable.setData` call that decides whether to. This measures both and writes the numbers to `build/pdu-perf.txt` so they can be reported honestly. The steady state figure is the one that matters, because it is what runs every frame; the build figure runs only on a sync or a control change.

The assertions are deliberately loose, three orders of magnitude above the expected time, so this pins the shape of the cost, not a wall clock that would flake on a busy machine.


## test/java/com/createstats/gui/pdu/PduThemeTest.java


**[1]** Tests for the PDU screen's responsive breakpoint choice.

A breakpoint bug is a panel drawn off the edge of the screen at a GUI scale nobody tests by hand. `PduTheme.currentFor` is pure arithmetic precisely so the thresholds can be pinned here instead: the exact window size where LARGE gives way to NORMAL, and the guarantee that a bigger window never drops to a smaller layout.


**[2]** currentFor keeps a margin of 2 * BASE (8 px) clear on every edge, so a breakpoint of ideal width W needs a window of at least W + 16, and likewise for height. These are those thresholds.


**[3]** GUI scale 3 on 1920x1080 gives a 640x360 canvas. LARGE at 504x336 fits, with only 24 px of vertical margin to spare, so this is the case that pins the height threshold.


## test/java/com/createstats/gui/pdu/PduTopologyTest.java


**[1]** Tests for the WIRING spanning tree.

The parent assignment is the load bearing part: it decides which branch a device hangs under, and a wrong parent draws a plausible but false diagram. So the cases walk a chain, a fork, a connector link that crosses air, a disconnected node that must be excluded, and the cap.


## test/java/com/createstats/pathfinder/planner/PathRoutePlannerCostTest.java


**[1]** Unit tests for the smarter planning cost model added in the perception/planning upgrade: the climb-run-escalating slope penalty (long 45-degree faces must cost far more than short bumps) and the graduated obstacle berth (the closer a path passes a tree/structure, the more it costs).


## test/java/com/createstats/pathfinder/planner/PathRoutePlannerSmoothingTest.java


**[1]** Unit tests for the ground-vehicle route smoothing (Phase 1 of the autopilot overhaul). These validate the pure geometry the vehicle cannot be driven to verify: resample spacing, local curvature radius, the curvature-limited speed profile (slows into curves, brakes BEFORE corners), string-pull shortcutting, and the load-bearing safety property that a smoothed path never leaves a drivable cell.


**[2]** A right angle whose full-radius arc would bulge over a hole; the multi-radius retry must keep every emitted point on a drivable cell (tighter arc or, failing that, the sharp vertex).


## test/java/com/createstats/power/CableLinksTest.java


**[1]** Tests for how one length of cable decides it meets another.

The test that matters most here is `everyRouterMoveJoinsBothWays()`. A cable run that visibly stops in mid air is almost always an **asymmetric** rule: A believes it reaches B, B does not believe it reaches A, so one arm is drawn and its other half is missing. Rather than assert on a handful of hand-picked shapes and hope they cover it, that test walks every move the router can make, in a world shaped so all three kinds occur, and insists the join is seen from both ends.


**[2]** Two runs one block apart on the same floor. They are both cable and both expose power, so anything that fell through to "does the neighbour take power" would wire them together.


**[3]** Every join the router can lay must be visible from both ends.

This is the regression guard. A rule that is complete but not symmetric produces a run whose arms are drawn from one side only, which on screen is a cable that stops halfway.


**[4]** The grid must reach every block the router laid, including across a convex wrap.

This is the one that matters for power actually arriving. The FE flood used to walk the six orthogonal neighbours, but a convex wrap lands on `pos + towards + face`, which is a diagonal step, so a run folding over any outside edge was drawn joined and carried nothing. The test asserts both halves: that the route really does contain a non-orthogonal step, so it is exercising the case at all, and that the flood still reaches the whole run.


**[5]** Paint one of them and the join is gone, and gone symmetrically. An asymmetric answer here would draw an arm from one block into a neighbour that does not draw one back, which is exactly what a run that stops in mid air looks like.


**[6]** A wall three high, with the cable at the top of it: the run wraps over the outside edge. One block lower and this would still be a flat climb, which is the mistake worth pinning.


## test/java/com/createstats/power/CableRouteTest.java


**[1]** Tests for the surface cable router.

Routing is geometry, and geometry bugs are the kind you cannot see by looking: a route that quietly climbs the wrong face, or wraps an edge onto a block that is not there, looks perfectly plausible in a preview right up until it refuses to place. So the three move kinds are asserted directly, and the worlds here are built by hand out of explicit solid blocks.


**[2]** The concave turn is the move that lands on a new face of the same block, which is the whole reason the cable block has to hold more than one face.


## test/java/com/createstats/power/CableTierTest.java


**[1]** The three cable grades.

The rule worth asserting is not any one number, it is that **capacity and width move together**. A player picks a grade by looking at it, so a grade that carried more without looking heavier would be a fact you can only learn from a wiki, and a grade that looked heavier without carrying more would be a lie told in geometry. That is exactly the sort of thing a later edit to one field and not the other breaks silently.


## test/java/com/createstats/power/FeBankMergeTest.java


**[1]** A 1x1x10 tower must become a full six-block bank with a four-block bank on top, which is what a player building upward expects to see, rather than an arbitrary slice.


**[2]** The invariant the whole design rests on. Every block floods the same cluster and runs this same split, so if input order could change the answer, two blocks in one structure would disagree about which bank they are in and the shared buffer would tear in half.


**[3]** The config allows a 100,000,000 base, and 100M x 54^1.1 is about 8 billion, well past what an int holds. It must clamp rather than wrap into a negative capacity.


## test/java/com/createstats/power/FeConsumerTest.java


**[1]** Zero by default so the pre-existing cases keep exercising the plain edge-triggered cutoff. The hysteresis cases set this explicitly. Overridden rather than inherited because the interface default reads config, which is not bootstrapped under test.


**[2]** The point of hysteresis: a cut-off block must charge to a real reserve before it runs, instead of waking for a single tick every time it scrapes together one tick of drain.


**[3]** A multiblock is one electrical device. Its buffer is the sum of its parts; its running cost is driven by the data it polls, not by how much screen area it covers. Scaling drain here too would compound with the source-count curve at the call site.


## test/java/com/createstats/power/FeGridSolverTest.java


**[1]** Tests for the wire grid solve. Energy transport is where duplication and voiding bugs live, so conservation is checked on every case: the FE in the world before a solve must equal the FE in the world after it.


**[2]** A grid must be able to deliver more per tick than its wire can hold.

The regression this guards is subtle and was invisible for as long as cable held a large buffer. Collection used to be capped at the grid's spare buffer alone, which made throughput equal to total wire capacity. Once a cable segment dropped to 25 FE, a battery could no longer run a 256 FE/t machine through a short run: eleven segments would have been needed before the energy could even enter the pool. Demand is the right allowance, because demand is exactly the part that leaves again in the same tick and never needs storing.


**[3]** A battery with nothing drawing on the grid must simply sit there. Draining it into the wire buffer would then push it back the next tick: a permanent ping-pong that shows up in game as a battery that slowly bleeds out while nothing is running.


**[4]** A load whose handler is not a FeConsumer cannot carry a priority, so it must land on the default tier and must never be marked shed (there is nothing to mark).


**[5]** The two-argument overload must be byte-for-byte identical to passing null explicitly, so every existing caller (and every other test in this file) is provably unaffected by this instrumentation existing at all.


## test/java/com/createstats/power/FeLoadCostTest.java


**[1]** The costing rule, and the failure it exists to prevent.

The interesting test here is not any of the arithmetic ones: it is `headroomCostingRatchetsShedDevicesOffPermanently()`, which reproduces the actual reported bug by driving the real controller. Everything above it is scaffolding for that.


**[2]** The reported bug, reproduced end to end: "sometimes in full auto mode it doesnt activate all even tho there is enough supply."

Under the old rule a device's demand was its buffer headroom, which is not a property of the device but of how recently it was fed. A served device sits topped off and looks cheap; a shed device sits empty and looks twice as expensive. The admit optimizer sorts cheapest first inside a tier, so being shed was itself what kept a device at the back of the queue, and the branch could never climb back out no matter how much supply returned.

Both halves are asserted. If the fix is ever reverted the first assertion fails, and if the scenario stops actually exercising the ratchet the second one fails, so the test cannot rot into passing for the wrong reason.


**[3]** Surplus true throughout, so nothing but the budget arithmetic can block a restore. This is the player's "there is enough supply": the buffer is healthy and the branch still will not come back up.


## test/java/com/createstats/power/FeManagedStateTest.java


**[1]** The tier and lock a built-in load carries.

These shipped hardcoded to `DEFAULT` and `NONE` on the roster and gated behind an interface the blocks do not implement on the write side, so no control on the panel could move either one. Both halves are asserted here.


## test/java/com/createstats/power/PduAutoControllerTest.java


**[1]** Tests for the Power Distribution Unit's ACTIVE AUTO admit decision.

A load controller's bugs are almost all temporal: it flaps across the admit boundary, it restores five devices at once, it waits out a dwell while the reserve it was protecting drains away. None of those are visible in a single call, so most of these tests drive a sequence of control steps and assert on the run set at each one.


**[2]** Cheapest-first admits both 30s and sheds the 90. Serving in list order would admit the 90 and then have room for neither 30, keeping one device alive instead of two.


**[3]** Demand equals the budget exactly, with a burst margin above it. The device fits the run test (<= runBudget) but not the restore test (<= admitBudget - margin), which is the deadband. Once running it must simply stay running, not toggle on alternate steps.


**[4]** Budget is ample but there is no spare supply: banks are not charging and the buffer is below the restore mark. Bringing the device back here would just re-drain the reserve.


**[5]** Now the loop that used to bite: with the device shed the buffer refills, so a buffer-coupled budget climbs back over 1000 and re-admits it, it drains the buffer again, and the panel pulses forever. Restore is judged on generation alone (600), which does not move when the buffer does, so it must stay shed no matter how full the reserve gets.


**[6]** Demand 100, generation 100, margin 10. A shed device does not fit 100 - 10, so it stays shed: the branch is never brought back up at exactly 100 percent of measured supply.


**[7]** The locked-off device is enormous. If it were counted as demand it would eat the whole budget and shed the two real devices, which is exactly the bug worth guarding.


**[8]** 100 of budget, and a locked-on Optional device eating 80 of it. The Normal device costs 40, so it cannot fit in what is left. If the locked-on cost were charged afterwards the optimizer would admit it and the branch would be over budget by 20.


**[9]** Shed it, then hand back plenty of supply while the dwell is still running. The device is not shed for want of power at this point, it is being held by the anti-flap timer, and telling the player "supply does not cover it" there would send them hunting a phantom.


**[10]** Same budget both times, so the same device is shed. The only difference is whether the branch wiring or the generation was the thing that ran out, and the panel must say which: one is fixed by more dynamos and the other is not.


**[11]** Rewire the branch so device 1 is gone, then bring it back. It must arrive as a new device (admitted, no dwell owed) rather than resuming a shed state from its previous life.


**[12]** Wire order can change when a network is rebuilt from a different origin connector, so equal devices are broken by key. Without that the admit boundary could fall on a different device between steps, which is a flap with no cause.


## test/java/com/createstats/power/PduBranchGuardTest.java


**[1]** Tests for the bulk action branch guard.

The one that matters is `aCraftedOffBranchPositionIsDropped`: a packet naming a device that is not on this PDU's branch must not touch it, however it was crafted. The rest pin the plain behaviour so the server side filter cannot quietly regress.


## test/java/com/createstats/power/PduDeviceIdsTest.java


**[1]** Device numbering.

Every test here is about **stability**, because that is the only property that matters. A numbering scheme that is correct but reshuffles is worse than none: a player watching "ID:07" get shed has to be able to trust that ID:07 is still the same machine on the next sync.


## test/java/com/createstats/power/PduLegPlanTest.java


**[1]** The per leg capacity screen.

The tests that matter here are the ones about the boundary between this pass and `PduAutoController`: what each is allowed to decide, and that a leg being full never leaks into looking like a supply problem or vice versa. Getting that wrong does not throw, it just sends the player off to build generation they do not need.


**[2]** The first is admitted, the second physically cannot be: 40,000 does not fit in 24,576 no matter what the player pinned. A panel that promised otherwise would be lying.


## test/java/com/createstats/power/PduStudsTest.java


**[1]** Stud picking.

All of this is about rotation, because rotation is the half that cannot be checked from a screenshot. A transform that is inverted or mirrored looks perfectly correct on a panel facing one way and picks the wrong stud on a panel facing another, and nobody notices until they have built two.


## test/java/com/createstats/power/PduStudWiringTest.java


**[1]** The stud endpoint rules.

Every one of these is a refusal a player can trigger with two clicks, and a refusal that fires on the wrong branch is worse than no refusal at all: it names a problem that is not the one in front of them. So the order the reasons are decided in is checked as carefully as the reasons themselves.


**[2]** Out of range, already linked and full as well: the index is still the honest answer, because a click that resolved to no terminal never had a span to judge in the first place.


## test/java/com/createstats/power/PumpTierTest.java


**[1]** The two FE Fluid Pump grades.

The one invariant worth pinning is the tie to Create: a Mechanical Pump moves `max(1, pressure / 2)` mB/t, so a pump matching it at a given RPM must move exactly half that RPM in millibuckets. A later edit that bumped a grade's ceiling without keeping that relationship would quietly make "MK I is a 128 RPM pump" a claim the numbers no longer back up.
