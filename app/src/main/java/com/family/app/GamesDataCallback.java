package com.family.app;

import java.util.List;

// Callback для обработки данных об играх, полученных из Firebase
interface GamesDataCallback {
    void onGamesLoaded(List<Game> games);
    void onDataNotAvailable(String error);
}
