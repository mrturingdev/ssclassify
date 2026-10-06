package com.mrturingdev.ssclassify.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mrturingdev.ssclassify.PERMISSION_DENIED
import com.mrturingdev.ssclassify.classify.ScreenshotContentSummarizer
import com.mrturingdev.ssclassify.data.MediaScanner
import com.mrturingdev.ssclassify.data.ScanOutcome
import com.mrturingdev.ssclassify.data.ThumbnailLoader
import com.mrturingdev.ssclassify.model.CategorySource
import com.mrturingdev.ssclassify.model.ImageCategory
import com.mrturingdev.ssclassify.model.ImageRecord

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
    onDeleteScreenshots: (List<String>) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    /** A screenshot to open in the detail page, e.g. from a widget tap; null when none. */
    openDetailId: String? = null,
    onDetailOpened: () -> Unit = {},
    /** Debug builds only: exports a screenshot's OCR lines as fixture JSON; null hides the action. */
    onExportOcrLines: ((id: String) -> Unit)? = null,
) {
    var selected by rememberSaveable { mutableStateOf(ALL_KEY) }
    var selectedSubCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var activeDetailId by rememberSaveable { mutableStateOf<String?>(null) }
    var fullscreenImageId by rememberSaveable { mutableStateOf<String?>(null) }
    val collapse = rememberCollapsingHeaderState()

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

    // Wait for the library to load, then open it; a deleted screenshot just lands on the grid.
    LaunchedEffect(openDetailId, outcome) {
        if (openDetailId == null || outcome !is ScanOutcome.Success) return@LaunchedEffect
        if (outcome.images.any { it.id == openDetailId }) {
            fullscreenImageId = null
            activeDetailId = openDetailId
        }
        onDetailOpened()
    }

    // Decided here, not inside the effect: the effect would read activeDetailId live but
    // activeDetailImage from an older frame, and close a page another effect just opened.
    val detailGone = activeDetailId != null && activeDetailImage == null
    LaunchedEffect(detailGone) {
        if (detailGone) activeDetailId = null
    }

    if (activeDetailImage != null) {
        ScreenshotDetailScreen(
            image = activeDetailImage,
            thumbnailLoader = thumbnailLoader,
            onBack = { activeDetailId = null },
            onOpenFullscreen = { fullscreenImageId = activeDetailImage.id },
            onExportOcrLines = onExportOcrLines?.let { export -> { export(activeDetailImage.id) } },
            onCategoryChange = { onCategoryChange(activeDetailImage.id, it) },
            onDelete = {
                onDeleteScreenshots(listOf(activeDetailImage.id))
                activeDetailId = null
            },
            modifier = modifier,
        )
    } else {
        // Only Success shows a scrollable grid; anywhere else a hidden bar could never come back.
        val canCollapse = outcome is ScanOutcome.Success
        LaunchedEffect(canCollapse) {
            if (!canCollapse) collapse.expand()
        }
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { AppWordmark() },
                    navigationIcon = {
                        IconButton(onClick = onOpenSettings) {
                            AppLogo(
                                size = 28.dp,
                                modifier = Modifier.semantics { contentDescription = "Settings" },
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onScan, enabled = !scanning) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Rescan")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    // Pinned so the bar ignores scrolling itself; [collapse] drives its height offset.
                    scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(collapse.bar),
                )
            },
            modifier = modifier.nestedScroll(collapse.connection),
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
                        val searching = query.isNotBlank()
                        CategoryBrowser(
                            collapse = collapse,
                            query = query,
                            onQueryChange = onQueryChange,
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
                            onDeleteScreenshots = onDeleteScreenshots,
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
private const val CLEANUP_KEY = "cleanup"
private const val CLEANUP_SUB_ALL = "all_cleanup"
private const val CLEANUP_SUB_BLANK = "blank_screens"
private const val CLEANUP_SUB_UNCAT = "uncategorized"

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(12.dp)
    val colors = OutlinedTextFieldDefaults.colors()
    // BasicTextField + the outlined decoration: OutlinedTextField enforces a 56dp minimum height.
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp)
            .height(44.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        interactionSource = interactionSource,
    ) { innerTextField ->
        OutlinedTextFieldDefaults.DecorationBox(
            value = query,
            innerTextField = innerTextField,
            enabled = true,
            singleLine = true,
            visualTransformation = VisualTransformation.None,
            interactionSource = interactionSource,
            placeholder = { Text("Search text in screenshots") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                    }
                }
            },
            colors = colors,
            contentPadding = OutlinedTextFieldDefaults.contentPadding(top = 0.dp, bottom = 0.dp),
            container = {
                OutlinedTextFieldDefaults.Container(
                    enabled = true,
                    isError = false,
                    interactionSource = interactionSource,
                    colors = colors,
                    shape = shape,
                )
            },
        )
    }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryBrowser(
    collapse: CollapsingHeaderState,
    query: String,
    onQueryChange: (String) -> Unit,
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
    onDeleteScreenshots: (List<String>) -> Unit,
) {
    // Track by id so the dialog shows the fresh record after a category change.
    var detailId by remember { mutableStateOf<String?>(null) }
    val grouped = images.groupBy { it.category }
    val counts = grouped.mapValues { it.value.size }

    // Cleanup mode state
    val isCleanupMode = selectedKey == CLEANUP_KEY
    var cleanupSubFilter by remember(isCleanupMode) { mutableStateOf(CLEANUP_SUB_ALL) }
    val cleanupImages = images.filter { it.isCleanUpCandidate }
    val blankCount = cleanupImages.count { it.isBlankScreen }
    val uncatCount = cleanupImages.size - blankCount

    val selectedBaseImages = when (selectedKey) {
        ALL_KEY -> images
        CLEANUP_KEY -> cleanupImages
        else -> grouped.entries.firstOrNull { it.key.name == selectedKey }?.value.orEmpty()
    }
    val subCategories = if (isCleanupMode || selectedKey == ALL_KEY) {
        emptyList()
    } else {
        selectedBaseImages.mapNotNull { it.subCategory }.distinct().sorted()
    }
    val visible = when {
        isCleanupMode -> when (cleanupSubFilter) {
            CLEANUP_SUB_BLANK -> cleanupImages.filter { it.isBlankScreen }
            CLEANUP_SUB_UNCAT -> cleanupImages.filter { !it.isBlankScreen }
            else -> cleanupImages
        }
        selectedSubCategoryKey != null -> selectedBaseImages.filter { it.subCategory == selectedSubCategoryKey }
        else -> selectedBaseImages
    }

    // An empty pane cannot scroll, so a hidden header could never come back.
    LaunchedEffect(visible.isEmpty()) {
        if (visible.isEmpty()) collapse.expand()
    }

    Column(Modifier.fillMaxSize()) {
        // Search and sub-filters collapse with the top app bar; the category row stays.
        CollapsingHeader(
            state = collapse,
            top = { SearchField(query = query, onQueryChange = onQueryChange) },
            pinned = {
                CategoryFilterRow(
                    counts = counts,
                    images = images,
                    selectedKey = selectedKey,
                    onSelect = onSelect,
                    // Opaque so the sub-filters can slide underneath it.
                    modifier = Modifier.background(MaterialTheme.colorScheme.background),
                )
            },
            bottom = {
                if (isCleanupMode) {
                    if (cleanupImages.isNotEmpty()) {
                        CleanupBanner(total = cleanupImages.size, blankCount = blankCount, uncatCount = uncatCount)
                    }
                    CleanupFilterRow(
                        selected = cleanupSubFilter,
                        onSelect = { cleanupSubFilter = it },
                        total = cleanupImages.size,
                        blankCount = blankCount,
                        uncatCount = uncatCount,
                    )
                } else if (subCategories.isNotEmpty()) {
                    SubCategoryRow(
                        subCategories = subCategories,
                        selected = selectedSubCategoryKey,
                        onSelect = onSelectSubCategory,
                    )
                }
            },
        )

        when {
            visible.isNotEmpty() -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                // Build tiles up to a screen ahead in idle time between frames, not inside a fling frame.
                state = rememberLazyGridState(cacheWindow = LazyLayoutCacheWindow(aheadFraction = 1f, behindFraction = 0.5f)),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxSize()
                    // Found by :classyBenchmark to fling the grid.
                    .testTag("screenshot_grid"),
            ) {
                items(visible, key = { it.id }) { image ->
                    ImageCard(
                        image = image,
                        thumbnailLoader = thumbnailLoader,
                        onClick = { detailId = image.id },
                    )
                }
            }

            isCleanupMode -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(
                        Icons.Rounded.CleaningServices,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                    Text(
                        "No items to clean up!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
        }
    }

    // Detail dialog — shown in both cleanup and normal mode
    if (detailId != null) {
        val browsingImages = remember(detailId) {
            if (visible.any { it.id == detailId }) visible else images
        }
        val currentRecords = remember(images, browsingImages) {
            browsingImages.map { img -> images.firstOrNull { it.id == img.id } ?: img }
        }
        ImageDetailDialog(
            images = currentRecords,
            initialImageId = detailId!!,
            thumbnailLoader = thumbnailLoader,
            onDismiss = { detailId = null },
            onCategoryChange = onCategoryChange,
            onOpenFullscreen = onOpenFullscreen,
            onNavigateToDetails = { id ->
                detailId = null
                onNavigateToDetails(id)
            },
            onDelete = { id ->
                detailId = null
                onDeleteScreenshots(listOf(id))
            },
        )
    }
}



@Composable
private fun CleanupBanner(total: Int, blankCount: Int, uncatCount: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Rounded.CleaningServices,
                contentDescription = null,
                modifier = Modifier.size(22.dp).padding(top = 2.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "You can clean up $total image${if (total == 1) "" else "s"}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                val parts = buildList {
                    if (blankCount > 0) add("$blankCount blank screen${if (blankCount == 1) "" else "s"}")
                    if (uncatCount > 0) add("$uncatCount uncategorized")
                }
                Text(
                    text = parts.joinToString(" · ") + " - tap any image to delete it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun CleanupFilterRow(
    selected: String,
    onSelect: (String) -> Unit,
    total: Int,
    blankCount: Int,
    uncatCount: Int,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = selected == CLEANUP_SUB_ALL,
                onClick = { onSelect(CLEANUP_SUB_ALL) },
                label = { Text("All ($total)") },
                shape = RoundedCornerShape(16.dp),
            )
        }
        item {
            FilterChip(
                selected = selected == CLEANUP_SUB_BLANK,
                onClick = { onSelect(CLEANUP_SUB_BLANK) },
                label = { Text("Blank Screens ($blankCount)") },
                leadingIcon = { Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(14.dp)) },
                shape = RoundedCornerShape(16.dp),
            )
        }
        item {
            FilterChip(
                selected = selected == CLEANUP_SUB_UNCAT,
                onClick = { onSelect(CLEANUP_SUB_UNCAT) },
                label = { Text("Uncategorized ($uncatCount)") },
                leadingIcon = { Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(14.dp)) },
                shape = RoundedCornerShape(16.dp),
            )
        }
    }
}

