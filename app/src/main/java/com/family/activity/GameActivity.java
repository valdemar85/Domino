package com.family.activity;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import com.family.app.R;

public class GameActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        // Обработка нажатий кнопок и других UI элементов здесь
        // Например, Button startGameButton = findViewById(R.id.startGameButton);
        // startGameButton.setOnClickListener(...);
    }
}
