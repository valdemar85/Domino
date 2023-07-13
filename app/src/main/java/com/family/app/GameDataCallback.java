package com.family.app;

// Callback для обработки данных, полученных из Firebase
public interface GameDataCallback {
    void onGameLoaded(Game game);
    void onDataNotAvailable(String error);
}
