package com.createstats.client;

import com.createstats.CreateStats;
import com.createstats.client.ponder.Labels;
import com.createstats.client.ponder.StudSpans;
import com.createstats.client.ponder.coupler.CouplerPonder;
import com.createstats.client.ponder.coupler.CouplerPonderScript;
import com.createstats.power.WireTier;
import com.createstats.registry.ModItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.TextElementBuilder;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class CouplerScenes {
    private CouplerScenes() {
    }

    private static final class Clock {
        int now;

        void until(CreateSceneBuilder scene, int tick) {
            if (tick < now) {
                CreateStats.LOGGER.warn("[Ponder] coupler cue {} is behind the scene clock {}", tick, now);
                return;
            }
            if (tick > now) {
                scene.idle(tick - now);
                now = tick;
            }
        }
    }

    private static TextElementBuilder say(CreateSceneBuilder scene, Clock c, int index, Vec3 at, boolean key) {
        c.until(scene, CouplerPonderScript.CAPTIONS[index]);
        final TextElementBuilder b = scene.overlay().showText(CouplerPonderScript.HOLD).pointAt(at).placeNearTarget();
        return key ? b.attachKeyFrame() : b;
    }

    public static void exchange(SceneBuilder builder, SceneBuildingUtil util) {
        final CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("universal_power_coupler", "The Universal Power Coupler");
        scene.configureBasePlate(0, 0, CouplerPonderScript.PLATE);
        scene.scaleSceneView(CouplerPonderScript.SCALE);
        final Clock c = new Clock();
        final BlockPos coupler = CouplerPonder.pos(CouplerPonderScript.COUPLER);
        final BlockPos theirs = CouplerPonder.pos(CouplerPonderScript.THEIRS);
        final BlockPos ours = CouplerPonder.pos(CouplerPonderScript.OURS);
        final int length = CouplerPonderScript.LENGTH;

        scene.addInstruction(CouplerPonder.drive(0, length));

        final String theirsText = I18n.get("create_stats.ponder.universal_power_coupler.label_theirs");
        final String oursText = I18n.get("create_stats.ponder.universal_power_coupler.label_ours");
        final net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
        final int theirsDx = -(font.width(theirsText) + 4) / 2;
        final int oursDx = -(font.width(oursText) + 4) / 2;
        final Vec3 theirsTag = new Vec3(theirs.getX() + 0.5, theirs.getY() + 0.5, theirs.getZ() + 0.1);
        final Vec3 oursTag = Vec3.atCenterOf(ours).add(0.0, 0.1, 0.0);
        scene.addInstruction(Labels.tag(pt -> theirsTag, t -> theirsText, t -> 0xE0A44C, theirsDx,
                CouplerPonderScript.TAG_BELOW, CouplerPonderScript.THEIRS_ON,
                CouplerPonderScript.THEIRS_OUT - CouplerPonderScript.THEIRS_ON));
        scene.addInstruction(Labels.tag(pt -> theirsTag, t -> theirsText, t -> 0xE0A44C, theirsDx,
                CouplerPonderScript.TAG_BELOW, CouplerPonderScript.THEIRS_BACK_ON,
                length - CouplerPonderScript.THEIRS_BACK_ON));
        scene.addInstruction(Labels.tag(pt -> oursTag, t -> oursText, t -> 0x6BE4FF, oursDx,
                CouplerPonderScript.TAG_BELOW, CouplerPonderScript.WIRED, length - CouplerPonderScript.WIRED));

        scene.world().showSection(util.select().layer(0), Direction.UP);
        c.until(scene, CouplerPonderScript.COUPLER_IN);
        scene.world().showSection(util.select().position(coupler), Direction.DOWN);
        say(scene, c, 0, CouplerPonder.px(8.0, 14.5, 8.0), true)
                .text("The Power Coupler carries FE between your grid and another mod's power network")
                .colored(PonderPalette.BLUE);

        c.until(scene, CouplerPonderScript.THEIRS_IN);
        scene.world().showSection(util.select().position(theirs), Direction.SOUTH);
        say(scene, c, 1, CouplerPonder.px(4.5, 13.4, 1.25), true)
                .text("Bolt their machine or connector to the amber end: the amber lens lights")
                .colored(PonderPalette.INPUT);

        c.until(scene, CouplerPonderScript.SPAN_HINT);
        scene.overlay().showControls(CouplerPonder.px(12.0, 15.5, 1.25), Pointing.DOWN, 40).rightClick()
                .withItem(new ItemStack(ModItems.COPPER_WIRE.get()));
        say(scene, c, 2, CouplerPonder.px(12.0, 15.25, 1.25), false)
                .text("Or wire it to the amber post on top: their span lands on the copper cap")
                .colored(PonderPalette.INPUT);

        c.until(scene, CouplerPonderScript.OURS_IN);
        scene.world().showSection(util.select().position(ours), Direction.DOWN);
        c.until(scene, CouplerPonderScript.WIRE - 8);
        scene.overlay().showControls(CouplerPonder.px(12.0, 15.0, 14.75), Pointing.DOWN, 30).rightClick()
                .withItem(new ItemStack(ModItems.COPPER_WIRE.get()));
        scene.addInstruction(StudSpans.grow("coupler_wire", CouplerPonder.px(12.0, 14.75, 14.75), coupler, ours,
                WireTier.COPPER, CouplerPonderScript.WIRE, CouplerPonderScript.WIRE_TICKS));
        say(scene, c, 3, CouplerPonder.px(12.0, 14.75, 14.75), true)
                .text("Wire the cyan post to your grid: the cyan lens lights")
                .colored(PonderPalette.INPUT);

        say(scene, c, 4, CouplerPonder.px(0.6, 6.3, 11.5), true)
                .text("Lever toward the cyan end: it pulls their power into your grid")
                .colored(PonderPalette.GREEN);
        c.until(scene, CouplerPonderScript.PUSH_CLICK);
        scene.overlay().showControls(CouplerPonder.px(0.0, 6.3, 11.5), Pointing.DOWN, 20).rightClick();
        say(scene, c, 5, CouplerPonder.px(0.6, 6.3, 4.5), false)
                .text("Click the lever to push instead: your grid's power goes out into theirs")
                .colored(PonderPalette.GREEN);
        c.until(scene, CouplerPonderScript.OFF_CLICK);
        scene.overlay().showControls(CouplerPonder.px(0.0, 6.3, 4.5), Pointing.DOWN, 20).rightClick()
                .whileSneaking();
        say(scene, c, 6, CouplerPonder.px(0.6, 7.75, 8.0), false)
                .text("Sneak-click it upright to switch off: both docks lift and nothing crosses")
                .colored(PonderPalette.WHITE);
        c.until(scene, CouplerPonderScript.BACK_CLICK);
        scene.overlay().showControls(CouplerPonder.px(0.0, 7.75, 8.0), Pointing.DOWN, 20).rightClick();
        say(scene, c, 7, CouplerPonder.px(0.6, 6.3, 4.5), false)
                .text("Click it again and it runs the way it last ran")
                .colored(PonderPalette.WHITE);

        scene.addInstruction(CouplerPonder.orbit(CouplerPonderScript.TURN_DEGREES, CouplerPonderScript.TURN,
                CouplerPonderScript.TURN_TICKS));
        say(scene, c, 8, CouplerPonder.px(8.0, 14.5, 8.0), true)
                .text("Each quarter turn carries a charged cassette across, faster the more FE crosses")
                .colored(PonderPalette.BLUE);
        say(scene, c, 9, CouplerPonder.px(15.4, 3.75, 12.25), true)
                .text("A dropped shutter bares the striped target: a loop, power would circle back")
                .colored(PonderPalette.RED);
        say(scene, c, 10, CouplerPonder.px(8.0, 15.0, 14.5), false)
                .text("Only our dock lifted: a power board has shed the export")
                .colored(PonderPalette.RED);

        c.until(scene, CouplerPonderScript.THEIRS_OUT);
        scene.world().hideSection(util.select().position(theirs), Direction.NORTH);
        say(scene, c, 11, CouplerPonder.px(4.5, 13.4, 1.25), false)
                .text("A dark lens: nothing is connected on that side")
                .colored(PonderPalette.RED);
        c.until(scene, CouplerPonderScript.THEIRS_BACK);
        scene.world().showSection(util.select().position(theirs), Direction.SOUTH);
        say(scene, c, 12, CouplerPonder.px(8.0, 14.5, 8.0), false)
                .text("Back on, it carries again: the full cassette crosses toward their side")
                .colored(PonderPalette.GREEN);

        c.until(scene, length);
    }
}
