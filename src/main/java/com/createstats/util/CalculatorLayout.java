package com.createstats.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CalculatorLayout
{
    private static final FaceRegion DISPLAY_AREA;
    private static final List<FaceRegion> FACE_BUTTONS;
    
    private CalculatorLayout() {
    }
    
    public static FaceRegion getDisplayArea() {
        return CalculatorLayout.DISPLAY_AREA;
    }
    
    public static List<FaceRegion> getFaceButtons() {
        return CalculatorLayout.FACE_BUTTONS;
    }
    
    public static FaceTarget resolve(double u, double vTop) {
        if (CalculatorLayout.DISPLAY_AREA.contains(u, vTop)) {
            return FaceTarget.display();
        }
        for (FaceRegion button : CalculatorLayout.FACE_BUTTONS) {
            if (button.contains(u, vTop)) {
                return FaceTarget.button(button.getToken());
            }
        }
        return FaceTarget.none();
    }
    
    private static void addButton(List<FaceRegion> buttons, int row, int col, String label, String token) {
        final double keyX = 0.09;
        final double keyY = 0.34;
        final double keyW = 0.82;
        final double keyH = 0.57;
        final double gap = 0.025;
        final int columns = 4;
        final int rows = 5;
        final double cellW = (keyW - gap * (columns - 1)) / columns;
        final double cellH = (keyH - gap * (rows - 1)) / rows;
        final double x = keyX + col * (cellW + gap);
        final double y = keyY + row * (cellH + gap);
        buttons.add(new FaceRegion(label, token, x, y, cellW, cellH));
    }
    
    static {
        DISPLAY_AREA = new FaceRegion("DISPLAY", "__display__", 0.09, 0.07, 0.82, 0.20);
        final List<FaceRegion> buttons = new ArrayList<FaceRegion>();
        addButton(buttons, 0, 0, "C", "C");
        addButton(buttons, 0, 1, "(", "(");
        addButton(buttons, 0, 2, ")", ")");
        addButton(buttons, 0, 3, "/", "/");
        addButton(buttons, 1, 0, "7", "7");
        addButton(buttons, 1, 1, "8", "8");
        addButton(buttons, 1, 2, "9", "9");
        addButton(buttons, 1, 3, "*", "*");
        addButton(buttons, 2, 0, "4", "4");
        addButton(buttons, 2, 1, "5", "5");
        addButton(buttons, 2, 2, "6", "6");
        addButton(buttons, 2, 3, "-", "-");
        addButton(buttons, 3, 0, "1", "1");
        addButton(buttons, 3, 1, "2", "2");
        addButton(buttons, 3, 2, "3", "3");
        addButton(buttons, 3, 3, "+", "+");
        addButton(buttons, 4, 0, "0", "0");
        addButton(buttons, 4, 1, ".", ".");
        addButton(buttons, 4, 2, "DEL", "__backspace__");
        addButton(buttons, 4, 3, "=", "=");
        FACE_BUTTONS = Collections.unmodifiableList(buttons);
    }
    
    public static final class FaceRegion
    {
        private final String label;
        private final String token;
        private final double x;
        private final double y;
        private final double width;
        private final double height;
        
        public FaceRegion(String label, String token, double x, double y, double width, double height) {
            this.label = label;
            this.token = token;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
        
        public String getLabel() {
            return this.label;
        }
        
        public String getToken() {
            return this.token;
        }
        
        public double getX() {
            return this.x;
        }
        
        public double getY() {
            return this.y;
        }
        
        public double getWidth() {
            return this.width;
        }
        
        public double getHeight() {
            return this.height;
        }
        
        public boolean contains(double u, double vTop) {
            return u >= this.x && u <= this.x + this.width && vTop >= this.y && vTop <= this.y + this.height;
        }
    }
    
    public static final class FaceTarget
    {
        private static final FaceTarget NONE;
        private static final FaceTarget DISPLAY;
        private final Type type;
        private final String token;
        
        private FaceTarget(Type type, String token) {
            this.type = type;
            this.token = token;
        }
        
        public static FaceTarget none() {
            return FaceTarget.NONE;
        }
        
        public static FaceTarget display() {
            return FaceTarget.DISPLAY;
        }
        
        public static FaceTarget button(String token) {
            return new FaceTarget(Type.BUTTON, token);
        }
        
        public boolean isDisplay() {
            return this.type == Type.DISPLAY;
        }
        
        public boolean isButton() {
            return this.type == Type.BUTTON;
        }
        
        public String getToken() {
            return this.token;
        }
        
        static {
            NONE = new FaceTarget(Type.NONE, "");
            DISPLAY = new FaceTarget(Type.DISPLAY, "");
        }
        
        private enum Type
        {
            NONE, 
            DISPLAY, 
            BUTTON;
        }
    }
}
