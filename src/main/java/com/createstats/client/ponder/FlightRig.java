package com.createstats.client.ponder;

import com.createstats.vccmk4.air.core.AerialPonderFlight;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class FlightRig {
    private final AerialPonderFlight.Recording flight;

    private final Vec3 com;

    private final double[] frame;

    private final double[] along;
    private ElementLink<WorldSectionElement> ship;

    private int shown;

    public FlightRig(AerialPonderFlight.Recording flight, Vec3 com) {
        this.flight = flight;
        this.com = com;
        this.frame = new double[flight.length()];
        this.along = new double[flight.length()];
        for (int t = 0; t < flight.length(); t++) along[t] = flight.northSince(0, t);
    }

    public AerialPonderFlight.Recording flight() {
        return flight;
    }

    public Vec3 com() {
        return com;
    }

    public void ship(ElementLink<WorldSectionElement> link) {
        ship = link;
    }

    public ElementLink<WorldSectionElement> ship() {
        return ship;
    }

    public FlightRig keepInFrame(double calm, double firm) {
        double y0 = flight.y(0);
        double f = 0.0;
        for (int t = 0; t < frame.length; t++) {
            double dy = t == 0 ? 0.0 : flight.y(t) - flight.y(t - 1);
            double off = flight.y(t - 1 < 0 ? 0 : t - 1) - y0 - f;

            double toward = dy >= 0 ? off : -off;
            double k = Ease.SMOOTH.clamped((toward - calm) / Math.max(1.0e-6, firm - calm));
            f += dy * k;
            frame[t] = f;
        }
        return this;
    }

    public int length() {
        return flight.length();
    }

    private int at(int tick) {
        return Math.max(0, Math.min(flight.length() - 1, tick));
    }

    public Vec3 shipOffset(int tick) {
        return new Vec3(0.0, flight.y(at(tick)) - flight.restY() - frame[at(tick)], 0.0);
    }

    public Vec3 worldOffset(int tick, boolean ground) {
        return new Vec3(ground ? -along[at(tick)] : 0.0, -frame[at(tick)], 0.0);
    }

    public double along(int tick) {
        return along[at(tick)];
    }

    public double frame(int tick) {
        return frame[at(tick)];
    }

    public double sceneY(double y, int tick) {
        return com.y + (y - flight.restY()) - frame[at(tick)];
    }

    void shown(int tick) {
        shown = tick;
    }

    public int shown() {
        return shown;
    }

    public Vec3 onShip(Vec3 rest, float pt) {
        Vec3 a = shipOffset(shown - 1);
        Vec3 b = shipOffset(shown);
        return rest.add(Mth.lerp(pt, a.x, b.x), Mth.lerp(pt, a.y, b.y), Mth.lerp(pt, a.z, b.z));
    }

    public Vec3 onWorld(Vec3 rest, boolean ground, float pt) {
        Vec3 a = worldOffset(shown - 1, ground);
        Vec3 b = worldOffset(shown, ground);
        return rest.add(Mth.lerp(pt, a.x, b.x), Mth.lerp(pt, a.y, b.y), Mth.lerp(pt, a.z, b.z));
    }
}
