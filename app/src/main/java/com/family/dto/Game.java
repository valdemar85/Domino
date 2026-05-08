package com.family.dto;

import java.util.ArrayList;
import java.util.List;

public class Game {
    private String id;
    private String name;
    private String bossId;
    private List<Player> players;
    private boolean isStarted;
    private List<Message> messages = new ArrayList<>();

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
        if (players == null) {
            players = new ArrayList<>();
        }
        return players;
    }

    public void setPlayers(List<Player> players) {
        this.players = players;
    }

    public Player getPlayerById(String playerId) {
        if (players == null) return null;
        for (Player player : players) {
            if (player.getId().equals(playerId)) {
                return player;
            }
        }
        return null;
    }

    public boolean startGame() {
        int size = (players == null) ? 0 : players.size();
        if (size < 2 || size > 4) {
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
        if (players == null) return false;
        for (Player player : players) {
            if (player.getId().equals(playerId)) {
                return true;
            }
        }
        return false;
    }

    public List<Message> getMessages() {
        if (messages == null) {
            messages = new ArrayList<>();
        }
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    public void addMessage(Message message) {
        if (this.messages == null) {
            this.messages = new ArrayList<>();
        }
        this.messages.add(message);
    }

}
