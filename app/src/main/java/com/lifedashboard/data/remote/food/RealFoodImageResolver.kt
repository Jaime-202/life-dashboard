package com.lifedashboard.data.remote.food

import java.util.Locale

/**
 * Resuelve imágenes reales de alimentos y productos españoles (especialmente Mercadona/Hacendado
 * y platos genéricos de la dieta mediterránea), y provee fallbacks de emojis ultra-precisos.
 */
object RealFoodImageResolver {

    /**
     * Mapeo de productos conocidos de Mercadona / Hacendado a fotos reales de su envase.
     */
    private val hacendadoImages = mapOf(
        "pan fibra y sesamo" to "https://images.openfoodfacts.net/images/products/871/044/502/0878/front_es.88.200.jpg",
        "pan fibra y sésamo" to "https://images.openfoodfacts.net/images/products/871/044/502/0878/front_es.88.200.jpg",
        "pan de molde 100% integral" to "https://images.openfoodfacts.net/images/products/848/000/082/3850/front_es.18.200.jpg",
        "pan tostado 100% integral" to "https://images.openfoodfacts.net/images/products/848/000/082/6127/front_es.25.200.jpg",
        "pechuga de pavo" to "https://images.openfoodfacts.net/images/products/848/000/059/1179/front_es.33.200.jpg",
        "queso fresco batido" to "https://images.openfoodfacts.net/images/products/848/000/015/6583/front_es.36.200.jpg",
        "hummus" to "https://images.openfoodfacts.net/images/products/848/000/026/0402/front_es.61.200.jpg",
        "copos de avena" to "https://images.openfoodfacts.net/images/products/848/000/009/4816/front_es.29.200.jpg",
        "clara de huevo" to "https://images.openfoodfacts.net/images/products/848/000/012/6531/front_es.34.200.jpg",
        "claras de huevo" to "https://images.openfoodfacts.net/images/products/848/000/012/6531/front_es.34.200.jpg",
        "atun claro al natural" to "https://images.openfoodfacts.net/images/products/848/000/018/1417/front_es.33.200.jpg",
        "atún claro al natural" to "https://images.openfoodfacts.net/images/products/848/000/018/1417/front_es.33.200.jpg",
        "guacamole" to "https://images.openfoodfacts.net/images/products/848/000/026/0419/front_es.39.200.jpg",
        "aceite de oliva virgen extra" to "https://images.openfoodfacts.net/images/products/848/000/021/4139/front_es.16.200.jpg",
        "crema de cacahuete" to "https://images.openfoodfacts.net/images/products/848/000/021/6881/front_es.35.200.jpg",
        "yogur griego natural" to "https://images.openfoodfacts.net/images/products/848/000/015/4565/front_es.25.200.jpg",
        "mousse de proteinas" to "https://images.openfoodfacts.net/images/products/848/000/015/8860/front_es.32.200.jpg",
        "mousse de proteínas" to "https://images.openfoodfacts.net/images/products/848/000/015/8860/front_es.32.200.jpg",
        "tofu firme" to "https://images.openfoodfacts.net/images/products/848/000/016/5288/front_es.16.200.jpg",
        "tortitas de maiz" to "https://images.openfoodfacts.net/images/products/848/000/082/2853/front_es.14.200.jpg",
        "tortitas de maíz" to "https://images.openfoodfacts.net/images/products/848/000/082/2853/front_es.14.200.jpg",
        "edamame" to "https://images.openfoodfacts.net/images/products/848/000/016/4830/front_es.25.200.jpg",
        "jamon serrano" to "https://images.openfoodfacts.net/images/products/848/000/051/5243/front_es.20.200.jpg",
        "jamón serrano" to "https://images.openfoodfacts.net/images/products/848/000/051/5243/front_es.20.200.jpg",
        "salmon ahumado" to "https://images.openfoodfacts.net/images/products/848/000/018/6955/front_es.17.200.jpg",
        "salmón ahumado" to "https://images.openfoodfacts.net/images/products/848/000/018/6955/front_es.17.200.jpg",
        "bebida de soja 0%" to "https://images.openfoodfacts.net/images/products/848/000/011/4986/front_es.28.200.jpg",
        "leche desnatada" to "https://images.openfoodfacts.net/images/products/848/000/010/8254/front_es.30.200.jpg",
        "garbanzos cocidos" to "https://images.openfoodfacts.net/images/products/848/000/016/0856/front_es.24.200.jpg",
        "lentejas cocidas" to "https://images.openfoodfacts.net/images/products/848/000/016/0863/front_es.22.200.jpg",
        "tomate triturado" to "https://images.openfoodfacts.net/images/products/848/000/016/2645/front_es.27.200.jpg"
    )

