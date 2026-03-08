package com.example.empty

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.os.StrictMode
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

val IosLightBackground = Color(0xFFF2F2F7)
val IosSurface = Color(0xFFFFFFFF)
val IosAccentBlue = Color(0xFF007AFF)
val IosTextDark = Color(0xFF1C1C1E)
val IosTextGray = Color(0xFF8E8E93)
val IslandBackground = Color(0xFFFFFFFF).copy(alpha = 0.98f)
val ColorAudio = Color(0xFFE8F5E9)

enum class TrayState { None, Storage, Extension }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val builder = StrictMode.VmPolicy.Builder()
        StrictMode.setVmPolicy(builder.build())

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    background = IosLightBackground,
                    surface = IosSurface,
                    primary = IosAccentBlue,
                    onBackground = IosTextDark,
                    onSurface = IosTextDark,
                    surfaceVariant = Color(0xFFE5E5EA)
                )
            ) {
                val view = LocalView.current
                if (!view.isInEditMode) {
                    SideEffect {
                        val window = (view.context as Activity).window
                        window.statusBarColor = IosLightBackground.toArgb()
                        window.navigationBarColor = IosLightBackground.toArgb()
                        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize().systemBarsPadding(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppScreen()
                }
            }
        }
    }
}

@Composable
fun AppScreen() {
    val context = LocalContext.current
    var hasStoragePermission by remember { mutableStateOf(checkStoragePermission(context)) }

    val manageStorageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        hasStoragePermission = checkStoragePermission(context)
    }
    val classicPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasStoragePermission = checkStoragePermission(context)
    }

    if (hasStoragePermission) {
        MainAppContent(context)
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Dosyalara Erişim İzni", style = MaterialTheme.typography.titleLarge, color = IosTextDark)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = IosAccentBlue),
                shape = CircleShape,
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.data = Uri.parse("package:${context.packageName}")
                            manageStorageLauncher.launch(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            manageStorageLauncher.launch(intent)
                        }
                    } else {
                        classicPermissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                    }
                }
            ) { Text("İzin Ver", color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
        }
    }
}

