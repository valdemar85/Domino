package com.family.activity;

import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import com.family.app.R;

public class TeamViewHolder extends RecyclerView.ViewHolder {
    TextView gameName, playerCount;
    Button connectButton;

    public TeamViewHolder(View itemView) {
        super(itemView);
        gameName = itemView.findViewById(R.id.game_name);
        playerCount = itemView.findViewById(R.id.player_count);
        connectButton = itemView.findViewById(R.id.connect_button);
    }
}
