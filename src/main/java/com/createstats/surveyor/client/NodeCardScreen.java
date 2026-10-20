package com.createstats.surveyor.client;

import com.createstats.vccmk4.client.PathwayBoardScreen;

import java.util.UUID;

// one screen for every edit
public final class NodeCardScreen {
    private NodeCardScreen() {
    }

    public static void open(UUID pathway, int node) {
        PathwayBoardScreen.open(pathway, null, node);
    }
}
