package com.family.app;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private static final String[] DEFAULT_NAMES = {"Альбертик", "Джузеппе", "Пипа", "Бобик", "Слон", "Гришка", "ВасяПупкин", "Горпына", "Чиполино", "Тараканище", "Губозакаточный", "Кабан", "Бегемот", "Чикибамбони", "Забияка", "Чирипыжик", "Карлос", "Гонсалес"};

    private GameService gameService;
    private GameAdapter gameAdapter;

    private Button newGameButton, cancelGameButton, startGameButton;
    private EditText playerNameInput;
    private RecyclerView gameList;
    private TextView errorMessage;
    private String currentGameId;
    private GameSyncService gameSyncService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        gameService = GameService.getInstance();
        gameSyncService = new GameSyncService();

        newGameButton = findViewById(R.id.new_game_button);
        cancelGameButton = findViewById(R.id.cancel_game_button);
        startGameButton = findViewById(R.id.start_game_button);
        playerNameInput = findViewById(R.id.player_name_input);
        gameList = findViewById(R.id.game_list);
        gameList.addItemDecoration(new DividerItemDecoration(this, LinearLayoutManager.VERTICAL));
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
            Game game = gameService.createGame(playerName);
            if (game != null) {
                currentGameId = game.getId();
                playerNameInput.setEnabled(false);
                gameSyncService.saveGame(game);
                gameAdapter.updateGameStatus(true);
                GameDataCallback gameDataCallback = new GameDataCallback() {
                    @Override
                    public void onGameLoaded(Game game) {
                        // Если игра успешно загружена, можно обновить UI или выполнить другие действия.
                        gameAdapter.updateGame(game); // предполагая, что у вас есть соответствующий метод в GameAdapter
                        updateUI(); // обновляем интерфейс
                    }

                    @Override
                    public void onDataNotAvailable(String error) {
                        // Если данные не доступны, можно показать сообщение об ошибке.
                        errorMessage.setText("Ошибка загрузки игры: " + error);
                        errorMessage.setVisibility(View.VISIBLE);
                    }
                };
                gameSyncService.syncGame(gameDataCallback, game.getId());
            }
            updateUI();
        });

        cancelGameButton.setOnClickListener(v -> {
            if (currentGameId != null) {
                gameService.cancelGame(currentGameId);
                gameSyncService.removeGame(currentGameId);
                currentGameId = null;
                playerNameInput.setEnabled(true);
                gameAdapter.updateGameStatus(false);
                updateUI();
            }
        });

        startGameButton.setOnClickListener(v -> {
            if (currentGameId != null) {
                gameService.startGame(currentGameId);
                gameSyncService.removeGameEventListener();
                // переходите на новую активность здесь
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
                    playerNameInput.setError("Введите имя");
                } else {
                    gameAdapter.updatePlayerName(s.toString());
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                // No operation needed here
            }
        });
        loadUnstartedGames();
    }

    private void loadUnstartedGames() {
        gameSyncService.getAllUnstartedGames(new GamesDataCallback() {
            @Override
            public void onGamesLoaded(List<Game> games) {
                // Обновление списка игр в RecyclerView
                gameService.updateGames(games);
                gameAdapter.updateGames(games);
                errorMessage.setVisibility(View.GONE);
                gameList.setVisibility(View.VISIBLE);
            }

            @Override
            public void onDataNotAvailable(String error) {
                // Показ сообщения об ошибке
                errorMessage.setText("Ошибка загрузки таблицы: " + error);
                errorMessage.setVisibility(View.VISIBLE);
                gameList.setVisibility(View.GONE);
            }
        });
    }

    private void updateUI() {
        Game game = gameService.getCurrentGame();
        if (game != null) {
            if (game.isStarted()) {
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
        System.out.println("--------------------------"+gameService.getGames().size());
    }

    @Override
    protected void onPause() {
        super.onPause();
        gameSyncService.removeGameEventListener();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadUnstartedGames();

        if (currentGameId != null) {
            gameSyncService.syncGame(new GameDataCallback() {
                @Override
                public void onGameLoaded(Game game) {
                    if (game != null) {
                        gameService.updateGame(game);
                        updateUI();
                    }
                }

                @Override
                public void onDataNotAvailable(String error) {
                    // handle error
                    errorMessage.setText("Sync error: " + error);
                    errorMessage.setVisibility(View.VISIBLE);
                    gameList.setVisibility(View.GONE);
                }
            }, currentGameId);
        }
    }
}
