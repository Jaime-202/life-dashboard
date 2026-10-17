# ⚡ Vital — Life Dashboard

> **Dashboard Integral de Salud, Nutrición Inteligente con IA, Hábitos y Peso para Android con Sincronización en Segundo Plano y Widgets Glassmorphic.**

[![Android](https://img.shields.io/badge/Platform-Android%209.0%2B-3DDC84.svg?style=flat&logo=android)](https://www.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF.svg?style=flat&logo=kotlin)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.09.00-4285F4.svg?style=flat&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Health Connect](https://img.shields.io/badge/Health%20Connect-1.1.0--alpha11-EA4335.svg?style=flat&logo=googlefit)](https://developer.android.com/health-and-fitness/guides/health-connect)
[![Glance](https://img.shields.io/badge/Jetpack%20Glance-Widgets%201.1.1-00C853.svg?style=flat)](https://developer.android.com/jetpack/compose/glance)
[![AI Vision Engine](https://img.shields.io/badge/AI%20Vision-Gemini%202.0%20Flash-8E24AA.svg?style=flat&logo=google)](https://aistudio.google.com)
[![Open Food Facts](https://img.shields.io/badge/Food%20Catalog-Open%20Food%20Facts-FF6F00.svg?style=flat)](https://world.openfoodfacts.org)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**Vital** es una aplicación moderna y de alto rendimiento para Android diseñada con enfoque en **privacidad total**, estética *dark obsidian* premium y automatización en segundo plano. Centraliza la actividad biométrica diaria (pasos, calorías, sueño reparador), seguimiento y evolución de peso corporal, entrenamientos, agenda, aviso para ir a dormir, registro nutricional con inteligencia artificial multimodal y widgets semitransparentes auto-actualizables para la pantalla de inicio y bloqueo.

---

## 🌟 Funcionalidades Principales

### 🥗 1. Comida y Nutrición Inteligente con IA (Gemini 2.0 Flash) & Supermercados con Fotos
* **Reconocimiento Visual de Platos por Foto:** Captura o sube una fotografía de tu comida y la IA multimodal (`gemini-2.0-flash`) analiza instantáneamente los ingredientes y porciones, estimando con alta fidelidad calorías totales, proteínas, carbohidratos y grasas.
* **Descripción de Contexto Pre-Envío:** Permite añadir una descripción textual opcional antes de enviar la foto (por ejemplo: *"lleva 2 cucharadas de aceite de oliva y salsa de soja baja en sal"*) para maximizar la exactitud de la inferencia nutricional.
* **Buscador de Supermercados con Fotos Reales:** Catálogo visual con imágenes de productos de **Mercadona (Hacendado)**, **Día**, **Carrefour**, **Alcampo**, **Supercor / El Corte Inglés** y alimentos básicos.
* **Escáner de Códigos de Barras Nativo:** Integración con Google Code Scanner para escanear al instante el código de barras de cualquier producto con la cámara del teléfono y obtener sus fotos y valores nutricionales de Open Food Facts en milisegundos.
* **Calculador Científico de Ingesta Calórica:** Modal interactivo basado en la ecuación de Mifflin-St Jeor que toma tu peso actual, altura y objetivo (Pérdida de grasa -500 kcal, Mantenimiento o Ganancia muscular) y calcula de forma inmediata tu ingesta diaria y desglose de macronutrientes (Proteínas, Carbohidratos, Grasas), aplicándolo con un solo toque.
* **Balance Calórico Integral:** Visualización clara de Calorías Consumidas, Calorías Quemadas en actividad y Calorías Restantes respecto a tu objetivo diario.

### 🧩 2. Widgets Glassmorphic Auto-Actualizables (1x1, 1x2, 2x2, 4x2)
Diseñados con acabado translúcido de alta estética para integrarse elegantemente con cualquier fondo de pantalla:
* **Auto-actualización Periódica Autónoma:** Impulsada por un `WidgetUpdateWorker` de WorkManager programado para refrescar los datos biométricos automáticamente cada 15 minutos en segundo plano sin intervención del usuario.
* **Sincronización Reactiva Inmediata:** Los widgets se actualizan al instante ante cualquier evento relevante dentro de la app (registro o eliminación de comidas, entrada de peso, sincronización con Health Connect o reapertura de la aplicación).
* **Formatos Disponibles:**
  * **1x1 (Micro Badge):** Puntuación de vitalidad, pasos acumulados y cápsulas compactas de calorías y horas de sueño.
  * **1x2 / 2x1 (Cápsula de Pulso):** Tres columnas simétricas y perfectamente alineadas para **Pasos** (Cyan), **Calorías** (Ámbar) y **Sueño** (Turquesa), con micro-barras de progreso y números limpios.
  * **2x2 (Cuadrante Glass):** Vista en cuadrícula con barras de progreso individuales para cada métrica clave.
  * **4x2 (Suite Biométrica):** Panel completo con métricas de salud, estado del entrenamiento del día y próximo evento del calendario.

### 🏃 3. Sincronización Biométrica con Health Connect
* **Lectura Idempotente y Cero Duplicados:** Sincronización continua de pasos, calorías activas y registro detallado de sueño (horas totales, fases de sueño profundo y REM).
* **Workouts Automáticos:** Reconocimiento de sesiones de fuerza y cardio registradas en aplicaciones compatibles con Health Connect (Google Fit, Samsung Health, Garmin, etc.).
* **Arquitectura de Sincronización en Segundo Plano:** `HealthConnectSyncWorker` mantiene los datos actualizados periódicamente.

### ⚖️ 4. Control y Seguimiento de Peso Corporal
* **Registro Preciso:** Selector de peso con ajustes rápidos (±0.1 kg, ±0.5 kg) y notas personalizadas (en ayunas, post-entreno).
* **Análisis de Evolución:** Cálculo automático de variación respecto al pesaje anterior, seguimiento de tendencia histórica y cálculo contextualizado del Índice de Masa Corporal (IMC).
* **Interfaz Limpia:** Botones de acción flotantes e interactivos sin solapamientos con textos ni elementos de la lista.

### 🌙 5. Notificación Inteligente de Hora de Dormir
* **Aviso 30 Minutos Antes:** La aplicación calcula automáticamente la hora ideal para acostarse en base a tu hora configurada de despertar (ej. 08:30) y tu objetivo de horas de sueño, emitiendo una notificación de descanso 30 minutos antes de la hora límite para optimizar el ritmo circadiano.

### 🤖 6. Motor de IA Híbrido (100% Gratuito y Privado)
* **Gemini 2.0 Flash:** Integración directa mediante API Key gratuita de Google AI Studio para análisis de patrones de recuperación y reconocimiento visual de comidas.
* **Motor Heurístico Local Offline:** En caso de no disponer de conexión o clave API, la aplicación utiliza un algoritmo fisiológico local en el dispositivo para generar recomendaciones diarias y consejos de entrenamiento.

---

## 🛠️ Arquitectura y Stack Tecnológico

El proyecto está construido bajo los principios de **Clean Architecture** y patrón **MVVM** recomendados por Google:

* **Lenguaje:** Kotlin 2.0.21.
* **Interfaz de Usuario (UI):** Jetpack Compose, Material 3, Canvas Graphics, Animaciones fluidas.
* **Widgets de Pantalla:** Jetpack Glance AppWidget 1.1.1 con layouts reactivos.
* **Inyección de Dependencias:** Dagger Hilt 2.52.
* **Base de Datos Local:** SQLite mediante AndroidX Room Database con soporte para Flow reactivo.
* **Preferencias:** Jetpack DataStore Preferences.
* **Tareas en Segundo Plano:** AndroidX WorkManager con workers asistidos por Hilt (`@HiltWorker`).
* **Integración de Salud:** AndroidX Health Connect Client SDK (`1.1.0-alpha11`).
* **Red y APIs:** OkHttp 4.12, Gson y Kotlinx Coroutines.
* **IA Multimodal:** Google GenAI / Google AI Client SDK (`gemini-2.0-flash`).

---

## 📁 Estructura del Código

```
app/src/main/java/com/lifedashboard/
├── core/
│   ├── worker/                 # HealthConnectSyncWorker, WidgetUpdateWorker, BedtimeNotificationWorker
│   └── scheduler/              # WorkManagerScheduler (orquestador de tareas en background)
├── data/
│   ├── local/                  # Room Database, Entidades y DAOs (Health, Workout, Weight, Meals, Calendar)
│   ├── remote/                 # FoodCatalogService (Open Food Facts), FoodAiService (Gemini Vision)
│   └── repository/             # Implementaciones de repositorios (Health, Workout, Nutrition, Preferences)
├── domain/
│   ├── model/                  # Modelos puros de dominio (HealthSummary, MealEntry, UserGoals, WeightEntry)
│   └── usecase/                # Casos de uso (DataAggregatorUseCase, CalculateBedtimeUseCase)
└── presentation/
    ├── navigation/             # NavGraph, Screen (Panel, Comida, Entreno, Salud, Config)
    ├── screen/
    │   ├── dashboard/          # Pantalla principal con Vitality Core y tarjetas de métricas
    │   ├── nutrition/          # Registro nutricional, cámara/galería, buscador de supermercados y macros
    │   ├── workout/            # Registro y lista de entrenamientos de fuerza y cardio
    │   ├── health/             # Historial biométrico y evolución de hábitos
    │   └── settings/           # Configuración de metas, perfil, hora de despertar y seguimiento de peso
    ├── theme/                  # Sistema de diseño Obsidian Glassmorphic, colores y tipografía
    └── widget/                 # Glance AppWidgets (1x1, 1x2, 2x2, 4x2) y receptores de broadcast
```

---

## 🚀 Guía de Instalación y Ejecución

### Requisitos Previos
* **Android Studio:** Ladybug (2024.2.1) o superior.
* **JDK:** Java 17 o superior (incluido en Android Studio JBR).
* **Dispositivo / Emulador:** Android 9.0 (API 28) o superior. Se recomienda Android 14+ para soporte nativo de Health Connect.

### Pasos de Configuración

1. **Clonar el repositorio:**
   ```bash
   git clone https://github.com/Jaime-202/life-dashboard.git
   cd life-dashboard
   ```

2. **Configurar el archivo `local.properties`:**
   Copia el archivo de ejemplo:
   ```bash
   # En Windows PowerShell:
   Copy-Item local.properties.example local.properties

   # En macOS / Linux:
   cp local.properties.example local.properties
   ```
   Define tu ruta de SDK y, opcionalmente, tu clave de Gemini API (obtenida gratis en [Google AI Studio](https://aistudio.google.com)):
   ```properties
   sdk.dir=C\:\\Users\\tu_usuario\\AppData\\Local\\Android\\Sdk
   GEMINI_API_KEY=tu_clave_api_de_gemini_aqui
   ```

3. **Compilar y Ejecutar:**
   ```bash
   # Windows PowerShell:
   $env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
   .\gradlew.bat assembleDebug

   # macOS / Linux:
   ./gradlew assembleDebug
   ```

---

## 🔒 Privacidad y Seguridad

* **Almacenamiento Local First:** Todas las métricas de salud, entrenamientos, comidas y registros de peso se almacenan exclusivamente en la base de datos local SQLite del dispositivo.
* **Sin Rastreo ni Telemetría:** Sin SDKs de análisis de terceros, sin anuncios y sin recolección de datos personales.
* **Seguridad de Credenciales:** Claves de API y configuraciones del entorno están salvaguardadas en `local.properties` e ignoradas por Git.

---

## 📄 Licencia

Este proyecto está bajo la licencia [MIT](LICENSE). Siéntete libre de utilizarlo, modificarlo y contribuir.
