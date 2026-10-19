package com.lifedashboard.data.remote.food

import java.util.Locale

/**
 * Resuelve imágenes reales de alimentos y productos españoles (especialmente Mercadona/Hacendado
 * y platos de la dieta mediterránea con URLs verificadas 200 OK), y provee fallbacks de emojis ultra-precisos.
 */
object RealFoodImageResolver {

    /**
     * Mapeo de productos conocidos de Mercadona / Hacendado a fotos reales gastronómicas y de alta fidelidad.
     */
    private val hacendadoImages = mapOf(
        // Panes y Tostadas
        "pan fibra y sesamo" to "https://images.unsplash.com/photo-1509440159596-0249088772ff?w=400&q=80",
        "pan fibra y sésamo" to "https://images.unsplash.com/photo-1509440159596-0249088772ff?w=400&q=80",
        "pan de molde 100% integral" to "https://images.unsplash.com/photo-1589367920969-ab8e050bbb04?w=400&q=80",
        "pan de molde blanco" to "https://images.unsplash.com/photo-1589367920969-ab8e050bbb04?w=400&q=80",
        "pan tostado 100% integral" to "https://images.unsplash.com/photo-1509440159596-0249088772ff?w=400&q=80",
        "pan tostado" to "https://images.unsplash.com/photo-1509440159596-0249088772ff?w=400&q=80",
        "barra de pan" to "https://images.unsplash.com/photo-1509440159596-0249088772ff?w=400&q=80",
        "reganas" to "https://images.unsplash.com/photo-1509440159596-0249088772ff?w=400&q=80",
        "regañás" to "https://images.unsplash.com/photo-1509440159596-0249088772ff?w=400&q=80",
        "picos camperos" to "https://images.unsplash.com/photo-1509440159596-0249088772ff?w=400&q=80",
        "pan pita" to "https://images.unsplash.com/photo-1509440159596-0249088772ff?w=400&q=80",
        "pan cristal" to "https://images.unsplash.com/photo-1509440159596-0249088772ff?w=400&q=80",
        "pan hamburguesa" to "https://images.unsplash.com/photo-1568901346375-23c9450c58cd?w=400&q=80",

        // Carnes, Aves y Embutidos
        "pechuga de pavo" to "https://images.unsplash.com/photo-1518492104633-130d0cc84637?w=400&q=80",
        "pechuga de pollo" to "https://images.unsplash.com/photo-1604503468506-a8da13d82791?w=400&q=80",
        "pollo" to "https://images.unsplash.com/photo-1604503468506-a8da13d82791?w=400&q=80",
        "lomo embuchado" to "https://images.unsplash.com/photo-1529692236671-f1f6cf9683ba?w=400&q=80",
        "jamon serrano" to "https://images.unsplash.com/photo-1529692236671-f1f6cf9683ba?w=400&q=80",
        "jamón serrano" to "https://images.unsplash.com/photo-1529692236671-f1f6cf9683ba?w=400&q=80",
        "jamon cocido" to "https://images.unsplash.com/photo-1529692236671-f1f6cf9683ba?w=400&q=80",
        "jamón cocido" to "https://images.unsplash.com/photo-1529692236671-f1f6cf9683ba?w=400&q=80",
        "hamburguesa" to "https://images.unsplash.com/photo-1568901346375-23c9450c58cd?w=400&q=80",
        "filete de ternera" to "https://images.unsplash.com/photo-1544025162-d76694265947?w=400&q=80",

        // Pescados y Mariscos
        "atun claro al natural" to "https://images.unsplash.com/photo-1501595091296-3aa970afb3ff?w=400&q=80",
        "atún claro al natural" to "https://images.unsplash.com/photo-1501595091296-3aa970afb3ff?w=400&q=80",
        "atun" to "https://images.unsplash.com/photo-1501595091296-3aa970afb3ff?w=400&q=80",
        "atún" to "https://images.unsplash.com/photo-1501595091296-3aa970afb3ff?w=400&q=80",
        "salmon ahumado" to "https://images.unsplash.com/photo-1467003909585-2f8a72700288?w=400&q=80",
        "salmón ahumado" to "https://images.unsplash.com/photo-1467003909585-2f8a72700288?w=400&q=80",
        "salmon" to "https://images.unsplash.com/photo-1467003909585-2f8a72700288?w=400&q=80",
        "salmón" to "https://images.unsplash.com/photo-1467003909585-2f8a72700288?w=400&q=80",
        "merluza" to "https://images.unsplash.com/photo-1519708227418-c8fd9a32b7a2?w=400&q=80",
        "langostinos" to "https://images.unsplash.com/photo-1565680018434-b513d5e5fd47?w=400&q=80",

        // Lácteos, Huevos y Bebidas
        "leche desnatada" to "https://images.unsplash.com/photo-1550583724-b2692b85b150?w=400&q=80",
        "leche semidesnatada" to "https://images.unsplash.com/photo-1550583724-b2692b85b150?w=400&q=80",
        "leche entera" to "https://images.unsplash.com/photo-1550583724-b2692b85b150?w=400&q=80",
        "leche sin lactosa" to "https://images.unsplash.com/photo-1550583724-b2692b85b150?w=400&q=80",
        "queso fresco batido" to "https://images.unsplash.com/photo-1488477181946-6428a0291777?w=400&q=80",
        "yogur griego natural" to "https://images.unsplash.com/photo-1505253716362-afaea1d3d1af?w=400&q=80",
        "yogur natural" to "https://images.unsplash.com/photo-1488477181946-6428a0291777?w=400&q=80",
        "mousse de proteinas" to "https://images.unsplash.com/photo-1511381939415-e44015466834?w=400&q=80",
        "mousse de proteínas" to "https://images.unsplash.com/photo-1511381939415-e44015466834?w=400&q=80",
        "proteinas" to "https://images.unsplash.com/photo-1488477181946-6428a0291777?w=400&q=80",
        "kefir" to "https://images.unsplash.com/photo-1550583724-b2692b85b150?w=400&q=80",
        "kéfir" to "https://images.unsplash.com/photo-1550583724-b2692b85b150?w=400&q=80",
        "queso cottage" to "https://images.unsplash.com/photo-1486297678162-eb2a19b0a32d?w=400&q=80",
        "queso semicurado" to "https://images.unsplash.com/photo-1486297678162-eb2a19b0a32d?w=400&q=80",
        "queso rallado" to "https://images.unsplash.com/photo-1486297678162-eb2a19b0a32d?w=400&q=80",
        "mozzarella" to "https://images.unsplash.com/photo-1486297678162-eb2a19b0a32d?w=400&q=80",
        "clara de huevo" to "https://images.unsplash.com/photo-1587486913049-53fc88980cfc?w=400&q=80",
        "claras de huevo" to "https://images.unsplash.com/photo-1587486913049-53fc88980cfc?w=400&q=80",
        "huevos" to "https://images.unsplash.com/photo-1587486913049-53fc88980cfc?w=400&q=80",
        "bebida de soja" to "https://images.unsplash.com/photo-1550583724-b2692b85b150?w=400&q=80",
        "bebida de avena" to "https://images.unsplash.com/photo-1550583724-b2692b85b150?w=400&q=80",
        "tofu firme" to "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=400&q=80",

        // Cereales, Pastas y Legumbres
        "copos de avena" to "https://images.unsplash.com/photo-1586495777744-4413f21062fa?w=400&q=80",
        "avena" to "https://images.unsplash.com/photo-1586495777744-4413f21062fa?w=400&q=80",
        "arroz redondo" to "https://images.unsplash.com/photo-1516684732162-798a0062be99?w=400&q=80",
        "arroz basmati" to "https://images.unsplash.com/photo-1516684732162-798a0062be99?w=400&q=80",
        "arroz integral" to "https://images.unsplash.com/photo-1516684732162-798a0062be99?w=400&q=80",
        "garbanzos cocidos" to "https://images.unsplash.com/photo-1505576399279-565b52d4ac71?w=400&q=80",
        "lentejas cocidas" to "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=400&q=80",
        "alubias cocidas" to "https://images.unsplash.com/photo-1584278860047-22db9ff82bed?w=400&q=80",
        "espaguetis" to "https://images.unsplash.com/photo-1551183053-bf91a1d81141?w=400&q=80",
        "macarrones" to "https://images.unsplash.com/photo-1551183053-bf91a1d81141?w=400&q=80",
        "quinoa" to "https://images.unsplash.com/photo-1586495777744-4413f21062fa?w=400&q=80",
        "tortitas de maiz" to "https://images.unsplash.com/photo-1599490659213-e2b9527bd087?w=400&q=80",
        "tortitas de maíz" to "https://images.unsplash.com/photo-1599490659213-e2b9527bd087?w=400&q=80",
        "tortitas de arroz" to "https://images.unsplash.com/photo-1599490659213-e2b9527bd087?w=400&q=80",

        // Preparados, Salsas y Grasas
        "hummus" to "https://images.unsplash.com/photo-1577906096429-f73c2c312435?w=400&q=80",
        "guacamole" to "https://images.unsplash.com/photo-1523049673857-eb18f1d7b578?w=400&q=80",
        "gazpacho" to "https://images.unsplash.com/photo-1547496502-affa22d38842?w=400&q=80",
        "salmorejo" to "https://images.unsplash.com/photo-1547496502-affa22d38842?w=400&q=80",
        "tortilla de patata" to "https://images.unsplash.com/photo-1584776296944-ab6fb57b0bdd?w=400&q=80",
        "ensaladilla rusa" to "https://images.unsplash.com/photo-1512621776951-a57141f2eefd?w=400&q=80",
        "pizza" to "https://images.unsplash.com/photo-1513104890138-7c749659a591?w=400&q=80",
        "aceite de oliva virgen extra" to "https://images.unsplash.com/photo-1474979266404-7eaacbcd87c5?w=400&q=80",
        "crema de cacahuete" to "https://images.unsplash.com/photo-1590080875515-8a3a8dc5735e?w=400&q=80",
        "tomate triturado" to "https://images.unsplash.com/photo-1592924357228-91a4daadcfea?w=400&q=80",
        "tomate frito" to "https://images.unsplash.com/photo-1592924357228-91a4daadcfea?w=400&q=80",
        "chocolate negro" to "https://images.unsplash.com/photo-1511381939415-e44015466834?w=400&q=80",
        "nueces" to "https://images.unsplash.com/photo-1541832676-9b763b0239ab?w=400&q=80",
        "almendras" to "https://images.unsplash.com/photo-1541832676-9b763b0239ab?w=400&q=80",
        "cafe" to "https://images.unsplash.com/photo-1509042239860-f550ce710b93?w=400&q=80",
        "café" to "https://images.unsplash.com/photo-1509042239860-f550ce710b93?w=400&q=80"
    )

