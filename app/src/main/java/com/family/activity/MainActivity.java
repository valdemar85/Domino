package com.family.activity;

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
import com.family.app.*;
import com.family.callbacks.GameDataCallback;
import com.family.callbacks.GamesDataCallback;
import com.family.dto.Game;
import com.family.dto.Player;
import com.family.service.GameService;
import com.family.service.GameSyncService;
import com.family.utils.UserUtils;

import java.util.List;
import java.util.concurrent.CountDownLatch;

public class MainActivity extends AppCompatActivity {

    private GameService gameService;
    private GameListTable gameListTable;

    private Button newGameButton, cancelGameButton, startGameButton;
    private EditText playerNameInput;
    private RecyclerView gameList;
    private TextView errorMessage;
    private GameSyncService gameSyncService;
    private String currentUserId;
    final CountDownLatch latch = new CountDownLatch(1);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        UserUtils.getAdvertisingId(getApplicationContext(), adId -> {
            currentUserId = adId;
            gameSyncService.findGameByPlayerId(adId, new GameDataCallback() {
                @Override
                public void onGameLoaded(Game game) {
                    try {
                        latch.await(); // ждем, пока счетчик CountDownLatch не станет 0
                        if (game != null) {
                            Player playerById = game.getPlayerById(adId);
                            playerNameInput.setText(playerById.getName());
                            playerNameInput.setEnabled(false);
                            gameService.setCurrentGame(game);
                            gameListTable.setInGame(true);
                            gameListTable.setPlayerName(playerById.getName());
                            gameListTable.setPlayerId(playerById.getId());
                            updateUI();
                        } else {
                            // Обрабатываем случай, когда игра не найдена
                            // ничего не делаем - у нас и так приложение загрузилось как для нового пользователя
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onDataNotAvailable(String error) {
                    try {
                        latch.await(); // ждем, пока счетчик CountDownLatch не станет 0
                        errorMessage.setText("Ошибка загрузки игры: " + error);
                        errorMessage.setVisibility(View.VISIBLE);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
        });

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
        gameListTable = new GameListTable(gameService, playerNameInput.getText().toString(), currentUserId);
        gameList.setAdapter(gameListTable);

        newGameButton.setOnClickListener(v -> {
            String playerName = playerNameInput.getText().toString();
            if (playerName.isEmpty()) {
                errorMessage.setText("Введите имя");
                return;
            }
            Game game = gameService.createGame(playerName, currentUserId);
            playerNameInput.setEnabled(false);
            gameSyncService.saveGame(game);
            gameListTable.updateGameStatus(true);
            GameDataCallback gameDataCallback = new GameDataCallback() {
                @Override
                public void onGameLoaded(Game game) {
                    // Если игра успешно загружена, можно обновить UI или выполнить другие действия.
                    gameService.updateGame(game);
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

            updateUI();
        });

        cancelGameButton.setOnClickListener(v -> {
            if (gameService.getCurrentGame() != null) {
                gameSyncService.removeGame(gameService.getCurrentGame().getId());
                gameService.removeGame(gameService.getCurrentGame().getId());
                playerNameInput.setEnabled(true);
                gameListTable.updateGameStatus(false);
                updateUI();
            }
        });

        startGameButton.setOnClickListener(v -> {
            if (gameService.getCurrentGame() != null) {
                gameService.startGame(gameService.getCurrentGame().getId());
                gameSyncService.removeGameEventListener();
                // переходите на новую активность здесь
                updateUI();
            }
        });

        String defaultName = UserUtils.generateDefaultName();
        playerNameInput.setText(defaultName);
        gameListTable.updatePlayerName(defaultName);

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
                    gameListTable.updatePlayerName(s.toString());
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
                gameService.setGames(games);
                errorMessage.setVisibility(View.GONE);
                gameList.setVisibility(View.VISIBLE);
                updateUI();
                latch.countDown(); // уменьшаем счетчик на 1
            }

            @Override
            public void onDataNotAvailable(String error) {
                // Показ сообщения об ошибке
                errorMessage.setText("Ошибка загрузки таблицы: " + error);
                errorMessage.setVisibility(View.VISIBLE);
                gameList.setVisibility(View.GONE);
                latch.countDown(); // уменьшаем счетчик на 1
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
        gameListTable.notifyDataSetChanged();
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

        if (gameService.getCurrentGame() != null) {
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
            }, gameService.getCurrentGame().getId());
        }
    }
}
