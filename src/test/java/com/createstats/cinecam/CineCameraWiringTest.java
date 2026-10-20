package com.createstats.cinecam;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CineCameraWiringTest {
    private static final Path SRC = Path.of("src/main/java/com/createstats");
    private static final Path ASSETS = Path.of("src/main/resources/assets/create_stats");
    private static final Path DATA = Path.of("src/main/resources/data/create_stats");

    private static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    static String statement(String s, int at) {
        if (at < 0) return "";
        int end = s.indexOf(';', at);
        return end < 0 ? "" : s.substring(at, end + 1);
    }

    @Test
    void theBlockAndItsBlockEntityAreRegisteredWithTheRenderer() throws IOException {
        String blocks = read(SRC.resolve("registry/ModBlocks.java"));
        String reg = statement(blocks, blocks.indexOf("CINE_CAMERA = "));
        assertTrue(reg.contains("\"cine_camera\"") && reg.contains("CineCameraBlock::new") && reg.contains(".register()"),
                "ModBlocks registers the Cine Camera block with its item");
        String bes = read(SRC.resolve("registry/ModBlockEntities.java"));
        String be = statement(bes, bes.indexOf("CINE_CAMERA = "));
        assertTrue(be.contains("CineCameraBE::new") && be.contains("ModBlocks.CINE_CAMERA")
                && be.contains("CineCameraRenderer::new"), "the block entity carries its renderer");
    }

    @Test
    void thePayloadsAreRegisteredServerbound() throws IOException {
        String packets = read(SRC.resolve("network/ModPackets.java"));
        for (String p : new String[]{"CineControlPacket", "CinePhotoPacket"}) {
            String s = statement(packets, packets.indexOf("registrar.playToServer(com.createstats.cinecam.net." + p));
            assertTrue(s.contains(p + ".STREAM_CODEC") && s.contains(p + "::handle"), p + " registered to the server");
        }
    }

    @Test
    void theDisplaySourceIsRegisteredAndMappedToTheBlock() throws IOException {
        String cs = read(SRC.resolve("CreateStats.java"));
        assertTrue(statement(cs, cs.indexOf("CINE_CAMERA_SOURCE = ")).contains("CineCameraDisplaySource::new"));
        assertTrue(statement(cs, cs.indexOf("this.registerDisplaySourceMapping(CreateStats.CINE_CAMERA_SOURCE"))
                .contains("ModBlocks.CINE_CAMERA"), "the source answers for the camera's block");
    }

    @Test
    void everyCinePartialHasItsModel() throws IOException {
        String pm = read(SRC.resolve("client/ModPartialModels.java"));
        Matcher m = Pattern.compile("block\\(\"(cine_camera/[a-z0-9_/]+)\"\\)").matcher(pm);
        int n = 0;
        while (m.find()) {
            n++;
            Path model = ASSETS.resolve("models/block/" + m.group(1) + ".json");
            assertTrue(Files.isRegularFile(model), "no model for partial " + m.group(1));
        }
        assertTrue(n >= 20, "the camera's partials are declared (" + n + ")");
    }

    @Test
    void theDataFilesAreThere() {
        for (Path p : new Path[]{ASSETS.resolve("blockstates/cine_camera.json"), ASSETS.resolve("models/item/cine_camera.json"),
                ASSETS.resolve("textures/item/cine_camera.png"), DATA.resolve("loot_table/blocks/cine_camera.json"),
                DATA.resolve("recipe/cine_camera.json")}) {
            assertTrue(Files.isRegularFile(p), "missing " + p);
        }
    }

    @Test
    void theFocusPullRunsWhereTheDepthIsStillWhole() throws IOException {
        String focus = read(SRC.resolve("cinecam/client/CineFocus.java"));
        assertTrue(focus.contains("@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT)"),
                "CineFocus is on the game bus");
        int at = focus.indexOf("public static void afterLevel(RenderLevelStageEvent event)");
        assertTrue(at > 0, "the pass hooks the level stages");
        assertTrue(focus.substring(Math.max(0, at - 80), at).contains("@SubscribeEvent(priority = EventPriority.LOWEST)"),
                "last in line, after every other mod's AFTER_LEVEL drawing");
        assertTrue(statement(focus, focus.indexOf("if (event.getStage()", at)).contains("RenderLevelStageEvent.Stage.AFTER_LEVEL"),
                "at AFTER_LEVEL, before GameRenderer clears the depth for the hand");
        assertTrue(focus.contains("GL11.GL_COLOR_BUFFER_BIT"), "the result goes back into main as colour only");
        try (var files = Files.walk(SRC.resolve("cinecam"))) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String s = read(p);
                assertFalse(s.contains("loadEffect("), p + " loads a post chain into vanilla's slot");
                assertFalse(s.contains("shaders/post/"), p + " names a post chain");
            }
        }
        assertFalse(Files.exists(ASSETS.resolve("shaders/post/cine_focus.json")), "1.14.99's chain is gone");
    }

    @Test
    void everyProgramCineFocusDrivesMatchesItsInputs() throws IOException {
        String focus = read(SRC.resolve("cinecam/client/CineFocus.java"));
        assertTrue(Files.isRegularFile(ASSETS.resolve("shaders/program/cine_quad.vsh")));
        Pattern use = Pattern.compile("\\b(meter|prefilter|bokeh|composite)\\.(setSampler|safeGetUniform)\\(\"(\\w+)\"");
        for (String var : new String[]{"meter", "prefilter", "bokeh", "composite"}) {
            String name = "cine_" + var;
            assertTrue(focus.contains(var + " = new EffectInstance(resources, CreateStats.MOD_ID + \":" + name + "\")"),
                    "CineFocus loads " + name);
            JsonObject json = JsonParser.parseString(read(ASSETS.resolve("shaders/program/" + name + ".json"))).getAsJsonObject();
            assertTrue("create_stats:cine_quad".equals(json.get("vertex").getAsString()), name + " uses the shared quad");
            assertTrue(("create_stats:" + name).equals(json.get("fragment").getAsString()), name + " names its fragment");
            String fsh = read(ASSETS.resolve("shaders/program/" + name + ".fsh"));
            Set<String> samplers = new HashSet<>();
            for (JsonElement e : json.getAsJsonArray("samplers")) samplers.add(e.getAsJsonObject().get("name").getAsString());
            Set<String> uniforms = new HashSet<>();
            for (JsonElement e : json.getAsJsonArray("uniforms")) uniforms.add(e.getAsJsonObject().get("name").getAsString());
            Set<String> setSamplers = new HashSet<>();
            Matcher m = use.matcher(focus);
            while (m.find()) {
                if (!m.group(1).equals(var)) continue;
                String input = m.group(3);
                boolean sampler = m.group(2).equals("setSampler");
                assertTrue((sampler ? samplers : uniforms).contains(input), name + ".json lists " + input);
                assertTrue(Pattern.compile("uniform\\s+" + (sampler ? "sampler2D" : "\\w+") + "\\s+" + input + "\\s*;")
                        .matcher(fsh).find(), name + ".fsh declares " + input);
                if (sampler) setSamplers.add(input);
            }
            assertTrue(setSamplers.equals(samplers), name + ": every sampler it declares is bound, " + samplers + " vs " + setSamplers);
        }
    }

    @Test
    void theLensViewDrawsNoHand() throws IOException {
        String view = read(SRC.resolve("cinecam/client/CineView.java"));
        assertTrue(body(view, view.indexOf("public static void hand(RenderHandEvent event)")).contains("event.setCanceled(true)"));
        assertTrue(body(view, view.indexOf("public static void screenEffect(RenderBlockScreenEffectEvent event)"))
                .contains("event.setCanceled(true)"));
    }

    @Test
    void theCameraFilmsOnItsOwnOncePerFilmFrame() throws IOException {
        String view = read(SRC.resolve("cinecam/client/CineView.java"));
        String pass = body(view, view.indexOf(AWAY_PASS));
        int draw = pass.indexOf("mc.gameRenderer.renderLevel(mc.getTimer());");
        assertTrue(draw > 0, "the camera's world is drawn");
        assertBefore(pass, "RenderSystem.enableScissor(", draw, "nothing shaded under the bars");
        assertAfter(pass, "RenderSystem.disableScissor();", draw, "the scissor lifted after");
        assertBefore(pass, "remotePass = true;", draw, "the camera's own frame is marked as such while it draws");
        assertAfter(pass, "remotePass = false;", draw, "and unmarked after");
        assertBefore(pass, "awayCull.begin(mc.levelRenderer);", draw, "the camera's own culling in place while it draws");
        assertAfter(pass, "awayCull.end(mc.levelRenderer);", draw, "the player's culling back after");
        assertAfter(pass, "CineRecorder.captureAway(mc, plan.frame());", draw, "drawn, then captured as its own frame of film");
        String frame = body(view, view.indexOf("public static void frame(RenderFrameEvent.Pre event)"));
        int away = frame.indexOf("} else {\n            for (FilmClock.Plan p : draws) filmPass(mc, cam, film, tick, p);");
        assertTrue(away > 0, "away, a pass for each frame of film the clock planned, and none otherwise");
        String focus = read(SRC.resolve("cinecam/client/CineFocus.java"));
        assertTrue(statement(focus, focus.indexOf("if (!CineView.drawingLens()")).contains("!FocusPull.on(CineView.tStop)"),
                "the blur only on the camera's own frames");
        String rec = read(SRC.resolve("cinecam/client/CineRecorder.java"));
        assertTrue(body(rec, rec.indexOf("public static void beforeGui(RenderGuiEvent.Pre event)"))
                .contains("if (frame >= 0 && CineView.inView()) s.captureFrame(Minecraft.getInstance(), frame);"),
                "the screen is the film only in view, and only on a frame drawn as one");
        String draws = body(rec, rec.indexOf("static List<FilmClock.Plan> draws("));
        assertTrue(draws.contains("FilmClock.draws(s.start, s.tpf, s.lastFrame, tick, partial, nextNow, max)"),
                "frames of film by the film clock");
        int starved = draws.indexOf("if (d.size() > s.free.size()) {");
        assertTrue(starved > 0 && draws.indexOf("return List.of();", starved) > starved, "and none planned that could not be taken");
        assertTrue(draws.indexOf("if (!d.isEmpty() && inView) planned = d.get(d.size() - 1).frame();") > starved,
                "in the view the player's own frame is the last of them");
    }

    private static final String AWAY_PASS = "private static void filmPass(Minecraft mc, CineCameraBE cam, boolean film, long tick, FilmClock.Plan plan)";

    @Test
    void theLensKeepsItsOwnFieldOfView() throws IOException {
        String view = read(SRC.resolve("cinecam/client/CineView.java"));
        String fov = body(view, view.indexOf("public static void fov(ViewportEvent.ComputeFov event)"));
        assertTrue(fov.contains("if (!drawingLens() || !event.usedConfiguredFov()) return;"), "only the lens's own frames");
        assertTrue(fov.contains("event.setFOV(FocusPull.zoomFov(lensFov(event.getCamera()), zoom));"),
                "the lens's own field of view, through its zoom");
        assertFalse(fov.contains("event.getFOV()"), "never the event's, which carries the player's sprint and flight");
        String lens = body(view, view.indexOf("static double lensFov(Camera camera)"));
        assertTrue(lens.contains("double fov = mc.options.fov().get();"), "from the configured field of view");
        assertFalse(lens.contains("getFieldOfViewModifier"), "with no player's FOV effect in it");
    }

    @Test
    void noFrameIsLostToTheMachinery() throws IOException {
        String rec = read(SRC.resolve("cinecam/client/CineRecorder.java"));
        String hand = body(rec, rec.indexOf("private void handOver(int i)"));
        assertFalse(hand.contains("queue.size()"), "a finished frame is never dropped for a full queue");
        assertTrue(hand.contains("queue.add(new Frame(i, data, pboSlot[i]));"), "it is handed over, always");
        assertTrue(rec.contains("final BlockingQueue<Item> queue = new LinkedBlockingQueue<>();"), "into a queue without a cap");
        assertTrue(statement(rec, rec.indexOf("this.pbos = ")).contains("BUFFER_BYTES / Math.max(1, frameBytes)"),
                "as many buffers as the memory set aside holds");
        assertTrue(rec.contains("that take held {} frames for the game, drew {} late, {} by extra passes"), "each take says what it held");
        String view = read(SRC.resolve("cinecam/client/CineView.java"));
        String frame = body(view, view.indexOf("public static void frame(RenderFrameEvent.Pre event)"));
        assertTrue(frame.contains("int max = frameSeconds < 1.0 / PLAYABLE_FPS ? MAX_DRAWS : 1;"), "extras only while playable");
        assertTrue(frame.contains("CineRecorder.draws(tick, own, next, inView, max)"), "the clock asked for that many");
        assertTrue(frame.contains("for (int i = 0; i + 1 < draws.size(); i++) filmPass(mc, cam, film, tick, draws.get(i));"),
                "in the view all but the last drawn as passes");
        int closing = view.indexOf("public static void closing(ClientTickEvent.Pre event)");
        assertTrue(closing > 0, "before each tick");
        assertTrue(view.substring(Math.max(0, closing - 80), closing).contains("@SubscribeEvent(priority = EventPriority.HIGHEST)"),
                "first in line, before anything in the tick can move the world");
        String close = body(view, closing);
        int plans = close.indexOf("CineRecorder.catchUp(ticks, MAX_CATCH_UP)");
        assertTrue(plans > 0 && close.indexOf("filmPass(mc, cam, film, ticks, p);", plans) > plans, "the closing tick's frames drawn");
        assertTrue(close.indexOf("if (catchUpNanos > CATCH_UP_BUDGET_NANOS) break;") > plans, "within the budget");
        assertTrue(body(view, view.indexOf("public static void frame(RenderFrameEvent.Pre event)")).contains("catchUpNanos = 0L;"),
                "a budget each frame");
    }

    @Test
    void everyTakeIsSavedAsAPlainMp4() throws IOException {
        String rec = read(SRC.resolve("cinecam/client/CineRecorder.java"));
        String encode = body(rec, rec.indexOf("private void encode(int bitrate)"));
        int close = encode.indexOf("mw.close();");
        int index = encode.indexOf("if (fragmented && frames > 0 && !closing) indexed = index();");
        assertTrue(close > 0 && index > close, "rewritten once the writer has finished the file");
        assertTrue(statement(encode, encode.indexOf("boolean fragmented = ")).contains("mw.fragmented()"), "a fragmented one");
        assertTrue(body(rec, rec.indexOf("private boolean index()")).contains("Mp4Faststart.rewriteInPlace(file, () -> closing);"),
                "in place, stopped by the game closing");
        int hook = rec.indexOf("hook = new Thread(() -> {");
        int flag = rec.indexOf("closing = true;", hook);
        assertTrue(hook > 0 && flag > hook && flag < rec.indexOf("queue.clear();", hook), "the hook says so first");
        String start = body(rec, rec.indexOf("static boolean start(Minecraft mc, CineCameraBE cam)"));
        int dirs = start.indexOf("Files.createDirectories(dir);");
        assertTrue(dirs > 0 && start.indexOf("Mp4Faststart.clearLeftovers(dir);") > dirs, "a killed rewrite's part file cleared");
    }

    @Test
    void theFilmHasItsSound() throws IOException {
        String rec = read(SRC.resolve("cinecam/client/CineRecorder.java"));
        assertTrue(statement(rec, rec.indexOf("boolean sound = ")).contains("cam.filmSound() && cam.filmSpeed() == FilmFormat.Speed.NORMAL"),
                "sound at Normal speed, when the camera's sound is on");
        assertTrue(statement(rec, rec.indexOf("this.sound = withSound")).contains("CineSound.open(mc,"), "the take opens its microphone");
        String finish = body(rec, rec.indexOf("void finish() {"));
        int sound = finish.indexOf("if (sound != null) sound.finish(");
        assertTrue(sound > 0 && sound < finish.indexOf("queue.add(new End(end));"), "the sound handed over before the file ends");
        String encode = body(rec, rec.indexOf("private void encode(int bitrate)"));
        assertTrue(encode.contains("mw = new MediaFoundationWriter(file, w, h, fps, bitrate, audioRate);"), "a sound track");
        assertTrue(encode.contains("mw.writeAudio(s.pcm(), s.frames(), s.at());"), "written on the encoder's thread");
        String mic = read(SRC.resolve("cinecam/client/CineSound.java"));
        String open = body(mic, mic.indexOf("static CineSound open("));
        int listen = open.indexOf("mc.getSoundManager().addListener(s);");
        assertTrue(listen > 0, "it hears the game");
        assertTrue(open.indexOf("s.joinPlaying();") > listen, "and joins the loops already going (a factory's hum starts once, before the take)");
        assertTrue(body(mic, mic.indexOf("private void joinPlaying()")).contains("if (s.isLooping() && s.getDelay() <= 0) mirror(s);"),
                "the game's own loops, not a one-shot already under way");
        String heard = body(mic, mic.indexOf("public void onPlaySound("));
        int muted = heard.indexOf("if (mc.options.getSoundSourceVolume(SoundSource.MASTER) <= 0.0F) return;");
        assertTrue(muted >= 0 && muted < heard.indexOf("mirror(instance);"), "nothing when the game plays nothing, not a click per sound");
        assertTrue(body(mic, mic.indexOf("void finish(long end)")).contains("mc.getSoundManager().removeListener(this);"),
                "and stops hearing it");
        assertTrue(mic.contains("if (closing || instance.isRelative() || mirrored.containsKey(instance)) return;"),
                "only world sounds, not the player's own interface and music, each once");
        String view = read(SRC.resolve("cinecam/client/CineView.java"));
        assertTrue(body(view, view.indexOf("public static void frame(RenderFrameEvent.Pre event)"))
                .contains("CineRecorder.soundFrame(eye.position(), viewDir, inView ? drawnAt : now);"), "the mix follows the film");
        assertTrue(body(view, view.indexOf("public static void counted(ClientTickEvent.Post event)")).contains("CineRecorder.soundTick();"),
                "and the game's sounds tick by tick");
    }

    @Test
    void autoZoomHoldsTheSubject() throws IOException {
        String view = read(SRC.resolve("cinecam/client/CineView.java"));
        String lens = body(view, view.indexOf("private static void lens("));
        assertTrue(lens.contains("zoomWant = AutoZoom.zoomFor(aim.at().distanceTo(eye.position()), aim.size(), framing, tanHalf, ZOOM_MAX);"),
                "the zoom that holds the framing");
        assertTrue(lens.contains("zoom = FocusPull.easeZoom(zoom, zoomWant, dt, autoHolding ? AutoZoom.TAU : FocusPull.TAU_ZOOM);"),
                "eased on game time, slower when auto");
        String scroll = body(view, view.indexOf("public static void scroll(InputEvent.MouseScrollingEvent event)"));
        int auto = scroll.indexOf("if (autoHolding) {");
        assertTrue(auto > 0 && scroll.indexOf("framing = AutoZoom.nudge(framing, up ? 1 : -1);", auto) > auto, "the wheel sets the framing");
        assertTrue(view.contains("Math.max(d.width(), d.height()) / 2.0"), "an entity framed by its standing size");
        assertTrue(view.contains("double own = CineShips.ownRadius(sub);"), "a ship by its own block box");
        String packet = read(SRC.resolve("cinecam/net/CineControlPacket.java"));
        assertTrue(packet.contains("case ZOOM -> cam.setAutoZoom(p.kind());"), "the packet sets it");
        assertTrue(packet.contains("case SOUND -> cam.setFilmSound(p.kind() != 0);"), "and the sound");
        String be = read(SRC.resolve("cinecam/CineCameraBE.java"));
        String save = body(be, be.indexOf("protected void saveAdditional("));
        assertTrue(save.contains("tag.putString(\"AutoZoom\", autoZoom.key());") && save.contains("f.putBoolean(\"Sound\", filmSound);"),
                "the camera keeps both");
        assertTrue(body(be, be.indexOf("public void setFilmSound(boolean on)")).contains("if (recording || on == filmSound) return;"),
                "the sound locks for a take");
    }

    @Test
    void eachFrameOfFilmIsDrawnAtItsMoment() throws IOException {
        String view = read(SRC.resolve("cinecam/client/CineView.java"));
        int at = view.indexOf("public static void frame(RenderFrameEvent.Pre event)");
        assertTrue(view.substring(Math.max(0, at - 80), at).contains("@SubscribeEvent(priority = EventPriority.HIGHEST)"),
                "first in line, so no handler reads the partial tick before it is moved");
        String frame = body(view, at);
        int shift = frame.indexOf("if (partial != own && !FilmTime.shift(mc, partial)) partial = own;");
        assertTrue(shift > 0 && frame.indexOf("lens(mc, cam, film, partial, drawnAt);", shift) > shift,
                "in the view the frame is moved, and the lens placed at the same moment");
        assertTrue(body(view, view.indexOf("public static void frameEnd(RenderFrameEvent.Post event)"))
                .contains("FilmTime.restore(Minecraft.getInstance());"), "and given back after the frame");
        String pass = body(view, view.indexOf(AWAY_PASS));
        int draw = pass.indexOf("mc.gameRenderer.renderLevel(mc.getTimer());");
        assertBefore(pass, "boolean moved = FilmTime.shift(mc, plan.partial());", draw, "the away pass drawn at its frame's moment");
        assertAfter(pass, "if (moved) FilmTime.restore(mc);", draw, "and the player's own frame given its own back");
        assertTrue(pass.indexOf("finally {") < pass.indexOf("if (moved) FilmTime.restore(mc);"), "given back whatever the pass threw");
        String counted = body(view, view.indexOf("public static void counted(ClientTickEvent.Post event)"));
        int paused = counted.indexOf("if (mc.isPaused()) return;");
        assertTrue(paused > 0 && paused < counted.indexOf("ticks++;"), "the film's game time stands still in a pause");
        String time = read(SRC.resolve("cinecam/client/FilmTime.java"));
        assertTrue(statement(time, time.indexOf("residual = field(")).contains("DeltaTracker.Timer.class, RESIDUAL"),
                "the partial tick every renderer reads");
    }

    @Test
    void theLensDrawsFromTheLens() throws IOException {
        String view = read(SRC.resolve("cinecam/client/CineView.java"));
        String frame = body(view, view.indexOf("public static void frame(RenderFrameEvent.Pre event)"));
        int hold = frame.indexOf("holdEyeHeight(mc);");
        assertTrue(hold > 0 && hold < frame.indexOf("if (camera == null) return;"),
                "held first, on every frame, even the one after the session ended");
        String held = body(view, view.indexOf("static void holdEyeHeight(Minecraft mc)"));
        assertTrue(held.contains("if (inView() && eye != null) FilmTime.eyeHeight(mc.gameRenderer.getMainCamera(), eye.getEyeHeight());"),
                "every frame through the lens at the lens's height");
        int due = held.indexOf("if (playerEyeDue) {");
        assertTrue(due >= 0 && held.indexOf("playerEyeDue = false;", due) > due
                        && held.indexOf("FilmTime.eyeHeight(mc.gameRenderer.getMainCamera(), mc.player.getEyeHeight());", due) > due,
                "the player's own height on the first frame after their view comes back, once");
        String give = body(view, view.indexOf("private static void releasePlayerView(Minecraft mc)"));
        int back = give.indexOf("mc.setCameraEntity(mc.player);");
        assertTrue(back > 0 && give.indexOf("playerEyeDue = true;", back) > back, "giving the view back asks for it");
        String pass = body(view, view.indexOf(AWAY_PASS));
        int draw = pass.indexOf("mc.gameRenderer.renderLevel(mc.getTimer());");
        assertBefore(pass, "float[] eyeWas = FilmTime.eyeHeight(shared, eye.getEyeHeight());", draw, "the away pass at the lens");
        assertAfter(pass, "FilmTime.eyeHeight(shared, eyeWas);", draw, "and the player's height back after it");
        String time = read(SRC.resolve("cinecam/client/FilmTime.java"));
        assertTrue(time.contains("eyeNow = field(Camera.class, EYE_HEIGHT);") && time.contains("eyeOld = field(Camera.class, EYE_HEIGHT_OLD);"),
                "the camera's eye height, now and as of the last tick");
        String set = body(time, time.indexOf("static float[] eyeHeight(Camera camera, float height)"));
        assertTrue(set.contains("eyeNow.setFloat(camera, height);") && set.contains("eyeOld.setFloat(camera, height);"),
                "both set, so it neither eases nor lags");
    }

    @Test
    void slowMotionGivesTheWorldBackOnEveryStop() throws IOException {
        String be = read(SRC.resolve("cinecam/CineCameraBE.java"));
        String rec = body(be, be.indexOf("public void setRecording(boolean on, @Nullable ServerPlayer by)"));
        assertTrue(rec.contains("if (on && by != null && filmSpeed.slow()) CineSlowMotion.begin(sl, worldPosition, by, filmSpeed.factor());"),
                "a slow motion take slows the world");
        assertTrue(rec.contains("else if (!on) CineSlowMotion.end(sl, worldPosition);"), "and every stop gives it back");
        assertTrue(rec.indexOf("if (on == recording) return;") < rec.indexOf("CineSlowMotion.begin("), "never begun twice");
        for (String hook : new String[]{"public void setRemoved()", "public void onChunkUnloaded()"}) {
            assertTrue(body(be, be.indexOf(hook)).contains("CineSlowMotion.end(sl, worldPosition);"), hook + " gives it back");
        }
        assertFalse(be.replace("recording = on;", "").replace("recording = tag.getBoolean(\"Recording\");", "")
                .contains("recording = "), "no stop that does not pass through setRecording");
        String slow = read(SRC.resolve("cinecam/CineSlowMotion.java"));
        assertTrue(slow.contains("@EventBusSubscriber(modid = CreateStats.MOD_ID)"), "on the game bus");
        assertTrue(body(slow, slow.indexOf("public static void stopped(ServerStoppedEvent event)")).contains("hold = null;"),
                "a world closed mid-take forgets it");
        String end = body(slow, slow.indexOf("static void end(ServerLevel level, BlockPos pos)"));
        int mine = end.indexOf("if (Math.abs(ticks.tickrate() - h.set()) > 1e-3f) return;");
        assertTrue(mine > 0 && end.indexOf("ticks.setTickRate(h.before());", mine) > mine,
                "given back only if still the rate it set: a /tick rate typed mid-take wins");
    }

    @Test
    void theCullingMixinsAreListed() throws IOException {
        JsonObject cfg = JsonParser.parseString(read(Path.of("src/main/resources/create_stats.mixins.json"))).getAsJsonObject();
        Set<String> client = new HashSet<>();
        for (JsonElement e : cfg.getAsJsonArray("client")) client.add(e.getAsString());
        assertTrue(client.contains("LevelRendererCullAccessor"), "the accessor, client side");
        assertTrue(client.contains("LevelRendererCullEventsMixin"), "the events mixin, client side");
        String events = read(SRC.resolve("mixin/LevelRendererCullEventsMixin.java"));
        for (String call : new String[]{"AwayCull.compiled(section);", "AwayCull.loaded(pos);", "AwayCull.invalidated();"}) {
            assertTrue(events.contains(call), call);
        }
    }

    @Test
    void theFormatReachesTheCameraAndHoldsForATake() throws IOException {
        String packet = read(SRC.resolve("cinecam/net/CineControlPacket.java"));
        assertTrue(packet.contains("case FORMAT -> cam.setFilmFormat(p.kind());"), "the packet sets the format");
        String be = read(SRC.resolve("cinecam/CineCameraBE.java"));
        String set = body(be, be.indexOf("public void setFilmFormat(int packed)"));
        assertTrue(set.contains("if (recording) return;"), "refused while a film rolls");
        assertTrue(set.indexOf("if (recording) return;") < set.indexOf("filmRate = rate;"), "refused before anything changes");
        String save = body(be, be.indexOf("protected void saveAdditional("));
        assertTrue(save.contains("tag.put(\"Film\", f);"), "the camera keeps it");
        String rec = read(SRC.resolve("cinecam/client/CineRecorder.java"));
        String start = body(rec, rec.indexOf("static boolean start(Minecraft mc, CineCameraBE cam)"));
        assertTrue(start.contains("FilmFormat.size(cam.filmResolution(), shape.aspect(), main.width, main.height)"), "its resolution");
        assertTrue(start.contains("int fps = cam.filmRate();"), "its rate");
        assertTrue(start.contains("FilmFormat.bitrate(size[0], size[1], fps, cam.filmQuality())"), "its quality");
        String view = read(SRC.resolve("cinecam/client/CineView.java"));
        assertTrue(view.contains("while (mc.options.keyInventory.consumeClick()) {"), "the menu has a key");
        assertTrue(view.contains("mc.setScreen(new CineMenuScreen(camera));"), "and the key opens it");
    }

    @Test
    void theMenuHasEveryWordItShows() throws IOException {
        JsonObject lang = JsonParser.parseString(read(ASSETS.resolve("lang/en_us.json"))).getAsJsonObject();
        Set<String> keys = new HashSet<>();
        String menu = read(SRC.resolve("cinecam/client/CineMenuScreen.java"));
        Matcher m = Pattern.compile("\"(create_stats\\.cine_camera\\.[a-z_.]+[a-z_])\"").matcher(menu);
        while (m.find()) keys.add(m.group(1));
        for (int r : com.createstats.cinecam.core.FilmFormat.RATES) keys.add("create_stats.cine_camera.menu.rate." + r);
        for (com.createstats.cinecam.core.FilmFormat.Resolution r : com.createstats.cinecam.core.FilmFormat.Resolution.values()) {
            keys.add("create_stats.cine_camera.menu.res." + r.key());
        }
        for (com.createstats.cinecam.core.FilmFormat.Quality q : com.createstats.cinecam.core.FilmFormat.Quality.values()) {
            keys.add("create_stats.cine_camera.menu.quality." + q.key());
            keys.add("create_stats.cine_camera.menu.quality." + q.key() + ".tip");
        }
        for (com.createstats.cinecam.core.FilmFormat.Shape sh : com.createstats.cinecam.core.FilmFormat.Shape.values()) {
            keys.add("create_stats.cine_camera.menu.shape." + sh.key());
            keys.add("create_stats.cine_camera.menu.shape." + sh.key() + ".tip");
        }
        for (com.createstats.cinecam.core.PhotoPrint.Target t : com.createstats.cinecam.core.PhotoPrint.Target.values()) {
            keys.add("create_stats.cine_camera.menu.print." + t.key());
            keys.add("create_stats.cine_camera.menu.print." + t.key() + ".tip");
        }
        for (com.createstats.cinecam.core.FilmFormat.Speed v : com.createstats.cinecam.core.FilmFormat.Speed.values()) {
            keys.add("create_stats.cine_camera.menu.speed." + v.key());
        }
        for (com.createstats.cinecam.core.AutoZoom.Framing f : com.createstats.cinecam.core.AutoZoom.Framing.values()) {
            keys.add("create_stats.cine_camera.menu.zoom." + f.key());
        }
        for (String file : new String[]{"cinecam/client/CineView.java", "cinecam/client/CineOverlay.java",
                "cinecam/client/CinePhotoTaker.java", "cinecam/CinePhotos.java", "cinecam/CineCameraBE.java",
                "cinecam/CineSlowMotion.java"}) {
            Matcher f = Pattern.compile("\"(create_stats\\.cine_camera\\.[a-z_.]+[a-z_])\"").matcher(read(SRC.resolve(file)));
            while (f.find()) keys.add(f.group(1));
        }
        String view = read(SRC.resolve("cinecam/client/CineView.java"));
        for (String k : new String[]{"create_stats.cine_camera.film.away", "create_stats.cine_camera.film.too_far",
                "create_stats.cine_camera.film.shaders", "create_stats.cine_camera.film.away_badge",
                "create_stats.cine_camera.film.remote_stop", "key.create_stats.cine_camera_record"}) {
            keys.add(k);
        }
        assertTrue(view.contains("\"create_stats.cine_camera.film.shaders\""), "the view names the shader line");
        for (String k : keys) {
            if (k.endsWith(".")) continue;
            assertTrue(lang.has(k), "en_us.json has " + k);
        }
        assertFalse(keys.isEmpty());
    }

    @Test
    void theRecordKeyIsARemote() throws IOException {
        String setup = read(SRC.resolve("cinecam/client/CineClientSetup.java"));
        assertTrue(body(setup, setup.indexOf("public static void keys(RegisterKeyMappingsEvent event)")).contains("event.register(RECORD);"),
                "the key is registered");
        String view = read(SRC.resolve("cinecam/client/CineView.java"));
        String tick = body(view, view.indexOf("public static void tick(ClientTickEvent.Pre event)"));
        int none = tick.indexOf("if (camera == null) {");
        assertTrue(none >= 0 && tick.indexOf("forgetRecordPresses();", none) > none, "presses without a camera are dropped");
        assertTrue(tick.contains("if (cam.cineMode() == CineMode.FILM) toggleFilm(mc, cam);"), "a record button in the view");
        String away = body(view, view.indexOf("private static void awayTick("));
        int remote = away.indexOf("if (CineClientSetup.RECORD.consumeClick()) {");
        assertTrue(remote >= 0 && away.indexOf("end();", remote) > remote, "the remote stops an away take");
        String click = body(view, view.indexOf("public static void click(InputEvent.InteractionKeyMappingTriggered event)"));
        assertTrue(click.contains("toggleFilm(mc, cam);"), "left-click and the key are one record button");
        String pass = body(view, view.indexOf(AWAY_PASS));
        int warm = pass.indexOf("if (awayCull != null && awayCull.warming()) return;");
        assertTrue(warm >= 0 && warm < pass.indexOf("CineRecorder.captureAway(mc, plan.frame());"),
                "no frame filmed before the culling finds the world");
    }

    @Test
    void theCameraIsPoweredLikeEveryLoad() throws IOException {
        String config = read(SRC.resolve("ModConfiguration.java"));
        assertTrue(config.contains("profiles.put(\"cineCamera\", new FePowerValues(builder, \"cineCamera\","), "the profile");
        assertTrue(statement(config, config.indexOf("profiles.put(\"cineCamera\"")).contains(", 216000, 1, 3, 0));"),
                "216,000 FE, 1 FE/t idle, 3 FE/t filming, no burst");
        assertTrue(read(SRC.resolve("power/FeProfiles.java")).contains("KEY_CINE_CAMERA = \"cineCamera\";"), "the key");
        String caps = read(SRC.resolve("power/FeCapabilities.java"));
        assertTrue(statement(caps, caps.indexOf("ModBlockEntities.CINE_CAMERA.get()")).contains("be.feConsumer()"),
                "the capability, on the consumer");
        String be = read(SRC.resolve("cinecam/CineCameraBE.java"));
        String tick = body(be, be.indexOf("public void serverTick()"));
        int pay = tick.indexOf("feConsumer.serviceTick(filming());");
        assertTrue(pay > 0 && pay < tick.indexOf("CineMode m = cineMode();"), "the upkeep first, at film running's draw");
        assertTrue(tick.contains("FePowerFx.handleTransition(sl, worldPosition, feConsumer);"), "and its charging and effects");
        assertTrue(tick.contains("if (!powered && recording) setRecording(false, null);"), "a film stops when the power goes");
        assertTrue(body(be, be.indexOf("public void lock(int kind, UUID id, String name)")).contains("if (!runs()) return;"),
                "no lock without power");
        assertTrue(body(be, be.indexOf("public void setRecording(boolean on, @Nullable ServerPlayer by)"))
                .contains("if (on && !runs()) on = false;"), "no film without power");
        String packet = read(SRC.resolve("cinecam/net/CineControlPacket.java"));
        String handle = body(packet, packet.indexOf("public static void handle(CineControlPacket p, IPayloadContext context)"));
        assertTrue(handle.contains("case PHOTO -> cam.setPhotoFormat(p.kind());"), "the print settings reach the camera");
        String view = read(SRC.resolve("cinecam/client/CineView.java"));
        String enter = body(view, view.indexOf("public static void enter(BlockPos pos)"));
        assertTrue(enter.contains("if (!cam.runs()) {"), "the view will not open without power");
        String vtick = body(view, view.indexOf("public static void tick(ClientTickEvent.Pre event)"));
        int lost = vtick.indexOf("if (!cam.runs()) {");
        assertTrue(lost > 0 && vtick.indexOf("end();", lost) > lost, "the view closes when the power goes");
        String away = body(view, view.indexOf("private static void awayTick("));
        int awayLost = away.indexOf("if (!cam.runs()) {");
        assertTrue(awayLost > 0 && away.indexOf("end();", awayLost) > awayLost, "and so does a take filming on its own");
    }

    @Test
    void theChargeGoesWithTheCamera() throws IOException {
        String be = read(SRC.resolve("cinecam/CineCameraBE.java"));
        String collect = body(be, be.indexOf("protected void collectImplicitComponents(DataComponentMap.Builder builder)"));
        assertTrue(collect.contains("int fe = feConsumer.getEnergyStored();")
                && collect.contains("if (fe > 0) builder.set(ModDataComponents.STORED_FE.get(), fe);"), "out of the camera");
        String apply = body(be, be.indexOf("protected void applyImplicitComponents(DataComponentInput input)"));
        int got = apply.indexOf("Integer fe = input.get(ModDataComponents.STORED_FE.get());");
        assertTrue(got > 0 && apply.indexOf("feConsumer.load(charge);", got) > got, "into the placed camera");
        JsonObject loot = JsonParser.parseString(read(DATA.resolve("loot_table/blocks/cine_camera.json"))).getAsJsonObject();
        boolean copies = false;
        for (JsonElement pool : loot.getAsJsonArray("pools")) {
            for (JsonElement entry : pool.getAsJsonObject().getAsJsonArray("entries")) {
                JsonObject e = entry.getAsJsonObject();
                if (!"create_stats:cine_camera".equals(e.get("name").getAsString()) || !e.has("functions")) continue;
                for (JsonElement f : e.getAsJsonArray("functions")) {
                    JsonObject fn = f.getAsJsonObject();
                    copies |= "minecraft:copy_components".equals(fn.get("function").getAsString())
                            && "block_entity".equals(fn.get("source").getAsString())
                            && fn.getAsJsonArray("include").toString().contains("\"create_stats:stored_fe\"");
                }
            }
        }
        assertTrue(copies, "the loot table copies the charge onto the dropped camera");
        String blocks = read(SRC.resolve("registry/ModBlocks.java"));
        assertTrue(statement(blocks, blocks.indexOf("CINE_CAMERA = ")).contains(".item(com.createstats.cinecam.CineCameraItem::new)"),
                "the camera's own item, with its charge bar");
        String caps = read(SRC.resolve("power/FeCapabilities.java"));
        String item = statement(caps, caps.indexOf("event.registerItem(Capabilities.EnergyStorage.ITEM,"));
        assertTrue(item.contains("new com.createstats.cinecam.CineCameraItem.Battery(stack)")
                && item.contains("ModBlocks.CINE_CAMERA.asItem()"), "an item charger can fill it");
    }

    @Test
    void theCordPlugsIntoEitherHalf() throws IOException {
        String block = read(SRC.resolve("cinecam/CineCameraBlock.java"));
        String use = body(block, block.indexOf("protected ItemInteractionResult useItemOn("));
        int cord = use.indexOf("if (!(stack.getItem() instanceof ApplianceCordItem cord)) {");
        int foot = use.indexOf("if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {");
        assertTrue(cord > 0 && foot > cord, "a cord, and only a cord, is the block's business here");
        assertTrue(statement(use, foot).contains("return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;"),
                "on the foot the cord's own use runs");
        assertTrue(use.indexOf("cord.useOn(new UseOnContext(player, hand, hit.withPosition(pos.below())));", foot) > foot,
                "on the head the cord is handed to the foot");
    }

    @Test
    void aPrintSpendsItsMapsAllOrNothing() throws IOException {
        String packet = read(SRC.resolve("cinecam/net/CinePhotoPacket.java"));
        String handle = body(packet, packet.indexOf("public static void handle(CinePhotoPacket p, IPayloadContext context)"));
        assertTrue(handle.contains("if (p.side() < PhotoPrint.MIN_SIDE || p.side() > PhotoPrint.MAX_SIDE) return;"), "the side");
        assertTrue(handle.contains("if (p.index() < 0 || p.index() >= PhotoPrint.mapsFor(p.side())) return;"), "the index");
        String photos = read(SRC.resolve("cinecam/CinePhotos.java"));
        String receive = body(photos, photos.indexOf("public static void receive("));
        int last = receive.indexOf("if (++j.received < j.maps.length) return;");
        assertTrue(last > 0 && receive.indexOf("print(level, player, cam, j);") > last, "printed only when every map is in");
        String accept = body(photos, photos.indexOf("private static boolean accept("));
        assertTrue(accept.contains("if (!cam.runs()) {"), "no print without power");
        assertFalse(accept.contains("shrink("), "nothing spent on accepting");
        assertTrue(body(photos, photos.indexOf("private static void print(")).contains("s.shrink(take);"), "spent when printed");
    }

    private static void assertBefore(String s, String what, int at, String why) {
        int i = s.indexOf(what);
        assertTrue(i >= 0 && i < at, why + ": " + what);
    }

    private static void assertAfter(String s, String what, int at, String why) {
        int i = s.indexOf(what, Math.max(0, at));
        assertTrue(i > at, why + ": " + what);
    }

    static String body(String s, int at) {
        if (at < 0) return "";
        int end = s.indexOf("\n    }\n", at);
        return end < 0 ? "" : s.substring(at, end);
    }

    @Test
    void theIrisReachesTheCamera() throws IOException {
        String packet = read(SRC.resolve("cinecam/net/CineControlPacket.java"));
        assertTrue(statement(packet, packet.indexOf("case IRIS ->")).contains("cam.setTStop(p.a())"));
        assertTrue(statement(packet, packet.indexOf("case RECORD ->")).contains("cam.setRecording(p.a() > 0.5f, player)"),
                "the camera hears that its film is rolling (its tally, its reels)");
    }

    @Test
    void theFilmIsTakenBeforeTheHudAndStoppedOnLeaving() throws IOException {
        String rec = read(SRC.resolve("cinecam/client/CineRecorder.java"));
        assertTrue(rec.contains("@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT)"));
        int at = rec.indexOf("public static void beforeGui(RenderGuiEvent.Pre event)");
        assertTrue(at > 0, "the recorder hooks the GUI's first event");
        assertTrue(rec.substring(Math.max(0, at - 80), at).contains("@SubscribeEvent(priority = EventPriority.HIGHEST)"),
                "first in line, before any mod draws on the frame");
        String view = read(SRC.resolve("cinecam/client/CineView.java"));
        assertTrue(body(view, view.indexOf("static void end()")).contains("stopFilm();"), "ending the session saves the film");
        assertTrue(body(view, view.indexOf("public static void loggingOut(")).contains("end();"),
                "leaving the world ends it while the GL context is alive");
        Pattern use = Pattern.compile("\\bnv12\\.(setSampler|safeGetUniform)\\(\"(\\w+)\"");
        JsonObject json = JsonParser.parseString(read(ASSETS.resolve("shaders/program/cine_nv12.json"))).getAsJsonObject();
        String fsh = read(ASSETS.resolve("shaders/program/cine_nv12.fsh"));
        Set<String> declared = new HashSet<>();
        for (String k : new String[]{"samplers", "uniforms"}) {
            for (JsonElement e : json.getAsJsonArray(k)) declared.add(e.getAsJsonObject().get("name").getAsString());
        }
        Matcher m = use.matcher(rec);
        int n = 0;
        while (m.find()) {
            n++;
            assertTrue(declared.contains(m.group(2)), "cine_nv12.json lists " + m.group(2));
            assertTrue(Pattern.compile("uniform\\s+\\w+\\s+" + m.group(2) + "\\s*;").matcher(fsh).find(), "cine_nv12.fsh declares " + m.group(2));
        }
        assertTrue(n >= 4, "the recorder sets the NV12 pass's inputs (" + n + ")");
        assertTrue(rec.contains("CreateStats.MOD_ID + \":cine_nv12\""), "and loads that program");
    }
}
