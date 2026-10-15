# ⚡ Vital

> **Dashboard Integral de Salud, Rendimiento Físico, Hábitos y Peso con IA Local y Health Connect.**

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84.svg?style=flat&logo=android)](https://www.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF.svg?style=flat&logo=kotlin)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.09.00-4285F4.svg?style=flat&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Health Connect](https://img.shields.io/badge/Health%20Connect-1.1.0--alpha11-EA4335.svg?style=flat&logo=googlefit)](https://developer.android.com/health-and-fitness/guides/health-connect)
[![AI Engine](https://img.shields.io/badge/AI-Gemini%202.0%20Flash%20%7C%20Local%20Heuristic-8E24AA.svg?style=flat)](https://aistudio.google.com)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**Vital** es una aplicación moderna para Android diseñada bajo la filosofía de privacidad total y diseño biométrico de vanguardia. Centraliza tus métricas de salud (pasos, sueño, calorías), historial y evolución de peso corporal, entrenamientos de fuerza/cardio, agenda y hábitos en una interfaz fluida e intuitiva con widgets semitransparentes en múltiples dimensiones tanto para tu pantalla de inicio como de bloqueo.

---

## 📸 Características Destacadas

### 🔮 1. Núcleo Biométrico de Vitalidad (Vitality Core)
* **Arco Orbital Dinámico:** Sustituye las ruedas concéntricas tradicionales por un arco de energía de 240° que calcula tu puntuación de vitalidad ponderada en tiempo real.
* **Cápsulas Interactivas:** Acceso directo e instantáneo a tus métricas de pasos, sueño y calorías activas.

### 🏃 2. Sincronización Biométrica con Health Connect
* **Idempotencia y Cero Duplicados:** Sincronización periódica automática (WorkManager) y manual con Health Connect (Google / Samsung Health).
* **Entrenamientos Automáticos:** Detecta sesiones de fuerza y cardio registradas en tu ecosistema de salud y calcula el volumen semanal.
* **Métricas de Sueño Avanzadas:** Desglose de horas totales, fases de sueño profundo y sueño REM.

### ⚖️ 3. Control y Seguimiento de Peso Corporal
* **Registro Intuitivo:** Registro rápido de pesaje con botones de ajuste fino (±0.1 kg, ±0.5 kg) y notas personalizadas (en ayunas, post-entreno).
* **Análisis de Evolución:** Cálculo automático de variación respecto al pesaje anterior, evolución histórica e índice de masa corporal (IMC) contextualizado.
* **Historial Completo:** Registro detallado con fecha, hora, notas y posibilidad de eliminación o corrección.

### 🧩 4. Widgets Biométricos Semitransparentes (1x1, 1x2, 2x2, 4x2)
Diseño moderno *glassmorphic* con fondo translúcido que muestra con claridad tus 3 pilares clave: **Pasos** (Cyan 🏃), **Calorías** (Ámbar 🔥) y **Sueño** (Turquesa 🌙):
* **1x1 (Tile / Badge):** Porcentaje de vitalidad, pasos acumulados y micro-cápsulas de calorías y horas de descanso.
* **1x2 / 2x1 (Cápsula de Pulso):** Tres columnas dedicadas para pasos, calorías y sueño con iconos y valores de alto contraste.
* **2x2 (Cuadrante Glassmorphic):** Tarjeta con barras de progreso individuales para cada métrica respecto a sus objetivos.
* **4x2 (Suite Completa):** Vista completa con agenda de eventos del calendario y monitor biométrico.

### 📅 5. Calendario Obsidian de Hábitos y Consistencia
* **Vistas Semanal, Mensual y Anual:** Tarjetas con diseño obsidian glassmorphic.
* **Indicadores Luminosos de Hábitos:** Puntos de estado discretos para pasos (Cyan), sueño óptimo (Teal) y entrenamiento realizado (Violeta).
* **Navegación Fluida:** Selección de días y vista de progreso histórico sin bloqueos.

### 🤖 6. Motor de IA Híbrido (100% Gratis y Privado)
* **Privacidad Absoluta:** Tus datos biométricos **nunca** salen de tu dispositivo sin tu autorización.
* **Gemini 2.0 Flash:** Integración opcional con la clave gratuita de Google AI Studio para análisis de patrones de correlación entre fatiga, descanso y rendimiento.
* **Motor Heurístico Offline:** Si no dispones de conexión a internet o de clave API, la app ejecuta un motor local que analiza tus métricas y te brinda recomendaciones fisiológicas inmediatas.

### ⚙️ 7. Perfil Biométrico y Personalización Total
* Configuración centralizada de metas (Pasos, Sueño, Calorías, Días y Minutos de entrenamiento).
* Perfil antropométrico (Nivel de entrenamiento, Objetivo primario, Peso, Altura y cálculo automático de IMC).
* Notificaciones inteligentes: Recordatorios matutinos, alerta de hidratación y pausas activas, y resumen nocturno de recuperación.
* Exportación de datos de salud en formato JSON estructurado.

---

## 🛠️ Arquitectura y Tecnologías

La aplicación sigue los principios de **Clean Architecture** y patrón **MVVM** recomendados por Google:

* **UI:** Jetpack Compose + Material 3 + Jetpack Glance (Widgets).
* **Inyección de Dependencias:** Dagger Hilt.
* **Persistencia Local:** Room Database con SQLite (DAOs reactivos con Kotlin Coroutines y Flow).
* **Almacenamiento de Preferencias:** Jetpack DataStore Preferences.
* **Trabajo en Segundo Plano:** AndroidX WorkManager con HiltWorker.
* **Salud y Deporte:** AndroidX Health Connect Client SDK.
* **IA:** Google AI Client SDK para Android (`gemini-2.0-flash`).

---

## 🚀 Guía de Instalación y Ejecución

### Requisitos Previos
* **Android Studio:** Ladybug (2024.2.1) o superior.
* **JDK:** Java 17 o superior (incluido en Android Studio JBR).
* **Dispositivo / Emulador:** Android 9.0 (API 28) o superior. Para soporte completo de Health Connect, se recomienda Android 14+ o dispositivo con la app [Health Connect](https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata) instalada.

### Pasos para Configurar el Proyecto

1. **Clonar el repositorio:**
   ```bash
   git clone https://github.com/Jaime-202/life-dashboard.git
   cd life-dashboard
   ```

2. **Configurar el archivo local de propiedades:**
   Copia la plantilla de ejemplo:
   ```bash
   # En Windows PowerShell:
   Copy-Item local.properties.example local.properties

   # En macOS/Linux:
   cp local.properties.example local.properties
   ```
   Asegúrate de que `local.properties` tenga la ruta a tu Android SDK:
   ```properties
   sdk.dir=C\:\\Users\\tu_usuario\\AppData\\Local\\Android\\Sdk
   # Opcional: Clave gratis de Google AI Studio (aistudio.google.com)
   GEMINI_API_KEY=
   ```

3. **Compilar y Ejecutar:**
   Abre el proyecto en Android Studio y presiona **Run** (`Shift + F10`) o compila desde terminal:
   ```bash
   # Windows PowerShell
   $env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
   .\gradlew.bat assembleDebug

   # macOS / Linux
   ./gradlew assembleDebug
   ```

---

## 🔒 Privacidad y Seguridad

* **Sin rastreadores ni analíticas de terceros.**
* Todos los datos biométricos se almacenan en una base de datos local SQLite mediante Room en el almacenamiento privado del sandbox de la aplicación.
* Los archivos con claves privadas (`local.properties`), keystores de firma y credenciales están excluidos mediante `.gitignore`.

---

## 📄 Licencia

Este proyecto está bajo la licencia [MIT](LICENSE). Puedes usarlo, estudiarlo y adaptarlo libremente.
