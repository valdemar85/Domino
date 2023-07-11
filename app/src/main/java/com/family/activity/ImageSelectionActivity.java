package com.family.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import com.family.app.MainActivity;
import com.family.app.R;

public class ImageSelectionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.select_images);

        Button doneButton = findViewById(R.id.button_done);
        doneButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Переход обратно к игре после завершения выбора изображений
                Intent intent = new Intent(ImageSelectionActivity.this, MainActivity.class);
                startActivity(intent);
            }
        });
    }
}
