package com.family.app;

import java.util.ArrayList;
import java.util.List;

public class GameState {
    private Game game;
    private List<Player> players;

    public GameState(Game game) {
        this.game = game;
        this.players = new ArrayList<>(game.getPlayers());
    }

    public Game getGame() {
        return game;
    }

    public List<Player> getPlayers() {
        return players;
    }
}
