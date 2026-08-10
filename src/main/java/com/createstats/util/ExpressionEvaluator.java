package com.createstats.util;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ExpressionEvaluator
{
    private ExpressionEvaluator() {
    }
    
    public static EvaluationResult evaluate(String expression, boolean scientificMode) throws EvaluationException {
        final double value = new Parser(expression, scientificMode, false, 0.0).parse();
        return new EvaluationResult(value, format(value));
    }
    
    public static double evaluateForX(String expression, boolean scientificMode, double xValue) throws EvaluationException {
        return new Parser(expression, scientificMode, true, xValue).parse();
    }
    
    public static float[] samplePlot(String expression, boolean scientificMode, double minX, double maxX, int sampleCount) throws EvaluationException {
        if (sampleCount < 2) {
            return new float[0];
        }
        if (maxX <= minX) {
            throw new EvaluationException("Plot range must increase");
        }
        final List<Float> samples = new ArrayList<Float>();
        EvaluationException firstError = null;
        for (int i = 0; i < sampleCount; ++i) {
            final double xValue = minX + (maxX - minX) * (double)i / (double)(sampleCount - 1);
            try {
                final double value = evaluateForX(expression, scientificMode, xValue);
                if (Double.isFinite(value) && Math.abs(value) <= 3.4028234663852886E38) {
                    samples.add((float)value);
                }
            }
            catch (EvaluationException e) {
                if (firstError == null) {
                    firstError = e;
                }
            }
        }
        if (samples.size() < 2) {
            if (firstError != null) {
                throw firstError;
            }
            return new float[0];
        }
        final float[] values = new float[samples.size()];
        for (int i = 0; i < samples.size(); ++i) {
            values[i] = samples.get(i);
        }
        return values;
    }
    
    public static String format(double value) throws EvaluationException {
        ensureFinite(value);
        final BigDecimal decimal = BigDecimal.valueOf(value).stripTrailingZeros();
        String text = decimal.toPlainString();
        if ("-0".equals(text)) {
            text = "0";
        }
        return text;
    }
    
    private static double ensureFinite(double value) throws EvaluationException {
        if (!Double.isFinite(value)) {
            throw new EvaluationException("Result is not finite");
        }
        return value;
    }
    
    public static final class EvaluationResult
    {
        private final double value;
        private final String formatted;
        
        public EvaluationResult(double value, String formatted) {
            this.value = value;
            this.formatted = formatted;
        }
        
        public double getValue() {
            return this.value;
        }
        
        public String getFormatted() {
            return this.formatted;
        }
    }
    
    public static class EvaluationException extends Exception
    {
        public EvaluationException(String message) {
            super(message);
        }
    }
    
    private static final class Parser
    {
        private final String input;
        private final boolean scientificMode;
        private final boolean allowVariable;
        private final double xValue;
        private int index;
        
        private Parser(String input, boolean scientificMode, boolean allowVariable, double xValue) {
            this.input = ((input != null) ? input.trim() : "");
            this.scientificMode = scientificMode;
            this.allowVariable = allowVariable;
            this.xValue = xValue;
            this.index = 0;
        }
        
        public double parse() throws EvaluationException {
            if (this.input.isEmpty()) {
                throw new EvaluationException("Expression is empty");
            }
            final double value = this.parseExpression();
            this.skipWhitespace();
            if (!this.isAtEnd()) {
                throw this.error("Unexpected '" + this.peek() + "'");
            }
            return ensureFinite(value);
        }
        
        private double parseExpression() throws EvaluationException {
            double value = this.parseTerm();
            while (true) {
                this.skipWhitespace();
                if (this.match('+')) {
                    value = ensureFinite(value + this.parseTerm());
                }
                else {
                    if (!this.match('-')) {
                        return value;
                    }
                    value = ensureFinite(value - this.parseTerm());
                }
            }
        }
        
        private double parseTerm() throws EvaluationException {
            double value = this.parsePower();
            while (true) {
                this.skipWhitespace();
                if (this.match('*')) {
                    value = ensureFinite(value * this.parsePower());
                }
                else if (this.match('/')) {
                    final double denominator = this.parsePower();
                    if (Math.abs(denominator) < 1.0E-12) {
                        throw this.error("Division by zero");
                    }
                    value = ensureFinite(value / denominator);
                }
                else {
                    return value;
                }
            }
        }
        
        private double parsePower() throws EvaluationException {
            double value = this.parseUnary();
            this.skipWhitespace();
            if (this.match('^')) {
                if (!this.scientificMode) {
                    throw this.error("Powers need scientific mode");
                }
                value = ensureFinite(Math.pow(value, this.parsePower()));
            }
            return value;
        }
        
        private double parseUnary() throws EvaluationException {
            this.skipWhitespace();
            if (this.match('+')) {
                return this.parseUnary();
            }
            if (this.match('-')) {
                return ensureFinite(-this.parseUnary());
            }
            return this.parsePrimary();
        }
        
        private double parsePrimary() throws EvaluationException {
            this.skipWhitespace();
            if (this.match('(')) {
                final double value = this.parseExpression();
                this.expect(')');
                return value;
            }
            if (this.isDigit(this.peek()) || this.peek() == '.') {
                return this.parseNumber();
            }
            if (this.isIdentifierStart(this.peek())) {
                return this.parseIdentifier();
            }
            throw this.error("Unexpected '" + this.peek() + "'");
        }
        
        private double parseNumber() throws EvaluationException {
            final int start = this.index;
            boolean seenDot = false;
            while (!this.isAtEnd()) {
                final char ch = this.peek();
                if (this.isDigit(ch)) {
                    ++this.index;
                }
                else if (ch == '.' && !seenDot) {
                    seenDot = true;
                    ++this.index;
                }
                else {
                    break;
                }
            }
            try {
                return ensureFinite(Double.parseDouble(this.input.substring(start, this.index)));
            }
            catch (NumberFormatException ex) {
                throw this.error("Invalid number");
            }
        }
        
        private double parseIdentifier() throws EvaluationException {
            final String name = this.readIdentifier().toLowerCase(Locale.ROOT);
            if ("x".equals(name)) {
                if (!this.allowVariable) {
                    throw this.error("x is only available while plotting");
                }
                if (!this.scientificMode) {
                    throw this.error("x needs scientific mode");
                }
                return this.xValue;
            }
            if ("pi".equals(name)) {
                if (!this.scientificMode) {
                    throw this.error("pi needs scientific mode");
                }
                return Math.PI;
            }
            if ("e".equals(name)) {
                if (!this.scientificMode) {
                    throw this.error("e needs scientific mode");
                }
                return Math.E;
            }
            if (!this.scientificMode) {
                throw this.error("Functions need scientific mode");
            }
            this.skipWhitespace();
            this.expect('(');
            final List<Double> args = new ArrayList<Double>();
            this.skipWhitespace();
            if (!this.match(')')) {
                do {
                    args.add(this.parseExpression());
                    this.skipWhitespace();
                } while (this.match(','));
                this.expect(')');
            }
            return ensureFinite(this.applyFunction(name, args));
        }
        
        private double applyFunction(String name, List<Double> args) throws EvaluationException {
            if ("sin".equals(name)) {
                return this.expectArgCount(name, args, 1, Math.sin(args.get(0)));
            }
            if ("cos".equals(name)) {
                return this.expectArgCount(name, args, 1, Math.cos(args.get(0)));
            }
            if ("tan".equals(name)) {
                return this.expectArgCount(name, args, 1, Math.tan(args.get(0)));
            }
            if ("asin".equals(name)) {
                return this.expectArgCount(name, args, 1, Math.asin(args.get(0)));
            }
            if ("acos".equals(name)) {
                return this.expectArgCount(name, args, 1, Math.acos(args.get(0)));
            }
            if ("atan".equals(name)) {
                return this.expectArgCount(name, args, 1, Math.atan(args.get(0)));
            }
            if ("sqrt".equals(name)) {
                final double value = this.expectSingleArg(name, args);
                if (value < 0.0) {
                    throw this.error("sqrt needs a non-negative value");
                }
                return Math.sqrt(value);
            }
            if ("log".equals(name)) {
                final double value = this.expectSingleArg(name, args);
                if (value <= 0.0) {
                    throw this.error("log needs a positive value");
                }
                return Math.log10(value);
            }
            if ("ln".equals(name)) {
                final double value = this.expectSingleArg(name, args);
                if (value <= 0.0) {
                    throw this.error("ln needs a positive value");
                }
                return Math.log(value);
            }
            if ("abs".equals(name)) {
                return this.expectArgCount(name, args, 1, Math.abs(args.get(0)));
            }
            if ("exp".equals(name)) {
                return this.expectArgCount(name, args, 1, Math.exp(args.get(0)));
            }
            if ("floor".equals(name)) {
                return this.expectArgCount(name, args, 1, Math.floor(args.get(0)));
            }
            if ("ceil".equals(name)) {
                return this.expectArgCount(name, args, 1, Math.ceil(args.get(0)));
            }
            if ("round".equals(name)) {
                return this.expectArgCount(name, args, 1, (double)Math.round(args.get(0)));
            }
            if ("pow".equals(name)) {
                if (args.size() != 2) {
                    throw this.error("pow expects 2 arguments");
                }
                return Math.pow(args.get(0), args.get(1));
            }
            if ("min".equals(name)) {
                if (args.size() != 2) {
                    throw this.error("min expects 2 arguments");
                }
                return Math.min(args.get(0), args.get(1));
            }
            if ("max".equals(name)) {
                if (args.size() != 2) {
                    throw this.error("max expects 2 arguments");
                }
                return Math.max(args.get(0), args.get(1));
            }
            throw this.error("Unknown function: " + name);
        }
        
        private double expectArgCount(String name, List<Double> args, int count, double value) throws EvaluationException {
            if (args.size() != count) {
                throw this.error(name + " expects " + count + " argument" + ((count == 1) ? "" : "s"));
            }
            return value;
        }
        
        private double expectSingleArg(String name, List<Double> args) throws EvaluationException {
            if (args.size() != 1) {
                throw this.error(name + " expects 1 argument");
            }
            return args.get(0);
        }
        
        private String readIdentifier() {
            final int start = this.index;
            while (!this.isAtEnd() && this.isIdentifierPart(this.peek())) {
                ++this.index;
            }
            return this.input.substring(start, this.index);
        }
        
        private void expect(char expected) throws EvaluationException {
            this.skipWhitespace();
            if (!this.match(expected)) {
                throw this.error("Expected '" + expected + "'");
            }
        }
        
        private boolean match(char expected) {
            if (this.isAtEnd() || this.input.charAt(this.index) != expected) {
                return false;
            }
            ++this.index;
            return true;
        }
        
        private void skipWhitespace() {
            while (!this.isAtEnd() && Character.isWhitespace(this.input.charAt(this.index))) {
                ++this.index;
            }
        }
        
        private char peek() {
            return this.isAtEnd() ? '\0' : this.input.charAt(this.index);
        }
        
        private boolean isAtEnd() {
            return this.index >= this.input.length();
        }
        
        private boolean isDigit(char ch) {
            return ch >= '0' && ch <= '9';
        }
        
        private boolean isIdentifierStart(char ch) {
            return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || ch == '_';
        }
        
        private boolean isIdentifierPart(char ch) {
            return this.isIdentifierStart(ch) || this.isDigit(ch);
        }
        
        private EvaluationException error(String message) {
            return new EvaluationException(message);
        }
    }
}
