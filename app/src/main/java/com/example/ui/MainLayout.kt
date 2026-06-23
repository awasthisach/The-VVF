package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ChatMessageEntity
import com.example.data.FileEntity
import com.example.ui.theme.BorderDark
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.example.data.GitHubRepoFile
import com.example.viewmodel.*

@Composable
fun MainLayout(viewModel: SmartViewModel) {
    var activeTab by remember { mutableStateOf(1) } // Default to Browse (1) matching Google Files landing page!

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
                    icon = { Icon(Icons.Filled.CleaningServices, contentDescription = "Clean") },
                    label = { Text("Clean") },
                    modifier = Modifier.testTag("nav_tab_clean")
                )
                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    icon = { Icon(Icons.Filled.Folder, contentDescription = "Browse") },
                    label = { Text("Browse") },
                    modifier = Modifier.testTag("nav_tab_local")
                )
                NavigationBarItem(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    icon = { Icon(Icons.Filled.Cloud, contentDescription = "Cloud Sim") },
                    label = { Text("Cloud Manager") },
                    modifier = Modifier.testTag("nav_tab_cloud")
                )
                NavigationBarItem(
                    selected = activeTab == 3,
                    onClick = { activeTab = 3 },
                    icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = "AI Assistant") },
                    label = { Text("AI Assistant") },
                    modifier = Modifier.testTag("nav_tab_ai")
                )
            }
        }
    ) { paddingValues ->
        val isDarkTheme by viewModel.isDarkMode.collectAsStateWithLifecycle()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Elegant top row with title and dynamic Material Theme switch pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = "Smart Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "Smart Files",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Developer Console",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable { viewModel.setDarkMode(!isDarkTheme) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("global_theme_toggle"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                        contentDescription = "Theme Toggle",
                        tint = if (isDarkTheme) Color(0xFF60A5FA) else Color(0xFFF59E0B),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isDarkTheme) "Dark Mode" else "Light Mode",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (activeTab) {
                    0 -> CleanScreen(viewModel)
                    1 -> BrowseScreen(viewModel)
                    2 -> CloudManagerScreen(viewModel)
                    3 -> AiAssistantScreen(viewModel)
                }
            }
        }
    }

    val context = LocalContext.current

    // --- Overlay Screens ---
    val activeViewerFile by viewModel.activeViewerFile.collectAsStateWithLifecycle()

    // 1. Storage Cleaner Panel
    StorageCleanerPanel(viewModel = viewModel)

    // 2. Custom Animated File Viewer
    AnimatedFileViewer(
        file = activeViewerFile,
        onDismiss = { viewModel.closeFileInViewer() },
        onDelete = { file -> viewModel.deleteLocalFilesByIds(setOf(file.id)) },
        formatSize = { viewModel.formatSize(it) },
        formatDate = { viewModel.formatDate(it) }
    )

    // 3. Document Scanner interface
    DocumentScannerInterface(
        viewModel = viewModel,
        onSavedCallback = {
            Toast.makeText(context, "Scanned PDF compiled and automatically saved into local directory!", Toast.LENGTH_LONG).show()
        }
    )
}

