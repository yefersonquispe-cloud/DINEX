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

Todo lo que debe verse igual en Android, iOS y web está en `App.kt`. El archivo
ahora tiene separadores grandes para encontrar cada zona:

- `APP STATE & NAVIGATION`: sesión, estado general y navegación.
- `AUTHENTICATION`: crear cuenta, iniciar sesión y validaciones.
- `SHARED SHELL`: barra superior, perfil, mascota y navegación inferior.
- `HOME / WALLET`: dashboard, las dos tarjetas, racha y presupuesto. El botón central `+` abre el registro general.
- `HISTORY`: historial mensual, movimientos y eliminación.
- `PLANS & REMINDERS`: presupuestos, recordatorios y confirmación de egreso.
- `INSIGHTS`: gráfico circular, barras diarias y filtros por categoría.
- `DINEX IA`: burbuja flotante, chat y contexto financiero para Gemini.
- `DIALOGS & QUICK CAPTURE`: formularios, cámara, revisión de foto y cargas premium.
- `SERIALIZATION & FORMATTING`: persistencia local y formato de importes.

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
- `Platform.*.kt`: información del sistema operativo.

Si un cambio debe funcionar en todas las plataformas, primero se cambia la
interfaz `expect` en `commonMain` y después sus implementaciones `actual`.

## Comandos habituales

Desde la raíz del proyecto:

```bash
./gradlew :androidApp:assembleDebug
./gradlew :webApp:jsBrowserDevelopmentExecutableDistribution
./gradlew :shared:compileKotlinIosSimulatorArm64
```

Los artefactos para compartir se guardan en `outputs/`. Los directorios
`build/`, `.gradle/` y `.kotlin/` son generados automáticamente: no se deben
editar ni usar como fuente del código.
