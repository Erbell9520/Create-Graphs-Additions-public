package com.createstats.vccmk4.console;

import com.createstats.surveyor.core.PathKind;
import com.createstats.surveyor.core.Pathway;
import com.createstats.surveyor.core.Stop;
import com.createstats.surveyor.core.StopCondition;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeRadialLayoutTest {
    private static final Path OUT = Path.of("build/node-radial");

    private static final int[][] SCREENS = {{427, 240}, {640, 360}, {853, 533}};

    private static Pathway quarry() {
        List<double[]> pts = new ArrayList<>();
        for (int i = 0; i < 8; i++) pts.add(new double[]{i * 6.0, 64, (i % 3) * 2.0});
        Pathway p = Pathway.create(new UUID(9, 9), "Quarry run", 4, new UUID(1, 1)).appendAll(pts);
        p = p.update(4, n -> n.withStop(Stop.DEFAULT.withRules(List.of(List.of(new StopCondition.Wait(10))))).withName("Quarry"));
        return p.update(6, n -> n.withSpeedLimit(4.0));
    }

    @Test
    void theSectorsAreCreatesSectors() {
        assertEquals(-1, NodeRadial.sectorAt(0, -40, 6), "inside the dead middle");
        assertEquals(0, NodeRadial.sectorAt(0, -80, 6), "straight up");
        assertEquals(1, NodeRadial.sectorAt(70, -40, 6), "up and right: the next one clockwise");
        assertEquals(3, NodeRadial.sectorAt(0, 80, 6), "straight down: half way round");
        assertEquals(5, NodeRadial.sectorAt(-70, -40, 6), "up and left: the last");
        assertEquals(0, NodeRadial.sectorAt(-5, -80, 6), "just left of straight up is still the first");
        assertEquals(1, NodeRadial.sectorAt(80, 0, 4), "four sectors: right is the next after the top");
        assertEquals(2, NodeRadial.sectorAt(0, 80, 4), "and down the one after that");
    }

    private static NodeRadial.Item item(NodeRadial.Page page, String id) {
        for (NodeRadial.Item it : page.items()) if (it.id().equals(id)) return it;
        return null;
    }
}
