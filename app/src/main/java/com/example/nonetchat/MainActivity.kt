package com.example.nonetchat

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes

class MainActivity : AppCompatActivity() {
    private val STRATEGY = Strategy.P2P_STAR
    private val SERVICE_ID = "com.example.nonetchat.SERVICE"

    private lateinit var myName: String
    private lateinit var chatDisplay: TextView
    private lateinit var messageInput: EditText
    private var connectedEndpointId: String? = null

    // Permissions for Bluetooth and Location
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) + (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_ADVERTISE
    ) else emptyArray())
    private val REQUEST_PERMISSIONS = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        myName = "User${(1000..9999).random()}"
        chatDisplay = findViewById(R.id.chatDisplay)
        messageInput = findViewById(R.id.messageInput)
        val btnAdvertise = findViewById<Button>(R.id.btnAdvertise)
        val btnDiscover = findViewById<Button>(R.id.btnDiscover)
        val sendMessageBtn = findViewById<Button>(R.id.sendMessageBtn)

        btnAdvertise.setOnClickListener { startAdvertising() }
        btnDiscover.setOnClickListener { startDiscovery() }
        sendMessageBtn.setOnClickListener { sendMessage() }

        if (!hasPermissions()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_PERMISSIONS)
        } else {
            showToast("Ready: $myName")
        }
    }

    private fun hasPermissions() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            showToast("Ready: $myName")
        } else {
            showToast("Permissions denied; app cannot function.")
        }
    }

    private fun startAdvertising() {
        Nearby.getConnectionsClient(this)
            .startAdvertising(
                myName, SERVICE_ID, connectionCallback,
                AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
            )
            .addOnSuccessListener { showToast("Advertising as $myName") }
            .addOnFailureListener { e -> showToast("Advertise failed: ${e.message}") }
    }

    private fun startDiscovery() {
        Nearby.getConnectionsClient(this).stopDiscovery()
        Nearby.getConnectionsClient(this)
            .startDiscovery(
                SERVICE_ID,
                discoveryCallback,
                DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
            )
            .addOnSuccessListener { showToast("Discovery started") }
            .addOnFailureListener { e ->
                if (e is ApiException && e.statusCode == ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING) {
                    showToast("Already discovering")
                } else {
                    val code = (e as? ApiException)?.statusCode
                    showToast("Discovery failed (status=$code): ${e.message}")
                }
            }
    }

    private fun sendMessage() {
        val msg = messageInput.text.toString().trim()
        if (msg.isEmpty()) {
            showToast("Enter a message")
            return
        }
        val endpoint = connectedEndpointId
        if (endpoint.isNullOrEmpty()) {
            showToast("Not connected")
            return
        }
        Nearby.getConnectionsClient(this)
            .sendPayload(endpoint, Payload.fromBytes(msg.toByteArray()))
        appendMessage("You: $msg")
        messageInput.text.clear()
    }

    private val connectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Nearby.getConnectionsClient(this@MainActivity)
                .acceptConnection(endpointId, payloadCallback)
            showToast("Connection initiated with ${info.endpointName}")
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                connectedEndpointId = endpointId
                showToast("Connected to $endpointId")
            } else {
                showToast("Connection failed: ${result.status.statusCode}")
            }
        }

        override fun onDisconnected(endpointId: String) {
            connectedEndpointId = null
            showToast("Disconnected from $endpointId")
        }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Nearby.getConnectionsClient(this@MainActivity)
                .requestConnection(myName, endpointId, connectionCallback)
                .addOnSuccessListener { showToast("Requested connection to ${info.endpointName}") }
                .addOnFailureListener { e -> showToast("Request failed: ${e.message}") }
        }

        override fun onEndpointLost(endpointId: String) {
            showToast("Lost endpoint: $endpointId")
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            payload.asBytes()?.let { appendMessage("Stranger: ${String(it)}") }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private fun appendMessage(msg: String) {
        chatDisplay.append("\n$msg")
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
