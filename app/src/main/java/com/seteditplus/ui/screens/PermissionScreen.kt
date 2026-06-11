package com.seteditplus.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.seteditplus.R
import com.seteditplus.data.PermissionMode
import com.seteditplus.util.HapticUtil
import com.seteditplus.viewmodel.SettingsViewModel
import kotlinx.coroutines.delay

/**
 * Startup screen where the user chooses Root or Shizuku as their permission provider.
 * Material 3 Expressive styled with animated entry, haptic feedback, and shape-shifting buttons.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PermissionScreen(
    viewModel: SettingsViewModel,
    onPermissionGranted: () -> Unit
) {
    val view = LocalView.current
    val isLoading by viewModel.isLoading
    val error by viewModel.permissionError
    val isGranted by viewModel.isPermissionGranted
    val snackbarHostState = remember { SnackbarHostState() }

    // Animate entrance
    var showContent by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(200)
        showContent = true
    }

    // Navigate when permission is granted
    LaunchedEffect(isGranted) {
        if (isGranted) {
            delay(300)
            onPermissionGranted()
        }
    }

    // Show error snackbar
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.permissionError.value = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(tween(500)) + slideInVertically(
                    initialOffsetY = { -60 },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.title_permission),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.subtitle_permission),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(64.dp))

            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(tween(600, delayMillis = 200))
            ) {
                Text(
                    text = stringResource(R.string.select_access_method),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }

            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(tween(500, delayMillis = 350)) + slideInVertically(
                    initialOffsetY = { 100 },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(32.dp)
                        )
                        .padding(4.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        PermissionButton(
                            title = stringResource(R.string.btn_root),
                            subtitle = stringResource(R.string.btn_root_desc),
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.AdminPanelSettings,
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp)
                                )
                            },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            isLoading = isLoading && viewModel.permissionMode.value == PermissionMode.ROOT,
                            isTop = true,
                            isBottom = false,
                            onClick = {
                                HapticUtil.performClick(view)
                                viewModel.selectPermissionMode(PermissionMode.ROOT)
                            }
                        )

                        PermissionButton(
                            title = stringResource(R.string.btn_shizuku),
                            subtitle = stringResource(R.string.btn_shizuku_desc),
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.Terminal,
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp)
                                )
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            isLoading = isLoading && viewModel.permissionMode.value == PermissionMode.SHIZUKU,
                            isTop = false,
                            isBottom = true,
                            onClick = {
                                HapticUtil.performClick(view)
                                viewModel.selectPermissionMode(PermissionMode.SHIZUKU)
                            }
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun PermissionButton(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    isLoading: Boolean,
    isTop: Boolean,
    isBottom: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isLoading || isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "buttonScale"
    )

    val cornerRadius = 28.dp
    val groupedRadius = 4.dp
    val pressedRadius = 8.dp

    val defaultTopStart = if (isTop) cornerRadius else groupedRadius
    val defaultTopEnd = if (isTop) cornerRadius else groupedRadius
    val defaultBottomStart = if (isBottom) cornerRadius else groupedRadius
    val defaultBottomEnd = if (isBottom) cornerRadius else groupedRadius

    val topStart by animateDpAsState(if (isPressed) pressedRadius else defaultTopStart, label = "ts")
    val topEnd by animateDpAsState(if (isPressed) pressedRadius else defaultTopEnd, label = "te")
    val bottomStart by animateDpAsState(if (isPressed) pressedRadius else defaultBottomStart, label = "bs")
    val bottomEnd by animateDpAsState(if (isPressed) pressedRadius else defaultBottomEnd, label = "be")

    Button(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .scale(scale),
        shape = RoundedCornerShape(
            topStart = topStart,
            topEnd = topEnd,
            bottomStart = bottomStart,
            bottomEnd = bottomEnd
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        enabled = !isLoading
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            icon()
            Spacer(modifier = Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.8f)
                )
            }
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = contentColor
                )
            }
        }
    }
}
