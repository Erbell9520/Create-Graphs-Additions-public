package com.createstats.statsmk3.paint;

import java.util.ArrayList;
import java.util.List;

import com.createstats.statsmk3.Mk3Settings;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;
import com.createstats.train.map.TrainGraphArt;
import com.createstats.train.smart.ClientRailNetwork;
import com.createstats.train.smart.RailSnapshot;
import com.createstats.train.smart.TrainIntel;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
final class TrainBoardFace {
    private TrainBoardFace() {
    }

    private static final float MIN_ROW = 11.0f;

    static void paint(PanelSurface s, Rect area, PanelModel model, Lod lod, String filter) {
        final RailSnapshot snap = ClientRailNetwork.get();
        final FaceType ft = FaceType.of(area);

        final List<RailSnapshot.Station> stations = TrainIntel.stationsMatching(snap, filter);
        if (stations.isEmpty()) {
            TrainFaceArt.noData(s, area, ft,
                    filter == null || filter.isBlank() ? "No station chosen" : "Station not found",
                    filter == null || filter.isBlank()
                            ? "Pick one in the display's Trains tab" : filter);
            return;
        }

        final boolean live = ClientRailNetwork.isLive();
        final String title = stations.size() == 1 ? stations.get(0).name : groupTitle(filter);

        if (lod.isGlyph()) {
            glyph(s, area, ft, snap, stations);
            return;
        }

        final TrainFaceArt.Head head = TrainFaceArt.header(s, area, ft, lod, title,
                live ? "" : "NO SIGNAL", Tokens.CAUTION, crumb(snap, stations, area, ft));

        Rect body = new Rect(area.x + ft.pad, head.bottom(), area.w - ft.pad * 2.0f,
                area.bottom() - ft.pad - head.bottom());
        if (body.h <= MIN_ROW * 0.6f || body.w < 24.0f) {
            return;
        }

        final int columns = columnsFor(s, body, ft);
        final boolean perStation = columns > 1 && stations.size() >= columns;
        final float rowIdeal = idealRow(ft, lod);

        if (perStation) {
            paintColumns(s, body, ft, lod, snap, model, stations, columns, rowIdeal);
            return;
        }

        final List<TrainIntel.BoardRow> merged = TrainIntel.board(snap, stations, 64);
        if (merged.isEmpty()) {
            TrainFaceArt.noData(s, body, ft, "No arrivals",
                    stations.size() == 1 ? "Nothing is booked into this platform"
                            : "Nothing is booked into these platforms");
            return;
        }

        final Rect rows = plan(s, body, ft, lod, snap, merged, columns, rowIdeal);
        paintList(s, rows, ft, lod, snap, model, merged, stations.size() > 1, columns, rowIdeal);
    }

    private static Rect plan(PanelSurface s, Rect body, FaceType ft, Lod lod, RailSnapshot snap,
                             List<TrainIntel.BoardRow> merged, int columns, float rowIdeal) {
        final int perColumn = (merged.size() + columns - 1) / columns;
        final TrainFaceArt.Rows fit = TrainFaceArt.rows(body.h, perColumn, MIN_ROW * ft.unit,
                rowIdeal);
        final float spare = body.h - fit.used();
        if (fit.count() < perColumn || spare < Text.lineHeight(ft.micro) * 2.5f
                || !lod.showsHeader()) {
            return body;
        }
        final Rect rows = new Rect(body.x, body.y, body.w, fit.used());
        final Rect rest = new Rect(body.x, rows.bottom() + ft.gap, body.w,
                body.bottom() - rows.bottom() - ft.gap);
        if (rest.h >= Text.lineHeight(ft.micro) * 4.0f) {
            TrainStatusFace.supplement(s, rest, ft, lod, snap);
        } else {
            TrainFaceArt.footer(s, rest, ft, snap, rest.h);
        }
        return rows;
    }

    private static int columnsFor(PanelSurface s, Rect body, FaceType ft) {
        final float need = s.textWidth("MMMMMMMMMM", ft.row) + s.textWidth("MMMMMMMM", ft.row)
                + s.textWidth("00m 00s", ft.meta) + ft.pad * 3.0f;

        return Math.max(1, Math.min(4, (int) Math.floor((body.w + ft.pad) / (need + ft.pad))));
    }

