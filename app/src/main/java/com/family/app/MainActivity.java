package com.family.app;

import android.util.Log;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {

    private DominoGame game;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Например, создание новой игры с 4 игроками:
        game = new DominoGame(this, 4); // передаем контекст и количество игроков

        final TextView firstPlayerText = findViewById(R.id.first_player_text);
        Button dealButton = findViewById(R.id.deal_button);
        dealButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                game.startNewGame(); // начинаем новую игру при нажатии на кнопку "Deal"

                // Добавим некоторый код для отладки:
                Player firstPlayer = game.getPlayers().get(game.getCurrentPlayerIndex());
                firstPlayerText.setText("First player is: " + firstPlayer.getName());
                Log.d("DominoGame", "First player is: " + firstPlayer.getName());
            }
        });
    }
}
