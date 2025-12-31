package com.indukto.tvgarden.ui

import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.indukto.tvgarden.data.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material3.CircularProgressIndicator

@OptIn(UnstableApi::class, ExperimentalTvMaterial3Api::class)
@Composable
fun LiveTvScreen() {
    val viewModel: MainViewModel = viewModel()
    val channels by viewModel.channels.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val selectedChannel by viewModel.selectedChannel.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    
    // UI States
    var isChannelListVisible by remember { mutableStateOf(false) }
    var isCategoryListVisible by remember { mutableStateOf(false) }
    var showChannelInfo by remember { mutableStateOf(false) }

    val playerFocusRequester = remember { FocusRequester() }
    val channelListFocusRequester = remember { FocusRequester() }
    val categoryListFocusRequester = remember { FocusRequester() }
    
    val channelListState = rememberLazyListState()
    val categoryListState = rememberLazyListState()

    // Show channel info briefly when channel changes (and sidebar is not open)
    LaunchedEffect(selectedChannel) {
        if (selectedChannel != null && !isChannelListVisible) {
            showChannelInfo = true
            delay(3000) // Show for 3 seconds
            showChannelInfo = false
        }
    }

    // Initial focus to player
    LaunchedEffect(Unit) {
        delay(500) // Wait for composition
        if (!isChannelListVisible) {
            playerFocusRequester.requestFocus()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (channels.isEmpty() && selectedChannel == null) {
             Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                 CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                 Text("Loading Channels...", modifier = Modifier.padding(top = 64.dp))
             }
        } else {
            // LAYER 1: Video Player (Always Background)
            // Wrap in a focusable Box because AndroidView doesn't propagate Compose focus properly
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(playerFocusRequester)
                    .focusable()
                    .onKeyEvent {
                       if (it.type == KeyEventType.KeyUp) {
                           // Open sidebar on RIGHT or CENTER/ENTER
                           if (it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                               it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                               it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER) {
                               isChannelListVisible = true
                               return@onKeyEvent true
                           }
                           // Channel UP (next channel)
                           if (it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                               it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_CHANNEL_UP ||
                               it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_PAGE_UP) {
                               viewModel.previousChannel()
                               return@onKeyEvent true
                           }
                           // Channel DOWN (previous channel)
                           if (it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                               it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN ||
                               it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_PAGE_DOWN) {
                               viewModel.nextChannel()
                               return@onKeyEvent true
                           }
                       }
                       false
                    }
            ) {
                VideoPlayer(
                    channel = selectedChannel,
                    modifier = Modifier.fillMaxSize(),
                    onStateChanged = { /* Handle buffering if needed here */ }
                )
            }
        }

        // LAYER 2: Channel Info Overlay (shows briefly when switching channels)
        AnimatedVisibility(
            visible = showChannelInfo && !isChannelListVisible && selectedChannel != null,
            enter = slideInHorizontally { -it } + androidx.compose.animation.fadeIn(),
            exit = slideOutHorizontally { -it } + androidx.compose.animation.fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(24.dp)
        ) {
            selectedChannel?.let { channel ->
                Box(
                    modifier = Modifier
                        .width(320.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.85f),
                            shape = MaterialTheme.shapes.medium
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(channel.logoUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.White.copy(alpha = 0.1f), MaterialTheme.shapes.small)
                                .padding(4.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = channel.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Text(
                                text = channel.category.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }

        // LAYER 2: Sidebars Container
        // We align the Row to the End (Right) side
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.End
        ) {
            
            // CHANNEL LIST SIDEBAR
            AnimatedVisibility(
                visible = isChannelListVisible,
                enter = slideInHorizontally { it },
                exit = slideOutHorizontally { it }
            ) {
                SidebarContainer(
                    title = selectedCategory,
                    modifier = Modifier
                        .width(300.dp)
                        .onKeyEvent {
                            // Close Channel List on LEFT or BACK
                            if (it.type == KeyEventType.KeyUp) {
                                if (it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || 
                                    it.key == Key.Back) {
                                    isChannelListVisible = false
                                    isCategoryListVisible = false
                                    playerFocusRequester.requestFocus()
                                    return@onKeyEvent true
                                }
                                // Open Categories on RIGHT
                                if (it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                                    isCategoryListVisible = true
                                    return@onKeyEvent true
                                }
                            }
                            false
                        }
                ) {
                    LazyColumn(
                        state = channelListState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(channelListFocusRequester)
                    ) {
                        items(channels) { channel ->
                            ChannelListItem(
                                channel = channel,
                                isSelected = channel == selectedChannel,
                                onClick = { 
                                    viewModel.selectChannel(channel)
                                    // Keep list open
                                }
                            )
                        }
                    }
                }
                
                // Focus Logic for Channel List
                LaunchedEffect(isChannelListVisible) {
                    if (isChannelListVisible) {
                         delay(100)
                         try {
                             if (!isCategoryListVisible) {
                                 channelListFocusRequester.requestFocus()
                                 val index = channels.indexOf(selectedChannel).coerceAtLeast(0)
                                 channelListState.scrollToItem(index)
                             }
                         } catch (e: Exception) {}
                    }
                }
            }

            // CATEGORY LIST SIDEBAR
            AnimatedVisibility(
                visible = isCategoryListVisible,
                enter = slideInHorizontally { it },
                exit = slideOutHorizontally { it }
            ) {
                 SidebarContainer(
                    title = "Categories",
                    modifier = Modifier
                        .width(200.dp)
                        .onKeyEvent {
                             // Close Category List on LEFT or BACK
                            if (it.type == KeyEventType.KeyUp) {
                                if (it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || 
                                    it.key == Key.Back) {
                                    isCategoryListVisible = false
                                    channelListFocusRequester.requestFocus()
                                    return@onKeyEvent true
                                }
                            }
                            false
                        }
                ) {
                    LazyColumn(
                        state = categoryListState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(categoryListFocusRequester)
                    ) {
                        items(categories) { category ->
                            CategoryListItem(
                                name = category,
                                isSelected = category == selectedCategory,
                                onClick = {
                                    viewModel.selectCategory(category)
                                }
                            )
                        }
                    }
                }
                
                // Focus Logic for Category List
                LaunchedEffect(isCategoryListVisible) {
                    if (isCategoryListVisible) {
                        delay(100)
                        try {
                            categoryListFocusRequester.requestFocus()
                            val index = categories.indexOf(selectedCategory).coerceAtLeast(0)
                            categoryListState.scrollToItem(index)
                        } catch(e: Exception) {}
                    }
                }
            }
        }
    }
}

@Composable
fun SidebarContainer(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(
                Brush.horizontalGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.8f), Color.Black.copy(alpha = 0.95f))
                )
            )
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            content()
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    channel: Channel?,
    modifier: Modifier = Modifier,
    onStateChanged: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    var isBuffering by remember { mutableStateOf(false) }
    
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_ALL
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    isBuffering = (playbackState == Player.STATE_BUFFERING)
                    onStateChanged(playbackState)
                }
            })
        }
    }

    // Update media item when channel changes
    LaunchedEffect(channel) {
        if (channel != null) {
            val mediaItem = MediaItem.fromUri(channel.streamUrl)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
        } else {
            exoPlayer.stop()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false // We build our own UI
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChannelListItem(
    channel: Channel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else Color.White,
            focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
            focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface
        ),
        shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.small),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            // Logo
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(channel.logoUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.White.copy(alpha = 0.1f), MaterialTheme.shapes.small)
                    .padding(4.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CategoryListItem(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else Color.White,
            focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
            focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface
        ),
        shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.small),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = name.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(12.dp)
        )
    }
}
