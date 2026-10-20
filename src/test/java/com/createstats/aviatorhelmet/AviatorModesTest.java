package com.createstats.aviatorhelmet;

import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AviatorModesTest {
    @Test
    void downTheyAreGogglesAndTheSightIsLit() {
        assertTrue(AviatorModes.gogglesActive(true));
        assertTrue(AviatorModes.hudActive(true));
        assertFalse(AviatorModes.gogglesActive(false), "up on the forehead they are not in front of the eyes");
        assertFalse(AviatorModes.hudActive(false));
    }

    @Test
    void theSightFollowsARiderIntoEveryCamera() {
        assertTrue(AviatorModes.hudInView(true, true, false), "first person, on foot");
        assertTrue(AviatorModes.hudInView(true, false, true), "F5 or Sable's vehicle views, riding");
        assertFalse(AviatorModes.hudInView(true, false, false), "third person on foot: the helmet is on the model");
        assertFalse(AviatorModes.hudInView(false, true, true), "goggles up: nothing to see");
        assertTrue(AviatorModes.lensesInView(true));
        assertFalse(AviatorModes.lensesInView(false), "the lenses' tint is only ever from behind them");
    }

    @Test
    void theTonesAreTheMocksAndCycle() {
        assertEquals(0xFFB848, HudTone.TUNGSTEN.core);
        assertEquals(0xFF9628, HudTone.TUNGSTEN.dim);
        assertEquals(0x96FF78, HudTone.RADIUM.core);
        assertEquals(0xCDE8FF, HudTone.MOONLIGHT.core);
        assertEquals(30, (HudTone.TUNGSTEN.tint >>> 24) & 0xFF);
        assertEquals(HudTone.RADIUM, HudTone.next(HudTone.TUNGSTEN.ordinal()));
        assertEquals(HudTone.TUNGSTEN, HudTone.next(HudTone.MOONLIGHT.ordinal()));
        assertEquals(HudTone.TUNGSTEN, HudTone.of(99), "an unknown tone is tungsten, not a crash");
        assertEquals(HudTone.COLD, HudTone.warmed(HudTone.RADIUM.core, 0.0), "cold filament first");
        assertEquals(HudTone.RADIUM.core, HudTone.warmed(HudTone.RADIUM.core, 1.0));
    }

    @Test
    void theGogglesFlickAndKeepTheirTone() {
        final AviatorGoggles g = AviatorGoggles.DEFAULT;
        assertFalse(g.down(), "a new helmet has its goggles up on the forehead");
        final AviatorGoggles down = g.nextTone().flicked(1234L);
        assertTrue(down.down());
        assertEquals(1234L, down.strokeTick());
        assertEquals(HudTone.RADIUM.ordinal(), down.tone());
        assertEquals(0L, down.settled().strokeTick(), "set down at rest");
        assertTrue(down.settled().down());
    }

    @Test
    void theGogglesSurviveASaveAndTheWire() {
        final AviatorGoggles g = new AviatorGoggles(true, 987654321L, 2);
        final var json = AviatorGoggles.CODEC.encodeStart(JsonOps.INSTANCE, g).getOrThrow();
        assertEquals(g, AviatorGoggles.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());

        assertEquals(AviatorGoggles.DEFAULT,
                AviatorGoggles.CODEC.parse(JsonOps.INSTANCE, new com.google.gson.JsonObject()).getOrThrow());
        final ByteBuf buf = Unpooled.buffer();
        AviatorGoggles.STREAM_CODEC.encode(buf, g);
        assertEquals(g, AviatorGoggles.STREAM_CODEC.decode(buf));
    }

    @Test
    void theSightsFiguresSurviveTheWire() {
        final AerialHudPacket p = new AerialHudPacket("GO TO", "PILOT LEFT THE HELM", 38.0f, 52.0f, Float.NaN, 240.0f,
                3.2f, 52.0f, "LODESTONE  312 b  1:10", 142.6f, 150.0f, Float.NaN, 147.0f, 2.6f, 38.0f, 0.5f, 0.5f, 4.2f,
                9.4f, 0.86f, -0.8f, 0.8f, "TERRAIN AHEAD: RIDGE AT 147 IN 60 b. CLIMBING TO CLEAR IT BY 4.", true);
        final FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        AerialHudPacket.STREAM_CODEC.encode(buf, p);
        final AerialHudPacket back = AerialHudPacket.STREAM_CODEC.decode(buf);
        assertEquals(p.mode(), back.mode());
        assertEquals(p.sub(), back.sub());
        assertEquals(p.targetLabel(), back.targetLabel());
        assertEquals(p.banner(), back.banner());
        assertTrue(back.alert());
        assertTrue(Float.isNaN(back.track()), "a NaN stays a NaN: no track");
        assertTrue(Float.isNaN(back.ceiling()));
        assertEquals(p.heading(), back.heading());
        assertEquals(p.heldAltitude(), back.heldAltitude());
        assertEquals(p.terrain(), back.terrain());
        assertEquals(p.roll(), back.roll());
        assertEquals(0, buf.readableBytes(), "every byte written is read back");
    }
}
