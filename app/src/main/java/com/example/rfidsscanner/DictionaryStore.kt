package com.example.rfidsscanner

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.InputStreamReader
import kotlin.system.measureTimeMillis

/**
 * Singleton store managing asynchronous parsing of dictionary.json and
 * providing O(1) constant-time array lookups for ingredients, categories, and bitmasks.
 */
object DictionaryStore {
    private const val TAG = "DictionaryStore"

    @Volatile private var ingredientNames: Array<String?> = emptyArray()
    @Volatile private var categoryNames: Array<String?> = emptyArray()
    @Volatile private var allergenNames: Array<String?> = emptyArray()
    @Volatile private var warningNames: Array<String?> = emptyArray()
    @Volatile private var dietaryNames: Array<String?> = emptyArray()

    @Volatile
    var isInitialized = false
        private set

    /** Asynchronously loads and parses dictionary.json on Dispatchers.IO. */
    fun initialize(context: Context) {
        if (isInitialized) return
        Log.d(TAG, "Dictionary initialization started")

        CoroutineScope(Dispatchers.IO).launch {
            val totalTime = measureTimeMillis {
                try {
                    val assetManager = context.assets
                    val inputStream = assetManager.open("dictionary.json")
                    val reader = InputStreamReader(inputStream)
                    val dictionaryData = Gson().fromJson(reader, DictionaryData::class.java)
                    reader.close()

                    // Build O(1) direct indexing arrays
                    ingredientNames = createLookupArray(dictionaryData.ingredients, { it.id }, { it.name })
                    categoryNames = createLookupArray(dictionaryData.categories, { it.id }, { it.name })
                    allergenNames = createLookupArray(dictionaryData.allergens, { it.bitPosition }, { it.name })
                    warningNames = createLookupArray(dictionaryData.warnings, { it.bitPosition }, { it.name })
                    dietaryNames = createLookupArray(dictionaryData.dietaryAttributes, { it.bitPosition }, { it.name })

                    isInitialized = true
                    Log.d(TAG, "Dictionary initialization completed successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize dictionary", e)
                }
            }
            Log.d(TAG, "Total initialization duration: ${totalTime}ms")
        }
    }

    /** Helper to generate fixed-size nullable string arrays indexed directly by ID/bit position. */
    private fun <T> createLookupArray(
        items: List<T>,
        idSelector: (T) -> Int,
        nameSelector: (T) -> String
    ): Array<String?> {
        if (items.isEmpty()) return emptyArray()
        val maxId = items.maxOf { idSelector(it) }
        val array = arrayOfNulls<String>(maxId + 1)
        for (item in items) {
            val id = idSelector(item)
            if (id in array.indices) {
                array[id] = nameSelector(item)
            }
        }
        return array
    }

    fun getIngredientName(id: Int): String? = ingredientNames.getOrNull(id)
    fun getCategoryName(id: Int): String? = categoryNames.getOrNull(id)
    fun getAllergenName(bitPosition: Int): String? = allergenNames.getOrNull(bitPosition)
    fun getWarningName(bitPosition: Int): String? = warningNames.getOrNull(bitPosition)
    fun getDietaryName(bitPosition: Int): String? = dietaryNames.getOrNull(bitPosition)
}
