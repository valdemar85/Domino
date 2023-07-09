package com.family.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import com.family.app.R;

public class PlayerSelectionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.select_players);

        Button connectButton = findViewById(R.id.button_connect);
        connectButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Переход к игре после подключения игроков
                Intent intent = new Intent(PlayerSelectionActivity.this, GameActivity.class);
                startActivity(intent);
            }
        });
    }
}

