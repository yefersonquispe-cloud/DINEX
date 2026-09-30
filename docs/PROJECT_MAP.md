# Mapa del proyecto Dinex

Este archivo sirve como guía rápida para saber dónde hacer cada cambio sin
tener que revisar todo el proyecto.

## Módulos principales

| Módulo | Responsabilidad | Punto de entrada |
| --- | --- | --- |
| `shared` | Lógica y UI compartida para Android, iOS y web | `shared/src/commonMain/kotlin/com/example/dinex/App.kt` |
| `androidApp` | Contenedor Android, permisos y almacenamiento Android | `androidApp/src/main/kotlin/com/example/dinex/MainActivity.kt` |
| `iosApp` | Contenedor Xcode y configuración del simulador/iPhone | `iosApp/iosApp/iOSApp.swift` |
| `webApp` | Arranque y recursos de la web | `webApp/src/webMain/kotlin/com/example/dinex/main.kt` |

## Cambios de interfaz

La interfaz compartida ya no está concentrada en un archivo gigante. Busca la
responsabilidad que quieres modificar dentro de
`shared/src/commonMain/kotlin/com/example/dinex/`:

| Archivo | Qué se modifica allí |
| --- | --- |
| `App.kt` | Estado global, sesión, navegación y coordinación de pantallas |
| `AuthScreens.kt` | Crear cuenta, iniciar sesión y validaciones |
| `NavigationComponents.kt` | Barra superior, perfil, mascota y navegación inferior |
| `HomeScreen.kt` | Dashboard, las dos tarjetas, racha, presupuesto y resumen |
| `MovementsScreen.kt` | Historial mensual, movimientos y eliminación |
| `PlanScreen.kt` | Presupuestos, recordatorios y confirmación de egreso |
| `InsightsScreen.kt` | Gráficos, barras diarias y filtros por categoría |
| `AssistantScreen.kt` | Burbuja, chat y contexto financiero para Gemini |
| `DinexDialogs.kt` | Registro rápido, formularios, cámara y automatización |
| `DinexSerialization.kt` | Persistencia serializada, importación y formato de importes |

Los colores y modelos compartidos siguen al inicio de `App.kt`; las funciones
de cada pantalla tienen visibilidad `internal` para reutilizarse dentro del
módulo sin exponerlas fuera de Dinex.

Las dos imágenes permanentes de las tarjetas están en:

```text
shared/src/commonMain/composeResources/drawable/dinex_card_balance_bg.png
shared/src/commonMain/composeResources/drawable/dinex_card_savings_bg.png
```

La mascota usada por el asistente está en:

```text
shared/src/commonMain/composeResources/drawable/dinex_mascot_ai.png
```

El nuevo logo basado en la mascota está en `dinex_app_icon.png` y se conecta
con el ícono adaptativo de Android y `iosApp/iosApp/Assets.xcassets/AppIcon`.
La paleta visual principal se define al inicio de `App.kt` en las constantes
`AppBg`, `Card`, `Green`, `Indigo`, `ElectricBlue`, `Lime` y `Orange`.

## Integraciones por plataforma

Cada archivo terminado en `.android.kt`, `.ios.kt`, `.js.kt` o `.wasmJs.kt`
implementa la misma capacidad para una plataforma concreta:

- `DinexStorage.*.kt`: guardado local de cuentas, movimientos, ahorros y racha.
- `GeminiAssistant.*.kt` y `GeminiClient.*.kt`: chat y llamadas a Gemini.
- `ReceiptScanner.*.kt`: cámara y reconocimiento de productos/boletas.
- `DeviceAutomation.*.kt`: voz, ubicación y acceso a notificaciones.
- `Platform.*.kt`: información del sistema operativo.

En Android, el servicio de voz permanente y el lector de alertas están en:

```text
androidApp/src/main/kotlin/com/example/dinex/DinexHotwordService.kt
androidApp/src/main/kotlin/com/example/dinex/DinexVoiceCommandActivity.kt
androidApp/src/main/kotlin/com/example/dinex/DinexNotificationListenerService.kt
```

Si un cambio debe funcionar en todas las plataformas, primero se cambia la
interfaz `expect` en `commonMain` y después sus implementaciones `actual`.

## Comandos habituales

Desde la raíz del proyecto:

```bash
./gradlew :androidApp:assembleDebug
./gradlew :androidApp:lintDebug
./gradlew :webApp:jsBrowserDevelopmentExecutableDistribution
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer ./gradlew :shared:compileKotlinIosSimulatorArm64
```

Los artefactos para compartir se guardan en `outputs/`. Los directorios
`build/`, `.gradle/` y `.kotlin/` son generados automáticamente: no se deben
editar ni usar como fuente del código.
