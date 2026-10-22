package com.example.rfidsscanner

import android.util.Log
import com.google.gson.Gson
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.system.measureTimeMillis

object PayloadDecoder {
    private const val TAG = "PayloadDecoder"
    private val gson = Gson()
    private val BASE_DATE = LocalDate.of(2020, 1, 1)

    fun decodePayload(jsonString: String): Product? {
        Log.d(TAG, "Payload received")
        var product: Product? = null
        val totalTime = measureTimeMillis {
            try {
                Log.d(TAG, "Payload parsing started")
                val parseTime = measureTimeMillis {
                    val payload = gson.fromJson(jsonString, ProductPayload::class.java)
                    
                    // Expiry logic
                    Log.d(TAG, "Expiry days received: ${payload.e}")
                    val expiryDate = BASE_DATE.plusDays(payload.e.toLong())
                    Log.d(TAG, "Expiry date calculated: $expiryDate")
                    
                    val today = LocalDate.now()
                    val daysRemaining = ChronoUnit.DAYS.between(today, expiryDate)
                    Log.d(TAG, "Days remaining calculated: $daysRemaining")
                    
                    val status = when {
                        daysRemaining < 0 -> ExpiryStatus.EXPIRED
                        daysRemaining <= 30 -> ExpiryStatus.WARNING
                        else -> ExpiryStatus.SAFE
                    }
                    Log.d(TAG, "Expiry status determined: $status")

                    product = Product(
                        brand = payload.b,
                        product = payload.p,
                        category = DictionaryStore.getCategoryName(payload.c).also { 
                            Log.d(TAG, "Decoded category: $it") 
                        },
                        ingredients = decodeIngredients(payload.i).also { 
                            Log.d(TAG, "Decoded ingredients: ${it.size} items") 
                        },
                        allergens = decodeAllergens(payload.a).also { 
                            Log.d(TAG, "Decoded allergens: $it") 
                        },
                        warnings = decodeWarnings(payload.w).also { 
                            Log.d(TAG, "Decoded warnings: $it") 
                        },
                        dietaryAttributes = decodeDietaryAttributes(payload.d).also { 
                            Log.d(TAG, "Decoded dietary attributes: $it") 
                        },
                        nutrition = payload.n,
                        expiryDate = expiryDate,
                        daysRemaining = daysRemaining,
                        expiryStatus = status
                    )
                }
                Log.d(TAG, "Payload parsing completed in ${parseTime}ms")
            } catch (e: Exception) {
                Log.e(TAG, "Payload parsing failed: ${e.message}")
            }
        }
        Log.d(TAG, "Payload decoding completed in ${totalTime}ms")
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