    private static void paintColumns(PanelSurface s, Rect body, FaceType ft, Lod lod,
                                     RailSnapshot snap, PanelModel model,
                                     List<RailSnapshot.Station> stations,
                                     int columns, float rowIdeal) {
        final float colW = (body.w - ft.pad * (columns - 1)) / columns;
        final List<List<RailSnapshot.Station>> split = balance(snap, stations, columns);

        for (int c = 0; c < columns; c++) {
            final Rect col = new Rect(body.x + c * (colW + ft.pad), body.y, colW, body.h);
            final List<RailSnapshot.Station> mine = split.get(c);
            if (mine.isEmpty()) {
                continue;
            }
            final String heading = headingFor(s, mine, col.w, ft);
            s.text(Text.truncate(s, Text.track(heading), col.w, ft.micro), col.x, col.y,
                    Tokens.INK_2, ft.micro);
            final float headH = Text.lineHeight(ft.micro) + ft.gap;
            Shapes.rect(s, col.x, col.y + headH - ft.gap * 0.5f, col.w, Math.max(1.0f, ft.hair),
                    Tokens.alpha(Tokens.LINE, 0.9f));

            final Rect rows = new Rect(col.x, col.y + headH, col.w, col.h - headH);
            final List<TrainIntel.BoardRow> board = TrainIntel.board(snap, mine, 32);
            if (board.isEmpty()) {
                s.textCenterV("no arrivals", rows.centerX(), rows.y + rowIdeal * 0.5f,
                        Tokens.INK_3, ft.micro);
                continue;
            }
            final TrainFaceArt.Rows plan = TrainFaceArt.rows(rows.h, board.size(),
                    MIN_ROW * ft.unit, rowIdeal);
            drawRows(s, rows, ft, lod, snap, model, board, plan, mine.size() > 1);
        }
    }

    private static List<List<RailSnapshot.Station>> balance(RailSnapshot snap,
                                                            List<RailSnapshot.Station> stations,
                                                            int columns) {
        final List<List<RailSnapshot.Station>> out = new ArrayList<>();
        final int[] load = new int[columns];
        for (int c = 0; c < columns; c++) {
            out.add(new ArrayList<>());
        }
        for (final RailSnapshot.Station st : stations) {
            int lightest = 0;
            for (int c = 1; c < columns; c++) {
                if (load[c] < load[lightest]) {
                    lightest = c;
                }
            }
            out.get(lightest).add(st);

            load[lightest] += Math.max(1, TrainIntel.board(snap, st, 32).size());
        }
        return out;
    }

    private static String headingFor(PanelSurface s, List<RailSnapshot.Station> mine, float width,
                                     FaceType ft) {
        if (mine.size() == 1) {
            return mine.get(0).name;
        }
        final StringBuilder sb = new StringBuilder();
        for (final RailSnapshot.Station st : mine) {
            final String candidate = sb.length() == 0 ? st.name : sb + " · " + st.name;
            if (s.textWidth(Text.track(candidate), ft.micro) > width) {
                return mine.size() + " platforms";
            }
            sb.setLength(0);
            sb.append(candidate);
        }
        return sb.toString();
    }

    private static void paintList(PanelSurface s, Rect body, FaceType ft, Lod lod,
                                  RailSnapshot snap, PanelModel model,
                                  List<TrainIntel.BoardRow> board,
                                  boolean showPlatform, int columns, float rowIdeal) {
        final TrainFaceArt.Rows single = TrainFaceArt.rows(body.h, board.size(), MIN_ROW * ft.unit,
                rowIdeal);
        if (columns < 2 || board.size() <= single.count()) {
            drawRows(s, body, ft, lod, snap, model, board, single, showPlatform);
            return;
        }
        final int cols = Math.min(columns, (board.size() + single.count() - 1) / single.count());
        final float colW = (body.w - ft.pad * (cols - 1)) / cols;
        final int per = (board.size() + cols - 1) / cols;
        for (int c = 0; c < cols; c++) {
            final List<TrainIntel.BoardRow> slice = board.subList(
                    Math.min(c * per, board.size()), Math.min((c + 1) * per, board.size()));
            if (slice.isEmpty()) {
                continue;
            }
            final Rect col = new Rect(body.x + c * (colW + ft.pad), body.y, colW, body.h);
            drawRows(s, col, ft, lod, snap, model, slice,
                    TrainFaceArt.rows(col.h, slice.size(), MIN_ROW * ft.unit, rowIdeal), showPlatform);
        }
    }

    private static void drawRows(PanelSurface s, Rect area, FaceType ft, Lod lod, RailSnapshot snap,
                                 PanelModel model, List<TrainIntel.BoardRow> board,
                                 TrainFaceArt.Rows plan, boolean showPlatform) {
        for (int i = 0; i < plan.count() && i < board.size(); i++) {
            final Rect row = new Rect(area.x, area.y + i * plan.height(), area.w,
                    plan.height() - ft.gap * 0.5f);
            if (row.bottom() > area.bottom() + 0.5f) {
                break;
            }
            drawRow(s, row, ft, lod, snap, model, board.get(i), showPlatform);
            if (i + 1 < plan.count() && i + 1 < board.size()) {
                Shapes.rect(s, row.x, row.bottom() + ft.gap * 0.25f, row.w, Math.max(1.0f, ft.hair),
                        Tokens.alpha(Tokens.LINE, 0.55f));
            }
        }
    }

