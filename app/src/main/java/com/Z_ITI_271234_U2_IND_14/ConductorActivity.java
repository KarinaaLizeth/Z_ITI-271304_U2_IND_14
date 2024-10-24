package com.Z_ITI_271234_U2_IND_14;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.RadioGroup;
import android.widget.TextView;
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
    private LatLng conductorLocation = new LatLng(23.743447, -99.158831);
    private List<LatLng> rutaOptima = new ArrayList<>();

    private List<RutaInfo> rutasAlternativas = new ArrayList<>();

    private static class Pasajero {
        LatLng ubicacion;
        String id;
        String nombreCorto; // Nuevo campo
        double distanciaAlConductor;

        Pasajero(String id, LatLng ubicacion, double distanciaAlConductor) {
            this.id = id;
            this.ubicacion = ubicacion;
            this.distanciaAlConductor = distanciaAlConductor;
            this.nombreCorto = "Pasajero " + (id.length() > 4 ? Character.toUpperCase(id.charAt(3)) : "?");
        }
    }

    private static class RutaInfo {
        List<LatLng> puntos;
        List<LatLng> ordenPuntos; // Nuevo campo para el orden de recogida
        String distancia;
        String duracion;

        RutaInfo(List<LatLng> puntos, String distancia, String duracion) {
            this.puntos = puntos;
            this.distancia = distancia;
            this.duracion = duracion;
            this.ordenPuntos = new ArrayList<>(puntos);
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

        RadioGroup rutasRadioGroup = findViewById(R.id.rutasRadioGroup);
        rutasRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (rutasAlternativas.size() <= 1) {
                Toast.makeText(this, "No hay rutas alternativas disponibles", Toast.LENGTH_SHORT).show();
                return;
            }

            if (checkedId == R.id.rutaPrincipalRadio) {
                mostrarRutaEnMapa(0);
            } else if (checkedId == R.id.rutaAlternativa1Radio) {
                mostrarRutaEnMapa(1);
            } else if (checkedId == R.id.rutaAlternativa2Radio) {
                mostrarRutaEnMapa(2);
            }
        });

    }

    private void mostrarRutaEnMapa(int rutaIndex) {
        mMap.clear();

        mMap.addMarker(new MarkerOptions()
                .position(UPV_LOCATION)
                .title("Universidad Politécnica de Victoria")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));

        mMap.addMarker(new MarkerOptions()
                .position(conductorLocation)
                .title("Mi ubicación")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));

        char letra = 'A';
        for (Pasajero pasajero : pasajeros) {
            mMap.addMarker(new MarkerOptions()
                    .position(pasajero.ubicacion)
                    .title(pasajero.nombreCorto)
                    .icon(createCustomMarker(String.valueOf(letra))));
            letra++;
        }

        TextView ordenPasajerosTextView = findViewById(R.id.ordenPasajerosTextView);

        if (rutaIndex < rutasAlternativas.size()) {
            RutaInfo rutaInfo = rutasAlternativas.get(rutaIndex);

            PolylineOptions polylineOptions = new PolylineOptions()
                    .addAll(rutaInfo.puntos)
                    .color(getColorForRoute(rutaIndex))
                    .width(10);
            mMap.addPolyline(polylineOptions);

            StringBuilder ordenTexto = new StringBuilder("Orden de recogida: ");
            for (LatLng punto : rutaInfo.ordenPuntos) {
                if (punto.equals(conductorLocation) || punto.equals(UPV_LOCATION)) {
                    continue;
                }
                for (Pasajero p : pasajeros) {
                    if (Math.abs(p.ubicacion.latitude - punto.latitude) < 0.0001 &&
                            Math.abs(p.ubicacion.longitude - punto.longitude) < 0.0001) {
                        ordenTexto.append(p.nombreCorto).append(" → ");
                        break;
                    }
                }
            }
            ordenTexto.append("UPV");
            ordenPasajerosTextView.setText(ordenTexto.toString());

            String mensaje = String.format("Distancia: %s | Tiempo estimado: %s",
                    rutaInfo.distancia, rutaInfo.duracion);
            Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show();
        }
    }

    private int getColorForRoute(int rutaIndex) {
        switch (rutaIndex) {
            case 0:
                return Color.BLUE;  // Ruta principal (Google Maps)
            case 1:
                return Color.RED;   // Primera ruta alternativa
            case 2:
                return Color.GREEN; // Segunda ruta alternativa
            default:
                return Color.GRAY;
        }
    }

    private void solicitarRutaOptimizada() {
        List<List<LatLng>> rutasLocales = generarRutasLocales();

        StringBuilder waypoints = new StringBuilder();
        for (int i = 1; i < rutaOptima.size() - 1; i++) {
            if (i > 1) waypoints.append("|");
            waypoints.append(rutaOptima.get(i).latitude).append(",").append(rutaOptima.get(i).longitude);
        }
        String url = "https://maps.googleapis.com/maps/api/directions/json?" +
                "origin=" + rutaOptima.get(0).latitude + "," + rutaOptima.get(0).longitude +
                "&destination=" + UPV_LOCATION.latitude + "," + UPV_LOCATION.longitude +
                "&waypoints=optimize:true|" + waypoints.toString() +
                "&alternatives=true" +
                "&key=" + DIRECTIONS_API_KEY;

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        rutasAlternativas.clear();

                        JSONArray routes = response.getJSONArray("routes");
                        JSONObject route = routes.getJSONObject(0);

                        JSONArray waypointOrder = route.getJSONArray("waypoint_order");
                        List<LatLng> puntosOrdenados = new ArrayList<>();

                        puntosOrdenados.add(conductorLocation);

                        List<LatLng> waypointsList = new ArrayList<>();
                        for (int i = 1; i < rutaOptima.size() - 1; i++) {
                            waypointsList.add(rutaOptima.get(i));
                        }

                        for (int i = 0; i < waypointOrder.length(); i++) {
                            int index = waypointOrder.getInt(i);
                            puntosOrdenados.add(waypointsList.get(index));
                        }

                        // Agregar punto final (UPV)
                        puntosOrdenados.add(UPV_LOCATION);

                        // Calcular distancia y duración total
                        JSONArray legs = route.getJSONArray("legs");
                        double distanciaTotal = 0;
                        long duracionTotal = 0;
                        for (int j = 0; j < legs.length(); j++) {
                            JSONObject leg = legs.getJSONObject(j);
                            distanciaTotal += leg.getJSONObject("distance").getDouble("value");
                            duracionTotal += leg.getJSONObject("duration").getLong("value");
                        }

                        String distanciaStr = String.format("%.1f km", distanciaTotal / 1000);
                        String duracionStr = formatearDuracion(duracionTotal);

                        // Decodificar la polyline para la ruta visual
                        String polyline = route.getJSONObject("overview_polyline").getString("points");
                        List<LatLng> decodedPath = PolyUtil.decode(polyline);

                        // Crear RutaInfo con la ruta visual pero mantener el orden de los puntos
                        RutaInfo rutaApi = new RutaInfo(decodedPath, distanciaStr, duracionStr);
                        rutaApi.ordenPuntos = puntosOrdenados; // Agregar esta propiedad a la clase RutaInfo
                        rutasAlternativas.add(rutaApi);

                        // Agregar rutas locales alternativas
                        for (List<LatLng> rutaLocal : rutasLocales) {
                            double distanciaLocalTotal = 0;
                            for (int i = 0; i < rutaLocal.size() - 1; i++) {
                                distanciaLocalTotal += calcularDistancia(rutaLocal.get(i), rutaLocal.get(i + 1));
                            }

                            long duracionLocalTotal = (long)((distanciaLocalTotal / 30.0) * 3600);
                            String distanciaLocalStr = String.format("%.1f km", distanciaLocalTotal);
                            String duracionLocalStr = formatearDuracion(duracionLocalTotal);

                            RutaInfo rutaLocalInfo = new RutaInfo(rutaLocal, distanciaLocalStr, duracionLocalStr);
                            rutaLocalInfo.ordenPuntos = rutaLocal;
                            rutasAlternativas.add(rutaLocalInfo);
                        }

                        // Mostrar la ruta principal por defecto
                        mostrarRutaEnMapa(0);

                    } catch (Exception e) {
                        Log.e("Directions", "Error al procesar respuesta: " + e.getMessage());
                    }
                },
                error -> Log.e("Directions", "Error: " + error.toString()));

        requestQueue.add(request);
        procesarSiguienteRuta(0, rutasLocales);

    }
    private void procesarSiguienteRuta(int rutaIndex, List<List<LatLng>> rutasLocales) {
        if (rutaIndex > 2) { // Limitamos a 3 rutas en total
            mostrarRutaEnMapa(0); // Mostrar la ruta principal por defecto
            return;
        }

        List<LatLng> rutaActual = rutaIndex == 0 ? rutaOptima : rutasLocales.get(rutaIndex - 1);

        // Construir waypoints en el orden específico (sin optimize:true para rutas alternativas)
        StringBuilder waypoints = new StringBuilder();
        for (int i = 1; i < rutaActual.size() - 1; i++) {
            if (i > 1) waypoints.append("|");
            waypoints.append(rutaActual.get(i).latitude).append(",").append(rutaActual.get(i).longitude);
        }

        // URL para la API de Directions
        String url = "https://maps.googleapis.com/maps/api/directions/json?" +
                "origin=" + rutaActual.get(0).latitude + "," + rutaActual.get(0).longitude +
                "&destination=" + UPV_LOCATION.latitude + "," + UPV_LOCATION.longitude +
                "&waypoints=" + (rutaIndex == 0 ? "optimize:true|" : "") + waypoints.toString() +
                "&key=" + DIRECTIONS_API_KEY;

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        if (rutaIndex == 0) rutasAlternativas.clear();

                        JSONObject route = response.getJSONArray("routes").getJSONObject(0);
                        List<LatLng> puntosOrdenados = new ArrayList<>();
                        puntosOrdenados.add(conductorLocation);

                        if (rutaIndex == 0) {
                            // Para la ruta principal, usar el orden optimizado de Google
                            JSONArray waypointOrder = route.getJSONArray("waypoint_order");
                            List<LatLng> waypointsList = new ArrayList<>();
                            for (int i = 1; i < rutaActual.size() - 1; i++) {
                                waypointsList.add(rutaActual.get(i));
                            }
                            for (int i = 0; i < waypointOrder.length(); i++) {
                                int index = waypointOrder.getInt(i);
                                puntosOrdenados.add(waypointsList.get(index));
                            }
                        } else {
                            // Para rutas alternativas, mantener el orden original
                            puntosOrdenados.addAll(rutaActual.subList(1, rutaActual.size() - 1));
                        }
                        puntosOrdenados.add(UPV_LOCATION);

                        // Calcular distancia y duración total
                        JSONArray legs = route.getJSONArray("legs");
                        double distanciaTotal = 0;
                        long duracionTotal = 0;

                        // Obtener todos los puntos de la ruta siguiendo las calles
                        List<LatLng> rutaCompleta = new ArrayList<>();
                        for (int j = 0; j < legs.length(); j++) {
                            JSONObject leg = legs.getJSONObject(j);
                            distanciaTotal += leg.getJSONObject("distance").getDouble("value");
                            duracionTotal += leg.getJSONObject("duration").getLong("value");

                            // Decodificar los pasos de la ruta para este segmento
                            JSONArray steps = leg.getJSONArray("steps");
                            for (int k = 0; k < steps.length(); k++) {
                                JSONObject step = steps.getJSONObject(k);
                                String pointsStr = step.getJSONObject("polyline").getString("points");
                                rutaCompleta.addAll(PolyUtil.decode(pointsStr));
                            }
                        }

                        String distanciaStr = String.format("%.1f km", distanciaTotal / 1000);
                        String duracionStr = formatearDuracion(duracionTotal);

                        // Crear RutaInfo con la ruta completa que sigue las calles
                        RutaInfo rutaInfo = new RutaInfo(rutaCompleta, distanciaStr, duracionStr);
                        rutaInfo.ordenPuntos = puntosOrdenados;
                        rutasAlternativas.add(rutaInfo);

                        // Procesar la siguiente ruta
                        procesarSiguienteRuta(rutaIndex + 1, rutasLocales);

                    } catch (Exception e) {
                        Log.e("Directions", "Error al procesar respuesta: " + e.getMessage());
                    }
                },
                error -> Log.e("Directions", "Error: " + error.toString()));

        requestQueue.add(request);
    }
    private List<List<LatLng>> generarRutasLocales() {
        List<List<LatLng>> rutasLocales = new ArrayList<>();

        // Ordenar pasajeros por distancia al conductor
        List<Pasajero> pasajerosOrdenados = new ArrayList<>(pasajeros);
        Collections.sort(pasajerosOrdenados, (p1, p2) ->
                Double.compare(calcularDistancia(conductorLocation, p1.ubicacion),
                        calcularDistancia(conductorLocation, p2.ubicacion)));

        // Generar segunda ruta (comenzando con el pasajero más cercano)
        if (pasajerosOrdenados.size() > 0) {
            rutasLocales.add(generarRutaDesdeInicio(pasajerosOrdenados.get(0)));
        }

        // Generar tercera ruta (comenzando con el segundo pasajero más cercano)
        if (pasajerosOrdenados.size() > 1) {
            rutasLocales.add(generarRutaDesdeInicio(pasajerosOrdenados.get(1)));
        }

        return rutasLocales;
    }

    private String formatearDuracion(long segundos) {
        long horas = segundos / 3600;
        long minutos = (segundos % 3600) / 60;

        if (horas > 0) {
            return String.format("%d h %d min", horas, minutos);
        } else {
            return String.format("%d min", minutos);
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

                // Agregar marcador de la UPV
                mMap.addMarker(new MarkerOptions()
                        .position(UPV_LOCATION)
                        .title("Universidad Politécnica de Victoria")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));

                // Agregar marcador del conductor
                mMap.addMarker(new MarkerOptions()
                        .position(conductorLocation)
                        .title("Mi ubicación")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));

                char letra = 'A';
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    try {
                        Double lat = snapshot.child("lat").getValue(Double.class);
                        Double lng = snapshot.child("lng").getValue(Double.class);

                        if (lat != null && lng != null) {
                            LatLng ubicacionPasajero = new LatLng(lat, lng);
                            double distancia = calcularDistancia(conductorLocation, ubicacionPasajero);
                            Pasajero pasajero = new Pasajero(snapshot.getKey(), ubicacionPasajero, distancia);
                            pasajero.nombreCorto = "Pasajero " + letra;
                            pasajeros.add(pasajero);

                            // Crear y agregar el marcador personalizado con la letra
                            mMap.addMarker(new MarkerOptions()
                                    .position(ubicacionPasajero)
                                    .title(pasajero.nombreCorto)
                                    .icon(createCustomMarker(String.valueOf(letra))));

                            letra++;
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
        // Lista para almacenar las tres rutas diferentes
        List<List<LatLng>> todasLasRutas = new ArrayList<>();

        // Primera ruta: la ruta optimizada por Google Maps
        List<LatLng> rutaPrincipal = new ArrayList<>();
        rutaPrincipal.add(conductorLocation);
        for (Pasajero pasajero : pasajeros) {
            rutaPrincipal.add(pasajero.ubicacion);
        }
        rutaPrincipal.add(UPV_LOCATION);
        todasLasRutas.add(rutaPrincipal);

        // Segunda y tercera ruta: basadas en los dos pasajeros más cercanos
        List<Pasajero> pasajerosOrdenados = new ArrayList<>(pasajeros);
        Collections.sort(pasajerosOrdenados, (p1, p2) ->
                Double.compare(calcularDistancia(conductorLocation, p1.ubicacion),
                        calcularDistancia(conductorLocation, p2.ubicacion)));

        // Generar segunda ruta (comenzando con el pasajero más cercano)
        if (pasajerosOrdenados.size() > 0) {
            List<LatLng> segundaRuta = generarRutaDesdeInicio(pasajerosOrdenados.get(0));
            todasLasRutas.add(segundaRuta);
        }

        // Generar tercera ruta (comenzando con el segundo pasajero más cercano)
        if (pasajerosOrdenados.size() > 1) {
            List<LatLng> terceraRuta = generarRutaDesdeInicio(pasajerosOrdenados.get(1));
            todasLasRutas.add(terceraRuta);
        }

        // Seleccionar la ruta que usaremos para la optimización
        rutaOptima = todasLasRutas.get(0); // Usamos la ruta principal para la optimización
        solicitarRutaOptimizada();
    }

    private List<LatLng> generarRutaDesdeInicio(Pasajero primerPasajero) {
        List<LatLng> ruta = new ArrayList<>();
        ruta.add(conductorLocation);

        // Agregar el primer pasajero
        ruta.add(primerPasajero.ubicacion);

        // Lista de pasajeros restantes
        List<Pasajero> pasajerosRestantes = new ArrayList<>(pasajeros);
        pasajerosRestantes.removeIf(p -> p.id.equals(primerPasajero.id));

        LatLng ubicacionActual = primerPasajero.ubicacion;

        // Algoritmo del vecino más cercano para los pasajeros restantes
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
                ruta.add(pasajeroMasCercano.ubicacion);
                ubicacionActual = pasajeroMasCercano.ubicacion;
                pasajerosRestantes.remove(pasajeroMasCercano);
            }
        }

        ruta.add(UPV_LOCATION);
        return ruta;
    }

    private BitmapDescriptor createCustomMarker(String letra) {
        int width = 80;
        int height = 80;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint circlePaint = new Paint();
        circlePaint.setColor(Color.RED);
        circlePaint.setAntiAlias(true);
        canvas.drawCircle(width/2, height/2, width/2, circlePaint);

        Paint textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(24);
        textPaint.setAntiAlias(true);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);

        float textHeight = textPaint.descent() - textPaint.ascent();
        float textOffset = (textHeight / 2) - textPaint.descent();
        canvas.drawText(letra, width/2, height/2 + textOffset, textPaint);

        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

}