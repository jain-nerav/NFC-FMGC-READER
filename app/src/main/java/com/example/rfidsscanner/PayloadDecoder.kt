package com.example.rfidsscanner

import android.util.Log
import com.google.gson.Gson
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Utility object for deserializing NFC JSON payloads and expanding numeric IDs
 * and bitmasks into fully resolved Product domain models.
 */
object PayloadDecoder {
    private const val TAG = "PayloadDecoder"
    private val gson = Gson()
    private val BASE_DATE = LocalDate.of(2020, 1, 1)

    /** Decodes raw NFC JSON string into a structured Product domain model. */
    fun decodePayload(jsonString: String): Product? {
        var product: Product? = null
        try {
            val payload = gson.fromJson(jsonString, ProductPayload::class.java)
            
            // Reconstruct expiration date from 2020-01-01 epoch offset days
            val expiryDate = BASE_DATE.plusDays(payload.e.toLong())
            val daysRemaining = ChronoUnit.DAYS.between(LocalDate.now(), expiryDate)
            
            val status = when {
                daysRemaining < 0 -> ExpiryStatus.EXPIRED
                daysRemaining <= 30 -> ExpiryStatus.WARNING
                else -> ExpiryStatus.SAFE
            }

            product = Product(
                brand = payload.b,
                product = payload.p,
                category = DictionaryStore.getCategoryName(payload.c),
                ingredients = decodeIngredients(payload.i),
                allergens = decodeAllergens(payload.a),
                warnings = decodeWarnings(payload.w),
                dietaryAttributes = decodeDietaryAttributes(payload.d),
                nutrition = payload.n,
                expiryDate = expiryDate,
                daysRemaining = daysRemaining,
                expiryStatus = status
            )
        } catch (e: Exception) {
            Log.e(TAG, "Payload parsing failed: ${e.message}")
        }
        return product
    }

    private fun decodeIngredients(ids: List<Int>): List<String> {
        return ids.mapNotNull { DictionaryStore.getIngredientName(it) }
    }

    private fun decodeAllergens(bitmask: Int): List<String> {
        val result = mutableListOf<String>()
        for (i in 0 until 32) {
            if ((bitmask and (1 shl i)) != 0) {
                DictionaryStore.getAllergenName(i)?.let { result.add(it) }
            }
        }
        return result
    }

    private fun decodeWarnings(bitmask: Int): List<String> {
        val result = mutableListOf<String>()
        for (i in 0 until 32) {
            if ((bitmask and (1 shl i)) != 0) {
                DictionaryStore.getWarningName(i)?.let { result.add(it) }
            }
        }
        return result
    }

    private fun decodeDietaryAttributes(bitmask: Int): List<String> {
        val result = mutableListOf<String>()
        for (i in 0 until 32) {
            if ((bitmask and (1 shl i)) != 0) {
                DictionaryStore.getDietaryName(i)?.let { result.add(it) }
            }
        }
        return result
    }
}
