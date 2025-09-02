package com.smokerider.app.ui.customer

import android.app.Activity
import android.content.pm.PackageManager
import android.location.Geocoder
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.maps.android.compose.*
import com.smokerider.app.viewmodel.PositionViewModel
import com.smokerider.app.viewmodel.OrderViewModel
import com.smokerider.app.ui.theme.screenInsets
import java.util.*

@Composable
fun CustomerPositionScreen(
    uid: String,
    navController: NavController,
    onConfirm: () -> Unit,
    positionViewModel: PositionViewModel = viewModel(),
    orderViewModel: OrderViewModel
) {
    val context = LocalContext.current
    val activity = context as? Activity

    var latLng by remember { mutableStateOf<LatLng?>(null) }
    var city by remember { mutableStateOf("") }
    var street by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var isPlacing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!Places.isInitialized()) {
            try {
                Places.initialize(context, context.getString(com.smokerider.app.R.string.google_maps_key))
            } catch (_: Throwable) { /* ignore */ }
        }
    }

    val fusedLocation = remember { LocationServices.getFusedLocationProviderClient(context) }

    // Ottieni posizione GPS iniziale
    LaunchedEffect(Unit) {
        if (ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocation.lastLocation.addOnSuccessListener { loc ->
                loc?.let {
                    val first = LatLng(it.latitude, it.longitude)
                    latLng = first

                    val geocoder = Geocoder(context, Locale.getDefault())
                    val results = geocoder.getFromLocation(it.latitude, it.longitude, 1)
                    if (!results.isNullOrEmpty()) {
                        city = results[0].locality ?: ""
                        street = listOfNotNull(
                            results[0].thoroughfare,
                            results[0].subThoroughfare
                        ).joinToString(" ")
                        searchQuery = results[0].getAddressLine(0) ?: ""
                    }
                }
            }
        }
    }

    // Launcher per Google Places Autocomplete
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let {
                val place = Autocomplete.getPlaceFromIntent(it)
                latLng = place.latLng
                searchQuery = place.address ?: ""

                city = place.addressComponents?.asList()
                    ?.firstOrNull { c -> "locality" in c.types }?.name ?: ""

                val route = place.addressComponents?.asList()
                    ?.firstOrNull { c -> "route" in c.types }?.name
                val number = place.addressComponents?.asList()
                    ?.firstOrNull { c -> "street_number" in c.types }?.name

                street = listOfNotNull(route, number).joinToString(" ")
            }
        }
    }

    val cameraPositionState = rememberCameraPositionState()

    // Centra la mappa quando cambia latLng
    LaunchedEffect(latLng) {
        latLng?.let {
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngZoom(it, 16f),
                durationMs = 800
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .screenInsets(includeTop = true, includeBottom = true, extraTop = 16.dp) // ⬅️ protezione e distacco
    ) {
        // Bottone che apre Autocomplete
        Button(
            onClick = {
                val fields = listOf(
                    Place.Field.ID,
                    Place.Field.NAME,
                    Place.Field.ADDRESS,
                    Place.Field.ADDRESS_COMPONENTS,
                    Place.Field.LAT_LNG
                )
                activity?.let {
                    val intent = Autocomplete.IntentBuilder(
                        AutocompleteActivityMode.OVERLAY, fields
                    ).build(it)
                    launcher.launch(intent)
                } ?: Toast.makeText(context, "Activity non valida", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            enabled = !isPlacing
        ) {
            Text(if (searchQuery.isBlank()) "Cerca indirizzo" else searchQuery)
        }

        if (latLng != null) {
            GoogleMap(
                modifier = Modifier.weight(1f),
                cameraPositionState = cameraPositionState
            ) {
                val markerState = rememberMarkerState(position = latLng!!)

                LaunchedEffect(latLng) { markerState.position = latLng!! }

                Marker(
                    state = markerState,
                    title = "Consegna qui",
                    draggable = true
                )

                LaunchedEffect(markerState.position) {
                    latLng = markerState.position
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                val pos = latLng
                if (pos == null) {
                    Toast.makeText(context, "Seleziona una posizione", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                isPlacing = true

                // 1) Salva/aggiorna posizione
                positionViewModel.saveOrUpdatePosition(
                    uid = uid,
                    city = city,
                    street = street,
                    latitude = pos.latitude,
                    longitude = pos.longitude
                )

                // 2) Crea ordine
                orderViewModel.createOrder(
                    clientId = uid
                ) { success, orderId ->
                    isPlacing = false
                    if (success && orderId != null) {
                        Toast.makeText(context, "Ordine confermato ✅", Toast.LENGTH_SHORT).show()
                        navController.navigate("customer/attesa/$orderId")
                    } else {
                        Toast.makeText(context, "Errore durante l'ordine", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            enabled = !isPlacing
        ) {
            Text(if (isPlacing) "Invio in corso…" else "Conferma ordine")
        }
    }
}
