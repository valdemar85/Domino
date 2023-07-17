package com.family.callbacks;

import com.family.dto.Game;

import java.util.List;

// Callback для обработки данных об играх, полученных из Firebase
public interface GamesDataCallback {
    void onGamesLoaded(List<Game> games);
    void onDataNotAvailable(String error);
}
