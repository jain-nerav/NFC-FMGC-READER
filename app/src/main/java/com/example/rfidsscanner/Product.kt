package com.example.rfidsscanner

import java.time.LocalDate

enum class ExpiryStatus {
    SAFE,
    WARNING,
    EXPIRED
}

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

/**
 * Raw payload structure from NFC
 */
data class ProductPayload(
    val b: String, // Brand
    val p: String, // Product
    val c: Int,    // Category ID
    val a: Int,    // Allergen Bitmask
    val w: Int,    // Warning Bitmask
    val d: Int,    // Dietary Bitmask
    val e: Int,    // Expiry Days since 2020-01-01
    val n: List<Int>, // Nutrition values
    val i: List<Int>  // Ingredient IDs
)
