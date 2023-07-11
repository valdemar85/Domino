package com.family.app;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class GameSyncService {
    private DatabaseReference databaseReference;

    public GameSyncService(String gameId) {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        databaseReference = database.getReference("games").child(gameId);
    }

    public void syncGameState(GameState gameState) {
        databaseReference.setValue(gameState);
    }
}
