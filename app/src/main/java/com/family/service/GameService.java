package com.family.service;

import com.family.dto.Game;
import com.family.dto.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GameService {
    private static GameService instance = null;
    private ConnectionRequestListener listener;
    private List<Game> games = new ArrayList<>();
    private Game currentGame;
    private Player currentPlayer;

    private GameService() {
    }

    public static GameService getInstance() {
        if (instance == null) {
            instance = new GameService();
        }
        return instance;
    }

    public interface ConnectionRequestListener {
        void onConnectionRequestApproved(Player player, Game game);
        void onConnectionRequestRejected(Player player, Game game);
    }

    public void setConnectionRequestListener(ConnectionRequestListener listener) {
        this.listener = listener;
    }

    public List<Game> getGames() {
        return games;
    }

    public void setGames(List<Game> games) {
        this.games = games;
    }

    public Game findGameById(String id) {
        for (Game game : games) {
            if (game.getId().equals(id)) {
                return game;
            }
        }
        return null;
    }

    public boolean connectToGame(String gameId, Player player) {
        Game game = findGameById(gameId);
        if (game == null) {
            if (listener != null) {
                listener.onConnectionRequestRejected(player, game);
            }
            return false; // Game not found
        }

        List<Player> players = game.getPlayers();
        if (players.size() >= 4 || players.contains(player)) {
            if (listener != null) {
                listener.onConnectionRequestRejected(player, game);
            }
            return false; // The game is already full or the player is already in the game
        }

        players.add(player);
        if (listener != null) {
            listener.onConnectionRequestApproved(player, game);
        }
        return true;
    }

    public void handleRequest(String gameId, Player player) {
        connectToGame(gameId, player);
    }

    public boolean kickPlayer(String gameId, Player player) {
        Game game = findGameById(gameId);
        if (game == null) {
            return false; // Game not found
        }

        List<Player> players = game.getPlayers();
        if (!players.contains(player)) {
            return false; // The player is not in the game
        }

        players.remove(player);
        return true;
    }

    public boolean addPlayer(String gameId, Player player) {
        Game game = findGameById(gameId);
        if (game == null) {
            return false; // Game not found
        }

        List<Player> players = game.getPlayers();
        if (players.contains(player)) {
            return false; // The player is already in the game
        }

        players.add(player);
        return true;
    }

    public boolean startGame() {
        Game game = findGameById(currentGame.getId());
        if (game == null) {
            return false; // Game not found
        }

        boolean gameStarted = game.startGame();
        if (gameStarted) {
            currentGame = game; // Update the current game after starting a game
        }

        return gameStarted;
    }

    public Game getCurrentGame() {
        return currentGame;
    }

    public void setCurrentGame(Game currentGame) {
        this.currentGame = currentGame;
    }

    public Player getCurrentPlayer() {
        return currentPlayer;
    }

    public void setCurrentPlayer(Player currentPlayer) {
        this.currentPlayer = currentPlayer;
    }

    public boolean removeGame(String gameId) {
        currentGame = null;
        Game game = findGameById(gameId);
        if (game == null) {
            return false; // Game not found
        }
        return games.remove(game);
    }

    public Game createGame() {
        String gameId = UUID.randomUUID().toString();
        String gameName = currentPlayer.getName() + "'s Game";
        List<Player> players = new ArrayList<>();
        players.add(currentPlayer);
        Game game = new Game(gameId, gameName, currentPlayer.getId(), players);
        currentGame = game;
        games.add(game);
        return game;
    }

    public void updateGame(Game updatedGame) {
        if (updatedGame == null) return;

        Game existingGame = findGameById(updatedGame.getId());

        if (existingGame != null) {
            games.remove(existingGame);
            games.add(updatedGame);
            if (currentGame != null && currentGame.getId().equals(updatedGame.getId())) {
                setCurrentGame(updatedGame);
            }
        }
    }
}
