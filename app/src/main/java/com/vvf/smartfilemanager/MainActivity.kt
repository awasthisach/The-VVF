package com.vvf.smartfilemanager

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vvf.smartfilemanager.ui.MainLayout
import com.vvf.smartfilemanager.ui.StoragePermissionGate
import com.vvf.smartfilemanager.ui.theme.MyApplicationTheme
import com.vvf.smartfilemanager.viewmodel.SmartViewModel
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier

import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {
    private val viewModel: SmartViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        Log.e("VVF_STARTUP", "permissionLauncher callback. allGranted = $allGranted")
        if (allGranted) {
            viewModel.scanRealDeviceFiles(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("VVF_TRACE", "VVF_TRACE: Activity Created")
        Log.e("VVF_STARTUP", "MainActivity.onCreate() STARTED")
        
        // Prevent screenshots and recent screen previews to protect private files (commented out for streaming emulator preview compatibility)
        // window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        
        enableEdgeToEdge()

        // Check and request appropriate permissions (disabled on immediate launch to prevent system permission dialog from blocking the initial render in emulator preview)
        // checkAndRequestPermissions()

        setContent {
            Log.e("VVF_STARTUP", "MainActivity.setContent() Composition Started")
            val isDarkTheme by viewModel.isDarkMode.collectAsStateWithLifecycle()
            val context = androidx.compose.ui.platform.LocalContext.current
            Log.e("VVF_STARTUP", "MainActivity.setContent() Theme collected. isDarkTheme = $isDarkTheme")
            MyApplicationTheme(darkTheme = isDarkTheme) {
                androidx.compose.material3.Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background
                ) {
                    StoragePermissionGate(
                        onPermissionsGranted = {
                            Log.e("VVF_STARTUP", "StoragePermissionGate.onPermissionsGranted callback triggered")
                            viewModel.scanRealDeviceFiles(context)
                        }
                    ) {
                        Log.e("VVF_STARTUP", "StoragePermissionGate.content() content block composition")
                        MainLayout(viewModel = viewModel)
                    }
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        android.util.Log.d("VVF_TRACE", "VVF_TRACE: Permission Check Started")
        val permissionsNeeded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val hasAllPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val allStandard = arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            ).all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
            val visualSelected = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
            allStandard || visualSelected
        } else {
            permissionsNeeded.all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }
        }

        Log.e("VVF_STARTUP", "checkAndRequestPermissions(): hasAllPermissions = $hasAllPermissions")

        if (hasAllPermissions) {
            // Permissions already granted, start scanning
            viewModel.scanRealDeviceFiles(this)
        } else {
            // Request permissions
            Log.e("VVF_STARTUP", "Launching permissionLauncher for: ${permissionsNeeded.joinToString()}")
            permissionLauncher.launch(permissionsNeeded)
        }
    }
}
