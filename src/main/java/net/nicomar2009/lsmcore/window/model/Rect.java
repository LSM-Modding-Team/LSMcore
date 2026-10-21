package net.nicomar2009.lsmcore.window.model;

import java.util.Optional;

/** Closed geometric bounds in blocks; intersections require positive area. */
public record Rect(double minX, double minY, double maxX, double maxY) {
    public static final double EPSILON = 1e-8;
    public Rect {
        if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(maxX)
                || !Double.isFinite(maxY) || minX >= maxX || minY >= maxY)
            throw new IllegalArgumentException("Invalid surface bounds");
    }
    public double width() { return maxX - minX; }
    public double height() { return maxY - minY; }
    public boolean contains(Rect other) {
        return minX <= other.minX + EPSILON && minY <= other.minY + EPSILON
                && maxX + EPSILON >= other.maxX && maxY + EPSILON >= other.maxY;
    }
    public Rect move(double x, double y) { return new Rect(minX+x, minY+y, maxX+x, maxY+y); }
    public Optional<Rect> intersect(Rect other) {
        double x0=Math.max(minX,other.minX), y0=Math.max(minY,other.minY);
        double x1=Math.min(maxX,other.maxX), y1=Math.min(maxY,other.maxY);
        return x1-x0 > EPSILON && y1-y0 > EPSILON ? Optional.of(new Rect(x0,y0,x1,y1)) : Optional.empty();
    }
}