    private static void drawRow(PanelSurface s, Rect r, FaceType ft, Lod lod, RailSnapshot snap,
                                PanelModel model, TrainIntel.BoardRow row, boolean showPlatform) {
        final RailSnapshot.TrainState train = row.trainId == null ? null : snap.train(row.trainId);
        final int status = statusColor(row);
        final int livery = TrainGraphArt.trainColor(train, row.trainId);

        if (row.present || !row.blockedBy.isEmpty() || row.imminent) {
            Shapes.roundedRect(s, r, 2.0f * ft.unit, Tokens.alpha(status, 0.10f));
        }

        final float stripe = Math.max(1.5f, 2.0f * ft.unit);
        Shapes.rect(s, r.x, r.y, stripe, r.h, Tokens.alpha(livery, row.present ? 1.0f : 0.75f));

        final float left = r.x + stripe + ft.gap;
        final float right = r.right() - ft.gap;
        final float inner = right - left;

        final String eta = row.eta();
        final float etaW = s.textWidth(eta, ft.row) + ft.gap * 2.0f;

        // two lines when it's cramped. "Stone Express" became "St"

        if (inner - etaW < s.textWidth("MMMMM", ft.row)) {
            final float half = r.h * 0.5f;
            s.textV(Text.truncate(s, row.train, inner, ft.meta), left, r.y + half * 0.75f,
                    Tokens.INK, ft.meta);
            s.textRightV(eta, right, r.bottom() - half * 0.4f, status, ft.meta);
            return;
        }

        final boolean twoLine = r.h >= Text.lineHeight(ft.row) + Text.lineHeight(ft.micro) + 1.0f
                && lod.showsLabels();
        final float lineY = twoLine ? r.y + ft.gap * 0.5f : r.centerY() - Text.lineHeight(ft.row) * 0.5f;

        s.textRight(eta, right, lineY, status, ft.row);

        float nameRoom = inner - etaW;
        final String dest = destinationOf(row);
        if (!dest.isEmpty()) {
            final float destW = Math.min(s.textWidth(dest, ft.row) + ft.pad, inner * 0.45f);
            if (nameRoom - destW > s.textWidth("MMMMM", ft.row)) {
                s.textRight(Text.truncate(s, dest, destW - ft.gap, ft.row), right - etaW, lineY,
                        row.terminates ? Tokens.INK_2 : Tokens.INK, ft.row);
                nameRoom -= destW;
            }
        }
        s.text(Text.truncate(s, row.train, nameRoom, ft.row), left, lineY, Tokens.INK, ft.row);

        if (!twoLine) {
            return;
        }
        final float subY = lineY + Text.lineHeight(ft.row) + ft.gap * 0.25f;
        drawSubLine(s, ft, model, row, train, left, right, subY, showPlatform, lod,
                threeLine(ft, r) && !row.callingAt.isEmpty());

        if (threeLine(ft, r) && !row.callingAt.isEmpty()
                && model.faceFlag(Mk3Settings.FACE_CALLING)) {
            final String calls = "calling " + TrainFaceArt.join(row.callingAt, 6);
            s.text(Text.truncate(s, calls, right - left, ft.nano), left,
                    subY + Text.lineHeight(ft.micro) + ft.gap * 0.25f,
                    Tokens.fade(Tokens.INK_3, lod.labelAlpha()), ft.nano);
        }
    }

