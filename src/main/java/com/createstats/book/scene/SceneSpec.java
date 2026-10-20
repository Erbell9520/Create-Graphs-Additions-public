package com.createstats.book.scene;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.level.block.state.BlockState;

public record SceneSpec(List<Placement> placements, float pitch, boolean spin) {
    public SceneSpec {
        placements = List.copyOf(placements);
    }

    public record Placement(int x, int y, int z, Supplier<BlockState> state) {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static SceneSpec single(Supplier<BlockState> state) {
        return builder().at(0, 0, 0, state).build();
    }

    public static final class Builder {
        private final List<Placement> placements = new ArrayList<>();
        private float pitch = 22.0f;
        private boolean spin = true;

        private Builder() {
        }

        public Builder at(int x, int y, int z, Supplier<BlockState> state) {
            this.placements.add(new Placement(x, y, z, state));
            return this;
        }

        public Builder run(int fromX, int toX, int y, int z, Supplier<BlockState> state) {
            for (int x = fromX; x <= toX; x++) {
                at(x, y, z, state);
            }
            return this;
        }

        public Builder pitch(float degrees) {
            this.pitch = degrees;
            return this;
        }

        public Builder still() {
            this.spin = false;
            return this;
        }

        public SceneSpec build() {
            return new SceneSpec(placements, pitch, spin);
        }
    }
}
