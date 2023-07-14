package com.family.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class GameAdapter extends RecyclerView.Adapter<GameViewHolder> {
    private List<Game> gameList;
    private GameService gameService;
    private String playerName;
    private boolean isInGame;  // flag to check if the player is already in a game

    public GameAdapter(List<Game> gameList, GameService gameService, String playerName) {
        this.gameList = gameList;
        this.gameService = gameService;
        this.playerName = playerName;
        this.isInGame = false;
    }

    @Override
    public GameViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_game, parent, false);
        return new GameViewHolder(view);
    }

    @Override
    public void onBindViewHolder(GameViewHolder holder, int position) {
        Game game = gameList.get(position);
        holder.gameName.setText(game.getName());
        holder.playerCount.setText(String.valueOf(game.getPlayers().size()));

        holder.connectButton.setOnClickListener(v -> {
            if (!isInGame && game.getPlayers().size() < 4 && !game.hasPlayer(playerName)) {
                gameService.connectToGame(game.getId(), new Player(playerName, "Optional Player Data"));
                isInGame = true;
                holder.connectButton.setEnabled(false); // disable the button after the player joins the game
            }
        });

        if (game.getPlayers().size() >= 4 || game.hasPlayer(playerName) || isInGame) {
            holder.connectButton.setEnabled(false);
        } else {
            holder.connectButton.setEnabled(true);
        }
    }

    @Override
    public int getItemCount() {
        return gameList.size();
    }

    public void addGame(Game game) {
        this.gameList.add(game);
        notifyItemInserted(gameList.size() - 1);
    }

    public void updateGames(List<Game> gameList) {
        this.gameList = gameList;
        notifyDataSetChanged();
    }

    public void updateAllGames() {
        notifyDataSetChanged();
    }

    public void updateGame(Game updatedGame) {
        for (int i = 0; i < gameList.size(); i++) {
            Game game = gameList.get(i);
            if (game.getId().equals(updatedGame.getId())) {
                gameList.set(i, updatedGame);
                notifyItemChanged(i);
                break;
            }
        }
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
