package com.family.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

public class Deck {
    private Stack<DominoTile> tiles = new Stack<>();
    private Context context;

    public Deck(Context context) {
        this.context = context;
        for (int i = 0; i <= 6; i++) {
            for (int j = i; j <= 6; j++) {
                int leftResId = context.getResources().getIdentifier("domino_" + i, "drawable", context.getPackageName());
                int rightResId = context.getResources().getIdentifier("domino_" + j, "drawable", context.getPackageName());
                Bitmap leftBitmap = BitmapFactory.decodeResource(context.getResources(), leftResId);
                Bitmap rightBitmap = BitmapFactory.decodeResource(context.getResources(), rightResId);
                tiles.push(new DominoTile(i, j, leftBitmap, rightBitmap));
            }
        }
        shuffle();
    }

    public void shuffle() {
        Collections.shuffle(tiles);
    }

    public DominoTile draw() {
        if (!tiles.empty()) {
            return tiles.pop();
        } else {
            return null;
        }
    }

    public int size() {
        return tiles.size();
    }
}
