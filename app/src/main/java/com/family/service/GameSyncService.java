package com.family.service;

import com.family.dto.Game;
import com.family.callbacks.GamesDataCallback;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class GameSyncService {
    public static final String GAMES_TABLE_NAME = "games";
    public static final long STALE_GAME_TTL_MILLIS = 60L * 60L * 1000L; // 1 hour

    public interface SaveErrorCallback {
        void onError(String error);
    }

    public interface ConnectionCallback {
        void onConnectionStateChanged(boolean connected);
    }

    private static volatile GameSyncService instance;

    /**
     * Initialize from Application.onCreate() to pre-warm Firebase before any Activity opens.
     * Subsequent calls are no-ops.
     */
    public static synchronized GameSyncService init() {
        if (instance == null) {
            // Persistence is intentionally NOT enabled. On Android emulators the disk
            // cache adds ambiguity (stale snapshots vs live updates) that makes
            // multi-device debugging confusing. Default Realtime Database behaviour
            // is in-memory only, which is what we want for this app's flow.
            FirebaseDatabase.getInstance().goOnline();
            instance = new GameSyncService();
            instance.startGamesListener();
            instance.startConnectionListener();
            instance.startServerTimeOffsetListener();
        }
        return instance;
    }

    public static GameSyncService getInstance() {
        if (instance == null) {
            return init();
        }
        return instance;
    }

    // In-memory state — kept current by the persistent listeners below.
    // Activities read this immediately on resume without making a fresh Firebase call.
    private final List<Game> cachedGames = new ArrayList<>();
    private volatile boolean hasGamesData = false;
    private volatile Boolean cachedConnected = null;
    /** Offset from device clock to Firebase server clock; updated live via .info/serverTimeOffset. */
    private volatile long serverTimeOffset = 0;

    private final CopyOnWriteArrayList<GamesDataCallback> gamesCallbacks = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ConnectionCallback> connectionCallbacks = new CopyOnWriteArrayList<>();

    private GameSyncService() {
    }

    /**
     * Returns "now" expressed in Firebase server time. This is our local clock
     * adjusted by the offset Firebase reports between this device and the server.
     * Use this everywhere you would otherwise use System.currentTimeMillis() —
     * timestamps stored in Firebase need to be comparable across devices that
     * may have clocks drifting apart by minutes or hours (notably on emulators).
     */
    public long currentServerTimeMillis() {
        return System.currentTimeMillis() + serverTimeOffset;
    }

    private void startServerTimeOffsetListener() {
        FirebaseDatabase.getInstance().getReference(".info/serverTimeOffset")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        Long offset = snapshot.getValue(Long.class);
                        if (offset != null) {
                            serverTimeOffset = offset;
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        // Keep last-known offset (or zero) on error
                    }
                });
    }

    private void startGamesListener() {
        DatabaseReference gamesRef = FirebaseDatabase.getInstance().getReference(GAMES_TABLE_NAME);
        gamesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                List<Game> activeGames = new ArrayList<>();
                long now = currentServerTimeMillis();
                for (DataSnapshot gameSnapshot : dataSnapshot.getChildren()) {
                    Game game = gameSnapshot.getValue(Game.class);
                    if (game == null) {
                        continue;
                    }
                    long createdAt = game.getCreatedAt();
                    // Cleanup criteria:
                    //  - createdAt == 0: legacy record from an old build.
                    //  - now - createdAt > TTL: orphan game whose creator never disbanded.
                    if (createdAt == 0 || (now - createdAt) > STALE_GAME_TTL_MILLIS) {
                        removeGame(game.getId());
                        continue;
                    }
                    // NOTE: we keep started games in the cache too — the lobby filters
                    // them out for display via gameService.getOpenGames(), but each
                    // player still needs to see "my own team just started" via this
                    // listener so MainActivity can launch GameActivity.
                    activeGames.add(game);
                }
                synchronized (cachedGames) {
                    cachedGames.clear();
                    cachedGames.addAll(activeGames);
                }
                hasGamesData = true;
                for (GamesDataCallback cb : gamesCallbacks) {
                    cb.onGamesLoaded(snapshotGames());
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                for (GamesDataCallback cb : gamesCallbacks) {
                    cb.onDataNotAvailable(databaseError.getMessage());
                }
            }
        });
    }

    private void startConnectionListener() {
        FirebaseDatabase.getInstance().getReference(".info/connected").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Boolean connected = snapshot.getValue(Boolean.class);
                boolean isConnected = connected != null && connected;
                cachedConnected = isConnected;
                for (ConnectionCallback cb : connectionCallbacks) {
                    cb.onConnectionStateChanged(isConnected);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                cachedConnected = false;
                for (ConnectionCallback cb : connectionCallbacks) {
                    cb.onConnectionStateChanged(false);
                }
            }
        });
    }

    private List<Game> snapshotGames() {
        synchronized (cachedGames) {
            return new ArrayList<>(cachedGames);
        }
    }

    /**
     * Subscribe to live games-list updates. The callback receives the latest cached
     * snapshot synchronously if data is already available, then continues to receive
     * updates whenever Firebase delivers new state.
     */
    public void registerGamesListener(GamesDataCallback callback) {
        gamesCallbacks.addIfAbsent(callback);
        if (hasGamesData) {
            callback.onGamesLoaded(snapshotGames());
        }
    }

    public void unregisterGamesListener(GamesDataCallback callback) {
        gamesCallbacks.remove(callback);
    }

    public void registerConnectionCallback(ConnectionCallback callback) {
        connectionCallbacks.addIfAbsent(callback);
        if (cachedConnected != null) {
            callback.onConnectionStateChanged(cachedConnected);
        }
    }

    public void unregisterConnectionCallback(ConnectionCallback callback) {
        connectionCallbacks.remove(callback);
    }

    public void saveGame(Game game) {
        saveGame(game, null);
    }

    public void saveGame(Game game, SaveErrorCallback errorCallback) {
        FirebaseDatabase.getInstance()
                .getReference(GAMES_TABLE_NAME)
                .child(game.getId())
                .setValue(game, (databaseError, ref) -> {
                    if (databaseError != null && errorCallback != null) {
                        errorCallback.onError(databaseError.getMessage());
                    }
                });
    }

    public void removeGame(String gameId) {
        FirebaseDatabase.getInstance()
                .getReference(GAMES_TABLE_NAME)
                .child(gameId)
                .removeValue();
    }
}
