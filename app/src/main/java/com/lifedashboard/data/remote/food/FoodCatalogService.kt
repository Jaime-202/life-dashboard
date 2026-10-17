package com.lifedashboard.data.remote.food

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
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
    val imageUrl: String? = null,
    val isGeneric: Boolean = false
)

@Singleton
class FoodCatalogService @Inject constructor() {

    // ─── Catálogo Base de Alimentos Españoles & Supermercados (Offline & Rápido) ─
    private val builtInFoods = listOf(
        // ─── MERCADONA (HACENDADO) CON FOTOS REALES ─────────────────────────────
        FoodCatalogItem(
            id = "mer_pan_1",
            name = "Pan fibra y sésamo Hacendado",
            brand = "Mercadona",
            caloriesPer100g = 398f,
            proteinPer100g = 13f,
            carbsPer100g = 56f,
            fatPer100g = 11f,
            servingSizeGrams = 10f,
            servingDescription = "1 tostada (~10g)",
            imageUrl = "https://images.openfoodfacts.net/images/products/871/044/502/0878/front_es.88.200.jpg",
            barcode = "8710445020878"
        ),
        FoodCatalogItem(
            id = "mer_pan_2",
            name = "Pan de molde 100% integral sin corteza",
            brand = "Mercadona",
            caloriesPer100g = 224f,
            proteinPer100g = 10f,
            carbsPer100g = 38f,
            fatPer100g = 2.4f,
            servingSizeGrams = 42f,
            servingDescription = "1 rebanada (~42g)",
            imageUrl = "https://images.openfoodfacts.net/images/products/848/000/082/3850/front_es.18.200.jpg",
            barcode = "8480000823850"
        ),
        FoodCatalogItem(
            id = "mer_pan_3",
            name = "Pan tostado 100% integral",
            brand = "Mercadona",
            caloriesPer100g = 373f,
            proteinPer100g = 13f,
            carbsPer100g = 62f,
            fatPer100g = 5.5f,
            servingSizeGrams = 18f,
            servingDescription = "1 biscote (~18g)",
            imageUrl = "https://images.openfoodfacts.net/images/products/848/000/082/6127/front_es.25.200.jpg",
            barcode = "8480000826127"
        ),
        FoodCatalogItem(
            id = "mer_pan_4",
            name = "Barra de pan rústica tradicional",
            brand = "Mercadona",
            caloriesPer100g = 255f,
            proteinPer100g = 8.5f,
            carbsPer100g = 51f,
            fatPer100g = 1.2f,
            servingSizeGrams = 60f,
            servingDescription = "1 porción (~60g)",
            imageUrl = "https://images.unsplash.com/photo-1509440159596-0249088772ff?w=200&q=80"
        ),
        FoodCatalogItem(
            id = "mer_pan_5",
            name = "Regañás con aceite de oliva virgen extra",
            brand = "Mercadona",
            caloriesPer100g = 415f,
            proteinPer100g = 11f,
            carbsPer100g = 68f,
            fatPer100g = 9.8f,
            servingSizeGrams = 20f,
            servingDescription = "1 puñado (~20g)",
            imageUrl = "https://images.openfoodfacts.net/images/products/848/000/082/5359/front_es.3.200.jpg"
        ),
        FoodCatalogItem(
            id = "mer_pavo_1",
            name = "Pechuga de pavo 92% carne",
            brand = "Mercadona",
            caloriesPer100g = 94f,
            proteinPer100g = 18.5f,
            carbsPer100g = 1.2f,
            fatPer100g = 1.6f,
            servingSizeGrams = 50f,
            servingDescription = "2 lonchas (~50g)",
            imageUrl = "https://images.openfoodfacts.net/images/products/848/000/059/1179/front_es.33.200.jpg"
        ),
        FoodCatalogItem(
            id = "mer_queso_1",
            name = "Queso fresco batido 0% m.g.",
            brand = "Mercadona",
            caloriesPer100g = 46f,
            proteinPer100g = 8.5f,
            carbsPer100g = 3.5f,
            fatPer100g = 0.1f,
            servingSizeGrams = 200f,
            servingDescription = "1 tazón (~200g)",
            imageUrl = "https://images.openfoodfacts.net/images/products/848/000/015/6583/front_es.36.200.jpg"
        ),
        FoodCatalogItem(
            id = "mer_hummus_1",
            name = "Hummus de garbanzos receta clásica",
            brand = "Mercadona",
            caloriesPer100g = 278f,
            proteinPer100g = 6.4f,
            carbsPer100g = 17.5f,
            fatPer100g = 19.8f,
            servingSizeGrams = 40f,
            servingDescription = "2 cucharadas (~40g)",
            imageUrl = "https://images.openfoodfacts.net/images/products/848/000/026/0402/front_es.61.200.jpg"
        ),
        FoodCatalogItem(
            id = "mer_avena_1",
            name = "Copos de avena integral",
            brand = "Mercadona",
            caloriesPer100g = 375f,
            proteinPer100g = 13.5f,
            carbsPer100g = 59f,
            fatPer100g = 7f,
            servingSizeGrams = 50f,
            servingDescription = "1 ración (~50g)",
            imageUrl = "https://images.openfoodfacts.net/images/products/848/000/009/4816/front_es.29.200.jpg"
        ),
        FoodCatalogItem(
            id = "mer_claras_1",
            name = "Clara de huevo pasteurizada",
            brand = "Mercadona",
            caloriesPer100g = 50f,
            proteinPer100g = 11f,
            carbsPer100g = 0.7f,
            fatPer100g = 0.2f,
            servingSizeGrams = 100f,
            servingDescription = "100ml (3 claras)",
            imageUrl = "https://images.openfoodfacts.net/images/products/848/000/012/6531/front_es.34.200.jpg"
        ),
        FoodCatalogItem(
            id = "mer_atun_1",
            name = "Atún claro al natural Hacendado",
            brand = "Mercadona",
            caloriesPer100g = 101f,
            proteinPer100g = 24f,
            carbsPer100g = 0f,
            fatPer100g = 0.6f,
            servingSizeGrams = 60f,
            servingDescription = "1 lata escurrida (~60g)",
            imageUrl = "https://images.openfoodfacts.net/images/products/848/000/018/1417/front_es.33.200.jpg"
        ),
        FoodCatalogItem(
            id = "mer_guaca_1",
            name = "Guacamole 95% aguacate fresco",
            brand = "Mercadona",
            caloriesPer100g = 162f,
            proteinPer100g = 1.8f,
            carbsPer100g = 3.6f,
            fatPer100g = 15f,
            servingSizeGrams = 50f,
            servingDescription = "1 ración (~50g)",
            imageUrl = "https://images.openfoodfacts.net/images/products/848/000/026/0419/front_es.39.200.jpg"
        ),
        FoodCatalogItem(
            id = "mer_aceite_1",
            name = "Aceite de oliva virgen extra Gran Selección",
            brand = "Mercadona",
            caloriesPer100g = 900f,
            proteinPer100g = 0f,
            carbsPer100g = 0f,
            fatPer100g = 100f,
            servingSizeGrams = 10f,
            servingDescription = "1 cucharada (~10g)",
            imageUrl = "https://images.openfoodfacts.net/images/products/848/000/021/4139/front_es.16.200.jpg"
        ),
        FoodCatalogItem(
            id = "mer_platano_1",
            name = "Plátano de Canarias",
            brand = "Mercadona",
            caloriesPer100g = 91f,
            proteinPer100g = 1.1f,
            carbsPer100g = 21.5f,
            fatPer100g = 0.3f,
            servingSizeGrams = 120f,
            servingDescription = "1 plátano mediano (~120g)",
            imageUrl = "https://images.unsplash.com/photo-1571771894821-ce9b6c11b08e?w=200&q=80"
        ),

        // ─── CARREFOUR ────────────────────────────────────────────────────────
        FoodCatalogItem(
            id = "car_pan_1",
            name = "Pan de molde 100% integral Carrefour",
            brand = "Carrefour",
            caloriesPer100g = 228f,
            proteinPer100g = 9.8f,
            carbsPer100g = 39f,
            fatPer100g = 2.6f,
            servingSizeGrams = 40f,
            servingDescription = "1 rebanada (~40g)",
            imageUrl = "https://images.openfoodfacts.net/images/products/356/007/045/8028/front_es.16.200.jpg"
        ),
        FoodCatalogItem(
            id = "car_pavo_1",
            name = "Pechuga de pavo extra Carrefour",
            brand = "Carrefour",
            caloriesPer100g = 95f,
            proteinPer100g = 19f,
            carbsPer100g = 1f,
            fatPer100g = 1.5f,
            servingSizeGrams = 50f,
            servingDescription = "2 lonchas (~50g)",
            imageUrl = "https://images.openfoodfacts.net/images/products/356/007/049/3081/front_es.17.200.jpg"
        ),
        FoodCatalogItem(
            id = "car_queso_1",
            name = "Queso fresco batido desnatado Carrefour",
            brand = "Carrefour",
            caloriesPer100g = 47f,
            proteinPer100g = 8.5f,
            carbsPer100g = 3.6f,
            fatPer100g = 0.1f,
            servingSizeGrams = 200f,
            servingDescription = "1 tazón (~200g)",
            imageUrl = "https://images.openfoodfacts.net/images/products/356/007/049/8406/front_es.14.200.jpg"
        ),

        // ─── DÍA ──────────────────────────────────────────────────────────────
        FoodCatalogItem(
            id = "dia_pan_1",
            name = "Pan de molde integral Día",
            brand = "Día",
            caloriesPer100g = 230f,
            proteinPer100g = 9.5f,
            carbsPer100g = 40f,
            fatPer100g = 2.5f,
            servingSizeGrams = 40f,
            servingDescription = "1 rebanada (~40g)",
            imageUrl = "https://images.openfoodfacts.net/images/products/848/001/710/2481/front_es.11.200.jpg"
        ),
        FoodCatalogItem(
            id = "dia_pavo_1",
            name = "Pechuga de pavo 90% carne Día",
            brand = "Día",
            caloriesPer100g = 92f,
            proteinPer100g = 18f,
            carbsPer100g = 1.5f,
            fatPer100g = 1.2f,
            servingSizeGrams = 50f,
            servingDescription = "2 lonchas (~50g)",
            imageUrl = "https://images.openfoodfacts.net/images/products/848/001/708/1793/front_es.12.200.jpg"
        ),

        // ─── BÁSICOS / GENÉRICOS DE ESPAÑA CON FOTOS ──────────────────────────
        FoodCatalogItem("gen_1", "Pechuga de pollo a la plancha", "Básico", 165f, 31f, 0f, 3.6f, 150f, "1 filete (~150g)", isGeneric = true, imageUrl = "https://images.unsplash.com/photo-1604503468506-a8da13d82791?w=200&q=80"),
        FoodCatalogItem("gen_2", "Huevo entero de gallina", "Básico", 143f, 12.6f, 0.7f, 9.5f, 60f, "1 huevo mediano (~60g)", isGeneric = true, imageUrl = "https://images.unsplash.com/photo-1587486913049-53fc88980cfc?w=200&q=80"),
        FoodCatalogItem("gen_3", "Arroz blanco cocido", "Básico", 130f, 2.7f, 28f, 0.3f, 150f, "1 plato (~150g cocido)", isGeneric = true, imageUrl = "https://images.unsplash.com/photo-1516684732162-798a0062be99?w=200&q=80"),
        FoodCatalogItem("gen_4", "Salmón a la plancha", "Básico", 208f, 20f, 0f, 13f, 150f, "1 lomo (~150g)", isGeneric = true, imageUrl = "https://images.unsplash.com/photo-1467003909585-2f8a72700288?w=200&q=80"),
        FoodCatalogItem("gen_5", "Patata cocida al vapor", "Básico", 86f, 2f, 18.5f, 0.1f, 200f, "1 patata mediana (~200g)", isGeneric = true, imageUrl = "https://images.unsplash.com/photo-1518977676601-b53f82aba655?w=200&q=80"),
        FoodCatalogItem("gen_6", "Pasta de trigo cocida", "Básico", 158f, 5.8f, 31f, 0.9f, 180f, "1 plato (~180g)", isGeneric = true, imageUrl = "https://images.unsplash.com/photo-1551183053-bf91a1d81141?w=200&q=80"),
        FoodCatalogItem("gen_7", "Yogur griego natural", "Básico", 115f, 6.4f, 3.8f, 8.5f, 125f, "1 vaso (~125g)", isGeneric = true, imageUrl = "https://images.unsplash.com/photo-1488477181946-6428a0291777?w=200&q=80"),
        FoodCatalogItem("gen_8", "Aguacate maduro", "Básico", 160f, 2f, 8.5f, 14.7f, 100f, "Medio aguacate (~100g)", isGeneric = true, imageUrl = "https://images.unsplash.com/photo-1523049673857-eb18f1d7b578?w=200&q=80"),
        FoodCatalogItem("gen_9", "Manzana fresca", "Básico", 52f, 0.3f, 13.8f, 0.2f, 150f, "1 manzana (~150g)", isGeneric = true, imageUrl = "https://images.unsplash.com/photo-1560806887-1e4cd0b6cbd6?w=200&q=80"),
        FoodCatalogItem("gen_10", "Brócoli al vapor", "Básico", 35f, 2.4f, 7.2f, 0.4f, 150f, "1 ración (~150g)", isGeneric = true, imageUrl = "https://images.unsplash.com/photo-1459411621453-7b03977f4bfc?w=200&q=80"),
        FoodCatalogItem("gen_11", "Lentejas cocidas", "Básico", 116f, 9f, 20f, 0.4f, 200f, "1 plato (~200g)", isGeneric = true, imageUrl = "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=200&q=80"),
        FoodCatalogItem("gen_12", "Nueces peladas", "Básico", 654f, 15f, 13.7f, 65f, 25f, "1 puñado (~25g)", isGeneric = true, imageUrl = "https://images.unsplash.com/photo-1541832676-9b763b0239ab?w=200&q=80")
    )

