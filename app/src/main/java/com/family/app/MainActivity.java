package com.family.app;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private static final String[] DEFAULT_NAMES = {"John", "Alex", "Maria", "Emma", "Jacob"};

    private GameService gameService;
    private GameAdapter gameAdapter;

    private Button newGameButton, cancelGameButton, startGameButton;
    private EditText playerNameInput;
    private RecyclerView gameList;
    private TextView errorMessage;
    private String currentGameId; // добавляем переменную для сохранения текущего ID игры

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        gameService = GameService.getInstance();

        newGameButton = findViewById(R.id.new_game_button);
        cancelGameButton = findViewById(R.id.cancel_game_button);
        startGameButton = findViewById(R.id.start_game_button);
        playerNameInput = findViewById(R.id.player_name_input);
        gameList = findViewById(R.id.game_list);
        errorMessage = findViewById(R.id.error_message);

        gameList.setLayoutManager(new LinearLayoutManager(this));
        gameAdapter = new GameAdapter(new ArrayList<>(), gameService, playerNameInput.getText().toString());
        gameList.setAdapter(gameAdapter);

        newGameButton.setOnClickListener(v -> {
            String playerName = playerNameInput.getText().toString();
            if (playerName.isEmpty()) {
                errorMessage.setText("Введите имя");
                return;
            }
            Game game = gameService.createGame(playerName); // получаем созданную игру
            if (game != null) {
                currentGameId = game.getId();
                playerNameInput.setEnabled(false); // делаем поле нередактируемым
            }
            updateUI();
        });

        cancelGameButton.setOnClickListener(v -> {
            if (currentGameId != null) {
                gameService.cancelGame(currentGameId);
                currentGameId = null;
                playerNameInput.setEnabled(true); // делаем поле снова редактируемым
                updateUI();
            }
        });

        startGameButton.setOnClickListener(v -> {
            if (currentGameId != null) {
                gameService.startGame(currentGameId);
                updateUI();
            }
        });

        String defaultName = DEFAULT_NAMES[new Random().nextInt(DEFAULT_NAMES.length)];
        playerNameInput.setText(defaultName);
        gameAdapter.updatePlayerName(defaultName);

        playerNameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // No operation needed here
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.toString().trim().isEmpty()) {
                    // Если поле ввода стало пустым, показываем сообщение об ошибке
                    playerNameInput.setError("Введите имя");
                } else {
                    // Иначе, обновляем имя в адаптере
                    gameAdapter.updatePlayerName(s.toString());
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                // No operation needed here
            }
        });
    }

    private void updateUI() {
        Game game = gameService.getCurrentGame();
        if (game != null) {
            if (game.isGameStarted()) {
                // Обновите UI для начала игры, возможно, переход на новую активность
            } else {
                newGameButton.setVisibility(View.GONE);
                cancelGameButton.setVisibility(View.VISIBLE);
                if (game.getPlayers().size() >= 2 && game.getPlayers().size() <= 4) {
                    startGameButton.setVisibility(View.VISIBLE);
                } else {
                    startGameButton.setVisibility(View.GONE);
                }
            }
        } else {
            newGameButton.setVisibility(View.VISIBLE);
            cancelGameButton.setVisibility(View.GONE);
            startGameButton.setVisibility(View.GONE);
        }
        gameAdapter.updateGames(gameService.getGames());
    }
}
