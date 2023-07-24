package com.family.activity;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.family.app.*;
import com.family.callbacks.GameDataCallback;
import com.family.callbacks.GamesDataCallback;
import com.family.dto.Game;
import com.family.dto.Message;
import com.family.dto.Player;
import com.family.service.GameService;
import com.family.service.GameSyncService;
import com.family.utils.UserUtils;

import java.util.List;
import java.util.concurrent.CountDownLatch;

import static com.family.dto.Message.ALERT;
import static com.family.dto.Message.TEAM_PARTICIPATION_REQUEST;

public class MainActivity extends AppCompatActivity {

    private GameService gameService;
    private final GameSyncService gameSyncService = new GameSyncService();
    final CountDownLatch latch = new CountDownLatch(1);

    private TeamTableAdapter teamTableAdapter;
    private CurrentGameTableAdapter currentGameTableAdapter;
    private RecyclerView teamList;
    private RecyclerView currentGameTable;
    private Button newTeamButton, disbandTeamButton, startGameButton;
    private EditText playerNameInput;
    private TextView errorMessage, currentTeamName;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);
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

        newTeamButton = findViewById(R.id.new_team_button);
        disbandTeamButton = findViewById(R.id.disband_team_button);
        startGameButton = findViewById(R.id.start_game_button);
        playerNameInput = findViewById(R.id.player_name_input);
        errorMessage = findViewById(R.id.error_message);
        currentTeamName = findViewById(R.id.current_team_name);

        teamList = findViewById(R.id.team_list);
        teamList.addItemDecoration(new DividerItemDecoration(this, LinearLayoutManager.VERTICAL));
        teamList.setLayoutManager(new LinearLayoutManager(this));
        teamTableAdapter = new TeamTableAdapter(gameService, gameSyncService);
        teamList.setAdapter(teamTableAdapter);

        currentGameTable = findViewById(R.id.current_game);
        currentGameTable.addItemDecoration(new DividerItemDecoration(this, LinearLayoutManager.VERTICAL));
        currentGameTable.setLayoutManager(new LinearLayoutManager(this));
        currentGameTableAdapter = new CurrentGameTableAdapter(gameService, gameSyncService);
        currentGameTable.setAdapter(currentGameTableAdapter);

        newTeamButton.setOnClickListener(v -> {
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

        disbandTeamButton.setOnClickListener(v -> {
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
                teamList.setVisibility(View.VISIBLE);
                updateUI();
                latch.countDown(); // уменьшаем счетчик на 1
            }

            @Override
            public void onDataNotAvailable(String error) {
                // Показ сообщения об ошибке
                errorMessage.setText("Ошибка загрузки таблицы: " + error);
                errorMessage.setVisibility(View.VISIBLE);
                teamList.setVisibility(View.GONE);
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
                newTeamButton.setVisibility(View.GONE);
                disbandTeamButton.setVisibility(View.VISIBLE);
                if (currentGame.getPlayers().size() >= 2 && currentGame.getPlayers().size() <= 4) {
                    startGameButton.setVisibility(View.VISIBLE);
                } else {
                    startGameButton.setVisibility(View.GONE);
                }
            }
            playerNameInput.setEnabled(false);
            currentTeamName.setText(currentGame.getName());
            currentTeamName.setVisibility(View.VISIBLE);
            precessMessages(currentGame);
        } else {
            newTeamButton.setVisibility(View.VISIBLE);
            disbandTeamButton.setVisibility(View.GONE);
            startGameButton.setVisibility(View.GONE);
            playerNameInput.setEnabled(true);
            currentTeamName.setText("");
            currentTeamName.setVisibility(View.GONE);
        }
        teamTableAdapter.notifyDataSetChanged();
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
                    teamList.setVisibility(View.GONE);
                }
            }, gameService.getCurrentGame().getId());
        }
    }

    private void precessMessages(Game currentGame) {
        List<Message> messages = currentGame.getMessages();
        for (Message message: messages) {
            if (message.getToId().equals(gameService.getCurrentPlayer().getId())) {
                if (message.getMessageType().equals(TEAM_PARTICIPATION_REQUEST)) {
                    alertBossTeamParticipationRequest(message.getFromId(), message.getFromName(), currentGame);
                } else if (message.getMessageType().equals(ALERT)) {
                    processAlertMessage(message.getMessageText());
                }
                messages.remove(message);
                gameSyncService.saveGame(currentGame);
                break;
            }
        }
    }

    private void processAlertMessage(String textMessageToShow) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Уведомление");
        builder.setMessage(textMessageToShow);

        // Устанавливаем обработчик для кнопки "OK"
        builder.setPositiveButton("OK", (dialog, which) -> {
            // Если пользователь нажимает "OK", закрываем диалоговое окно
            dialog.dismiss();
        });

        // Показываем диалоговое окно
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    // Это может быть в вашем Activity или Fragment, где отображается экран лидера команды
    private void alertBossTeamParticipationRequest(String playerId, String playerName, Game game) {
        // Создаем диалоговое окно
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Запрос на присоединение к команде");
        builder.setMessage(playerName + " хочет в вашу команду");

        // Устанавливаем обработчики для кнопок диалогового окна
        builder.setPositiveButton("Одобрить", (dialog, which) -> {
            // Если лидер нажимает "Одобрить", подключаем игрока к команде
            gameService.addPlayerToCurrentGame(new Player(playerName, game.getId(), playerId));
            gameSyncService.saveGame(game);
        });

        builder.setNegativeButton("Отказать", (dialog, which) -> {
            // Если лидер нажимает "Отказать", ничего не делаем
            // отправить игроку уведомление об отказе здесь
//            Player currentPlayer = gameService.getCurrentPlayer();
//            Message message = new Message(currentPlayer.getId(), currentPlayer.getName(), playerId, ALERT);
//            message.setMessageText("Вам отказано");
//            gameService.getCurrentGame().addMessage(message);
//            gameSyncService.saveGame(gameService.getCurrentGame());
        });

        // Устанавливаем таймаут для автоматического отказа
        final AlertDialog dialog = builder.create();
        dialog.show();

        final Handler handler  = new Handler(Looper.getMainLooper());
        final Runnable runnable = () -> {
            if (dialog.isShowing()) {
                dialog.dismiss();
                // send a notification of denial to the player here
                Player currentPlayer = gameService.getCurrentPlayer();
                Message message = new Message(currentPlayer.getId(), currentPlayer.getName(), playerId, ALERT);
                message.setMessageText("Истекло время на одобрение");
                gameService.getCurrentGame().addMessage(message);
                gameSyncService.saveGame(gameService.getCurrentGame());
            }
        };

        dialog.setOnDismissListener(dialogInterface -> handler.removeCallbacks(runnable));

        handler.postDelayed(runnable, 15000);
    }

}
