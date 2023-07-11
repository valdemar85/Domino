package com.family.app;

import java.util.List;

public class Game {
    private String id;
    private String name;
    private Player boss;
    private List<Player> players;
    private boolean isStarted;

    public Game(String id, String name, Player boss, List<Player> players) {
        this.id = id;
        this.name = name;
        this.boss = boss;
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

    public Player getBoss() {
        return boss;
    }

    public void setBoss(Player boss) {
        this.boss = boss;
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

    public boolean isGameStarted() {
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
