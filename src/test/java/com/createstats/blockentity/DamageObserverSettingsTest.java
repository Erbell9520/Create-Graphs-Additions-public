package com.createstats.blockentity;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DamageObserverSettingsTest {
    @Test
    void defaultGovernorSignMappingMatchesSharedBattleScreenConvention() {
        DamageObserverSettings settings = new DamageObserverSettings();

        assertEquals(-24, settings.mapYawGovernorRpm(24));
        assertEquals(24, settings.mapPitchGovernorRpm(24));
        assertFalse(settings.isWeaponYawInverted());
        assertFalse(settings.isWeaponPitchInverted());
    }

    @Test
    void invertTogglesFlipGovernorSignMapping() {
        DamageObserverSettings settings = new DamageObserverSettings();

        settings.toggleWeaponYawInverted();
        settings.toggleWeaponPitchInverted();

        assertEquals(24, settings.mapYawGovernorRpm(24));
        assertEquals(-24, settings.mapPitchGovernorRpm(24));
        assertTrue(settings.isWeaponYawInverted());
        assertTrue(settings.isWeaponPitchInverted());
    }

    @Test
    void saveLoadPreservesGovernorDirectionFlags() {
        DamageObserverSettings settings = new DamageObserverSettings();
        settings.toggleWeaponYawInverted();
        settings.toggleWeaponPitchInverted();

        DamageObserverSettings loaded = DamageObserverSettings.load(settings.save());

        assertTrue(loaded.isWeaponYawInverted());
        assertTrue(loaded.isWeaponPitchInverted());
        assertEquals(-12, loaded.mapYawGovernorRpm(-12));
        assertEquals(12, loaded.mapPitchGovernorRpm(-12));
    }

    @Test
    void encodeDecodePreservesGovernorDirectionFlags() {
        DamageObserverSettings settings = new DamageObserverSettings();
        settings.toggleWeaponYawInverted();
        settings.toggleWeaponPitchInverted();

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        settings.encode(buf);

        DamageObserverSettings decoded = DamageObserverSettings.decode(buf);

        assertTrue(decoded.isWeaponYawInverted());
        assertTrue(decoded.isWeaponPitchInverted());
        assertEquals(7, decoded.mapYawGovernorRpm(7));
        assertEquals(-7, decoded.mapPitchGovernorRpm(7));
    }
}
