package com.ashkorehennessy.af2location

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ashkorehennessy.af2location.ui.theme.AF2LocationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AF2LocationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var portInput by remember { mutableStateOf("49002") }

    var hasPermissions by remember {
        mutableStateOf(
            checkPermissions(context)
        )
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { perms ->
            val fineLoc = perms[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarseLoc = perms[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
            if (fineLoc || coarseLoc) {
                hasPermissions = true
            } else {
                Toast.makeText(context, context.getString(R.string.toast_permission_loc), Toast.LENGTH_LONG).show()
            }
        }
    )

    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            val permissionsToRequest = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            permissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    Column(modifier = modifier.padding(16.dp)) {
        Text(text = stringResource(R.string.app_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = portInput,
            onValueChange = { portInput = it },
            label = { Text(stringResource(R.string.label_udp_port)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                // 双重保险：点击按钮时再次检查权限
                if (!checkPermissions(context)) {
                    Toast.makeText(context, context.getString(R.string.toast_permission_grant), Toast.LENGTH_SHORT).show()
                    return@Button
                }

                val port = portInput.toIntOrNull() ?: 49002
                val intent = Intent(context, UdpLocationService::class.java)
                intent.putExtra("PORT", port)

                context.startForegroundService(intent)
            },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text(stringResource(R.string.btn_start_service))
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = {
                val intent = Intent(context, UdpLocationService::class.java)
                context.stopService(intent)
            },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text(stringResource(R.string.btn_stop_service))
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = stringResource(R.string.desc_instruction),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary)
    }
}

fun checkPermissions(context: android.content.Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
}