@Composable
private fun SubCategoryRow(subCategories: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("All") },
                shape = RoundedCornerShape(16.dp),
            )
        }
        items(subCategories, key = { it }) { sub ->
            FilterChip(
                selected = selected == sub,
                onClick = { onSelect(sub) },
                label = { Text(sub) },
                shape = RoundedCornerShape(16.dp),
            )
        }
    }
}

@Composable
private fun CategoryFilterRow(
    counts: Map<ImageCategory, Int>,
    images: List<ImageRecord>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cleanupCount = images.count { it.isCleanUpCandidate }
    LazyRow(
        modifier = modifier.fillMaxWidth(),
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
        item {
            CategoryChip(
                label = "Clean Up",
                icon = Icons.Rounded.CleaningServices,
                count = cleanupCount,
                selected = selectedKey == CLEANUP_KEY,
                onClick = { onSelect(CLEANUP_KEY) },
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
    selectable: Boolean = false,
    checked: Boolean = false,
) {
    var bitmap by remember(image.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(image.id) {
        bitmap = thumbnailLoader.load(image.id, 320)
    }
    // Plain modifiers rather than Card/Surface: a tile is built and measured inside a scroll
    // frame, and Card's shadow was GPU work on every frame (seen in :classyBenchmark traces).
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .then(if (checked) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
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
        val summary = remember(image) {
            // Keyed on the whole record: the preview reads its title too, which re-analysis can change alone.
            ScreenshotContentSummarizer.previewText(image)
        }
        val hasSummary = summary.isNotBlank() && summary != "No text detected"
        val hasSubCategory = !image.subCategory.isNullOrEmpty()

        if (!selectable && (hasSummary || hasSubCategory)) {
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
                        Text(
                            text = image.subCategory,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        )
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

        // Checkbox overlay for selection mode
        if (selectable) {
            if (checked) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
            ) {
                Icon(
                    imageVector = if (checked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = if (checked) "Selected" else "Not selected",
                    modifier = Modifier.size(22.dp),
                    tint = if (checked) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.85f),
                )
            }
        }
    }
}


@Composable
internal fun ImageDetailDialog(
    images: List<ImageRecord>,
    initialImageId: String,
    thumbnailLoader: ThumbnailLoader,
    onDismiss: () -> Unit,
    onCategoryChange: (id: String, ImageCategory?) -> Unit,
    onOpenFullscreen: (id: String) -> Unit,
    onNavigateToDetails: (id: String) -> Unit,
    onDelete: (id: String) -> Unit,
) {
    if (images.isEmpty()) return

    val initialIndex = remember(initialImageId) {
        images.indexOfFirst { it.id == initialImageId }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { images.size },
    )
    val coroutineScope = rememberCoroutineScope()
    val currentImage = images.getOrNull(pagerState.currentPage) ?: images.first()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Screenshot?") },
            text = { Text("This will permanently delete this screenshot from your device. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(currentImage.id)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                FilledTonalButton(
                    onClick = { showDeleteConfirm = false },
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = if (images.size > 1) {
            {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (pagerState.currentPage > 0) {
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Previous screenshot",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        Spacer(Modifier.size(28.dp))
                    }

                    Text(
                        text = "${pagerState.currentPage + 1} of ${images.size}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (pagerState.currentPage < images.size - 1) {
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = "Next screenshot",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        Spacer(Modifier.size(28.dp))
                    }
                }
            }
        } else null,
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    onDismiss()
                    onNavigateToDetails(currentImage.id)
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = "Delete screenshot",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("Done")
                }
            }
        },
        text = {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
            ) { page ->
                val pageImage = images.getOrNull(page) ?: return@HorizontalPager
                ImageDetailContent(
                    image = pageImage,
                    thumbnailLoader = thumbnailLoader,
                    onOpenFullscreen = { onOpenFullscreen(pageImage.id) },
                    onCategoryChange = { onCategoryChange(pageImage.id, it) },
                )
            }
        },
    )
}

@Composable
private fun ImageDetailContent(
    image: ImageRecord,
    thumbnailLoader: ThumbnailLoader,
    onOpenFullscreen: () -> Unit,
    onCategoryChange: (ImageCategory?) -> Unit,
) {
    var bitmap by remember(image.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(image.id) {
        bitmap = thumbnailLoader.load(image.id, 720)
    }

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
                    contentDescription = null,
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

        // Summary card
        val summary = remember(image) {
            // Keyed on the whole record: the preview reads its title too, which re-analysis can change alone.
            ScreenshotContentSummarizer.previewText(image)
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
    ImageCategory.QR -> Icons.Rounded.QrCodeScanner
    ImageCategory.Learning -> Icons.Rounded.School
    ImageCategory.Travels -> Icons.Rounded.Flight
    ImageCategory.Foods -> Icons.Rounded.Restaurant
    ImageCategory.Health -> Icons.Rounded.LocalHospital
    ImageCategory.Others -> Icons.Rounded.Category
    ImageCategory.Uncategorized -> Icons.Rounded.Screenshot
}