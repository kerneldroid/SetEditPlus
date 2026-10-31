package com.seteditplus.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.ImportExport
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seteditplus.R
import com.seteditplus.data.PermissionMode
import com.seteditplus.data.SettingsTable
import com.seteditplus.util.HapticUtil
import com.seteditplus.viewmodel.FilterMode
import com.seteditplus.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

enum class ExtendedTab {
    SYSTEM, SECURE, GLOBAL, MODIFIED
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class
)
@Composable
fun MainScreen(
    viewModel: SettingsViewModel,
    onNavigateToSettings: () -> Unit
) {
    val view = LocalView.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val currentTab by viewModel.currentTab
    val filterMode by viewModel.filterMode
    val favoriteIds by viewModel.favoriteIds
    val permissionMode by viewModel.permissionMode
    val settings = viewModel.filteredSettings
    val isLoading by viewModel.isLoading
    val searchQuery by viewModel.searchQuery
    val isSearchActive by viewModel.isSearchActive
    val operationMessage by viewModel.operationMessage

    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    val showScrollToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 5 }
    }

    var showEditSheet by remember { mutableStateOf(false) }
    var editKey by remember { mutableStateOf("") }
    var editValue by remember { mutableStateOf("") }
    var editTable by remember { mutableStateOf(SettingsTable.SYSTEM) }

    var showAddSheet by remember { mutableStateOf(false) }
    var showSnapshotSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var showRestoreAllConfirm by remember { mutableStateOf(false) }
    var actionKey by remember { mutableStateOf("") }
    var actionTable by remember { mutableStateOf(SettingsTable.SYSTEM) }

    var isTopBarVisible by remember { mutableStateOf(true) }

    LaunchedEffect(operationMessage) {
        operationMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(16.dp)
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                )
            ) {
                AnimatedVisibility(
                    visible = showScrollToTop,
                    enter = fadeIn(tween(300)) + slideInVertically(
                        initialOffsetY = { 100 },
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f)
                    ) + scaleIn(
                        initialScale = 0.5f,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f)
                    ),
                    exit = fadeOut(tween(200)) + slideOutVertically(targetOffsetY = { 100 }) + scaleOut(targetScale = 0.8f)
                ) {
                    FloatingActionButton(
                        onClick = {
                            HapticUtil.performClick(view)
                            coroutineScope.launch {
                                listState.animateScrollToItem(0)
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = RoundedCornerShape(16.dp),
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(imageVector = Icons.Rounded.ArrowUpward, contentDescription = "Scroll to top")
                    }
                }

                Surface(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .combinedClickable(
                            onClick = {
                                HapticUtil.performClick(view)
                                showAddSheet = true
                            },
                            onLongClick = {
                                HapticUtil.performLongPress(view)
                                isTopBarVisible = !isTopBarVisible
                            }
                        ),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.cd_add)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedVisibility(
                visible = isTopBarVisible,
                enter = fadeIn(tween(300)) + slideInVertically(
                    initialOffsetY = { -it },
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
                ) + expandVertically(
                    expandFrom = Alignment.Top,
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
                ),
                exit = fadeOut(tween(300)) + slideOutVertically(
                    targetOffsetY = { -it },
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
                ) + shrinkVertically(
                    shrinkTowards = Alignment.Top,
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
                )
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp)
                            .padding(top = 16.dp)
                    ) {
                        AnimatedContent(
                            targetState = isSearchActive,
                            transitionSpec = {
                                (fadeIn(tween(300)) + slideInVertically { 40 }).togetherWith(
                                    fadeOut(tween(150)) + slideOutVertically { -40 }
                                )
                            },
                            label = "searchTransition"
                        ) { active ->
                            if (active) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(64.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val focusRequester = remember { FocusRequester() }
                                    LaunchedEffect(Unit) { focusRequester.requestFocus() }

                                    TextField(
                                        value = searchQuery,
                                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                                        placeholder = {
                                            Text(
                                                stringResource(R.string.search_hint),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .focusRequester(focusRequester),
                                        singleLine = true,
                                        shape = RoundedCornerShape(28.dp),
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent
                                        ),
                                        trailingIcon = {
                                            IconButton(onClick = {
                                                HapticUtil.performClick(view)
                                                viewModel.isSearchActive.value = false
                                                viewModel.onSearchQueryChanged("")
                                            }) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Close,
                                                    contentDescription = stringResource(R.string.cd_close_search)
                                                )
                                            }
                                        }
                                    )
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(64.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.app_name),
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        permissionMode?.let { mode ->
                                            BadgePill(
                                                text = if (mode == PermissionMode.ROOT) "ROOT" else "SHIZUKU",
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }

                                    Row {
                                        IconButton(onClick = {
                                            HapticUtil.performClick(view)
                                            viewModel.isSearchActive.value = true
                                        }) {
                                            Icon(
                                                imageVector = Icons.Rounded.Search,
                                                contentDescription = stringResource(R.string.cd_search),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        IconButton(onClick = {
                                            HapticUtil.performClick(view)
                                            showSnapshotSheet = true
                                        }) {
                                            Icon(
                                                imageVector = Icons.Rounded.ImportExport,
                                                contentDescription = stringResource(R.string.cd_backup),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        IconButton(onClick = {
                                            HapticUtil.performClick(view)
                                            onNavigateToSettings()
                                        }) {
                                            Icon(
                                                imageVector = Icons.Rounded.Settings,
                                                contentDescription = "Settings",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ExtendedTab.entries.forEachIndexed { index, tab ->
                                val labelRes = when (tab) {
                                    ExtendedTab.SYSTEM -> R.string.tab_system
                                    ExtendedTab.SECURE -> R.string.tab_secure
                                    ExtendedTab.GLOBAL -> R.string.tab_global
                                    ExtendedTab.MODIFIED -> R.string.tab_modified
                                }
                                val modCount = viewModel.modifiedSettingsMap.size
                                SegmentedButton(
                                    selected = currentTab == tab,
                                    onClick = {
                                        HapticUtil.performClick(view)
                                        viewModel.switchTab(tab)
                                        coroutineScope.launch { listState.scrollToItem(0) }
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = ExtendedTab.entries.size
                                    ),
                                    label = {
                                        val baseText = stringResource(labelRes)
                                        Text(
                                            text = if (tab == ExtendedTab.MODIFIED && modCount > 0) {
                                                "$baseText ($modCount)"
                                            } else {
                                                baseText
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            // Secondary toolbar: count + filter chips + Restore All button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnimatedContent(
                    targetState = settings.size,
                    transitionSpec = {
                        (fadeIn(tween(200)) + slideInVertically { -20 })
                            .togetherWith(fadeOut(tween(200)) + slideOutVertically { 20 })
                    },
                    label = "countAnim"
                ) { count ->
                    Text(
                        text = stringResource(R.string.settings_count, count),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, end = 4.dp)
                    )
                }

                FilterChip(
                    selected = filterMode == FilterMode.ALL,
                    onClick = {
                        HapticUtil.performClick(view)
                        viewModel.setFilterMode(FilterMode.ALL)
                    },
                    label = { Text(stringResource(R.string.filter_all)) }
                )

                FilterChip(
                    selected = filterMode == FilterMode.FAVORITES_ONLY,
                    onClick = {
                        HapticUtil.performClick(view)
                        viewModel.setFilterMode(
                            if (filterMode == FilterMode.FAVORITES_ONLY) FilterMode.ALL else FilterMode.FAVORITES_ONLY
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        val favCount = favoriteIds.size
                        Text(
                            if (favCount > 0) {
                                "${stringResource(R.string.filter_favorites)} ($favCount)"
                            } else {
                                stringResource(R.string.filter_favorites)
                            }
                        )
                    }
                )

                if (currentTab != ExtendedTab.MODIFIED) {
                    FilterChip(
                        selected = filterMode == FilterMode.MODIFIED_IN_TABLE,
                        onClick = {
                            HapticUtil.performClick(view)
                            viewModel.setFilterMode(
                                if (filterMode == FilterMode.MODIFIED_IN_TABLE) FilterMode.ALL else FilterMode.MODIFIED_IN_TABLE
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.FilterList,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text(stringResource(R.string.filter_modified)) }
                    )
                } else if (viewModel.modifiedSettingsMap.isNotEmpty()) {
                    FilterChip(
                        selected = false,
                        onClick = {
                            HapticUtil.performClick(view)
                            showRestoreAllConfirm = true
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Restore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text(stringResource(R.string.btn_restore_all)) }
                    )
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                }
            } else {
                val isRefreshing by viewModel.isRefreshing
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refreshSettings() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (settings.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (searchQuery.isNotEmpty() || filterMode != FilterMode.ALL) {
                                        stringResource(R.string.nothing_found)
                                    } else {
                                        stringResource(R.string.no_settings)
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 4.dp,
                                bottom = 140.dp + WindowInsets.navigationBars.asPaddingValues()
                                    .calculateBottomPadding()
                            )
                        ) {
                            itemsIndexed(
                                items = settings,
                                key = { _, entry -> entry.uniqueId }
                            ) { index, entry ->
                                val isFirst = index == 0
                                val isLast = index == settings.size - 1

                                val topRadius by animateDpAsState(
                                    targetValue = if (isFirst) 28.dp else 4.dp,
                                    label = "topRadius"
                                )
                                val bottomRadius by animateDpAsState(
                                    targetValue = if (isLast) 28.dp else 4.dp,
                                    label = "bottomRadius"
                                )
                                val shape = RoundedCornerShape(
                                    topStart = topRadius,
                                    topEnd = topRadius,
                                    bottomStart = bottomRadius,
                                    bottomEnd = bottomRadius
                                )

                                val modRecord = viewModel.getModifiedSetting(entry.table, entry.key)
                                val isPinned = favoriteIds.contains(entry.uniqueId)

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateItem(
                                            fadeInSpec = tween(300),
                                            fadeOutSpec = tween(300)
                                        )
                                        .padding(vertical = 1.dp)
                                        .clip(shape)
                                        .combinedClickable(
                                            onClick = {
                                                HapticUtil.performClick(view)
                                                if (currentTab == ExtendedTab.MODIFIED && modRecord?.isDeleted == true) {
                                                    actionKey = entry.key
                                                    actionTable = entry.table
                                                    showRestoreConfirm = true
                                                } else {
                                                    editKey = entry.key
                                                    editValue = entry.value
                                                    editTable = entry.table
                                                    showEditSheet = true
                                                }
                                            },
                                            onLongClick = {
                                                HapticUtil.performLongPress(view)
                                                actionKey = entry.key
                                                actionTable = entry.table
                                                if (currentTab == ExtendedTab.MODIFIED) {
                                                    showRestoreConfirm = true
                                                } else {
                                                    showDeleteConfirm = true
                                                }
                                            }
                                        ),
                                    shape = shape,
                                    color = MaterialTheme.colorScheme.surfaceContainer
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        val isDeletedItem = modRecord?.isDeleted == true
                                        val isAddedItem = modRecord != null && modRecord.stockValue == null && !isDeletedItem
                                        val isModifiedItem = modRecord != null && !isDeletedItem && !isAddedItem

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                if (currentTab == ExtendedTab.MODIFIED) {
                                                    BadgePill(
                                                        text = entry.table.displayName.uppercase(),
                                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                                    )
                                                }
                                                when {
                                                    isDeletedItem -> BadgePill(
                                                        text = "DELETED",
                                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                                    )
                                                    isAddedItem -> BadgePill(
                                                        text = "NEW",
                                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                                    )
                                                    isModifiedItem -> BadgePill(
                                                        text = "MODIFIED",
                                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                            }

                                            if (currentTab == ExtendedTab.MODIFIED || modRecord != null) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                            }

                                            Text(
                                                text = entry.key,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isDeletedItem) {
                                                    MaterialTheme.colorScheme.error
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = if (isDeletedItem) {
                                                    "Deleted (Stock: ${modRecord?.stockValue ?: "none"})"
                                                } else {
                                                    entry.value.ifEmpty { stringResource(R.string.empty_value) }
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = when {
                                                    isDeletedItem -> MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                                    isAddedItem -> MaterialTheme.colorScheme.primary
                                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (currentTab == ExtendedTab.MODIFIED && !isDeletedItem && modRecord?.stockValue != null) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "Stock: ${modRecord.stockValue.ifEmpty { "(empty)" }}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    HapticUtil.performClick(view)
                                                    viewModel.toggleFavorite(entry.table, entry.key)
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isPinned) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                                    contentDescription = stringResource(R.string.cd_favorite),
                                                    modifier = Modifier.size(18.dp),
                                                    tint = if (isPinned) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                                    }
                                                )
                                            }

                                            Icon(
                                                imageVector = if (currentTab == ExtendedTab.MODIFIED && isDeletedItem) {
                                                    Icons.Rounded.Restore
                                                } else {
                                                    Icons.Rounded.Edit
                                                },
                                                contentDescription = stringResource(R.string.cd_edit),
                                                modifier = Modifier
                                                    .padding(end = 8.dp)
                                                    .size(18.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
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

    if (showEditSheet) {
        val modRecord = viewModel.getModifiedSetting(editTable, editKey)
        EditSettingSheet(
            table = editTable,
            key = editKey,
            currentValue = editValue,
            stockValue = modRecord?.stockValue,
            onDismiss = { showEditSheet = false },
            onSave = { newValue ->
                viewModel.putSetting(editKey, newValue, editTable)
                showEditSheet = false
            },
            onDelete = {
                showEditSheet = false
                actionKey = editKey
                actionTable = editTable
                showDeleteConfirm = true
            },
            onRestoreStock = if (modRecord != null) {
                {
                    viewModel.restoreSetting(editKey, editTable)
                    showEditSheet = false
                }
            } else null,
            onCopyText = { label, text ->
                copyToClipboard(context, label, text)
                viewModel.operationMessage.value = "✓ Copied $label to clipboard"
            }
        )
    }

    if (showAddSheet) {
        AddSettingSheet(
            initialTable = viewModel.currentTable,
            onDismiss = { showAddSheet = false },
            onAdd = { targetTable, key, value ->
                viewModel.addSetting(key, value, targetTable)
                showAddSheet = false
            }
        )
    }

    if (showSnapshotSheet) {
        SnapshotSheet(
            viewModel = viewModel,
            onDismiss = { showSnapshotSheet = false },
            onCopyText = { label, text ->
                copyToClipboard(context, label, text)
                viewModel.operationMessage.value = "✓ Copied $label to clipboard"
            }
        )
    }

    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            key = "${actionTable.displayName}: $actionKey",
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                viewModel.deleteSetting(actionKey, actionTable)
                showDeleteConfirm = false
            }
        )
    }

    if (showRestoreConfirm) {
        RestoreConfirmDialog(
            key = "${actionTable.displayName}: $actionKey",
            onDismiss = { showRestoreConfirm = false },
            onConfirm = {
                viewModel.restoreSetting(actionKey, actionTable)
                showRestoreConfirm = false
            }
        )
    }

    if (showRestoreAllConfirm) {
        RestoreConfirmDialog(
            key = "all ${viewModel.modifiedSettingsMap.size} modified settings",
            onDismiss = { showRestoreAllConfirm = false },
            onConfirm = {
                viewModel.restoreAllModifiedSettings()
                showRestoreAllConfirm = false
            }
        )
    }
}

@Composable
fun BadgePill(
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(containerColor)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

@Composable
fun RestoreConfirmDialog(
    key: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val view = LocalView.current

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Text(
                text = "Restore setting?",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "Are you sure you want to restore '$key' to its original stock state (or delete it if newly added)?",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = {
                        HapticUtil.performClick(view)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Button(
                    onClick = {
                        HapticUtil.performConfirm(view)
                        onConfirm()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Restore")
                }
            }
        }
    )
}