// ==========================================
// SCREEN 1: GOOGLE FILES CLEAN SCREEN
// ==========================================
@Composable
fun CleanScreen(viewModel: SmartViewModel) {
    val context = LocalContext.current
    val files by viewModel.allLocalNonSafeFiles.collectAsStateWithLifecycle()
    val junkList by viewModel.junkFiles.collectAsStateWithLifecycle()
    val duplicatesList by viewModel.duplicateFiles.collectAsStateWithLifecycle()
    
    val duplicatesState by viewModel.duplicateScannerState.collectAsStateWithLifecycle()
    val duplicatesProgress by viewModel.duplicateScanProgress.collectAsStateWithLifecycle()
    
    val cleanerState by viewModel.cleanerState.collectAsStateWithLifecycle()
    val cleanerProgress by viewModel.cleanerProgress.collectAsStateWithLifecycle()
    
    val usedFilesSpace = files.sumOf { it.size }
    val junkFilesSpace = junkList.sumOf { it.size }
    
    val totalLocalSpace = 120_000_000_000L // 120 GB
    val usedSpace = usedFilesSpace + junkFilesSpace + duplicatesList.sumOf { it.size }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp)
    ) {
        // App Header like Files by Google Clean section
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        Icons.Filled.CleaningServices, 
                        contentDescription = "Clean Icon", 
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "Clean | साफ़ करें",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "Free up space of your local system",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        // Storage Progress Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("clean_storage_overview_card"),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(70.dp)) {
                        CircularProgressIndicator(
                            progress = { (usedSpace.toFloat() / totalLocalSpace.toFloat()).coerceIn(0.01f, 1f) },
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 8.dp,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${((usedSpace.toFloat() / totalLocalSpace.toFloat()) * 100).toInt()}%",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text("Used", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Internal Storage Device",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${viewModel.formatSize(usedSpace)} used of 120 GB",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Clean junk or duplicate files to recover space.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }

        // Action Card 0: Advanced Storage Cleaner Scan card
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("advanced_storage_cleaner_card"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                ),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.CleaningServices,
                                contentDescription = "Deep Cleaner",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Storage Cleaner Utility | संग्रहण क्लीनर",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Scans local structures to identify clone duplicates and giant temporary files for quick deletion.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Instant Multi-Delete",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )

                        Button(
                            onClick = { viewModel.setStorageCleanerActive(true) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("tool_deep_scan")
                        ) {
                            Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Fast Scan Repos")
                        }
                    }
                }
            }
        }

        // Action Card 1: Junk Files Cleaner
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("junk_clean_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.CleaningServices,
                                contentDescription = "Junk Sweeper",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Junk & Temp Files | अस्थायी फ़ाइलें",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Clear logs, temporary cache, and redundant metrics",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${viewModel.formatSize(junkFilesSpace)} can be freed",
                            fontWeight = FontWeight.Bold,
                            color = if (junkFilesSpace > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        Button(
                            onClick = { viewModel.runJunkCleaner() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.testTag("tool_junk")
                        ) {
                            Icon(Icons.Filled.CleaningServices, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clean")
                        }
                    }
                }
            }
        }

        // Action Card 2: Duplicate Files Scanner
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("duplicate_clean_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.FolderSpecial,
                                contentDescription = "Duplicates Detector",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Duplicate Files | दुगुनी फ़ाइलें",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Identify identical images and files occupying double space",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val dups = duplicatesList.filter { it.isDuplicate }
                        Text(
                            text = if (dups.isNotEmpty()) "${dups.size} twin items (${viewModel.formatSize(dups.sumOf { it.size })})"
                                   else "Clean & Optimized",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )

                        Button(
                            onClick = { viewModel.runDuplicateScanner() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("tool_duplicate")
                        ) {
                            Icon(Icons.Filled.Analytics, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scan Duplicates")
                        }
                    }
                }
            }
        }

        // Action Card 3: Gemini Project README Analyzer Card
        item {
            val isReadmeGenerating by viewModel.isReadmeGenerating.collectAsStateWithLifecycle()
            val generatedReadme by viewModel.generatedReadme.collectAsStateWithLifecycle()
            val readmeThinkingProcess by viewModel.readmeThinkingProcess.collectAsStateWithLifecycle()

            Card(
                modifier = Modifier.fillMaxWidth().testTag("repo_readme_analyzer_card"),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
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
                        
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f))
                        ) {
                            Text(
                                text = "Gemini AI",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Analyzes local directory configurations, room tables, and sync files to construct a premium project README.md.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), shape = RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val liveFilesCount = files.size
                        val liveSafeFilesCount = viewModel.allSafeFiles.collectAsStateWithLifecycle().value.size
                        val liveDuplicatesCount = duplicatesList.size

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Local Files", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            Text("$liveFilesCount", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Safe Vault", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            Text("$liveSafeFilesCount", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Duplicates", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            Text("$liveDuplicatesCount", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

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
                                        "Thinking Process...",
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

                    generatedReadme?.let { readmeText ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
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
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

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
                                Text("Generate README")
                            }
                        } else {
                            OutlinedButton(
                                onClick = { viewModel.clearGeneratedReadme() },
                                modifier = Modifier.weight(1f).testTag("clear_readme_btn"),
                                shape = RoundedCornerShape(8.dp)
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
                                    Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1.2f).testTag("copy_readme_btn"),
                                shape = RoundedCornerShape(8.dp)
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
    }

    if (duplicatesState != ScannerState.Idle) {
        Dialog(onDismissRequest = { viewModel.dismissDuplicateScanner() }) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Duplicate Scanner", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    if (duplicatesState == ScannerState.Scanning) {
                        CircularProgressIndicator(
                            progress = { duplicatesProgress },
                            modifier = Modifier.size(80.dp),
                            strokeWidth = 6.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Looking for duplicate file bytes...", fontWeight = FontWeight.Medium)
                    } else if (duplicatesState == ScannerState.Finished) {
                        val dups = duplicatesList.filter { it.isDuplicate }
                        if (dups.isEmpty()) {
                            Icon(Icons.Filled.Check, "Success", modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("No duplicate files found!", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(onClick = { viewModel.dismissDuplicateScanner() }) {
                                Text("Dismiss")
                            }
                        } else {
                            Icon(Icons.Filled.FolderSpecial, "Duplicates", modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Found ${dups.size} duplicate items!", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                            Text("Freeable space: ${viewModel.formatSize(dups.sumOf { it.size })}", fontSize = 12.sp)

                            Spacer(modifier = Modifier.height(16.dp))
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(dups, key = { "dup_${it.id}" }) { file ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(file.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                                        Text(viewModel.formatSize(file.size), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { viewModel.dismissDuplicateScanner() }, modifier = Modifier.weight(1f)) {
                                    Text("Cancel")
                                }
                                Button(
                                    onClick = {
                                        viewModel.deleteLocalFilesByIds(dups.map { it.id }.toSet())
                                        viewModel.dismissDuplicateScanner()
                                        Toast.makeText(context, "Duplicates removed!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.weight(1.3f)
                                ) {
                                    Text("Remove All")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (cleanerState != CleanerState.Idle) {
        Dialog(onDismissRequest = { viewModel.cancelJunkCleaner() }) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Junk Space Cleaner", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    if (cleanerState == CleanerState.Scanning) {
                        CircularProgressIndicator(
                            progress = { cleanerProgress },
                            modifier = Modifier.size(80.dp),
                            strokeWidth = 6.dp,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Shredding temporary files and cache...", fontSize = 12.sp)
                    } else if (cleanerState == CleanerState.Finished) {
                        Icon(Icons.Filled.Check, "Completed", modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("System storage is optimized successfully!", fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { viewModel.cancelJunkCleaner() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)) {
                            Text("Perfect")
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// SCREEN 2: GOOGLE FILES BROWSE SCREEN
// ==========================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrowseScreen(viewModel: SmartViewModel) {
    val context = LocalContext.current
    val files by viewModel.allLocalNonSafeFiles.collectAsStateWithLifecycle()
    val searchQueries by viewModel.localSearchQuery.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedLocalFileIds.collectAsStateWithLifecycle()
    val isMultiSelect by viewModel.isMultiSelectMode.collectAsStateWithLifecycle()
    val safeState by viewModel.safeFolderState.collectAsStateWithLifecycle()

    var showSafeFolderDialog by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("ALL") }

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
            
            // Search Bar matching Google Files aesthetic
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                OutlinedTextField(
                    value = searchQueries,
                    onValueChange = { viewModel.updateLocalSearchQuery(it) },
                    placeholder = { Text("Search your local files...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary) },
                    trailingIcon = {
                        IconButton(onClick = { showSafeFolderDialog = true }) {
                            Icon(
                                imageVector = if (safeState is SafeFolderState.Unlocked) Icons.Filled.FolderSpecial else Icons.Filled.Lock,
                                contentDescription = "Safe Vault",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("local_search_input"),
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    ),
                    singleLine = true
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                item {
                    Text(
                        text = "Categories | श्रेणियाँ",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    HorizontalCategoryGrid(
                        selectedCategory = selectedCategory,
                        onCategorySelect = { selectedCategory = it }
                    )
                }

                item {
                    Text(
                        text = "Collections | संग्रह",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable { showSafeFolderDialog = true }
                            .testTag("safe_folder_entrance_card"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (safeState is SafeFolderState.Unlocked) Icons.Filled.FolderSpecial else Icons.Filled.Lock,
                                    contentDescription = "Safe Folder",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Safe Folder | सुरक्षित फ़ोल्डर",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = if (safeState is SafeFolderState.Unlocked) "Accessible & Unlocked" else "Locked via secure 4-digit setup",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                            Icon(Icons.Filled.FolderSpecial, "Unlock", tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Internal Storage ($selectedCategory files)",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "${filteredFiles.size} items",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (filteredFiles.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Filled.Folder,
                                    contentDescription = "Empty",
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("No local archives in this category.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        }
                    }
                } else {
                    items(filteredFiles, key = { it.id }) { file ->
                        val isSelected = selectedIds.contains(file.id)
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            LocalFileItemCard(
                                file = file,
                                isSelected = isSelected,
                                onOpenFile = { viewModel.openFileInViewer(file) },
                                onToggleSelect = { viewModel.toggleLocalFileSelection(file.id) },
                                formatSize = { viewModel.formatSize(it) },
                                formatDate = { viewModel.formatDate(it) }
                            )
                        }
                    }
                }
            }
        }

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

        // Speed dial menu choices
        var showFabMenu by remember { mutableStateOf(false) }

        if (showFabMenu) {
            // Semi-transparent backdrop to focus dial options
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { showFabMenu = false }
            )
            
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 96.dp, end = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End
            ) {
                // Option 1: Capture & Scan Document
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.clickable {
                        viewModel.setDocumentScannerActive(true)
                        showFabMenu = false
                    }
                ) {
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Text(
                            "Scan Document (Camera PDF)",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    FloatingActionButton(
                        onClick = {
                            viewModel.setDocumentScannerActive(true)
                            showFabMenu = false
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Filled.PhotoCamera, "Scan PDF")
                    }
                }

                // Option 2: Quick Mock Import
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.clickable {
                        val randNo = (1000..9999).random()
                        val options = listOf(
                            Triple("Personal_Contract_$randNo.pdf", "application/pdf", "Documents"),
                            Triple("Trip_Selfie_View_$randNo.jpg", "image/jpeg", "Images"),
                            Triple("Vlog_Nature_Short_$randNo.mp4", "video/mp4", "Videos"),
                            Triple("Instrumental_Beat_$randNo.mp3", "audio/mp3", "Audio")
                        )
                        val choice = options.random()
                        viewModel.addLocalSimulatedFile(
                            name = choice.first,
                            size = (400000..25000000).random().toLong(),
                            mimeType = choice.second,
                            path = "InternalStorage/SimulatedWorkspace/${choice.third}/${choice.first}"
                        )
                        Toast.makeText(context, "Successfully simulated mock document import!", Toast.LENGTH_SHORT).show()
                        showFabMenu = false
                    }
                ) {
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Text(
                            "Quick Mock Import",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    FloatingActionButton(
                        onClick = {
                            val randNo = (1000..9999).random()
                            val options = listOf(
                                Triple("Personal_Contract_$randNo.pdf", "application/pdf", "Documents"),
                                Triple("Trip_Selfie_View_$randNo.jpg", "image/jpeg", "Images"),
                                Triple("Vlog_Nature_Short_$randNo.mp4", "video/mp4", "Videos"),
                                Triple("Instrumental_Beat_$randNo.mp3", "audio/mp3", "Audio")
                            )
                            val choice = options.random()
                            viewModel.addLocalSimulatedFile(
                                name = choice.first,
                                size = (400000..25000000).random().toLong(),
                                mimeType = choice.second,
                                path = "InternalStorage/SimulatedWorkspace/${choice.third}/${choice.first}"
                            )
                            Toast.makeText(context, "Successfully simulated mock document import!", Toast.LENGTH_SHORT).show()
                            showFabMenu = false
                        },
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Filled.CloudUpload, "Import")
                    }
                }
            }
        }

        // Floating Action Button to Import Simulated Files directly
        FloatingActionButton(
            onClick = { showFabMenu = !showFabMenu },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 24.dp, end = 24.dp)
                .testTag("import_file_fab"),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Icon(
                imageVector = if (showFabMenu) Icons.Filled.Close else Icons.Filled.Add,
                contentDescription = "Expand Options"
            )
        }

        if (showSafeFolderDialog) {
            Dialog(onDismissRequest = { showSafeFolderDialog = false }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .testTag("safe_folder_dialog_card"),
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
                                Icon(Icons.Filled.Lock, "Locked", modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Enter Safe Folder PIN", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("Unlock safe vault files.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))

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
                                        Icon(Icons.Filled.FolderSpecial, "Safe Vault Unlocked", tint = MaterialTheme.colorScheme.tertiary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Safe Vault", fontWeight = FontWeight.Bold)
                                    }

                                    Row {
                                        IconButton(onClick = { viewModel.lockSafeFolder() }) {
                                            Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                        IconButton(onClick = { showSafeFolderDialog = false }) {
                                            Icon(Icons.Filled.Close, null)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                if (safeFiles.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Filled.Lock, "Empty vault", modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("Safe folder is empty.", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 240.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(safeFiles, key = { "safe_${it.id}" }) { file ->
                                            val isSafeSelected = selectedSafeIds.contains(file.id)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        selectedSafeIds = if (isSafeSelected) selectedSafeIds - file.id else selectedSafeIds + file.id
                                                    }
                                                    .background(
                                                        if (isSafeSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                                        RoundedCornerShape(12.dp)
                                                    )
                                                    .padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Filled.FolderSpecial, null, tint = MaterialTheme.colorScheme.secondary)
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                                    Text(viewModel.formatSize(file.size), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                                }
                                                Checkbox(
                                                    checked = isSafeSelected,
                                                    onCheckedChange = {
                                                        selectedSafeIds = if (isSafeSelected) selectedSafeIds - file.id else selectedSafeIds + file.id
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))
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
                                            Icon(Icons.Filled.Refresh, null)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Restore")
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
                                            Icon(Icons.Filled.Delete, null)
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

@Composable
fun HorizontalCategoryGrid(
    selectedCategory: String,
    onCategorySelect: (String) -> Unit
) {
    val itemsList = listOf(
        Triple("ALL", "All Files", Icons.Filled.Folder to Color(0xFF6B7280)),
        Triple("IMAGES", "Images", Icons.Filled.Image to Color(0xFF3B82F6)),
        Triple("VIDEOS", "Videos", Icons.Filled.VideoFile to Color(0xFF10B981)),
        Triple("AUDIO", "Audio", Icons.Filled.AudioFile to Color(0xFFF59E0B)),
        Triple("DOCUMENTS", "Documents", Icons.Filled.Description to Color(0xFF8B5CF6))
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsList.forEach { (catKey, label, iconAndColor) ->
            val isSelected = selectedCategory == catKey
            val (icon, color) = iconAndColor

            Card(
                modifier = Modifier
                    .width(105.dp)
                    .clickable { onCategorySelect(catKey) }
                    .testTag("filter_chip_$catKey"),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = color,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
    onOpenFile: () -> Unit,
    onToggleSelect: () -> Unit,
    formatSize: (Long) -> String,
    formatDate: (Long) -> String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenFile() }
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
                    )
                    .clickable { onToggleSelect() },
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
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
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
                    .background(MaterialTheme.colorScheme.surfaceVariant)
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
                .background(MaterialTheme.colorScheme.surfaceVariant)
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

    fun formatLastSynced(timestamp: Long): String {
        if (timestamp == 0L) return "Never synced"
        val diff = System.currentTimeMillis() - timestamp
        if (diff < 0) return "Just now"
        val seconds = diff / 1000
        if (seconds < 60) return "Just now"
        val minutes = seconds / 60
        if (minutes < 60) return "${minutes}m ago"
        val hours = minutes / 60
        if (hours < 24) return "${hours}h ago"
        val days = hours / 24
        return "${days}d ago"
    }

    // State, Backdrop scrim, and animations for Side Panel
    var showCommitHistoryPanel by remember { mutableStateOf(false) }
    
    val conflictedFiles by viewModel.conflictedFiles.collectAsStateWithLifecycle()
    var selectedFileToResolve by remember { mutableStateOf<com.example.data.ConflictFile?>(null) }

    val activeWorkspaceTab by viewModel.activeGitWorkspaceSubTab.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        // Tab switcher for workspace
        TabRow(
            selectedTabIndex = activeWorkspaceTab,
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().testTag("git_workspace_subtabs")
        ) {
            Tab(
                selected = activeWorkspaceTab == 0,
                onClick = { viewModel.setGitWorkspaceSubTab(0) },
                icon = { Icon(Icons.Filled.Dashboard, null, modifier = Modifier.size(18.dp)) },
                text = { Text("Dashboard Stats", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                modifier = Modifier.testTag("git_tab_dashboard")
            )
            Tab(
                selected = activeWorkspaceTab == 1,
                onClick = { viewModel.setGitWorkspaceSubTab(1) },
                icon = { Icon(Icons.Filled.Folder, null, modifier = Modifier.size(18.dp)) },
                text = { Text("Code Explorer", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                modifier = Modifier.testTag("git_tab_explorer")
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            if (activeWorkspaceTab == 0) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
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

            // Tracked Repositories Status & Sync History List Card
            item {
                val trackedRepos by viewModel.trackedRepositories.collectAsStateWithLifecycle()
                
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("tracked_repos_status_card"),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.History,
                                contentDescription = "History icon",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Tracked Repositories Status",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (trackedRepos.isEmpty()) {
                            Text(
                                text = "No tracked repositories yet. Enter a path above to start tracking.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                trackedRepos.forEach { repo ->
                                    val isActive = repo.path == repoPath
                                    
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                                else Color.Transparent,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                if (!isActive) {
                                                    viewModel.updateGithubRepoPath(repo.path)
                                                }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isActive) Icons.Filled.Bookmark else Icons.Filled.Folder,
                                                    contentDescription = "Repo status",
                                                    tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = repo.path,
                                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (isActive) {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                        modifier = Modifier.padding(start = 4.dp)
                                                    ) {
                                                        Text(
                                                            text = "ACTIVE",
                                                            fontSize = 8.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            
                                            Spacer(modifier = Modifier.height(4.dp))
                                            
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            when (repo.status) {
                                                                "Synced" -> Color(0xFF22C55E)
                                                                "Synced (Simulated)" -> Color(0xFF3B82F6)
                                                                "Syncing" -> Color(0xFFF59E0B)
                                                                "Failed" -> Color(0xFFEF4444)
                                                                else -> Color.Gray
                                                            }
                                                        )
                                                )
                                                Text(
                                                    text = "${repo.status} • Last synced: ${formatLastSynced(repo.lastSynced)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                                )
                                            }
                                        }

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(
                                                onClick = { viewModel.fetchGitHubData(repo.path) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Refresh,
                                                    contentDescription = "Force sync repo",
                                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            
                                            IconButton(
                                                onClick = { viewModel.removeTrackedRepository(repo.path) },
                                                enabled = !isActive,
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Delete,
                                                    contentDescription = "Remove tracker",
                                                    tint = if (isActive) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f) 
                                                           else MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                                    modifier = Modifier.size(16.dp)
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
            androidx.compose.animation.AnimatedVisibility(
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
                                .background(MaterialTheme.colorScheme.surfaceVariant)
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
        } else {
            GitCodeWorkspace(viewModel = viewModel)
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
fun GitCodeWorkspace(viewModel: SmartViewModel) {
    val repositoryFiles by viewModel.repositoryFiles.collectAsStateWithLifecycle()
    val selectedRepoFile by viewModel.selectedRepoFile.collectAsStateWithLifecycle()
    val repoChatHistory by viewModel.repoChatHistory.collectAsStateWithLifecycle()
    val isRepoChatGenerating by viewModel.isRepoChatGenerating.collectAsStateWithLifecycle()
    val repoPath by viewModel.githubRepoPath.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600

    // Expanded folders set state
    var expandedFolders by remember { mutableStateOf(setOf<String>("src", "src/main", "src/main/java", "src/main/java/com", "src/main/java/com/example")) }
    // Collapsible side panel state
    var isChatPanelVisible by remember { mutableStateOf(true) }

    // Dialog trigger states
    var showRenameDialogForFile by remember { mutableStateOf<GitHubRepoFile?>(null) }
    var showDeleteDialogForFile by remember { mutableStateOf<GitHubRepoFile?>(null) }
    var showInfoDialogForFile by remember { mutableStateOf<GitHubRepoFile?>(null) }
    var inputRenameValue by remember { mutableStateOf("") }

    // Sort repository files so directories come first, then files alphabetically
    val sortedFiles = remember(repositoryFiles) {
        repositoryFiles.sortedWith(
            compareBy<GitHubRepoFile> { it.path.substringBeforeLast("/", "") }
                .thenByDescending { it.isDirectory }
                .thenBy { it.name }
        )
    }

    // Determine visibility based on expansion state of ancestors
    val visibleFiles = remember(sortedFiles, expandedFolders) {
        sortedFiles.filter { file ->
            var currentParent = file.parentPath
            var isVisible = true
            while (currentParent.isNotEmpty()) {
                if (!expandedFolders.contains(currentParent)) {
                    isVisible = false
                    break
                }
                val parentFile = sortedFiles.find { it.path == currentParent }
                currentParent = parentFile?.parentPath ?: ""
            }
            isVisible
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(modifier = Modifier.fillMaxSize()) {
            
            // 1. Google Drive Mimetic Hierarchical Sidebar File Explorer
            if (isWideScreen) {
                Column(
                    modifier = Modifier
                        .width(260.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(0.dp))
                ) {
                    GitFileExplorerHeader(repoPath = repoPath)
                    
                    if (repositoryFiles.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No files synced.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(visibleFiles, key = { it.id }) { file ->
                                GitFileExplorerItemRow(
                                    file = file,
                                    isSelected = selectedRepoFile?.id == file.id,
                                    isExpanded = expandedFolders.contains(file.path),
                                    onNodeClicked = { clicked ->
                                        if (clicked.isDirectory) {
                                            expandedFolders = if (expandedFolders.contains(clicked.path)) {
                                                expandedFolders - clicked.path
                                            } else {
                                                expandedFolders + clicked.path
                                            }
                                        } else {
                                            viewModel.selectRepoFile(clicked)
                                        }
                                    },
                                    onRenameRequested = { target ->
                                        showRenameDialogForFile = target
                                        inputRenameValue = target.name
                                    },
                                    onDeleteRequested = { target ->
                                        showDeleteDialogForFile = target
                                    },
                                    onInfoRequested = { target ->
                                        showInfoDialogForFile = target
                                    },
                                    onAnalysisRequested = { target ->
                                        viewModel.runAiAnalysis(target.id) {
                                            isChatPanelVisible = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 2. Main Workspace (Header, File Editor & Mobile overlays)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Workspace Toolbar
                GitWorkspaceToolbar(
                    selectedFile = selectedRepoFile,
                    isWideScreen = isWideScreen,
                    isChatPanelVisible = isChatPanelVisible,
                    onToggleChat = { isChatPanelVisible = !isChatPanelVisible },
                    onAnalyzeCurrent = {
                        selectedRepoFile?.let { current ->
                            viewModel.runAiAnalysis(current.id) {
                                isChatPanelVisible = true
                            }
                        }
                    },
                    onCopyContent = {
                        selectedRepoFile?.let { current ->
                            val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clipData = android.content.ClipData.newPlainText("repo_code", current.content)
                            clipboardManager.setPrimaryClip(clipData)
                            Toast.makeText(context, "Code copied to clipboard!", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (selectedRepoFile == null) {
                        GitWorkspaceEmptyState(repoPath = repoPath)
                    } else {
                        GitWorkspaceCodeEditor(selectedFile = selectedRepoFile!!)
                    }
                }
            }

            // 3. Right-side Collapsible Conversational Panel
            if (isWideScreen) {
                AnimatedVisibility(
                    visible = isChatPanelVisible,
                    enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .width(340.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(0.dp))
                    ) {
                        GitChatPanel(
                            repoPath = repoPath,
                            selectedFile = selectedRepoFile,
                            repoChatHistory = repoChatHistory,
                            isGenerating = isRepoChatGenerating,
                            onSendMessage = { msg -> viewModel.sendRepoChatMessage(msg) },
                            onClearHistory = { viewModel.clearRepoChatHistory() }
                        )
                    }
                }
            }
        }

        // 4. Mobile Mode Support (Floating Overlays/FABS)
        if (!isWideScreen) {
            var showMobileExplorer by remember { mutableStateOf(false) }
            var showMobileChat by remember { mutableStateOf(false) }

            // Bottom bar drawer toggles
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 56.dp, start = 12.dp, end = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FloatingActionButton(
                    onClick = { showMobileExplorer = true },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Icon(Icons.Filled.Folder, "Explorer Sidebar")
                }

                FloatingActionButton(
                    onClick = { showMobileChat = true },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ) {
                    Box {
                        Icon(Icons.Filled.Chat, "Conversational AI")
                        if (repoChatHistory.size > 1) {
                            Badge(modifier = Modifier.align(Alignment.TopEnd)) { Text("${repoChatHistory.size - 1}") }
                        }
                    }
                }
            }

            // Mobile File Explorer Bottom Sheet/Dialog
            if (showMobileExplorer) {
                Dialog(onDismissRequest = { showMobileExplorer = false }) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.85f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Repository Files", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                IconButton(onClick = { showMobileExplorer = false }) {
                                    Icon(Icons.Filled.Close, "Close")
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(bottom = 12.dp)
                            ) {
                                items(visibleFiles) { file ->
                                    GitFileExplorerItemRow(
                                        file = file,
                                        isSelected = selectedRepoFile?.id == file.id,
                                        isExpanded = expandedFolders.contains(file.path),
                                        onNodeClicked = { clicked ->
                                            if (clicked.isDirectory) {
                                                expandedFolders = if (expandedFolders.contains(clicked.path)) {
                                                    expandedFolders - clicked.path
                                                } else {
                                                    expandedFolders + clicked.path
                                                }
                                            } else {
                                                viewModel.selectRepoFile(clicked)
                                                showMobileExplorer = false
                                            }
                                        },
                                        onRenameRequested = { target ->
                                            showRenameDialogForFile = target
                                            inputRenameValue = target.name
                                        },
                                        onDeleteRequested = { target ->
                                            showDeleteDialogForFile = target
                                        },
                                        onInfoRequested = { target ->
                                            showInfoDialogForFile = target
                                        },
                                        onAnalysisRequested = { target ->
                                            viewModel.runAiAnalysis(target.id) {
                                                showMobileChat = true
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Mobile Chat Dialog
            if (showMobileChat) {
                Dialog(onDismissRequest = { showMobileChat = false }) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.9f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Gemini Repo Chat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                IconButton(onClick = { showMobileChat = false }) {
                                    Icon(Icons.Filled.Close, "Close")
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                GitChatPanel(
                                    repoPath = repoPath,
                                    selectedFile = selectedRepoFile,
                                    repoChatHistory = repoChatHistory,
                                    isGenerating = isRepoChatGenerating,
                                    onSendMessage = { msg -> viewModel.sendRepoChatMessage(msg) },
                                    onClearHistory = { viewModel.clearRepoChatHistory() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Context Menu Dialog Implementations ---

    // 1. Rename Entry Dialog
    if (showRenameDialogForFile != null) {
        val node = showRenameDialogForFile!!
        AlertDialog(
            onDismissRequest = { showRenameDialogForFile = null },
            title = { Text("Rename Virtual Node", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter a new name for the virtual node at path '${node.path}':", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = inputRenameValue,
                        onValueChange = { inputRenameValue = it },
                        modifier = Modifier.fillMaxWidth().testTag("rename_node_input"),
                        singleLine = true,
                        placeholder = { Text("AppModule.kt") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputRenameValue.isNotBlank()) {
                            viewModel.renameRepoFile(node.id, inputRenameValue.trim())
                        }
                        showRenameDialogForFile = null
                    },
                    modifier = Modifier.testTag("rename_node_confirm")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialogForFile = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 2. Delete Confirmation Dialog
    if (showDeleteDialogForFile != null) {
        val node = showDeleteDialogForFile!!
        AlertDialog(
            onDismissRequest = { showDeleteDialogForFile = null },
            title = { Text("Delete Node Entry?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to delete '${node.path}' from the synchronized workspace?\n\nThis will remove it from the code exploration listing and clear its references.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteRepoFile(node.id)
                        showDeleteDialogForFile = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("delete_node_confirm")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialogForFile = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 3. View Info Dialog
    if (showInfoDialogForFile != null) {
        val node = showInfoDialogForFile!!
        AlertDialog(
            onDismissRequest = { showInfoDialogForFile = null },
            title = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Repository Node Info", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column {
                        Text("Node Name", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(node.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Column {
                        Text("Full Sync Path", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(node.path, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                    }
                    Column {
                        Text("Node Classification", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (node.isDirectory) "Directory Folder (Branch)" else "Source File (Blob)", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (!node.isDirectory) {
                        Column {
                            Text("Simulated Binary Size", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${node.size} bytes (${String.format("%.2f", node.size / 1024.0)} KB)", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Column {
                        Text("Lifecycle Catalog State", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF22C55E)))
                            Text("Synced Virtual Node (Cached)", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF22C55E))
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showInfoDialogForFile = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun GitFileExplorerHeader(repoPath: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Filled.Folder, "Drive mimicking logo", tint = Color(0xFF3B82F6), modifier = Modifier.size(20.dp))
            Text("Google Drive View", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, letterSpacing = 0.5.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Synced: $repoPath",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontFamily = FontFamily.Monospace
        )
        HorizontalDivider(modifier = Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GitFileExplorerItemRow(
    file: GitHubRepoFile,
    isSelected: Boolean,
    isExpanded: Boolean,
    onNodeClicked: (GitHubRepoFile) -> Unit,
    onRenameRequested: (GitHubRepoFile) -> Unit,
    onDeleteRequested: (GitHubRepoFile) -> Unit,
    onInfoRequested: (GitHubRepoFile) -> Unit,
    onAnalysisRequested: (GitHubRepoFile) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val depth = remember(file.path) {
        if (file.path.contains("/")) file.path.split("/").size - 1 else 0
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else Color.Transparent
            )
            .pointerInput(file.id) {
                detectTapGestures(
                    onLongPress = { showMenu = true },
                    onTap = { onNodeClicked(file) }
                )
            }
            .padding(start = (depth * 12 + 8).dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Expand/Collapse Chevron for directories
        if (file.isDirectory) {
            Icon(
                imageVector = if (isExpanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(20.dp)
                    .clickable { onNodeClicked(file) }
            )
        } else {
            Spacer(modifier = Modifier.width(20.dp))
        }

        Spacer(modifier = Modifier.width(4.dp))

        // File/Folder main Icon
        val (iconVec, iconColor) = remember(file.isDirectory, file.name) {
            when {
                file.isDirectory -> Icons.Filled.Folder to Color(0xFFFBBF24) // Gold
                file.name.endsWith(".md") -> Icons.Filled.Description to Color(0xFF3B82F6) // Accent Blue
                file.name.endsWith(".kt") || file.name.endsWith(".java") -> Icons.Filled.Code to Color(0xFF34D399) // Mint Teal
                file.name.endsWith(".gradle") || file.name.endsWith(".kts") -> Icons.Filled.Code to Color(0xFF8B5CF6) // Purple
                else -> Icons.Filled.Description to Color(0xFF9CA3AF) // Slate
            }
        }

        Icon(
            imageVector = iconVec,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(18.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Name text
        Text(
            text = file.name,
            fontSize = 13.sp,
            fontWeight = if (file.isDirectory || isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // Metadata menu launcher trigger
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(24.dp).testTag("options_menu_${file.name.replace(".", "_")}")
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "Options menu",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Filled.Edit, null, modifier = Modifier.size(16.dp)) },
                    onClick = {
                        showMenu = false
                        onRenameRequested(file)
                    },
                    modifier = Modifier.testTag("menu_rename")
                )
                
                DropdownMenuItem(
                    text = { Text("Delete") },
                    leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) },
                    onClick = {
                        showMenu = false
                        onDeleteRequested(file)
                    },
                    modifier = Modifier.testTag("menu_delete")
                )

                DropdownMenuItem(
                    text = { Text("View Info") },
                    leadingIcon = { Icon(Icons.Filled.Info, null, modifier = Modifier.size(16.dp)) },
                    onClick = {
                        showMenu = false
                        onInfoRequested(file)
                    },
                    modifier = Modifier.testTag("menu_info")
                )

                if (!file.isDirectory) {
                    DropdownMenuItem(
                        text = { Text("Run AI Analysis") },
                        leadingIcon = { Icon(Icons.Filled.AutoAwesome, null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(16.dp)) },
                        onClick = {
                            showMenu = false
                            onAnalysisRequested(file)
                        },
                        modifier = Modifier.testTag("menu_analysis")
                    )
                }
            }
        }
    }
}

@Composable
fun GitWorkspaceToolbar(
    selectedFile: GitHubRepoFile?,
    isWideScreen: Boolean,
    isChatPanelVisible: Boolean,
    onToggleChat: () -> Unit,
    onAnalyzeCurrent: () -> Unit,
    onCopyContent: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                if (selectedFile != null) {
                    Icon(
                        imageVector = Icons.Filled.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = selectedFile.path,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = "Workspace Root Catalog",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (selectedFile != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onCopyContent,
                        modifier = Modifier.size(32.dp).testTag("action_copy_code")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Copy code to clipboard",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onAnalyzeCurrent,
                        modifier = Modifier.size(32.dp).testTag("action_run_ai_analyst")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = "Analyze current code with Gemini AI",
                            tint = Color(0xFF8B5CF6),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    if (isWideScreen) {
                        IconButton(
                            onClick = onToggleChat,
                            modifier = Modifier.size(32.dp).testTag("action_toggle_chat_panel")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Chat,
                                contentDescription = "Toggle Gemini conversational bar",
                                tint = if (isChatPanelVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GitWorkspaceEmptyState(repoPath: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "No File Selected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Select any source code or markdown document from the Drive file explorer sidebar to review its structures.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun GitWorkspaceCodeEditor(selectedFile: GitHubRepoFile) {
    val lines = remember(selectedFile.content) { selectedFile.content.lines() }

    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            
            // Code Line numbers column
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                    .padding(vertical = 12.dp, horizontal = 10.dp),
                horizontalAlignment = Alignment.End
            ) {
                for (idx in 1..lines.size) {
                    Text(
                        text = idx.toString(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        lineHeight = 16.sp
                    )
                }
            }

            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

            // Scrollable Code Editor Area
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = selectedFile.content,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 16.sp,
                    letterSpacing = 0.sp
                )
            }
        }
    }
}

@Composable
fun GitChatPanel(
    repoPath: String,
    selectedFile: GitHubRepoFile?,
    repoChatHistory: List<ChatMessageEntity>,
    isGenerating: Boolean,
    onSendMessage: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    var rawInputText by remember { mutableStateOf("") }
    val chatScrollState = rememberLazyListState()

    // Keep scrolled to bottom
    LaunchedEffect(repoChatHistory.size, isGenerating) {
        if (repoChatHistory.isNotEmpty()) {
            chatScrollState.animateScrollToItem(repoChatHistory.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        
        // Chat panel Header
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(18.dp))
                    Text(
                        "Gemini Architect",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                IconButton(
                    onClick = onClearHistory,
                    modifier = Modifier.size(32.dp).testTag("btn_clear_chat_history")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Clear conversational path context",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // Prompt helper card (if viewing a file)
        if (selectedFile != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Info,
                        null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        "Context active: ${selectedFile.name}. Gemini has ingestion of this code block active.",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Message lists
        androidx.compose.foundation.lazy.LazyColumn(
            state = chatScrollState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(repoChatHistory) { message ->
                val isGemini = message.sender == "gemini"
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isGemini) Arrangement.Start else Arrangement.End
                ) {
                    Card(
                        modifier = Modifier.widthIn(max = 270.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isGemini) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                            else MaterialTheme.colorScheme.primaryContainer
                        ),
                        shape = RoundedCornerShape(
                            topStart = 12.dp,
                            topEnd = 12.dp,
                            bottomStart = if (isGemini) 2.dp else 12.dp,
                            bottomEnd = if (isGemini) 12.dp else 2.dp
                        )
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isGemini) "Gemini Analyst" else "User",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isGemini) Color(0xFF8B5CF6) else MaterialTheme.colorScheme.primary
                                )
                                val timeFormat = remember(message.timestamp) {
                                    java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(message.timestamp))
                                }
                                Text(
                                    text = timeFormat,
                                    fontSize = 8.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                            
                            // Markdown style rendering helper
                            Text(
                                text = message.messageText,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            if (isGenerating) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                                Text("Gemini is reading code structures...", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // Chat Input Field Panel
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = rawInputText,
                    onValueChange = { rawInputText = it },
                    placeholder = { Text("Ask about these files...", fontSize = 12.sp) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_repo_panel_input"),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    singleLine = false,
                    maxLines = 3,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                    ),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (rawInputText.isNotBlank()) {
                                    onSendMessage(rawInputText.trim())
                                    rawInputText = ""
                                }
                            },
                            enabled = rawInputText.isNotBlank() && !isGenerating,
                            modifier = Modifier.testTag("chat_repo_panel_send_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Send,
                                contentDescription = "Send prompt",
                                tint = if (rawInputText.isNotBlank() && !isGenerating) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                    }
                )
            }
        }
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

// ============================================================================
// NEW STORAGE CLEANER, HIGH-FIDELITY VIEWER, AND CAMERA SCANNER OVERLAYS
// ============================================================================

@Composable
fun StorageCleanerPanel(
    viewModel: SmartViewModel
) {
    val isCleanerActive by viewModel.storageCleanerActive.collectAsStateWithLifecycle()
    val isScanning by viewModel.isStorageScanning.collectAsStateWithLifecycle()
    val scanProgress by viewModel.storageScanProgress.collectAsStateWithLifecycle()
    val duplicateFiles by viewModel.scannedDuplicates.collectAsStateWithLifecycle()
    val largeTempFiles by viewModel.scannedLargeTempFiles.collectAsStateWithLifecycle()
    val selectedIds by viewModel.storageSelectedFileIds.collectAsStateWithLifecycle()

    var activeSubTab by remember { mutableStateOf(0) } // 0: Duplicates, 1: Large Temp Files

    androidx.compose.animation.AnimatedVisibility(
        visible = isCleanerActive,
        enter = scaleIn(initialScale = 0.95f, animationSpec = tween(400)) + fadeIn(animationSpec = tween(400)),
        exit = scaleOut(targetScale = 0.95f, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(Unit) {}
        ) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                // Header Toolbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.setStorageCleanerActive(false) }) {
                        Icon(Icons.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Storage Cleaner Utility",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                if (isScanning) {
                    // Scanner running state
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(140.dp)) {
                            CircularProgressIndicator(
                                progress = { scanProgress },
                                modifier = Modifier.fillMaxSize(),
                                strokeWidth = 10.dp,
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "${(scanProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text("SCANNING", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                        Text(
                            text = "Analyzing local file tree headers...",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Locating duplicate clones & redundant temp logs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    // Scan finished state list
                    Column(modifier = Modifier.weight(1f)) {
                        // Quick overview card
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Scan Summary | स्कैन सारांश",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Duplicate Clones", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                        Text("${duplicateFiles.size} items", fontWeight = FontWeight.Bold)
                                    }
                                    Column {
                                        Text("Temp & Large Files", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                        Text("${largeTempFiles.size} items", fontWeight = FontWeight.Bold)
                                    }
                                    Column {
                                        val totalSz = (duplicateFiles + largeTempFiles).distinctBy { it.id }.sumOf { it.size }
                                        Text("Total Recoverable", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                        Text(viewModel.formatSize(totalSz), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }

                        // Category tabs
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Button(
                                onClick = { activeSubTab = 0 },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (activeSubTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Duplicates (${duplicateFiles.size})", color = if (activeSubTab == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Button(
                                onClick = { activeSubTab = 1 },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (activeSubTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Temp Files (${largeTempFiles.size})", color = if (activeSubTab == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        // Scrolling List of selected tab
                        val displayFiles = if (activeSubTab == 0) duplicateFiles else largeTempFiles

                        if (displayFiles.isEmpty()) {
                            Box(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Filled.CheckCircleOutline, "Clean", tint = Color(0xFF22C55E), modifier = Modifier.size(56.dp))
                                    Text("Excellent! No items found in this section.", fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Select All header helper row
                                item {
                                    val isAllSelected = displayFiles.all { selectedIds.contains(it.id) }
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Select the files to delete:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        TextButton(
                                            onClick = {
                                                if (isAllSelected) viewModel.selectNoStorageFiles()
                                                else viewModel.selectAllStorageFiles(displayFiles.map { it.id })
                                            }
                                        ) {
                                            Text(if (isAllSelected) "Deselect All" else "Select All", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                items(displayFiles, key = { it.id }) { file ->
                                    val isSelected = selectedIds.contains(file.id)
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.toggleStorageSelectedFile(file.id) }
                                            .testTag("storage_clean_item_${file.id}"),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                                             else MaterialTheme.colorScheme.surface
                                        ),
                                        border = BorderStroke(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = { viewModel.toggleStorageSelectedFile(file.id) },
                                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(file.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(file.path, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Text(viewModel.formatSize(file.size), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                                    Text("•", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                                                    Text(
                                                        text = if (file.isDuplicate) "Clone Copy" else if (file.isJunk) "Temp Cache" else "Large File",
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.secondary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Clean Floating bar
                    if (selectedIds.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            elevation = CardDefaults.cardElevation(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val selectedFilesList = (duplicateFiles + largeTempFiles).filter { selectedIds.contains(it.id) }
                                val selectedSz = selectedFilesList.sumOf { it.size }
                                Column {
                                    Text("${selectedIds.size} files selected", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = "Will recover ${viewModel.formatSize(selectedSz)}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                
                                val context = LocalContext.current
                                Button(
                                    onClick = {
                                        viewModel.deleteSelectedStorageFiles()
                                        Toast.makeText(context, "Cleaned selected temporary and duplicate items!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Filled.Delete, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("CLEAN NOW")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedFileViewer(
    file: FileEntity?,
    onDismiss: () -> Unit,
    onDelete: (FileEntity) -> Unit,
    formatSize: (Long) -> String,
    formatDate: (Long) -> String
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = file != null,
        enter = scaleIn(initialScale = 0.8f, animationSpec = tween(380)) + fadeIn(animationSpec = tween(380)),
        exit = scaleOut(targetScale = 0.82f, animationSpec = tween(320)) + fadeOut(animationSpec = tween(320))
    ) {
        if (file != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F172A)) // Deep slate dark backdrop
                    .pointerInput(Unit) {} // Consume touch events
            ) {
                Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                    // Header Toolbar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.Close, "Close Viewer", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 240.dp)
                                )
                                Text(
                                    text = formatSize(file.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.LightGray.copy(alpha = 0.7f)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val context = LocalContext.current
                            IconButton(onClick = {
                                onDelete(file)
                                Toast.makeText(context, "Deleted ${file.name}!", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }) {
                                Icon(Icons.Filled.Delete, "Delete", tint = Color(0xFFEF4444))
                            }
                        }
                    }

                    // Content Canvas
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            file.mimeType.startsWith("image/") -> {
                                var rotationAngle by remember { mutableStateOf(0f) }
                                var imageScale by remember { mutableStateOf(1f) }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    // Simulated high-fidelity Image View
                                    Box(
                                        modifier = Modifier
                                            .size(280.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color(0xFF1E293B))
                                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                                            .graphicsLayer(
                                                rotationZ = rotationAngle,
                                                scaleX = imageScale,
                                                scaleY = imageScale
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        // Draw a gorgeous photo placeholder or geometric design using canvas
                                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                            // Slate background design
                                            drawRect(Color(0xFF1E293B))
                                            // Dynamic geometric shapes representing real visual photographic layers
                                            drawCircle(
                                                color = Color(0xFF38BDF8),
                                                radius = size.minDimension / 4f,
                                                center = center
                                            )
                                            drawRect(
                                                color = Color(0xFF3B82F6).copy(alpha = 0.5f),
                                                size = size / 3f,
                                                topLeft = androidx.compose.ui.geometry.Offset(size.width/4f, size.height/3f)
                                            )
                                        }
                                        Icon(
                                            Icons.Filled.Image,
                                            contentDescription = null,
                                            modifier = Modifier.size(56.dp),
                                            tint = Color.White.copy(alpha = 0.8f)
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(24.dp))
                                    // Interactive Image controls
                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        Button(
                                            onClick = { rotationAngle += 90f },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                                        ) {
                                            Icon(Icons.Filled.RotateRight, null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Rotate 90°", color = Color.White)
                                        }
                                        Button(
                                            onClick = { imageScale = if (imageScale == 1f) 1.5f else 1f },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                                        ) {
                                            Icon(Icons.Filled.ZoomIn, null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(if (imageScale == 1f) "Zoom In" else "Zoom Out", color = Color.White)
                                        }
                                    }
                                }
                            }
                            file.name.endsWith(".pdf", ignoreCase = true) || file.mimeType.contains("pdf") -> {
                                var selectedPage by remember { mutableStateOf(1) }
                                val pageCount = 3
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // Simulated multi-page PDF Page Frame
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(340.dp)
                                            .padding(horizontal = 16.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        shape = RoundedCornerShape(8.dp),
                                        elevation = CardDefaults.cardElevation(8.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(24.dp)
                                        ) {
                                            // Document header
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Filled.Info, null, tint = Color(0xFFDC2626), modifier = Modifier.size(24.dp))
                                                Text(
                                                    "Page $selectedPage of $pageCount",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color.DarkGray
                                                )
                                            }
                                            
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.LightGray)
                                            
                                            // Simulated document lines based on current selectedPage
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text(
                                                    text = if (selectedPage == 1) "1. SECURE DIGITAL STORAGE SYSTEMS CONTRACT"
                                                           else if (selectedPage == 2) "2. HARDWARE SCANNERS AND DIRECTORY CACHES"
                                                           else "3. LEGAL NOTICES, COGNITIVE METRICS & COMPLIANCE",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = Color.Black
                                                )
                                                
                                                repeat(5) { index ->
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth(if (index == 4) 0.6f else 1f)
                                                            .height(8.dp)
                                                            .background(Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                                    )
                                                }
                                                
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Text(
                                                    text = "Processed and indexed under: com.aistudio.smartfilemanager. Registered locally on ${formatDate(file.lastModified)}.",
                                                    fontSize = 11.sp,
                                                    color = Color.Gray,
                                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                                )
                                            }
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(24.dp))
                                    // Page Navigation Selection
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        IconButton(
                                            enabled = selectedPage > 1,
                                            onClick = { selectedPage-- },
                                            modifier = Modifier.background(Color(0xFF334155), CircleShape)
                                        ) {
                                            Icon(Icons.Filled.ChevronLeft, null, tint = Color.White)
                                        }
                                        Text("Page $selectedPage / $pageCount", color = Color.White, fontWeight = FontWeight.Bold)
                                        IconButton(
                                            enabled = selectedPage < pageCount,
                                            onClick = { selectedPage++ },
                                            modifier = Modifier.background(Color(0xFF334155), CircleShape)
                                        ) {
                                            Icon(Icons.Filled.ChevronRight, null, tint = Color.White)
                                        }
                                    }
                                }
                            }
                            file.mimeType.startsWith("audio/") -> {
                                var isPlaying by remember { mutableStateOf(false) }
                                var playbackProgress by remember { mutableStateOf(0.4f) }
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // Beautiful rotating record disc frame
                                    val infiniteTransition = rememberInfiniteTransition()
                                    val rotationAnim by infiniteTransition.animateFloat(
                                        initialValue = 0f,
                                        targetValue = 360f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(4000, easing = LinearEasing),
                                            repeatMode = RepeatMode.Restart
                                        )
                                    )
                                    
                                    Box(
                                        modifier = Modifier
                                            .size(200.dp)
                                            .graphicsLayer(rotationZ = if (isPlaying) rotationAnim else 0f)
                                            .background(Color.Black, CircleShape)
                                            .border(8.dp, Color(0xFF1E293B), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        // Vinyl central emblem
                                        Box(
                                            modifier = Modifier
                                                .size(60.dp)
                                                .background(Color(0xFF3B82F6), CircleShape)
                                                .border(4.dp, Color.White, CircleShape)
                                        )
                                        Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(24.dp))
                                    }
                                    
                                    Spacer(modifier = Modifier.height(30.dp))
                                    
                                    // Glowing equalizer soundwave
                                    Row(
                                        modifier = Modifier.height(40.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        repeat(12) { i ->
                                            val waveHeight = if (isPlaying) {
                                                val hzTransition = rememberInfiniteTransition()
                                                val heightAnim by hzTransition.animateFloat(
                                                    initialValue = 8.dp.value,
                                                    targetValue = (24 + (i * 3) % 20).dp.value,
                                                    animationSpec = infiniteRepeatable(
                                                        animation = tween(400 + (i * 60), easing = FastOutSlowInEasing),
                                                        repeatMode = RepeatMode.Reverse
                                                    )
                                                )
                                                heightAnim.dp
                                            } else {
                                                8.dp
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .width(6.dp)
                                                    .height(waveHeight)
                                                    .background(Color(0xFF38BDF8), RoundedCornerShape(3.dp))
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    // Audio play controls
                                    Slider(
                                        value = playbackProgress,
                                        onValueChange = { playbackProgress = it },
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                                        colors = SliderDefaults.colors(
                                            activeTrackColor = Color(0xFF38BDF8),
                                            thumbColor = Color(0xFF38BDF8)
                                        )
                                    )
                                    
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(32.dp)
                                    ) {
                                        IconButton(onClick = {}) {
                                            Icon(Icons.Filled.ArrowBack, null, tint = Color.LightGray)
                                        }
                                        IconButton(
                                            onClick = { isPlaying = !isPlaying },
                                            modifier = Modifier
                                                .size(56.dp)
                                                .background(Color(0xFF38BDF8), CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                                contentDescription = if (isPlaying) "Pause" else "Play",
                                                tint = Color.Black,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                        IconButton(onClick = {}) {
                                            Icon(Icons.Filled.ArrowForward, null, tint = Color.LightGray)
                                        }
                                    }
                                }
                            }
                            file.mimeType.startsWith("video/") -> {
                                var isPlaying by remember { mutableStateOf(false) }
                                var videoFrameTick by remember { mutableStateOf(0.12f) }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    // High-fidelity video viewfinder border mockup
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(240.dp)
                                            .background(Color.Black)
                                            .border(1.dp, Color(0xFF334155)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        // Dynamic changing color gradient canvas simulating rolling frame sequence
                                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                            drawRect(Color.DarkGray)
                                            drawLine(
                                                color = Color.Red,
                                                start = Offset(videoFrameTick * size.width, 0f),
                                                end = Offset(videoFrameTick * size.width, size.height),
                                                strokeWidth = 3f
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            IconButton(
                                                onClick = { isPlaying = !isPlaying },
                                                modifier = Modifier
                                                    .size(64.dp)
                                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                                    .border(2.dp, Color.White, CircleShape)
                                            ) {
                                                Icon(
                                                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                                    contentDescription = "Control",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(36.dp)
                                                )
                                            }
                                        }
                                        // Widescreen safe grid markers
                                        Text(
                                            "REC [00:14:02]",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Red,
                                            modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                                        )
                                        Text(
                                            "1080P 60FPS",
                                            fontSize = 9.sp,
                                            color = Color.White,
                                            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Slider(
                                        value = videoFrameTick,
                                        onValueChange = { videoFrameTick = it },
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                        colors = SliderDefaults.colors(
                                            activeTrackColor = Color.Red,
                                            thumbColor = Color.Red
                                        )
                                    )
                                }
                            }
                            else -> {
                                // Fallback info sheet
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp)
                                        .background(Color(0xFF1E293B), RoundedCornerShape(16.dp))
                                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Filled.Description, null, tint = Color.LightGray, modifier = Modifier.size(64.dp))
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Pre-indexed System Binary File", style = MaterialTheme.typography.titleMedium, color = Color.White)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("File Path: ${file.path}", fontSize = 11.sp, color = Color.White)
                                            Text("Logical Size: ${formatSize(file.size)}", fontSize = 11.sp, color = Color.White)
                                            Text("Index Modified: ${formatDate(file.lastModified)}", fontSize = 11.sp, color = Color.White)
                                            Text("MIME Type: ${file.mimeType}", fontSize = 11.sp, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Properties bottom card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Storage Partition", fontSize = 11.sp, color = Color.LightGray.copy(alpha = 0.6f))
                                Text("Local Emulated Device", fontWeight = FontWeight.SemiBold, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF334155)
                            ) {
                                Text(
                                    text = file.mimeType.substringAfter("/").uppercase(),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 10.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DocumentScannerInterface(
    viewModel: SmartViewModel,
    onSavedCallback: () -> Unit
) {
    val isScannerActive by viewModel.isDocumentScannerActive.collectAsStateWithLifecycle()
    val isCapturing by viewModel.isCameraCapturing.collectAsStateWithLifecycle()
    val hasCapturedDoc by viewModel.scannedDocumentSaved.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.scannedDocumentFilter.collectAsStateWithLifecycle()
    val customFileName by viewModel.scannedFileName.collectAsStateWithLifecycle()

    androidx.compose.animation.AnimatedVisibility(
        visible = isScannerActive,
        enter = scaleIn(initialScale = 0.9f, animationSpec = tween(400)) + fadeIn(animationSpec = tween(400)),
        exit = scaleOut(targetScale = 0.9f, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {}
        ) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                // Toolbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.setDocumentScannerActive(false) }) {
                            Icon(Icons.Filled.Close, "Cancel", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Doc Scanner | दस्तावेज स्कैनर", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }

                    if (hasCapturedDoc) {
                        TextButton(onClick = { viewModel.saveScannedDocument(onSavedCallback) }) {
                            Text("SAVE PDF", color = Color(0xFF22C55E), fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Main Workspace
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (!hasCapturedDoc) {
                        // Viewfinder Mode
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.85f)
                                .clip(RoundedCornerShape(16.dp))
                                .border(2.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                                .background(Color(0xFF0F172A))
                        ) {
                            // Guide corners / grid lines
                            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                val stroke = 4f
                                val margin = 40f
                                val len = 60f
                                // Draw four corner crop bracket guides
                                // Top-Left
                                drawLine(Color.Green, Offset(margin, margin), Offset(margin + len, margin), strokeWidth = stroke)
                                drawLine(Color.Green, Offset(margin, margin), Offset(margin, margin + len), strokeWidth = stroke)
                                // Top-Right
                                drawLine(Color.Green, Offset(size.width - margin, margin), Offset(size.width - margin - len, margin), strokeWidth = stroke)
                                drawLine(Color.Green, Offset(size.width - margin, margin), Offset(size.width - margin, margin + len), strokeWidth = stroke)
                                // Bottom-Left
                                drawLine(Color.Green, Offset(margin, size.height - margin), Offset(margin + len, size.height - margin), strokeWidth = stroke)
                                drawLine(Color.Green, Offset(margin, size.height - margin), Offset(margin, size.height - margin - len), strokeWidth = stroke)
                                // Bottom-Right
                                drawLine(Color.Green, Offset(size.width - margin, size.height - margin), Offset(size.width - margin - len, size.height - margin), strokeWidth = stroke)
                                drawLine(Color.Green, Offset(size.width - margin, size.height - margin), Offset(size.width - margin, size.height - margin - len), strokeWidth = stroke)
                            }

                            Column(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color.Black.copy(alpha = 0.6f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(Icons.Filled.AutoAwesome, null, tint = Color.Yellow, modifier = Modifier.size(12.dp))
                                            Text("AUTO-CROP ON", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Text(
                                    text = "Position your document inside the guides",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Spacer(modifier = Modifier.height(1.dp))
                            }

                            if (isCapturing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.align(Alignment.Center).size(56.dp),
                                    color = Color.Green,
                                    strokeWidth = 6.dp
                                )
                            }
                        }
                    } else {
                        // Edit & Document Adjustments Mode
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Scanned Page Preview",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )

                            // Document Sheet Preview under selected filter
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .height(300.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                colors = CardDefaults.cardColors(
                                    containerColor = when (selectedFilter) {
                                        "bw" -> Color.White
                                        "grayscale" -> Color.LightGray
                                        "contrast" -> Color(0xFFE2E8F0)
                                        else -> Color(0xFFF1F5F9) // Original off-white document paper
                                    }
                                ),
                                border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
                            ) {
                                Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                                    // Simulated high contrast text document
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF22C55E), modifier = Modifier.size(16.dp))
                                            Text(
                                                text = "PDF DIGITIZATION SUCCESS",
                                                color = if (selectedFilter == "bw") Color.Black else Color.DarkGray,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp
                                            )
                                        }
                                        HorizontalDivider(color = Color.LightGray)
                                        repeat(6) { index ->
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(if (index == 5) 0.5f else 1f)
                                                    .height(6.dp)
                                                    .background(
                                                        if (selectedFilter == "bw") Color.Black 
                                                        else if (selectedFilter == "contrast") Color(0xFF334155)
                                                        else Color.Gray.copy(alpha = 0.6f), RoundedCornerShape(3.dp)
                                                    )
                                            )
                                        }
                                    }

                                    // Guide dots on crop corners (interactive handles)
                                    Box(modifier = Modifier.size(12.dp).background(Color.Green, CircleShape).align(Alignment.TopStart))
                                    Box(modifier = Modifier.size(12.dp).background(Color.Green, CircleShape).align(Alignment.TopEnd))
                                    Box(modifier = Modifier.size(12.dp).background(Color.Green, CircleShape).align(Alignment.BottomStart))
                                    Box(modifier = Modifier.size(12.dp).background(Color.Green, CircleShape).align(Alignment.BottomEnd))
                                }
                            }

                            // Dynamic interactive scan color filters selection row
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                val filtersList = listOf(
                                    "original" to "Original",
                                    "bw" to "B&W Scan",
                                    "contrast" to "High-Contrast",
                                    "grayscale" to "Grayscale"
                                )
                                filtersList.forEach { item ->
                                    val isSelected = selectedFilter == item.first
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = if (isSelected) Color(0xFF22C55E) else Color(0xFF1E293B),
                                        modifier = Modifier.clickable { viewModel.setScannedFilter(item.first) }
                                    ) {
                                        Text(
                                            text = item.second,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            color = if (isSelected) Color.Black else Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Action buttons
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A))
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (!hasCapturedDoc) {
                        // Capture actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {}) { Icon(Icons.Filled.FlashOff, null, tint = Color.LightGray) }
                            
                            // Glowing circular shutter trigger button
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .border(4.dp, Color.White, CircleShape)
                                    .padding(6.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .clickable { viewModel.captureDocument() }
                            )
                            
                            IconButton(onClick = {}) { Icon(Icons.Filled.GridOn, null, tint = Color.LightGray) }
                        }
                    } else {
                        // Save Options & Edit title block
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("PDF Filename:", color = Color.White, fontSize = 11.sp)
                            OutlinedTextField(
                                value = customFileName,
                                onValueChange = { viewModel.setScannedFileName(it) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF22C55E),
                                    unfocusedBorderColor = Color.DarkGray,
                                    cursorColor = Color(0xFF22C55E)
                                ),
                                singleLine = true,
                                trailingIcon = { Text(".pdf", color = Color.LightGray, modifier = Modifier.padding(end = 12.dp)) }
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.setDocumentScannerActive(true) }, // Reset scanner state
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Retake Photo", color = Color.White)
                                }
                                Button(
                                    onClick = { viewModel.saveScannedDocument(onSavedCallback) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Compile & Save PDF", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
