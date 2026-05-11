package com.family.activity;

import android.app.Application;

import com.family.service.GameSyncService;

public class DominoApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Pre-warm Firebase. setPersistenceEnabled is moved here from a static block so it
        // happens reliably before any FirebaseDatabase.getInstance() usage in any class loader
        // path. GameSyncService.init() opens the persistent listener on /games immediately,
        // so Firebase connection setup overlaps with the rest of process startup and the
        // first Activity sees data ready (or close to ready) when it opens.
        GameSyncService.init();
    }
}
