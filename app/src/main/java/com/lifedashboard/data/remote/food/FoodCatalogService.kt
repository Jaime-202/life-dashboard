package com.lifedashboard.data.remote.food

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FoodCatalogService"

enum class FoodSourceCategory(val label: String) {
    TODOS("Todos"),
    MERCADONA("Mercadona"),
    CARREFOUR("Carrefour"),
    DIA("Día"),
    ALCAMPO("Alcampo"),
    SUPERCOR("Supercor / ECI"),
    BASICOS("Básicos / Genéricos")
}

data class FoodCatalogItem(
    val id: String,
    val name: String,
    val brand: String,
    val caloriesPer100g: Float,
    val proteinPer100g: Float,
    val carbsPer100g: Float,
    val fatPer100g: Float,
    val servingSizeGrams: Float = 100f,
    val servingDescription: String = "100g",
    val barcode: String? = null,
    val isGeneric: Boolean = false
)

@Singleton
class FoodCatalogService @Inject constructor() {

    // ─── Catálogo Base de Alimentos Españoles (Offline & Instantáneo) ─────────
    private val genericSpanishFoods = listOf(
        // Proteínas
        FoodCatalogItem("gen_1", "Pechuga de pollo a la plancha", "Básico", 165f, 31f, 0f, 3.6f, 150f, "1 filete (~150g)", isGeneric = true),
        FoodCatalogItem("gen_2", "Pechuga de pavo", "Básico", 135f, 29f, 0f, 1.8f, 150f, "1 filete (~150g)", isGeneric = true),
        FoodCatalogItem("gen_3", "Huevo entero mediano", "Básico", 143f, 12.6f, 0.7f, 9.5f, 60f, "1 huevo (~60g)", isGeneric = true),
        FoodCatalogItem("gen_4", "Clara de huevo", "Básico", 52f, 11f, 0.7f, 0.2f, 100f, "100ml / 3 claras", isGeneric = true),
        FoodCatalogItem("gen_5", "Ternera magra picada", "Básico", 215f, 26f, 0f, 12f, 150f, "1 ración (~150g)", isGeneric = true),
        FoodCatalogItem("gen_6", "Lomo de cerdo", "Básico", 160f, 28f, 0f, 4.5f, 125f, "1 filete (~125g)", isGeneric = true),
        FoodCatalogItem("gen_7", "Salmón fresco", "Básico", 208f, 20f, 0f, 13f, 150f, "1 lomo (~150g)", isGeneric = true),
        FoodCatalogItem("gen_8", "Atún al natural", "Básico", 108f, 24f, 0f, 1f, 60f, "1 lata escurrida (~60g)", isGeneric = true),
        FoodCatalogItem("gen_9", "Merluza o pescado blanco", "Básico", 86f, 18f, 0f, 1.3f, 150f, "1 ración (~150g)", isGeneric = true),
        FoodCatalogItem("gen_10", "Tofu natural firme", "Básico", 144f, 15.5f, 2f, 8.5f, 100f, "100g", isGeneric = true),

        // Hidratos de Carbono
        FoodCatalogItem("gen_11", "Arroz blanco cocido", "Básico", 130f, 2.7f, 28f, 0.3f, 150f, "1 plato (~150g cocido)", isGeneric = true),
        FoodCatalogItem("gen_12", "Arroz integral cocido", "Básico", 123f, 2.7f, 25.5f, 1f, 150f, "1 plato (~150g cocido)", isGeneric = true),
        FoodCatalogItem("gen_13", "Copos de avena integral", "Básico", 370f, 13.5f, 58.7f, 7f, 50f, "1 tazón (~50g)", isGeneric = true),
        FoodCatalogItem("gen_14", "Pasta de trigo cocida", "Básico", 158f, 5.8f, 31f, 0.9f, 180f, "1 plato (~180g cocido)", isGeneric = true),
        FoodCatalogItem("gen_15", "Patata cocida o al vapor", "Básico", 86f, 2f, 18.5f, 0.1f, 200f, "1 patata mediana (~200g)", isGeneric = true),
        FoodCatalogItem("gen_16", "Boniato asado", "Básico", 90f, 2f, 20.7f, 0.2f, 180f, "1 boniato (~180g)", isGeneric = true),
        FoodCatalogItem("gen_17", "Pan integral 100%", "Básico", 240f, 9.5f, 43f, 2.5f, 40f, "1 rebanada (~40g)", isGeneric = true),
        FoodCatalogItem("gen_18", "Lentejas cocidas", "Básico", 116f, 9f, 20f, 0.4f, 200f, "1 plato (~200g)", isGeneric = true),
        FoodCatalogItem("gen_19", "Garbanzos cocidos", "Básico", 164f, 8.9f, 27.4f, 2.6f, 200f, "1 plato (~200g)", isGeneric = true),

        // Lácteos y Grasas Saludables
        FoodCatalogItem("gen_20", "Aceite de oliva virgen extra", "Básico", 884f, 0f, 0f, 100f, 10f, "1 cucharada sopera (~10g)", isGeneric = true),
        FoodCatalogItem("gen_21", "Yogur natural entero", "Básico", 61f, 3.5f, 4.7f, 3.3f, 125f, "1 vaso (~125g)", isGeneric = true),
        FoodCatalogItem("gen_22", "Yogur griego natural", "Básico", 115f, 6.4f, 3.8f, 8.5f, 125f, "1 vaso (~125g)", isGeneric = true),
        FoodCatalogItem("gen_23", "Queso fresco batido 0%", "Básico", 48f, 8.5f, 3.5f, 0.1f, 200f, "1 tazón (~200g)", isGeneric = true),
        FoodCatalogItem("gen_24", "Leche semidesnatada", "Básico", 46f, 3.2f, 4.8f, 1.6f, 250f, "1 vaso (~250ml)", isGeneric = true),
        FoodCatalogItem("gen_25", "Nueces peladas", "Básico", 654f, 15f, 13.7f, 65f, 25f, "1 puñado (~25g)", isGeneric = true),
        FoodCatalogItem("gen_26", "Almendras crudas", "Básico", 579f, 21f, 21.6f, 50f, 25f, "1 puñado (~25g)", isGeneric = true),
        FoodCatalogItem("gen_27", "Aguacate maduro", "Básico", 160f, 2f, 8.5f, 14.7f, 100f, "Medio aguacate (~100g)", isGeneric = true),

        // Frutas y Verduras
        FoodCatalogItem("gen_28", "Plátano", "Básico", 89f, 1.1f, 22.8f, 0.3f, 110f, "1 pieza mediana (~110g)", isGeneric = true),
        FoodCatalogItem("gen_29", "Manzana", "Básico", 52f, 0.3f, 13.8f, 0.2f, 150f, "1 manzana (~150g)", isGeneric = true),
        FoodCatalogItem("gen_30", "Arándanos frescos", "Básico", 57f, 0.7f, 14.5f, 0.3f, 100f, "1 taza (~100g)", isGeneric = true),
        FoodCatalogItem("gen_31", "Espinacas frescas", "Básico", 23f, 2.9f, 3.6f, 0.4f, 100f, "100g", isGeneric = true),
        FoodCatalogItem("gen_32", "Brócoli cocido", "Básico", 35f, 2.4f, 7.2f, 0.4f, 150f, "1 ración (~150g)", isGeneric = true)
    )

