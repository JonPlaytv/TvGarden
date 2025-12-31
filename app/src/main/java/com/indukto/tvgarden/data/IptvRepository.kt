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

    companion object {
        val COUNTRIES = listOf(
            "all" to "All Countries",
            "al" to "Albania",
            "dz" to "Algeria",
            "ar" to "Argentina",
            "am" to "Armenia",
            "au" to "Australia",
            "at" to "Austria",
            "az" to "Azerbaijan",
            "bd" to "Bangladesh",
            "by" to "Belarus",
            "be" to "Belgium",
            "br" to "Brazil",
            "bg" to "Bulgaria",
            "ca" to "Canada",
            "cl" to "Chile",
            "cn" to "China",
            "co" to "Colombia",
            "hr" to "Croatia",
            "cy" to "Cyprus",
            "cz" to "Czech Republic",
            "dk" to "Denmark",
            "eg" to "Egypt",
            "ee" to "Estonia",
            "fi" to "Finland",
            "fr" to "France",
            "ge" to "Georgia",
            "de" to "Germany",
            "gr" to "Greece",
            "hk" to "Hong Kong",
            "hu" to "Hungary",
            "is" to "Iceland",
            "in" to "India",
            "id" to "Indonesia",
            "ir" to "Iran",
            "iq" to "Iraq",
            "ie" to "Ireland",
            "il" to "Israel",
            "it" to "Italy",
            "jp" to "Japan",
            "jo" to "Jordan",
            "kz" to "Kazakhstan",
            "kr" to "South Korea",
            "kw" to "Kuwait",
            "lv" to "Latvia",
            "lb" to "Lebanon",
            "lt" to "Lithuania",
            "lu" to "Luxembourg",
            "my" to "Malaysia",
            "mt" to "Malta",
            "mx" to "Mexico",
            "ma" to "Morocco",
            "nl" to "Netherlands",
            "nz" to "New Zealand",
            "ng" to "Nigeria",
            "no" to "Norway",
            "om" to "Oman",
            "pk" to "Pakistan",
            "pe" to "Peru",
            "ph" to "Philippines",
            "pl" to "Poland",
            "pt" to "Portugal",
            "qa" to "Qatar",
            "ro" to "Romania",
            "ru" to "Russia",
            "sa" to "Saudi Arabia",
            "rs" to "Serbia",
            "sg" to "Singapore",
            "sk" to "Slovakia",
            "si" to "Slovenia",
            "za" to "South Africa",
            "es" to "Spain",
            "se" to "Sweden",
            "ch" to "Switzerland",
            "tw" to "Taiwan",
            "th" to "Thailand",
            "tn" to "Tunisia",
            "tr" to "Turkey",
            "ua" to "Ukraine",
            "ae" to "United Arab Emirates",
            "gb" to "United Kingdom",
            "us" to "United States",
            "uz" to "Uzbekistan",
            "vn" to "Vietnam"
        )
    }

    suspend fun getChannelsByCountry(countryCode: String): List<Channel> = withContext(Dispatchers.IO) {
        if (countryCode == "all") {
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
                val info = line.substringAfter("#EXTINF:")
                val dictionary = parseAttributes(info)
                currentLogo = dictionary["tvg-logo"]
                val groupTitle = dictionary["group-title"] ?: category
                currentCategory = groupTitle.split(";").firstOrNull() ?: category
                currentName = info.substringAfterLast(",").trim()
            } else if (line.isNotEmpty() && !line.startsWith("#")) {
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
        val regex = Regex("([a-zA-Z0-9-]+)=\"([^\"]*)\"")
        regex.findAll(line).forEach { matchResult ->
            attributes[matchResult.groupValues[1]] = matchResult.groupValues[2]
        }
        return attributes
    }
}
