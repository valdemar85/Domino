package com.family.service;

import com.family.dto.Game;
import com.family.callbacks.GameDataCallback;
import com.family.callbacks.GamesDataCallback;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class GameSyncService {
    public static final String GAMES_TABLE_NAME = "games";
    private ValueEventListener gameEventListener;

    public GameSyncService() {
    }

    public void saveGame(Game game) {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference gameRef = database.getReference(GAMES_TABLE_NAME).child(game.getId());

        gameRef.setValue(game);
    }

    public void syncGame(final GameDataCallback callback, String gameId) {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference gameRef = database.getReference(GAMES_TABLE_NAME).child(gameId);

        gameRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Game game = dataSnapshot.getValue(Game.class);
                if (game != null) {
                    callback.onGameLoaded(game);
                } else {
                    callback.onDataNotAvailable("Game not found");
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                callback.onDataNotAvailable(databaseError.getMessage());
            }
        });
    }


    // Удаление игры из базы данных по ее id
    public void removeGame(String gameId) {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        database.getReference(GAMES_TABLE_NAME).child(gameId).removeValue();
    }

    // Получение данных об игре из базы данных
    public void getGame(final String gameId, final GameDataCallback callback) {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference gameRef = database.getReference(GAMES_TABLE_NAME).child(gameId);

        gameRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Game game = dataSnapshot.getValue(Game.class);
                callback.onGameLoaded(game);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                callback.onDataNotAvailable(databaseError.getMessage());
            }
        });
    }

    // Метод для получения всех игр, которые еще не начались
    public void getAllUnstartedGames(final GamesDataCallback callback) {
        DatabaseReference gamesRef = FirebaseDatabase.getInstance().getReference(GAMES_TABLE_NAME);

        gameEventListener = gamesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                List<Game> unstartedGames = new ArrayList<>();
                for (DataSnapshot gameSnapshot : dataSnapshot.getChildren()) {
                    Game game = gameSnapshot.getValue(Game.class);
                    if (game != null && !game.isStarted()) {
                        unstartedGames.add(game);
                    }
                }
                callback.onGamesLoaded(unstartedGames);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                callback.onDataNotAvailable(databaseError.getMessage());
            }
        });
    }

    // Метод для получения игры, в которую записан игрок с playerId, которая еще не началась
    public void findGameByPlayerId(String playerId, GameDataCallback callback) {
        DatabaseReference gamesRef = FirebaseDatabase.getInstance().getReference(GAMES_TABLE_NAME);

        gameEventListener = gamesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (DataSnapshot gameSnapshot : dataSnapshot.getChildren()) {
                    Game game = gameSnapshot.getValue(Game.class);
                    if (game != null && !game.isStarted() && game.hasPlayer(playerId)) {
                        gamesRef.removeEventListener(this); // Удалить слушатель, так как мы нашли нужную игру
                        callback.onGameLoaded(game);
                        return;
                    }
                }
                // Если мы дошли до этого момента и не нашли игру, это означает, что подходящей игры нет
                callback.onGameLoaded(null);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                callback.onDataNotAvailable(databaseError.getMessage());
            }
        });
    }


    // Метод для удаления слушателя базы данных
    public void removeGameEventListener() {
        if (gameEventListener != null) {
            FirebaseDatabase.getInstance().getReference(GAMES_TABLE_NAME).removeEventListener(gameEventListener);
            gameEventListener = null;
        }
    }
}
