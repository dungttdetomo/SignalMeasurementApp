package com.example.signalmeasurementapp

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.signalmeasurementapp.data.SignalMeasurement
import com.example.signalmeasurementapp.ui.theme.SignalMeasurementAppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var service: SignalMeasurementService? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, binder: IBinder) {
            val localBinder = binder as SignalMeasurementService.LocalBinder
            service = localBinder.getService()
            bound = true
            Log.d("MainActivity", "Service connected")
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            bound = false
            Log.d("MainActivity", "Service disconnected")
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true &&
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true &&
                permissions[Manifest.permission.ACCESS_BACKGROUND_LOCATION] == true) {
                // Permissions granted, start the service
                startSignalMeasurementService()
            } else {
                // Permissions denied, handle accordingly
                setContent {
                    SignalMeasurementAppTheme {
                        MainScreen(showSnackbar = true)
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if permissions are already granted
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED) {
            // Permissions are already granted, start the service
            startSignalMeasurementService()
        } else {
            // Request the necessary permissions
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                )
            )
        }

        enableEdgeToEdge()
        setContent {
            SignalMeasurementAppTheme {
                MainScreen()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Bind to SignalMeasurementService
        Intent(this, SignalMeasurementService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        // Unbind from the service
        if (bound) {
            unbindService(connection)
            bound = false
        }
    }

    private fun startSignalMeasurementService() {
        val intent = Intent(this, SignalMeasurementService::class.java)
        startService(intent)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainScreen(showSnackbar: Boolean = false) {
        val context = LocalContext.current
        var measurement by remember { mutableStateOf<SignalMeasurement?>(null) }

        LaunchedEffect(service, bound) {
            if (bound) {
                // Introduce a delay to ensure service is ready
                delay(1000)
                service?.measurementFlow?.collect { value ->
                    Log.d("MainActivity", "Collecting from service: $value")
                    measurement = value
                }
            }
        }

        // Log the received data
        Log.d("MainActivity", "Received measurement in UI: $measurement")

        // Snackbar state
        val snackbarHostState = remember { SnackbarHostState() }
        val coroutineScope = rememberCoroutineScope()

        if (showSnackbar) {
            coroutineScope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "Location permissions are required to measure signal strength. Please grant the permissions in settings.",
                    actionLabel = "Settings"
                )
                if (result == SnackbarResult.ActionPerformed) {
                    // Open app settings if the user clicks on "Settings"
                    val intent = Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    )
                    context.startActivity(intent)
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Signal Measurement") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = Color.White
                    )
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) } // Attach the SnackbarHost
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color(0xFFE3F2FD))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Current Location:",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Latitude: ${measurement?.latitude ?: "Loading..."}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Longitude: ${measurement?.longitude ?: "Loading..."}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Signal: ${if (measurement?.signalStrength ?: 0 > 0) "Available" else "No Signal"}",
                        color = if (measurement?.signalStrength ?: 0 > 0) Color.Green else Color.Red,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { refreshMeasurement(context) }) {
                        Text("Refresh")
                    }
                }
            }
        }
    }

    private fun refreshMeasurement(context: Context) {
        context.startService(Intent(context, SignalMeasurementService::class.java).apply {
            action = "com.example.signalmeasurementapp.ACTION_REFRESH_MEASUREMENT"
        })
    }

    @Preview(showBackground = true)
    @Composable
    fun MainScreenPreview() {
        SignalMeasurementAppTheme {
            MainScreen()
        }
    }
}
