package com.agy.imagecategorizer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agy.imagecategorizer.PERMISSION_DENIED
import com.agy.imagecategorizer.classify.ScreenshotContentSummarizer
import com.agy.imagecategorizer.data.MediaScanner
import com.agy.imagecategorizer.data.ScanOutcome
import com.agy.imagecategorizer.data.ThumbnailLoader
import com.agy.imagecategorizer.model.CategorySource
import com.agy.imagecategorizer.model.ImageCategory
import com.agy.imagecategorizer.model.ImageRecord

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    scanning: Boolean,
    outcome: ScanOutcome?,
    scanner: MediaScanner,
    thumbnailLoader: ThumbnailLoader,
    onScan: () -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    searchResults: List<ImageRecord>?,
    onCategoryChange: (id: String, category: ImageCategory?) -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(ALL_KEY) }
    var selectedSubCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var activeDetailId by rememberSaveable { mutableStateOf<String?>(null) }
    var fullscreenImageId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        scanner.watchChanges().collect { onScan() }
    }

    val currentImages = when (outcome) {
        is ScanOutcome.Success -> searchResults ?: outcome.images
        else -> searchResults ?: emptyList()
    }
    val activeDetailImage = currentImages.firstOrNull { it.id == activeDetailId }
        ?: (outcome as? ScanOutcome.Success)?.images?.firstOrNull { it.id == activeDetailId }

    val fullscreenImage = currentImages.firstOrNull { it.id == fullscreenImageId }
        ?: (outcome as? ScanOutcome.Success)?.images?.firstOrNull { it.id == fullscreenImageId }

    LaunchedEffect(activeDetailId, currentImages) {
        if (activeDetailId != null && activeDetailImage == null) {
            activeDetailId = null
        }
    }

    if (activeDetailImage != null) {
        ScreenshotDetailScreen(
            image = activeDetailImage,
            thumbnailLoader = thumbnailLoader,
            onBack = { activeDetailId = null },
            onOpenFullscreen = { fullscreenImageId = activeDetailImage.id },
            onCategoryChange = { onCategoryChange(activeDetailImage.id, it) },
        )
    } else {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "Image Categorizer",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        )
                    },
                    actions = {
                        IconButton(onClick = onScan, enabled = !scanning) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Rescan")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                )
            },
            modifier = modifier,
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                when {
                    scanning && outcome !is ScanOutcome.Success -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(44.dp),
                            )
                            Text(
                                "Analyzing your photos\u2026",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    outcome is ScanOutcome.Failure -> ErrorPane(
                        modifier = Modifier.fillMaxSize(),
                        reason = outcome.reason,
                        onScan = onScan,
                    )

                    outcome is ScanOutcome.Success -> {
                        // Cached results stay visible while an incremental scan catches up.
                        if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
                        SearchField(query = query, onQueryChange = onQueryChange)
                        val searching = query.isNotBlank()
                        CategoryBrowser(
                            images = searchResults ?: outcome.images,
                            emptyMessage = if (searching) {
                                "No screenshots contain \u201C${query.trim()}\u201D."
                            } else {
                                "No images in this category yet."
                            },
                            onRescan = if (searching) null else onScan,
                            thumbnailLoader = thumbnailLoader,
                            selectedKey = selected,
                            onSelect = {
                                selected = it
                                selectedSubCategory = null
                            },
                            selectedSubCategoryKey = selectedSubCategory,
                            onSelectSubCategory = { selectedSubCategory = it },
                            onCategoryChange = onCategoryChange,
                            onOpenFullscreen = { fullscreenImageId = it },
                            onNavigateToDetails = { activeDetailId = it },
                        )
                    }

                    else -> EmptyLook(
                        modifier = Modifier.fillMaxSize(),
                        onScan = onScan,
                    )
                }
            }
        }
    }

    fullscreenImage?.let { img ->
        FullscreenImageViewer(
            image = img,
            thumbnailLoader = thumbnailLoader,
            onDismiss = { fullscreenImageId = null },
        )
    }
}

private const val ALL_KEY = "all"

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp),
        placeholder = { Text("Search text in screenshots") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
    )
}

