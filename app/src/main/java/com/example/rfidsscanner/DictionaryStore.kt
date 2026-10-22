package com.example.rfidsscanner

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.InputStreamReader
import kotlin.system.measureTimeMillis

object DictionaryStore {
    private const val TAG = "DictionaryStore"

    @Volatile
    private var ingredientNames: Array<String?> = emptyArray()
    @Volatile
    private var categoryNames: Array<String?> = emptyArray()
    @Volatile
    private var allergenNames: Array<String?> = emptyArray()
    @Volatile
    private var warningNames: Array<String?> = emptyArray()
    @Volatile
    private var dietaryNames: Array<String?> = emptyArray()

    @Volatile
    var isInitialized = false
        private set

    fun initialize(context: Context) {
        if (isInitialized) return
        Log.d(TAG, "Dictionary initialization started")

        // Move expensive work to IO thread to prevent UI jank
        CoroutineScope(Dispatchers.IO).launch {
            val totalTime = measureTimeMillis {
                try {
                    val assetManager = context.assets
                    
                    val loadTime = measureTimeMillis {
                        val inputStream = assetManager.open("dictionary.json")
                        Log.d(TAG, "JSON file loaded")
                        val reader = InputStreamReader(inputStream)
                        
                        val parseTime = measureTimeMillis {
                            val dictionaryData = Gson().fromJson(reader, DictionaryData::class.java)
                            reader.close()
                            Log.d(TAG, "JSON parse completed")

                            val buildTime = measureTimeMillis {
                                // Build O(1) Lookup Arrays
                                val newIngredients = createLookupArray(dictionaryData.ingredients, { it.id }, { it.name })
                                val newCategories = createLookupArray(dictionaryData.categories, { it.id }, { it.name })
                                val newAllergens = createLookupArray(dictionaryData.allergens, { it.bitPosition }, { it.name })
                                val newWarnings = createLookupArray(dictionaryData.warnings, { it.bitPosition }, { it.name })
                                val newDietary = createLookupArray(dictionaryData.dietaryAttributes, { it.bitPosition }, { it.name })

                                // Assign to volatile fields
                                ingredientNames = newIngredients
                                categoryNames = newCategories
                                allergenNames = newAllergens
                                warningNames = newWarnings
                                dietaryNames = newDietary
                                
                                Log.d(TAG, "Lookup tables generated")
                                Log.d(TAG, "Number of ingredients loaded: ${dictionaryData.ingredients.size}")
                                Log.d(TAG, "Number of allergens loaded: ${dictionaryData.allergens.size}")
                            }
                            Log.d(TAG, "Lookup tables built in ${buildTime}ms")
                        }
                        Log.d(TAG, "JSON parsing and building took ${parseTime}ms")
                    }
                    Log.d(TAG, "Assets loading and processing took ${loadTime}ms")

                    isInitialized = true
                    Log.d(TAG, "Dictionary initialization completed")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize dictionary", e)
                }
            }
            Log.d(TAG, "Total initialization duration: ${totalTime}ms")
        }
    }

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
            } else {
                Log.e(TAG, "Invalid ID $id for item ${nameSelector(item)}")
            }
        }
        return array
    }

    fun getIngredientName(id: Int): String? {
        val name = ingredientNames.getOrNull(id)
        if (name == null && isInitialized) Log.e(TAG, "Missing ingredient ID: $id")
        return name
    }

    fun getCategoryName(id: Int): String? {
        val name = categoryNames.getOrNull(id)
        if (name == null && isInitialized) Log.e(TAG, "Invalid category ID: $id")
        return name
    }

    fun getAllergenName(bitPosition: Int): String? {
        val name = allergenNames.getOrNull(bitPosition)
        if (name == null && isInitialized) Log.e(TAG, "Invalid allergen bitmask value: $bitPosition")
        return name
    }

    fun getWarningName(bitPosition: Int): String? {
        val name = warningNames.getOrNull(bitPosition)
        if (name == null && isInitialized) Log.e(TAG, "Invalid warning bitmask value: $bitPosition")
        return name
    }

    fun getDietaryName(bitPosition: Int): String? {
        val name = dietaryNames.getOrNull(bitPosition)
        if (name == null && isInitialized) Log.e(TAG, "Invalid dietary bitmask value: $bitPosition")
        return name
    }
}
