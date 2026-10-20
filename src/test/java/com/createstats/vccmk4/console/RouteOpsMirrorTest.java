package com.createstats.vccmk4.console;

import com.createstats.surveyor.RouteOrders;
import com.createstats.surveyor.network.RouteActionPacket;
import com.createstats.surveyor.network.SurveyorEditPacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RouteOpsMirrorTest {
    @Test
    void theRoutePagesOrdersAreThePackets() {
        assertEquals(RouteActionPacket.ASSIGN, Vcc4Console.ROUTE_ASSIGN);
        assertEquals(RouteActionPacket.UNASSIGN, Vcc4Console.ROUTE_UNASSIGN);
        assertEquals(RouteActionPacket.START, Vcc4Console.ROUTE_START);
        assertEquals(RouteActionPacket.PAUSE, Vcc4Console.ROUTE_PAUSE);
        assertEquals(RouteActionPacket.SKIP_STOP, Vcc4Console.ROUTE_SKIP_STOP);
        assertEquals(RouteActionPacket.ARM_RECORD, Vcc4Console.ROUTE_ARM_RECORD);
        assertEquals(RouteActionPacket.STOP_RECORD, Vcc4Console.ROUTE_STOP_RECORD);
        assertEquals(RouteActionPacket.OPTION, Vcc4Console.ROUTE_OPTION);
        assertEquals(RouteActionPacket.CANCEL_RECORD, Vcc4Console.ROUTE_CANCEL_RECORD);
        assertEquals(RouteActionPacket.KEEP_TRACE, Vcc4Console.ROUTE_KEEP_TRACE);
        assertEquals(RouteActionPacket.REMOVE_SCHEDULE, Vcc4Console.ROUTE_REMOVE_SCHEDULE);
        assertEquals(RouteOrders.OPT_CRUISE, Vcc4Console.ROUTE_OPT_CRUISE);
        assertEquals(RouteOrders.OPT_COMFORT, Vcc4Console.ROUTE_OPT_COMFORT);
        assertEquals(RouteOrders.OPT_START_ON_ASSIGN, Vcc4Console.ROUTE_OPT_START_ON_ASSIGN);
        assertEquals(RouteOrders.OPT_HORN, Vcc4Console.ROUTE_OPT_HORN);
    }

    @Test
    void theBoardsOrdersAreThePackets() {
        assertEquals(RouteActionPacket.UNASSIGN, PathwayBoard.ORDER_UNASSIGN);
        assertEquals(RouteActionPacket.START, PathwayBoard.ORDER_START);
        assertEquals(RouteActionPacket.PAUSE, PathwayBoard.ORDER_PAUSE);
        assertEquals(RouteActionPacket.SKIP_STOP, PathwayBoard.ORDER_SKIP);
        assertEquals(RouteActionPacket.SEND_TO, PathwayBoard.ORDER_SEND_TO);
        assertEquals(RouteActionPacket.RECALL, PathwayBoard.ORDER_RECALL);
    }

    @Test
    void theBoardsEditsAreThePackets() {
        assertEquals(SurveyorEditPacket.MOVE_NODE, PathwayBoard.OP_MOVE);
        assertEquals(SurveyorEditPacket.DELETE_NODE, PathwayBoard.OP_DELETE_NODE);
        assertEquals(SurveyorEditPacket.SET_SPEED, PathwayBoard.OP_SET_SPEED);
        assertEquals(SurveyorEditPacket.SET_CORNER, PathwayBoard.OP_SET_CORNER);
        assertEquals(SurveyorEditPacket.SET_NODE_NAME, PathwayBoard.OP_SET_NODE_NAME);
        assertEquals(SurveyorEditPacket.SET_STOP, PathwayBoard.OP_SET_STOP);
        assertEquals(SurveyorEditPacket.SET_NAME, PathwayBoard.OP_SET_NAME);
        assertEquals(SurveyorEditPacket.SET_COLOUR, PathwayBoard.OP_SET_COLOUR);
        assertEquals(SurveyorEditPacket.SET_KIND, PathwayBoard.OP_SET_KIND);
        assertEquals(SurveyorEditPacket.SET_CRUISE, PathwayBoard.OP_SET_CRUISE);
        assertEquals(SurveyorEditPacket.SET_COMFORT, PathwayBoard.OP_SET_COMFORT);
        assertEquals(SurveyorEditPacket.SET_SHARED, PathwayBoard.OP_SET_SHARED);
        assertEquals(SurveyorEditPacket.DELETE_PATHWAY, PathwayBoard.OP_DELETE_PATHWAY);
        assertEquals(SurveyorEditPacket.REVERSE, PathwayBoard.OP_REVERSE);
        assertEquals(SurveyorEditPacket.FAIR, PathwayBoard.OP_FAIR);
        assertEquals(SurveyorEditPacket.FIX, PathwayBoard.OP_FIX);
        assertEquals(SurveyorEditPacket.SET_AMPHIBIOUS, PathwayBoard.OP_SET_AMPHIBIOUS);
    }

    @Test
    void theBoardsFixesAndLevelsAreTheSurveys() {
        assertEquals(com.createstats.surveyor.core.PathSurvey.Fix.MOVE_NODE.ordinal(), PathwayBoard.FIX_MOVE_NODE);
        assertEquals(com.createstats.surveyor.core.PathSurvey.Fix.FAIR.ordinal(), PathwayBoard.FIX_FAIR);
        assertEquals(com.createstats.surveyor.core.PathSurvey.Fix.AMPHIBIOUS.ordinal(), PathwayBoard.FIX_AMPHIBIOUS);
        assertEquals(com.createstats.surveyor.core.PathSurvey.Fix.CLEAR.ordinal(), PathwayBoard.FIX_CLEAR);
        assertEquals(com.createstats.surveyor.core.PathSurvey.Fix.WIDEN.ordinal(), PathwayBoard.FIX_WIDEN);
        assertEquals(com.createstats.surveyor.core.SegmentCheck.Level.UNKNOWN.ordinal(), PathwayBoard.HEALTH_UNKNOWN);
        assertEquals(com.createstats.surveyor.core.SegmentCheck.Level.AMBER.ordinal(), PathwayBoard.HEALTH_AMBER);
        assertEquals(com.createstats.surveyor.core.SegmentCheck.Level.RED.ordinal(), PathwayBoard.HEALTH_RED);

        for (com.createstats.surveyor.core.PathSurvey.Fix f : com.createstats.surveyor.core.PathSurvey.Fix.values()) {
            boolean button = f == com.createstats.surveyor.core.PathSurvey.Fix.MOVE_NODE || f == com.createstats.surveyor.core.PathSurvey.Fix.FAIR
                    || f == com.createstats.surveyor.core.PathSurvey.Fix.AMPHIBIOUS || f == com.createstats.surveyor.core.PathSurvey.Fix.WIDEN;
            assertEquals(button, PathwayBoard.fixKey(f.ordinal()) != null, f + ": a button exactly when there is something to do");
        }
    }
}