@Composable
private fun ErrorPane(modifier: Modifier, reason: String, onScan: () -> Unit) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Access Needed",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
        )
        Text(
            when (reason) {
                PERMISSION_DENIED -> "Photo library access is required to categorize your screenshots and downloads. Grant permission in system settings to proceed."
                else -> reason
            },
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onScan,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.height(44.dp),
        ) {
            Text("Try Again", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun EmptyLook(modifier: Modifier, onScan: () -> Unit) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(80.dp),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            "Scan Your Photos",
            modifier = Modifier.padding(top = 20.dp),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
        )
        Text(
            "Discover receipts, documents, chats, and screenshots organized on-device with zero cloud uploads.",
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onScan,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.height(44.dp),
        ) {
            Text("Start Scanning", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun CategoryBrowser(
    images: List<ImageRecord>,
    emptyMessage: String,
    onRescan: (() -> Unit)?,
    thumbnailLoader: ThumbnailLoader,
    selectedKey: String,
    onSelect: (String) -> Unit,
    selectedSubCategoryKey: String?,
    onSelectSubCategory: (String?) -> Unit,
    onCategoryChange: (id: String, category: ImageCategory?) -> Unit,
    onOpenFullscreen: (id: String) -> Unit,
    onNavigateToDetails: (id: String) -> Unit,
) {
    // Track by id so the dialog shows the fresh record after a category change.
    var detailId by remember { mutableStateOf<String?>(null) }
    val grouped = images.groupBy { it.category }
    val counts = grouped.mapValues { it.value.size }

    Column(Modifier.fillMaxSize()) {
        CategoryFilterRow(
            counts = counts,
            selectedKey = selectedKey,
            onSelect = onSelect,
        )

        val selectedBaseImages = if (selectedKey == ALL_KEY) {
            images
        } else {
            grouped.entries.firstOrNull { it.key.name == selectedKey }?.value.orEmpty()
        }

        // Subcategory filter row
        if (selectedKey != ALL_KEY && selectedBaseImages.isNotEmpty()) {
            val subCategories = selectedBaseImages.mapNotNull { it.subCategory }.distinct().sorted()
            if (subCategories.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = selectedSubCategoryKey == null,
                            onClick = { onSelectSubCategory(null) },
                            label = { Text("All") },
                            shape = RoundedCornerShape(16.dp),
                        )
                    }
                    items(subCategories, key = { it }) { sub ->
                        FilterChip(
                            selected = selectedSubCategoryKey == sub,
                            onClick = { onSelectSubCategory(sub) },
                            label = { Text(sub) },
                            shape = RoundedCornerShape(16.dp),
                        )
                    }
                }
            }
        }

        val visible = if (selectedSubCategoryKey != null) {
            selectedBaseImages.filter { it.subCategory == selectedSubCategoryKey }
        } else {
            selectedBaseImages
        }

        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        emptyMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                    if (onRescan != null) {
                        FilledTonalButton(
                            onClick = onRescan,
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Text("Rescan")
                        }
                    }
                }
            }
        } else {
            val gridState = rememberLazyGridState()
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                state = gridState,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(visible, key = { it.id }) { image ->
                    ImageCard(
                        image = image,
                        thumbnailLoader = thumbnailLoader,
                        onClick = { detailId = image.id },
                    )
                }
            }
        }
    }

    images.firstOrNull { it.id == detailId }?.let { detailImage ->
        ImageDetailDialog(
            image = detailImage,
            thumbnailLoader = thumbnailLoader,
            onDismiss = { detailId = null },
            onCategoryChange = { onCategoryChange(detailImage.id, it) },
            onOpenFullscreen = { onOpenFullscreen(detailImage.id) },
            onNavigateToDetails = {
                detailId = null
                onNavigateToDetails(detailImage.id)
            },
        )
    }
}

@Composable
private fun CategoryFilterRow(
    counts: Map<ImageCategory, Int>,
    selectedKey: String,
    onSelect: (String) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            CategoryChip(
                label = "All",
                icon = Icons.Rounded.FolderOpen,
                count = counts.values.sum(),
                selected = selectedKey == ALL_KEY,
                onClick = { onSelect(ALL_KEY) },
            )
        }
        items(ImageCategory.entries.toList(), key = { it.name }) { category ->
            CategoryChip(
                label = category.displayName,
                icon = category.icon(),
                count = counts[category] ?: 0,
                selected = selectedKey == category.name,
                onClick = { onSelect(category.name) },
            )
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    icon: ImageVector,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                "$label ($count)",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                ),
            )
        },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
        shape = RoundedCornerShape(18.dp),
    )
}

