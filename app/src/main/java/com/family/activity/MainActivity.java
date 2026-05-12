package com.family.activity;

import android.content.Context;
import android.content.Intent;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.family.app.*;
import com.family.callbacks.GamesDataCallback;
import com.family.dto.Game;
import com.family.dto.GameState;
import com.family.dto.Message;
import com.family.dto.Player;
import com.family.service.DominoLogic;
import com.family.service.GameService;
import com.family.service.GameSyncService;
import com.family.utils.UserUtils;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Iterator;
import java.util.List;

import static com.family.dto.Message.ALERT;
import static com.family.dto.Message.TEAM_PARTICIPATION_REQUEST;

public class MainActivity extends AppCompatActivity {

    private GameService gameService;
    private final GameSyncService gameSyncService = GameSyncService.getInstance();

    private String userId;
    /** Guard so we don't open GameActivity repeatedly on every listener tick. */
    private boolean gameActivityLaunched = false;

    private TeamTableAdapter teamTableAdapter;
    private CurrentGameTableAdapter currentGameTableAdapter;
    private RecyclerView teamList;
    private RecyclerView currentGameTable;
    private MaterialCardView teamListCard;
    private MaterialCardView currentTeamCard;
    private Button newTeamButton, disbandTeamButton, startGameButton;
    private EditText playerNameInput;
    private TextView errorMessage, currentTeamName;

    private final GamesDataCallback gamesCallback = new GamesDataCallback() {
        @Override
        public void onGamesLoaded(List<Game> games) {
            gameService.setGames(games);
            if (errorMessage.getVisibility() == View.VISIBLE
                    && errorMessage.getText().toString().startsWith("Ошибка загрузки таблицы")) {
                errorMessage.setVisibility(View.GONE);
            }
            teamList.setVisibility(View.VISIBLE);
            resolvePlayerState();
        }

        @Override
        public void onDataNotAvailable(String error) {
            errorMessage.setText("Ошибка загрузки таблицы: " + error);
            errorMessage.setVisibility(View.VISIBLE);
            teamList.setVisibility(View.GONE);
        }
    };

