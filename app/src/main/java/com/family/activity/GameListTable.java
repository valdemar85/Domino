package com.family.activity;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.recyclerview.widget.RecyclerView;
import com.family.dto.Game;
import com.family.service.GameService;
import com.family.dto.Player;
import com.family.app.R;

public class GameListTable extends RecyclerView.Adapter<GameViewHolder> {
    private final GameService gameService;
    private String playerName;
    private String playerId;
    private boolean isInGame;  // flag to check if the player is already in a game

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public String getPlayerId() {
        return playerId;
    }

    public boolean isInGame() {
        return isInGame;
    }

    public void setInGame(boolean inGame) {
        isInGame = inGame;
    }



    public GameListTable(GameService gameService, String playerName, String playerId) {
        this.gameService = gameService;
        this.playerName = playerName;
        this.playerId = playerId;
        this.isInGame = false;
    }

    @Override
    public GameViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_game, parent, false);
        return new GameViewHolder(view);
    }

    @Override
    public void onBindViewHolder(GameViewHolder holder, int position) {
        Game game = gameService.getGames().get(position);
        holder.gameName.setText(game.getName());
        holder.playerCount.setText(String.valueOf(game.getPlayers().size()));

        holder.connectButton.setOnClickListener(v -> {
            if (!isInGame && game.getPlayers().size() < 4 && !game.hasPlayer(playerId)) {
                gameService.connectToGame(game.getId(), new Player(playerName, "Optional Player Data", playerId));
                isInGame = true;
                holder.connectButton.setEnabled(false); // disable the button after the player joins the game
            }
        });

        if (game.getPlayers().size() >= 4 || game.hasPlayer(playerId) || isInGame) {
            holder.connectButton.setEnabled(false);
        } else {
            holder.connectButton.setEnabled(true);
        }
    }

    @Override
    public int getItemCount() {
        return gameService.getGames().size();
    }

    public void updatePlayerName(String playerName) {
        this.playerName = playerName;
    }

    // Use this method to update the isInGame flag
    public void updateGameStatus(boolean isInGame) {
        this.isInGame = isInGame;
        notifyDataSetChanged();  // It's necessary to redraw the elements to update the buttons' state
    }

}