@Composable
fun MainAppContent(context: Context) {
    val storagePaths = remember { getStorageDirectories(context) }
    var selectedStoragePath by remember { mutableStateOf(storagePaths.firstOrNull()) }
    var selectedCategory by remember { mutableStateOf("Photos") }
    var selectedExtension by remember { mutableStateOf<String?>("All") }
    var selectedFiles by remember { mutableStateOf(setOf<File>()) }

    var isScanning by remember { mutableStateOf(false) }
    var scannedFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var availableExtensions by remember { mutableStateOf<List<String>>(emptyList()) }

    var expandedTrayState by remember { mutableStateOf(TrayState.None) }

    BackHandler(enabled = expandedTrayState != TrayState.None) {
        expandedTrayState = TrayState.None
    }

    LaunchedEffect(selectedStoragePath, selectedCategory) {
        if (selectedStoragePath != null) {
            isScanning = true
            // AKICILIK ÇÖZÜMÜ: Başka sekmeye geçildiğinde eski listeyi anında temizle!
            scannedFiles = emptyList()
            availableExtensions = emptyList()

            withContext(Dispatchers.IO) {
                val root = File(selectedStoragePath!!)
                val allowedExtensions = when(selectedCategory) {
                    "Photos" -> listOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
                    "Videos" -> listOf("mp4", "mkv", "avi", "mov")
                    "Audio"  -> listOf("mp3", "wav", "m4a", "ogg", "flac")
                    "Files"  -> listOf("pdf", "doc", "docx", "xls", "zip", "txt", "rar")
                    else -> emptyList()
                }
                val found = mutableListOf<File>()
                try {
                    root.walkTopDown().onEnter { !it.name.startsWith(".") && it.name != "Android" }
                        .forEach { if (it.isFile && it.extension.lowercase() in allowedExtensions) found.add(it) }
                } catch (e: Exception) {}

                scannedFiles = found
                availableExtensions = found.map { it.extension.lowercase() }.distinct().sorted()
            }
            isScanning = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        if (selectedStoragePath != null) {
            if (isScanning && scannedFiles.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = IosAccentBlue)
                }
            } else {
                val filesToShow = if (selectedExtension == "All") {
                    scannedFiles.sortedByDescending { it.lastModified() }
                } else {
                    scannedFiles.filter { it.extension.lowercase() == selectedExtension }.sortedByDescending { it.lastModified() }
                }

                ContentGridScreen(
                    context = context,
                    category = selectedCategory,
                    files = filesToShow,
                    selectedFiles = selectedFiles,
                    onSelectedFilesChange = { selectedFiles = it },
                    bottomPadding = 140.dp
                )
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = selectedFiles.isEmpty(),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp, start = 16.dp, end = 16.dp)
        ) {
            Card(
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = IslandBackground),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {

                    AnimatedVisibility(
                        visible = expandedTrayState == TrayState.Storage,
                        enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
                        exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                            Text("Depolama Seç", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = IosTextDark, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
                            storagePaths.forEach { path ->
                                val isInternal = path.contains("emulated")
                                val label = if (isInternal) "Dahili Hafıza" else "SD Kart"
                                val icon = if (isInternal) Icons.Default.PhoneIphone else Icons.Default.SdStorage
                                val isSelected = selectedStoragePath == path
                                val storageInfo = remember(path) { getStorageInfo(path) }

                                Row(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                        .clickable {
                                            selectedStoragePath = path
                                            expandedTrayState = TrayState.None
                                            selectedExtension = "All"
                                        }
                                        .background(if (isSelected) IosLightBackground else Color.Transparent)
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(icon, contentDescription = null, tint = if(isSelected) IosAccentBlue else IosTextDark)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(label, style = MaterialTheme.typography.bodyLarge, color = if(isSelected) IosAccentBlue else IosTextDark, fontWeight = if(isSelected) FontWeight.Bold else FontWeight.Normal)
                                        Text(storageInfo, style = MaterialTheme.typography.bodySmall, color = IosTextGray)
                                    }
                                    Spacer(modifier = Modifier.weight(1f))
                                    if(isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = IosAccentBlue)
                                }
                            }
                            HorizontalDivider(color = Color(0xFFE5E5EA), modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                        }
                    }

                    AnimatedVisibility(
                        visible = expandedTrayState == TrayState.Extension,
                        enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
                        exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut()
                    ) {
                        val groupedByExt = remember(scannedFiles) { scannedFiles.groupBy { it.extension.lowercase() } }
                        val formatTitle = when(selectedCategory) {
                            "Photos" -> "Fotoğraf Formatı"
                            "Videos" -> "Video Formatı"
                            "Audio" -> "Ses Formatı"
                            else -> "Belge Formatı"
                        }

                        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                            Text(formatTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = IosTextDark, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))

                            // AKICILIK ÇÖZÜMÜ: Tarama sürerken spinner göster, eski listeyi ekrana basma!
                            if (isScanning) {
                                Box(modifier = Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = IosAccentBlue, modifier = Modifier.size(24.dp))
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.heightIn(max = 280.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    item {
                                        val isSelected = selectedExtension == "All"
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                                .clickable { selectedExtension = "All"; expandedTrayState = TrayState.None }
                                                .background(if (isSelected) IosLightBackground else Color.Transparent)
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.AllInclusive, contentDescription = null, tint = if(isSelected) IosAccentBlue else IosTextDark)
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text("Tümünü Göster", style = MaterialTheme.typography.bodyLarge, color = if(isSelected) IosAccentBlue else IosTextDark, fontWeight = if(isSelected) FontWeight.Bold else FontWeight.Normal)
                                                Text("${scannedFiles.size} Dosya", style = MaterialTheme.typography.bodySmall, color = IosTextGray)
                                            }
                                            Spacer(modifier = Modifier.weight(1f))
                                            if(isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = IosAccentBlue)
                                        }
                                    }

                                    items(availableExtensions) { ext ->
                                        val isSelected = selectedExtension == ext
                                        val count = groupedByExt[ext]?.size ?: 0
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                                .clickable { selectedExtension = ext; expandedTrayState = TrayState.None }
                                                .background(if (isSelected) IosLightBackground else Color.Transparent)
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Folder, contentDescription = null, tint = if(isSelected) IosAccentBlue else IosTextDark)
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(ext.uppercase(), style = MaterialTheme.typography.bodyLarge, color = if(isSelected) IosAccentBlue else IosTextDark, fontWeight = if(isSelected) FontWeight.Bold else FontWeight.Normal)
                                                Text("$count Dosya", style = MaterialTheme.typography.bodySmall, color = IosTextGray)
                                            }
                                            Spacer(modifier = Modifier.weight(1f))
                                            if(isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = IosAccentBlue)
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(color = Color(0xFFE5E5EA), modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val currentStorageIcon = if (selectedStoragePath?.contains("emulated") == true) Icons.Default.PhoneIphone else Icons.Default.SdStorage
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { expandedTrayState = if (expandedTrayState == TrayState.Storage) TrayState.None else TrayState.Storage }
                                .background(if (expandedTrayState == TrayState.Storage) IosLightBackground else Color.Transparent)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(currentStorageIcon, contentDescription = "Depolama", tint = IosTextDark)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(if (expandedTrayState == TrayState.Storage) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp, contentDescription = null, tint = IosTextGray, modifier = Modifier.size(16.dp))
                        }

                        Spacer(modifier = Modifier.height(24.dp).width(1.dp).background(Color(0xFFE5E5EA)))

                        val onCategorySingleClick: (String) -> Unit = { newCat ->
                            selectedCategory = newCat
                            selectedExtension = "All"
                            expandedTrayState = TrayState.None
                        }

                        val onCategoryLongClick: (String) -> Unit = { newCat ->
                            if (selectedCategory != newCat) {
                                selectedCategory = newCat
                                selectedExtension = "All"
                            }
                            expandedTrayState = if (expandedTrayState == TrayState.Extension) TrayState.None else TrayState.Extension
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            DockTab(
                                icon = Icons.Default.PhotoLibrary, isSelected = selectedCategory == "Photos",
                                onSingleClick = { onCategorySingleClick("Photos") }, onLongClick = { onCategoryLongClick("Photos") }
                            )
                            DockTab(
                                icon = Icons.Default.VideoLibrary, isSelected = selectedCategory == "Videos",
                                onSingleClick = { onCategorySingleClick("Videos") }, onLongClick = { onCategoryLongClick("Videos") }
                            )
                            DockTab(
                                icon = Icons.Default.Headset, isSelected = selectedCategory == "Audio",
                                onSingleClick = { onCategorySingleClick("Audio") }, onLongClick = { onCategoryLongClick("Audio") }
                            )
                            DockTab(
                                icon = Icons.Default.Folder, isSelected = selectedCategory == "Files",
                                onSingleClick = { onCategorySingleClick("Files") }, onLongClick = { onCategoryLongClick("Files") }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DockTab(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onSingleClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val bgColor by animateColorAsState(targetValue = if (isSelected) IosAccentBlue.copy(alpha = 0.15f) else Color.Transparent, label = "")
    val iconColor by animateColorAsState(targetValue = if (isSelected) IosAccentBlue else IosTextGray, label = "")

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(bgColor)
            .combinedClickable(
                onClick = onSingleClick,
                onDoubleClick = onLongClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
    }
}

fun getStorageInfo(path: String): String {
    return try {
        val stat = StatFs(path)
        val blockSize = stat.blockSizeLong
        val availableBlocks = stat.availableBlocksLong
        val availableSpaceGB = (availableBlocks * blockSize) / (1024 * 1024 * 1024)
        "Boş: $availableSpaceGB GB"
    } catch (e: Exception) {
        "Hafıza okunamadı"
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ContentGridScreen(
    context: Context,
    category: String,
    files: List<File>,
    selectedFiles: Set<File>,
    onSelectedFilesChange: (Set<File>) -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp
) {
    val scope = rememberCoroutineScope()
    var viewerStartIndex by remember { mutableIntStateOf(-1) }
    var videoToView by remember { mutableStateOf<File?>(null) }
    var audioToPlay by remember { mutableStateOf<File?>(null) }

    val isMedia = category == "Photos" || category == "Videos"
    var gridColumnCount by remember(category) { mutableIntStateOf(if (isMedia) 4 else 1) }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }
    var transferActionType by remember { mutableStateOf("") }
    var isIslandExpanded by remember { mutableStateOf(true) }

    val gridState = rememberLazyGridState()
    var loadLimit by remember(category, files) { mutableIntStateOf(50) }
    val paginatedFiles = files.take(loadLimit)

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastIndex ->
                if (lastIndex != null && lastIndex >= loadLimit - 10) {
                    if (loadLimit < files.size) {
                        loadLimit += 50
                    }
                }
            }
    }

    if (files.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = IosTextGray, modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Dosya Bulunamadı", style = MaterialTheme.typography.titleLarge, color = IosTextGray)
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {

            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumnCount),
                state = gridState,
                modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (isMedia) {
                            if (zoom > 1.2f && gridColumnCount > 1) gridColumnCount--
                            else if (zoom < 0.8f && gridColumnCount < 5) gridColumnCount++
                        }
                    }
                },
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomPadding)
            ) {
                items(paginatedFiles) { file ->
                    val isSelected = selectedFiles.contains(file)
                    val scale by animateFloatAsState(targetValue = if (isSelected) 0.85f else 1f, animationSpec = tween(200), label = "scaleAnim")

                    Box(
                        modifier = Modifier.then(if (isMedia) Modifier.aspectRatio(1f) else Modifier.fillMaxWidth()).graphicsLayer { scaleX = scale; scaleY = scale }.clip(RoundedCornerShape(12.dp)).border(if (isSelected) 3.dp else 0.dp, IosAccentBlue, RoundedCornerShape(12.dp)).background(if (isMedia) Color.Transparent else IosSurface)
                            .combinedClickable(
                                onClick = {
                                    if (selectedFiles.isNotEmpty()) {
                                        val newSet = if (isSelected) selectedFiles - file else selectedFiles + file
                                        onSelectedFilesChange(newSet)
                                        if (newSet.isEmpty()) isIslandExpanded = true
                                    } else {
                                        if (isMedia) viewerStartIndex = files.indexOf(file)
                                        else if (category == "Audio") audioToPlay = file
                                    }
                                },
                                onLongClick = {
                                    val newSet = if (isSelected) selectedFiles - file else selectedFiles + file
                                    onSelectedFilesChange(newSet)
                                    if (newSet.isEmpty()) isIslandExpanded = true
                                }
                            )
                    ) {
                        if (isMedia) {
                            AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(file).decoderFactory(VideoFrameDecoder.Factory()).crossfade(true).build(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            if (category == "Videos") {
                                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
                                Icon(Icons.Default.PlayCircleOutline, contentDescription = "Oynat", tint = Color.White, modifier = Modifier.align(Alignment.Center).size(36.dp))
                            }
                        } else if (category == "Audio") {
                            Row(modifier = Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(48.dp).background(ColorAudio, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Audiotrack, contentDescription = null, tint = Color(0xFF4CAF50)) }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(file.nameWithoutExtension, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge, color = IosTextDark, fontWeight = FontWeight.SemiBold)
                                    val kb = file.length() / 1024
                                    Text(if (kb > 1024) "${kb / 1024} MB" else "$kb KB", style = MaterialTheme.typography.bodySmall, color = IosTextGray)
                                }
                                Icon(Icons.Default.PlayArrow, contentDescription = "Dinle", tint = IosAccentBlue)
                            }
                        } else {
                            Row(modifier = Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = IosAccentBlue)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(file.name, maxLines = 1, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = IosTextDark)
                            }
                        }
                    }
                }
            }

            if (selectedFiles.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isIslandExpanded, enter = scaleIn(transformOrigin = TransformOrigin(1f, 0f), animationSpec = tween(300)) + fadeIn(), exit = scaleOut(transformOrigin = TransformOrigin(1f, 0f), animationSpec = tween(300)) + fadeOut(),
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(0.92f).shadow(16.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(IslandBackground).padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { isIslandExpanded = false }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Remove, "Küçült", tint = IosTextDark, modifier = Modifier.size(20.dp)) }
                                Spacer(modifier = Modifier.width(8.dp))
                                LazyRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy((-12).dp), contentPadding = PaddingValues(end = 12.dp)) {
                                    items(selectedFiles.toList()) { file ->
                                        Box(modifier = Modifier.size(40.dp).clip(CircleShape).border(2.dp, IslandBackground, CircleShape).background(Color(0xFFE5E5EA))) {
                                            if (isMedia) AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(file).decoderFactory(VideoFrameDecoder.Factory()).crossfade(true).build(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                            else Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = IosTextGray, modifier = Modifier.size(20.dp).align(Alignment.Center))
                                        }
                                    }
                                }
                                Text(text = "${selectedFiles.size} Öğe", style = MaterialTheme.typography.labelLarge, color = IosAccentBlue)
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(onClick = { onSelectedFilesChange(if (selectedFiles.size == files.size) emptySet() else files.toSet()) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.DoneAll, "Tümünü Seç", tint = IosAccentBlue, modifier = Modifier.size(20.dp)) }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = Color(0xFFE5E5EA), modifier = Modifier.fillMaxWidth(0.9f))
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(0.9f), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { transferActionType = "copy"; showTransferDialog = true }.padding(4.dp)) { Icon(Icons.Default.ContentCopy, "Kopyala", tint = IosTextDark, modifier = Modifier.size(20.dp)); Spacer(modifier = Modifier.height(2.dp)); Text("Kopyala", style = MaterialTheme.typography.labelSmall, color = IosTextDark) }
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { transferActionType = "move"; showTransferDialog = true }.padding(4.dp)) { Icon(Icons.Default.DriveFileMove, "Taşı", tint = IosTextDark, modifier = Modifier.size(20.dp)); Spacer(modifier = Modifier.height(2.dp)); Text("Taşı", style = MaterialTheme.typography.labelSmall, color = IosTextDark) }
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { val uris = ArrayList(selectedFiles.map { Uri.parse("file://${it.absolutePath}") }); val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply { type = "*/*"; putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris) }; context.startActivity(Intent.createChooser(intent, "Paylaş")); onSelectedFilesChange(emptySet()) }.padding(4.dp)) { Icon(Icons.Default.Share, "Paylaş", tint = IosTextDark, modifier = Modifier.size(20.dp)); Spacer(modifier = Modifier.height(2.dp)); Text("Paylaş", style = MaterialTheme.typography.labelSmall, color = IosTextDark) }
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { showDeleteDialog = true }.padding(4.dp)) { Icon(Icons.Default.Delete, "Sil", tint = Color.Red, modifier = Modifier.size(20.dp)); Spacer(modifier = Modifier.height(2.dp)); Text("Sil", style = MaterialTheme.typography.labelSmall, color = Color.Red) }
                            }
                        }
                    }

                    val infiniteTransition = rememberInfiniteTransition(label = "floating")
                    val floatOffset by infiniteTransition.animateFloat(initialValue = 0f, targetValue = -12f, animationSpec = infiniteRepeatable(animation = tween(1500, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse), label = "floatAnim")

                    androidx.compose.animation.AnimatedVisibility(
                        visible = !isIslandExpanded, enter = scaleIn(transformOrigin = TransformOrigin(1f, 0f), animationSpec = tween(300, delayMillis = 150)) + fadeIn(), exit = scaleOut(transformOrigin = TransformOrigin(1f, 0f), animationSpec = tween(200)) + fadeOut(),
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp).offset(y = floatOffset.dp)
                    ) {
                        Card(shape = CircleShape, colors = CardDefaults.cardColors(containerColor = IslandBackground), elevation = CardDefaults.cardElevation(defaultElevation = 8.dp), modifier = Modifier.clickable { isIslandExpanded = true }) {
                            Row(modifier = Modifier.padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                val previewFiles = selectedFiles.take(3).toList()
                                Box(modifier = Modifier.width((previewFiles.size * 20 + 16).dp).height(36.dp)) {
                                    previewFiles.forEachIndexed { index, file ->
                                        Box(modifier = Modifier.size(36.dp).offset(x = (index * 20).dp).clip(CircleShape).border(2.dp, IslandBackground, CircleShape).background(Color(0xFFE5E5EA)).zIndex((previewFiles.size - index).toFloat())) {
                                            if (isMedia) AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(file).decoderFactory(VideoFrameDecoder.Factory()).crossfade(true).build(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                            else Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = IosTextGray, modifier = Modifier.size(16.dp).align(Alignment.Center))
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "${selectedFiles.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = IosAccentBlue)
                            }
                        }
                    }
                }
            }
        }
    }

    if (viewerStartIndex >= 0 && isMedia) {
        val pagerState = rememberPagerState(initialPage = viewerStartIndex, pageCount = { files.size })
        val filmstripScrollState = rememberLazyListState()

        LaunchedEffect(pagerState.currentPage) {
            filmstripScrollState.animateScrollToItem(pagerState.currentPage)
        }

        Dialog(onDismissRequest = { viewerStartIndex = -1 }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    val file = files[page]
                    if (category == "Videos") {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(file).decoderFactory(VideoFrameDecoder.Factory()).crossfade(true).build(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
                            IconButton(onClick = { videoToView = file }, modifier = Modifier.size(80.dp)) { Icon(Icons.Default.PlayCircleOutline, contentDescription = "Oynat", tint = Color.White, modifier = Modifier.fillMaxSize()) }
                        }
                    } else {
                        AsyncImage(model = file, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(horizontal = 16.dp, vertical = 24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    val currentFile = files.getOrNull(pagerState.currentPage)
                    val isSelected = currentFile != null && selectedFiles.contains(currentFile)

                    if (currentFile != null) {
                        Row(modifier = Modifier.clip(RoundedCornerShape(50)).background(if (isSelected) IosAccentBlue else Color.Black.copy(alpha = 0.5f))
                            .clickable {
                                val newSet = if (isSelected) selectedFiles - currentFile else selectedFiles + currentFile
                                onSelectedFilesChange(newSet)
                                if (newSet.isNotEmpty()) isIslandExpanded = true
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, contentDescription = "Seç", tint = Color.White, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isSelected) "Seçildi" else "Seç", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else { Spacer(modifier = Modifier.width(1.dp)) }

                    IconButton(onClick = { viewerStartIndex = -1 }, modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)) { Icon(Icons.Default.Close, "Kapat", tint = Color.White) }
                }

                LazyRow(state = filmstripScrollState, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.6f)).padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    itemsIndexed(files.take(minOf(files.size, loadLimit + 50))) { index, file ->
                        val isSelectedThumbnail = pagerState.currentPage == index
                        Box(modifier = Modifier.size(if (isSelectedThumbnail) 64.dp else 48.dp).clip(RoundedCornerShape(8.dp)).border(if (isSelectedThumbnail) 2.dp else 0.dp, IosAccentBlue, RoundedCornerShape(8.dp)).clickable { scope.launch { pagerState.animateScrollToPage(index) } }, contentAlignment = Alignment.Center) {
                            AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(file).decoderFactory(VideoFrameDecoder.Factory()).crossfade(true).build(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            if (category == "Videos") {
                                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    videoToView?.let { file -> VideoPlayerDialog(videoFile = file) { videoToView = null } }
    audioToPlay?.let { file -> AudioPlayerDialog(audioFile = file) { audioToPlay = null } }

    if (showDeleteDialog) {
        AlertDialog(
            containerColor = IosSurface, onDismissRequest = { showDeleteDialog = false },
            title = { Text("Sil", color = IosTextDark) },
            text = { Text("${selectedFiles.size} dosyayı silmek istiyor musunuz?", color = IosTextGray) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        selectedFiles.forEach { it.delete() }
                        withContext(Dispatchers.Main) {
                            onSelectedFilesChange(emptySet())
                            showDeleteDialog = false
                            isIslandExpanded = true
                        }
                    }
                }) { Text("Sil", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("İptal", color = IosAccentBlue) } }
        )
    }

    if (showTransferDialog) {
        AlertDialog(
            containerColor = IosSurface, onDismissRequest = { showTransferDialog = false },
            title = { Text("Hedef Seç", color = IosTextDark) },
            text = {
                val context = LocalContext.current
                val storagePaths = remember { getStorageDirectories(context) }
                Column {
                    storagePaths.forEach { path ->
                        val label = if (path.contains("emulated")) "Dahili Hafıza" else "SD Kart"
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            onClick = {
                                scope.launch {
                                    performTransfer(context, selectedFiles, transferActionType, path)
                                    onSelectedFilesChange(emptySet())
                                    showTransferDialog = false
                                    isIslandExpanded = true
                                }
                            }
                        ) { Text(label, color = IosTextDark) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTransferDialog = false }) { Text("İptal", color = IosAccentBlue) } }
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
fun AudioPlayerDialog(audioFile: File, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse("file://${audioFile.absolutePath}")))
            prepare()
            playWhenReady = true
        }
    }
    var isPlaying by remember { mutableStateOf(true) }
    DisposableEffect(Unit) {
        val listener = object : Player.Listener { override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing } }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener); exoPlayer.release() }
    }
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(32.dp), colors = CardDefaults.cardColors(containerColor = IosSurface), elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.size(96.dp).background(IosLightBackground, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Audiotrack, contentDescription = null, tint = IosAccentBlue, modifier = Modifier.size(48.dp)) }
                Spacer(modifier = Modifier.height(24.dp))
                Text(text = audioFile.nameWithoutExtension, style = MaterialTheme.typography.titleLarge, color = IosTextDark, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(32.dp))
                Row(horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp).background(IosLightBackground, CircleShape)) { Icon(Icons.Default.Close, contentDescription = "Kapat", tint = IosTextGray) }
                    FloatingActionButton(onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() }, containerColor = IosAccentBlue, contentColor = Color.White, shape = CircleShape, modifier = Modifier.size(64.dp)) { Icon(imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Oynat/Duraklat", modifier = Modifier.size(32.dp)) }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerDialog(videoFile: File, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse("file://${videoFile.absolutePath}")))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(factory = { ctx -> PlayerView(ctx).apply { player = exoPlayer; useController = true } }, modifier = Modifier.fillMaxSize())
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopStart).padding(32.dp)) { Icon(Icons.Default.Close, "Kapat", tint = Color.White, modifier = Modifier.size(32.dp)) }
        }
    }
}

suspend fun performTransfer(context: Context, files: Set<File>, action: String, targetRootPath: String) {
    withContext(Dispatchers.IO) {
        val targetDir = File(targetRootPath, "EmptyTransfer")
        if (!targetDir.exists()) targetDir.mkdirs()
        files.forEach { file ->
            try {
                if (action == "copy") file.copyTo(File(targetDir, file.name), overwrite = true)
                else if (action == "move") { file.copyTo(File(targetDir, file.name), overwrite = true); file.delete() }
            } catch (e: Exception) {}
        }
        withContext(Dispatchers.Main) { Toast.makeText(context, "${files.size} işlem tamam.", Toast.LENGTH_SHORT).show() }
    }
}

fun checkStoragePermission(context: Context): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
fun getStorageDirectories(context: Context): List<String> {
    val paths = mutableListOf(Environment.getExternalStorageDirectory().absolutePath)
    ContextCompat.getExternalFilesDirs(context, null).forEach { dir ->
        if (dir != null && dir.absolutePath.contains("/Android/data/")) {
            val root = dir.absolutePath.substringBefore("/Android/data/")
            if (root != Environment.getExternalStorageDirectory().absolutePath) paths.add(root)
        }
    }
    return paths.distinct()
}