    private final GameSyncService.ConnectionCallback connectionCallback = connected -> runOnUiThread(() -> {
        if (!connected) {
            errorMessage.setText("Нет связи с сервером — изменения могут не сохраняться");
            errorMessage.setVisibility(View.VISIBLE);
        } else if (errorMessage.getVisibility() == View.VISIBLE
                && errorMessage.getText().toString().startsWith("Нет связи")) {
            errorMessage.setVisibility(View.GONE);
        }
    });


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);
        Context applicationContext = getApplicationContext();
        gameService = GameService.getInstance(applicationContext);
        userId = UserUtils.resolveUserId(applicationContext);

        newTeamButton = findViewById(R.id.new_team_button);
        disbandTeamButton = findViewById(R.id.disband_team_button);
        startGameButton = findViewById(R.id.start_game_button);
        playerNameInput = findViewById(R.id.player_name_input);
        errorMessage = findViewById(R.id.error_message);
        currentTeamName = findViewById(R.id.current_team_name);
        teamListCard = findViewById(R.id.team_list_card);
        currentTeamCard = findViewById(R.id.current_team_card);

        teamList = findViewById(R.id.team_list);
        teamList.setLayoutManager(new LinearLayoutManager(this));
        teamTableAdapter = new TeamTableAdapter(gameService, gameSyncService);
        teamList.setAdapter(teamTableAdapter);

        currentGameTable = findViewById(R.id.current_game);
        currentGameTable.setLayoutManager(new LinearLayoutManager(this));
        currentGameTableAdapter = new CurrentGameTableAdapter(gameService, gameSyncService);
        currentGameTable.setAdapter(currentGameTableAdapter);

        newTeamButton.setOnClickListener(v -> {
            if (gameService.getCurrentPlayer() == null) return;
            Game game = gameService.createGame();
            gameSyncService.saveGame(game, error -> runOnUiThread(() -> {
                errorMessage.setText("Не удалось сохранить команду: " + error);
                errorMessage.setVisibility(View.VISIBLE);
            }));
            updateUI();
        });

        disbandTeamButton.setOnClickListener(v -> {
            Game current = gameService.getCurrentGame();
            if (current != null) {
                gameSyncService.removeGame(current.getId());
                gameService.removeGame(current.getId());
                updateUI();
            }
        });

        startGameButton.setOnClickListener(v -> {
            Game current = gameService.getCurrentGame();
            if (current == null) return;
            if (!gameService.startGame()) return;
            // Deal a fresh round. Boss is the source of truth for the initial shuffle —
            // other players will receive it via the games listener and just render it.
            java.util.List<String> playerIds = new java.util.ArrayList<>();
            for (Player p : current.getPlayers()) playerIds.add(p.getId());
            GameState state = DominoLogic.newRound(playerIds, current.getState());
            current.setState(state);
            gameSyncService.saveGame(current);
            updateUI();
        });

        playerNameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // No operation needed here
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String text = s.toString();
                if (text.trim().isEmpty()) {
                    playerNameInput.setError("Введите имя");
                } else {
                    Player current = gameService.getCurrentPlayer();
                    if (current != null) {
                        current.setName(text);
                    }
                    UserUtils.saveName(getApplicationContext(), text);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                // No operation needed here
            }
        });
    }

    private void resolvePlayerState() {
        if (userId == null) return; // defensive — userId is resolved synchronously in onCreate
        Player currentPlayer = gameService.getCurrentPlayer();

        if (currentPlayer == null) {
            // First-time initialization. If a game in the list already has us as a player
            // (created by us before app restart, or we've been added to someone's team
            // earlier), restore identity from there. Otherwise create a fresh player.
            Game existingGame = findMyGameInList(userId);
            if (existingGame != null) {
                Player playerInGame = existingGame.getPlayerById(userId);
                gameService.setCurrentPlayer(playerInGame);
                gameService.setCurrentGame(existingGame);
                playerNameInput.setText(playerInGame.getName());
            } else {
                String name = UserUtils.getSavedName(getApplicationContext());
                if (name == null || name.trim().isEmpty()) {
                    name = UserUtils.generateDefaultName();
                    UserUtils.saveName(getApplicationContext(), name);
                }
                Player newPlayer = new Player(name, userId);
                gameService.setCurrentPlayer(newPlayer);
                playerNameInput.setText(name);
            }
        } else if (gameService.getCurrentGame() == null) {
            // Already initialized, no team — pick up the team if a boss just approved us
            Game existingGame = findMyGameInList(userId);
            if (existingGame != null) {
                gameService.setCurrentGame(existingGame);
            }
        }

        updateUI();
    }

    private Game findMyGameInList(String playerId) {
        // Find ANY game (started or open) I'm part of — started games still belong
        // to their players; MainActivity needs to know to launch the game screen.
        for (Game g : gameService.getGames()) {
            if (g.hasPlayer(playerId)) {
                return g;
            }
        }
        return null;
    }

    private void updateUI() {
        Game currentGame = gameService.getCurrentGame();
        Player currentPlayer = gameService.getCurrentPlayer();
        // If our team's round has started, hand off to GameActivity.
        // The guard prevents repeated launches every time the listener ticks.
        if (currentGame != null && currentGame.isStarted() && !gameActivityLaunched) {
            gameActivityLaunched = true;
            Intent i = new Intent(this, GameActivity.class);
            i.putExtra(GameActivity.EXTRA_GAME_ID, currentGame.getId());
            startActivity(i);
            return;
        }
        if (currentGame != null) {
            // In a team — show the current-team card, hide the open-teams list.
            // The list is useless to a player who's already committed: every "Вступить"
            // button there is disabled anyway, so the section just adds clutter.
            teamListCard.setVisibility(View.GONE);
            currentTeamCard.setVisibility(View.VISIBLE);

            // Only the boss (team creator) gets disband / start-game controls.
            // Guests can only leave their own row from the players list.
            boolean iAmBoss = currentPlayer != null
                    && currentGame.getBossId() != null
                    && currentGame.getBossId().equals(currentPlayer.getId());
            if (currentGame.isStarted()) {
                // Обновите UI для начала игры, возможно, переход на новую активность
            } else {
                newTeamButton.setVisibility(View.GONE);
                if (iAmBoss) {
                    disbandTeamButton.setVisibility(View.VISIBLE);
                    int playerCount = currentGame.getPlayers().size();
                    startGameButton.setVisibility(
                            playerCount >= 2 && playerCount <= 4 ? View.VISIBLE : View.GONE);
                } else {
                    disbandTeamButton.setVisibility(View.GONE);
                    startGameButton.setVisibility(View.GONE);
                }
            }
            playerNameInput.setEnabled(false);
            currentTeamName.setText(currentGame.getName());
            precessMessages(currentGame);
        } else {
            teamListCard.setVisibility(View.VISIBLE);
            currentTeamCard.setVisibility(View.GONE);

            newTeamButton.setVisibility(View.VISIBLE);
            disbandTeamButton.setVisibility(View.GONE);
            startGameButton.setVisibility(View.GONE);
            playerNameInput.setEnabled(true);
        }
        teamTableAdapter.notifyDataSetChanged();
        currentGameTableAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onPause() {
        super.onPause();
        gameSyncService.unregisterGamesListener(gamesCallback);
        gameSyncService.unregisterConnectionCallback(connectionCallback);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reset on return from GameActivity. If the round is still in progress, the
        // first listener tick will re-launch GameActivity; if it's over, we stay in lobby.
        gameActivityLaunched = false;
        // Persistent listeners live in GameSyncService for the lifetime of the process.
        // Subscribing here just hooks our callbacks into the in-memory cache and
        // delivers the latest state synchronously — no fresh Firebase round trip.
        gameSyncService.registerGamesListener(gamesCallback);
        gameSyncService.registerConnectionCallback(connectionCallback);
    }

    private void precessMessages(Game currentGame) {
        List<Message> messages = currentGame.getMessages();
        Iterator<Message> it = messages.iterator();
        while (it.hasNext()) {
            Message message = it.next();
            if (message.getToId().equals(gameService.getCurrentPlayer().getId())) {
                if (message.getMessageType().equals(TEAM_PARTICIPATION_REQUEST)) {
                    alertBossTeamParticipationRequest(message.getFromId(), message.getFromName(), currentGame);
                } else if (message.getMessageType().equals(ALERT)) {
                    processAlertMessage(message.getMessageText());
                }
                it.remove();
                gameSyncService.saveGame(currentGame);
                break;
            }
        }
    }

    private void processAlertMessage(String textMessageToShow) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Уведомление")
                .setMessage(textMessageToShow)
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void sendAlertToPlayer(String targetPlayerId, String text) {
        Player currentPlayer = gameService.getCurrentPlayer();
        Game currentGame = gameService.getCurrentGame();
        if (currentPlayer == null || currentGame == null) return;
        Message message = new Message(currentPlayer.getId(), currentPlayer.getName(), targetPlayerId, ALERT);
        message.setMessageText(text);
        currentGame.addMessage(message);
        gameSyncService.saveGame(currentGame);
    }

    private void alertBossTeamParticipationRequest(String playerId, String playerName, Game game) {
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Запрос на участие")
                .setMessage(playerName + " хочет вступить в вашу команду")
                .setPositiveButton("Одобрить", (d, which) -> {
                    // Resolve the freshest current-game reference at click time.
                    // The `game` argument was captured when the dialog was shown and may
                    // be stale — setGames() can have refreshed gameService.currentGame
                    // between dialog show and tap. Saving the stale `game` would push a
                    // version of the team WITHOUT the new player back to Firebase.
                    Game current = gameService.getCurrentGame();
                    if (current == null) return;
                    if (gameService.addPlayerToCurrentGame(new Player(playerName, playerId))) {
                        gameSyncService.saveGame(current);
                    }
                })
                .setNegativeButton("Отказать", (d, which) -> {
                    sendAlertToPlayer(playerId, "Босс отклонил ваш запрос на участие");
                })
                .create();
        dialog.show();

        // Auto-deny after 15s if the boss doesn't react — let the guest know they timed out.
        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable runnable = () -> {
            if (dialog.isShowing()) {
                dialog.dismiss();
                sendAlertToPlayer(playerId, "Истекло время на одобрение");
            }
        };
        dialog.setOnDismissListener(d -> handler.removeCallbacks(runnable));
        handler.postDelayed(runnable, 15000);
    }

}
