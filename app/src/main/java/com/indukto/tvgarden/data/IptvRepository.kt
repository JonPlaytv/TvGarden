package com.indukto.tvgarden.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

class IptvRepository {

    private val client = OkHttpClient()

    suspend fun getChannels(category: String): List<Channel> = withContext(Dispatchers.IO) {
        val url = "https://iptv-org.github.io/iptv/categories/${category}.m3u"
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) return@withContext emptyList()
        
        val inputStream = response.body?.byteStream() ?: return@withContext emptyList()
        parseM3u(inputStream, category)
    }

    private fun parseM3u(inputStream: InputStream, category: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        val reader = BufferedReader(InputStreamReader(inputStream))
        var line: String? = reader.readLine()
        
        var currentLogo: String? = null
        var currentName: String? = null
        
        while (line != null) {
            line = line.trim()
            if (line.startsWith("#EXTINF:")) {
                // Parse metadata
                // Example: #EXTINF:-1 tvg-id="" tvg-logo="http://logo.png" group-title="News",CNN
                val info = line.substringAfter("#EXTINF:")
                val dictionary = parseAttributes(info)
                currentLogo = dictionary["tvg-logo"]
                // Name is usually after the last comma
                currentName = info.substringAfterLast(",").trim()
            } else if (line.isNotEmpty() && !line.startsWith("#")) {
                // This is the URL
                if (currentName != null) {
                    channels.add(
                        Channel(
                            name = currentName ?: "Unknown",
                            logoUrl = currentLogo,
                            streamUrl = line,
                            category = category
                        )
                    )
                }
                currentName = null
                currentLogo = null
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
