package com.createstats.vccmk3.runtime;

import com.createstats.vccmk3.avionics.SensorSource;
import com.createstats.vccmk3.hardware.DeviceCategory;
import com.createstats.vccmk3.model.ControlAxis;
import com.createstats.vccmk3.model.DeviceGeometry;
import com.createstats.vccmk3.model.VccDevice;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlMixerTest {
    private static final double TOL = 1.0e-9;

    private static CraftFrame northFacingFrame() {
        return new CraftFrame(new Vec3(0, 0, -1), new Vec3(0, 1, 0), new Vec3(1, 0, 0),
                new Vec3(0.5, 0.5, 0.5), SensorSource.SENSOR, "");
    }

    private static CraftFrame pivotlessFrame() {
        return new CraftFrame(new Vec3(0, 0, -1), new Vec3(0, 1, 0), new Vec3(1, 0, 0),
                new Vec3(0.5, 0.5, 0.5), SensorSource.NONE, "not assembled");
    }

    private static VccDevice device(DeviceCategory category, int x, int y, int z, DeviceGeometry geometry) {
        return VccDevice.fromRow(UUID.randomUUID(), category, new BlockPos(x, y, z),
                        category.requiresGovernor() ? new BlockPos(x, y + 1, z) : null, 0, 1)
                .withGeometry(geometry);
    }

    private static VccDevice propForward(int x, int y, int z) {
        return device(DeviceCategory.PROPELLER, x, y, z,
                DeviceGeometry.pushing(new Vec3(0, 0, -1), SensorSource.SENSOR));
    }

    private static VccDevice surfaceHingedEast(int x, int y, int z) {
        return device(DeviceCategory.CONTROL_SURFACE, x, y, z,
                DeviceGeometry.swinging(new Vec3(1, 0, 0), 1, SensorSource.DERIVED));
    }

    @Nullable
    private static ControlMixer.Share shareOf(ControlMixer.Mix mix, VccDevice device) {
        for (ControlMixer.Share share : mix.shares()) {
            if (share.deviceId().equals(device.id())) return share;
        }
        return null;
    }

    private static double gainOf(ControlMixer.Mix mix, VccDevice device) {
        ControlMixer.Share share = shareOf(mix, device);
        assertNotNull(share, "no share was produced for the device");
        return share.gain();
    }

    @Test
    void differentialThrustYawsTowardTheWeakerSide() {
        VccDevice port = propForward(-4, 0, 0);
        VccDevice starboard = propForward(4, 0, 0);

        ControlMixer.Mix mix = ControlMixer.solve(northFacingFrame(), ControlAxis.YAW,
                List.of(port, starboard));

        assertEquals(1.0, gainOf(mix, port), TOL, "port propeller should spin up for a right turn");
        assertEquals(-1.0, gainOf(mix, starboard), TOL, "starboard propeller should spin down for a right turn");
    }

    @Test
    void centrelineThrustProducesNoYaw() {
        VccDevice centre = propForward(0, 0, 3);

        ControlMixer.Mix mix = ControlMixer.solve(northFacingFrame(), ControlAxis.YAW, List.of(centre));

        assertTrue(mix.isEmpty() || gainOf(mix, centre) == 0.0,
                "a centreline propeller cannot yaw the craft");
    }

    @Test
    void asymmetricThrustReportsItsLeftoverPush() {
        VccDevice portA = propForward(-4, 0, 0);
        VccDevice portB = propForward(-5, 0, 0);
        VccDevice portC = propForward(-6, 0, 0);
        VccDevice starboard = propForward(4, 0, 0);

        ControlMixer.Mix mix = ControlMixer.solve(northFacingFrame(), ControlAxis.YAW,
                List.of(portA, portB, portC, starboard));

        assertEquals(6.0 / 6.0, gainOf(mix, portC), TOL);
        assertEquals(5.0 / 6.0, gainOf(mix, portB), TOL);
        assertEquals(4.0 / 6.0, gainOf(mix, portA), TOL);
        assertEquals(-4.0 / 6.0, gainOf(mix, starboard), TOL);

        Double forwardBleed = mix.crossCoupling().get(ControlAxis.FORWARD);
        assertNotNull(forwardBleed, "an unbalanced yaw mix must report the push it leaves behind");
        assertTrue(forwardBleed > 0.5, "three-to-one thrust should bleed a clear forward push");
    }

    @Test
    void pairedAftSurfacesMoveTogetherForPitch() {
        VccDevice port = surfaceHingedEast(-4, 0, 5);
        VccDevice starboard = surfaceHingedEast(4, 0, 5);

        ControlMixer.Mix mix = ControlMixer.solve(northFacingFrame(), ControlAxis.PITCH,
                List.of(port, starboard));

        double portGain = gainOf(mix, port);
        double starboardGain = gainOf(mix, starboard);

        assertEquals(starboardGain, portGain, TOL, "elevators must deflect together, not opposed");
        assertEquals(-1.0, portGain, TOL, "an aft surface pushed down is what raises the nose");
    }

    @Test
    void theSameSurfacesOpposeEachOtherForRoll() {
        VccDevice port = surfaceHingedEast(-4, 0, 5);
        VccDevice starboard = surfaceHingedEast(4, 0, 5);

        ControlMixer.Mix mix = ControlMixer.solve(northFacingFrame(), ControlAxis.ROLL,
                List.of(port, starboard));

        double portGain = gainOf(mix, port);
        double starboardGain = gainOf(mix, starboard);

        assertEquals(-starboardGain, portGain, TOL, "ailerons must oppose each other");
        assertEquals(1.0, portGain, TOL,
                "positive roll drops the right wing, so the port surface lifts the port wing");
    }

    @Test
    void hardwarePointingTheWrongWayIsZeroedAndExplained() {
        VccDevice lift = device(DeviceCategory.PROPELLER, 0, 0, 0,
                DeviceGeometry.pushing(new Vec3(0, 1, 0), SensorSource.SENSOR));

        ControlMixer.Mix mix = ControlMixer.solve(northFacingFrame(), ControlAxis.FORWARD, List.of(lift));

        ControlMixer.Share share = shareOf(mix, lift);
        assertNotNull(share);
        assertEquals(0.0, share.gain(), TOL);
        assertFalse(mix.summary().isEmpty(), "a mix that does nothing must explain itself");
    }

    @Test
    void unreadableGeometryContributesNothing() {
        VccDevice blind = device(DeviceCategory.PROPELLER, -4, 0, 0,
                DeviceGeometry.unavailable("The propeller bearing is not loaded."));

        ControlMixer.Mix mix = ControlMixer.solve(northFacingFrame(), ControlAxis.YAW, List.of(blind));

        ControlMixer.Share share = shareOf(mix, blind);
        assertNotNull(share);
        assertEquals(0.0, share.gain(), TOL);
        assertTrue(share.note().contains("not loaded"), "the reason must survive to the console");
    }

    @Test
    void momentAxesRefuseToMixWithoutAPivot() {
        VccDevice port = propForward(-4, 0, 0);
        VccDevice starboard = propForward(4, 0, 0);

        ControlMixer.Mix mix = ControlMixer.solve(pivotlessFrame(), ControlAxis.YAW,
                List.of(port, starboard));

        assertTrue(mix.isEmpty(), "yaw cannot be solved without a centre of mass");
        assertEquals("not assembled", mix.summary());
    }

    @Test
    void forceAxesStillMixWithoutAPivot() {
        VccDevice engine = propForward(0, 0, 0);

        ControlMixer.Mix mix = ControlMixer.solve(pivotlessFrame(), ControlAxis.FORWARD, List.of(engine));

        assertEquals(1.0, gainOf(mix, engine), TOL);
    }

    @Test
    void rearAxleSteeringInverts() {
        VccDevice frontAxle = device(DeviceCategory.WHEEL, 0, 0, -5,
                DeviceGeometry.swinging(new Vec3(1, 0, 0), 1, SensorSource.DERIVED));
        VccDevice rearAxle = device(DeviceCategory.WHEEL, 0, 0, 5,
                DeviceGeometry.swinging(new Vec3(1, 0, 0), 1, SensorSource.DERIVED));

        ControlMixer.Mix mix = ControlMixer.solve(northFacingFrame(), ControlAxis.YAW,
                List.of(frontAxle, rearAxle));

        assertEquals(1.0, gainOf(mix, frontAxle), TOL, "the front axle steers the nose the way it points");
        assertEquals(-1.0, gainOf(mix, rearAxle), TOL, "the rear axle must steer the opposite way");
    }

    @Test
    void signalChannelsAreTakenOnTrustAndSaidToBe() {
        VccDevice burners = device(DeviceCategory.SIGNAL_LINK, 0, 0, 0, DeviceGeometry.notSpatial());

        ControlMixer.Mix mix = ControlMixer.solve(northFacingFrame(), ControlAxis.UP, List.of(burners));

        ControlMixer.Share share = shareOf(mix, burners);
        assertNotNull(share);
        assertEquals(1.0, share.gain(), TOL);
        assertTrue(share.asserted(), "a signal channel must be flagged as unverified, not as measured");
    }

    @Test
    void balancedYawLeavesNoCrossCoupling() {
        VccDevice port = propForward(-4, 0, 0);
        VccDevice starboard = propForward(4, 0, 0);

        ControlMixer.Mix mix = ControlMixer.solve(northFacingFrame(), ControlAxis.YAW,
                List.of(port, starboard));

        Map<ControlAxis, Double> coupling = mix.crossCoupling();
        assertTrue(coupling.isEmpty(),
                "a symmetric yaw mix must produce no leftover force, got " + coupling);
    }
}
