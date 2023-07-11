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

        public GameAdapter(List<Game> gameList, GameService gameService, String playerName) {
            this.gameList = gameList;
            this.gameService = gameService;
            this.playerName = playerName;
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

            if (game.getPlayers().size() >= 4 || game.hasPlayer(playerName)) {
                holder.connectButton.setEnabled(false);
            } else {
                holder.connectButton.setOnClickListener(v -> {
                    gameService.connectToGame(game.getId(), new Player(playerName, "Optional Player Data"));
                    notifyDataSetChanged();
                });
            }
        }

        @Override
        public int getItemCount() {
            return gameList.size();
        }

        public void updateGames(List<Game> gameList) {
            this.gameList = gameList;
            notifyDataSetChanged();
        }

        public void updatePlayerName(String playerName) {
            this.playerName = playerName;
        }
    }