@Composable
private fun ImageCard(
    image: ImageRecord,
    thumbnailLoader: ThumbnailLoader,
    onClick: () -> Unit,
) {
    var bitmap by remember(image.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(image.id) {
        bitmap = thumbnailLoader.load(image.id, 320)
    }
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp, pressedElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val bmp = bitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = image.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            val summary = remember(image.ocrText, image.description) {
                ScreenshotContentSummarizer.summarizeDigest(image.ocrText, image.description)
            }
            val hasSummary = summary.isNotBlank() && summary != "No text detected"
            val hasSubCategory = !image.subCategory.isNullOrEmpty()

            if (hasSummary || hasSubCategory) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                            ),
                        )
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (hasSubCategory) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                            ) {
                                Text(
                                   text = image.subCategory,
                                   style = MaterialTheme.typography.labelSmall,
                                   color = Color.White,
                                   modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }
                        if (hasSummary) {
                            Text(
                                text = summary,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ImageDetailDialog(
    image: ImageRecord,
    thumbnailLoader: ThumbnailLoader,
    onDismiss: () -> Unit,
    onCategoryChange: (ImageCategory?) -> Unit,
    onOpenFullscreen: () -> Unit,
    onNavigateToDetails: () -> Unit,
) {
    var bitmap by remember(image.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(image.id) {
        bitmap = thumbnailLoader.load(image.id, 720)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    onDismiss()
                    onNavigateToDetails()
                },
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("View Full Details")
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("Done")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val bmp = bitmap
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onOpenFullscreen),
                    contentAlignment = Alignment.Center,
                ) {
                    if (bmp != null) {
                        Image(
                            bitmap = bmp,
                            contentDescription = image.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }

                    // Floating fullscreen expand button in top-right corner
                    IconButton(
                        onClick = onOpenFullscreen,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            modifier = Modifier.size(36.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Fullscreen,
                                    contentDescription = "Open Fullscreen",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }

                Text(
                    text = image.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                // Summary card
                val summary = remember(image.ocrText, image.description) {
                    ScreenshotContentSummarizer.summarizeDigest(image.ocrText, image.description)
                }
                if (summary.isNotBlank() && summary != "No text detected") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = "Summary",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(
                                text = summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                // Key highlights if any
                val highlights = remember(image.ocrText) {
                    ScreenshotContentSummarizer.extractHighlights(image.ocrText)
                }
                if (highlights.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        highlights.take(2).forEach { (label, value) ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            ) {
                                Text(
                                    text = "$label: $value",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CategoryPicker(image = image, onCategoryChange = onCategoryChange)
                    if (!image.subCategory.isNullOrEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                text = image.subCategory,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }

                val sourceNote = when (image.source) {
                    CategorySource.User -> "Category set by you \u00B7 automatic: ${image.autoCategory.displayName}"
                    CategorySource.Learned -> "Category learned from similar screenshots you corrected"
                    CategorySource.Rules -> null
                }
                if (sourceNote != null) {
                    Text(
                        text = sourceNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (image.width > 0 && image.height > 0) {
                    Text(
                        text = "Resolution: ${image.width} × ${image.height} px",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (!image.description.isNullOrEmpty() && image.description != summary) {
                    Text(
                        text = image.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
    )
}

/** Category pill that opens a menu to correct the category or reset it to automatic. */
@Composable
internal fun CategoryPicker(image: ImageRecord, onCategoryChange: (ImageCategory?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        ) {
            Row(
                modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    image.category.icon(),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = image.category.displayName,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    Icons.Rounded.ArrowDropDown,
                    contentDescription = "Change category",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (image.isCategoryCorrected) {
                DropdownMenuItem(
                    text = { Text("Automatic (${image.autoCategory.displayName})") },
                    leadingIcon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onCategoryChange(null)
                    },
                )
                HorizontalDivider()
            }
            ImageCategory.entries.forEach { category ->
                val current = category == image.category
                DropdownMenuItem(
                    text = { Text(category.displayName) },
                    leadingIcon = { Icon(category.icon(), contentDescription = null) },
                    trailingIcon = if (current) {
                        { Icon(Icons.Rounded.Check, contentDescription = "Current") }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        if (!current) onCategoryChange(category)
                    },
                )
            }
        }
    }
}

fun ImageCategory.icon(): ImageVector = when (this) {
    ImageCategory.Receipts -> Icons.AutoMirrored.Rounded.ReceiptLong
    ImageCategory.Finance -> Icons.Rounded.AccountBalance
    ImageCategory.Shopping -> Icons.Rounded.ShoppingCart
    ImageCategory.Travel -> Icons.Rounded.Flight
    ImageCategory.Food -> Icons.Rounded.Restaurant
    ImageCategory.Health -> Icons.Rounded.LocalHospital
    ImageCategory.Work -> Icons.Rounded.Work
    ImageCategory.Code -> Icons.Rounded.Code
    ImageCategory.Chat -> Icons.Rounded.ChatBubbleOutline
    ImageCategory.Social -> Icons.Rounded.People
    ImageCategory.Documents -> Icons.Rounded.Description
    ImageCategory.Other -> Icons.Rounded.Screenshot
}