    private static void drawSubLine(PanelSurface s, FaceType ft, PanelModel model,
                                    TrainIntel.BoardRow row, RailSnapshot.TrainState train,
                                    float left, float right, float y, boolean showPlatform,
                                    Lod lod, boolean hasCallingLine) {
        final float alpha = lod.labelAlpha();
        float tail = right;

        if (!row.manual && !Float.isNaN(row.lateBy)) {
            final String late = TrainFaceArt.lateShort(row.lateBy);
            if (!late.isEmpty() && tail - left > s.textWidth(late, ft.micro) * 3.0f) {
                s.textRight(late, tail, y, Tokens.fade(TrainFaceArt.latenessColor(row.lateBy),
                        alpha), ft.micro);
                tail -= s.textWidth(late, ft.micro) + ft.gap * 1.5f;
            }
        }

        if (train != null && row.speed > 0.05f && model.faceFlag(Mk3Settings.FACE_SPEED)) {
            final String fig = TrainIntel.speed(row.speed);
            final float figW = s.textWidth(fig, ft.micro);
            final float meterW = Math.max(6.0f, 10.0f * ft.unit);
            if (tail - left > (figW + meterW) * 2.5f) {
                s.textRight(fig, tail, y, Tokens.fade(Tokens.INK_2, alpha), ft.micro);
                tail -= figW + ft.gap;
                if (row.maxSpeed > 0.01f && ft.hasNano()) {
                    final float mh = Math.max(1.5f, 2.0f * ft.unit);
                    TrainFaceArt.meter(s, new Rect(tail - meterW, y + Text.lineHeight(ft.micro) * 0.5f
                                    - mh * 0.5f, meterW, mh),
                            row.speed / row.maxSpeed, Tokens.fade(Tokens.GOOD, alpha));
                    tail -= meterW + ft.gap;
                }
            }
        }

        final float room = tail - left;
        if (room < s.textWidth("MMMM", ft.micro)) {
            return;
        }
        final StringBuilder sb = new StringBuilder();
        int color = Tokens.INK_3;
        if (showPlatform && !row.station.isEmpty()) {
            sb.append(row.station);
        }
        final String why = !row.blockedBy.isEmpty() ? row.blockedBy
                : model.faceFlag(Mk3Settings.FACE_CAUSE) ? row.cause : "";
        if (!why.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(why);
            color = TrainFaceArt.color(!row.blockedBy.isEmpty()
                    ? TrainIntel.Severity.CRITICAL : row.causeSeverity);
        } else if (row.present) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append("at platform");
            color = Tokens.GOOD;
        } else if (row.imminent) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append("approaching");
            color = Tokens.GOOD;
        } else if (!row.callingAt.isEmpty() && model.faceFlag(Mk3Settings.FACE_CALLING)
                && !hasCallingLine) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append("calling ").append(TrainFaceArt.join(row.callingAt, 4));
        } else if (!row.scheduleTitle.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(row.scheduleTitle);
        }
        if (sb.length() > 0) {
            s.text(Text.truncate(s, sb.toString(), room, ft.micro), left, y,
                    Tokens.fade(color, alpha), ft.micro);
        }
    }

    private static String destinationOf(TrainIntel.BoardRow row) {
        if (row.terminates) {
            return "terminates";
        }
        return row.destination;
    }

    private static int statusColor(TrainIntel.BoardRow row) {
        if (!row.blockedBy.isEmpty()) {
            return Tokens.CRITICAL;
        }
        if (row.present) {
            return Tokens.GOOD;
        }
        if (row.causeSeverity == TrainIntel.Severity.WARN) {
            return Tokens.CAUTION;
        }
        return row.ticks >= 0 && row.ticks < 200 ? Tokens.GOOD : Tokens.INK;
    }

    private static float idealRow(FaceType ft, Lod lod) {
        return lod.showsLabels() ? ft.twoLineRow() + ft.gap : ft.oneLineRow() + ft.gap;
    }

    private static boolean threeLine(FaceType ft, Rect row) {
        return row.h >= Text.lineHeight(ft.row) + Text.lineHeight(ft.micro)
                + Text.lineHeight(ft.nano) + ft.gap * 2.0f;
    }

    private static String crumb(RailSnapshot snap, List<RailSnapshot.Station> stations, Rect area,
                                FaceType ft) {
        if (stations.size() < 2 || area.h < 70.0f) {
            return "";
        }
        int occupied = 0;
        for (final RailSnapshot.Station st : stations) {
            if (st.presentTrain != null) {
                occupied++;
            }
        }
        return stations.size() + " platforms · " + occupied + " occupied";
    }

    private static String groupTitle(String filter) {
        final String first = filter.split("[,;]")[0].trim();
        final String bare = first.endsWith("*") ? first.substring(0, first.length() - 1).trim()
                : first;
        final String name = bare.isEmpty() ? first : bare;
        return filter.contains(",") || filter.contains(";") ? name + " +" : name;
    }

    private static void glyph(PanelSurface s, Rect area, FaceType ft, RailSnapshot snap,
                              List<RailSnapshot.Station> stations) {
        final List<TrainIntel.BoardRow> board = TrainIntel.board(snap, stations, 1);
        if (board.isEmpty()) {
            Shapes.rect(s, area, Tokens.alpha(Tokens.INK_3, 0.10f));
            return;
        }
        final TrainIntel.BoardRow next = board.get(0);
        final int color = statusColor(next);
        Shapes.rect(s, area, Tokens.alpha(color, 0.14f));
        s.advanceLayer();
        final String eta = next.eta();
        final float scale = Text.fitScale(s, eta, area.w * 0.82f, area.h * 0.45f, ft.hero, ft.row);
        s.textCenter(eta, area.centerX(), area.y + area.h * 0.28f, color, scale);
    }
}
