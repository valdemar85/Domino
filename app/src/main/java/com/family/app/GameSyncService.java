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

    public GameSyncService() {
    }

    // Синхронизация состояния игры с базой данных
    public void syncGame(Game game) {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference databaseReference = database.getReference(GAMES_TABLE_NAME).child(game.getId());
        // Обновляем значение игры в базе данных
        databaseReference.setValue(game);
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

        gamesRef.addListenerForSingleValueEvent(new ValueEventListener() {
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
}



