package com.createstats.statsmk3.paint;

import com.createstats.statsmk3.Mk3Settings;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;
import com.createstats.train.map.ClientRailMap;
import com.createstats.train.map.RailHistory;
import com.createstats.train.map.RailMap;
import com.createstats.train.map.TrainGraphArt;
import com.createstats.train.map.TrainGraphView;
import com.createstats.train.smart.ClientRailNetwork;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class TrainFacePainter {
    private TrainFacePainter() {
    }

    public static boolean paint(PanelSurface s, Rect area, PanelModel model, Lod lod) {
        if (!model.faceMode.isTrain()) {
            return false;
        }
        Shapes.rect(s, area, model.style.ground);
        s.advanceLayer();
        switch (model.faceMode) {
            case TRAIN_BOARD -> TrainBoardFace.paint(s, area, model, lod, model.boardStation);
            case TRAIN_MAP -> TrainMapFace.paint(s, area, model, lod);
            case TRAIN_DIAGRAM -> paintDiagramBody(s, area, model, lod);
            case TRAIN_ROUTE -> TrainRouteFace.paint(s, area, model, lod);
            case TRAIN_FOCUS -> TrainFocusFace.paint(s, area, model, lod);
            case TRAIN_STATUS -> TrainStatusFace.paint(s, area, model, lod);
            default -> {
                return false;
            }
        }
        return true;
    }

    public static void paintBoard(PanelSurface s, Rect area, PanelModel model, Lod lod,
                                  String stationFilter) {
        Shapes.rect(s, area, model.style.ground);
        s.advanceLayer();
        TrainBoardFace.paint(s, area, model, lod, stationFilter);
    }

    public static void paintMap(PanelSurface s, Rect area, PanelModel model, Lod lod) {
        Shapes.rect(s, area, model.style.ground);
        s.advanceLayer();
        TrainMapFace.paint(s, area, model, lod);
    }

    public static void paintRoute(PanelSurface s, Rect area, PanelModel model, Lod lod) {
        Shapes.rect(s, area, model.style.ground);
        s.advanceLayer();
        TrainRouteFace.paint(s, area, model, lod);
    }

    public static void paintFocus(PanelSurface s, Rect area, PanelModel model, Lod lod) {
        Shapes.rect(s, area, model.style.ground);
        s.advanceLayer();
        TrainFocusFace.paint(s, area, model, lod);
    }

    public static void paintStatus(PanelSurface s, Rect area, PanelModel model, Lod lod) {
        Shapes.rect(s, area, model.style.ground);
        s.advanceLayer();
        TrainStatusFace.paint(s, area, model, lod);
    }

    public static void paintDiagram(PanelSurface s, Rect area, PanelModel model, Lod lod) {
        Shapes.rect(s, area, model.style.ground);
        s.advanceLayer();
        paintDiagramBody(s, area, model, lod);
    }

    private static void paintDiagramBody(PanelSurface s, Rect area, PanelModel model, Lod lod) {
        final FaceType ft = FaceType.of(area);
        final RailMap map = ClientRailMap.sampled();
        if (map.isEmpty()) {
            TrainFaceArt.noData(s, area, ft, "No railway", "Waiting for the network");
            return;
        }
        final int line = ClientRailMap.lineNamed(map, model.diagramLine);
        if (line < 0) {
            TrainFaceArt.noData(s, area, ft, "No scheduled service",
                    "A diagram plots one service against time");
            return;
        }
        final RailMap.Line service = map.lines().get(line);

        if (lod.isGlyph()) {
            paintDiagramGlyph(s, area, ft, map, line);
            return;
        }

        float top = area.y + ft.pad * 0.75f;
        if (lod.showsHeader() && Math.min(area.w, area.h) >= 96.0f) {
            final int running = service.activeTrains(ClientRailNetwork.get());
            final String status = running + " running";

            final float statusW = s.textWidth(status, ft.micro) + ft.pad;
            s.text(Text.truncate(s, Text.track(service.label(map)),
                            area.w - ft.pad * 2.0f - statusW, ft.micro), area.x + ft.pad, top,
                    Tokens.fade(Tokens.INK_2, lod.headerAlpha()), ft.micro);
            s.textRight(status, area.right() - ft.pad, top,
                    Tokens.fade(running > 0 ? Tokens.GOOD : Tokens.INK_3, lod.headerAlpha()),
                    ft.micro);
            top += Text.lineHeight(ft.micro) + ft.gap;
        }

        final Rect plot = new Rect(area.x + ft.pad * 0.75f, top,
                area.w - ft.pad * 1.5f, area.bottom() - ft.pad * 0.75f - top);
        if (plot.w < 90.0f || plot.h < 70.0f) {
            paintDiagramGlyph(s, area, ft, map, line);
            return;
        }

        final TrainGraphView view = FACE_VIEW.get();
        view.setLine(line);

        final boolean planned = model.faceFlag(Mk3Settings.FACE_PLANNED)
                && Math.min(plot.w, plot.h) >= 140.0f;
        if (view.showPlanned() != planned) {
            view.togglePlanned();
        }
        view.goLive();
        TrainGraphArt.draw(s, plot, map, view);
        if (Float.isNaN(RailHistory.latestTime())) {
            s.textCenterV("Recording", plot.centerX(), plot.centerY(),
                    Tokens.alpha(Tokens.INK_3, 0.7f), ft.micro);
        }
    }

    private static void paintDiagramGlyph(PanelSurface s, Rect area, FaceType ft, RailMap map,
                                          int line) {
        final RailMap.Line service = map.lines().get(line);
        final int running = service.activeTrains(ClientRailNetwork.get());
        final int color = running > 0 ? Tokens.GOOD : Tokens.INK_3;

        final RailHistory.Pulse pulse = RailHistory.pulse();
        final String caption = running + (running == 1 ? " running" : " running");
        final float captionH = Text.lineHeight(ft.micro) + ft.gap;

        final Rect trace = new Rect(area.x + ft.gap * 2.0f, area.y + ft.gap * 2.0f,
                area.w - ft.gap * 4.0f, area.h - captionH - ft.gap * 3.0f);
        final int n = pulse.size();
        if (n > 3 && !trace.isEmpty()) {
            final int step = Math.max(1, n / 40);
            final float[] xy = new float[((n + step - 1) / step) * 2];
            int peak = 1;
            for (int i = 0; i < n; i += step) {
                peak = Math.max(peak, pulse.moving(i));
            }
            int w = 0;
            for (int i = 0; i < n; i += step, w++) {
                xy[w * 2] = trace.x + trace.w * (i / (float) Math.max(1, n - 1));
                xy[w * 2 + 1] = trace.bottom() - trace.h * (pulse.moving(i) / (float) peak);
            }
            if (w > 1) {
                Shapes.areaFill(s, xy, w, trace.bottom(), Tokens.alpha(color, 0.25f),
                        Tokens.alpha(color, 0.02f));
                Shapes.polyline(s, xy, w, Math.max(1.0f, 1.4f * ft.unit),
                        Tokens.alpha(color, 0.9f));
            }
        }
        s.textCenter(caption, area.centerX(), area.bottom() - captionH,
                Tokens.alpha(color, 0.9f), ft.micro);
    }

    private static final ThreadLocal<TrainGraphView> FACE_VIEW =
            ThreadLocal.withInitial(TrainGraphView::new);
}
