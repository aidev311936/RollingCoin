package com.example.rollingcoin

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.coinrain.CoinRainView
import com.example.coinrain.SensorAdapter
import com.example.coinrain.core.Denomination
import org.json.JSONObject
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var coinRainView: CoinRainView
    private lateinit var sensorAdapter: SensorAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        coinRainView = findViewById(R.id.coinRainView)
        sensorAdapter = SensorAdapter(this, coinRainView)

        coinRainView.onError = { error ->
            Log.e(TAG, "CoinRain error: $error")
        }

        coinRainView.post { loadAndStartRain() }
    }

    private fun loadAndStartRain() {
        val configFile = File(getExternalFilesDir(null), "testapp.json")

        if (!configFile.exists()) {
            val default = """
                {
                  "amount": "2.43",
                  "allowedCoins": ["EURO_1", "CENT_50", "CENT_20", "CENT_10", "CENT_5", "CENT_1"]
                }
            """.trimIndent()
            try {
                configFile.writeText(default)
                Log.i(TAG, "Created default testapp config at ${configFile.absolutePath}")
            } catch (e: Exception) {
                Log.w(TAG, "Could not write default config: ${e.message}")
            }
        }

        var amount = "2.43"
        var allowedCoins: Set<Denomination>? = null
        try {
            val text = configFile.readText()
            val json = JSONObject(text)
            amount = json.getString("amount")
            val arr = json.optJSONArray("allowedCoins")
            if (arr != null) {
                val ids = (0 until arr.length()).map { arr.getString(it) }
                allowedCoins = ids.mapNotNull { Denomination.fromSpecId(it) }.toSet()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse testapp.json, falling back to defaults: ${e.message}")
        }

        coinRainView.rain(amount, allowedCoins?.takeIf { it.isNotEmpty() })
    }

    override fun onResume() {
        super.onResume()
        sensorAdapter.register()
        coinRainView.resume()
    }

    override fun onPause() {
        super.onPause()
        sensorAdapter.unregister()
        coinRainView.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        coinRainView.stop()
    }

    companion object {
        private const val TAG = "CoinRainTestApp"
    }
}
