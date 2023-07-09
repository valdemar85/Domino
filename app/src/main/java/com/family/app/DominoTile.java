package com.family.app;

import android.graphics.Bitmap;

public class DominoTile implements Comparable<DominoTile> {
    private int leftValue;
    private int rightValue;
    private Bitmap leftImage;
    private Bitmap rightImage;

    public DominoTile(int leftValue, int rightValue, Bitmap leftImage, Bitmap rightImage) {
        this.leftValue = leftValue;
        this.rightValue = rightValue;
        this.leftImage = leftImage;
        this.rightImage = rightImage;
    }

    public int getLeftValue() {
        return leftValue;
    }

    public int getRightValue() {
        return rightValue;
    }

    public Bitmap getLeftImage() {
        return leftImage;
    }

    public Bitmap getRightImage() {
        return rightImage;
    }

    public boolean isDouble() {
        return leftValue == rightValue;
    }

    @Override
    public int compareTo(DominoTile o) {
        if (isDouble() && o.isDouble()) {
            return Integer.compare(leftValue, o.leftValue);
        }
        return Integer.compare(getValue(), o.getValue());
    }

    public int getValue() {
        return leftValue + rightValue;
    }
}
