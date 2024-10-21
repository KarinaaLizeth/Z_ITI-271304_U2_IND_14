package com.Z_ITI_271234_U2_IND_14;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnConductor = findViewById(R.id.btnConductor);
        Button btnPasajero = findViewById(R.id.btnPasajero);

        // Configura el botón de Conductor
        btnConductor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Redirige a la actividad del conductor
                Intent intent = new Intent(MainActivity.this, ConductorActivity.class);
                startActivity(intent);
            }
        });

        // Configura el botón de Pasajero
        btnPasajero.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Redirige a la actividad del pasajero
                Intent intent = new Intent(MainActivity.this, PasajeroActivity.class);
                startActivity(intent);
            }
        });
    }
}