/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.ui.update

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.proify.lyricon.app.BuildConfig
import io.github.proify.lyricon.app.R
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 居中的「无更新 / 检查中 / 检查失败」视图。
 */
@Composable
internal fun NoUpdateView(
    state: UpdateUiState,
    isDark: Boolean,
    showCurrentLog: Boolean,
    onToggleCurrentLog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.app_name),
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(10.dp))

        when (state) {
            UpdateUiState.Loading -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "CheckingRotation")
                    val rotation by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "Rotation"
                    )

                    Icon(
                        imageVector = MiuixIcons.Regular.Refresh,
                        contentDescription = null,
                        modifier = Modifier
                            .size(15.dp)
                            .graphicsLayer { rotationZ = rotation },
                        tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )

                    Text(
                        text = "v${BuildConfig.VERSION_NAME}  " + stringResource(R.string.update_checking),
                        fontSize = 15.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.60f)
                    )
                }
            }

            is UpdateUiState.Ready -> {
                Text(
                    text = "v${BuildConfig.VERSION_NAME}  " + stringResource(R.string.update_already_latest),
                    fontSize = 15.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.60f)
                )

                if (state.release.body.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    ChangelogLink(
                        text = stringResource(
                            if (showCurrentLog) R.string.update_collapse_changelog
                            else R.string.update_current_version_log
                        ),
                        isDark = isDark,
                        fontSize = 13.5.sp,
                        onClick = onToggleCurrentLog,
                    )
                }
            }

            is UpdateUiState.Error -> {
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    fontSize = 15.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.60f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.update_check_failed),
                    fontSize = 14.sp,
                    color = Color(0xFFE53935),
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = state.message,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.50f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        }
    }
}

/** 展开 / 收起更新日志的文字链，带箭头。 */
@Composable
internal fun ChangelogLink(
    text: String,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val linkColor = if (isDark) Color(0xFF6B9BFF) else Color(0xFF2655FF)
        Text(
            text = text,
            fontSize = fontSize,
            color = linkColor,
            fontWeight = FontWeight.Medium
        )
        Icon(
            imageVector = MiuixIcons.Basic.ArrowRight,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = linkColor
        )
    }
}

/** 发现新版本时的顶部头卡。 */
@Composable
internal fun NewUpdateHeaderCard(
    release: GithubRelease,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    UpdateCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.app_name),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.app_name) + " " + release.tagName,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val primaryColor = if (isDark) Color(0xFF2050FF) else Color(0xFF2655FF)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(primaryColor.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = stringResource(R.string.update_new_version_found),
                        fontSize = 11.5.sp,
                        color = if (isDark) Color(0xFF7A9DFF) else primaryColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                val archLabel = release.matchedAsset?.let { detectArchLabel(it.name) }
                if (archLabel != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = archLabel,
                            fontSize = 11.5.sp,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.70f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (release.publishedAt.isNotBlank()) {
                    Text(
                        text = release.publishedAt,
                        fontSize = 12.5.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                }
            }
        }
    }
}

/** 更新日志卡片。 */
@Composable
internal fun ChangelogCard(
    title: String,
    changelog: String,
    modifier: Modifier = Modifier,
) {
    UpdateCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = MiuixIcons.Basic.Check,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MiuixTheme.colorScheme.primary
                    )
                }

                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            ReleaseMarkdown(
                markdown = changelog.ifBlank { stringResource(R.string.update_empty_changelog) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun UpdateCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    Card(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        colors = CardDefaults.defaultColors(
            color = if (isDark) MiuixTheme.colorScheme.surfaceContainer else Color.White
        ),
        content = { content() },
    )
}

/**
 * 底部常驻动作按钮。下载中时按钮本身就是进度条：
 * 蓝色填充随进度推进，文案在填充过半时转白以保持对比度。
 */
@Composable
internal fun BottomActionButton(
    hasUpdate: Boolean,
    isChecking: Boolean,
    isDark: Boolean,
    downloadState: UpdateDownloadState,
    onButtonClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val buttonHeight = 50.dp
    val pillShape = CircleShape
    val accentBlue = if (isDark) Color(0xFF2050FF) else Color(0xFF2655FF)

    when (downloadState) {
        is UpdateDownloadState.Downloading -> {
            val trackColor = if (isDark) Color(0xFF19284D) else Color(0xFFE4EDFF)
            val animatedProgress by animateFloatAsState(
                targetValue = downloadState.progress.coerceIn(0.01f, 1f),
                animationSpec = tween(durationMillis = 200, easing = LinearEasing),
                label = "DownloadProgress"
            )
            val pct = (animatedProgress * 100).toInt().coerceIn(0, 100)
            val speedText = if (downloadState.speedBytesPerSec > 0) {
                UpdateDownloadManager.formatSpeed(downloadState.speedBytesPerSec)
            } else ""
            val displayText = if (speedText.isNotBlank()) "$pct% · $speedText" else "$pct%"

            val textColor by animateColorAsState(
                targetValue = if (animatedProgress >= 0.45f) Color.White
                else if (isDark) Color(0xFF7A9DFF) else Color(0xFF2655FF),
                animationSpec = tween(200),
                label = "DownloadTextColor"
            )

            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .height(buttonHeight)
                    .clip(pillShape)
                    .background(trackColor),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress)
                        .background(accentBlue)
                )
                Text(
                    text = displayText,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor
                )
            }
        }

        is UpdateDownloadState.Completed -> ActionPill(
            modifier = modifier,
            height = buttonHeight,
            text = stringResource(R.string.update_install),
            background = accentBlue,
            contentColor = Color.White,
            fontSize = 16.sp,
            onClick = onButtonClick,
        )

        is UpdateDownloadState.Failed -> ActionPill(
            modifier = modifier,
            height = buttonHeight,
            text = stringResource(R.string.update_download_retry),
            background = accentBlue,
            contentColor = Color.White,
            fontSize = 15.sp,
            onClick = onButtonClick,
        )

        UpdateDownloadState.Idle -> {
            val background = if (hasUpdate) accentBlue else {
                if (isDark) Color(0xFF2A2A2F) else Color(0xFFEBEBF0)
            }
            val contentColor = when {
                hasUpdate -> Color.White
                isChecking -> MiuixTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                else -> MiuixTheme.colorScheme.onSurface
            }
            ActionPill(
                modifier = modifier,
                height = buttonHeight,
                text = when {
                    hasUpdate -> stringResource(R.string.update_download)
                    isChecking -> stringResource(R.string.update_checking)
                    else -> stringResource(R.string.update_check_button)
                },
                background = background,
                contentColor = contentColor,
                fontSize = 16.sp,
                enabled = !isChecking,
                onClick = onButtonClick,
            )
        }
    }
}

@Composable
private fun ActionPill(
    height: androidx.compose.ui.unit.Dp,
    text: String,
    background: Color,
    contentColor: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(background)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
            color = contentColor
        )
    }
}
