package com.family.activity;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.recyclerview.widget.RecyclerView;
import com.family.app.R;
import com.family.dto.Game;
import com.family.dto.Player;
import com.family.service.GameService;
import com.family.service.GameSyncService;

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
        //  - My row, I am NOT the boss → "Выйти" (I leave the team)
        //  - Someone else's row, I AM the boss → "Выгнать" (boss kicks a guest)
        //    (boss can never appear here as "someone else" since there is one boss
        //     and isMe is true for the boss's own row)
        //  - Otherwise hide (boss's own row, or guest viewing other guests)
        if (isMe && !iAmBoss) {
            holder.kickButton.setVisibility(View.VISIBLE);
            holder.kickButton.setText("Выйти");
        } else if (!isMe && iAmBoss) {
            holder.kickButton.setVisibility(View.VISIBLE);
            holder.kickButton.setText("Выгнать");
        } else {
            holder.kickButton.setVisibility(View.GONE);
            holder.kickButton.setOnClickListener(null);
            return;
        }

        holder.kickButton.setOnClickListener(v -> {
            // Resolve everything fresh at click time — currentGame ref may have been
            // refreshed by setGames() between bind and click.
            Game game = gameService.getCurrentGame();
            if (game == null) return;
            int pos = holder.getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION || pos >= game.getPlayers().size()) return;
            game.getPlayers().remove(pos);
            if (isEmpty(game.getPlayers())) {
                // Edge case — should not happen since boss is always present, but be safe
                gameService.removeGame(game.getId());
                gameSyncService.removeGame(game.getId());
            } else {
                gameSyncService.saveGame(game);
            }
        });
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
