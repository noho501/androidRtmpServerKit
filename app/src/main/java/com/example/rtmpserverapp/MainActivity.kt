package com.example.rtmpserverapp

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import com.example.rtmpserverapp.databinding.ActivityMainBinding
import com.example.rtmpserverkit.publicapi.RTMPServerPublic
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val RTMP_PORT = 1935
        private const val TEST_STREAM_KEY = "test"
    }

    private lateinit var binding: ActivityMainBinding
    private val rtmpServer = RTMPServerPublic()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on during streaming
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRtmpUrl()
        setupServer()
    }

    @SuppressLint("SetTextI18n")
    private fun setupRtmpUrl() {
        val ip = getLocalIpAddress() ?: "192.168.x.x"
        val url = "rtmp://$ip:$RTMP_PORT/live"

        binding.tvRtmpUrl.text = url
        binding.tvStreamKey.text = "Stream key: $TEST_STREAM_KEY"

        Log.d(TAG, "URL: $url")
    }

    @SuppressLint("SetTextI18n")
    private fun setupServer() {
        rtmpServer.onPublish = { streamKey ->
            runOnUiThread {
                binding.tvStatus.text = "● LIVE ($streamKey)"
                binding.tvStatus.setTextColor("#FF4444".toColorInt())
            }
        }

        rtmpServer.onDisconnect = { streamKey ->
            Log.i(TAG, "Client disconnected: $streamKey")
            runOnUiThread {
                binding.tvStatus.text = "Waiting for stream…"
                binding.tvStatus.setTextColor(android.graphics.Color.WHITE)
            }
        }

        // Attach the SurfaceView for video rendering mapped with the key
        rtmpServer.attachSurface(TEST_STREAM_KEY, binding.surfaceView)

        try {
            // Start server
            rtmpServer.start(RTMP_PORT)
        } catch (e: Exception) {
            Log.d(TAG, "Failed to start RTMP Server: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        rtmpServer.stop()
    }

    private fun getLocalIpAddress(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: return null
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val addrs = intf.inetAddresses?.toList() ?: continue
                for (addr in addrs) {
                    if (addr !is Inet4Address) continue
                    return addr.hostAddress
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Could not get IP: ${e.message}")
            null
        }
    }
}