    /**
     * Búsqueda híbrida instantánea:
     * 1. Si es código de barras numérico (8 a 14 dígitos), consulta directa a Open Food Facts por barcode.
     * 2. Si es texto, busca en el catálogo local con coincidencia inmediata y consulta la API remota.
     */
    suspend fun searchFoods(
        query: String,
        category: FoodSourceCategory = FoodSourceCategory.TODOS
    ): List<FoodCatalogItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()

        // 1. Detección de código de barras
        if (trimmed.length in 8..14 && trimmed.all { it.isDigit() }) {
            val barcodeItem = queryByBarcode(trimmed)
            if (barcodeItem != null) {
                return@withContext listOf(barcodeItem)
            }
        }

        // 2. Filtro local inmediato
        val localMatches = if (trimmed.isBlank()) {
            filterByCategory(builtInFoods, category)
        } else {
            val tokens = trimmed.lowercase().split("\\s+".toRegex()).filter { it.isNotBlank() }
            val filtered = builtInFoods.filter { item ->
                val fullText = "${item.name} ${item.brand}".lowercase()
                tokens.all { token -> fullText.contains(token) }
            }
            filterByCategory(filtered, category)
        }

        // Si la categoría es Básicos o la búsqueda tiene menos de 2 caracteres, no consultamos red
        if (category == FoodSourceCategory.BASICOS || trimmed.length < 2) {
            return@withContext localMatches
        }

