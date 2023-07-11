package com.family.app;

import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;

public class GameViewHolder extends RecyclerView.ViewHolder {
    TextView gameName, playerCount;
    Button connectButton;

    public GameViewHolder(View itemView) {
        super(itemView);
        gameName = itemView.findViewById(R.id.game_name);
        playerCount = itemView.findViewById(R.id.player_count);
        connectButton = itemView.findViewById(R.id.connect_button);
    }
}