    /**
     * Mapeo de comidas y platos genéricos a fotos reales de alta calidad gastronómica.
     */
    private val genericFoodImages = listOf(
        // Carnes & Aves
        listOf("pollo", "pechuga") to "https://images.unsplash.com/photo-1604503468506-a8da13d82791?w=200&q=80",
        listOf("pavo") to "https://images.unsplash.com/photo-1518492104633-130d0cc84637?w=200&q=80",
        listOf("ternera", "filete") to "https://images.unsplash.com/photo-1544025162-d76694265947?w=200&q=80",
        listOf("carne picada") to "https://images.unsplash.com/photo-1588168333986-5078d3ae3976?w=200&q=80",
        listOf("hamburguesa") to "https://images.unsplash.com/photo-1568901346375-23c9450c58cd?w=200&q=80",
        listOf("jamon", "jamón") to "https://images.unsplash.com/photo-1529692236671-f1f6cf9683ba?w=200&q=80",
        listOf("lomo", "cerdo") to "https://images.unsplash.com/photo-1432139555190-58524dae6a55?w=200&q=80",

        // Pescados & Mariscos
        listOf("salmon", "salmón") to "https://images.unsplash.com/photo-1467003909585-2f8a72700288?w=200&q=80",
        listOf("atun", "atún") to "https://images.unsplash.com/photo-1501595091296-3aa970afb3ff?w=200&q=80",
        listOf("merluza", "pescado") to "https://images.unsplash.com/photo-1519708227418-c8fd9a32b7a2?w=200&q=80",
        listOf("gamba", "langostino", "marisco") to "https://images.unsplash.com/photo-1565680018434-b513d5e5fd47?w=200&q=80",

        // Huevos & Tortillas
        listOf("tortilla de patata", "tortilla española") to "https://images.unsplash.com/photo-1584776296944-ab6fb57b0bdd?w=200&q=80",
        listOf("tortilla francesa", "tortilla") to "https://images.unsplash.com/photo-1525351484163-7529414344d8?w=200&q=80",
        listOf("huevo", "huevos") to "https://images.unsplash.com/photo-1587486913049-53fc88980cfc?w=200&q=80",

        // Arroces & Pastas
        listOf("paella") to "https://images.unsplash.com/photo-1534080564583-6be75777b70a?w=200&q=80",
        listOf("arroz") to "https://images.unsplash.com/photo-1516684732162-798a0062be99?w=200&q=80",
        listOf("pasta", "espagueti", "macarron", "macarrón") to "https://images.unsplash.com/photo-1551183053-bf91a1d81141?w=200&q=80",
        listOf("pizza") to "https://images.unsplash.com/photo-1513104890138-7c749659a591?w=200&q=80",

        // Legumbres & Guisos
        listOf("lenteja", "lentejas") to "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=200&q=80",
        listOf("garbanzo", "garbanzos", "hummus") to "https://images.unsplash.com/photo-1505576399279-565b52d4ac71?w=200&q=80",
        listOf("alubia", "alubias", "judia", "judías") to "https://images.unsplash.com/photo-1584278860047-22db9ff82bed?w=200&q=80",

        // Desayuno, Panes & Avena
        listOf("avena", "porridge") to "https://images.unsplash.com/photo-1586495777744-4413f21062fa?w=200&q=80",
        listOf("tostada", "pan") to "https://images.unsplash.com/photo-1509440159596-0249088772ff?w=200&q=80",
        listOf("croissant", "bollo") to "https://images.unsplash.com/photo-1555507036-ab1f4038808a?w=200&q=80",
        listOf("cafe", "café") to "https://images.unsplash.com/photo-1509042239860-f550ce710b93?w=200&q=80",

        // Patatas & Verduras
        listOf("patata", "patatas") to "https://images.unsplash.com/photo-1518977676601-b53f82aba655?w=200&q=80",
        listOf("ensalada", "lechuga") to "https://images.unsplash.com/photo-1512621776951-a57141f2eefd?w=200&q=80",
        listOf("gazpacho", "salmorejo") to "https://images.unsplash.com/photo-1547496502-affa22d38842?w=200&q=80",
        listOf("tomate") to "https://images.unsplash.com/photo-1546470427-227c7369a92a?w=200&q=80",
        listOf("brocoli", "brócoli") to "https://images.unsplash.com/photo-1459411621453-7b03977f4bfc?w=200&q=80",
        listOf("aguacate", "guacamole") to "https://images.unsplash.com/photo-1523049673857-eb18f1d7b578?w=200&q=80",

        // Frutas
        listOf("platano", "plátano") to "https://images.unsplash.com/photo-1571771894821-ce9b6c11b08e?w=200&q=80",
        listOf("manzana") to "https://images.unsplash.com/photo-1560806887-1e4cd0b6cbd6?w=200&q=80",
        listOf("naranja", "mandarina") to "https://images.unsplash.com/photo-1547514701-42782101795e?w=200&q=80",
        listOf("fresa", "fresas") to "https://images.unsplash.com/photo-1464965911861-746a04b4bca6?w=200&q=80",
        listOf("sandia", "sandía") to "https://images.unsplash.com/photo-1587049352846-4a222e784d38?w=200&q=80",

        // Lácteos & Grasas
        listOf("yogur") to "https://images.unsplash.com/photo-1488477181946-6428a0291777?w=200&q=80",
        listOf("leche") to "https://images.unsplash.com/photo-1550583724-b2692b85b150?w=200&q=80",
        listOf("queso") to "https://images.unsplash.com/photo-1486297678162-eb2a19b0a32d?w=200&q=80",
        listOf("aceite", "oliva") to "https://images.unsplash.com/photo-1474979266404-7eaacbcd87c5?w=200&q=80",
        listOf("nuez", "nueces", "almendra", "cacahuete") to "https://images.unsplash.com/photo-1541832676-9b763b0239ab?w=200&q=80",
        listOf("chocolate") to "https://images.unsplash.com/photo-1511381939415-e44015466834?w=200&q=80"
    )

