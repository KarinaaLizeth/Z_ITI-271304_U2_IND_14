package com.Z_ITI_271234_U2_IND_14;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.*;
import com.google.firebase.database.*;
import com.google.maps.android.PolyUtil;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

public class ConductorActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private DatabaseReference databaseRef;
    private RequestQueue requestQueue;
    private final LatLng UPV_LOCATION = new LatLng(23.7284197, -99.0769976);
    private static final String DIRECTIONS_API_KEY = "AIzaSyASsRlrfxIYtGkjBcB586T8waOJpc-biP0";
    private List<Pasajero> pasajeros = new ArrayList<>();
    private LatLng conductorLocation = new LatLng(23.7537602, -99.1635846); // Ubicación inicial del conductor
    private List<LatLng> rutaOptima = new ArrayList<>();

    // Clase para almacenar información de los pasajeros
    private static class Pasajero {
        LatLng ubicacion;
        String id;
        double distanciaAlConductor;

        Pasajero(String id, LatLng ubicacion, double distanciaAlConductor) {
            this.id = id;
            this.ubicacion = ubicacion;
            this.distanciaAlConductor = distanciaAlConductor;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conductor);

        requestQueue = Volley.newRequestQueue(this);
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        databaseRef = database.getReference("pasajeros");

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map_conductor);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.addMarker(new MarkerOptions()
                .position(UPV_LOCATION)
                .title("Universidad Politécnica de Victoria")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));

        mMap.addMarker(new MarkerOptions()
                .position(conductorLocation)
                .title("Mi ubicación")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));

        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(conductorLocation, 13));
        cargarUbicacionesPasajeros();
    }

    private void cargarUbicacionesPasajeros() {
        databaseRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                pasajeros.clear();
                mMap.clear();

                // Restaurar marcadores fijos
                mMap.addMarker(new MarkerOptions()
                        .position(UPV_LOCATION)
                        .title("Universidad Politécnica de Victoria")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));

                mMap.addMarker(new MarkerOptions()
                        .position(conductorLocation)
                        .title("Mi ubicación")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));

                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    try {
                        Double lat = snapshot.child("lat").getValue(Double.class);
                        Double lng = snapshot.child("lng").getValue(Double.class);

                        if (lat != null && lng != null) {
                            LatLng ubicacionPasajero = new LatLng(lat, lng);
                            double distancia = calcularDistancia(conductorLocation, ubicacionPasajero);
                            pasajeros.add(new Pasajero(snapshot.getKey(), ubicacionPasajero, distancia));

                            mMap.addMarker(new MarkerOptions()
                                    .position(ubicacionPasajero)
                                    .title("Pasajero")
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
                        }
                    } catch (Exception e) {
                        Log.e("Firebase", "Error al procesar ubicación: " + e.getMessage());
                    }
                }

                if (!pasajeros.isEmpty()) {
                    calcularRutaOptima();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e("Firebase", "Error en la lectura: " + databaseError.getMessage());
            }
        });
    }

    private double calcularDistancia(LatLng punto1, LatLng punto2) {
        double radioTierra = 6371; // Radio de la Tierra en kilómetros
        double dLat = Math.toRadians(punto2.latitude - punto1.latitude);
        double dLng = Math.toRadians(punto2.longitude - punto1.longitude);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(Math.toRadians(punto1.latitude)) * Math.cos(Math.toRadians(punto2.latitude)) *
                        Math.sin(dLng/2) * Math.sin(dLng/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return radioTierra * c;
    }

    private void calcularRutaOptima() {
        rutaOptima.clear();
        rutaOptima.add(conductorLocation);

        List<Pasajero> pasajerosRestantes = new ArrayList<>(pasajeros);
        LatLng ubicacionActual = conductorLocation;

        // Algoritmo del vecino más cercano
        while (!pasajerosRestantes.isEmpty()) {
            Pasajero pasajeroMasCercano = null;
            double distanciaMinima = Double.MAX_VALUE;

            for (Pasajero pasajero : pasajerosRestantes) {
                double distancia = calcularDistancia(ubicacionActual, pasajero.ubicacion);
                if (distancia < distanciaMinima) {
                    distanciaMinima = distancia;
                    pasajeroMasCercano = pasajero;
                }
            }

            if (pasajeroMasCercano != null) {
                rutaOptima.add(pasajeroMasCercano.ubicacion);
                ubicacionActual = pasajeroMasCercano.ubicacion;
                pasajerosRestantes.remove(pasajeroMasCercano);
            }
        }

        rutaOptima.add(UPV_LOCATION);
        solicitarRutaOptimizada();
    }

    private void solicitarRutaOptimizada() {
        StringBuilder waypoints = new StringBuilder();
        for (int i = 1; i < rutaOptima.size() - 1; i++) {
            if (i > 1) waypoints.append("|");
            waypoints.append(rutaOptima.get(i).latitude).append(",").append(rutaOptima.get(i).longitude);
        }

        String url = "https://maps.googleapis.com/maps/api/directions/json?" +
                "origin=" + rutaOptima.get(0).latitude + "," + rutaOptima.get(0).longitude +
                "&destination=" + UPV_LOCATION.latitude + "," + UPV_LOCATION.longitude +
                "&waypoints=optimize:true|" + waypoints.toString() +
                "&key=" + DIRECTIONS_API_KEY;

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            JSONArray routes = response.getJSONArray("routes");
                            if (routes.length() > 0) {
                                JSONObject route = routes.getJSONObject(0);
                                String polyline = route.getJSONObject("overview_polyline").getString("points");
                                List<LatLng> decodedPath = PolyUtil.decode(polyline);

                                // Limpiar el mapa antes de dibujar la nueva ruta
                                mMap.clear();

                                // Restaurar marcador de la UPV
                                mMap.addMarker(new MarkerOptions()
                                        .position(UPV_LOCATION)
                                        .title("Universidad Politécnica de Victoria")
                                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));

                                // Restaurar marcador del conductor
                                mMap.addMarker(new MarkerOptions()
                                        .position(conductorLocation)
                                        .title("Mi ubicación")
                                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));

                                // Añadir marcadores numerados para los pasajeros
                                for (int i = 1; i < rutaOptima.size() - 1; i++) {
                                    MarkerOptions markerOptions = new MarkerOptions()
                                            .position(rutaOptima.get(i))
                                            .title("Pasajero " + i)
                                            .snippet("Parada #" + i)
                                            .icon(BitmapDescriptorFactory.fromBitmap(
                                                    createNumberedMarkerBitmap(i)))
                                            .anchor(0.5f, 0.5f);
                                    mMap.addMarker(markerOptions);
                                }

                                // Dibujar la ruta optimizada
                                PolylineOptions polylineOptions = new PolylineOptions()
                                        .addAll(decodedPath)
                                        .color(Color.BLUE)
                                        .width(10);
                                mMap.addPolyline(polylineOptions);

                                // ... (resto del código para mostrar distancia y tiempo se mantiene igual)
                            }
                        } catch (Exception e) {
                            Log.e("Directions", "Error al procesar respuesta: " + e.getMessage());
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e("Directions", "Error: " + error.toString());
                    }
                });

        requestQueue.add(request);
    }
    private Bitmap createNumberedMarkerBitmap(int number) {
        Paint paint = new Paint();
        paint.setTextSize(40);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.DEFAULT_BOLD);

        Paint backgroundPaint = new Paint();
        backgroundPaint.setColor(Color.RED);

        int width = 80;
        int height = 80;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // Dibujar círculo rojo
        canvas.drawCircle(width/2, height/2, width/2, backgroundPaint);

        // Dibujar número
        canvas.drawText(String.valueOf(number), width/2, height/2 + 15, paint);

        return bitmap;
    }
}
