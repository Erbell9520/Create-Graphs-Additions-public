package com.createstats.web;

// hand rolled, gson would allocate a tree several times a second forever
public final class WebJson {
    private static final int INITIAL = 16 * 1024;

    private final StringBuilder sb = new StringBuilder(INITIAL);

    private boolean fresh = true;

    public WebJson beginObject() {
        separate();
        this.sb.append('{');
        this.fresh = true;
        return this;
    }

    public WebJson endObject() {
        this.sb.append('}');
        this.fresh = false;
        return this;
    }

    public WebJson beginArray() {
        separate();
        this.sb.append('[');
        this.fresh = true;
        return this;
    }

    public WebJson endArray() {
        this.sb.append(']');
        this.fresh = false;
        return this;
    }

    public WebJson name(String key) {
        separate();
        quote(this.sb, key);
        this.sb.append(':');
        this.fresh = true;
        return this;
    }

    public WebJson value(String text) {
        separate();
        if (text == null) {
            this.sb.append("null");
        } else {
            quote(this.sb, text);
        }
        this.fresh = false;
        return this;
    }

    public WebJson value(boolean flag) {
        separate();
        this.sb.append(flag ? "true" : "false");
        this.fresh = false;
        return this;
    }

    public WebJson value(int number) {
        separate();
        this.sb.append(number);
        this.fresh = false;
        return this;
    }

    public WebJson value(long number) {
        separate();
        this.sb.append(number);
        this.fresh = false;
        return this;
    }

    public WebJson value(double number) {
        separate();
        this.sb.append(compact(number));
        this.fresh = false;
        return this;
    }

    public WebJson put(String key, String text) {
        return name(key).value(text);
    }

    public WebJson put(String key, boolean flag) {
        return name(key).value(flag);
    }

    public WebJson put(String key, int number) {
        return name(key).value(number);
    }

    public WebJson put(String key, long number) {
        return name(key).value(number);
    }

    public WebJson put(String key, double number) {
        return name(key).value(number);
    }

    public WebJson putColor(String key, int argb) {
        return name(key).value(hex(argb));
    }

    @Override
    public String toString() {
        return this.sb.toString();
    }

    public static String hex(int argb) {
        final char[] out = new char[7];
        out[0] = '#';
        for (int i = 0; i < 6; i++) {
            final int nibble = (argb >> (20 - i * 4)) & 0xF;
            out[i + 1] = (char) (nibble < 10 ? '0' + nibble : 'a' + nibble - 10);
        }
        return new String(out);
    }

    public static String compact(double number) {
        if (!Double.isFinite(number)) {
            return "0";
        }
        final double rounded = Math.round(number * 1000.0) / 1000.0;
        if (rounded == Math.rint(rounded) && Math.abs(rounded) < 1.0e15) {
            return Long.toString((long) rounded);
        }
        return Double.toString(rounded);
    }

    public static void quote(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '<' -> out.append("\\u003c");
                case '>' -> out.append("\\u003e");
                default -> {
                    if (c < 0x20 || c == 0x2028 || c == 0x2029) {
                        escapeUnicode(out, c);
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    private static void escapeUnicode(StringBuilder out, char c) {
        out.append("\\u");
        for (int shift = 12; shift >= 0; shift -= 4) {
            final int nibble = (c >> shift) & 0xF;
            out.append((char) (nibble < 10 ? '0' + nibble : 'a' + nibble - 10));
        }
    }

    private void separate() {
        if (!this.fresh) {
            this.sb.append(',');
        }
        this.fresh = false;
    }
}
