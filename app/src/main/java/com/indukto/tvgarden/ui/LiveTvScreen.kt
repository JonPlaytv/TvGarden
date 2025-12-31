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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
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
    val countries by viewModel.countries.collectAsState()
    val selectedChannel by viewModel.selectedChannel.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val selectedCountry by viewModel.selectedCountry.collectAsState()
    
    // PRELOAD LOGIC: Find next and previous channels
    val nextChannel = remember(channels, selectedChannel) {
        if (channels.isEmpty() || selectedChannel == null) null
        else {
            val index = channels.indexOf(selectedChannel)
            if (index == -1) null 
            else if (index == channels.size - 1) channels.first()
            else channels[index + 1]
        }
    }
    
    val prevChannel = remember(channels, selectedChannel) {
        if (channels.isEmpty() || selectedChannel == null) null
        else {
            val index = channels.indexOf(selectedChannel)
            if (index == -1) null
            else if (index == 0) channels.last()
            else channels[index - 1]
        }
    }
    
    val subtitlesEnabled by viewModel.subtitlesEnabled.collectAsState()
    val autoSkipBroken by viewModel.autoSkipBroken.collectAsState()

    // UI States
    var isChannelListVisible by remember { mutableStateOf(false) }
    var isCategoryListVisible by remember { mutableStateOf(false) }
    var isCountryListVisible by remember { mutableStateOf(false) }
    var isSettingsVisible by remember { mutableStateOf(false) }
    var showChannelInfo by remember { mutableStateOf(false) }
    var lastSwitchTime by remember { mutableStateOf(0L) }

    val playerFocusRequester = remember { FocusRequester() }
    val channelListFocusRequester = remember { FocusRequester() }
    val categoryListFocusRequester = remember { FocusRequester() }
    val countryListFocusRequester = remember { FocusRequester() }
    val settingsFocusRequester = remember { FocusRequester() }
    
    val channelListState = rememberLazyListState()
    val categoryListState = rememberLazyListState()
    val countryListState = rememberLazyListState()

    // Show channel info briefly when channel changes (and sidebar is not open)
    LaunchedEffect(selectedChannel) {
        if (selectedChannel != null) {
            lastSwitchTime = System.currentTimeMillis()
            if (!isChannelListVisible) {
                showChannelInfo = true
                delay(3000) // Show for 3 seconds
                showChannelInfo = false
            }
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
                           
                           // Open settings on LEFT or MENU key
                           if (it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                               it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MENU) {
                               // ONLY open if NO other sidebars are visible
                               if (!isChannelListVisible && !isCategoryListVisible && !isCountryListVisible) {
                                   isSettingsVisible = true
                                   return@onKeyEvent true
                               }
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
                    nextChannel = nextChannel,
                    prevChannel = prevChannel,
                    subtitlesEnabled = subtitlesEnabled,
                    isZapping = (System.currentTimeMillis() - lastSwitchTime < 2000), // Consider fast if < 2s
                    onBroken = { brokenChannel ->
                        if (autoSkipBroken) {
                            viewModel.markChannelAsBroken(brokenChannel)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
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
                                .height(48.dp)
                                .aspectRatio(16f / 9f)
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = channel.category.replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                                if (subtitlesEnabled) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "CC",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Black,
                                        modifier = Modifier
                                            .background(Color.White, MaterialTheme.shapes.extraSmall)
                                            .padding(horizontal = 4.dp)
                                    )
                                }
                            }
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
                    modifier = Modifier.width(300.dp)
                ) {
                    LazyColumn(
                        state = channelListState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(channelListFocusRequester)
                            .onPreviewKeyEvent {
                                if (it.type == KeyEventType.KeyDown) {
                                    if (it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || 
                                        it.key == Key.Back) {
                                        isChannelListVisible = false
                                        return@onPreviewKeyEvent true
                                    }
                                    // Open Categories on RIGHT
                                    if (it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                                        isCategoryListVisible = true
                                        return@onPreviewKeyEvent true
                                    }
                                }
                                false
                            }
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
                    } else {
                        // When channel list closes, return focus to video player
                        delay(100)
                        try {
                            playerFocusRequester.requestFocus()
                        } catch(e: Exception) {}
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
                    isSecondary = true,
                    modifier = Modifier.width(200.dp)
                ) {
                    LazyColumn(
                        state = categoryListState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(categoryListFocusRequester)
                            .onPreviewKeyEvent {
                                if (it.type == KeyEventType.KeyDown) {
                                    // Close Category List on LEFT or BACK - return to channel list only
                                    if (it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || 
                                        it.key == Key.Back) {
                                        isCategoryListVisible = false
                                        // Focus will be returned by LaunchedEffect
                                        return@onPreviewKeyEvent true
                                    }
                                    // Open Countries on RIGHT
                                    if (it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                                        isCountryListVisible = true
                                        return@onPreviewKeyEvent true
                                    }
                                }
                                false
                            }
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
                            if (!isCountryListVisible) {
                                categoryListFocusRequester.requestFocus()
                                val index = categories.indexOf(selectedCategory).coerceAtLeast(0)
                                categoryListState.scrollToItem(index)
                            }
                        } catch(e: Exception) {}
                    } else {
                        // When category list closes, return focus to channel list
                        delay(50)
                        try {
                            channelListFocusRequester.requestFocus()
                        } catch(e: Exception) {}
                    }
                }
            }

            // COUNTRY LIST SIDEBAR
            AnimatedVisibility(
                visible = isCountryListVisible,
                enter = slideInHorizontally { it },
                exit = slideOutHorizontally { it }
            ) {
                 SidebarContainer(
                    title = "Country",
                    isSecondary = true,
                    modifier = Modifier.width(200.dp)
                ) {
                    LazyColumn(
                        state = countryListState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(countryListFocusRequester)
                            .onPreviewKeyEvent {
                                if (it.type == KeyEventType.KeyDown) {
                                    // Close Country List on LEFT or BACK - return to category list
                                    if (it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || 
                                        it.key == Key.Back) {
                                        isCountryListVisible = false
                                        // Focus will be returned by LaunchedEffect
                                        return@onPreviewKeyEvent true
                                    }
                                }
                                false
                            }
                    ) {
                        items(countries) { (code, name) ->
                            CategoryListItem(
                                name = name,
                                isSelected = code == selectedCountry,
                                onClick = {
                                    viewModel.selectCountry(code)
                                    // Close all sidebars after country selection
                                    isCountryListVisible = false
                                    isCategoryListVisible = false
                                    isChannelListVisible = false
                                    playerFocusRequester.requestFocus()
                                }
                            )
                        }
                    }
                }
                
                // Focus Logic for Country List
                LaunchedEffect(isCountryListVisible) {
                    if (isCountryListVisible) {
                        delay(100)
                        try {
                            countryListFocusRequester.requestFocus()
                            val index = countries.indexOfFirst { it.first == selectedCountry }.coerceAtLeast(0)
                            countryListState.scrollToItem(index)
                        } catch(e: Exception) {}
                    } else {
                        // When country list closes, return focus to category list
                        delay(50)
                        try {
                            categoryListFocusRequester.requestFocus()
                        } catch(e: Exception) {}
                    }
                }
            }

            // SETTINGS SIDEBAR
            AnimatedVisibility(
                visible = isSettingsVisible,
                enter = slideInHorizontally { -it }, // Comes from the left
                exit = slideOutHorizontally { -it }
            ) {
                 SidebarContainer(
                    title = "Settings",
                    isSecondary = true,
                    modifier = Modifier.width(280.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(settingsFocusRequester)
                            .onPreviewKeyEvent {
                                if (it.type == KeyEventType.KeyDown) {
                                    if (it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || 
                                        it.key == Key.Back) {
                                        isSettingsVisible = false
                                        return@onPreviewKeyEvent true
                                    }
                                }
                                false
                            }
                    ) {
                        SettingsToggleItem(
                            label = "Subtitles",
                            isEnabled = subtitlesEnabled,
                            onToggle = { viewModel.toggleSubtitles() }
                        )
                        SettingsToggleItem(
                            label = "Auto-Skip Broken",
                            isEnabled = autoSkipBroken,
                            onToggle = { viewModel.toggleAutoSkip() }
                        )
                        
                        Spacer(modifier = Modifier.weight(1f))
                        
                        Text(
                            text = "Garden TV v1.2",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                
                // Focus Logic for Settings List
                LaunchedEffect(isSettingsVisible) {
                    if (isSettingsVisible) {
                        delay(100)
                        try {
                            settingsFocusRequester.requestFocus()
                        } catch(e: Exception) {}
                    } else {
                        delay(100)
                        try {
                            playerFocusRequester.requestFocus()
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
    isSecondary: Boolean = false,
    content: @Composable () -> Unit
) {
    val backgroundColor = if (isSecondary) {
        // Solid color for secondary (category) sidebar - no gradient clash
        Color.Black.copy(alpha = 0.92f)
    } else {
        // Keep null for primary to use gradient
        null
    }
    
    Box(
        modifier = modifier
            .fillMaxHeight()
            .then(
                if (backgroundColor != null) {
                    Modifier.background(backgroundColor)
                } else {
                    Modifier.background(
                        Brush.horizontalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Black.copy(alpha = 0.92f))
                        )
                    )
                }
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
    nextChannel: Channel? = null,
    prevChannel: Channel? = null,
    subtitlesEnabled: Boolean = true,
    isZapping: Boolean = false,
    onBroken: (Channel) -> Unit = {},
    modifier: Modifier = Modifier,
    onStateChanged: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    var isBuffering by remember { mutableStateOf(false) }
    
    val exoPlayer = remember {
        // Optimized for Live Streams (IPTV)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                30000, // minBufferMs: 30s
                60000, // maxBufferMs: 60s
                2500,  // bufferForPlaybackMs: 2.5s
                5000   // bufferForPlaybackAfterRebufferMs: 5s
            )
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true // Handle audio focus automatically
            )
            .build().apply {
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        isBuffering = (playbackState == Player.STATE_BUFFERING)
                        onStateChanged(playbackState)
                    }
                    
                    override fun onPlayerError(error: PlaybackException) {
                        // Attempt to recover from errors by re-preparing
                        prepare()
                        play()
                    }
                })
            }
    }

    // Health Check Logic: If buffering for > 5 seconds and not zapping, mark as broken
    LaunchedEffect(isBuffering, channel, isZapping) {
        if (isBuffering && channel != null && !isZapping) {
            delay(5000) // Wait for 5 seconds
            if (isBuffering) {
                // Still buffering after 5s and not fast switching
                onBroken(channel)
            }
        }
    }

    // Subtitles Toggle
    LaunchedEffect(subtitlesEnabled) {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesEnabled)
            .build()
    }

    // Helper to create MediaItem for Live Stream
    fun createMediaItem(c: Channel): MediaItem {
        return MediaItem.Builder()
            .setUri(c.streamUrl)
            .setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(20000)
                    .setMinOffsetMs(10000)
                    .setMaxOffsetMs(40000)
                    .build()
            )
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()
    }

    // Update media items when channel changes
    LaunchedEffect(channel) {
        if (channel != null) {
            val items = mutableListOf<MediaItem>()
            items.add(createMediaItem(channel)) // Current (Index 0)
            
            // Add next/prev to playlist for preloading
            nextChannel?.let { items.add(createMediaItem(it)) }
            prevChannel?.let { items.add(createMediaItem(it)) }
            
            exoPlayer.setMediaItems(items)
            exoPlayer.prepare()
        } else {
            exoPlayer.stop()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    exoPlayer.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    exoPlayer.play()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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
                    .height(40.dp)
                    .aspectRatio(16f / 9f)
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
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsToggleItem(
    label: String,
    isEnabled: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        onClick = onToggle,
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.05f),
            contentColor = Color.White,
            focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
            focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface
        ),
        shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.small),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 24.dp)
                    .background(
                        if (isEnabled) MaterialTheme.colorScheme.primary 
                        else Color.White.copy(alpha = 0.2f),
                        MaterialTheme.shapes.extraLarge
                    )
                    .padding(horizontal = 4.dp),
                contentAlignment = if (isEnabled) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(Color.White, MaterialTheme.shapes.extraLarge)
                )
            }
        }
    }
}
