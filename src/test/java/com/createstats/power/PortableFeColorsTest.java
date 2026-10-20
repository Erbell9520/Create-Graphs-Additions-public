package com.createstats.power;

import net.minecraft.ChatFormatting;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PortableFeColorsTest {
    @Test
    @DisplayName("each dark tone is the chat colour its legacy code names")
    void darkTonesMatchTheirCodes() {
        assertEquals(ChatFormatting.GREEN.toString(), PortableFeColors.CHARGE_CODE);
        assertEquals(ChatFormatting.GREEN.getColor().intValue(), PortableFeColors.CHARGE_ON_DARK);
        assertEquals(ChatFormatting.GOLD.toString(), PortableFeColors.DRAW_CODE);
        assertEquals(ChatFormatting.GOLD.getColor().intValue(), PortableFeColors.DRAW_ON_DARK);
    }

    @Test
    @DisplayName("code() and onDark() agree for both modes")
    void codeFollowsMode() {
        for (PortableFeTransfer.Mode mode : PortableFeTransfer.Mode.values()) {
            final ChatFormatting named = ChatFormatting.getByCode(PortableFeColors.code(mode).charAt(1));
            assertEquals(named.getColor().intValue(), PortableFeColors.onDark(mode), mode.name());
        }
    }
}