    /**
     * Mapeo de comidas y platos genéricos a fotos reales de alta calidad gastronómica verificadas.
     */
    private val genericFoodImages = listOf(
        // Carnes & Aves
        listOf("pollo", "pechuga") to "https://images.unsplash.com/photo-1604503468506-a8da13d82791?w=400&q=80",
        listOf("pavo") to "https://images.unsplash.com/photo-1518492104633-130d0cc84637?w=400&q=80",
        listOf("ternera", "filete") to "https://images.unsplash.com/photo-1544025162-d76694265947?w=400&q=80",
        listOf("carne picada") to "https://images.unsplash.com/photo-1588168333986-5078d3ae3976?w=400&q=80",
        listOf("hamburguesa") to "https://images.unsplash.com/photo-1568901346375-23c9450c58cd?w=400&q=80",
        listOf("jamon", "jamón") to "https://images.unsplash.com/photo-1529692236671-f1f6cf9683ba?w=400&q=80",
        listOf("lomo", "cerdo") to "https://images.unsplash.com/photo-1432139555190-58524dae6a55?w=400&q=80",

        // Pescados & Mariscos
        listOf("salmon", "salmón") to "https://images.unsplash.com/photo-1467003909585-2f8a72700288?w=400&q=80",
        listOf("atun", "atún", "bonito") to "https://images.unsplash.com/photo-1501595091296-3aa970afb3ff?w=400&q=80",
        listOf("merluza", "pescado", "dorada", "lubina", "bacalao") to "https://images.unsplash.com/photo-1519708227418-c8fd9a32b7a2?w=400&q=80",
        listOf("gamba", "langostino", "marisco", "calamar", "pulpo") to "https://images.unsplash.com/photo-1565680018434-b513d5e5fd47?w=400&q=80",

        // Huevos & Tortillas
        listOf("tortilla de patata", "tortilla española", "tortilla espanola") to "https://images.unsplash.com/photo-1584776296944-ab6fb57b0bdd?w=400&q=80",
        listOf("tortilla francesa", "tortilla") to "https://images.unsplash.com/photo-1510693206972-df098062cb71?w=400&q=80",
        listOf("huevo frito") to "https://images.unsplash.com/photo-1525351484163-7529414344d8?w=400&q=80",
        listOf("huevo", "huevos", "claras") to "https://images.unsplash.com/photo-1587486913049-53fc88980cfc?w=400&q=80",

        // Arroces & Pastas
        listOf("paella") to "https://images.unsplash.com/photo-1534080564583-6be75777b70a?w=400&q=80",
        listOf("arroz") to "https://images.unsplash.com/photo-1516684732162-798a0062be99?w=400&q=80",
        listOf("pasta", "espagueti", "macarron", "macarrón", "lasaña") to "https://images.unsplash.com/photo-1551183053-bf91a1d81141?w=400&q=80",
        listOf("pizza") to "https://images.unsplash.com/photo-1513104890138-7c749659a591?w=400&q=80",

        // Legumbres & Guisos
        listOf("lenteja", "lentejas") to "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=400&q=80",
        listOf("garbanzo", "garbanzos") to "https://images.unsplash.com/photo-1505576399279-565b52d4ac71?w=400&q=80",
        listOf("hummus") to "https://images.unsplash.com/photo-1577906096429-f73c2c312435?w=400&q=80",
        listOf("alubia", "alubias", "judia", "judías") to "https://images.unsplash.com/photo-1584278860047-22db9ff82bed?w=400&q=80",

        // Desayuno, Panes & Avena
        listOf("avena", "porridge") to "https://images.unsplash.com/photo-1586495777744-4413f21062fa?w=400&q=80",
        listOf("pan de molde", "molde") to "https://images.unsplash.com/photo-1589367920969-ab8e050bbb04?w=400&q=80",
        listOf("tostada", "pan", "reganas", "biscote") to "https://images.unsplash.com/photo-1509440159596-0249088772ff?w=400&q=80",
        listOf("croissant", "bollo", "magdalena") to "https://images.unsplash.com/photo-1555507036-ab1f4038808a?w=400&q=80",
        listOf("cafe", "café") to "https://images.unsplash.com/photo-1509042239860-f550ce710b93?w=400&q=80",
        listOf("tortita", "tortitas") to "https://images.unsplash.com/photo-1599490659213-e2b9527bd087?w=400&q=80",

        // Patatas & Verduras
        listOf("patatas fritas", "papas fritas") to "https://images.unsplash.com/photo-1573080496219-bb080dd4f877?w=400&q=80",
        listOf("patata", "patatas", "pure") to "https://images.unsplash.com/photo-1518977676601-b53f82aba655?w=400&q=80",
        listOf("ensalada", "lechuga") to "https://images.unsplash.com/photo-1512621776951-a57141f2eefd?w=400&q=80",
        listOf("gazpacho", "salmorejo") to "https://images.unsplash.com/photo-1547496502-affa22d38842?w=400&q=80",
        listOf("tomate") to "https://images.unsplash.com/photo-1592924357228-91a4daadcfea?w=400&q=80",
        listOf("brocoli", "brócoli") to "https://images.unsplash.com/photo-1459411621453-7b03977f4bfc?w=400&q=80",
        listOf("aguacate", "guacamole") to "https://images.unsplash.com/photo-1523049673857-eb18f1d7b578?w=400&q=80",

        // Frutas
        listOf("platano", "plátano") to "https://images.unsplash.com/photo-1571771894821-ce9b6c11b08e?w=400&q=80",
        listOf("manzana") to "https://images.unsplash.com/photo-1560806887-1e4cd0b6cbd6?w=400&q=80",
        listOf("naranja", "mandarina") to "https://images.unsplash.com/photo-1547514701-42782101795e?w=400&q=80",
        listOf("fresa", "fresas") to "https://images.unsplash.com/photo-1464965911861-746a04b4bca6?w=400&q=80",
        listOf("sandia", "sandía", "melon", "melón") to "https://images.unsplash.com/photo-1587049352846-4a222e784d38?w=400&q=80",

        // Lácteos & Grasas
        listOf("queso fresco batido", "yogur griego") to "https://images.unsplash.com/photo-1505253716362-afaea1d3d1af?w=400&q=80",
        listOf("yogur") to "https://images.unsplash.com/photo-1488477181946-6428a0291777?w=400&q=80",
        listOf("leche") to "https://images.unsplash.com/photo-1550583724-b2692b85b150?w=400&q=80",
        listOf("queso") to "https://images.unsplash.com/photo-1486297678162-eb2a19b0a32d?w=400&q=80",
        listOf("aceite", "oliva") to "https://images.unsplash.com/photo-1474979266404-7eaacbcd87c5?w=400&q=80",
        listOf("crema de cacahuete", "mantequilla de cacahuete") to "https://images.unsplash.com/photo-1590080875515-8a3a8dc5735e?w=400&q=80",
        listOf("nuez", "nueces", "almendra", "cacahuete") to "https://images.unsplash.com/photo-1541832676-9b763b0239ab?w=400&q=80",
        listOf("chocolate", "cacao") to "https://images.unsplash.com/photo-1511381939415-e44015466834?w=400&q=80"
    )

