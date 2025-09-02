package com.smokerider.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode

class MainActivityTest : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Inizializza Places
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, getString(R.string.google_maps_key))
        }

        // ✅ Layout con un solo bottone
        val layout = FrameLayout(this)
        val button = Button(this).apply {
            text = "Apri Autocomplete"
            setOnClickListener {
                try {
                    val fields = listOf(
                        Place.Field.ID,
                        Place.Field.NAME,
                        Place.Field.ADDRESS,
                        Place.Field.LAT_LNG
                    )
                    val intent = Autocomplete.IntentBuilder(
                        AutocompleteActivityMode.OVERLAY,
                        fields
                    ).build(this@MainActivityTest)

                    Log.d("PLACES", "Lancio Autocomplete...")
                    startActivityForResult(intent, 100)
                } catch (e: Exception) {
                    Log.e("PLACES", "Errore apertura Autocomplete", e)
                }
            }
        }
        layout.addView(button)
        setContentView(layout)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100) {
            Log.d("PLACES", "Autocomplete chiuso con codice $resultCode e data=$data")
        }
    }
}
