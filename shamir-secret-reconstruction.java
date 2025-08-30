// Main.java
// Build: javac Main.java
// Run:   java Main input.json
// where input.json is one of the provided testcases.

import java.io.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import org.json.*;  // Add org.json dependency (see README)

public class Main {

    // -------- Big Rational (exact) ----------
    static final class BigFrac implements Comparable<BigFrac> {
        final BigInteger num, den; // always den > 0 and gcd(|num|, den) = 1

        BigFrac(BigInteger n, BigInteger d) {
            if (d.signum() == 0) throw new ArithmeticException("Division by zero in fraction");
            if (d.signum() < 0) { n = n.negate(); d = d.negate(); }
            BigInteger g = n.gcd(d);
            if (!g.equals(BigInteger.ONE)) { n = n.divide(g); d = d.divide(g); }
            this.num = n; this.den = d;
        }

        static BigFrac of(BigInteger n) { return new BigFrac(n, BigInteger.ONE); }

        BigFrac add(BigFrac o) { return new BigFrac(num.multiply(o.den).add(o.num.multiply(den)), den.multiply(o.den)); }
        BigFrac sub(BigFrac o) { return new BigFrac(num.multiply(o.den).subtract(o.num.multiply(den)), den.multiply(o.den)); }
        BigFrac mul(BigFrac o) { return new BigFrac(num.multiply(o.num), den.multiply(o.den)); }
        BigFrac div(BigFrac o) { return new BigFrac(num.multiply(o.den), den.multiply(o.num)); }

        boolean isInteger() { return den.equals(BigInteger.ONE); }
        BigInteger toBigIntegerExact() {
            if (!isInteger()) throw new ArithmeticException("Non-integer result where integer expected: " + this);
            return num;
        }
        @Override public int compareTo(BigFrac o) {
            return num.multiply(o.den).compareTo(o.num.multiply(den));
        }
        @Override public boolean equals(Object o) {
            if (!(o instanceof BigFrac)) return false;
            BigFrac b = (BigFrac)o;
            return num.equals(b.num) && den.equals(b.den);
        }
        @Override public int hashCode() { return Objects.hash(num, den); }
        @Override public String toString() { return isInteger() ? num.toString() : (num + "/" + den); }
    }

    // -------- Point ----------
    static final class Point {
        final BigInteger x;
        final BigInteger y;
        Point(BigInteger x, BigInteger y) { this.x = x; this.y = y; }
        @Override public String toString() { return "(" + x + "," + y + ")"; }
    }

    // -------- Expression evaluator for "value" strings (ADD,SUB,MUL,DIV) ----------
    // The whole expression is in a given base. Numbers inside are parsed in that base.
    static final class ExprParser {
        private final String s;
        private int pos;
        private final int base;

        ExprParser(String s, int base) { this.s = s.trim(); this.base = base; this.pos = 0; }

        BigInteger parse() {
            skip();
            BigInteger val = parseExpr();
            skip();
            if (pos != s.length()) throw new RuntimeException("Trailing characters in value: " + s.substring(pos));
            return val;
        }

        private BigInteger parseExpr() {
            skip();
            if (peekAlpha()) {
                String name = parseIdent().toUpperCase(Locale.ROOT);
                expect('(');
                List<BigInteger> args = new ArrayList<>();
                do {
                    skip();
                    args.add(parseExpr());
                    skip();
                } while (accept(','));
                expect(')');
                return apply(name, args);
            } else {
                // number in given base
                String lit = parseNumberToken();
                try {
                    return new BigInteger(lit, base);
                } catch (Exception e) {
                    throw new RuntimeException("Bad number '" + lit + "' for base " + base);
                }
            }
        }

        private BigInteger apply(String name, List<BigInteger> a) {
            switch (name) {
                case "ADD": ensureArgs(name, a, 2); return a.get(0).add(a.get(1));
                case "SUB": ensureArgs(name, a, 2); return a.get(0).subtract(a.get(1));
                case "MUL": ensureArgs(name, a, 2); return a.get(0).multiply(a.get(1));
                case "DIV":
                    ensureArgs(name, a, 2);
                    if (a.get(1).equals(BigInteger.ZERO)) throw new ArithmeticException("DIV by zero");
                    // Expect exact division; if not exact, do integer division toward zero (define policy)
                    BigInteger[] qr = a.get(0).divideAndRemainder(a.get(1));
                    if (!qr[1].equals(BigInteger.ZERO))
                        throw new ArithmeticException("Non-exact DIV in value expression");
                    return qr[0];
                default: throw new RuntimeException("Unknown function: " + name);
            }
        }

