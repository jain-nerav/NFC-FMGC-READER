package com.example.rfidsscanner

import com.google.gson.annotations.SerializedName

data class DictionaryData(
    @SerializedName("ingredients") val ingredients: List<Ingredient>,
    @SerializedName("allergens") val allergens: List<BitAttribute>,
    @SerializedName("warnings") val warnings: List<BitAttribute>,
    @SerializedName("dietary_attributes") val dietaryAttributes: List<BitAttribute>,
    @SerializedName("categories") val categories: List<Category>,
    @SerializedName("nutrition_fields") val nutritionFields: List<NutritionField>
)

data class Ingredient(
    val id: Int,
    val name: String,
    val aliases: List<String>,
    @SerializedName("ingredient_type") val ingredientType: String
)

data class BitAttribute(
    @SerializedName("bit_position") val bitPosition: Int,
    val name: String,
    val severity: String? = null,
    val priority: String? = null
)

data class Category(
    val id: Int,
    val name: String,
    @SerializedName("parent_category") val parentCategory: String?
)

data class NutritionField(
    val position: Int,
    val name: String,
    val unit: String
)