    /**
     * Resuelve la URL de la imagen real para un producto o alimento:
     * 1. Si ya tiene imageUrl, la devuelve sanitizada (https).
     * 2. Si es de Mercadona/Hacendado, busca en el catálogo de envases reales de Hacendado.
     * 3. Si es genérico o cualquier otro, busca una foto real de alta calidad de ese plato/ingrediente.
     */
    fun resolve(name: String, brand: String? = null, providedUrl: String? = null): String? {
        if (!providedUrl.isNullOrBlank()) {
            return providedUrl.replace("http://", "https://")
        }

        val nameLower = name.lowercase(Locale.ROOT).trim()
        val brandLower = (brand ?: "").lowercase(Locale.ROOT).trim()
        val isMercadona = brandLower.contains("mercadona") || brandLower.contains("hacendado") || nameLower.contains("hacendado")

        // 1. Búsqueda en catálogo Hacendado
        if (isMercadona) {
            for ((key, url) in hacendadoImages) {
                if (nameLower.contains(key)) {
                    return url
                }
            }
        }

        // 2. Búsqueda en fotos gastronómicas reales para genéricos y caseros
        for ((tokens, url) in genericFoodImages) {
            if (tokens.any { token -> nameLower.contains(token) }) {
                return url
            }
        }

        return null
    }

