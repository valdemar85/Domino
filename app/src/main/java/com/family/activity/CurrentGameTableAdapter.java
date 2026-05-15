package com.family.activity;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.family.app.R;
import com.family.dto.Game;
import com.family.dto.Player;
import com.family.service.GameService;
import com.family.service.GameSyncService;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import static com.google.android.gms.common.util.CollectionUtils.isEmpty;

public class CurrentGameTableAdapter extends RecyclerView.Adapter<CurrentGameViewHolder> {
    private final GameService gameService;
    private final GameSyncService gameSyncService;

    public CurrentGameTableAdapter(GameService gameService,
                                   GameSyncService gameSyncService) {
        this.gameService = gameService;
        this.gameSyncService = gameSyncService;
    }

    @Override
    public CurrentGameViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.current_game_activity, parent, false);
        return new CurrentGameViewHolder(view);
    }

    @Override
    public void onBindViewHolder(CurrentGameViewHolder holder, int position) {
        Game currentGame = gameService.getCurrentGame();
        if (currentGame == null) {
            return;
        }
        Player player = currentGame.getPlayers().get(position);
        holder.playerName.setText(player.getName());

        Player currentPlayer = gameService.getCurrentPlayer();
        if (currentPlayer == null) {
            holder.kickButton.setVisibility(View.GONE);
            holder.kickButton.setOnClickListener(null);
            return;
        }
        String currentPlayerId = currentPlayer.getId();
        String bossId = currentGame.getBossId();
        boolean isMe = player.getId().equals(currentPlayerId);
        boolean iAmBoss = bossId != null && bossId.equals(currentPlayerId);

        // Visibility rules:
        //  - My row, I am NOT the boss → "Выйти" (I leave the team) — neutral outline
        //  - Someone else's row, I AM the boss → "Выгнать" (boss kicks a guest) — red outline
        //  - Otherwise hide (boss's own row, or guest viewing other guests)
        Context ctx = holder.itemView.getContext();
        if (isMe && !iAmBoss) {
            holder.kickButton.setVisibility(View.VISIBLE);
            holder.kickButton.setText("Выйти");
            int neutralColor = ContextCompat.getColor(ctx, R.color.domino_primary);
            holder.kickButton.setStrokeColor(ColorStateList.valueOf(neutralColor));
            holder.kickButton.setTextColor(neutralColor);
        } else if (!isMe && iAmBoss) {
            holder.kickButton.setVisibility(View.VISIBLE);
            holder.kickButton.setText("Выгнать");
            int destructiveColor = ContextCompat.getColor(ctx, R.color.domino_error);
            holder.kickButton.setStrokeColor(ColorStateList.valueOf(destructiveColor));
            holder.kickButton.setTextColor(destructiveColor);
        } else {
            holder.kickButton.setVisibility(View.GONE);
            holder.kickButton.setOnClickListener(null);
            return;
        }

        // Both "Выйти" and "Выгнать" are destructive and irreversible — once you
        // leave/are kicked, the team list shows you nothing to rejoin. A confirm
        // step prevents the common pattern of an accidental tap removing a player
        // mid-conversation. Final removal logic is the same for both flows.
        boolean leavingMyself = isMe;
        String title = leavingMyself ? "Выйти из команды?" : "Выгнать игрока?";
        String message = leavingMyself
                ? "После выхода вернуться в эту команду можно будет только по новому одобрению босса."
                : "Игрок " + player.getName() + " будет удалён из команды.";
        String confirm = leavingMyself ? "Выйти" : "Выгнать";
        holder.kickButton.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(holder.itemView.getContext())
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton(confirm, (d, w) -> performRemoval(holder))
                    .setNegativeButton("Отмена", null)
                    .show();
        });
    }

    private void performRemoval(CurrentGameViewHolder holder) {
        Game game = gameService.getCurrentGame();
        if (game == null) return;
        int pos = holder.getAdapterPosition();
        if (pos == RecyclerView.NO_POSITION || pos >= game.getPlayers().size()) return;
        game.getPlayers().remove(pos);
        if (isEmpty(game.getPlayers())) {
            gameService.removeGame(game.getId());
            gameSyncService.removeGame(game.getId());
        } else {
            gameSyncService.saveGame(game);
        }
    }

    @Override
    public int getItemCount() {
        if ((gameService.getCurrentGame() != null) && (!isEmpty(gameService.getCurrentGame().getPlayers()))) {
            return gameService.getCurrentGame().getPlayers().size();
        } else {
            return 0;
        }
    }

}