        private static void ensureArgs(String n, List<BigInteger> a, int cnt) {
            if (a.size() != cnt) throw new RuntimeException(n + " expects " + cnt + " args");
        }

        // --- helpers
        private void skip() { while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++; }
        private boolean accept(char c) { if (pos < s.length() && s.charAt(pos) == c) { pos++; return true; } return false; }
        private void expect(char c) { if (!accept(c)) throw new RuntimeException("Expected '" + c + "' at " + pos); }
        private boolean peekAlpha() { return pos < s.length() && Character.isLetter(s.charAt(pos)); }
        private String parseIdent() {
            int start = pos;
            while (pos < s.length() && (Character.isLetterOrDigit(s.charAt(pos)) || s.charAt(pos) == '_')) pos++;
            if (start == pos) throw new RuntimeException("Identifier expected at " + pos);
            return s.substring(start, pos);
        }
        private String parseNumberToken() {
            int start = pos;
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (Character.isLetterOrDigit(c)) pos++;
                else break;
            }
            if (start == pos) throw new RuntimeException("Number expected at " + pos);
            return s.substring(start, pos);
        }
    }

    // -------- Lagrange interpolation utilities ----------
    // f(0) from a K-subset, exact rationals
    static BigFrac f0FromSubset(List<Point> subset) {
        BigFrac total = BigFrac.of(BigInteger.ZERO);
        for (int i = 0; i < subset.size(); i++) {
            BigInteger xi = subset.get(i).x;
            BigInteger yi = subset.get(i).y;
            BigInteger num = BigInteger.ONE;
            BigInteger den = BigInteger.ONE;
            for (int j = 0; j < subset.size(); j++) if (j != i) {
                BigInteger xj = subset.get(j).x;
                num = num.multiply(xj.negate());           // -xj
                den = den.multiply(xi.subtract(xj));       // (xi - xj)
            }
            BigFrac term = new BigFrac(yi.multiply(num), den);
            total = total.add(term);
        }
        return total;
    }

    // Build full polynomial (degree K-1) from a K-subset: p(x) = sum_i y_i * L_i(x)
    static List<BigFrac> polyFromSubset(List<Point> subset) {
        int k = subset.size();
        List<BigFrac> coeff = new ArrayList<>(Collections.nCopies(k, BigFrac.of(BigInteger.ZERO)));
        for (int i = 0; i < k; i++) {
            BigInteger xi = subset.get(i).x;
            BigInteger yi = subset.get(i).y;
            // basis numerator: Π_{j!=i} (X - xj)
            List<BigFrac> basis = new ArrayList<>();
            basis.add(BigFrac.of(BigInteger.ONE)); // 1
            BigInteger den = BigInteger.ONE;
            for (int j = 0; j < k; j++) if (j != i) {
                BigInteger xj = subset.get(j).x;
                basis = polyMul(basis, Arrays.asList(
                        BigFrac.of(xj.negate()), // constant term (-xj)
                        BigFrac.of(BigInteger.ONE) // + 1*X
                ));
                den = den.multiply(xi.subtract(xj));
            }
            BigFrac scale = new BigFrac(yi, BigInteger.ONE).div(new BigFrac(den, BigInteger.ONE));
            basis = polyScale(basis, scale);
            coeff = polyAdd(coeff, basis);
        }
        return coeff; // coeff[0] is constant = secret
    }

    static List<BigFrac> polyMul(List<BigFrac> a, List<BigFrac> b) {
        List<BigFrac> res = new ArrayList<>(Collections.nCopies(a.size() + b.size() - 1, BigFrac.of(BigInteger.ZERO)));
        for (int i = 0; i < a.size(); i++)
            for (int j = 0; j < b.size(); j++)
                res.set(i + j, res.get(i + j).add(a.get(i).mul(b.get(j))));
        return res;
    }

    static List<BigFrac> polyAdd(List<BigFrac> a, List<BigFrac> b) {
        int n = Math.max(a.size(), b.size());
        List<BigFrac> res = new ArrayList<>(Collections.nCopies(n, BigFrac.of(BigInteger.ZERO)));
        for (int i = 0; i < n; i++) {
            BigFrac ai = i < a.size() ? a.get(i) : BigFrac.of(BigInteger.ZERO);
            BigFrac bi = i < b.size() ? b.get(i) : BigFrac.of(BigInteger.ZERO);
            res.set(i, ai.add(bi));
        }
        return res;
    }

    static List<BigFrac> polyScale(List<BigFrac> p, BigFrac s) {
        List<BigFrac> r = new ArrayList<>(p.size());
        for (BigFrac c : p) r.add(c.mul(s));
        return r;
    }

    static BigFrac polyEval(List<BigFrac> coeff, BigInteger x) {
        BigFrac pow = BigFrac.of(BigInteger.ONE);
        BigFrac sum = BigFrac.of(BigInteger.ZERO);
        for (BigFrac c : coeff) {
            sum = sum.add(c.mul(pow));
            pow = pow.mul(BigFrac.of(x));
        }
        return sum;
    }

    // -------- Combinatorics: all K-combinations of indices 0..n-1 ----------
    static void combinations(int n, int k, IntConsumer combConsumer) {
        int[] idx = new int[k];
        for (int i = 0; i < k; i++) idx[i] = i;
        while (true) {
            combConsumer.accept(idx.clone());
            int p = k - 1;
            while (p >= 0 && idx[p] == p + n - k) p--;
            if (p < 0) break;
            idx[p]++;
            for (int j = p + 1; j < k; j++) idx[j] = idx[j - 1] + 1;
        }
    }
    interface IntConsumer { void accept(int[] idx); }

    // -------- Parse JSON and run ----------
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java Main <input.json>");
            System.exit(1);
        }
        String jsonText = Files.readString(Path.of(args[0]));
        JSONObject root = new JSONObject(jsonText);

        JSONObject keys = root.getJSONObject("keys");
        int n = keys.getInt("n");
        int k = keys.getInt("k");

        // Collect all (x, y) as Points
        List<Point> all = new ArrayList<>();
        for (String key : root.keySet()) {
            if (key.equals("keys")) continue;
            // 'key' is x-coordinate as string. The object has base and value.
            BigInteger x = new BigInteger(key); // x are small but keep BigInteger
            JSONObject obj = root.getJSONObject(key);
            int base = Integer.parseInt(obj.getString("base"));
            String valueStr = obj.getString("value").trim();

            BigInteger y;
            if (looksLikeFunction(valueStr)) {
                // Evaluate functional expression in the given base
                y = new ExprParser(valueStr, base).parse();
            } else {
                y = new BigInteger(valueStr, base);
            }
            all.add(new Point(x, y));
        }
        if (all.size() != n) {
            System.err.println("Warning: provided n=" + n + " but parsed " + all.size() + " points.");
        }

        // Majority vote over all K-subsets on f(0)
        Map<BigFrac, Integer> freq = new HashMap<>();
        final List<List<Point>> goodSubsets = new ArrayList<>();
        combinations(all.size(), k, idx -> {
            List<Point> subset = new ArrayList<>(k);
            for (int id : idx) subset.add(all.get(id));
            BigFrac f0 = f0FromSubset(subset);
            freq.merge(f0, 1, Integer::sum);
        });

        BigFrac bestF0 = null; int bestCount = -1;
        for (Map.Entry<BigFrac,Integer> e : freq.entrySet()) {
            if (e.getValue() > bestCount) { bestCount = e.getValue(); bestF0 = e.getKey(); }
        }
        if (bestF0 == null) throw new RuntimeException("Could not determine secret");

        // Find a concrete subset that yields bestF0 to build full polynomial
        List<Point> winner = null;
        combinations(all.size(), k, idx -> {
            if (winner != null) return;
            List<Point> subset = new ArrayList<>(k);
            for (int id : idx) subset.add(all.get(id));
            if (f0FromSubset(subset).equals(bestF0)) winner = subset;
        });
        if (winner == null) throw new RuntimeException("No subset matched the winning secret");

        // Build polynomial and check all points
        List<BigFrac> coeff = polyFromSubset(winner);
        BigInteger secret = coeff.get(0).toBigIntegerExact();

        List<String> wrong = new ArrayList<>();
        for (Point p : all) {
            BigFrac val = polyEval(coeff, p.x);
            if (!val.isInteger() || val.toBigIntegerExact().compareTo(p.y) != 0) {
                wrong.add("x=" + p.x + ": expected=" + val.toBigIntegerExact() + ", got=" + p.y);
            }
        }

        // Output
        System.out.println("Secret: " + secret);
        System.out.println("Wrong shares: " + (wrong.isEmpty() ? "[]" : wrong));
    }

    static boolean looksLikeFunction(String v) {
        // crude but effective: NAME( ... )
        int p = v.indexOf('(');
        return p > 0 && Character.isLetter(v.charAt(0)) && v.endsWith(")");
    }
}