    /**
     * Resuelve la URL de la imagen real para un producto o alimento:
     * 1. Si providedUrl existe y es válida (no es un enlace roto conocido de OpenFoodFacts), la limpia y usa.
     * 2. Si es de Mercadona/Hacendado, busca en el catálogo de fotos de productos.
     * 3. Si es genérico o cualquier otro, busca una foto culinaria de alta resolución probada.
     */
    fun resolve(name: String, brand: String? = null, providedUrl: String? = null): String? {
        if (!providedUrl.isNullOrBlank() && providedUrl.startsWith("http")) {
            val sanitized = providedUrl.replace("http://", "https://")
            // Aceptar fotos de Mercadona CDN o Unsplash directamente
            if (sanitized.contains("prod-mercadona.imgix.net") || sanitized.contains("images.unsplash.com")) {
                return sanitized
            }
            // Para Open Food Facts u otros CDN, aceptar si son válidos
            if (!sanitized.contains("error") && !sanitized.contains("not_found")) {
                return sanitized
            }
        }

        val nameLower = name.lowercase(Locale.ROOT).trim()
        val brandLower = (brand ?: "").lowercase(Locale.ROOT).trim()
        val isMercadona = brandLower.contains("mercadona") || brandLower.contains("hacendado") || nameLower.contains("hacendado")

        // 1. Catálogo específico de Hacendado / Mercadona
        if (isMercadona) {
            for ((key, url) in hacendadoImages) {
                if (nameLower.contains(key)) {
                    return url
                }
            }
        }

        // 2. Catálogo gastronómico real
        for ((tokens, url) in genericFoodImages) {
            if (tokens.any { token -> nameLower.contains(token) }) {
                return url
            }
        }

        return "https://images.unsplash.com/photo-1498837167922-ddd27525d352?w=400&q=80" // Plato saludable genérico
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
