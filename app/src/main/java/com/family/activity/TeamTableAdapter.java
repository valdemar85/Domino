package com.family.activity;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.recyclerview.widget.RecyclerView;
import com.family.dto.Game;
import com.family.service.GameService;
import com.family.dto.Player;
import com.family.app.R;

public class TeamTableAdapter extends RecyclerView.Adapter<TeamViewHolder> {
    private final GameService gameService;

    public TeamTableAdapter(GameService gameService) {
        this.gameService = gameService;
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
        holder.playerCount.setText(String.valueOf(game.getPlayers().size()));
        Player currentPlayer = gameService.getCurrentPlayer();
        String currentPlayerId = currentPlayer.getId();

        holder.connectButton.setOnClickListener(v -> {
            if ((gameService.getCurrentGame() == null) && game.getPlayers().size() < 4 && !game.hasPlayer(currentPlayerId)) {
                gameService.connectToGame(game.getId(), new Player(currentPlayer.getName(), game.getId(), currentPlayerId));
                holder.connectButton.setEnabled(false); // disable the button after the player joins the game
            }
        });

        if (game.getPlayers().size() >= 4 || game.hasPlayer(currentPlayerId) || (gameService.getCurrentGame() != null)) {
            holder.connectButton.setEnabled(false);
        } else {
            holder.connectButton.setEnabled(true);
        }
    }

    @Override
    public int getItemCount() {
        return gameService.getGames().size();
    }

}
