package com.lifedashboard.data.remote.food

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
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

data class QuadrupleCalories(
    val calories: Float,
    val protein: Float,
    val carbs: Float,
    val fat: Float
)

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
class FoodCatalogService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aiService: FoodAiService
) {

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
            imageUrl = "https://images.openfoodfacts.net/images/products/848/000/082/5359/front_es.3.200.jpg",
            barcode = "8480000825359"
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
            imageUrl = "https://images.openfoodfacts.net/images/products/848/000/059/1179/front_es.33.200.jpg",
            barcode = "8480000591179"
        ),
        FoodCatalogItem(
            id = "mer_pollo_1",
            name = "Pechuga de pollo 90% en lonchas",
            brand = "Mercadona",
            caloriesPer100g = 95f,
            proteinPer100g = 19f,
            carbsPer100g = 1f,
            fatPer100g = 1.5f,
            servingSizeGrams = 50f,
            servingDescription = "2 lonchas (~50g)",
            imageUrl = "https://images.openfoodfacts.net/images/products/848/000/059/1186/front_es.23.200.jpg",
            barcode = "8480000591186"
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
            imageUrl = "https://images.openfoodfacts.net/images/products/848/000/015/6583/front_es.36.200.jpg",
            barcode = "8480000156583"
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
            imageUrl = "https://images.openfoodfacts.net/images/products/848/000/026/0402/front_es.61.200.jpg",
            barcode = "8480000260402"
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
            imageUrl = "https://images.openfoodfacts.net/images/products/848/000/009/4816/front_es.29.200.jpg",
            barcode = "8480000094816"
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
            imageUrl = "https://images.openfoodfacts.net/images/products/848/000/012/6531/front_es.34.200.jpg",
            barcode = "8480000126531"
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
            imageUrl = "https://images.openfoodfacts.net/images/products/848/000/018/1417/front_es.33.200.jpg",
            barcode = "8480000181417"
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
            imageUrl = "https://images.openfoodfacts.net/images/products/848/000/026/0419/front_es.39.200.jpg",
            barcode = "8480000260419"
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
            imageUrl = "https://images.openfoodfacts.net/images/products/848/000/021/4139/front_es.16.200.jpg",
            barcode = "8480000214139"
        ),
        FoodCatalogItem(
            id = "mer_cacahuete_1",
            name = "Crema de cacahuete 100% cacahuetes",
            brand = "Mercadona",
            caloriesPer100g = 618f,
            proteinPer100g = 30f,
            carbsPer100g = 7f,
            fatPer100g = 50f,
            servingSizeGrams = 20f,
            servingDescription = "1 cucharada colmada (~20g)",
            imageUrl = "https://images.openfoodfacts.net/images/products/848/000/021/6881/front_es.35.200.jpg",
            barcode = "8480000216881"
        ),
        FoodCatalogItem(
            id = "mer_mousse_1",
            name = "Mousse de proteínas sabor chocolate +Proteínas",
            brand = "Mercadona",
            caloriesPer100g = 76f,
            proteinPer100g = 10f,
            carbsPer100g = 5.2f,
            fatPer100g = 1.6f,
            servingSizeGrams = 200f,
            servingDescription = "1 tarrina (~200g)",
            imageUrl = "https://images.openfoodfacts.net/images/products/848/000/015/8860/front_es.32.200.jpg",
            barcode = "8480000158860"
        ),
        FoodCatalogItem(
            id = "mer_griego_1",
            name = "Yogur griego natural Hacendado",
            brand = "Mercadona",
            caloriesPer100g = 129f,
            proteinPer100g = 3.9f,
            carbsPer100g = 3.2f,
            fatPer100g = 11f,
            servingSizeGrams = 125f,
            servingDescription = "1 yogur (~125g)",
            imageUrl = "https://images.openfoodfacts.net/images/products/848/000/015/4565/front_es.25.200.jpg",
            barcode = "8480000154565"
        ),
        FoodCatalogItem(
            id = "mer_tofu_1",
            name = "Tofu firme Hacendado",
            brand = "Mercadona",
            caloriesPer100g = 123f,
            proteinPer100g = 12f,
            carbsPer100g = 1.8f,
            fatPer100g = 7.2f,
            servingSizeGrams = 100f,
            servingDescription = "1 ración (~100g)",
            imageUrl = "https://images.openfoodfacts.net/images/products/848/000/016/5288/front_es.16.200.jpg",
            barcode = "8480000165288"
        ),
        FoodCatalogItem(
            id = "mer_tortitas_1",
            name = "Tortitas de maíz Hacendado",
            brand = "Mercadona",
            caloriesPer100g = 387f,
            proteinPer100g = 7.9f,
            carbsPer100g = 82f,
            fatPer100g = 2.3f,
            servingSizeGrams = 8f,
            servingDescription = "1 tortita (~8g)",
            imageUrl = "https://images.openfoodfacts.net/images/products/848/000/082/2853/front_es.14.200.jpg",
            barcode = "8480000822853"
        ),
        FoodCatalogItem(
            id = "mer_jamon_1",
            name = "Jamón serrano reserva lonchas",
            brand = "Mercadona",
            caloriesPer100g = 231f,
            proteinPer100g = 32f,
            carbsPer100g = 0.5f,
            fatPer100g = 11f,
            servingSizeGrams = 40f,
            servingDescription = "2 lonchas (~40g)",
            imageUrl = "https://images.openfoodfacts.net/images/products/848/000/051/5243/front_es.20.200.jpg",
            barcode = "8480000515243"
        ),
        FoodCatalogItem(
            id = "mer_salmon_1",
            name = "Salmón ahumado lonchas",
            brand = "Mercadona",
            caloriesPer100g = 192f,
            proteinPer100g = 22f,
            carbsPer100g = 0.5f,
            fatPer100g = 11.5f,
            servingSizeGrams = 50f,
            servingDescription = "2 lonchas (~50g)",
            imageUrl = "https://images.openfoodfacts.net/images/products/848/000/018/6955/front_es.17.200.jpg",
            barcode = "8480000186955"
        ),
        FoodCatalogItem(
            id = "mer_soja_1",
            name = "Bebida de soja 0% azúcares añadidos",
            brand = "Mercadona",
            caloriesPer100g = 32f,
            proteinPer100g = 3.2f,
            carbsPer100g = 0.8f,
            fatPer100g = 1.8f,
            servingSizeGrams = 200f,
            servingDescription = "1 vaso (~200ml)",
            imageUrl = "https://images.openfoodfacts.net/images/products/848/000/011/4986/front_es.28.200.jpg",
            barcode = "8480000114986"
        ),
        FoodCatalogItem(
            id = "mer_leche_1",
            name = "Leche desnatada vaca Hacendado",
            brand = "Mercadona",
            caloriesPer100g = 35f,
            proteinPer100g = 3.2f,
            carbsPer100g = 4.8f,
            fatPer100g = 0.2f,
            servingSizeGrams = 200f,
            servingDescription = "1 vaso (~200ml)",
            imageUrl = "https://images.openfoodfacts.net/images/products/848/000/010/8254/front_es.30.200.jpg",
            barcode = "8480000108254"
        ),
        FoodCatalogItem(
            id = "mer_garbanzos_1",
            name = "Garbanzos cocidos bote cristal",
            brand = "Mercadona",
            caloriesPer100g = 103f,
            proteinPer100g = 6.5f,
            carbsPer100g = 13f,
            fatPer100g = 2.1f,
            servingSizeGrams = 200f,
            servingDescription = "Medio bote escurrido (~200g)",
            imageUrl = "https://images.openfoodfacts.net/images/products/848/000/016/0856/front_es.24.200.jpg",
            barcode = "8480000160856"
        ),
        FoodCatalogItem(
            id = "mer_lentejas_1",
            name = "Lentejas cocidas bote cristal",
            brand = "Mercadona",
            caloriesPer100g = 91f,
            proteinPer100g = 7.2f,
            carbsPer100g = 11.5f,
            fatPer100g = 0.8f,
            servingSizeGrams = 200f,
            servingDescription = "Medio bote escurrido (~200g)",
            imageUrl = "https://images.openfoodfacts.net/images/products/848/000/016/0863/front_es.22.200.jpg",
            barcode = "8480000160863"
        ),
        FoodCatalogItem(
            id = "mer_tomate_1",
            name = "Tomate triturado natural",
            brand = "Mercadona",
            caloriesPer100g = 19f,
            proteinPer100g = 1f,
            carbsPer100g = 3.2f,
            fatPer100g = 0.1f,
            servingSizeGrams = 100f,
            servingDescription = "100g",
            imageUrl = "https://images.openfoodfacts.net/images/products/848/000/016/2645/front_es.27.200.jpg",
            barcode = "8480000162645"
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

        // ─── CARREFOUR (FOTOS REALES DE PRODUCTO) ───────────────────────────
        FoodCatalogItem("car_pan_1", "Pan de molde 100% integral Carrefour", "Carrefour", 228f, 9.8f, 39f, 2.6f, 40f, "1 rebanada (~40g)", imageUrl = "https://images.openfoodfacts.net/images/products/356/007/045/8028/front_es.16.200.jpg"),
        FoodCatalogItem("car_pan_2", "Barra de pan rústica Carrefour", "Carrefour", 252f, 8.4f, 51f, 1.3f, 60f, "1 trozo (~60g)", imageUrl = "https://images.unsplash.com/photo-1509440159596-0249088772ff?w=200&q=80"),
        FoodCatalogItem("car_pavo_1", "Pechuga de pavo extra Carrefour", "Carrefour", 95f, 19f, 1f, 1.5f, 50f, "2 lonchas (~50g)", imageUrl = "https://images.openfoodfacts.net/images/products/356/007/049/3081/front_es.17.200.jpg"),
        FoodCatalogItem("car_pollo_1", "Pechuga de pollo asada Carrefour", "Carrefour", 98f, 20f, 0.8f, 1.6f, 50f, "2 lonchas (~50g)", imageUrl = "https://images.unsplash.com/photo-1604503468506-a8da13d82791?w=200&q=80"),
        FoodCatalogItem("car_queso_1", "Queso fresco batido desnatado Carrefour", "Carrefour", 47f, 8.5f, 3.6f, 0.1f, 200f, "1 tazón (~200g)", imageUrl = "https://images.openfoodfacts.net/images/products/356/007/049/8406/front_es.14.200.jpg"),
        FoodCatalogItem("car_leche_1", "Leche desnatada Carrefour", "Carrefour", 34f, 3.1f, 4.8f, 0.2f, 200f, "1 vaso (~200ml)", imageUrl = "https://images.openfoodfacts.net/images/products/356/007/038/4952/front_es.21.200.jpg"),
        FoodCatalogItem("car_atun_1", "Atún claro al natural Carrefour", "Carrefour", 102f, 24f, 0f, 0.7f, 60f, "1 lata escurrida (~60g)", imageUrl = "https://images.openfoodfacts.net/images/products/356/007/050/0949/front_es.19.200.jpg"),
        FoodCatalogItem("car_avena_1", "Copos de avena integral Carrefour Bio", "Carrefour", 370f, 13f, 58f, 7f, 50f, "1 ración (~50g)", imageUrl = "https://images.openfoodfacts.net/images/products/356/007/081/8112/front_es.25.200.jpg"),
        FoodCatalogItem("car_hummus_1", "Hummus clásico Carrefour", "Carrefour", 270f, 6.2f, 17f, 19f, 40f, "2 cucharadas (~40g)", imageUrl = "https://images.unsplash.com/photo-1577906096429-f73c2c312435?w=200&q=80"),
        FoodCatalogItem("car_tortitas_1", "Tortitas de maíz Carrefour", "Carrefour", 385f, 7.8f, 82f, 2.2f, 8f, "1 tortita (~8g)", imageUrl = "https://images.unsplash.com/photo-1599490659213-e2b9527bd087?w=200&q=80"),

        // ─── DÍA (FOTOS REALES DE PRODUCTO) ──────────────────────────────────
        FoodCatalogItem("dia_pan_1", "Pan de molde integral Día", "Día", 230f, 9.5f, 40f, 2.5f, 40f, "1 rebanada (~40g)", imageUrl = "https://images.openfoodfacts.net/images/products/848/001/710/2481/front_es.11.200.jpg"),
        FoodCatalogItem("dia_pan_2", "Barra de pan horneada Día", "Día", 250f, 8.5f, 50f, 1.2f, 60f, "1 trozo (~60g)", imageUrl = "https://images.unsplash.com/photo-1509440159596-0249088772ff?w=200&q=80"),
        FoodCatalogItem("dia_pavo_1", "Pechuga de pavo 90% carne Día", "Día", 92f, 18f, 1.5f, 1.2f, 50f, "2 lonchas (~50g)", imageUrl = "https://images.openfoodfacts.net/images/products/848/001/708/1793/front_es.12.200.jpg"),
        FoodCatalogItem("dia_pollo_1", "Pechuga de pollo braseada Día", "Día", 96f, 19f, 1.2f, 1.4f, 50f, "2 lonchas (~50g)", imageUrl = "https://images.unsplash.com/photo-1604503468506-a8da13d82791?w=200&q=80"),
        FoodCatalogItem("dia_leche_1", "Leche desnatada Día", "Día", 35f, 3.2f, 4.9f, 0.2f, 200f, "1 vaso (~200ml)", imageUrl = "https://images.openfoodfacts.net/images/products/848/001/704/8666/front_es.20.200.jpg"),
        FoodCatalogItem("dia_queso_1", "Queso fresco batido 0% Día", "Día", 46f, 8.5f, 3.5f, 0.1f, 200f, "1 tazón (~200g)", imageUrl = "https://images.unsplash.com/photo-1488477181946-6428a0291777?w=200&q=80"),
        FoodCatalogItem("dia_atun_1", "Atún claro al natural Día", "Día", 100f, 24f, 0f, 0.6f, 60f, "1 lata escurrida (~60g)", imageUrl = "https://images.openfoodfacts.net/images/products/848/001/704/6761/front_es.17.200.jpg"),
        FoodCatalogItem("dia_avena_1", "Copos de avena integral Día", "Día", 372f, 13f, 58f, 7f, 50f, "1 ración (~50g)", imageUrl = "https://images.unsplash.com/photo-1586495777744-4413f21062fa?w=200&q=80"),

        // ─── ALCAMPO (FOTOS REALES DE PRODUCTO) ──────────────────────────────
        FoodCatalogItem("alc_pan_1", "Pan 100% integral Auchan", "Alcampo", 235f, 9.2f, 41f, 2.7f, 40f, "1 rebanada (~40g)", imageUrl = "https://images.openfoodfacts.net/images/products/359/671/045/6368/front_es.16.200.jpg"),
        FoodCatalogItem("alc_pan_2", "Barra baguette Auchan", "Alcampo", 255f, 8.2f, 52f, 1.1f, 60f, "1 trozo (~60g)", imageUrl = "https://images.unsplash.com/photo-1509440159596-0249088772ff?w=200&q=80"),
        FoodCatalogItem("alc_pavo_1", "Pechuga de pavo Auchan", "Alcampo", 94f, 18f, 1.4f, 1.3f, 50f, "2 lonchas (~50g)", imageUrl = "https://images.openfoodfacts.net/images/products/359/671/047/5567/front_es.15.200.jpg"),
        FoodCatalogItem("alc_pollo_1", "Pechuga de pollo lonchas Auchan", "Alcampo", 95f, 19f, 1f, 1.5f, 50f, "2 lonchas (~50g)", imageUrl = "https://images.unsplash.com/photo-1604503468506-a8da13d82791?w=200&q=80"),
        FoodCatalogItem("alc_leche_1", "Leche desnatada Auchan", "Alcampo", 34f, 3.2f, 4.8f, 0.2f, 200f, "1 vaso (~200ml)", imageUrl = "https://images.openfoodfacts.net/images/products/359/671/034/1800/front_es.18.200.jpg"),
        FoodCatalogItem("alc_queso_1", "Queso fresco batido 0% Auchan", "Alcampo", 47f, 8.4f, 3.5f, 0.1f, 200f, "1 tazón (~200g)", imageUrl = "https://images.unsplash.com/photo-1488477181946-6428a0291777?w=200&q=80"),
        FoodCatalogItem("alc_atun_1", "Atún claro al natural Auchan", "Alcampo", 101f, 24f, 0f, 0.6f, 60f, "1 lata escurrida (~60g)", imageUrl = "https://images.unsplash.com/photo-1501595091296-3aa970afb3ff?w=200&q=80"),

        // ─── SUPERCOR / EL CORTE INGLÉS (FOTOS REALES) ────────────────────────
        FoodCatalogItem("sup_pan_1", "Pan de molde multicereales El Corte Inglés", "Supercor", 245f, 10.5f, 42f, 3.5f, 40f, "1 rebanada (~40g)", imageUrl = "https://images.unsplash.com/photo-1509440159596-0249088772ff?w=200&q=80"),
        FoodCatalogItem("sup_pavo_1", "Pechuga de pavo braseada El Corte Inglés", "Supercor", 98f, 19.5f, 1.2f, 1.6f, 50f, "2 lonchas (~50g)", imageUrl = "https://images.openfoodfacts.net/images/products/843/332/902/5253/front_es.12.200.jpg"),
        FoodCatalogItem("sup_pollo_1", "Pechuga de pollo a las finas hierbas El Corte Inglés", "Supercor", 99f, 20f, 1f, 1.5f, 50f, "2 lonchas (~50g)", imageUrl = "https://images.unsplash.com/photo-1604503468506-a8da13d82791?w=200&q=80"),
        FoodCatalogItem("sup_leche_1", "Leche semidesnatada El Corte Inglés", "Supercor", 46f, 3.2f, 4.8f, 1.6f, 200f, "1 vaso (~200ml)", imageUrl = "https://images.unsplash.com/photo-1550583724-b2692b85b150?w=200&q=80"),
        FoodCatalogItem("sup_queso_1", "Queso semicurado cuña El Corte Inglés", "Supercor", 380f, 25f, 1.2f, 31f, 40f, "1 porción (~40g)", imageUrl = "https://images.unsplash.com/photo-1486297678162-eb2a19b0a32d?w=200&q=80"),
        FoodCatalogItem("sup_atun_1", "Atún claro en aceite de oliva virgen extra El Corte Inglés", "Supercor", 195f, 25f, 0f, 10.5f, 60f, "1 lata escurrida (~60g)", imageUrl = "https://images.unsplash.com/photo-1501595091296-3aa970afb3ff?w=200&q=80")
    )

    private var assetFoods: List<FoodCatalogItem>? = null

    private fun loadAssetFoods(): List<FoodCatalogItem> {
        val cached = assetFoods
        if (cached != null) return cached

        val list = ArrayList<FoodCatalogItem>()
        // 1. Mercadona Catalog
        try {
            val jsonString = context.assets.open("mercadona_catalog.json").bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    FoodCatalogItem(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        brand = obj.getString("brand"),
                        caloriesPer100g = obj.getDouble("caloriesPer100g").toFloat(),
                        proteinPer100g = obj.getDouble("proteinPer100g").toFloat(),
                        carbsPer100g = obj.getDouble("carbsPer100g").toFloat(),
                        fatPer100g = obj.getDouble("fatPer100g").toFloat(),
                        servingSizeGrams = obj.optDouble("servingSizeGrams", 100.0).toFloat(),
                        servingDescription = obj.optString("servingDescription", "100g"),
                        imageUrl = obj.optString("imageUrl", "").takeIf { it.isNotBlank() },
                        barcode = obj.optString("barcode", "").takeIf { it.isNotBlank() },
                        isGeneric = false
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error cargando mercadona_catalog.json: ${e.message}")
        }

        // 2. Supermarkets Catalog (Día, Supercor, Carrefour, Alcampo)
        try {
            val jsonString = context.assets.open("supermarkets_catalog.json").bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    FoodCatalogItem(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        brand = obj.getString("brand"),
                        caloriesPer100g = obj.getDouble("caloriesPer100g").toFloat(),
                        proteinPer100g = obj.getDouble("proteinPer100g").toFloat(),
                        carbsPer100g = obj.getDouble("carbsPer100g").toFloat(),
                        fatPer100g = obj.getDouble("fatPer100g").toFloat(),
                        servingSizeGrams = obj.optDouble("servingSizeGrams", 100.0).toFloat(),
                        servingDescription = obj.optString("servingDescription", "100g"),
                        imageUrl = obj.optString("imageUrl", "").takeIf { it.isNotBlank() },
                        barcode = obj.optString("barcode", "").takeIf { it.isNotBlank() },
                        isGeneric = false
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error cargando supermarkets_catalog.json: ${e.message}")
        }

        // 3. Basics / Generic Foods Catalog (Pollo, Ternera, Huevos, Arroz, Frutas, Verduras)
        try {
            val jsonString = context.assets.open("basics_catalog.json").bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    FoodCatalogItem(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        brand = obj.optString("brand", "Básico"),
                        caloriesPer100g = obj.getDouble("caloriesPer100g").toFloat(),
                        proteinPer100g = obj.getDouble("proteinPer100g").toFloat(),
                        carbsPer100g = obj.getDouble("carbsPer100g").toFloat(),
                        fatPer100g = obj.getDouble("fatPer100g").toFloat(),
                        servingSizeGrams = obj.optDouble("servingSizeGrams", 100.0).toFloat(),
                        servingDescription = obj.optString("servingDescription", "100g"),
                        imageUrl = obj.optString("imageUrl", "").takeIf { it.isNotBlank() },
                        barcode = obj.optString("barcode", "").takeIf { it.isNotBlank() },
                        isGeneric = true
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error cargando basics_catalog.json: ${e.message}")
        }

        assetFoods = list
        return list
    }

    private fun normalize(text: String): String {
        return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .lowercase(Locale.ROOT)
            .trim()
    }

    private fun calculateRelevance(item: FoodCatalogItem, cleanQuery: String, tokens: List<String>): Int {
        val normName = normalize(item.name)
        val normBrand = normalize(item.brand)

        var score = 0
        if (normName.startsWith(cleanQuery)) {
            score += 1000 - normName.length.coerceAtMost(100)
        } else if (normName.contains(" $cleanQuery ") || normName.endsWith(" $cleanQuery")) {
            score += 800 - normName.length.coerceAtMost(100)
        } else if (normName.contains(cleanQuery)) {
            score += 500 - normName.length.coerceAtMost(100)
        }

        for (token in tokens) {
            when {
                normName.startsWith(token) -> score += 300
                normName.contains(" $token ") || normName.endsWith(" $token") -> score += 250
                normName.contains(token) -> score += 100
                normBrand.contains(token) -> score += 50
            }
        }
        return score
    }

    /**
     * Búsqueda híbrida instantánea de ultra-alta cobertura:
     * 1. Consulta el catálogo offline masivo (Mercadona/Hacendado oficial + Carrefour + Día + Alcampo + Básicos con fotos reales).
     * 2. Si es código de barras, busca en local y consulta Open Food Facts.
     * 3. Aplica normalización de acentos y ranking de relevancia.
     * 4. Si hay pocos resultados para una marca o no hay coincidencias, complementa con Open Food Facts o IA.
     */
    suspend fun searchFoods(
        query: String,
        category: FoodSourceCategory = FoodSourceCategory.TODOS
    ): List<FoodCatalogItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        val allFoods = builtInFoods + loadAssetFoods()

        // 1. Detección y búsqueda por código de barras o QR
        val digitMatch = Regex("""\b(\d{8,14})\b""").find(trimmed)?.value ?: trimmed
        if (digitMatch.length in 8..14 && digitMatch.all { it.isDigit() }) {
            val localBarcodeMatch = allFoods.firstOrNull { it.barcode == digitMatch }
            if (localBarcodeMatch != null) {
                val resolvedBarcodeImg = RealFoodImageResolver.resolve(localBarcodeMatch.name, localBarcodeMatch.brand, localBarcodeMatch.imageUrl)
                return@withContext listOf(localBarcodeMatch.copy(imageUrl = resolvedBarcodeImg))
            }
            val barcodeItem = queryByBarcode(digitMatch)
            if (barcodeItem != null) {
                val resolvedBarcodeImg = RealFoodImageResolver.resolve(barcodeItem.name, barcodeItem.brand, barcodeItem.imageUrl)
                return@withContext listOf(barcodeItem.copy(imageUrl = resolvedBarcodeImg))
            }
        }

        // 2. Filtro local normalizado y rankeado por relevancia
        val normQuery = normalize(trimmed)
        val tokens = normQuery.split("\\s+".toRegex()).filter { it.isNotBlank() }

        val localMatches = if (trimmed.isBlank()) {
            filterByCategory(allFoods, category).take(500)
        } else {
            val categorized = filterByCategory(allFoods, category)
            val filtered = categorized.filter { item ->
                val normItem = normalize("${item.name} ${item.brand}")
                tokens.all { token -> normItem.contains(token) }
            }
            filtered.sortedByDescending { item ->
                calculateRelevance(item, normQuery, tokens)
            }.take(250)
        }

        // Si la búsqueda está vacía (ej: selección de pestaña de supermercado), devolver catálogo completo filtrado
        if (trimmed.isBlank()) {
            return@withContext localMatches.map { item ->
                item.copy(imageUrl = RealFoodImageResolver.resolve(item.name, item.brand, item.imageUrl))
            }
        }

        // Si tenemos suficientes resultados locales relevantes (al menos 12), devolverlos
        if (localMatches.size >= 12) {
            return@withContext localMatches.map { item ->
                item.copy(imageUrl = RealFoodImageResolver.resolve(item.name, item.brand, item.imageUrl))
            }
        }

        // 3. Consulta remota en Open Food Facts para enriquecer la búsqueda con el catálogo oficial completo
        val remoteResults = if (category != FoodSourceCategory.BASICOS && trimmed.length >= 2) {
            queryOpenFoodFacts(trimmed, category)
        } else emptyList()

        val combined = (localMatches + remoteResults).distinctBy {
            normalize(it.name) to normalize(it.brand)
        }.sortedByDescending {
            calculateRelevance(it, normQuery, tokens)
        }.take(250)

        if (combined.isNotEmpty()) {
            return@withContext combined.map { item ->
                item.copy(imageUrl = RealFoodImageResolver.resolve(item.name, item.brand, item.imageUrl))
            }
        }

        // 4. Fallback de IA Nutricional garantizada: nunca devolver lista vacía para búsquedas de alimentos
        if (trimmed.length >= 2) {
            val aiEstimated = aiService.estimateFoodCatalogItem(trimmed, category)
            if (aiEstimated != null) {
                return@withContext listOf(aiEstimated)
            }
        }

        emptyList()
    }

    /**
     * Busca un alimento de forma directa e instantánea por código de barras o extraído de un código QR.
     * Busca primero en los catálogos locales (Mercadona, Día, Supercor, Carrefour) y si no existe consulta Open Food Facts.
     */
    suspend fun findFoodByBarcode(barcode: String): FoodCatalogItem? = withContext(Dispatchers.IO) {
        val trimmed = barcode.trim()
        val allFoods = builtInFoods + loadAssetFoods()
        val digitMatch = Regex("""\b(\d{8,14})\b""").find(trimmed)?.value ?: trimmed
        if (digitMatch.length in 8..14 && digitMatch.all { it.isDigit() }) {
            val localMatch = allFoods.firstOrNull { it.barcode == digitMatch }
            if (localMatch != null) {
                val resolvedImg = RealFoodImageResolver.resolve(localMatch.name, localMatch.brand, localMatch.imageUrl)
                return@withContext localMatch.copy(imageUrl = resolvedImg)
            }
            val offMatch = queryByBarcode(digitMatch)
            if (offMatch != null) {
                val resolvedImg = RealFoodImageResolver.resolve(offMatch.name, offMatch.brand, offMatch.imageUrl)
                return@withContext offMatch.copy(imageUrl = resolvedImg)
            }
        }
        null
    }

    private fun filterByCategory(items: List<FoodCatalogItem>, category: FoodSourceCategory): List<FoodCatalogItem> {
        return when (category) {
            FoodSourceCategory.TODOS -> items
            FoodSourceCategory.MERCADONA -> items.filter {
                val b = normalize(it.brand)
                val n = normalize(it.name)
                b.contains("mercadona") || b.contains("hacendado") || n.contains("hacendado")
            }
            FoodSourceCategory.CARREFOUR -> items.filter {
                val b = normalize(it.brand)
                val n = normalize(it.name)
                b.contains("carrefour") || n.contains("carrefour")
            }
            FoodSourceCategory.DIA -> items.filter {
                val b = normalize(it.brand)
                val n = normalize(it.name)
                b.contains("dia") || n.contains("dia")
            }
            FoodSourceCategory.ALCAMPO -> items.filter {
                val b = normalize(it.brand)
                val n = normalize(it.name)
                b.contains("alcampo") || b.contains("auchan") || n.contains("alcampo")
            }
            FoodSourceCategory.SUPERCOR -> items.filter {
                val b = normalize(it.brand)
                val n = normalize(it.name)
                b.contains("supercor") || b.contains("corte") || b.contains("hipercor") ||
                n.contains("supercor") || n.contains("corte")
            }
            FoodSourceCategory.BASICOS -> items.filter {
                it.isGeneric ||
                normalize(it.brand).contains("basico") ||
                normalize(it.brand).contains("generico") ||
                normalize(it.brand).contains("tradicional") ||
                normalize(it.brand).contains("bedca")
            }
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
     * Consulta REST de texto a Open Food Facts con soporte de marcas de supermercado (Día, Carrefour, Hacendado, etc.).
     */
    private fun queryOpenFoodFacts(query: String, category: FoodSourceCategory): List<FoodCatalogItem> {
        val results = mutableListOf<FoodCatalogItem>()
        val brandTag = when (category) {
            FoodSourceCategory.MERCADONA -> "hacendado"
            FoodSourceCategory.CARREFOUR -> "carrefour"
            FoodSourceCategory.DIA -> "dia"
            FoodSourceCategory.ALCAMPO -> "auchan"
            FoodSourceCategory.SUPERCOR -> "el corte ingles"
            else -> ""
        }

        val endpoints = mutableListOf<String>()
        val encodedQuery = URLEncoder.encode(query, "UTF-8")

        if (brandTag.isNotBlank()) {
            endpoints.add("https://es.openfoodfacts.net/cgi/search.pl?search_terms=$encodedQuery&tagtype_0=brands&tag_contains_0=contains&tag_0=$brandTag&action=process&json=1&page_size=40")
            endpoints.add("https://es.openfoodfacts.net/cgi/search.pl?search_terms=${URLEncoder.encode("$query $brandTag", "UTF-8")}&search_simple=1&action=process&json=1&page_size=35")
        } else {
            endpoints.add("https://es.openfoodfacts.net/cgi/search.pl?search_terms=$encodedQuery&search_simple=1&action=process&json=1&page_size=40")
            endpoints.add("https://world.openfoodfacts.net/cgi/search.pl?search_terms=$encodedQuery&search_simple=1&action=process&json=1&page_size=35")
        }

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
                    if (results.size >= 15) break
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fallo al consultar $urlString: ${e.message}")
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
        val nutriments = p.optJSONObject("nutriments")

        var kcal = 0f
        var protein = 0f
        var carbs = 0f
        var fat = 0f
        var alcohol = 0f

        if (nutriments != null) {
            kcal = nutriments.optDouble("energy-kcal_100g", -1.0).toFloat()
            if (kcal <= 0f) {
                kcal = nutriments.optDouble("energy-kcal", -1.0).toFloat()
            }
            if (kcal <= 0f) {
                kcal = nutriments.optDouble("energy-kcal_value", -1.0).toFloat()
            }
            if (kcal <= 0f) {
                val energyKj = nutriments.optDouble("energy_100g", nutriments.optDouble("energy-kj_100g", 0.0)).toFloat()
                if (energyKj > 0f) {
                    kcal = energyKj / 4.184f
                }
            }

            protein = nutriments.optDouble("proteins_100g", nutriments.optDouble("proteins", 0.0)).toFloat().coerceAtLeast(0f)
            carbs = nutriments.optDouble("carbohydrates_100g", nutriments.optDouble("carbohydrates", 0.0)).toFloat().coerceAtLeast(0f)
            fat = nutriments.optDouble("fat_100g", nutriments.optDouble("fat", 0.0)).toFloat().coerceAtLeast(0f)
            alcohol = nutriments.optDouble("alcohol_100g", nutriments.optDouble("alcohol", 0.0)).toFloat().coerceAtLeast(0f)

            if (kcal <= 0f && (protein > 0f || carbs > 0f || fat > 0f || alcohol > 0f)) {
                kcal = (protein * 4f) + (carbs * 4f) + (fat * 9f) + (alcohol * 7f)
            }
        }

        val code = p.optString("code").ifBlank { fallbackBarcode ?: "" }
        val servingStr = p.optString("serving_size").ifBlank { "100g" }

        // Extraer imagen del producto
        val rawImgUrl = p.optString("image_front_small_url")
            .ifBlank { p.optString("image_small_url") }
            .ifBlank { p.optString("image_front_url") }
            .ifBlank { p.optString("image_url") }
            .ifBlank {
                val selected = p.optJSONObject("selected_images")?.optJSONObject("front")?.optJSONObject("small")
                selected?.optString("es")?.ifBlank { selected.optString("en") } ?: ""
            }

        val cleanName = cleanProductName(name)
        val cleanBrand = cleanBrandName(brands)
        val resolvedImg = RealFoodImageResolver.resolve(cleanName, cleanBrand, rawImgUrl)

        // Inferencia nutricional para evitar comidas con 0 calorías
        val inf = inferMacrosIfZero(cleanName, kcal, protein, carbs, fat, alcohol)

        return FoodCatalogItem(
            id = "off_${code.ifBlank { cleanName.hashCode().toString() }}",
            name = cleanName,
            brand = cleanBrand,
            caloriesPer100g = inf.calories,
            proteinPer100g = inf.protein,
            carbsPer100g = inf.carbs,
            fatPer100g = inf.fat,
            servingSizeGrams = 100f,
            servingDescription = servingStr,
            barcode = code.takeIf { it.isNotBlank() },
            imageUrl = resolvedImg,
            isGeneric = false
        )
    }

    private fun cleanProductName(name: String): String {
        return name.trim().replaceFirstChar { it.uppercase() }
    }

    private fun cleanBrandName(brand: String): String {
        val first = brand.split(",").firstOrNull()?.trim() ?: brand.trim()
        return first.replaceFirstChar { it.uppercase() }
    }

    companion object {
        fun inferMacrosIfZero(
            name: String,
            rawKcal: Float,
            rawP: Float,
            rawC: Float,
            rawF: Float,
            rawAlcohol: Float = 0f
        ): QuadrupleCalories {
            val lower = name.lowercase(Locale.ROOT)

            // Agua mineral / soda / sifón: 0 kcal es legítimo
            if (lower.contains("agua mineral") || lower.contains("agua de soda") ||
                lower.contains("bezoya") || lower.contains("solan") || lower.contains("solán") ||
                lower.contains("font vella") || lower.contains("lanjaron") || lower.contains("lanjarón") ||
                (lower.startsWith("agua ") && !lower.contains("coco") && !lower.contains("sabor"))
            ) {
                return QuadrupleCalories(0f, 0f, 0f, 0f)
            }

            // Bebidas zero / refrescos zero
            if (lower.contains("zero") || lower.contains("0% azucar") || lower.contains("0% azúcar") || lower.contains("light")) {
                if (lower.contains("coca-cola") || lower.contains("cola") || lower.contains("fanta") ||
                    lower.contains("sprite") || lower.contains("pepsi") || lower.contains("gaseosa") ||
                    lower.contains("aquarius zero") || lower.contains("monster zero")
                ) {
                    return QuadrupleCalories(1f, 0f, 0.2f, 0f)
                }
            }

            // Si ya tiene calorías válidas (> 0) y macros
            if (rawKcal > 0f) {
                return QuadrupleCalories(rawKcal, rawP, rawC, rawF)
            }

            // Si no tiene calorías pero tiene macros:
            if (rawP > 0f || rawC > 0f || rawF > 0f || rawAlcohol > 0f) {
                val computed = (rawP * 4f) + (rawC * 4f) + (rawF * 9f) + (rawAlcohol * 7f)
                if (computed > 0f) {
                    return QuadrupleCalories(computed, rawP, rawC, rawF)
                }
            }

            // Inferencia contextual por categoría alimentaria:
            return when {
                // Cervezas (con y sin alcohol)
                lower.contains("cerveza") || lower.contains("shandy") || lower.contains("radler") ||
                lower.contains("mahou") || lower.contains("heineken") || lower.contains("estrella") ||
                lower.contains("san miguel") || lower.contains("cruzcampo") || lower.contains("steinburg") ||
                lower.contains("ramblers") || lower.contains("birra") -> {
                    when {
                        lower.contains("0,0") || lower.contains("0.0") || lower.contains("sin alcohol") || lower.contains("cero") ->
                            QuadrupleCalories(24f, 0.3f, 5.2f, 0f)
                        lower.contains("radler") || lower.contains("limon") || lower.contains("limón") ->
                            QuadrupleCalories(35f, 0.2f, 6.2f, 0f)
                        lower.contains("tostada") || lower.contains("especial") ->
                            QuadrupleCalories(48f, 0.5f, 3.8f, 0f)
                        else ->
                            QuadrupleCalories(43f, 0.5f, 3.5f, 0f)
                    }
                }

                // Vinos
                lower.contains("vino") ->
                    QuadrupleCalories(75f, 0.1f, 2.5f, 0f)

                // Cafés
                lower.contains("cafe") || lower.contains("café") -> {
                    when {
                        lower.contains("leche") -> QuadrupleCalories(55f, 3.2f, 5.0f, 2.4f)
                        lower.contains("cortado") -> QuadrupleCalories(38f, 2.2f, 3.5f, 1.6f)
                        else -> QuadrupleCalories(2f, 0.2f, 0.3f, 0f) // Café solo espresso o americano
                    }
                }

                // Leche
                lower.contains("leche") || lower.contains("lactea") || lower.contains("láctea") -> {
                    when {
                        lower.contains("semi") -> QuadrupleCalories(46f, 3.2f, 4.8f, 1.6f)
                        lower.contains("desnatada") || lower.contains("0%") -> QuadrupleCalories(35f, 3.3f, 4.9f, 0.2f)
                        lower.contains("entera") -> QuadrupleCalories(63f, 3.1f, 4.7f, 3.6f)
                        else -> QuadrupleCalories(46f, 3.2f, 4.8f, 1.6f)
                    }
                }

                // Salsas
                lower.contains("salsa") || lower.contains("mayonesa") || lower.contains("alioli") ||
                lower.contains("allioli") || lower.contains("ketchup") || lower.contains("kétchup") || lower.contains("mostaza") -> {
                    when {
                        lower.contains("mayonesa") || lower.contains("alioli") || lower.contains("allioli") ->
                            QuadrupleCalories(645f, 1.1f, 2.5f, 71f)
                        lower.contains("barbacoa") ->
                            QuadrupleCalories(135f, 1.2f, 30f, 0.4f)
                        lower.contains("burger") ->
                            QuadrupleCalories(290f, 1.1f, 12f, 26f)
                        lower.contains("cesar") || lower.contains("césar") ->
                            QuadrupleCalories(295f, 1.5f, 6.5f, 30f)
                        lower.contains("pesto") ->
                            QuadrupleCalories(460f, 5.2f, 6.0f, 47f)
                        lower.contains("tomate") ->
                            QuadrupleCalories(65f, 1.8f, 7.5f, 3.0f)
                        lower.contains("soja") ->
                            QuadrupleCalories(65f, 8.5f, 7.5f, 0.1f)
                        lower.contains("ketchup") || lower.contains("kétchup") ->
                            QuadrupleCalories(110f, 1.2f, 25f, 0.2f)
                        lower.contains("mostaza") ->
                            QuadrupleCalories(90f, 5.0f, 5.0f, 5.0f)
                        else ->
                            QuadrupleCalories(150f, 1.5f, 12f, 10.5f)
                    }
                }

                // Salmón
                lower.contains("salmon") || lower.contains("salmón") -> {
                    if (lower.contains("ahumado") || lower.contains("marinado") || lower.contains("taquitos")) {
                        QuadrupleCalories(192f, 22f, 0.5f, 11.5f)
                    } else {
                        QuadrupleCalories(208f, 20f, 0f, 13.5f)
                    }
                }

                // Ensaladilla
                lower.contains("ensaladilla") ->
                    QuadrupleCalories(185f, 4.5f, 14f, 12f)

                // Frutos secos
                lower.contains("pistacho") || lower.contains("cacahuete") || lower.contains("almendra") ||
                lower.contains("nuez") || lower.contains("anacardo") || lower.contains("pipas") || lower.contains("piñones") ->
                    QuadrupleCalories(600f, 20f, 15f, 50f)

                // Panes
                lower.contains("pan") || lower.contains("tostada") || lower.contains("bocadillo") ->
                    QuadrupleCalories(250f, 9f, 48f, 2f)

                // Yogures
                lower.contains("yogur") || lower.contains("yogurt") ->
                    QuadrupleCalories(65f, 4.5f, 4.8f, 3.2f)

                // Carnes
                lower.contains("pollo") || lower.contains("pavo") ->
                    QuadrupleCalories(120f, 22f, 0f, 3.5f)
                lower.contains("ternera") || lower.contains("carne") || lower.contains("hamburguesa") ->
                    QuadrupleCalories(210f, 20f, 0.5f, 14f)

                // Arroz / Pasta
                lower.contains("arroz") || lower.contains("pasta") || lower.contains("macarron") || lower.contains("espagueti") ->
                    QuadrupleCalories(135f, 3.5f, 28f, 0.8f)

                // Frutas
                lower.contains("manzana") || lower.contains("platano") || lower.contains("plátano") || lower.contains("naranja") ->
                    QuadrupleCalories(60f, 0.8f, 14f, 0.2f)

                // Verduras
                lower.contains("ensalada") || lower.contains("tomate") || lower.contains("lechuga") || lower.contains("brocoli") ->
                    QuadrupleCalories(25f, 1.5f, 4.0f, 0.3f)

                // Default
                else ->
                    QuadrupleCalories(50f, 2f, 8f, 1f)
            }
        }
    }
}
