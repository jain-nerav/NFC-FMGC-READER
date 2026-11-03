package com.example.rfidsscanner

import java.time.LocalDate

/** Expiry condition categories relative to current date. */
enum class ExpiryStatus {
    SAFE,
    WARNING,
    EXPIRED
}

/** Fully resolved product domain model used by UI and TTS. */
data class Product(
    val brand: String,
    val product: String,
    val category: String?,
    val ingredients: List<String>,
    val allergens: List<String>,
    val warnings: List<String>,
    val dietaryAttributes: List<String>,
    val nutrition: List<Int>,
    val expiryDate: LocalDate,
    val daysRemaining: Long,
    val expiryStatus: ExpiryStatus
)

/** Raw JSON payload structure read from NFC/RFID tags. */
data class ProductPayload(
    val b: String, // Brand name
    val p: String, // Product name
    val c: Int,    // Category ID reference
    val a: Int,    // Allergen bitmask
    val w: Int,    // Warning bitmask
    val d: Int,    // Dietary attributes bitmask
    val e: Int,    // Expiry days elapsed since 2020-01-01 epoch
    val n: List<Int>, // Nutrition values array
    val i: List<Int>  // Ingredient ID references
)
