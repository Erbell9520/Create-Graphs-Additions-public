package com.createstats.surveyor.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

final class OwnerPathways {
    private OwnerPathways() {
    }

    static Pathway painted() {
        return load("owner_pathway_1.bin");
    }

    static Pathway lake() {
        return load("owner_pathway_2.bin");
    }

    static Pathway plaza() {
        return load("owner_pathway_3.bin");
    }

    static Pathway load(String name) {
        try (InputStream in = OwnerPathways.class.getResourceAsStream("/surveyor/" + name)) {
            if (in == null) throw new IllegalStateException("missing test resource " + name);
            return PathwayCodec.decode(in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static boolean water(double x, double z) {
        return x >= 450.0;
    }
}
