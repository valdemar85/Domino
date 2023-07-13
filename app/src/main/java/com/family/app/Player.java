package com.family.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Player {
    private String id;
    private String name;
    private String gameId;
    private List<DominoTile> hand = new ArrayList<>();

    // No-argument constructor
    public Player() {}

    public Player(String name, String gameId) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.gameId = gameId;
    }

    public String getName() {
        return name;
    }

    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
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

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    // Other methods...
}
