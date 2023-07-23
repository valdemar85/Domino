package com.family.activity;

import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import com.family.app.R;

public class CurrentGameViewHolder extends RecyclerView.ViewHolder {
    TextView playerName;
    Button kickButton;

    public CurrentGameViewHolder(View itemView) {
        super(itemView);
        playerName = itemView.findViewById(R.id.player_name);
        kickButton = itemView.findViewById(R.id.kick_button);
    }
}
