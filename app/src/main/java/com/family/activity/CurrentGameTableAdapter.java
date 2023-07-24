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
        String currentPlayerId = currentPlayer.getId();

        holder.kickButton.setOnClickListener(v -> {
            if ((player.getId().equals(currentPlayerId)) || currentGame.getBossId().equals(currentPlayerId)) {
                currentGame.getPlayers().remove(position);
                if (isEmpty(currentGame.getPlayers())) {
                    gameService.setCurrentGame(null);
                    gameService.removeGame(currentGame.getId());
                    gameSyncService.removeGame(currentGame.getId());
                } else {
                    gameSyncService.saveGame(currentGame);
                }
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
