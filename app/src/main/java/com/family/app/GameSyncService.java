package com.family.app;

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

    // Метод для удаления слушателя базы данных
    public void removeGameEventListener() {
        if (gameEventListener != null) {
            FirebaseDatabase.getInstance().getReference(GAMES_TABLE_NAME).removeEventListener(gameEventListener);
            gameEventListener = null;
        }
    }
}
