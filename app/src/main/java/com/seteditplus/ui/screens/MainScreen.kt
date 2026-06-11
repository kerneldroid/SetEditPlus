package com.seteditplus.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seteditplus.R
import com.seteditplus.data.SettingsTable
import com.seteditplus.ui.components.groupedItemShape
import com.seteditplus.util.HapticUtil
import com.seteditplus.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

enum class ExtendedTab {
    SYSTEM, SECURE, GLOBAL, MODIFIED
}

/**
 * Main settings editor screen with tab navigation (System/Secure/Global/Modified),
 * search bar, Settings menu, smart scroll-to-top, and grouped card list of settings.
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class
)
@Composable
fun MainScreen(
    viewModel: SettingsViewModel,
    onNavigateToSettings: () -> Unit // Callback to open full settings dialog or sheet
) {
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    
    val currentTab by viewModel.currentTab

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
    var showAddSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var deleteKey by remember { mutableStateOf("") }
    var showSettingsMenu by remember { mutableStateOf(false) }

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
                                (fadeIn(tween(300)) + slideInVertically { 40 }).togetherWith(fadeOut(tween(150)) + slideOutVertically { -40 })
                            },
                            label = "searchTransition"
                        ) { active ->
                            if (active) {
                                Row(modifier = Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
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
                                    modifier = Modifier.fillMaxWidth().height(64.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(R.string.app_name),
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

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
                                        Text(
                                            text = stringResource(labelRes),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }

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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )
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
                @OptIn(ExperimentalMaterial3Api::class)
                androidx.compose.material3.pulltorefresh.PullToRefreshBox(
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
                                    text = if (searchQuery.isNotEmpty()) stringResource(R.string.nothing_found) else stringResource(R.string.no_settings),
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
                        key = { _, entry -> entry.key }
                    ) { index, entry ->
                        val isFirst = index == 0
                        val isLast = index == settings.size - 1
                        
                        val topRadius by androidx.compose.animation.core.animateDpAsState(
                            targetValue = if (isFirst) 28.dp else 4.dp,
                            label = "topRadius"
                        )
                        val bottomRadius by androidx.compose.animation.core.animateDpAsState(
                            targetValue = if (isLast) 28.dp else 4.dp,
                            label = "bottomRadius"
                        )
                        val shape = RoundedCornerShape(
                            topStart = topRadius,
                            topEnd = topRadius,
                            bottomStart = bottomRadius,
                            bottomEnd = bottomRadius
                        )

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
                                        if (currentTab != ExtendedTab.MODIFIED) {
                                            editKey = entry.key
                                            editValue = entry.value
                                            showEditSheet = true
                                        } else {
                                            val mod = viewModel.modifiedSettingsMap[entry.key]
                                            if (mod?.isDeleted == true) {
                                                viewModel.operationMessage.value = "Cannot edit a deleted item, restore it first"
                                            } else {
                                                editKey = entry.key
                                                editValue = entry.value
                                                showEditSheet = true
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        HapticUtil.performLongPress(view)
                                        deleteKey = entry.key
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
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                
                                val isDeletedItem = currentTab == ExtendedTab.MODIFIED && 
                                                    viewModel.modifiedSettingsMap[entry.key]?.isDeleted == true
                                val isAddedItem = currentTab == ExtendedTab.MODIFIED && 
                                                    viewModel.modifiedSettingsMap[entry.key]?.stockValue == null

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.key,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isDeletedItem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (isDeletedItem) "Deleted" else entry.value.ifEmpty { stringResource(R.string.empty_value) },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isDeletedItem) MaterialTheme.colorScheme.error.copy(alpha=0.7f) else if (isAddedItem) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                if (currentTab == ExtendedTab.MODIFIED) {
                                    Icon(
                                        imageVector = Icons.Rounded.Restore,
                                        contentDescription = "Restore",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = stringResource(R.string.cd_edit),
                                        modifier = Modifier.size(18.dp),
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

    // Edit Setting Sheet
    if (showEditSheet) {
        EditSettingSheet(
            key = editKey,
            currentValue = editValue,
            onDismiss = { showEditSheet = false },
            onSave = { newValue ->
                viewModel.putSetting(editKey, newValue)
                showEditSheet = false
            }
        )
    }

    // Add Setting Sheet
    if (showAddSheet) {
        AddSettingSheet(
            onDismiss = { showAddSheet = false },
            onAdd = { key, value ->
                viewModel.addSetting(key, value)
                showAddSheet = false
            }
        )
    }

    // Delete confirmation
    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            key = deleteKey,
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                viewModel.deleteSetting(deleteKey)
                showDeleteConfirm = false
            }
        )
    }

    // Restore confirmation
    if (showRestoreConfirm) {
        RestoreConfirmDialog(
            key = deleteKey,
            onDismiss = { showRestoreConfirm = false },
            onConfirm = {
                viewModel.restoreSetting(deleteKey)
                showRestoreConfirm = false
            }
        )
    }
}

@Composable
fun RestoreConfirmDialog(
    key: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val view = LocalView.current

    androidx.compose.material3.AlertDialog(
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
                text = "Are you sure you want to restore '$key' to its original state or completely delete it if newly added?",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                androidx.compose.material3.TextButton(
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
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Restore")
                }
            }
        }
    )
}
