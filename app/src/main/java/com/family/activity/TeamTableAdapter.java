package com.family.activity;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.recyclerview.widget.RecyclerView;
import com.family.dto.Game;
import com.family.dto.Message;
import com.family.service.GameService;
import com.family.dto.Player;
import com.family.app.R;
import com.family.service.GameSyncService;
import com.google.android.material.snackbar.Snackbar;

import static com.family.dto.Message.TEAM_PARTICIPATION_REQUEST;

public class TeamTableAdapter extends RecyclerView.Adapter<TeamViewHolder> {
    private final GameService gameService;
    private final GameSyncService gameSyncService;

    public TeamTableAdapter(GameService gameService,
                            GameSyncService gameSyncService) {
        this.gameService = gameService;
        this.gameSyncService = gameSyncService;
    }

    @Override
    public TeamViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.team_activity, parent, false);
        return new TeamViewHolder(view);
    }

    @Override
    public void onBindViewHolder(TeamViewHolder holder, int position) {
        Game game = gameService.getGames().get(position);
        holder.gameName.setText(game.getName());
        int playerCount = game.getPlayers().size();
        holder.playerCount.setText(playerCount + " / 4 игроков");

        Player currentPlayer = gameService.getCurrentPlayer();
        if (currentPlayer == null) {
            holder.connectButton.setEnabled(false);
            holder.connectButton.setOnClickListener(null);
            return;
        }
        String currentPlayerId = currentPlayer.getId();

        holder.connectButton.setOnClickListener(v -> {
            if ((gameService.getCurrentGame() == null) && game.getPlayers().size() < 4 && !game.hasPlayer(currentPlayerId)) {
                Message message = new Message(currentPlayerId, currentPlayer.getName(), game.getBossId(), TEAM_PARTICIPATION_REQUEST);
                game.addMessage(message);
                gameSyncService.saveGame(game);
                holder.connectButton.setEnabled(false); // disable the button after the player joins the game
                Snackbar.make(v, "Запрос отправлен. Ждите ответа босса.", Snackbar.LENGTH_SHORT).show();
            }
        });

        boolean canConnect = playerCount < 4
                && !game.hasPlayer(currentPlayerId)
                && gameService.getCurrentGame() == null;
        holder.connectButton.setEnabled(canConnect);
    }

    @Override
    public int getItemCount() {
        return gameService.getGames().size();
    }

}
