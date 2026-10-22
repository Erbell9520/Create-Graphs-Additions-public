package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CouplerForeignTest {
    private static final String COUPLER = "com.createstats.blockentity.UniversalPowerCouplerBE";
    private static final String OUR_CABLE = "com.createstats.blockentity.CableBE";
    private static final String OUR_BANK = "com.createstats.blockentity.CapacitorBankBE";
    private static final String OUR_DOOR_BLOCK = "com.createstats.block.PoweredDoorBlock";
    private static final String NEW_AGE = "org.antarcticgardens.cna.content.electricity.connector.ElectricalConnectorBlockEntity";
    private static final String CA = "com.mrh0.createaddition.blocks.connector.ConnectorBlockEntity";
    private static final String MEK = "mekanism.common.tile.TileEntityEnergyCube";
    private static final String VANILLA_BLOCK = "net.minecraft.world.level.block.Block";

    @Test
    @DisplayName("anything of ours is refused, whatever it implements and whether or not it holds FE")
    void oursIsNeverForeign() {
        for (String ours : new String[] {COUPLER, OUR_CABLE, OUR_BANK,
                "com.createstats.blockentity.KineticDynamoBE", "com.createstats.blockentity.PowerMeterBE",
                "com.createstats.blockentity.PowerLimiterBE", "com.createstats.blockentity.StarterCapacitorBE"}) {
            assertEquals(CouplerForeign.Verdict.OURS, CouplerForeign.bolted(ours, VANILLA_BLOCK, false, false, true));
            assertEquals(CouplerForeign.Verdict.OURS, CouplerForeign.wired(ours, VANILLA_BLOCK, false, true));
        }

        assertEquals(CouplerForeign.Verdict.OURS, CouplerForeign.bolted(null, OUR_DOOR_BLOCK, false, false, true));
        assertFalse(CouplerForeign.Verdict.OURS.isEndpoint());
    }

    @Test
    @DisplayName("their New Age connector counts on the face only when it is bolted to this Coupler")
    void newAgeConnectorMustSupportThis() {
        assertEquals(CouplerForeign.Verdict.NEW_AGE_CONNECTOR,
                CouplerForeign.bolted(NEW_AGE, VANILLA_BLOCK, true, true, true));
        assertEquals(CouplerForeign.Verdict.NEW_AGE_ELSEWHERE,
                CouplerForeign.bolted(NEW_AGE, VANILLA_BLOCK, true, false, true));
        assertFalse(CouplerForeign.Verdict.NEW_AGE_ELSEWHERE.isEndpoint(),
                "a connector bolted to another block was priced at this Coupler's key (KI-212 item 3)");

        assertEquals(CouplerForeign.Verdict.NEW_AGE_CONNECTOR, CouplerForeign.wired(NEW_AGE, VANILLA_BLOCK, true, true));
    }

    @Test
    @DisplayName("Crafts and Additions connectors and every other foreign FE block are endpoints; nothing is empty")
    void foreignKinds() {
        assertEquals(CouplerForeign.Verdict.CREATE_ADDITION, CouplerForeign.bolted(CA, VANILLA_BLOCK, false, false, true));
        assertEquals(CouplerForeign.Verdict.GENERIC, CouplerForeign.bolted(MEK, VANILLA_BLOCK, false, false, true));
        assertEquals(CouplerForeign.Verdict.GENERIC, CouplerForeign.wired(null, "net.minecraft.world.level.block.LodestoneBlock", false, true));
        assertEquals(CouplerForeign.Verdict.EMPTY, CouplerForeign.bolted(MEK, VANILLA_BLOCK, false, false, false));
        assertEquals(CouplerForeign.Verdict.EMPTY, CouplerForeign.bolted(null, VANILLA_BLOCK, false, false, false));
        assertEquals(CouplerEndpoint.Family.CREATE_ADDITION, CouplerForeign.Verdict.CREATE_ADDITION.family());
        assertEquals(CouplerEndpoint.Family.NONE, CouplerForeign.Verdict.OURS.family());
    }

    @Test
    @DisplayName("a Crafts and Additions connector's mode opposes ours exactly when it would push into an exporter or pull an importer")
    void createAdditionModes() {
        assertTrue(CouplerForeign.createAdditionOpposes("push", false));
        assertFalse(CouplerForeign.createAdditionOpposes("push", true));
        assertTrue(CouplerForeign.createAdditionOpposes("pull", true));
        assertFalse(CouplerForeign.createAdditionOpposes("pull", false));
        assertFalse(CouplerForeign.createAdditionOpposes("none", true));
        assertFalse(CouplerForeign.createAdditionOpposes("passive", false));
        assertFalse(CouplerForeign.createAdditionOpposes(null, false));
        assertTrue(CouplerForeign.createAdditionDrives("push") && CouplerForeign.createAdditionDrives("pull"));
        assertFalse(CouplerForeign.createAdditionDrives("passive") || CouplerForeign.createAdditionDrives("none"));
    }

    @Test
    @DisplayName("ours is decided by package, so a foreign class that merely contains our name is not ours")
    void packagePrefixOnly() {
        assertTrue(CouplerForeign.isOurs("com.createstats.anything.Deep", null));
        assertFalse(CouplerForeign.isOurs("org.other.com.createstats.Fake", "org.other.Block"));
        assertFalse(CouplerForeign.isOurs(null, null));
    }
}
