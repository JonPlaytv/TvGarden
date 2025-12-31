package com.indukto.tvgarden.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.indukto.tvgarden.data.Channel
import com.indukto.tvgarden.data.IptvRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    private val repository = IptvRepository()

    private val _allChannels = MutableStateFlow<List<Channel>>(emptyList())
    
    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()
    
    private val _categories = MutableStateFlow<List<String>>(emptyList())
    val categories: StateFlow<List<String>> = _categories.asStateFlow()
    
    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _selectedChannel = MutableStateFlow<Channel?>(null)
    val selectedChannel: StateFlow<Channel?> = _selectedChannel.asStateFlow()

    fun selectChannel(channel: Channel?) {
        _selectedChannel.value = channel
    }

    fun nextChannel() {
        val currentList = _channels.value
        val current = _selectedChannel.value
        if (currentList.isEmpty()) return
        
        val currentIndex = currentList.indexOf(current)
        val nextIndex = if (currentIndex < 0 || currentIndex >= currentList.size - 1) 0 else currentIndex + 1
        _selectedChannel.value = currentList[nextIndex]
    }

    fun previousChannel() {
        val currentList = _channels.value
        val current = _selectedChannel.value
        if (currentList.isEmpty()) return
        
        val currentIndex = currentList.indexOf(current)
        val prevIndex = if (currentIndex <= 0) currentList.size - 1 else currentIndex - 1
        _selectedChannel.value = currentList[prevIndex]
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
        filterChannels()
    }

    private fun filterChannels() {
        val cat = _selectedCategory.value
        if (cat == "All") {
            _channels.value = _allChannels.value
        } else {
            _channels.value = _allChannels.value.filter { it.category == cat }
        }
    }

    init {
        loadChannels()
    }

    private fun loadChannels() {
        viewModelScope.launch {
            _loading.value = true
            try {
                // Fetch all categories in PARALLEL for faster loading
                val deferredNews = async { repository.getChannels("news") }
                val deferredMovies = async { repository.getChannels("movies") }
                val deferredMusic = async { repository.getChannels("music") }
                val deferredKids = async { repository.getChannels("kids") }
                val deferredEntertainment = async { repository.getChannels("entertainment") }
                val deferredSports = async { repository.getChannels("sports") }
                
                // Await all in parallel
                val results = awaitAll(deferredNews, deferredMovies, deferredMusic, deferredKids, deferredEntertainment, deferredSports)
                
                val combined = results.flatten().shuffled()
                _allChannels.value = combined
                _channels.value = combined
                
                // Extract categories
                val cats = mutableListOf("All")
                cats.addAll(combined.map { it.category }.distinct().sorted())
                _categories.value = cats
                
                if (combined.isNotEmpty()) {
                     _selectedChannel.value = combined.first()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _loading.value = false
            }
        }
    }
}

