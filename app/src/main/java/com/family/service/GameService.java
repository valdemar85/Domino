package com.family.service;

import android.content.Context;
import com.declination.Declinator;
import com.family.dto.Game;
import com.family.dto.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


public class GameService {
    private static GameService instance = null;
    private ConnectionRequestListener listener;

    private volatile Declinator declinator;
    private final Future<?> declinatorInitFuture;

    private List<Game> games = new ArrayList<>();
    private Game currentGame;
    private Player currentPlayer;


    private GameService(final Context applicationContext) {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        declinatorInitFuture = executorService.submit(() -> {
            declinator = Declinator.getInstance(applicationContext);
        });
        executorService.shutdown();
    }

    public static GameService getInstance(Context applicationContext) {
        if (instance == null) {
            instance = new GameService(applicationContext);
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

    public boolean addPlayerToCurrentGame(Player player) {
        List<Player> players = currentGame.getPlayers();
        if (players.size() >= 4 || players.contains(player)) {
            return false; // The game is already full or the player is already in the game
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
        String declinedName;
        try {
            declinedName = getDeclinator().makeNameGenitive(currentPlayer.getName());
        } catch (InterruptedException | ExecutionException e) {
            declinedName = currentPlayer.getName();
        }
        String gameName = declinedName + " команда";
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

    private Declinator getDeclinator() throws InterruptedException, ExecutionException {
        // Дожидаемся инициализации Declinator
        declinatorInitFuture.get();
        return declinator;
    }
}