    /**
     * Búsqueda híbrida: filtra en el catálogo local y consulta la API de Open Food Facts.
     */
    suspend fun searchFoods(
        query: String,
        category: FoodSourceCategory = FoodSourceCategory.TODOS
    ): List<FoodCatalogItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        val localMatches = if (trimmed.isBlank()) {
            genericSpanishFoods.take(15)
        } else {
            genericSpanishFoods.filter {
                it.name.contains(trimmed, ignoreCase = true) ||
                it.brand.contains(trimmed, ignoreCase = true)
            }
        }

        // Si la categoría elegida es Básicos, no hace falta consultar red
        if (category == FoodSourceCategory.BASICOS || trimmed.length < 2) {
            return@withContext filterByCategory(localMatches, category)
        }

        // Consulta Open Food Facts API (España)
        val remoteResults = queryOpenFoodFacts(trimmed, category)

        val combined = (localMatches + remoteResults).distinctBy { it.name.lowercase().trim() to it.brand.lowercase().trim() }
        filterByCategory(combined, category)
    }

    private fun filterByCategory(items: List<FoodCatalogItem>, category: FoodSourceCategory): List<FoodCatalogItem> {
        return when (category) {
            FoodSourceCategory.TODOS -> items
            FoodSourceCategory.MERCADONA -> items.filter { it.brand.contains("mercadona", ignoreCase = true) || it.brand.contains("hacendado", ignoreCase = true) }
            FoodSourceCategory.CARREFOUR -> items.filter { it.brand.contains("carrefour", ignoreCase = true) }
            FoodSourceCategory.DIA -> items.filter { it.brand.contains("dia", ignoreCase = true) || it.brand.contains("día", ignoreCase = true) }
            FoodSourceCategory.ALCAMPO -> items.filter { it.brand.contains("alcampo", ignoreCase = true) || it.brand.contains("auchan", ignoreCase = true) }
            FoodSourceCategory.SUPERCOR -> items.filter { it.brand.contains("supercor", ignoreCase = true) || it.brand.contains("corte inglés", ignoreCase = true) || it.brand.contains("hipercor", ignoreCase = true) }
            FoodSourceCategory.BASICOS -> items.filter { it.isGeneric }
        }
    }

    /**
     * Consulta REST a Open Food Facts España con soporte de marcas de supermercado.
     */
    private fun queryOpenFoodFacts(query: String, category: FoodSourceCategory): List<FoodCatalogItem> {
        val results = mutableListOf<FoodCatalogItem>()
        var connection: HttpURLConnection? = null
        try {
            val brandParam = when (category) {
                FoodSourceCategory.MERCADONA -> "Hacendado,Mercadona"
                FoodSourceCategory.CARREFOUR -> "Carrefour"
                FoodSourceCategory.DIA -> "Dia"
                FoodSourceCategory.ALCAMPO -> "Alcampo,Auchan"
                FoodSourceCategory.SUPERCOR -> "El Corte Ingles,Hipercor,Supercor"
                else -> ""
            }

            val searchTerm = if (brandParam.isNotEmpty() && !query.contains(brandParam, ignoreCase = true)) {
                "$query $brandParam"
            } else {
                query
            }

            val encoded = URLEncoder.encode(searchTerm, "UTF-8")
            val urlString = "https://es.openfoodfacts.org/cgi/search.pl?search_terms=$encoded&search_simple=1&action=process&json=1&page_size=20"
            val url = URL(urlString)

            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "LifeDashboard - Android Health App")
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                val json = JSONObject(responseText)
                val products = json.optJSONArray("products")

                if (products != null) {
                    for (i in 0 until products.length()) {
                        val p = products.optJSONObject(i) ?: continue
                        val name = p.optString("product_name_es").ifBlank {
                            p.optString("product_name")
                        }
                        if (name.isBlank()) continue

                        val brands = p.optString("brands").ifBlank { "Supermercado" }
                        val nutriments = p.optJSONObject("nutriments") ?: continue

                        val kcal = nutriments.optDouble("energy-kcal_100g", nutriments.optDouble("energy-kcal", 0.0)).toFloat()
                        val protein = nutriments.optDouble("proteins_100g", 0.0).toFloat()
                        val carbs = nutriments.optDouble("carbohydrates_100g", 0.0).toFloat()
                        val fat = nutriments.optDouble("fat_100g", 0.0).toFloat()
                        val code = p.optString("code")
                        val servingStr = p.optString("serving_size").ifBlank { "100g" }

                        // Solo añadimos si tiene calorías válidas registradas
                        if (kcal > 0 || protein > 0 || carbs > 0 || fat > 0) {
                            results.add(
                                FoodCatalogItem(
                                    id = "off_${code.ifBlank { name.hashCode().toString() }}",
                                    name = cleanProductName(name),
                                    brand = cleanBrandName(brands),
                                    caloriesPer100g = kcal,
                                    proteinPer100g = protein,
                                    carbsPer100g = carbs,
                                    fatPer100g = fat,
                                    servingSizeGrams = 100f,
                                    servingDescription = servingStr,
                                    barcode = code.takeIf { it.isNotBlank() },
                                    isGeneric = false
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error buscando en Open Food Facts: ${e.message}")
        } finally {
            connection?.disconnect()
        }
        return results
    }

    private fun cleanProductName(name: String): String {
        return name.trim().replaceFirstChar { it.uppercase() }
    }

    private fun cleanBrandName(brand: String): String {
        val first = brand.split(",").firstOrNull()?.trim() ?: brand.trim()
        return first.replaceFirstChar { it.uppercase() }
    }
}
