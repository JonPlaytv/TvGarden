package com.indukto.tvgarden.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class IptvRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // Country codes for popular countries
    companion object {
        val COUNTRIES = listOf(
            "all" to "All Countries",
            "us" to "United States",
            "gb" to "United Kingdom",
            "de" to "Germany",
            "fr" to "France",
            "es" to "Spain",
            "it" to "Italy",
            "nl" to "Netherlands",
            "br" to "Brazil",
            "mx" to "Mexico",
            "ar" to "Argentina",
            "in" to "India",
            "jp" to "Japan",
            "kr" to "South Korea",
            "cn" to "China",
            "ru" to "Russia",
            "au" to "Australia",
            "ca" to "Canada"
        )
    }

    suspend fun getChannelsByCountry(countryCode: String): List<Channel> = withContext(Dispatchers.IO) {
        if (countryCode == "all") {
            // Fallback to category-based loading for "all"
            return@withContext emptyList()
        }
        
        val url = "https://iptv-org.github.io/iptv/countries/${countryCode}.m3u"
        fetchAndParse(url, countryCode)
    }

    suspend fun getChannels(category: String): List<Channel> = withContext(Dispatchers.IO) {
        val url = "https://iptv-org.github.io/iptv/categories/${category}.m3u"
        fetchAndParse(url, "all", category)
    }

    private fun fetchAndParse(url: String, country: String, category: String = "general"): List<Channel> {
        return try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) return emptyList()
            
            val inputStream = response.body?.byteStream() ?: return emptyList()
            parseM3u(inputStream, category, country)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun parseM3u(inputStream: InputStream, category: String, country: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        val reader = BufferedReader(InputStreamReader(inputStream))
        var line: String? = reader.readLine()
        
        var currentLogo: String? = null
        var currentName: String? = null
        var currentCategory: String = category
        
        while (line != null) {
            line = line.trim()
            if (line.startsWith("#EXTINF:")) {
                // Parse metadata
                // Example: #EXTINF:-1 tvg-id="" tvg-logo="http://logo.png" group-title="News",CNN
                val info = line.substringAfter("#EXTINF:")
                val dictionary = parseAttributes(info)
                currentLogo = dictionary["tvg-logo"]
                val groupTitle = dictionary["group-title"] ?: category
                // Split multi-categories (e.g. "Movies;News") and take the first one for simplicity
                currentCategory = groupTitle.split(";").firstOrNull() ?: category
                
                // Name is usually after the last comma
                currentName = info.substringAfterLast(",").trim()
            } else if (line.isNotEmpty() && !line.startsWith("#")) {
                // This is the URL
                if (!currentName.isNullOrEmpty()) {
                    channels.add(
                        Channel(
                            name = currentName,
                            logoUrl = currentLogo,
                            streamUrl = line,
                            category = currentCategory.trim().lowercase(),
                            country = country
                        )
                    )
                }
                currentName = null
                currentLogo = null
                currentCategory = category
            }
            line = reader.readLine()
        }
        return channels
    }

    private fun parseAttributes(line: String): Map<String, String> {
        val attributes = mutableMapOf<String, String>()
        // distinct attribute pattern: key="value"
        val regex = Regex("([a-zA-Z0-9-]+)=\"([^\"]*)\"")
        regex.findAll(line).forEach { matchResult ->
            attributes[matchResult.groupValues[1]] = matchResult.groupValues[2]
        }
        return attributes
    }
}
