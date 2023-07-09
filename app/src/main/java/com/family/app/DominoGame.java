package com.family.app;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

public class DominoGame {
    private List<Player> players = new ArrayList<>();
    private Deck deck;
    private int currentPlayerIndex = 0;
    private int initialTileCount = 7;
    private Context context; // Assume you have a context here

    public DominoGame(Context context, int playerCount) {
        this.context = context;
        if (playerCount < 2 || playerCount > 4) {
            throw new IllegalArgumentException("Player count must be between 2 and 4.");
        }
        for (int i = 1; i <= playerCount; i++) {
            players.add(new Player("Player" + i));
        }
    }

    public void startNewGame() {
        // create deck
        deck = new Deck(context);

        // draw initial tiles for all players
        for (Player player : players) {
            player.draw(deck, initialTileCount);
        }

        // determine first player
        currentPlayerIndex = determineFirstPlayer();
    }

    public int determineFirstPlayer() {
        DominoTile lowestDouble = new DominoTile(6, 6, null, null);
        int firstPlayerIndex = -1;
        for (int i = 0; i < players.size(); i++) {
            DominoTile playerLowestDouble = players.get(i).getSmallestDoubleTile();
            if (playerLowestDouble != null && playerLowestDouble.compareTo(lowestDouble) < 0) {
                lowestDouble = playerLowestDouble;
                firstPlayerIndex = i;
            }
        }
        return firstPlayerIndex != -1 ? firstPlayerIndex : 0;
    }

    public List<Player> getPlayers() {
        return players;
    }

    public int getCurrentPlayerIndex() {
        return currentPlayerIndex;
    }
}
