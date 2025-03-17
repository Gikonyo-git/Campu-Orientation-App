package com.example.orrientation

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var locationSpinner: Spinner
    private lateinit var locationImage: ImageView
    private lateinit var distanceText: TextView
    private lateinit var directionsText: TextView
    private lateinit var coordinatesText: TextView
    private lateinit var nearbyPlacesLayout: LinearLayout
    private val osrmUrl = "https://router.project-osrm.org/route/v1/walking/"

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var userLocation: GeoPoint? = null
    private var selectedDestination: GeoPoint? = null

    private val locations = mapOf(
        "Administration Block" to GeoPoint(-0.16711, 35.96616),
        "Daniel T. Arap Moi Library" to GeoPoint(-0.16841, 35.96634),
        "Mess" to GeoPoint(-0.16617, 35.96570),
        "Kabarak Chapel" to GeoPoint(-0.16508, 35.96511),
        "School of Business" to GeoPoint(-0.16616, 35.96258),
        "Student Centre" to GeoPoint(-0.16695, 35.96249),
        "School of Science & Engineering" to GeoPoint(-0.16720, 35.96542),
        "School of Education" to GeoPoint(-0.16761, 35.96622),
        "ICT Office" to GeoPoint(-0.16751, 35.96530),
        "Bethlehem Auditorium" to GeoPoint(-0.16750, 35.96691),
        "Inventory Centre" to GeoPoint(-0.16698, 35.96704),
        "School of Medicine & Health" to GeoPoint(-0.16915, 35.96545),
        "Law School" to GeoPoint(-0.16988, 35.96457),
        "Law School Office" to GeoPoint(-0.17062, 35.96507),
        "Field" to GeoPoint(-0.16499, 35.96404),
        "School of Music & Performing Arts" to GeoPoint(-0.16784, 35.96372),
        "Convocational Hall" to GeoPoint(-0.16829, 35.96410),
        "hospital" to GeoPoint(-0.16869461005319408, 35.96552610397339),
        "Kabuo" to GeoPoint(-0.1658085654496233, 35.96528738737106)
    )

    private val locationImages = mapOf(
        "Administration Block" to R.drawable.admin_block,
        "Daniel T. Arap Moi Library" to R.drawable.library,
        "Mess" to R.drawable.mess,
        "Kabarak Chapel" to R.drawable.chappell,
        "School of Business" to R.drawable.sob,
        "Student Centre" to R.drawable.stc,
        "School of Science & Engineering" to R.drawable.sset,
        "School of Education" to R.drawable.soeducation,
        "ICT Office" to R.drawable.sset,
        "Bethlehem Auditorium" to R.drawable.auditorium,
        "Inventory Centre" to R.drawable.inovationcenter,
        "School of Medicine & Health" to R.drawable.hms,
        "Law School" to R.drawable.lawschool,
        "Law School Office" to R.drawable.lso,
        "Field" to R.drawable.filed,
        "School of Music & Performing Arts" to R.drawable.som,
        "Convocational Hall" to R.drawable.convocational,
        "hospital" to R.drawable.hospital,
        "kabuo" to R.drawable.kbuonline
    )

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            for (location in locationResult.locations) {
                userLocation = GeoPoint(location.latitude, location.longitude)
                Log.d("MainActivity", "📍 New Location: ${userLocation!!.latitude}, ${userLocation!!.longitude}")

                // Update the user's marker on the map
                addUserLocationMarker(userLocation!!)

                // If a destination is selected, fetch the route
                selectedDestination?.let {
                    fetchRouteFromOSRM(userLocation!!, it)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Views
        mapView = findViewById(R.id.map)
        locationSpinner = findViewById(R.id.locationSpinner)
        locationImage = findViewById(R.id.locationImage)
        distanceText = findViewById(R.id.distanceText)
        directionsText = findViewById(R.id.directionsText)
        coordinatesText = findViewById(R.id.coordinatesText)
        nearbyPlacesLayout = findViewById(R.id.nearbyPlacesLayout)

        Configuration.getInstance().load(applicationContext, getSharedPreferences("osm_prefs", MODE_PRIVATE))
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.controller.setZoom(16.0)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        requestLocationPermission()
        setupLocationDropdown()
        addFixedMarkers()
        checkLocationSettings()
    }

    private fun checkLocationSettings() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()

        val locationSettingsRequest = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setAlwaysShow(true)
            .build()

        val settingsClient = LocationServices.getSettingsClient(this)

        settingsClient.checkLocationSettings(locationSettingsRequest)
            .addOnSuccessListener {
                // Location services are enabled
                requestLocationPermission()
            }
            .addOnFailureListener { exception ->
                if (exception is ResolvableApiException) {
                    try {
                        exception.startResolutionForResult(this, 2)
                    } catch (sendEx: Exception) {
                        Log.e("MainActivity", "Error requesting location settings: ${sendEx.message}")
                    }
                }
            }
    }

    private fun requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        } else {
            startLocationUpdates()
        }
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateIntervalMillis(3000)
            .build()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        }
    }

    private fun addUserLocationMarker(geoPoint: GeoPoint) {
        // Remove any existing user location marker
        mapView.overlays.removeIf { it is Marker && it.title == "You are here" }

        // Add a new marker for the user's location
        val userMarker = Marker(mapView).apply {
            position = geoPoint
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.baseline_person_pin_circle_24)
            title = "You are here"
        }
        mapView.overlays.add(userMarker)
        mapView.invalidate() // Refresh the map
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

                    // Draw the route on the map
                    withContext(Dispatchers.Main) {
                        drawRouteOnMap(routePoints)
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "❌ Error fetching route", e)
            }
        }
    }

    private var currentRoute: Polyline? = null  // Store the current route polyline

    private fun drawRouteOnMap(routePoints: List<GeoPoint>) {
        // Remove the previous route if it exists
        currentRoute?.let { mapView.overlays.remove(it) }

        val polyline = Polyline()
        polyline.setPoints(routePoints)
        polyline.color = android.graphics.Color.BLUE
        polyline.width = 7f

        mapView.overlays.add(polyline)
        currentRoute = polyline  // Update the current route
        mapView.invalidate()
        mapView.controller.setZoom(17.0)
        mapView.controller.setCenter(routePoints.first())
    }


    private fun setupLocationDropdown() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, locations.keys.toList())
        locationSpinner.adapter = adapter

        locationSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val locationName = locations.keys.elementAt(position)
                selectedDestination = locations[locationName]
                updateLocationDetails(locationName, selectedDestination!!)
                getUserLocation()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    private fun getUserLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    userLocation = GeoPoint(location.latitude, location.longitude)
                    Log.d("MainActivity", "✅ Current Location: ${userLocation!!.latitude}, ${userLocation!!.longitude}")

                    addUserLocationMarker(userLocation!!)
                    // Check if a destination is selected and fetch the route
                    if (selectedDestination != null) {
                        fetchRouteFromOSRM(userLocation!!, selectedDestination!!)
                    } else {
                        coordinatesText.text = "⚠️ Please select a destination from the spinner."
                    }
                } else {

                }
            }
        } else {
            // Request permissions if not granted
        }
    }
    private var isUserInteracting = false // Track if the user is interacting with the map
    private fun updateLocationDetails(name: String, geoPoint: GeoPoint) {
        if (!isUserInteracting) {
            mapView.controller.animateTo(geoPoint)
            mapView.controller.setZoom(16.0)
        }

        locationImage.setImageResource(locationImages[name] ?: R.drawable.hospital)

        userLocation?.let { userLoc ->
            val results = FloatArray(1)
            Location.distanceBetween(userLoc.latitude, userLoc.longitude, geoPoint.latitude, geoPoint.longitude, results)
            val distance = results[0] / 1000
            distanceText.text = "Distance: %.2f km".format(distance)
        } ?: run {
            distanceText.text = "Distance: Unknown"
        }

        directionsText.text = when (name) {
            "Administration Block" -> "You are at the Administration Block.\nIn front: Innovation Center and METS.\nBehind: School of Education & School of Technology.\nDirectly behind: ICT Office."
            "Innovation Center" -> "The Innovation Center is directly in front of the Administration Block."
            "Mess" -> "The Mess (cafeteria) is near the Innovation Center, in front of the Administration Block."
            "School of Education" -> "The School of Education is behind the Administration Block and near the Library."
            "School of Technology" -> "The School of Technology is behind the Administration Block, next to the School of Education."
            "ICT Office" -> "The ICT Office is located directly behind the Administration Block."
            "Bethlehem Auditorium" -> "Bethlehem Auditorium is beside the Innovation Center, to the left."
            "Daniel T. Arap Moi Library" -> "Located southwest of the School of Education and northeast of the Hospital."
            "Hospital" -> "The Hospital is southwest of the Library, in between the School of Medicine & Health, Library, and School of Education."
            "Convocational Hall" -> "Located behind the Law School and School of Music & Performing Arts.\nTo the left: School of Health Sciences."
            "Law School" -> "Between the Law School Offices, the School of Medicine & Health, and the Convocational Hall."
            "Law School Office" -> "Located in front of the Law School, across the road."
            "School of Medicine & Health" -> "In the middle of the Law School, the Hospital, and the Library.\nLibrary is to the left, Law School to the right, Hospital behind."
            "School of Music & Performing Arts" -> "Behind the Convocational Hall.\nTo the northwest: Student Centre."
            "Student Centre" -> "Between the School of Music & Performing Arts and the School of Business.\nTo the left of the road from the School of Music."
            "School of Business" -> "Below the school field.\nTo the left: Swimming pool.\nTo the right: Student Centre."
            "Kabarak Chapel" -> "On the right side of Philip Road.\nBefore Kabarak Online, near the Student Centre and School of Business."
            "Kabuo" -> "Located in the middle of the Chapel and the Mess, just behind the Mess."
            else -> "To reach $name, follow the marked path on the map."
        }

        showNearbyPlaces(name)
    }

    private val nearbyLocations = mapOf(
        "Administration Block" to listOf("Innovation Center", "METS", "School of Education", "School of Technology", "ICT Office"),
        "Innovation Center" to listOf("Administration Block"),
        "Mess" to listOf("Innovation Center", "Administration Block"),
        "School of Education" to listOf("Administration Block", "Library"),
        "School of Technology" to listOf("Administration Block", "School of Education"),
        "ICT Office" to listOf("Administration Block"),
        "Bethlehem Auditorium" to listOf("Innovation Center"),
        "Daniel T. Arap Moi Library" to listOf("School of Education", "Hospital"),
        "Hospital" to listOf("Library", "School of Medicine & Health", "School of Education"),
        "Convocational Hall" to listOf("Law School", "School of Music & Performing Arts", "School of Health Sciences"),
        "Law School" to listOf("Law School Office", "School of Medicine & Health", "Convocational Hall"),
        "Law School Office" to listOf("Law School"),
        "School of Medicine & Health" to listOf("Law School", "Hospital", "Library"),
        "School of Music & Performing Arts" to listOf("Convocational Hall", "Student Centre"),
        "Student Centre" to listOf("School of Music & Performing Arts", "School of Business"),
        "School of Business" to listOf("Student Centre", "Swimming Pool"),
        "Kabarak Chapel" to listOf("Kabuo", "Student Centre", "School of Business"),
        "Kabuo" to listOf("Mess", "Kabarak Chapel")
    )

    private fun addFixedMarkers() {
        for ((name, geoPoint) in locations) {
            val marker = Marker(mapView).apply {
                position = geoPoint
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.baseline_location_pin_24)
                title = name
                setOnMarkerClickListener { _, _ ->
                    Toast.makeText(this@MainActivity, "📍 $name", Toast.LENGTH_SHORT).show()
                    true
                }
            }
            mapView.overlays.add(marker)
        }
        mapView.invalidate()
    }

    private fun showNearbyPlaces(selectedLocation: String) {
        nearbyPlacesLayout.removeAllViews()

        val nearby = nearbyLocations[selectedLocation] ?: emptyList()

        for (name in nearby) {
            val imgView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(150, 150)
                setImageResource(locationImages[name] ?: R.drawable.hospital)
                setPadding(8, 8, 8, 8)
            }
            nearbyPlacesLayout.addView(imgView)
        }
    }



    override fun onResume() {
        super.onResume()
        startLocationUpdates()
    }
}