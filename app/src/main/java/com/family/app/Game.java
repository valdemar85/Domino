package com.family.app;

import java.util.List;

public class Game {
    private String id;
    private String name;
    private String bossId;
    private List<Player> players;
    private boolean isStarted;


    // No-argument constructor
    public Game() {}

    public Game(String id, String name, String bossId, List<Player> players) {
        this.id = id;
        this.name = name;
        this.bossId = bossId;
        this.players = players;
        this.isStarted = false;  // The game is not started initially
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBossId() {
        return bossId;
    }

    public void setBossId(String bossId) {
        this.bossId = bossId;
    }

    public List<Player> getPlayers() {
        return players;
    }

    public void setPlayers(List<Player> players) {
        this.players = players;
    }

    public boolean startGame() {
        if (players.size() < 2) {
            return false; // Not enough players to start the game
        }

        isStarted = true;
        return true;
    }

    public boolean isStarted() {
        return isStarted;
    }

    public boolean hasPlayer(String playerName) {
        for (Player player : players) {
            if (player.getName().equals(playerName)) {
                return true;
            }
        }
        return false;
    }
}