    /**
     * Provee un emoji hiper-preciso en caso de que la imagen aún esté cargando o falle la conexión.
     */
    fun getAccurateEmoji(name: String): String {
        val lower = name.lowercase(Locale.ROOT)
        return when {
            // Huevos y tortillas
            lower.contains("tortilla de patata") || lower.contains("tortilla espanola") || lower.contains("tortilla española") -> "🥘"
            lower.contains("tortilla francesa") || lower.contains("tortilla") -> "🍳"
            lower.contains("huevo frito") -> "🍳"
            lower.contains("huevo") || lower.contains("claras") -> "🥚"

            // Carnes y Aves
            lower.contains("pollo") || lower.contains("pechuga") || lower.contains("pavo") || lower.contains("alitas") -> "🍗"
            lower.contains("hamburguesa") || lower.contains("burger") -> "🍔"
            lower.contains("jamon") || lower.contains("jamón") || lower.contains("bacon") || lower.contains("panceta") || lower.contains("embutido") || lower.contains("lomo") -> "🥓"
            lower.contains("ternera") || lower.contains("filete") || lower.contains("chuleta") || lower.contains("carne") || lower.contains("buey") -> "🥩"

            // Pescados y Mariscos
            lower.contains("atun") || lower.contains("atún") || lower.contains("bonito") -> "🐟"
            lower.contains("pescado") || lower.contains("salmon") || lower.contains("salmón") || lower.contains("merluza") || lower.contains("bacalao") || lower.contains("dorada") || lower.contains("lubina") -> "🐟"
            lower.contains("gamba") || lower.contains("langostino") || lower.contains("marisco") -> "🦐"
            lower.contains("calamar") || lower.contains("pulpo") -> "🐙"

            // Legumbres, Guisos y Sopas
            lower.contains("lenteja") || lower.contains("lentejas") || lower.contains("garbanzo") || lower.contains("garbanzos") || lower.contains("alubia") || lower.contains("judia") || lower.contains("judía") || lower.contains("cocido") || lower.contains("guiso") || lower.contains("puchero") -> "🍲"
            lower.contains("sopa") || lower.contains("caldo") || lower.contains("crema") || lower.contains("pure") || lower.contains("puré") || lower.contains("gazpacho") || lower.contains("salmorejo") -> "🥣"

            // Pastas, Arroces y Masas
            lower.contains("paella") || lower.contains("arroz") -> "🍚"
            lower.contains("pizza") -> "🍕"
            lower.contains("pasta") || lower.contains("espagueti") || lower.contains("macarron") || lower.contains("macarrón") || lower.contains("lasaña") || lower.contains("fideo") -> "🍝"

            // Patatas y Snacks
            lower.contains("patatas fritas") || lower.contains("papas fritas") -> "🍟"
            lower.contains("patata") || lower.contains("papas") -> "🥔"

            // Panadería & Desayuno
            lower.contains("croissant") || lower.contains("bollo") || lower.contains("magdalena") || lower.contains("bizcocho") -> "🥐"
            lower.contains("tostad") || lower.contains("biscote") -> "🍞"
            lower.contains("pan") || lower.contains("baguette") || lower.contains("hogaza") || lower.contains("bocadillo") || lower.contains("sandwich") -> "🥖"
            lower.contains("avena") || lower.contains("cereal") || lower.contains("muesli") || lower.contains("porridge") -> "🥣"

            // Lácteos
            lower.contains("queso") -> "🧀"
            lower.contains("yogur") || lower.contains("kefir") || lower.contains("kéfir") -> "🥛"
            lower.contains("leche") -> "🥛"

            // Frutas
            lower.contains("platano") || lower.contains("plátano") -> "🍌"
            lower.contains("manzana") -> "🍎"
            lower.contains("naranja") || lower.contains("mandarina") -> "🍊"
            lower.contains("fresa") || lower.contains("arandano") || lower.contains("arándano") || lower.contains("frambuesa") -> "🍓"
            lower.contains("limon") || lower.contains("limón") -> "🍋"
            lower.contains("pera") -> "🍐"
            lower.contains("sandia") || lower.contains("sandía") -> "🍉"
            lower.contains("melon") || lower.contains("melón") -> "🍈"
            lower.contains("uva") || lower.contains("uvas") -> "🍇"
            lower.contains("aguacate") || lower.contains("guacamole") -> "🥑"

            // Verduras y Ensaladas
            lower.contains("ensalada") || lower.contains("lechuga") || lower.contains("espinaca") || lower.contains("acelga") -> "🥗"
            lower.contains("tomate") -> "🍅"
            lower.contains("zanahoria") -> "🥕"
            lower.contains("brocoli") || lower.contains("brócoli") -> "🥦"
            lower.contains("champiñon") || lower.contains("champiñón") || lower.contains("seta") -> "🍄"
            lower.contains("cebolla") || lower.contains("ajo") -> "🧅"

            // Grasas, Frutos Secos & Dulces
            lower.contains("aceite") || lower.contains("oliva") || lower.contains("aceituna") -> "🫒"
            lower.contains("nuez") || lower.contains("nueces") || lower.contains("almendra") || lower.contains("cacahuete") || lower.contains("pistacho") || lower.contains("anacardo") -> "🥜"
            lower.contains("chocolate") || lower.contains("cacao") -> "🍫"
            lower.contains("galleta") -> "🍪"

            // Bebidas
            lower.contains("cafe") || lower.contains("café") -> "☕"
            lower.contains("te") || lower.contains("té") || lower.contains("infusion") || lower.contains("infusión") -> "🍵"
            lower.contains("agua") -> "💧"
            lower.contains("zumo") || lower.contains("refresco") || lower.contains("bebida") -> "🥤"
            lower.contains("cerveza") -> "🍺"
            lower.contains("vino") -> "🍷"

            else -> "🍽️"
        }
    }
}
