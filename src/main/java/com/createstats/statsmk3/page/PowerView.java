package com.createstats.statsmk3.page;

import java.util.ArrayList;
import java.util.List;

import com.createstats.power.FePriority;
import com.createstats.statsmk3.page.PowerGraph.Circuit;
import com.createstats.statsmk3.page.PowerGraph.Distributor;
import com.createstats.statsmk3.page.PowerGraph.Grid;
import com.createstats.statsmk3.page.PowerGraph.Node;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Spring;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;
import com.createstats.statsmk3.view.StudioContext;
import com.createstats.statsmk3.view.StudioView;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class PowerView extends StudioView {
    static final String[] FAMILIES = PowerGraph.FAMILIES;

    private static final float FLOW_SCALE = 900.0f;

    private final Spring netRate = Spring.ui(0.0f);
    private final Spring storedPct = Spring.ui(0.0f);

    private Grid grid = PowerGraph.EMPTY;

    public PowerView(StudioContext ctx) {
        super(ctx);
    }

    @Override
    public String id() {
        return "power";
    }

    @Override
    public String label() {
        return "Power";
    }

    @Override
    public void drawIcon(PanelSurface c, float cx, float cy, int color) {
        Shapes.line(c, cx + 2.0f, cy - 6.5f, cx - 3.0f, cy + 0.5f, 1.6f, color);
        Shapes.line(c, cx - 3.0f, cy + 0.5f, cx + 1.0f, cy + 0.5f, 1.6f, color);
        Shapes.line(c, cx + 1.0f, cy + 0.5f, cx - 2.0f, cy + 6.5f, 1.6f, color);
    }

    @Override
    protected void build() {
    }

    @Override
    public void update(float dt) {
        super.update(dt);
        this.grid = PowerGraph.read(this.ctx.panel);
        this.netRate.set(this.grid.net()).step(dt);
        this.storedPct.set(this.grid.fill() * 100.0f).step(dt);
    }

    @Override
    protected void drawContent(PanelSurface c) {
        paint(c, this.area, this.grid, this.ctx.time,
                this.netRate.value(), this.storedPct.value());
    }

    static void paint(PanelSurface c, Rect area, Grid grid, float time,
                      float netSpring, float pctSpring) {
        c.text("Power network", area.x, area.y, Tokens.INK, Text.SUB);
        c.text(summary(grid), area.x, area.y + 20.0f, Tokens.INK_3, Text.META);

        final String fault = grid.fault();
        if (fault != null && area.w > 360.0f) {
            PageArt.chip(c, area.right() - c.textWidth(fault, Text.MICRO) - 24.0f, area.y + 2.0f,
                    fault, Tokens.CAUTION);
        }

        final Rect body = area.inset(0.0f, 46.0f, 0.0f, 0.0f);
        if (grid.isEmpty()) {
            drawEmpty(c, body.top(110.0f));
            return;
        }

        final float sankeyH = Math.max(150.0f, Math.min(body.h * (grid.isManaged() ? 0.54f : 0.58f),
                grid.isManaged() ? 320.0f : 290.0f));
        drawSchematic(c, body.top(sankeyH), grid, time);

        final Rect rest = body.below(sankeyH + Tokens.S3);
        if (rest.h < 46.0f) {
            return;
        }
        drawFooter(c, rest, grid, netSpring, time);
    }

    private static String summary(Grid grid) {
        if (grid.isEmpty()) {
            return "No energy machine linked";
        }
        final StringBuilder sb = new StringBuilder();
        sb.append(grid.sources().size()).append(" generating");
        if (!grid.storage().isEmpty() || grid.capacity() > 0.0f) {
            sb.append(", ").append(Math.max(1, grid.storage().size())).append(" storing");
        }
        for (final Distributor d : grid.distributors()) {
            sb.append(", ").append(d.panel() ? "panel " : "controller ")
                    .append(d.servedDevices()).append('/').append(d.devices());
        }
        if (!grid.isManaged()) {
            sb.append(", ").append(grid.loads().size()).append(" drawing");
        }
        if (grid.gridNodes() > 0) {
            sb.append(" · grid of ").append(grid.gridNodes());
        }
        return sb.toString();
    }

    private static void drawFooter(PanelSurface c, Rect rest, Grid grid, float net, float time) {
        if (!grid.isManaged()) {
            final float bankW = Math.min(rest.w * 0.36f, 220.0f);
            drawBank(c, rest.left(bankW), grid, net, time);
            drawTrend(c, rest.afterLeft(bankW + Tokens.S3), grid);
            return;
        }
        final float bankW = Math.min(rest.w * 0.32f, 200.0f);
        drawBank(c, rest.left(bankW), grid, net, time);
        final Rect right = rest.afterLeft(bankW + Tokens.S3);

        if (right.w > 460.0f) {
            final float trendW = Math.min(right.w * 0.34f, 200.0f);
            drawCircuits(c, right.inset(0.0f, 0.0f, trendW + Tokens.S3, 0.0f), grid, time);
            drawTrend(c, right.afterLeft(right.w - trendW), grid);
            return;
        }
        drawCircuits(c, right, grid, time);
    }

    // sankey, not a schematic. you know what's wired, you wired it. you can't see the proportions
    private static void drawSchematic(PanelSurface c, Rect a, Grid grid, float time) {
        if (a.isEmpty()) {
            return;
        }
        Shapes.card(c, a, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        final Rect inner = a.inset(Tokens.S3, Tokens.S3, Tokens.S3, Tokens.S2);

        final float storageNet = grid.bandNet();
        final float toBank = Math.max(0.0f, storageNet);
        final float fromBank = Math.max(0.0f, -storageNet);
        final float rightTotal = grid.consumed() + toBank;
        final float metered = grid.generated() + fromBank;

        final float unmetered = rightTotal - metered > Math.max(15.0f, rightTotal * 0.04f)
                ? rightTotal - metered : 0.0f;
        final float leftTotal = metered + unmetered;
        final float peak = Math.max(1.0e-3f, Math.max(leftTotal, rightTotal));

        final boolean staged = grid.isManaged() && inner.w > 330.0f;
        final float nodeW = 9.0f;
        final float boxW = staged ? Math.min(inner.w * 0.21f, 128.0f) : 0.0f;
        final float labelW = Math.min(inner.w * (staged ? 0.19f : 0.26f), staged ? 96.0f : 128.0f);
        final float leftX = inner.x + labelW;
        final float rightX = inner.right() - labelW;
        final float boxX = (leftX + nodeW + rightX - nodeW - boxW) * 0.5f;

        final float bandTop = inner.y + 16.0f;
        final float bandH = Math.max(30.0f, inner.h - 34.0f);

        final List<Stream> sources = new ArrayList<>();
        final List<Stream> sinks = new ArrayList<>();
        final List<Stream> middle = new ArrayList<>();

        final List<int[]> spans = new ArrayList<>();

        final int leftCount = Math.max(1, grid.sources().size()
                + (fromBank > 0.01f ? 1 : 0) + (unmetered > 0.01f ? 1 : 0));
        final int rightCount = Math.max(1, (staged ? grid.allCircuits().size() : grid.loads().size())
                + (toBank > 0.01f ? 1 : 0) + 1);
        final float gap = 4.0f;
        final float usable = Math.max(12.0f, bandH - gap * Math.max(leftCount, rightCount));
        final float scale = usable / peak;

        c.text(Text.track("Generation"), inner.x, inner.y, Tokens.INK_3, Text.MICRO);
        if (staged) {
            c.textCenter(Text.track("Distribution"), boxX + boxW * 0.5f, inner.y,
                    Tokens.INK_3, Text.MICRO);
        }
        c.textRight(Text.track("Demand"), inner.right(), inner.y, Tokens.INK_3, Text.MICRO);

        float y = bandTop;
        for (final Node node : grid.sources()) {
            final float h = Math.max(node.rate() > 0.005f ? 1.5f : 0.0f, node.rate() * scale);
            sources.add(new Stream(node.label(), node.rate(), y, y + h,
                    node.role() == PowerGraph.Role.STORAGE ? Tokens.CAUTION : Tokens.GOOD));
            y += h + gap;
        }
        if (fromBank > 0.01f) {
            final float h = Math.max(1.5f, fromBank * scale);
            sources.add(new Stream("Bank", fromBank, y, y + h, Tokens.CAUTION));
            y += h + gap;
        }
        if (unmetered > 0.01f) {
            final float h = Math.max(1.5f, unmetered * scale);
            sources.add(new Stream("Unmetered", unmetered, y, y + h, Tokens.INK_3));
        }

        float ry = bandTop;
        if (staged) {
            float my = bandTop;
            for (final Distributor d : grid.distributors()) {
                final int from = sinks.size();
                for (final Circuit circuit : d.circuits()) {
                    final float h = Math.max(circuit.served() > 0.005f ? 1.5f : 0.0f,
                            circuit.served() * scale);
                    sinks.add(new Stream(circuit.name(), circuit.served(), ry, ry + h,
                            circuitColor(circuit)));
                    ry += h + gap;
                }
                final float mh = Math.max(18.0f, d.served() * scale);
                middle.add(new Stream(d.label(), d.served(), my, my + mh,
                        d.panel() ? Tokens.SERIES[4] : Tokens.SERIES[0]));
                spans.add(new int[] { from, sinks.size() });
                my += mh + gap;
            }

            final int from = sinks.size();
            ry = appendTail(sinks, grid, toBank, leftTotal, rightTotal, scale, ry, gap);
            if (sinks.size() > from) {
                float direct = 0.0f;
                for (int i = from; i < sinks.size(); i++) {
                    direct += sinks.get(i).rate();
                }
                final float mh = Math.max(12.0f, direct * scale);
                middle.add(new Stream("Direct", direct, my, my + mh, Tokens.INK_3));
                spans.add(new int[] { from, sinks.size() });
            }
        } else {
            if (toBank > 0.01f) {
                final float h = Math.max(1.5f, toBank * scale);
                sinks.add(new Stream("Bank", toBank, ry, ry + h, Tokens.GOOD));
                ry += h + gap;
            }
            for (final Node node : grid.loads()) {
                final float h = Math.max(node.rate() > 0.005f ? 1.5f : 0.0f, node.rate() * scale);
                sinks.add(new Stream(node.label(), node.rate(), ry, ry + h, Tokens.SERIES[0]));
                ry += h + gap;
            }
            appendUnaccounted(sinks, leftTotal, rightTotal, scale, ry);
        }

        if (staged && !middle.isEmpty()) {
            weave(c, sources, middle, leftX + nodeW, boxX, scale, time);
            for (int i = 0; i < middle.size(); i++) {
                final int[] span = spans.get(i);
                weave(c, List.of(middle.get(i)), sinks.subList(span[0], span[1]),
                        boxX + boxW, rightX - nodeW, scale, time);
            }
        } else {
            weave(c, sources, sinks, leftX + nodeW, rightX - nodeW, scale, time);
        }

        for (final Stream s : sources) {
            Shapes.roundedRect(c, new Rect(leftX, s.top(), nodeW, s.height()), 2.0f, s.color());
            drawStreamLabel(c, inner.x, leftX - 6.0f, s, false);
        }
        for (final Stream s : sinks) {
            Shapes.roundedRect(c, new Rect(rightX - nodeW, s.top(), nodeW, s.height()), 2.0f,
                    s.color());
            drawStreamLabel(c, rightX + 6.0f, inner.right(), s, true);
        }
        if (staged) {
            for (int i = 0; i < middle.size(); i++) {
                drawSwitchboard(c, new Rect(boxX, middle.get(i).top(), boxW, middle.get(i).height()),
                        middle.get(i), i < grid.distributors().size() ? grid.distributors().get(i) : null);
            }
        }

        if (sources.isEmpty()) {
            c.textCenterV("Nothing is generating", inner.centerX(), inner.centerY(),
                    Tokens.INK_3, Text.META);
        }
    }

    private static float appendTail(List<Stream> sinks, Grid grid, float toBank,
                                    float leftTotal, float rightTotal,
                                    float scale, float ry, float gap) {
        float cursor = ry;
        if (toBank > 0.01f) {
            final float h = Math.max(1.5f, toBank * scale);
            sinks.add(new Stream("Bank", toBank, cursor, cursor + h, Tokens.GOOD));
            cursor += h + gap;
        }
        return appendUnaccounted(sinks, leftTotal, rightTotal, scale, cursor);
    }

    private static float appendUnaccounted(List<Stream> sinks, float leftTotal, float rightTotal,
                                           float scale, float ry) {
        final float unaccounted = leftTotal - rightTotal;
        if (unaccounted <= Math.max(15.0f, leftTotal * 0.04f)) {
            return ry;
        }
        final float h = Math.max(1.5f, unaccounted * scale);
        sinks.add(new Stream("Unaccounted", unaccounted, ry, ry + h, Tokens.CRITICAL));
        return ry + h + 4.0f;
    }

    private static void weave(PanelSurface c, List<Stream> from, List<Stream> to,
                              float x0, float x1, float scale, float time) {
        int sinkIndex = 0;
        float sinkUsed = 0.0f;
        for (final Stream source : from) {
            float remaining = source.rate();
            float sourceCursor = source.top();
            while (remaining > 1.0e-3f && sinkIndex < to.size()) {
                final Stream sink = to.get(sinkIndex);
                final float sinkLeft = sink.rate() - sinkUsed;
                if (sinkLeft <= 1.0e-3f) {
                    sinkIndex++;
                    sinkUsed = 0.0f;
                    continue;
                }
                final float amount = Math.min(remaining, sinkLeft);
                final float sh = amount * scale;
                final float sourceTop = sourceCursor;
                final float sinkTop = sink.top() + sinkUsed * scale;
                PageArt.ribbon(c, x0, sourceTop, sourceTop + sh,
                        x1, sinkTop, sinkTop + sh,
                        Tokens.alpha(source.color(), 0.52f), Tokens.alpha(sink.color(), 0.52f));
                PageArt.ribbonFlow(c, x0, sourceTop + sh * 0.5f, x1, sinkTop + sh * 0.5f,
                        Math.max(1.2f, Math.min(2.6f, sh * 0.28f)),
                        sink.color(), amount, FLOW_SCALE, time);
                sourceCursor += sh;
                sinkUsed += amount;
                remaining -= amount;
            }
        }
    }

    private static void drawSwitchboard(PanelSurface c, Rect box, Stream s, Distributor d) {
        final int accent = s.color();
        Shapes.roundedRect(c, box, Tokens.R_CHIP, Tokens.alpha(accent, 0.16f));
        Shapes.roundedRectOutline(c, box, Tokens.R_CHIP, 1.0f, Tokens.alpha(accent, 0.55f));
        if (box.h < 20.0f) {
            return;
        }
        final Rect inner = box.inset(6.0f, 4.0f, 6.0f, 4.0f);
        c.text(Text.truncate(c, s.name(), inner.w, Text.MICRO), inner.x, inner.y,
                Tokens.INK, Text.MICRO);
        if (box.h >= 32.0f) {
            c.text(Text.value(s.rate()) + " FE/t", inner.x, inner.y + 11.0f, accent, Text.MICRO);
        }
        if (d == null || box.h < 46.0f) {
            return;
        }

        if (!d.policy().isEmpty()) {
            c.text(Text.truncate(c, d.policy(), inner.w, Text.MICRO), inner.x, inner.y + 22.0f,
                    Tokens.INK_3, Text.MICRO);
        }
        if (d.devices() > 0 && box.h >= 58.0f) {
            final int color = d.shedDevices() > 0 ? Tokens.CAUTION : Tokens.INK_2;
            c.text(d.servedDevices() + " of " + d.devices() + " running",
                    inner.x, inner.y + 33.0f, color, Text.MICRO);
        }
    }

    private static int circuitColor(Circuit circuit) {
        return switch (circuit.state()) {
            case OK -> Tokens.SERIES[0];
            case IDLE -> Tokens.INK_3;
            case RATIONED, PARTIAL -> Tokens.CAUTION;
            case FLOOR, RECHARGING -> Tokens.SERIES[2];
            case SHED, OVER_CAPACITY, OPEN, TRIPPED -> Tokens.CRITICAL;
        };
    }

    private record Stream(String name, float rate, float top, float bottom, int color) {
        float height() {
            return Math.max(1.5f, this.bottom - this.top);
        }
    }

    private static void drawStreamLabel(PanelSurface c, float left, float right, Stream s,
                                        boolean rightAligned) {
        if (s.height() < 11.0f) {
            return;
        }
        final float width = right - left;
        final float mid = (s.top() + s.bottom()) * 0.5f;
        final String rate = Text.value(s.rate()) + " FE/t";
        final String name = Text.truncate(c, s.name(), width, Text.MICRO);
        if (s.height() >= 24.0f) {
            if (rightAligned) {
                c.text(name, left, mid - 10.0f, Tokens.INK_2, Text.MICRO);
                c.text(rate, left, mid + 1.0f, s.color(), Text.MICRO);
            } else {
                c.textRight(name, right, mid - 10.0f, Tokens.INK_2, Text.MICRO);
                c.textRight(rate, right, mid + 1.0f, s.color(), Text.MICRO);
            }
            return;
        }
        if (rightAligned) {
            c.textV(name, left, mid, Tokens.INK_2, Text.MICRO);
        } else {
            c.textRightV(name, right, mid, Tokens.INK_2, Text.MICRO);
        }
    }

    private static void drawCircuits(PanelSurface c, Rect card, Grid grid, float time) {
        if (card.isEmpty() || card.h < 40.0f) {
            return;
        }
        Shapes.card(c, card, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        final Rect body = card.inset(Tokens.S3);
        c.text(Text.track("Circuits"), body.x, body.y, Tokens.INK_3, Text.MICRO);

        final List<Circuit> circuits = grid.allCircuits();
        if (circuits.isEmpty()) {
            c.textCenterV("Nothing wired to any output", body.centerX(), body.centerY(),
                    Tokens.INK_3, Text.META);
            return;
        }

        Rect rows = body.below(15.0f);
        final Distributor first = grid.distributors().get(0);
        if (first.tierTotal().length > 0 && rows.h >= 62.0f) {
            drawTiers(c, rows.top(20.0f), first);
            rows = rows.below(26.0f);
        }

        float peak = 1.0f;
        for (final Circuit circuit : circuits) {
            peak = Math.max(peak, Math.max(circuit.ceiling(),
                    Math.max(circuit.demand(), circuit.served())));
        }

        final float rowH = Math.max(13.0f, Math.min(22.0f, rows.h / Math.max(1, circuits.size())));
        final int visible = Math.max(1, Math.min(circuits.size(), (int) (rows.h / rowH)));
        for (int i = 0; i < visible; i++) {
            drawCircuitRow(c, new Rect(rows.x, rows.y + i * rowH, rows.w, rowH - 2.0f),
                    circuits.get(i), peak, time);
        }
        if (visible < circuits.size()) {
            c.textRight((circuits.size() - visible) + " more", body.right(), body.bottom() - 9.0f,
                    Tokens.INK_3, Text.MICRO);
        }
    }

    private static void drawCircuitRow(PanelSurface c, Rect row, Circuit circuit, float peak,
                                       float time) {
        final int color = circuitColor(circuit);
        final float nameW = Math.min(row.w * 0.28f, 76.0f);
        final float stateW = Math.min(row.w * 0.22f, 74.0f);
        final Rect track = new Rect(row.x + nameW, row.centerY() - 4.0f,
                Math.max(8.0f, row.w - nameW - stateW - Tokens.S2), 8.0f);

        c.textRightV(Text.truncate(c, circuit.name(), nameW - 6.0f, Text.MICRO),
                row.x + nameW - 6.0f, row.centerY(), Tokens.INK_2, Text.MICRO);

        Shapes.roundedRect(c, track, 3.0f, Tokens.alpha(Tokens.LINE_HI, 0.55f));
        final float servedW = track.w * Math.min(1.0f, circuit.served() / peak);
        if (servedW > 0.5f) {
            Shapes.roundedRect(c, new Rect(track.x, track.y, servedW, track.h), 3.0f, color);
        }

        final float wantW = track.w * Math.min(1.0f, circuit.demand() / peak);
        if (wantW > servedW + 1.0f) {
            final Rect refused = new Rect(track.x + servedW, track.y, wantW - servedW, track.h);
            Shapes.roundedRectOutline(c, refused, 3.0f, 1.0f, Tokens.alpha(Tokens.CRITICAL, 0.75f));
        }

        if (circuit.ceiling() > 0.0f) {
            final float tick = track.x + track.w * Math.min(1.0f, circuit.ceiling() / peak);
            Shapes.rect(c, tick - 0.5f, track.y - 2.0f, 1.0f, track.h + 4.0f,
                    Tokens.alpha(Tokens.INK_2, 0.8f));
        }

        if (circuit.floorPct() > 0) {
            final float tick = track.x + track.w * Math.min(1.0f, circuit.floorPct() / 100.0f);
            Shapes.rect(c, tick - 0.5f, track.y, 1.0f, track.h, Tokens.alpha(Tokens.SERIES[2], 0.7f));
        }

        final String state = circuit.state() == Circuit.State.OK
                ? Text.value(circuit.served()) + " FE/t" : circuit.state().label();
        c.textV(Text.truncate(c, state, stateW - 4.0f, Text.MICRO),
                track.right() + Tokens.S2, row.centerY(),
                circuit.state().isFault() ? color : Tokens.INK_3, Text.MICRO);

        if (circuit.served() > 0.5f && servedW > 6.0f) {
            final float phase = (time * 0.55f + circuit.name().hashCode() * 0.013f) % 1.0f;
            Shapes.rect(c, track.x + servedW * phase, track.y, 1.5f, track.h,
                    Tokens.alpha(0xFFFFFFFF, 0.28f));
        }
    }

    private static void drawTiers(PanelSurface c, Rect r, Distributor d) {
        final FePriority[] tiers = FePriority.values();
        final float colW = r.w / tiers.length;
        for (int i = 0; i < tiers.length && i < d.tierTotal().length; i++) {
            final int total = d.tierTotal()[i];
            final int served = i < d.tierServed().length ? d.tierServed()[i] : 0;
            final float x = r.x + colW * i;
            final int color = total == 0 ? Tokens.INK_3
                    : served >= total ? Tokens.GOOD : served > 0 ? Tokens.CAUTION : Tokens.CRITICAL;
            c.text(tiers[i].getDisplayName().substring(0, 1), x, r.y, Tokens.INK_3, Text.MICRO);
            c.text(total == 0 ? "-" : served + "/" + total, x + 9.0f, r.y, color, Text.MICRO);

            Shapes.rect(c, x, r.y + 12.0f, colW - 6.0f, 1.0f, Tokens.alpha(Tokens.LINE_HI, 0.7f));
            if (total > 0) {
                Shapes.rect(c, x, r.y + 11.0f, (colW - 6.0f) * (served / (float) total), 2.0f, color);
            }
        }
    }

    private static void drawBank(PanelSurface c, Rect card, Grid grid, float net, float time) {
        if (card.isEmpty()) {
            return;
        }
        Shapes.card(c, card, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        final Rect body = card.inset(Tokens.S3);
        c.text(Text.track(grid.isManaged() ? "Reserve" : "Storage"), body.x, body.y,
                Tokens.INK_3, Text.MICRO);
        if (!grid.hasStorage()) {
            c.textCenterV("No buffer", body.centerX(), body.centerY() - 4.0f, Tokens.INK_2, Text.META);
            c.textCenterV("Generation feeds load directly", body.centerX(), body.centerY() + 10.0f,
                    Tokens.INK_3, Text.MICRO);
            return;
        }

        final int columns = grid.cellColumns() > 0 ? grid.cellColumns() : 2;
        final int rows = grid.cellRows() > 0 ? grid.cellRows() : 4;
        final float fill = grid.fill();
        final int color = fill < 0.12f ? Tokens.CRITICAL : fill < 0.30f ? Tokens.CAUTION : Tokens.GOOD;

        final float cellW = Math.min(body.w * 0.34f, 62.0f);
        final Rect cellArea = new Rect(body.x, body.y + 16.0f, cellW,
                Math.max(20.0f, body.h - 22.0f));
        PageArt.cells(c, cellArea, columns, rows, fill, color, net > 1.0f, time);

        final float textX = cellArea.right() + Tokens.S3;
        final float textW = Math.max(10.0f, body.right() - textX);
        c.text(Text.value(fill * 100.0f) + "%", textX, cellArea.y + 2.0f, Tokens.INK, Text.TITLE);

        final String direction = net > 1.0f ? "charging" : net < -1.0f ? "draining" : "holding";
        final int netColor = net > 1.0f ? Tokens.GOOD : net < -1.0f ? Tokens.CAUTION : Tokens.INK_3;
        c.text(Text.delta(net, 0) + " FE/t", textX, cellArea.y + 26.0f, netColor, Text.META);
        c.text(direction, textX, cellArea.y + 38.0f, Tokens.INK_3, Text.MICRO);

        if (grid.capacity() > 0.0f && Math.abs(net) > 1.0f) {
            final float energy = net < 0.0f ? grid.stored() : grid.capacity() - grid.stored();
            final String d = PageArt.duration(energy / (Math.abs(net) * 20.0f), 36_000.0f);
            if (d != null) {
                c.text(Text.truncate(c, (net < 0.0f ? "empty in " : "full in ") + d,
                                textW, Text.MICRO),
                        textX, cellArea.y + 52.0f, net < 0.0f ? Tokens.CAUTION : Tokens.INK_2,
                        Text.MICRO);
            }
        }
        c.text(Text.truncate(c, Text.value(grid.stored()) + " of " + Text.value(grid.capacity()),
                        textW, Text.MICRO),
                textX, cellArea.bottom() - 10.0f, Tokens.INK_3, Text.MICRO);
    }

    private static void drawTrend(PanelSurface c, Rect a, Grid grid) {
        if (a.isEmpty() || a.h < 50.0f) {
            return;
        }
        Shapes.card(c, a, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        final Rect inner = a.inset(Tokens.S3);
        c.text(Text.track("Stored energy"), inner.x, inner.y, Tokens.INK_3, Text.MICRO);

        final Rect plot = inner.below(16.0f);
        final float[] data = grid.storedHistory();
        if (data == null || data.length < 2 || plot.isEmpty()) {
            c.textCenterV("Waiting for history", plot.centerX(), plot.centerY(),
                    Tokens.INK_3, Text.META);
            return;
        }

        final float ceiling = Math.max(grid.capacity(), 1.0f);
        final int n = data.length;
        final float[] xy = new float[n * 2];
        for (int i = 0; i < n; i++) {
            xy[i * 2] = plot.x + plot.w * (n == 1 ? 0.0f : i / (float) (n - 1));
            xy[i * 2 + 1] = plot.bottom()
                    - plot.h * Math.max(0.0f, Math.min(1.0f, data[i] / ceiling));
        }
        Shapes.areaFill(c, xy, n, plot.bottom(),
                Tokens.alpha(Tokens.GOOD, 0.22f), Tokens.alpha(Tokens.GOOD, 0.0f));
        Shapes.polyline(c, xy, n, Tokens.DATA_STROKE, Tokens.GOOD);

        Shapes.rect(c, plot.x, Math.round(plot.y), plot.w, 1.0f, Tokens.alpha(Tokens.LINE_HI, 0.8f));
        c.textRight("capacity " + Text.value(ceiling), plot.right(), plot.y + 2.0f,
                Tokens.INK_3, Text.MICRO);
    }

    private static void drawEmpty(PanelSurface c, Rect box) {
        Shapes.card(c, box, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        c.textCenter("Nothing on this network yet", box.centerX(), box.centerY() - 22.0f,
                Tokens.INK_2, Text.BODY);
        c.textCenter("Point a Display Link from a dynamo, power connector, accumulator,",
                box.centerX(), box.centerY() - 6.0f, Tokens.INK_3, Text.MICRO);
        c.textCenter("capacitor bank, electric motor or alternator at this panel.",
                box.centerX(), box.centerY() + 6.0f, Tokens.INK_3, Text.MICRO);
        c.textCenter("Link a Distribution Unit or Battery Controller to see it divide the load.",
                box.centerX(), box.centerY() + 22.0f, Tokens.INK_3, Text.MICRO);
    }
}
