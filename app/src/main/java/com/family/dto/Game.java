package com.family.dto;

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

    public List<Player> getPlayers() {
        return players;
    }

    public Player getPlayerById(String playerId) {
        for (Player player : players) {
            if (player.getId().equals(playerId)) {
                return player;
            }
        }
        return null;
    }

    public boolean startGame() {
        if ((players.size() < 2) || (players.size() > 4)) {
            return false;
        }

        isStarted = true;
        return true;
    }

    public boolean isStarted() {
        return isStarted;
    }

    public String getBossId() {
        return bossId;
    }

    public void setBossId(String bossId) {
        this.bossId = bossId;
    }

    public void setStarted(boolean started) {
        isStarted = started;
    }

    public boolean hasPlayer(String playerId) {
        for (Player player : players) {
            if (player.getId().equals(playerId)) {
                return true;
            }
        }
        return false;
    }

}
