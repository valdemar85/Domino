package com.family.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Player {
    private String name;
    private List<DominoTile> hand = new ArrayList<>();

    public Player(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void draw(Deck deck, int count) {
        for (int i = 0; i < count; i++) {
            DominoTile tile = deck.draw();
            if (tile != null) {
                hand.add(tile);
            }
        }
    }

    public DominoTile getSmallestDoubleTile() {
        Collections.sort(hand);
        for (DominoTile tile : hand) {
            if (tile.isDouble()) {
                return tile;
            }
        }
        return null;
    }

    // Other methods...
}
