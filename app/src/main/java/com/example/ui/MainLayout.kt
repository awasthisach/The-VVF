package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ChatMessageEntity
import com.example.data.FileEntity
import com.example.ui.theme.BorderDark
import com.example.ui.theme.SurfaceDarkHeader
import com.example.viewmodel.*

@Composable
fun MainLayout(viewModel: SmartViewModel) {
    var activeTab by remember { mutableStateOf(0) } // 0: Local Manager, 1: Drive Sim, 2: AI Assistant

    // Insets-friendly Root Scaffold
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    icon = { Icon(Icons.Filled.Folder, contentDescription = "Local Files") },
                    label = { Text("Local Files") },
                    modifier = Modifier.testTag("nav_tab_local")
                )
                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    icon = { Icon(Icons.Filled.Cloud, contentDescription = "Cloud Sim") },
                    label = { Text("Cloud Manager") },
                    modifier = Modifier.testTag("nav_tab_cloud")
                )
                NavigationBarItem(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    icon = { Icon(Icons.Filled.Psychology, contentDescription = "AI Assistant") },
                    label = { Text("AI Assistant") },
                    modifier = Modifier.testTag("nav_tab_ai")
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (activeTab) {
                0 -> LocalManagerScreen(viewModel)
                1 -> CloudManagerScreen(viewModel)
                2 -> AiAssistantScreen(viewModel)
            }
        }
    }
}

