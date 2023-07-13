package com.family.app;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GameService {
    private List<Game> games = new ArrayList<>();
    private ConnectionRequestListener listener;
    private static GameService instance = null;
    private Game currentGame;

    private GameService() {
        // конструктор
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

    public Game createGame(String id, String name, String boss, List<Player> players) {
        Game game = new Game(id, name, boss, players);
        games.add(game);
        return game;
    }

    public List<Game> getGames() {
        return games;
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

    public boolean startGame(String gameId) {
        Game game = findGameById(gameId);
        if (game == null) {
            return false; // Game not found
        }

        boolean gameStarted = game.startGame(); // This should start the game and return true if successful
        if (gameStarted) {
            // Code to sync the game state with Firebase
            // This will depend on how you've set up your Firebase database
            setCurrentGame(game); // Update the current game after starting a game
        }

        return gameStarted;
    }

    public Game getCurrentGame() {
        return currentGame;
    }

    public void setCurrentGame(Game currentGame) {
        this.currentGame = currentGame;
    }

    public boolean cancelGame(String gameId) {
        Game game = findGameById(gameId);
        if (game == null) {
            return false; // Game not found
        }

        boolean result = games.remove(game); // Remove the game from the list of games
        if (result) {
            setCurrentGame(null); // Reset the current game after cancelling a game
        }
        return result;
    }

    public Game createGame(String playerName) {
        String gameId = UUID.randomUUID().toString();
        String gameName = playerName + "'s Game";
        Player boss = new Player(playerName, gameId);
        List<Player> players = new ArrayList<>();
        players.add(boss);

        Game game = createGame(gameId, gameName, boss.getId(), players);
        setCurrentGame(game); // Set the current game after creating a new game
        return game;
    }
}
