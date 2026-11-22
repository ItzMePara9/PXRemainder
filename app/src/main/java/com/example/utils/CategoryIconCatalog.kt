package com.example.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector

object CategoryIconCatalog {

    val options: List<Pair<String, ImageVector>> = listOf(
        "person" to Icons.Rounded.Person,
        "school" to Icons.Rounded.School,
        "work" to Icons.Rounded.Work,
        "favorite" to Icons.Rounded.Favorite,
        "shopping_bag" to Icons.Rounded.ShoppingBag,
        "cake" to Icons.Rounded.Cake,
        "label" to Icons.Rounded.Label,
        "home" to Icons.Rounded.Home,
        "bolt" to Icons.Rounded.Bolt,
        "code" to Icons.Rounded.Code,
        "payments" to Icons.Rounded.Payments,
        "run" to Icons.Rounded.DirectionsRun,
        "book" to Icons.Rounded.AutoStories,
        "flight" to Icons.Rounded.Flight
    )

    val colorPalette: List<Long> = listOf(
        0xFFA78BFA, // Violet
        0xFF60A5FA, // Blue
        0xFF34D399, // Emerald
        0xFFF87171, // Coral Red
        0xFFFBBF24, // Amber
        0xFFF472B6, // Pink
        0xFF2DD4BF, // Teal
        0xFFFB923C, // Orange
        0xFF94A3B8  // Slate
    )

    fun iconForKey(key: String): ImageVector =
        options.firstOrNull { it.first == key }?.second ?: Icons.Rounded.Label
}
