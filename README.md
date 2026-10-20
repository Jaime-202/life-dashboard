# ⚡ Vital — Life Dashboard

> **Plataforma Integral de Salud, Nutrición Inteligente con IA Multimodal (Gemini 3.8 Flash), Hábitos, Peso y Rendimiento para Android, con Sincronización en Segundo Plano y Widgets Glassmorphic.**

[![Android](https://img.shields.io/badge/Platform-Android%209.0%2B%20(API%2028%2B)-3DDC84.svg?style=flat&logo=android)](https://www.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF.svg?style=flat&logo=kotlin)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.09.00-4285F4.svg?style=flat&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Health Connect](https://img.shields.io/badge/Health%20Connect-1.1.0--alpha11-EA4335.svg?style=flat&logo=googlefit)](https://developer.android.com/health-and-fitness/guides/health-connect)
[![Glance](https://img.shields.io/badge/Jetpack%20Glance-Widgets%201.1.1-00C853.svg?style=flat)](https://developer.android.com/jetpack/compose/glance)
[![Google Gemini](https://img.shields.io/badge/AI%20Engine-Gemini%203.8%20Flash-8E24AA.svg?style=flat&logo=google)](https://aistudio.google.com)
[![Room DB](https://img.shields.io/badge/Database-Room%202.6.1%20SQLite-4CAF50.svg?style=flat)](https://developer.android.com/training/data-storage/room)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

---

## 📖 Descripción General

**Vital (Life Dashboard)** es una aplicación moderna, fluida y reactiva para Android concebida bajo la filosofía **Local-First**, garantizando **privacidad absoluta**, estética *Dark Obsidian Glassmorphic* y autonomía energética. 

Centraliza en un único ecosistema la monitorización biométrica en tiempo real (pasos, gasto calórico activo, arquitectura del sueño), el control de la ingesta nutricional mediante visión artificial multimodal, el seguimiento y análisis de tendencia del peso corporal, la gestión de sesiones de entrenamiento de fuerza y cardio, recordatorios circadianos de descanso y widgets de pantalla de inicio transparentes que se actualizan automáticamente en segundo plano.

---

## 🌟 Módulos y Funcionalidades en Detalle

### 🥗 1. Comida y Nutrición Inteligente con IA (Gemini 3.8 Flash + Motor Local)

* **Reconocimiento Visual de Platos con Gemini 3.8 Flash:**
  - Captura una foto directamente con la cámara del dispositivo o elígela de la galería.
  - El motor multimodal analiza la imagen reconociendo ingredientes, guarniciones y porciones, devolviendo una estimación detallada de gramos, calorías totales, proteínas, hidratos de carbono y grasas.
  - Incluye soporte para **contexto adicional antes del análisis** (por ejemplo: *"son 2 cucharadas de aceite de oliva y salsa de soja sin azúcar"*).

* **Detección de Marcas Españolas vs. Alimentos Genéricos:**
  - Sistema de parsing inteligente que distingue productos de marca específica de supermercados (**Hacendado / Mercadona**, **Carrefour**, **Día**, **Alcampo**, **Lidl / Milbona**, **Danone**, **El Corte Inglés / Supercor**, **Central Lechera Asturiana**, etc.).
  - Si el usuario indica la marca (por ejemplo: *"yogur de hacendado y una manzana"*), el sistema etiqueta el producto como marca oficial con su correspondiente badge violeta (`[Hacendado]`) y asigna la manzana como producto **Genérico** limpio sin etiquetas redundantes.
  - La segmentación sintáctica por cláusulas evita la contaminación de marcas entre distintos alimentos de una misma frase.

* **Protección Estricta de la Cuota Gratuita de Gemini (~20 peticiones diarias):**
  - Todas las consultas en segundo plano, el **Coach Nutricional** y los **Insights del Panel** operan de forma **100% offline y local** mediante algoritmos fisiológicos heurísticos (consumo de **0 peticiones** de API).
  - Las peticiones de la clave API se reservan de forma exclusiva para cuando el usuario solicita explícitamente analizar una **foto de comida** o una consulta compleja de **texto libre**.

* **Catálogo Integrado de Supermercados con Fotografías HD:**
  - Buscador predictivo ultra-rápido en memoria con ranking de relevancia basado en productos habituales de la compra española.
  - Galería visual de empaquetados reales y alimentos frescos.

* **Escáner de Códigos de Barras Nativo (Google Code Scanner):**
  - Escaneo instantáneo mediante Google Play Services (sin necesidad de permisos invasivos de cámara para el proceso básico).
  - Conexión directa con la API de **Open Food Facts** para importar automáticamente la información nutricional, marca e imagen del producto.

* **Calculador Científico de Ingesta y Macros (Mifflin-St Jeor):**
  - Estimación precisa de la Tasa Metabólica Basal (BMR) y Gasto Energético Total Diario (TDEE).
  - Ajuste instantáneo según objetivo (Déficit / Pérdida de grasa, Mantenimiento normocalórico o Superávit / Volumen muscular) y distribución óptima de macronutrientes.

---

### 🔥 2. Objetivos Calóricos Desacoplados e Independientes

A diferencia de otras apps que mezclan el gasto energético con la comida:
* **Objetivo de Calorías Activas Quemadas (`activeCaloriesTarget`):**
  - Establece cuántas calorías deseas quemar cada día mediante tus pasos, movimiento y ejercicio (ej. 400, 500, 600 u 800 kcal).
  - Alimenta la tarjeta de **Calorías** del panel principal, el anillo de progreso circular y la sección biométrica de **Salud**.
  - Puede modificarse en 1 toque desde la propia tarjeta de Salud con el botón **[Meta]** o desde Ajustes.
* **Objetivo de Ingesta Nutricional (`caloriesTarget`):**
  - Gestiona exclusivamente la comida consumida frente a las necesidades energéticas diarias (ej. 1914 o 2200 kcal).
  - Ambos objetivos son **100% independientes**: cambiar el objetivo de calorías activas nunca altera ni resetea la ingesta de comida, y viceversa.

---

### 📊 3. Panel Principal (Vitality Dashboard)

* **Anillos de Progreso Neón (Vitality Rings):** Representación visual simultánea del progreso de **Pasos** (Cyan), **Gasto Calórico Activo** (Ámbar) y **Sueño** (Turquesa).
* **Pulso Biométrico en Tiempo Real:** Tarjeta condensada con métricas activas conectadas en vivo con Health Connect.
* **Pilares Modulares de Rendimiento:** Tarjetas interactivas con acceso directo a cada módulo especializado (Entrenamiento, Pasos, Sueño, Calorías).
* **Calendario de Consistencia Mensual / Semanal:** Historial visual interactivo que evalúa el cumplimiento de hábitos día a día.
* **Próximos Eventos de Agenda:** Integración opcional con el proveedor de calendario del dispositivo para anticipar eventos programados.

---

### 🏃 4. Sincronización Biométrica con Health Connect

* **Lectura Idempotente y Robusta:**
  - Sincronización continua de pasos diarios (`StepsRecord`), distancia recorrida (`DistanceRecord`), calorías activas quemadas (`ActiveCaloriesBurnedRecord`) y registro de sueño (`SleepSessionRecord`).
  - Detección de sesiones de entrenamiento registradas por apps de terceros o wearables (Samsung Health, Google Fit, Garmin, Polar, Whoop, etc.).
* **Frecuencias de Sincronización Flexibles:** Tareas periódicas gestionadas con `WorkManager` (cada 15, 30 o 60 minutos) y botón de sincronización manual instantánea.

---

### 🏋️ 5. Entrenamiento de Fuerza y Cardio

* **Enfoque en Fuerza y Progresión:** Registro detallado de ejercicios, series, repeticiones efectivas y peso levantado.
* **Detección Automática de Sesiones:** Días de descanso activo y días de entrenamiento estructurado con cálculo de volumen total.
* **Historial Cronológico:** Vista en línea de tiempo de entrenamientos completados con métricas de duración y tipo de sesión.

---

### ⚖️ 6. Control y Análisis de Peso Corporal

* **Registro Rápido y Ergonómico:** Entrada de peso con micro-ajustes incrementales (±0.1 kg, ±0.5 kg) y notas contextuales (ej. *"en ayunas tras entreno"*).
* **Cálculo de IMC y Tendencias:** Evaluación contextualizada del Índice de Masa Corporal y cálculo de delta respecto al pesaje anterior.
* **Persistencia Segura en Room:** Historial completo con posibilidad de eliminación granular de registros atípicos.

---

### 🌙 7. Optimización Circadiana y Descanso

* **Calculador de Hora de Dormir Óptima:** Algoritmo que calcula la hora límite para acostarse tomando como referencia la hora de levantarse programada y el objetivo de horas de sueño.
* **Notificación Preventiva 30 Minutos Antes:** Alerta discreta en el móvil para iniciar la desconexión de pantallas y preparar el descanso.

---

### 🧩 8. Widgets Glassmorphic para Pantalla de Inicio (Jetpack Glance)

Diseñados con bordes sutiles, fondo translúcido y compatibilidad con modo oscuro:
* **Micro Widget (1x1):** Indicador compacto de vitalidad con métrica principal.
* **Cápsula de Pulso (1x2 / 2x1):** Tres columnas perfectamente proporcionadas para Pasos, Calorías y Sueño con barras de progreso individuales.
* **Cuadrante Glass (2x2):** Vista en matriz con las cuatro métricas clave de salud.
* **Suite Completa (4x2):** Panel ejecutivo con métricas de actividad, estado del entrenamiento y próximo evento.
* **Actualización Autónoma:** Los widgets se refrescan automáticamente cada 15 minutos en segundo plano (`WidgetUpdateWorker`) y reaccionan en tiempo real cuando se registra cualquier cambio en la aplicación.

---

## 🏗️ Arquitectura de Software

La aplicación sigue los principios de **Clean Architecture** y el patrón oficial **MVVM (Model-View-ViewModel)** recomendado por Google Android:

```
app/src/main/java/com/lifedashboard/
├── core/
│   ├── scheduler/              # Orquestador central de tareas en segundo plano
│   └── worker/                 # HealthConnectSyncWorker, WidgetUpdateWorker, BedtimeNotificationWorker
├── data/
│   ├── local/                  # Room Database (Entidades, DAOs y Migraciones de esquema)
│   ├── remote/
│   │   ├── food/               # FoodCatalogService, FoodAiService, RealFoodImageResolver
│   │   └── gemini/             # GeminiApiService, GeminiModelManager (descubrimiento dinámico)
│   └── repository/             # Repositorios: Health, Nutrition, Workout, Calendar, UserPreferences
├── domain/
│   ├── model/                  # Modelos puros de dominio (HealthSummary, UserGoals, MealEntry)
│   └── usecase/                # Casos de uso (cálculo de ingesta, balance calórico, estimaciones)
└── presentation/
    ├── navigation/             # NavGraph, Screen (Panel, Entreno, Comida, Salud, Config)
    ├── screen/
    │   ├── dashboard/          # Pantalla principal (Vitality Rings, tarjetas de pilares)
    │   ├── nutrition/          # Comida, IA texto/foto, supermercados, escáner de barras, macros
    │   ├── workout/            # Registro y métricas de entrenamientos de fuerza
    │   ├── health/             # Pantalla de Salud & Metas (Pasos, Sueño, Calorías Activas)
    │   └── settings/           # Configuración general, perfil, API keys, objetivos de gasto
    ├── theme/                  # Sistema de diseño Dark Obsidian, paleta de colores y tipografía
    └── widget/                 # Jetpack Glance Widgets (1x1, 1x2, 2x2, 4x2) y AppWidgetReceivers
```

---

## 🛠️ Stack Tecnológico

| Capa / Módulo | Tecnología Utilizada |
| :--- | :--- |
| **Lenguaje** | Kotlin 2.0.21 |
| **UI Framework** | Jetpack Compose (BOM 2024.09.00) + Material 3 |
| **Inyección de Dependencias** | Dagger Hilt 2.52 con Hilt WorkManager integration |
| **Persistencia Local** | AndroidX Room Database 2.6.1 con soporte para Coroutines y Flow |
| **Preferencias Clave-Valor** | Jetpack DataStore Preferences 1.1.1 |
| **Segundo Plano / Background** | AndroidX WorkManager 2.9.1 (`@HiltWorker`) |
| **Salud y Biometría** | AndroidX Health Connect Client SDK (`1.1.0-alpha11`) |
| **Widgets** | Jetpack Glance AppWidget 1.1.1 |
| **Inteligencia Artificial** | Google Gemini API (`gemini-3.8-flash` con auto-descubrimiento y multi-tier fallback) |
| **Red y Parsing** | OkHttp 4.12, Retrofit-ready JSON parsing, Gson |
| **Visión y Códigos de Barras** | Google Code Scanner (Play Services) + Open Food Facts API |

---

## 🔐 Privacidad, Seguridad y Buenas Prácticas

* **Local-First & Cero Telemetría:** Todas las bases de datos de salud, nutrición y entrenamientos residen localmente en el almacenamiento privado de la app (`SQLite / Room`). No existen servidores intermedios de recopilación de datos, trackers ni SDKs de publicidad.
* **Seguridad de Credenciales:**
  - El proyecto está preparado para su publicación en repositorios públicos.
  - Ninguna clave API o credencial sensible se almacena en el control de versiones (Git).
  - Las claves opcionales de Gemini se gestionan de forma segura a través de `local.properties` (ignorado en `.gitignore`) o mediante el diálogo cifrado de ajustes dentro de la propia aplicación en `DataStore`.

---

## 🚀 Guía de Instalación y Compilación

### Requisitos Previos
* **Android Studio:** Ladybug (2024.2.1) o superior.
* **JDK:** Java 17 o Java 21 (incluido en Android Studio JBR).
* **Dispositivo Físico o Emulador:** Android 9.0 (API 28) o superior. Se recomienda Android 14+ para soporte nativo directo de Health Connect sin dependencias externas.

### Pasos para Compilar

1. **Clonar el repositorio:**
   ```bash
   git clone https://github.com/Jaime-202/life-dashboard.git
   cd life-dashboard
   ```

2. **Configurar el entorno local (`local.properties`):**
   Copia el archivo de plantilla proporcionado:
   ```bash
   # Windows PowerShell:
   Copy-Item local.properties.example local.properties

   # macOS / Linux:
   cp local.properties.example local.properties
   ```
   Abre `local.properties` e indica la ruta de tu Android SDK y, opcionalmente, tu clave de Gemini API gratuita de [Google AI Studio](https://aistudio.google.com):
   ```properties
   sdk.dir=C\:\\Users\\tu_usuario\\AppData\\Local\\Android\\Sdk
   GEMINI_API_KEY=tu_clave_de_gemini_opcional
   ```
   *(Nota: Si no introduces ninguna clave, la aplicación funcionará al 100% en modo local offline con su motor heurístico).*

3. **Compilar el APK de depuración:**
   ```bash
   # Windows PowerShell:
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
   .\gradlew.bat assembleDebug

   # macOS / Linux:
   ./gradlew assembleDebug
   ```

4. **Instalar en un dispositivo conectado:**
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

---

## 📄 Licencia

Este proyecto está distribuido bajo la licencia [MIT](LICENSE). Siéntete libre de adaptarlo, mejorarlo y utilizarlo en tus propios proyectos personales o profesionales.
