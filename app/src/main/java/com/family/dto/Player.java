package com.family.dto;

public class Player {
    private String id;
    private String name;
    private String gameId;

    // No-argument constructor
    public Player() {}

    public Player(String name, String gameId, String playerId) {
        this.id = playerId;
        this.name = name;
        this.gameId = gameId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

}
