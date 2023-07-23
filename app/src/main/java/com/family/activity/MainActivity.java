package com.family.activity;

import android.content.Context;
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
    private final GameSyncService gameSyncService = new GameSyncService();
    final CountDownLatch latch = new CountDownLatch(1);

    private GameListTableAdapter gameListTableAdapter;
    private CurrentGameTableAdapter currentGameTableAdapter;
    private RecyclerView gameList;
    private RecyclerView currentGameTable;
    private Button newGameButton, cancelGameButton, startGameButton;
    private EditText playerNameInput;
    private TextView errorMessage;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);
        Context applicationContext = getApplicationContext();
        gameService = GameService.getInstance(applicationContext);

        UserUtils.getAdvertisingId(applicationContext, adId -> {
            gameSyncService.findGameByPlayerId(adId, new GameDataCallback() {
                @Override
                public void onGameLoaded(Game game) {
                    try {
                        latch.await(); // ждем, пока счетчик CountDownLatch не станет 0
                        if (game != null) {
                            Player playerById = game.getPlayerById(adId);
                            gameService.setCurrentPlayer(playerById);
                            playerNameInput.setText(playerById.getName());
                            gameService.setCurrentGame(game);
                        } else {
                            // Обрабатываем случай, когда игра не найдена
                            String defaultName = UserUtils.generateDefaultName();
                            Player currentNewPlayer = new Player(defaultName, null, adId);
                            gameService.setCurrentPlayer(currentNewPlayer);
                            playerNameInput.setText(defaultName);
                            gameService.setCurrentGame(null);
                        }
                        updateUI();
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

        newGameButton = findViewById(R.id.new_game_button);
        cancelGameButton = findViewById(R.id.cancel_game_button);
        startGameButton = findViewById(R.id.start_game_button);
        playerNameInput = findViewById(R.id.player_name_input);
        errorMessage = findViewById(R.id.error_message);

        gameList = findViewById(R.id.game_list);
        gameList.addItemDecoration(new DividerItemDecoration(this, LinearLayoutManager.VERTICAL));
        gameList.setLayoutManager(new LinearLayoutManager(this));
        gameListTableAdapter = new GameListTableAdapter(gameService);
        gameList.setAdapter(gameListTableAdapter);

        currentGameTable = findViewById(R.id.current_game);
        currentGameTable.addItemDecoration(new DividerItemDecoration(this, LinearLayoutManager.VERTICAL));
        currentGameTable.setLayoutManager(new LinearLayoutManager(this));
        currentGameTableAdapter = new CurrentGameTableAdapter(gameService, gameSyncService);
        currentGameTable.setAdapter(currentGameTableAdapter);

        newGameButton.setOnClickListener(v -> {
            Game game = gameService.createGame();
            gameSyncService.saveGame(game);
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
                gameService.setCurrentGame(null);
                updateUI();
            }
        });

        startGameButton.setOnClickListener(v -> {
            if (gameService.getCurrentGame() != null) {
                gameService.startGame();
                gameSyncService.removeGameEventListener();
                // переходите на новую активность здесь
                updateUI();
            }
        });

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
                    gameService.getCurrentPlayer().setName(s.toString());
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
        Game currentGame = gameService.getCurrentGame();
        if (currentGame != null) {
            if (currentGame.isStarted()) {
                // Обновите UI для начала игры, возможно, переход на новую активность
            } else {
                newGameButton.setVisibility(View.GONE);
                cancelGameButton.setVisibility(View.VISIBLE);
                if (currentGame.getPlayers().size() >= 2 && currentGame.getPlayers().size() <= 4) {
                    startGameButton.setVisibility(View.VISIBLE);
                } else {
                    startGameButton.setVisibility(View.GONE);
                }
            }
            playerNameInput.setEnabled(false);
        } else {
            newGameButton.setVisibility(View.VISIBLE);
            cancelGameButton.setVisibility(View.GONE);
            startGameButton.setVisibility(View.GONE);
            playerNameInput.setEnabled(true);
        }
        gameListTableAdapter.notifyDataSetChanged();
        currentGameTableAdapter.notifyDataSetChanged();
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
