package com.createstats.manual.figure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A complete data-driven lesson for one manual entry: an ordered list of figure sheets. */
public record ManualFigure(String pageId, List<FigureSheet> sheets) {

    public ManualFigure {
        sheets = Collections.unmodifiableList(new ArrayList<>(sheets));
    }
}
