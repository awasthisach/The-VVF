package com.vvf.smartfilemanager

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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

import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {
    private val viewModel: SmartViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            viewModel.scanRealDeviceFiles(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Prevent screenshots and recent screen previews to protect private files
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        
        enableEdgeToEdge()

        // Check and request appropriate permissions
        checkAndRequestPermissions()

        setContent {
            val isDarkTheme by viewModel.isDarkMode.collectAsStateWithLifecycle()
            val context = androidx.compose.ui.platform.LocalContext.current
            MyApplicationTheme(darkTheme = isDarkTheme) {
                StoragePermissionGate(
                    onPermissionsGranted = {
                        viewModel.scanRealDeviceFiles(context)
                    }
                ) {
                    MainLayout(viewModel = viewModel)
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
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

        if (hasAllPermissions) {
            // Permissions already granted, start scanning
            viewModel.scanRealDeviceFiles(this)
        } else {
            // Request permissions
            permissionLauncher.launch(permissionsNeeded)
        }
    }
}
