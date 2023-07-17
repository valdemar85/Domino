package com.family.callbacks;

import com.family.dto.Game;

// Callback для обработки данных, полученных из Firebase
public interface GameDataCallback {
    void onGameLoaded(Game game);
    void onDataNotAvailable(String error);
}
