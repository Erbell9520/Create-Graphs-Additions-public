package com.createstats.statsmk3.page;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.createstats.blockentity.SourceConfig;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.display.CustomScreenSourceFamilyCatalog;
import com.createstats.display.SourceFamilyDefinition;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class Mk3PageRegistry {
    private static final List<Mk3Page> PAGES = new ArrayList<>();

    static {
        register(new StressometerPage());
        register(new RefinedStoragePage());
        register(new StoragePage());
        register(new TrainPage());
        register(new TopologyPage());
        register(new EfficiencyPage());
        register(new PowerPage());
        register(new OilfieldPage());
        register(new HeatPage());
        register(new FluidsPage());
        register(new DieselPage());
        register(new AeroPage());
        register(new DrivePage());
        register(new SurveyPage());
    }

    private Mk3PageRegistry() {
    }

    // whole panel, the focused source is empty on open and the stress tab used to just not exist
    public static List<Mk3Page> pagesForPanel(StatsDisplayPanelBE panel) {
        if (panel == null || PAGES.isEmpty()) {
            return List.of();
        }
        final List<Mk3Page> matches = new ArrayList<>();

        for (final Mk3Page page : PAGES) {
            if (page.appliesTo(panel)) {
                matches.add(page);
            }
        }

        final var registry = panel.getSourceRegistry();
        if (registry != null) {
            for (final SourceConfig source : registry.values()) {
                if (source == null) {
                    continue;
                }
                final SourceFamilyDefinition family =
                        CustomScreenSourceFamilyCatalog.familyForSource(source);
                for (final Mk3Page page : PAGES) {
                    if (page.handles(family) && !matches.contains(page)) {
                        matches.add(page);
                    }
                }
            }
        }
        matches.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
        return matches;
    }

    public static String signatureFor(StatsDisplayPanelBE panel) {
        final StringBuilder sb = new StringBuilder();
        for (final Mk3Page page : pagesForPanel(panel)) {
            sb.append(page.id()).append(';');
        }
        return sb.toString();
    }

    public static void register(Mk3Page page) {
        if (page == null || page.id() == null || page.id().isEmpty()) {
            return;
        }
        for (final Mk3Page existing : PAGES) {
            if (existing.id().equals(page.id())) {
                return;
            }
        }
        PAGES.add(page);
        PAGES.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
    }

    public static List<Mk3Page> all() {
        return Collections.unmodifiableList(PAGES);
    }

    public static List<Mk3Page> pagesFor(SourceFamilyDefinition family) {
        if (family == null || PAGES.isEmpty()) {
            return List.of();
        }
        final List<Mk3Page> matches = new ArrayList<>();
        for (final Mk3Page page : PAGES) {
            if (page.handles(family)) {
                matches.add(page);
            }
        }
        return matches;
    }

    public static SourceFamilyDefinition familyOf(StatsDisplayPanelBE panel, String sourceId) {
        if (panel == null || sourceId == null || sourceId.isEmpty()) {
            return null;
        }
        final SourceConfig config = panel.getSourceRegistry() == null
                ? null : panel.getSourceRegistry().get(sourceId);
        if (config != null) {
            return CustomScreenSourceFamilyCatalog.familyForSource(config);
        }
        return CustomScreenSourceFamilyCatalog.familyForSourceType(panel.getSourceType());
    }
}
