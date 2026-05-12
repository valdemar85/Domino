package com.family.dto;

import com.google.firebase.database.Exclude;

import java.util.Objects;

/**
 * One domino tile. Both faces hold a number 0..6.
 * Equality is orientation-independent: (3|5) == (5|3).
 */
public class Tile {
    private int a;
    private int b;

    public Tile() {}

    public Tile(int a, int b) {
        this.a = a;
        this.b = b;
    }

    public int getA() { return a; }
    public void setA(int a) { this.a = a; }
    public int getB() { return b; }
    public void setB(int b) { this.b = b; }

    @Exclude
    public int points() {
        return a + b;
    }

    @Exclude
    public boolean matches(int value) {
        return a == value || b == value;
    }

    @Exclude
    public int otherSide(int value) {
        return a == value ? b : a;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Tile)) return false;
        Tile other = (Tile) o;
        return (a == other.a && b == other.b) || (a == other.b && b == other.a);
    }

    @Override
    public int hashCode() {
        int min = Math.min(a, b);
        int max = Math.max(a, b);
        return Objects.hash(min, max);
    }
}
