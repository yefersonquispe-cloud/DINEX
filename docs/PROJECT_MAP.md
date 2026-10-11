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
| `App.kt` | Vista raíz: conecta pantallas con el ViewModel y gestiona los diálogos |
| `presentation/DinexViewModel.kt` | Estado observable de sesión y finanzas, navegación y acciones del usuario |
| `data/DinexRepository.kt` | Lectura/escritura de perfiles separados, migración y preferencias |
| `data/DinexModels.kt` | Modelos de movimientos, cuentas, ahorros y recordatorios |
| `data/ExternalVoiceRecorder.kt` | Registro por voz desde la burbuja Android en el mismo repositorio |
| `data/NotificationTransactionParser.kt` | Interpretación de notificaciones financieras y montos |
| `DinexTheme.kt` | Colores, tipografía, formas Material 3 y componentes compartidos |
| `DinexVisualSystem.kt` | Fondo aurora, entradas suaves de tarjetas y encabezados accesibles |
| `DinexLaunchScreen.kt` | Escritura vectorial DINEX, lápiz, varita y opción de omitir/reducir animación |
| `AuthScreens.kt` | Crear cuenta, iniciar sesión y validaciones |
| `NavigationComponents.kt` | Barra superior, perfil, mascota y navegación con acabado de cristal |
| `DinexNavigationIcons.kt` | Iconos vectoriales de casa, movimientos, documento, análisis y botón + |
| `HomeScreen.kt` | Dashboard, las dos tarjetas, racha, presupuesto y resumen |
| `MovementsScreen.kt` | Historial mensual, movimientos y eliminación |
| `PlanScreen.kt` | Presupuestos, recordatorios y confirmación de egreso |
| `InsightsScreen.kt` | Gráficos, barras diarias y filtros por categoría |
| `AssistantScreen.kt` | Burbuja, chat y contexto financiero para Gemini |
| `DinexDialogs.kt` | Registro rápido, formularios, cámara y automatización |
| `DinexSerialization.kt` | Persistencia serializada, importación y formato de importes |
| `transfer/TransferDialogs.kt` | Seleccionar archivos, vista previa, confirmación y guardar reportes |
| `transfer/MovementImport.kt` | Reconocer columnas CSV/Excel, fechas, montos y tipos de movimiento |
| `transfer/WorkbookCodec.kt` | Lectura segura de Excel .xlsx y ZIP/DEFLATE multiplataforma |
| `transfer/MovementReports.kt` | Generar Excel, CSV y PDF reales |
| `transfer/TransferModels.kt` | Contrato de archivos, periodos, fechas y comparación de repetidos |

Los modelos viven en `data/DinexModels.kt` y la paleta en `DinexTheme.kt`; las funciones
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
La paleta visual principal se define en `DinexTheme.kt` en las constantes
`AppBg`, `Card`, `Green`, `Indigo`, `ElectricBlue`, `Lime` y `Orange`.
La dirección visual, animaciones y opción de reducir movimiento están descritas
en `docs/DESIGN_SYSTEM.md`. La revisión del rediseño 1.5 está en
`docs/QA_AURORA_2026-10-06.md`.

## Integraciones por plataforma

Cada archivo terminado en `.android.kt`, `.ios.kt`, `.js.kt` o `.wasmJs.kt`
implementa la misma capacidad para una plataforma concreta:

- `DinexStorage.*.kt`: guardado local de cuentas, movimientos, ahorros y racha.
- `GeminiAssistant.*.kt` y `GeminiClient.*.kt`: chat y llamadas a Gemini.
- `ReceiptScanner.*.kt`: cámara y reconocimiento de productos/boletas.
- `DeviceAutomation.*.kt`: voz, ubicación y acceso a notificaciones.
- `Platform.*.kt`: información del sistema operativo.
- `FileTransfer.*.kt`: selección y guardado de archivos reales. Ver `docs/IMPORT_EXPORT.md`.

En Android, el servicio de voz permanente y el lector de alertas están en:

```text
androidApp/src/main/kotlin/com/example/dinex/DinexHotwordService.kt
androidApp/src/main/kotlin/com/example/dinex/DinexVoiceCommandActivity.kt
androidApp/src/main/kotlin/com/example/dinex/DinexVoiceOverlay.kt
androidApp/src/main/kotlin/com/example/dinex/DinexNotificationListenerService.kt
```

Si un cambio debe funcionar en todas las plataformas, primero se cambia la
interfaz `expect` en `commonMain` y después sus implementaciones `actual`.

## Comandos habituales

Desde la raíz del proyecto:

```bash
./gradlew :androidApp:assembleDebug
./gradlew :androidApp:lintDebug
./gradlew :shared:testAndroidHostTest
./gradlew :webApp:jsBrowserDevelopmentExecutableDistribution
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer ./gradlew :shared:compileKotlinIosSimulatorArm64
```

Los artefactos para compartir se guardan en `outputs/`. Los directorios
`build/`, `.gradle/` y `.kotlin/` son generados automáticamente: no se deben
editar ni usar como fuente del código.

## MVVM y base de datos

`Pantallas Compose → DinexViewModel (StateFlow) → DinexRepository → DinexStore`.
La vista envía eventos; el ViewModel valida y actualiza el estado; el repositorio
guarda cada perfil por correo. `App.kt` ya no contiene los modelos, la paleta ni
las escrituras financieras. Los estados efímeros de diálogos y chat permanecen
en la vista, y las integraciones nativas están separadas por plataforma.

Android utiliza `SQLiteOpenHelper` y iOS la biblioteca SQLite del sistema mediante
`src/nativeInterop/cinterop/sqlite3.def`. Archivo: `dinex.db`, tabla `app_state`.
Cada perfil financiero se guarda en una fila atómica, además de cuentas, sesión,
preferencias y bandeja Android de notificaciones. Conserva los datos anteriores
mediante una migración local. Web usa `localStorage`: no se afirma que el navegador
use SQLite, y no hay sincronización entre dispositivos ni servidor de autenticación.

La prueba `DinexViewModelTest.kt` cubre aislamiento de cuentas, restauración,
saldo inicial, ahorros consecutivos, importación, pagos de recordatorios y preferencias.
Las pruebas del parser cubren importes y exclusión de pagos rechazados/promociones.
Para los permisos específicos del teléfono, consulta `ANDROID_AUTOMATION.md`.
