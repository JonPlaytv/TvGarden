package com.indukto.tvgarden.data

data class Channel(
    val id: String = "",
    val name: String,
    val logoUrl: String?,
    val streamUrl: String,
    val category: String
)
