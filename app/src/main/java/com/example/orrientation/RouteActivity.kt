package com.example.orrientation

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.*
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.net.HttpURLConnection
import java.net.URL

class RouteActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val destination = GeoPoint(-0.16698, 35.96704) // Your Destination
    private val osrmUrl = "https://router.project-osrm.org/route/v1/walking/" // OSRM API for Walking Routes

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Configure OSM settings
        Configuration.getInstance().userAgentValue = packageName
        setContentView(R.layout.activity_route)

        mapView = findViewById(R.id.mapView)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        getCurrentLocationAndRoute()
    }

    private fun getCurrentLocationAndRoute() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                val currentLocation = GeoPoint(location.latitude, location.longitude)
                Log.d("RouteActivity", "✅ Current Location: ${currentLocation.latitude}, ${currentLocation.longitude}")

                // ✅ Fetch route from OSRM
                fetchRouteFromOSRM(currentLocation, destination)
            } else {
                Log.e("RouteActivity", "❌ Could not get current location!")
            }
        }
    }

    private fun fetchRouteFromOSRM(start: GeoPoint, end: GeoPoint) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val urlString = "${osrmUrl}${start.longitude},${start.latitude};${end.longitude},${end.latitude}?overview=full&geometries=geojson"
                val url = URL(urlString)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"

                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonResponse = JSONObject(response)
                val routes = jsonResponse.getJSONArray("routes")

                if (routes.length() > 0) {
                    val geometry = routes.getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates")
                    val routePoints = mutableListOf<GeoPoint>()

                    for (i in 0 until geometry.length()) {
                        val point = geometry.getJSONArray(i)
                        val lon = point.getDouble(0)
                        val lat = point.getDouble(1)
                        routePoints.add(GeoPoint(lat, lon))
                    }

                    // ✅ Draw the route on the map
                    withContext(Dispatchers.Main) {
                        drawRouteOnMap(routePoints)
                    }
                }
            } catch (e: Exception) {
                Log.e("RouteActivity", "❌ Error fetching route", e)
            }
        }
    }

    private fun drawRouteOnMap(routePoints: List<GeoPoint>) {
        val polyline = Polyline()
        polyline.setPoints(routePoints)
        polyline.color = android.graphics.Color.BLUE
        polyline.width = 7f

        mapView.overlays.add(polyline)
        mapView.invalidate()
        mapView.controller.setZoom(17.0)
        mapView.controller.setCenter(routePoints.first())
    }
}
