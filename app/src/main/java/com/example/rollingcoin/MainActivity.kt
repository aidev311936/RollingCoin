package com.example.rollingcoin

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.coinrain.CoinRainView
import com.example.coinrain.SensorAdapter

class MainActivity : AppCompatActivity() {

    private lateinit var coinRainView: CoinRainView
    private lateinit var sensorAdapter: SensorAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        coinRainView = findViewById(R.id.coinRainView)
        sensorAdapter = SensorAdapter(this, coinRainView)

        coinRainView.post { coinRainView.spawnDefaultCoins() }
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
}
