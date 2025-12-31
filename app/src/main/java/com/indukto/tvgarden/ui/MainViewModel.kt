package com.indukto.tvgarden.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.indukto.tvgarden.data.Channel
import com.indukto.tvgarden.data.IptvRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    private val repository = IptvRepository()

    private val _allChannels = MutableStateFlow<List<Channel>>(emptyList())
    
    // Derived state for filtered channels would normally be better, but lets keep it simple for now
    // We will just expose the filtered list as 'channels' to not break existing code much
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
                // Fetching a few categories
                val news = repository.getChannels("news")
                val movies = repository.getChannels("movies")
                val music = repository.getChannels("music")
                val kids = repository.getChannels("kids")
                
                val combined = (news + movies + music + kids).shuffled()
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