        // 3. Consulta remota en Open Food Facts (España / Mirror)
        val remoteResults = queryOpenFoodFacts(trimmed, category)

        val combined = (localMatches + remoteResults).distinctBy {
            it.name.lowercase().trim() to it.brand.lowercase().trim()
        }
        filterByCategory(combined, category)
    }

    private fun filterByCategory(items: List<FoodCatalogItem>, category: FoodSourceCategory): List<FoodCatalogItem> {
        return when (category) {
            FoodSourceCategory.TODOS -> items
            FoodSourceCategory.MERCADONA -> items.filter {
                it.brand.contains("mercadona", ignoreCase = true) ||
                it.brand.contains("hacendado", ignoreCase = true)
            }
            FoodSourceCategory.CARREFOUR -> items.filter { it.brand.contains("carrefour", ignoreCase = true) }
            FoodSourceCategory.DIA -> items.filter { it.brand.contains("dia", ignoreCase = true) || it.brand.contains("día", ignoreCase = true) }
            FoodSourceCategory.ALCAMPO -> items.filter { it.brand.contains("alcampo", ignoreCase = true) || it.brand.contains("auchan", ignoreCase = true) }
            FoodSourceCategory.SUPERCOR -> items.filter {
                it.brand.contains("supercor", ignoreCase = true) ||
                it.brand.contains("corte inglés", ignoreCase = true) ||
                it.brand.contains("hipercor", ignoreCase = true)
            }
            FoodSourceCategory.BASICOS -> items.filter { it.isGeneric }
        }
    }

    /**
     * Consulta directa a Open Food Facts por código de barras escaneado.
     */
    private fun queryByBarcode(barcode: String): FoodCatalogItem? {
        val endpoints = listOf(
            "https://world.openfoodfacts.net/api/v0/product/$barcode.json",
            "https://es.openfoodfacts.net/api/v0/product/$barcode.json"
        )
        for (urlString in endpoints) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(urlString)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4000
                    readTimeout = 4000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "LifeDashboardApp/1.0 (contacto@lifedashboard.app)")
                }
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val responseText = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                    val json = JSONObject(responseText)
                    if (json.optInt("status") == 1) {
                        val p = json.optJSONObject("product") ?: continue
                        return parseProductJson(p, barcode)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error buscando barcode $barcode: ${e.message}")
            } finally {
                connection?.disconnect()
            }
        }
        return null
    }

    /**
     * Consulta REST de texto a Open Food Facts con soporte de marcas de supermercado.
     */
    private fun queryOpenFoodFacts(query: String, category: FoodSourceCategory): List<FoodCatalogItem> {
        val results = mutableListOf<FoodCatalogItem>()
        val brandSearchSuffix = when (category) {
            FoodSourceCategory.MERCADONA -> "hacendado"
            FoodSourceCategory.CARREFOUR -> "carrefour"
            FoodSourceCategory.DIA -> "dia"
            FoodSourceCategory.ALCAMPO -> "alcampo"
            FoodSourceCategory.SUPERCOR -> "el corte ingles"
            else -> ""
        }

        val searchTerm = if (brandSearchSuffix.isNotEmpty() && !query.contains(brandSearchSuffix, ignoreCase = true)) {
            "$query $brandSearchSuffix"
        } else {
            query
        }

        val endpoints = listOf(
            "https://es.openfoodfacts.net/cgi/search.pl",
            "https://world.openfoodfacts.net/cgi/search.pl"
        )

        for (baseEndpoint in endpoints) {
            var connection: HttpURLConnection? = null
            try {
                val encoded = URLEncoder.encode(searchTerm, "UTF-8")
                val urlString = "$baseEndpoint?search_terms=$encoded&search_simple=1&action=process&json=1&page_size=25"
                val url = URL(urlString)

                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4500
                    readTimeout = 4500
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "LifeDashboardApp/1.0 (contacto@lifedashboard.app)")
                }

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val responseText = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                    val json = JSONObject(responseText)
                    val products = json.optJSONArray("products")

                    if (products != null) {
                        for (i in 0 until products.length()) {
                            val p = products.optJSONObject(i) ?: continue
                            val item = parseProductJson(p, null)
                            if (item != null) {
                                results.add(item)
                            }
                        }
                    }
                    if (results.isNotEmpty()) break
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fallo al consultar $baseEndpoint: ${e.message}")
            } finally {
                connection?.disconnect()
            }
        }
        return results
    }

    private fun parseProductJson(p: JSONObject, fallbackBarcode: String?): FoodCatalogItem? {
        val name = p.optString("product_name_es").ifBlank {
            p.optString("product_name")
        }
        if (name.isBlank()) return null

        val brands = p.optString("brands").ifBlank { "Supermercado" }
        val nutriments = p.optJSONObject("nutriments") ?: return null

        val kcal = nutriments.optDouble("energy-kcal_100g", nutriments.optDouble("energy-kcal", 0.0)).toFloat()
        val protein = nutriments.optDouble("proteins_100g", 0.0).toFloat()
        val carbs = nutriments.optDouble("carbohydrates_100g", 0.0).toFloat()
        val fat = nutriments.optDouble("fat_100g", 0.0).toFloat()
        val code = p.optString("code").ifBlank { fallbackBarcode ?: "" }
        val servingStr = p.optString("serving_size").ifBlank { "100g" }

        // Extraer imagen del producto
        val imgUrl = p.optString("image_front_small_url")
            .ifBlank { p.optString("image_small_url") }
            .ifBlank { p.optString("image_front_url") }
            .ifBlank { p.optString("image_url") }
            .ifBlank {
                val selected = p.optJSONObject("selected_images")?.optJSONObject("front")?.optJSONObject("small")
                selected?.optString("es")?.ifBlank { selected.optString("en") } ?: ""
            }

        if (kcal > 0 || protein > 0 || carbs > 0 || fat > 0) {
            return FoodCatalogItem(
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
                imageUrl = imgUrl.takeIf { it.isNotBlank() },
                isGeneric = false
            )
        }
        return null
    }

    private fun cleanProductName(name: String): String {
        return name.trim().replaceFirstChar { it.uppercase() }
    }

    private fun cleanBrandName(brand: String): String {
        val first = brand.split(",").firstOrNull()?.trim() ?: brand.trim()
        return first.replaceFirstChar { it.uppercase() }
    }
}
