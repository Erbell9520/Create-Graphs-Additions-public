package com.createstats.display;

public enum DisplayTheme
{
    DARK("Dark", -301068772, -3351058, -15066573, -16711800, -10061944, -2838729, -16119274, -15198160, -14013867, -14540220, -39356, -1996536764, 0.00f, 0, 0.00f, 0.00f, 0.10f, 0.20f),
    LIGHT("Light", -986902, -14540237, -3355461, -14527045, -7829351, -11180374, -1513248, -2236971, -3355444, -2763312, -3390430, -1999879100, 0.00f, 0, 0.00f, 0.00f, 0.05f, 0.10f),
    CRT("CRT", -301660923, -13369549, -16111094, -16711936, -14518494, -16724992, -16644094, -16119286, -16111094, -16246776, -52480, -1996545536, 0.18f, 3, 0.30f, 0.05f, 0.35f, 0.05f),
    BLUEPRINT("Blueprint", -300275900, -2232577, -14535851, -11158529, -10057558, -12285748, -15392200, -14930358, -14008218, -14534568, -30652, -1996528060, 0.04f, 6, 0.00f, 0.00f, 0.10f, 0.30f),
    STEAMPUNK("Steampunk", -299229420, -660272, -12767718, -2838729, -7638187, -4684277, -14805747, -14016748, -12767718, -13424618, -3394816, -1996545536, 0.00f, 0, 0.05f, 0.00f, 0.20f, 0.85f),
    CREATE("Create", -300147682, -1518173, -14014942, -3630276, -8754350, -4684277, -15396338, -14804202, -13423068, -14146532, -1552336, -1998045152, 0.00f, 0, 0.10f, 0.00f, 0.10f, 0.65f);

    private final String displayName;
    public final int bg;
    public final int text;
    public final int grid;
    public final int accent;
    public final int dim;
    public final int border;
    public final int graphBg;
    public final int tabBg;
    public final int tabSel;
    public final int tabHover;
    public final int warn;
    public final int threshLine;

    public final float scanlineOpacity;
    public final int scanlineSpacing;
    public final float phosphorBloom;
    public final float flickerStrength;
    public final float vignetteStrength;
    public final float ornamentLevel;

    private DisplayTheme(String displayName, int bg, int text, int grid, int accent, int dim, int border, int graphBg, int tabBg, int tabSel, int tabHover, int warn, int threshLine,
                          float scanlineOpacity, int scanlineSpacing, float phosphorBloom, float flickerStrength, float vignetteStrength, float ornamentLevel) {
        this.displayName = displayName;
        this.bg = bg;
        this.text = text;
        this.grid = grid;
        this.accent = accent;
        this.dim = dim;
        this.border = border;
        this.graphBg = graphBg;
        this.tabBg = tabBg;
        this.tabSel = tabSel;
        this.tabHover = tabHover;
        this.warn = warn;
        this.threshLine = threshLine;
        this.scanlineOpacity = scanlineOpacity;
        this.scanlineSpacing = scanlineSpacing;
        this.phosphorBloom = phosphorBloom;
        this.flickerStrength = flickerStrength;
        this.vignetteStrength = vignetteStrength;
        this.ornamentLevel = ornamentLevel;
    }

    public int selectionOutline() { return this.accent; }
    public int chromeDivider() { return this.border; }
    public int handleAccent() { return this.accent; }
    public int entryChrome() { return this.tabSel; }

    public String getDisplayName() {
        return this.displayName;
    }

    public DisplayTheme next() {
        final DisplayTheme[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }
}
