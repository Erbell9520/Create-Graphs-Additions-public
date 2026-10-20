package com.createstats.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopyableSettingsContractTest {
    @Test
    void theClipboardKeyIsTheFamilyKey() {
        assertEquals("create_stats:test", new Fake(7).getClipboardKey());
    }

    @Test
    void aCopyCarriesTheSettingsAndOnlyWhenThereAreSome() {
        final CompoundTag copied = new CompoundTag();
        assertTrue(new Fake(7).writeToClipboard(null, copied, null));
        assertEquals(7, copied.getInt("Value"));

        final Fake plain = new Fake(7);
        plain.has = false;
        final CompoundTag none = new CompoundTag();
        assertFalse(plain.writeToClipboard(null, none, null), "a plain rocker has nothing to copy");
        assertTrue(none.isEmpty());
    }

    @Test
    void aClipboardHoldingNothingForThisFamilyIsNotAPaste() {
        final Fake lamp = new Fake(3);
        assertFalse(lamp.readFromClipboard(null, new CompoundTag(), null, null, false));
        assertFalse(lamp.asked, "an empty tag never reaches applySettings");
        assertEquals(3, lamp.value);
    }

    @Test
    void aSimulatedPasteAnswersAndChangesNothing() {
        final Fake target = new Fake(3);
        final CompoundTag copied = new CompoundTag();
        new Fake(9).writeToClipboard(null, copied, null);
        assertTrue(target.readFromClipboard(null, copied, null, null, true));
        assertEquals(3, target.value, "the client's simulate and the hover hint must not change a block");
        assertTrue(target.readFromClipboard(null, copied, null, null, false));
        assertEquals(9, target.value);
    }

    @Test
    void aRepeatOfTheSamePasteIsRecognised() {
        final Fake target = new Fake(9);
        final CompoundTag same = new CompoundTag();
        same.putInt("Value", 9);
        final CompoundTag other = new CompoundTag();
        other.putInt("Value", 8);
        assertTrue(target.alreadyHolds(same, null));
        assertFalse(target.alreadyHolds(other, null));
    }

    @Test
    void aMemberOfAMultiblockCopiesAndPastesItsController() {
        final Fake controller = new Fake(5);
        final Fake member = new Fake(0);
        member.holder = controller;
        final CompoundTag copied = new CompoundTag();
        assertTrue(member.writeToClipboard(null, copied, null));
        assertEquals(5, copied.getInt("Value"), "a click on any block of a wall copies the wall's face");

        final CompoundTag pasted = new CompoundTag();
        pasted.putInt("Value", 8);
        assertTrue(member.readFromClipboard(null, pasted, null, null, false));
        assertEquals(8, controller.value);
        assertEquals(0, member.value);
    }

    @Test
    void aSchematicCarriesTheFreshDefaultsAndTheSettings() {
        final CompoundTag printed = new CompoundTag();
        new Fake(4).writeSafe(printed, null);
        assertEquals(Set.of("Tier", "Value"), printed.getAllKeys());
        assertEquals(2, printed.getInt("Tier"));
        assertEquals(4, printed.getInt("Value"));
    }

    private static final class Fake implements CopyableSettings {
        int value;
        boolean has = true;
        boolean asked;
        CopyableSettings holder = this;

        Fake(int value) {
            this.value = value;
        }

        @Override
        public String settingsKey() {
            return "create_stats:test";
        }

        @Override
        public boolean hasCopyableSettings() {
            return has;
        }

        @Override
        public CopyableSettings settingsHolder() {
            return holder;
        }

        @Override
        public void writeSettings(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putInt("Value", value);
        }

        @Override
        public boolean applySettings(CompoundTag tag, HolderLookup.Provider registries, @Nullable Player player,
                                     boolean simulate) {
            asked = true;
            if (!tag.contains("Value")) {
                return false;
            }
            if (!simulate) {
                value = tag.getInt("Value");
            }
            return true;
        }

        @Override
        public void writeFreshDefaults(CompoundTag tag) {
            tag.putInt("Tier", 2);
        }
    }
}