// ==========================================
// SCREEN 1: LOCAL MANAGER SCREEN
// ==========================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocalManagerScreen(viewModel: SmartViewModel) {
    val context = LocalContext.current
    val files by viewModel.allLocalNonSafeFiles.collectAsStateWithLifecycle()
    val searchQueries by viewModel.localSearchQuery.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedLocalFileIds.collectAsStateWithLifecycle()
    val isMultiSelect by viewModel.isMultiSelectMode.collectAsStateWithLifecycle()

    val duplicatesState by viewModel.duplicateScannerState.collectAsStateWithLifecycle()
    val duplicatesProgress by viewModel.duplicateScanProgress.collectAsStateWithLifecycle()
    val duplicatesList by viewModel.duplicateFiles.collectAsStateWithLifecycle()

    val cleanerState by viewModel.cleanerState.collectAsStateWithLifecycle()
    val cleanerProgress by viewModel.cleanerProgress.collectAsStateWithLifecycle()
    val junkList by viewModel.junkFiles.collectAsStateWithLifecycle()

    val safeState by viewModel.safeFolderState.collectAsStateWithLifecycle()

    var showSafeFolderDialog by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("ALL") }

    // Aggregate Storage Information
    val totalLocalSpace = 128_000_000_000L
    val usedFilesSpace = files.sumOf { it.size }
    val junkFilesSpace = junkList.sumOf { it.size }
    val duplicatesFilesSpace = duplicatesList.sumOf { it.size }

    // Filter local list
    val filteredFiles = remember(files, searchQueries, selectedCategory) {
        files.filter { file ->
            val matchesSearch = file.name.contains(searchQueries, ignoreCase = true)
            val matchesCategory = when (selectedCategory) {
                "ALL" -> true
                "IMAGES" -> file.mimeType.startsWith("image/")
                "VIDEOS" -> file.mimeType.startsWith("video/")
                "AUDIO" -> file.mimeType.startsWith("audio/")
                "DOCUMENTS" -> file.mimeType.contains("pdf") || file.mimeType.contains("sheet") || file.mimeType.contains("document") || file.mimeType.contains("text")
                else -> true
            }
            matchesSearch && matchesCategory
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Elegant glowing gradient banner at top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                SurfaceDarkHeader,
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Local Storage",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Secure, offline visual file administration.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }

                        // Secure Safe entrance icon
                        IconButton(
                            onClick = { showSafeFolderDialog = true },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .testTag("safe_folder_entrance")
                        ) {
                            Icon(
                                imageVector = if (safeState is SafeFolderState.Unlocked) Icons.Filled.FolderSpecial else Icons.Filled.Lock,
                                contentDescription = "Safe Folder",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Storage Metrics Summary Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Analytics, "Total files usage", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Files Size", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                }
                                Text(
                                    text = viewModel.formatSize(usedFilesSpace),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.CleaningServices, "Junk files space", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Junk Space", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                }
                                Text(
                                    text = viewModel.formatSize(junkFilesSpace),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (junkFilesSpace > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.FolderSpecial, "Duplicates space", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Duplicates", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                }
                                Text(
                                    text = "${duplicatesList.size} items",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Quick Active Tools Row (Scanners)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { viewModel.runDuplicateScanner() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                    modifier = Modifier.weight(1f).testTag("tool_duplicate")
                ) {
                    Icon(Icons.Filled.Analytics, contentDescription = "Scan duplicates", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Duplicate Scan", fontSize = 12.sp)
                }

                Button(
                    onClick = { viewModel.runJunkCleaner() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer),
                    modifier = Modifier.weight(1f).testTag("tool_junk")
                ) {
                    Icon(Icons.Filled.CleaningServices, contentDescription = "Clean junk", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Junk Clean", fontSize = 12.sp)
                }
            }

            // Search and Category Selector
            OutlinedTextField(
                value = searchQueries,
                onValueChange = { viewModel.updateLocalSearchQuery(it) },
                label = { Text("Search files...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("local_search_input"),
                shape = RoundedCornerShape(12.dp)
            )

            // Horizontal File Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "ALL" to "All Files",
                    "IMAGES" to "Images",
                    "VIDEOS" to "Videos",
                    "AUDIO" to "Audio",
                    "DOCUMENTS" to "Documents"
                ).forEach { (categoryKey, label) ->
                    FilterChip(
                        selected = selectedCategory == categoryKey,
                        onClick = { selectedCategory = categoryKey },
                        label = { Text(label) },
                        modifier = Modifier.testTag("filter_chip_$categoryKey")
                    )
                }
            }

            // File items scrollable listing
            if (filteredFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.Folder,
                            contentDescription = "No Files",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No matching files found.",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "Import more docs or refine search criteria.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredFiles, key = { it.id }) { file ->
                        val isSelected = selectedIds.contains(file.id)
                        LocalFileItemCard(
                            file = file,
                            isSelected = isSelected,
                            onToggleSelect = { viewModel.toggleLocalFileSelection(file.id) },
                            formatSize = { viewModel.formatSize(it) },
                            formatDate = { viewModel.formatDate(it) }
                        )
                    }
                }
            }
        }

        // ==========================================
        // OVERLAY PANEL: DYNAMIC OVERLAYS
        // ==========================================

        // Bottom Multi-select actions bar
        AnimatedVisibility(
            visible = isMultiSelect,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(250)) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(250)) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${selectedIds.size} Selected",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { viewModel.selectAllLocalFiles(filteredFiles) }) {
                            Text("Select All", fontWeight = FontWeight.SemiBold)
                        }

                        IconButton(
                            onClick = { viewModel.moveSelectedToSafe() },
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                .testTag("multi_select_move_safe")
                        ) {
                            Icon(Icons.Filled.Lock, "Move to safe", tint = MaterialTheme.colorScheme.primary)
                        }

                        IconButton(
                            onClick = { viewModel.deleteLocalFilesByIds(selectedIds) },
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                                .testTag("multi_select_delete")
                        ) {
                            Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                        }

                        IconButton(
                            onClick = { viewModel.clearLocalSelection() },
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        ) {
                            Icon(Icons.Filled.Close, "Clear Selection")
                        }
                    }
                }
            }
        }

        // 1. DUPLICATE SCANNER FULLSCREEN MODAL DIALOG
        if (duplicatesState != ScannerState.Idle) {
            Dialog(onDismissRequest = { viewModel.dismissDuplicateScanner() }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Duplicate Scanner",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        if (duplicatesState == ScannerState.Scanning) {
                            CircularProgressIndicator(
                                progress = duplicatesProgress,
                                modifier = Modifier.size(80.dp),
                                strokeWidth = 6.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Identifying byte-identical files...", fontWeight = FontWeight.Medium)
                            Text("${(duplicatesProgress * 100).toInt()}% scanned", style = MaterialTheme.typography.bodySmall)
                        } else if (duplicatesState == ScannerState.Finished) {
                            val dups = duplicatesList.filter { it.isDuplicate }
                            if (dups.isEmpty()) {
                                Icon(Icons.Filled.Check, "Success", modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.tertiary)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("No duplicates found! Your storage is optimal.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(onClick = { viewModel.dismissDuplicateScanner() }) {
                                    Text("Dismiss")
                                }
                            } else {
                                Icon(Icons.Filled.Analytics, "Duplicates", modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.secondary)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Found ${dups.size} byte-identical duplicates!", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                                Text("Free space option: ${viewModel.formatSize(dups.sumOf { it.size })}", fontSize = 12.sp)

                                Spacer(modifier = Modifier.height(16.dp))
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 180.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(dups) { file ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                                .padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Filled.Description, "File", modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(file.name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(viewModel.formatSize(file.size), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    TextButton(onClick = { viewModel.dismissDuplicateScanner() }) {
                                        Text("Cancel")
                                    }
                                    Button(
                                        onClick = { viewModel.deleteSelectedDuplicates() },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                        modifier = Modifier.testTag("execute_delete_duplicates")
                                    ) {
                                        Text("Delete Duplicates")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. JUNK CLEANER MODAL DIALOG
        if (cleanerState != CleanerState.Idle) {
            Dialog(onDismissRequest = { viewModel.cancelJunkCleaner() }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Junk Cleaner Engine",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        when (cleanerState) {
                            CleanerState.Scanning -> {
                                CircularProgressIndicator(
                                    progress = cleanerProgress,
                                    modifier = Modifier.size(80.dp),
                                    strokeWidth = 6.dp,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Analyzing log files, system cache... ")
                                Text("${(cleanerProgress * 100).toInt()}% completed", style = MaterialTheme.typography.bodySmall)
                            }
                            CleanerState.Cleaning -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(80.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Flushing caches & deleting temporary blocks...", fontWeight = FontWeight.Bold)
                            }
                            CleanerState.Finished -> {
                                val totalJunk = junkList.sumOf { it.size }
                                if (totalJunk == 0L) {
                                    Icon(Icons.Filled.Check, "Clean", modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.tertiary)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("Excellent! System is fully optimized.")
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(onClick = { viewModel.cancelJunkCleaner() }) {
                                        Text("Close")
                                    }
                                } else {
                                    Icon(Icons.Filled.CleaningServices, "Junk Collected", modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Found ${viewModel.formatSize(totalJunk)} of temporary junk!",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text("Unnecessary log scripts and thumbnail indices.", fontSize = 11.sp, textAlign = TextAlign.Center)

                                    Spacer(modifier = Modifier.height(24.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        TextButton(onClick = { viewModel.cancelJunkCleaner() }) {
                                            Text("Cancel")
                                        }
                                        Button(
                                            onClick = { viewModel.executeCleanJunk() },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                            modifier = Modifier.testTag("execute_clean_junk")
                                        ) {
                                            Text("Clean Space")
                                        }
                                    }
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }
        }

        // 3. SECURE SAFE FOLDER DIALOG (PIN SETUP / DIAL-PAD)
        if (showSafeFolderDialog) {
            Dialog(onDismissRequest = { showSafeFolderDialog = false }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    shape = RoundedCornerShape(28.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    when (safeState) {
                        is SafeFolderState.PinSetupNeeded -> {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Filled.Lock, "Lock", modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Secure Safe Folder", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (viewModel.pinSetupStep.collectAsStateWithLifecycle().value == "ENTER_PIN")
                                        "Setup your new 4-digit PIN access"
                                    else "Verify your 4-digit PIN code",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )

                                Spacer(modifier = Modifier.height(16.dp))
                                SecurePinDisplayRow(viewModel.inputPinBuffer.collectAsStateWithLifecycle().value)

                                val err by viewModel.pinErrorMessage.collectAsStateWithLifecycle()
                                err?.let {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp, textAlign = TextAlign.Center)
                                }

                                Spacer(modifier = Modifier.height(24.dp))
                                PinKeyboardDialer(
                                    onDigit = { viewModel.appendPinDigit(it) },
                                    onClear = { viewModel.clearLastPinDigit() }
                                )

                                Spacer(modifier = Modifier.height(16.dp))
                                TextButton(onClick = { showSafeFolderDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        }
                        is SafeFolderState.Locked -> {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Filled.Lock, "Locked safe door", modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Enter Safe Folder PIN", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("Files within are locally isolated and hidden.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))

                                Spacer(modifier = Modifier.height(16.dp))
                                SecurePinDisplayRow(viewModel.inputPinBuffer.collectAsStateWithLifecycle().value)

                                val err by viewModel.pinErrorMessage.collectAsStateWithLifecycle()
                                err?.let {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp, textAlign = TextAlign.Center)
                                }

                                Spacer(modifier = Modifier.height(24.dp))
                                PinKeyboardDialer(
                                    onDigit = { viewModel.appendPinDigit(it) },
                                    onClear = { viewModel.clearLastPinDigit() }
                                )

                                Spacer(modifier = Modifier.height(16.dp))
                                TextButton(onClick = { showSafeFolderDialog = false }) {
                                    Text("Dismiss")
                                }
                            }
                        }
                        is SafeFolderState.Unlocked -> {
                            val safeFiles by viewModel.allSafeFiles.collectAsStateWithLifecycle()
                            var selectedSafeIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

                            Column(
                                modifier = Modifier
                                    .padding(20.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.FolderSpecial, "Unlocked Safe folder Content", tint = MaterialTheme.colorScheme.tertiary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Safe Vault", fontWeight = FontWeight.Bold)
                                    }

                                    Row {
                                        IconButton(onClick = { viewModel.lockSafeFolder() }) {
                                            Icon(Icons.Filled.Lock, "Secure lock door", tint = MaterialTheme.colorScheme.primary)
                                        }
                                        IconButton(onClick = { showSafeFolderDialog = false }) {
                                            Icon(Icons.Filled.Close, "Close Door")
                                        }
                                    }
                                }

                                Text(
                                    text = "Locally isolated files. Restoring makes them visible on generic directories again.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )

                                Divider(modifier = Modifier.padding(vertical = 8.dp))

                                if (safeFiles.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(180.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Your Secure Vault folder is currently empty.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 240.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(safeFiles) { sFile ->
                                            val isSelected = selectedSafeIds.contains(sFile.id)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(
                                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                                        else MaterialTheme.colorScheme.surfaceVariant,
                                                        RoundedCornerShape(12.dp)
                                                    )
                                                    .clickable {
                                                        selectedSafeIds = if (isSelected) selectedSafeIds - sFile.id else selectedSafeIds + sFile.id
                                                    }
                                                    .padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(
                                                    checked = isSelected,
                                                    onCheckedChange = {
                                                        selectedSafeIds = if (isSelected) selectedSafeIds - sFile.id else selectedSafeIds + sFile.id
                                                    }
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(Icons.Filled.Description, "File")
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(sFile.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                                                    Text(viewModel.formatSize(sFile.size), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                                }
                                            }
                                        }
                                    }
                                }

                                if (selectedSafeIds.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                viewModel.restoreSelectedFromSafe(selectedSafeIds)
                                                selectedSafeIds = emptySet()
                                                Toast.makeText(context, "Restored successfully", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.weight(1f).testTag("safe_restore_btn")
                                        ) {
                                            Icon(Icons.Filled.Refresh, "Restore")
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Restore Files")
                                        }

                                        Button(
                                            onClick = {
                                                viewModel.deleteLocalFilesByIds(selectedSafeIds)
                                                selectedSafeIds = emptySet()
                                                Toast.makeText(context, "Deleted permanently", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                            modifier = Modifier.weight(1f).testTag("safe_delete_perm_btn")
                                        ) {
                                            Icon(Icons.Filled.Delete, "Delete permanence")
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Delete Perm")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// SUPPORTING LOCAL COMPOSE ITEMS
@Composable
fun LocalFileItemCard(
    file: FileEntity,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    formatSize: (Long) -> String,
    formatDate: (Long) -> String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleSelect() }
            .testTag("file_item_card_${file.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checked/Type badge
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(Icons.Filled.Check, "Selected", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                } else {
                    val icon = when {
                        file.mimeType.startsWith("image/") -> Icons.Filled.Image
                        file.mimeType.startsWith("video/") -> Icons.Filled.VideoFile
                        file.mimeType.startsWith("audio/") -> Icons.Filled.AudioFile
                        else -> Icons.Filled.Description
                    }
                    Icon(icon, "File Type Icon", tint = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = formatSize(file.size), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Text(text = "•", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    Text(text = formatDate(file.lastModified), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }

            if (file.isDuplicate) {
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("Duplicate", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SecurePinDisplayRow(pin: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until 4) {
            val isFilled = i < pin.length
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(
                        if (isFilled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
            )
        }
    }
}

@Composable
fun PinKeyboardDialer(
    onDigit: (String) -> Unit,
    onClear: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val buttons = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("CLEAR", "0", "")
        )

        buttons.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { char ->
                    if (char.isNotEmpty()) {
                        IconButton(
                            onClick = { if (char == "CLEAR") onClear() else onDigit(char) },
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(
                                    if (char == "CLEAR") MaterialTheme.colorScheme.surfaceVariant
                                    else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                )
                                .testTag("pin_dial_${char.lowercase()}")
                        ) {
                            if (char == "CLEAR") {
                                Icon(Icons.Filled.Backspace, "Backspace")
                            } else {
                                Text(char, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                        }
                    } else {
                        // Empty spacer
                        Box(modifier = Modifier.size(56.dp))
                    }
                }
            }
        }
    }
}

// ==========================================
// SCREEN 2: CLOUD MANAGER / DRIVE SIM SCREEN
// ==========================================
@Composable
fun CloudManagerScreen(viewModel: SmartViewModel) {
    val cloudFiles by viewModel.cloudFiles.collectAsStateWithLifecycle()
    val accounts by viewModel.connectedAccounts.collectAsStateWithLifecycle()
    val activeEmail by viewModel.activeAccountEmail.collectAsStateWithLifecycle()
    val cloudQuery by viewModel.cloudSearchQuery.collectAsStateWithLifecycle()
    val selectCloudIds by viewModel.selectedCloudFileIds.collectAsStateWithLifecycle()
    val isCloudMultiSelect by viewModel.isCloudMultiSelectMode.collectAsStateWithLifecycle()

    val isScanningCloud by viewModel.isCloudScanning.collectAsStateWithLifecycle()
    val scanProgress by viewModel.cloudScanProgress.collectAsStateWithLifecycle()
    val scanQuery by viewModel.cloudScanQuery.collectAsStateWithLifecycle()
    val scanResults by viewModel.semanticScanResults.collectAsStateWithLifecycle()

    var showAddAccountDialog by remember { mutableStateOf(false) }

    // Filter cloud files
    val filteredCloudFiles = remember(cloudFiles, cloudQuery) {
        cloudFiles.filter { file ->
            file.name.contains(cloudQuery, ignoreCase = true)
        }
    }

    val activeSubTab by viewModel.activeCloudSubTab.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = activeSubTab,
            containerColor = SurfaceDarkHeader,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = activeSubTab == 0,
                onClick = { viewModel.setCloudSubTab(0) },
                text = { Text("Google Drive", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                icon = { Icon(Icons.Filled.Cloud, contentDescription = "Google Drive", modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = activeSubTab == 1,
                onClick = { viewModel.setCloudSubTab(1) },
                text = { Text("GitHub Tracker", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                icon = { Icon(Icons.Filled.Code, contentDescription = "GitHub Tracker", modifier = Modifier.size(18.dp)) }
            )
        }

        if (activeSubTab == 0) {
            // Multi-Account Switcher Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDarkHeader)
                    .padding(16.dp)
            ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "Cloud Manager",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Simulated Google Drive file organization.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    // Add Virtual account icon
                    IconButton(
                        onClick = { showAddAccountDialog = true },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .testTag("add_cloud_account_btn")
                    ) {
                        Icon(Icons.Filled.Add, "Add Cloud Account", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable Account list
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    accounts.forEach { email ->
                        val isActive = email == activeEmail
                        Card(
                            modifier = Modifier
                                .clickable { viewModel.selectActiveCloudAccount(email) }
                                .testTag("account_chip_$email"),
                            shape = CircleShape,
                            border = BorderStroke(
                                1.dp,
                                if (isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isActive) MaterialTheme.colorScheme.primary else Color.Gray)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(email, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

                                if (accounts.size > 1) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Filled.Logout,
                                        contentDescription = "Logout virtual account",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { viewModel.logoutVirtualAccount(email) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Semantic Scanning Interactive Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, BorderDark)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Psychology, "AI scan", tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("AI Semantic Scan", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Evaluate drive files fitting your semantic criteria context using live Gemini Pro reasoning if configured.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = scanQuery,
                    onValueChange = { viewModel.updateCloudScanQuery(it) },
                    placeholder = { Text("e.g., Financial budget, resumes...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("semantic_scan_query_input"),
                    shape = RoundedCornerShape(8.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (isScanningCloud) {
                    LinearProgressIndicator(
                        progress = scanProgress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Scanning file catalog... ${(scanProgress * 100).toInt()}% match ratings parsed.", fontSize = 11.sp)
                } else {
                    Button(
                        onClick = { viewModel.runCloudSemanticScan() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("run_semantic_scan_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Search Semantic Match Score")
                    }
                }
            }
        }

        // Active Cloud Directory Search
        OutlinedTextField(
            value = cloudQuery,
            onValueChange = { viewModel.updateCloudSearchQuery(it) },
            label = { Text("Search virtual drive directory...") },
            leadingIcon = { Icon(Icons.Filled.Search, "Search cloud") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("cloud_search_input"),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Files List representation
        if (filteredCloudFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("This virtual Cloud Drive catalog is empty.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        } else {
            CloudFileItemsBox(
                modifier = Modifier.weight(1f),
                viewModel = viewModel,
                filteredCloudFiles = filteredCloudFiles,
                selectCloudIds = selectCloudIds,
                isCloudMultiSelect = isCloudMultiSelect,
                scanResults = scanResults
            )
        }
    } else {
        GitHubTrackerScreen(viewModel)
    }
}

    // Add Account dialog modal
    if (showAddAccountDialog) {
        var addName by remember { mutableStateOf("") }
        var addEmail by remember { mutableStateOf("") }
        var inputError by remember { mutableStateOf<String?>(null) }

        Dialog(onDismissRequest = { showAddAccountDialog = false }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Add Simulated Drive Account", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = addName,
                        onValueChange = { addName = it },
                        label = { Text("Account Holder Name") },
                        modifier = Modifier.fillMaxWidth().testTag("add_account_name"),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = addEmail,
                        onValueChange = { addEmail = it },
                        label = { Text("Email Address") },
                        modifier = Modifier.fillMaxWidth().testTag("add_account_email"),
                        shape = RoundedCornerShape(8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )

                    inputError?.let {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showAddAccountDialog = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = {
                                if (addEmail.isBlank() || !addEmail.contains("@")) {
                                    inputError = "Please insert a valid Gmail location."
                                } else {
                                    viewModel.addNewCloudAccount(addName, addEmail)
                                    showAddAccountDialog = false
                                }
                            },
                            modifier = Modifier.testTag("add_account_confirm")
                        ) {
                            Text("Connect Sim")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CloudFileItemsBox(
    modifier: Modifier = Modifier,
    viewModel: SmartViewModel,
    filteredCloudFiles: List<FileEntity>,
    selectCloudIds: Set<Long>,
    isCloudMultiSelect: Boolean,
    scanResults: List<Pair<Long, Int>>
) {
    Box(modifier = modifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            items(filteredCloudFiles, key = { it.id }) { cFile ->
                val isSelected = selectCloudIds.contains(cFile.id)
                val matchResult = scanResults.firstOrNull { it.first == cFile.id }

                CloudFileItemCard(
                    file = cFile,
                    isSelected = isSelected,
                    onToggleSelect = { viewModel.toggleCloudFileSelection(cFile.id) },
                    matchPercent = matchResult?.second,
                    formatSize = { viewModel.formatSize(it) },
                    formatDate = { viewModel.formatDate(it) }
                )
            }
        }

        // Cloud multi-select bottom action container
        AnimatedVisibility(
            visible = isCloudMultiSelect,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(200)) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(200)) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${selectCloudIds.size} Selected Drive Files",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { viewModel.selectAllCloudFiles(filteredCloudFiles) }) {
                            Text("All")
                        }

                        Button(
                            onClick = { viewModel.deleteCloudFilesByIds(selectCloudIds) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Filled.Delete, "Delete drive item", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Unlink", fontSize = 12.sp)
                        }

                        IconButton(onClick = { viewModel.clearCloudSelection() }) {
                            Icon(Icons.Filled.Close, "Cancel Selection")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CloudFileItemCard(
    file: FileEntity,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    matchPercent: Int?,
    formatSize: (Long) -> String,
    formatDate: (Long) -> String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleSelect() }
            .testTag("cloud_file_item_${file.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                val icon = when {
                    file.mimeType.startsWith("image/") -> Icons.Filled.Image
                    file.mimeType.startsWith("video/") -> Icons.Filled.VideoFile
                    file.mimeType.startsWith("audio/") -> Icons.Filled.AudioFile
                    else -> Icons.Filled.Description
                }
                Icon(icon, "Cloud File Icon", tint = MaterialTheme.colorScheme.secondary)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = formatSize(file.size), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Text(text = "•", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    Text(text = formatDate(file.lastModified), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }

            // Semantic matching results badge
            matchPercent?.let { percent ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (percent >= 75) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Match: $percent%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (percent >= 75) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
            }
        }
    }
}

// ==========================================
// SCREEN 3: CONFIG & CHAT / AI ASSISTANT SCREEN
// ==========================================
@Composable
fun AiAssistantScreen(viewModel: SmartViewModel) {
    val key by viewModel.apiKey.collectAsStateWithLifecycle()
    val highThinking by viewModel.highThinkingEnabled.collectAsStateWithLifecycle()
    val isPanelExpanded by viewModel.isApiPanelExpanded.collectAsStateWithLifecycle()

    val chatHistory by viewModel.chatHistory.collectAsStateWithLifecycle()
    val userPromptInput by viewModel.chatInput.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGeminiGenerating.collectAsStateWithLifecycle()
    val thinkingProcess by viewModel.activeThinkingProcess.collectAsStateWithLifecycle()

    var inputKeyBuffer by remember { mutableStateOf(key) }

    Column(modifier = Modifier.fillMaxSize()) {
        // AI Assistant Glowing Header banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceDarkHeader)
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "Gemini AI Assistant",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Inquire, search, organize, or restructure catalog data.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    Row {
                        IconButton(
                            onClick = { viewModel.clearChatHistory() },
                            modifier = Modifier.testTag("clear_chat_history_btn")
                        ) {
                            Icon(Icons.Filled.History, "Clear History", tint = MaterialTheme.colorScheme.error)
                        }

                        IconButton(
                            onClick = { viewModel.toggleApiPanel() },
                            modifier = Modifier.testTag("toggle_settings_panel_btn")
                        ) {
                            Icon(
                                imageVector = if (isPanelExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.Settings,
                                contentDescription = "Secret parameters config"
                            )
                        }
                    }
                }
            }
        }

        // Collapsible API Setup Panel
        AnimatedVisibility(
            visible = isPanelExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, BorderDark),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Gemini API Secret Settings",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Input your Gemini API key below. Changes are saved offline locally within private preferences.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = inputKeyBuffer,
                        onValueChange = { inputKeyBuffer = it },
                        label = { Text("Gemini API Key") },
                        placeholder = { Text("AI Studio Secrets injection or personal key...") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gemini_api_key_input"),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Pro Advanced High-Thinking Toggle Check
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Psychology, "Thinking icon", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("High-Thinking Mode (Pro)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("Engage gemini-3.1-pro-preview with HIGH reasoning level.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        }

                        Switch(
                            checked = highThinking,
                            onCheckedChange = { viewModel.setHighThinkingMode(it) },
                            modifier = Modifier.testTag("high_thinking_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            viewModel.setApiKey(inputKeyBuffer)
                            viewModel.toggleApiPanel()
                        },
                        modifier = Modifier.fillMaxWidth().testTag("apply_api_key_btn")
                    ) {
                        Text("Apply Key Preferences")
                    }
                }
            }
        }

        // Live Chat message pane
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (chatHistory.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Psychology,
                            contentDescription = "No Chat History",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            modifier = Modifier.size(80.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Smart Directory Assistant",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Ask questions like:\n• \"Which local files takes up the most space?\"\n• \"Suggest duplicates I can delete comfortably.\"\n• \"Do I have financial sheets on my cloud account?\"",
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            } else {
                val state = rememberScrollState()
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
                ) {
                    items(chatHistory) { msg ->
                        ChatMessageBubble(msg)
                    }

                    if (isGenerating) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = thinkingProcess ?: "Gemini API is indexing catalog documents...",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Chat text entry block
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = userPromptInput,
                    onValueChange = { viewModel.updateChatInput(it) },
                    placeholder = { Text("Ask anything about your directories...") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_text_field"),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )

                Spacer(modifier = Modifier.width(8.dp))

                FloatingActionButton(
                    onClick = { viewModel.sendChatMessage() },
                    shape = CircleShape,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("send_chat_message_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = "Send Message",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatMessageBubble(message: ChatMessageEntity) {
    val isUser = message.sender == "user"
    val align = if (isUser) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = align
    ) {
        val bubbleColor = if (isUser) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant

        val textColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant

        val border = if (isUser) null
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        val shape = if (isUser) {
            RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 2.dp)
        } else {
            RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 2.dp, bottomEnd = 16.dp)
        }

        Card(
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            border = border,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Sender label
                Text(
                    text = if (isUser) "You" else "Gemini Helper",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Optional collapsible Thinking Process stream (for high thinking mode)
                if (message.isThinking && message.thinkingProcess != null) {
                    var showThoughts by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showThoughts = !showThoughts }
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Psychology, "Thoughts", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (showThoughts) "Hide Thought Chain" else "Show Thought Chain",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (showThoughts) {
                        Text(
                            text = message.thinkingProcess,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(vertical = 4.dp).testTag("thought_chain_log")
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Text(
                    text = message.messageText,
                    fontSize = 13.sp,
                    color = textColor,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
fun GitHubTrackerScreen(viewModel: SmartViewModel) {
    val repoPath by viewModel.githubRepoPath.collectAsStateWithLifecycle()
    val issues by viewModel.githubIssues.collectAsStateWithLifecycle()
    val commits by viewModel.githubCommits.collectAsStateWithLifecycle()
    val isLoading by viewModel.isGithubLoading.collectAsStateWithLifecycle()
    val errorMsg by viewModel.githubError.collectAsStateWithLifecycle()

    var editingRepoPath by remember(repoPath) { mutableStateOf(repoPath) }
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    // State, Backdrop scrim, and animations for Side Panel
    var showCommitHistoryPanel by remember { mutableStateOf(false) }
    
    val conflictedFiles by viewModel.conflictedFiles.collectAsStateWithLifecycle()
    var selectedFileToResolve by remember { mutableStateOf<com.example.data.ConflictFile?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // 1. Repository Selector Input Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Terminal, "Terminal icon", tint = MaterialTheme.colorScheme.primary)
                            Text(
                                "Select Repository Path",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = editingRepoPath,
                                onValueChange = { editingRepoPath = it },
                                placeholder = { Text("owner/repo e.g., google/dagger") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("github_repo_input"),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                            )

                            Button(
                                onClick = { viewModel.updateGithubRepoPath(editingRepoPath) },
                                enabled = !isLoading,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("github_sync_btn")
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Icon(Icons.Filled.Refresh, "Sync data", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Sync")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (errorMsg == null) Color(0xFF22C55E) else MaterialTheme.colorScheme.error)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (errorMsg == null) "Tracking repository: $repoPath" else "Sync Issue Detected",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (errorMsg == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                            )
                        }

                        errorMsg?.let {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = it,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.error,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }

            // Real-Time Repository Sync Monitor
            item {
                val syncState by viewModel.gitSyncState.collectAsStateWithLifecycle()
                
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("repo_sync_monitor_card"),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Title Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (syncState.status == com.example.data.GitSyncStatus.SYNCED) Color(0xFF22C55E)
                                            else if (syncState.status == com.example.data.GitSyncStatus.SYNCING) Color(0xFF3B82F6)
                                            else if (syncState.status == com.example.data.GitSyncStatus.CONFLICT) Color(0xFFEF4444)
                                            else Color(0xFFF59E0B)
                                        )
                                )
                                Text(
                                    "Real-Time Sync Monitor",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            
                            // Repository Badge Capsule
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Filled.Code, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text(
                                        text = syncState.repositoryName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Status Dashboard Panel
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), shape = RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Status Badge & Icon
                            Column(modifier = Modifier.weight(1.1f)) {
                                Text("Sync Status", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                val statusText: String
                                val statusBg: Color
                                val statusColor: Color
                                val statusIcon: androidx.compose.ui.graphics.vector.ImageVector
                                
                                when (syncState.status) {
                                    com.example.data.GitSyncStatus.SYNCED -> {
                                        statusText = "UP-TO-DATE"
                                        statusBg = Color(0xFFE8F5E9)
                                        statusColor = Color(0xFF2E7D32)
                                        statusIcon = Icons.Filled.CheckCircle
                                    }
                                    com.example.data.GitSyncStatus.OUT_OF_SYNC -> {
                                        statusText = "LOCAL BEHIND"
                                        statusBg = Color(0xFFFFF3E0)
                                        statusColor = Color(0xFFE65100)
                                        statusIcon = Icons.Filled.Warning
                                    }
                                    com.example.data.GitSyncStatus.AHEAD -> {
                                        statusText = "LOCAL AHEAD"
                                        statusBg = Color(0xFFE3F2FD)
                                        statusColor = Color(0xFF1565C0)
                                        statusIcon = Icons.Filled.ArrowUpward
                                    }
                                    com.example.data.GitSyncStatus.SYNCING -> {
                                        statusText = "SYNCING..."
                                        statusBg = Color(0xFFECEFF1)
                                        statusColor = Color(0xFF37474F)
                                        statusIcon = Icons.Filled.Refresh
                                    }
                                    com.example.data.GitSyncStatus.CONFLICT -> {
                                        statusText = "MERGE OVERLAPS"
                                        statusBg = Color(0xFFFFEBEE)
                                        statusColor = Color(0xFFC62828)
                                        statusIcon = Icons.Filled.Warning
                                    }
                                }

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = statusBg),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = statusIcon,
                                            contentDescription = null,
                                            tint = statusColor,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = statusText,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = statusColor
                                        )
                                    }
                                }
                            }

                            Box(modifier = Modifier.width(1.dp).height(38.dp).background(MaterialTheme.colorScheme.outlineVariant))

                            Spacer(modifier = Modifier.width(12.dp))

                            // Heartbeat Indicator Display
                            Column(modifier = Modifier.weight(1.1f)) {
                                Text("Last Active Scan", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Settings,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    val minutes = syncState.lastCheckedSecondsAgo / 60
                                    val seconds = syncState.lastCheckedSecondsAgo % 60
                                    val timeStr = if (minutes > 0) "${minutes}m ${seconds}s ago" else "${seconds}s ago"
                                    Text(
                                        text = "Synced $timeStr",
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Sync description feedback
                        Text(
                            text = syncState.latestSyncActionMessage,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        // If behind or ahead, show special count badges
                        if (syncState.remoteAheadCount > 0 || syncState.localAheadCount > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                            ) {
                                if (syncState.remoteAheadCount > 0) {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                                        shape = RoundedCornerShape(20.dp),
                                        border = BorderStroke(1.dp, Color(0xFFFFB74D).copy(alpha = 0.5f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(Icons.Filled.ArrowDownward, null, modifier = Modifier.size(12.dp), tint = Color(0xFFD84315))
                                            Text("${syncState.remoteAheadCount} new remote commits to fetch", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD84315))
                                        }
                                    }
                                }
                                if (syncState.localAheadCount > 0) {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
                                        shape = RoundedCornerShape(20.dp),
                                        border = BorderStroke(1.dp, Color(0xFF90CAF9).copy(alpha = 0.5f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(Icons.Filled.ArrowUpward, null, modifier = Modifier.size(12.dp), tint = Color(0xFF1565C0))
                                            Text("${syncState.localAheadCount} local commits unpushed", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Interactive Sync Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Primary Sync Tool Check
                            OutlinedButton(
                                onClick = { viewModel.triggerFetchSync() },
                                modifier = Modifier.weight(1.1f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Check Sync", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            // Dynamic pull/push actions
                            if (syncState.status == com.example.data.GitSyncStatus.OUT_OF_SYNC) {
                                Button(
                                    onClick = { viewModel.triggerGitPull() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                                    modifier = Modifier.weight(1.2f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Filled.ArrowDownward, null, modifier = Modifier.size(14.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Pull Changes", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            } else if (syncState.status == com.example.data.GitSyncStatus.AHEAD) {
                                Button(
                                    onClick = { viewModel.triggerGitPush() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                                    modifier = Modifier.weight(1.2f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Filled.ArrowUpward, null, modifier = Modifier.size(14.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Push Changes", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }

                        // Simulation sandbox controls
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "SIMULATION SANDBOX (Test Real-time Statuses)",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.triggerSimulateBehind() },
                                enabled = syncState.status != com.example.data.GitSyncStatus.CONFLICT && syncState.status != com.example.data.GitSyncStatus.SYNCING,
                                modifier = Modifier.weight(1.1f),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f))
                            ) {
                                Icon(Icons.Filled.ArrowDownward, null, modifier = Modifier.size(10.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("Set Behind", fontSize = 9.sp)
                            }

                            OutlinedButton(
                                onClick = { viewModel.triggerSimulateAhead() },
                                enabled = syncState.status != com.example.data.GitSyncStatus.CONFLICT && syncState.status != com.example.data.GitSyncStatus.SYNCING,
                                modifier = Modifier.weight(1.1f),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f))
                            ) {
                                Icon(Icons.Filled.ArrowUpward, null, modifier = Modifier.size(10.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("Set Ahead", fontSize = 9.sp)
                            }
                        }
                    }
                }
            }

            // Gemini AI Project README Analyzer Card
            item {
                val isReadmeGenerating by viewModel.isReadmeGenerating.collectAsStateWithLifecycle()
                val generatedReadme by viewModel.generatedReadme.collectAsStateWithLifecycle()
                val readmeThinkingProcess by viewModel.readmeThinkingProcess.collectAsStateWithLifecycle()
                val context = LocalContext.current

                Card(
                    modifier = Modifier.fillMaxWidth().testTag("repo_readme_analyzer_card"),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Title row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(
                                    Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    "Gemini README Analyst",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            
                            // Technology badge
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f))
                            ) {
                                Text(
                                    text = "Gemini Pro/Flash",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Uses Gemini API to traverse local directory structures, analyze Room entities, live file counts, and write a professional README.md.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Live metrics summary display
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), shape = RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val liveFilesCount = viewModel.allLocalNonSafeFiles.collectAsStateWithLifecycle().value.size
                            val liveSafeFilesCount = viewModel.allSafeFiles.collectAsStateWithLifecycle().value.size
                            val liveDuplicatesCount = viewModel.duplicateFiles.collectAsStateWithLifecycle().value.size

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Local Files", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                Text("$liveFilesCount", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Safe Files", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                Text("$liveSafeFilesCount", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Duplicates", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                Text("$liveDuplicatesCount", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Remote Repo", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                val rawPath = viewModel.githubRepoPath.collectAsStateWithLifecycle().value
                                val truncatedPath = if (rawPath.length > 12) {
                                    rawPath.take(10) + "..."
                                } else rawPath
                                Text(truncatedPath, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Thinking Process Panel if generating
                        if (isReadmeGenerating) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), shape = RoundedCornerShape(12.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            "Thinking Process (High-fidelity analysis)...",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    
                                    readmeThinkingProcess?.let { thoughts ->
                                        Text(
                                            text = thoughts,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        // Display formatted README if exists
                        generatedReadme?.let { readmeText ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 250.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), shape = RoundedCornerShape(12.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                val scrollState = rememberScrollState()
                                Column(
                                    modifier = Modifier
                                        .verticalScroll(scrollState)
                                        .fillMaxWidth()
                                ) {
                                    // Custom visual markdown formatter/renderer for the summary
                                    readmeText.split("\n").forEach { line ->
                                        if (line.startsWith("#")) {
                                            val depth = line.takeWhile { it == '#' }.length
                                            val cleanText = line.drop(depth).trim()
                                            Text(
                                                text = cleanText,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = if (depth == 1) 18.sp else if (depth == 2) 16.sp else 14.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            )
                                        } else if (line.trim().startsWith("-") || line.trim().startsWith("*")) {
                                            val cleanText = line.trim().drop(1).trim()
                                            Row(modifier = Modifier.padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text("•", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                                                Text(cleanText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                            }
                                        } else if (line.isNotBlank()) {
                                            Text(
                                                text = line,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                                lineHeight = 16.sp,
                                                modifier = Modifier.padding(vertical = 2.dp)
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.height(4.dp))
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        // Trigger actions Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (generatedReadme == null) {
                                Button(
                                    onClick = { viewModel.generateProjectReadme() },
                                    enabled = !isReadmeGenerating,
                                    modifier = Modifier.fillMaxWidth().testTag("generate_readme_btn"),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Analyze & Generate README.md")
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { viewModel.clearGeneratedReadme() },
                                    modifier = Modifier.weight(1f).testTag("clear_readme_btn"),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Icon(Icons.Filled.Delete, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Clear")
                                }

                                Button(
                                    onClick = {
                                        val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("README", generatedReadme)
                                        clipboardManager.setPrimaryClip(clip)
                                        Toast.makeText(context, "README copied to clipboard!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1.2f).testTag("copy_readme_btn"),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy Markdown")
                                }
                            }
                        }
                    }
                }
            }

            // 2. Commit Frequency Bar Chart Card (Productivity visualization)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, BorderDark)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.TrendingUp, "Productivity Trend", tint = Color(0xFF0EA5E9))
                                Text(
                                    "Commit Activity (Last 30 Days)",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                shape = CircleShape
                            ) {
                                Text(
                                    "${commits.size} Commits",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Code submission frequency over the last 30 days to measure development velocity.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        if (commits.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No commit activity in last 30 days.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        } else {
                            CommitFrequencyChart(commits = commits)

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedButton(
                                onClick = { showCommitHistoryPanel = true },
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .testTag("github_view_commit_history_trigger"),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Filled.History, contentDescription = "History Icon", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("View All Commits", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Merge Conflict Assistant Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.Warning, "Merge Conflict Icon", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                Column {
                                    Text(
                                        "Merge Conflict Assistant",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        "Resolve Git overlaps with smart AI assist",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                            
                            IconButton(
                                onClick = { viewModel.resetConflictsDemo() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Reset Conflicts Demo",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        conflictedFiles.forEach { file ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { selectedFileToResolve = file }
                                    .testTag("conflict_file_item_${file.id}"),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (file.isFullyResolved) 
                                        Color(0xFFE8F5E9).copy(alpha = 0.5f) 
                                    else 
                                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.12f)
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (file.isFullyResolved) Color(0xFF81C784).copy(alpha = 0.5f) else MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(if (file.isFullyResolved) Color(0xFF22C55E) else MaterialTheme.colorScheme.error),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (file.isFullyResolved) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = file.name,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = file.path,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (file.isFullyResolved) Color(0xFF22C55E) else MaterialTheme.colorScheme.error
                                        ),
                                        shape = CircleShape
                                    ) {
                                        Text(
                                            text = if (file.isFullyResolved) "Resolved" else "Needs Attention",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Issues Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.Warning, "Open issues list", tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                        Text(
                            "Open Tasks & Issues",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Text(
                        "${issues.size} Open Issues",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // 4. Open Issues List
            if (issues.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.CheckCircle, "Clean", tint = Color(0xFF22C55E), modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Zero open issues found! Splendid job.", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else {
                items(issues) { issue ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clickable {
                                try {
                                    uriHandler.openUri(issue.htmlUrl)
                                } catch (e: Exception) {
                                    // Safe fallback if browser unavailable
                                }
                            }
                            .testTag("github_issue_${issue.number}"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Avatar or icon info
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                if (issue.user?.avatarUrl != null) {
                                    val imageLoading = coil.compose.rememberAsyncImagePainter(model = issue.user.avatarUrl)
                                    Image(
                                        painter = imageLoading,
                                        contentDescription = "User Avatar",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    val initials = (issue.user?.login?.take(2) ?: "GH").uppercase()
                                    Text(
                                        initials,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }

                            // Info details
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.15f)),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            "#${issue.number}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFEF4444),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    Text(
                                        "by @${issue.user?.login ?: "unknown"}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = issue.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Comments badge
                            if (issue.comments > 0) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Comment,
                                        "Comments",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        "${issue.comments}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Side Panel overlay matching material design specifications ---
        if (showCommitHistoryPanel) {
            // Semi-transparent backdrop to dim the rest of the application
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { showCommitHistoryPanel = false }
                    .testTag("github_commits_sheet_backdrop")
            )

            // Animated slide-in transition for the Side sheet
            AnimatedVisibility(
                visible = showCommitHistoryPanel,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                modifier = Modifier
                    .fillMaxHeight()
                    .width(320.dp)
                    .align(Alignment.CenterEnd)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(320.dp)
                        .clickable(enabled = false) { } // block propagation of click actions
                        .testTag("github_commits_side_panel"),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 80.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfaceDarkHeader)
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column {
                                    Text(
                                        "Commit History",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "Last 30 Days",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { showCommitHistoryPanel = false },
                                modifier = Modifier.testTag("github_commits_close_btn")
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Close commit history side panel")
                            }
                        }

                        // Active repository indicator sub-banner
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "Repository: $repoPath",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Scrollable List of Commit Objects
                        if (commits.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No recent commits found.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(commits) { commitItem ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Author details
                                                Text(
                                                    text = commitItem.commit.author.name,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )

                                                // Clean SHA digest tag
                                                val shortSha = if (commitItem.sha.length > 7) commitItem.sha.take(7) else commitItem.sha
                                                Card(
                                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = shortSha,
                                                        fontSize = 10.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Medium,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))

                                            // Action Message
                                            Text(
                                                text = commitItem.commit.message,
                                                fontSize = 11.sp,
                                                lineHeight = 15.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )

                                            Spacer(modifier = Modifier.height(6.dp))

                                            // Commit Date (formatted)
                                            val readableDate = remember(commitItem.commit.author.date) {
                                                commitItem.commit.author.date
                                                    .replace("T", " ")
                                                    .replace("Z", " UTC")
                                            }
                                            Text(
                                                text = readableDate,
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal dialog trigger for resolution logic
    selectedFileToResolve?.let { file ->
        val currentFileState = conflictedFiles.find { it.id == file.id } ?: file
        MergeConflictResolverDialog(
            file = currentFileState,
            viewModel = viewModel,
            onDismiss = { selectedFileToResolve = null }
        )
    }
}

@Composable
fun CommitFrequencyChart(commits: List<com.example.data.GitHubCommit>) {
    val dayFrequencies = remember(commits) {
        val map = mutableMapOf<String, Int>()
        
        val sdfSource = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val sdfTarget = java.text.SimpleDateFormat("MMM dd", java.util.Locale.US)
        
        // Initialize map with 0 for all of last 30 days chronologically
        for (i in 0 until 30) {
            val cal = java.util.Calendar.getInstance()
            cal.add(java.util.Calendar.DAY_OF_YEAR, - (29 - i))
            val key = sdfTarget.format(cal.time)
            map[key] = 0
        }
        
        // Populate actual counts
        commits.forEach { commit ->
            try {
                val dateStr = commit.commit.author.date
                if (dateStr.length >= 10) {
                    val parsedDate = sdfSource.parse(dateStr.substring(0, 10))
                    if (parsedDate != null) {
                        val formatted = sdfTarget.format(parsedDate)
                        if (map.containsKey(formatted)) {
                            map[formatted] = (map[formatted] ?: 0) + 1
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore parsing errors
            }
        }
        map.toList() // Keeps insertion order (chronological)
    }

    val maxCommits = remember(dayFrequencies) {
        val max = dayFrequencies.maxOfOrNull { it.second } ?: 1
        if (max == 0) 1 else max
    }

    // Scrollable layout containing the Canvas bar graph
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            dayFrequencies.forEach { (date, count) ->
                Column(
                    modifier = Modifier.width(36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Count label
                    Text(
                        text = "$count",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (count > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )

                    // Vertical Bar utilizing elegant DrawScope sizing
                    val barHeightFraction = count.toFloat() / maxCommits.toFloat()
                    
                    Box(
                        modifier = Modifier
                            .height(100.dp)
                            .width(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight(barHeightFraction.coerceIn(0.01f, 1f))
                                .fillMaxWidth()
                                .clip(CircleShape)
                                .background(
                                    color = if (count > 0) MaterialTheme.colorScheme.primary else Color.Transparent
                                )
                        )
                    }

                    // Date label (e.g., Jun 16)
                    Text(
                        text = date,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun MergeConflictResolverDialog(
    file: com.example.data.ConflictFile,
    viewModel: SmartViewModel,
    onDismiss: () -> Unit
) {
    val isAiResolvingBlock by viewModel.isAiResolvingBlock.collectAsStateWithLifecycle()

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(24.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Resolve Overlaps: ${file.name}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Merging '${file.incomingBranch}' into '${file.currentBranch}'",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Close Dialog")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Scrollable Conflicts List
                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(file.blocks) { block ->
                            val isResolvingThisBlock = isAiResolvingBlock[block.id] == true

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    // Block Descriptor Header
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Conflict Segment (Lines Starting at ${block.lineStart})",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )

                                        if (block.resolutionChoice != null) {
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFF22C55E)),
                                                shape = CircleShape
                                            ) {
                                                Text(
                                                    text = "Status: Resolved",
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        } else {
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                                shape = CircleShape
                                            ) {
                                                Text(
                                                    text = "Unresolved",
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = block.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.height(14.dp))

                                    // Choices Panels
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // 1. Current Branch (Ours) Box
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    viewModel.resolveConflictBlock(file.id, block.id, "ours")
                                                }
                                                .testTag("resolve_ours_${block.id}"),
                                            border = BorderStroke(
                                                width = if (block.resolutionChoice == "ours") 3.dp else 1.dp,
                                                color = if (block.resolutionChoice == "ours") Color(0xFF22C55E) else Color(0xFF81C784).copy(alpha = 0.5f)
                                            ),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (block.resolutionChoice == "ours") 
                                                    Color(0xFFE8F5E9).copy(alpha = 0.8f) 
                                                else 
                                                    Color(0xFFE8F5E9).copy(alpha = 0.3f)
                                            ),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        "Current Change (HEAD - ${file.currentBranch})",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF2E7D32)
                                                    )
                                                    if (block.resolutionChoice == "ours") {
                                                        Icon(Icons.Filled.Check, contentDescription = "Selected", tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = block.currentCode,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF1B5E20),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(Color.White.copy(alpha = 0.5f))
                                                        .padding(6.dp)
                                                )
                                            }
                                        }

                                        // 2. Incoming Branch (Theirs) Box
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    viewModel.resolveConflictBlock(file.id, block.id, "theirs")
                                                }
                                                .testTag("resolve_theirs_${block.id}"),
                                            border = BorderStroke(
                                                width = if (block.resolutionChoice == "theirs") 3.dp else 1.dp,
                                                color = if (block.resolutionChoice == "theirs") Color(0xFF22C55E) else Color(0xFFFFB74D).copy(alpha = 0.5f)
                                            ),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (block.resolutionChoice == "theirs") 
                                                    Color(0xFFFFF3E0).copy(alpha = 0.8f) 
                                                else 
                                                    Color(0xFFFFF3E0).copy(alpha = 0.3f)
                                            ),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        "Incoming Change (Branch - ${file.incomingBranch})",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFFE65100)
                                                    )
                                                    if (block.resolutionChoice == "theirs") {
                                                        Icon(Icons.Filled.Check, contentDescription = "Selected", tint = Color(0xFFE65100), modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = block.incomingCode,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.sp,
                                                    color = Color(0xFFD84315),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(Color.White.copy(alpha = 0.5f))
                                                        .padding(6.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    // Manual and AI Strategies Row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { viewModel.resolveConflictBlock(file.id, block.id, "both") },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(
                                                width = if (block.resolutionChoice == "both") 2.dp else 1.dp,
                                                color = if (block.resolutionChoice == "both") Color(0xFF22C55E) else MaterialTheme.colorScheme.outline
                                            ),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = if (block.resolutionChoice == "both") Color(0xFFE8F5E9).copy(alpha = 0.5f) else Color.Transparent
                                            )
                                        ) {
                                            Text("Keep BOTH", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Button(
                                            onClick = { viewModel.resolveConflictBlockAI(file.id, block.id) },
                                            enabled = !isResolvingThisBlock,
                                            modifier = Modifier
                                                .weight(1.3f)
                                                .testTag("resolve_ai_${block.id}"),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (block.resolutionChoice == "ai") Color(0xFF22C55E) else MaterialTheme.colorScheme.secondary
                                            )
                                        ) {
                                            if (isResolvingThisBlock) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Filled.Bolt, 
                                                    contentDescription = "AI Resolver", 
                                                    modifier = Modifier.size(14.dp),
                                                    tint = Color.White
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("AI Smart Merge", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                        }
                                    }

                                    // Result preview block
                                    block.resolvedCode?.let { resolvedCode ->
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Text(
                                            "Selected Resolution Preview:",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(6.dp))
                                                .padding(10.dp)
                                        ) {
                                            Text(
                                                text = resolvedCode,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                color = Color(0xFF4CAF50)
                                            )
                                        }
                                        
                                        if (block.resolutionChoice == "ai") {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                "✨ Automated Gemini AI Suggestion formulated successfully.",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Footer Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            viewModel.markFileAsCompleted(file.id)
                            onDismiss()
                        },
                        enabled = file.blocks.all { it.resolutionChoice != null },
                        modifier = Modifier
                            .weight(1.5f)
                            .testTag("submit_conflict_resolution"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF22C55E),
                            disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        )
                    ) {
                        Icon(imageVector = Icons.Filled.Done, contentDescription = "Done Icon")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Apply Resolution", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
