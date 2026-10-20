package com.createstats.pducabinet;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

class CabinetWiringTest {
    private static final Path JAVA=Path.of("src/main/java/com/createstats");
    private static String code(String file) throws Exception {
        return Files.readString(JAVA.resolve(file)).replaceAll("(?s)/\\*.*?\\*/","").replaceAll("//[^\\n]*","");
    }
    private static boolean statementContains(String code,String start,String... required) {
        var m=Pattern.compile(start).matcher(code);
        if(!m.find())return false;
        int end=code.indexOf(';',m.start());if(end<0)return false;
        String statement=code.substring(m.start(),end);
        return java.util.Arrays.stream(required).allMatch(statement::contains);
    }

    @Test void registeredCabinetHasAnInheritedControllerAndItsOwnCapabilitiesRendererAndDisplayLink() throws Exception {
        assertTrue(statementContains(code("registry/ModBlocks.java"),"PDU_CABINET\\s*=","\"pdu_cabinet\"","PduCabinetBlock::new","simpleItem()"));
        assertTrue(statementContains(code("registry/ModBlockEntities.java"),"PDU_CABINET\\s*=","PduCabinetBE::new","ModBlocks.PDU_CABINET.get()","CabinetRenderer::new"));
        assertTrue(hasCapabilities(code("power/FeCapabilities.java")));
        String be=code("blockentity/PduCabinetBE.java");
        assertTrue(be.contains("extends PowerDistributionUnitBE"));
        assertFalse(Pattern.compile("void\\s+tick\\s*\\(").matcher(be).find(),"cabinet must not fork the PDU solve");
        assertTrue(statementContains(code("CreateStats.java"),"this\\.registerDisplaySourceMapping\\(powerSource, ModBlocks\\.PDU_CABINET","ModBlocks.PDU_CABINET"));
        assertTrue(code("block/PduCabinetBlock.java").contains("ClientScreenAccess.openPowerDistributionUnit(be)"));
        assertTrue(code("pducabinet/CabinetTelemetry.java").contains("afterLevelTick(LevelTickEvent.Post event)"));
    }
    private static boolean hasCapabilities(String source) {
        return statementContains(source,"event\\.registerBlockEntity\\(Capabilities\\.EnergyStorage\\.BLOCK, ModBlockEntities\\.PDU_CABINET\\.get\\(\\)",
                "guard(","be.energyHandle()","be.backOutputHandle()","be.inputHandle(side)");
    }

    @Test void guardsRejectMissingAndBorrowedCapabilityEvidence() throws Exception {
        String real=code("power/FeCapabilities.java");

        assertFalse(hasCapabilities(real.replaceFirst("be\\.backOutputHandle\\(\\)","null")));
        int start=real.indexOf("ModBlockEntities.PDU_CABINET.get()");int end=real.indexOf(';',start);
        String first=real.substring(start,end);
        assertFalse(hasCapabilities(real.replace(first,first.replace("be.inputHandle(side)","null"))));
        assertFalse(hasCapabilities(real.replace("ModBlockEntities.PDU_CABINET.get()","ModBlockEntities.POWER_DISTRIBUTION_UNIT.get()")));
    }

    @Test void upperClicksNormalizeBeforeLookupAndBeforeRecordingAnEndpoint() throws Exception {
        String item=code("item/CopperWireItem.java");
        int declaration=item.indexOf("private static InteractionResult useOnPanel(");
        String method=item.substring(declaration,item.indexOf("private static InteractionResult useOnBatteryController(",declaration));
        int normalize=method.indexOf("PduCabinetBlock.controllerPos(clickedState, pos)");
        assertTrue(normalize>=0 && normalize<method.indexOf("level.getBlockEntity(pos)"));
        assertTrue(statementContains(method,"final int stud\\s*=","PduCabinetBlock.pickedStud(clickedState, clickedPos, hit, context.getClickedFace())"));
        assertTrue(statementContains(method,"return connect\\(","PduStudWiring.Endpoint.stud(pos.asLong(), stud)"));
        assertTrue(code("client/PduStudHighlight.java").contains("PduCabinetBlock.pickedStud(state, clicked, hit.getLocation(), hit.getDirection())"));
    }
}
