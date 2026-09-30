# Dinex

Aplicación financiera multiplataforma para registrar ingresos y gastos, revisar el historial mensual, controlar presupuestos, crear recordatorios, mantener una racha de ahorro y conversar con Dinex IA.

La interfaz compartida usa Compose Multiplatform y funciona en Android, iOS y web. La cámara inteligente está implementada en Android e iOS; en web el resto de las funciones permanece disponible.

## Guía rápida para cambios

Consulta [docs/PROJECT_MAP.md](./docs/PROJECT_MAP.md) antes de modificar el
proyecto. Ahí se indica dónde cambiar cada parte de la interfaz, la cámara,
Gemini, las tarjetas Dinex, los ahorros y la persistencia. Los APK, ZIP y
capturas generados están documentados en [outputs/README.md](./outputs/README.md).

## Configuración de Gemini

No guardes claves reales en Git ni las publiques dentro del APK final. Para desarrollo local:

- Android: agrega `GEMINI_API_KEY=TU_CLAVE` a `local.properties`.
- iOS: define `GEMINI_API_KEY` mediante un archivo `.xcconfig` local y úsalo desde `Config.xcconfig`.

Dinex intenta primero Gemini 3.1 Pro y dispone de modelos de respaldo compatibles. Si no hay clave, Android conserva el reconocimiento local de texto y objetos; el chat muestra un aviso claro.

## Voz y notificaciones en Android

Desde el botón `+` abre **Voz y notificaciones**:

- **Notificaciones financieras:** detecta importes en alertas de Yape, Plin y aplicaciones bancarias cuando incluyen palabras como “pagaste”, “compraste”, “transferiste”, “recibiste” o “depósito”. Dinex siempre muestra los movimientos detectados antes de registrarlos.
- **Oye Dinex:** activa un servicio visible de micrófono. Con la pantalla desbloqueada puedes decir “Oye Dinex” y luego dictar un gasto, ingreso o recordatorio. Android mantiene una notificación mientras el modo está activo y permite apagarlo inmediatamente.

En Redmi/HyperOS, una instalación por APK puede bloquear el lector como ajuste restringido. Abre **Información de la aplicación → ⋮ → Permitir ajustes restringidos**, vuelve a Dinex y activa **Acceso a notificaciones**. Para que “Oye Dinex” permanezca disponible, habilita también **Inicio automático** y excluye Dinex del ahorro agresivo de batería.

Android puede impedir que una aplicación abra una pantalla automáticamente desde segundo plano. Si el fabricante bloquea la apertura directa, Dinex muestra una alerta de alta prioridad; tócala para continuar el comando de voz.

## Acceso por correo

El flujo actual permite crear una cuenta local con nombre, correo y contraseña,
validando la contraseña en tiempo real. Las credenciales se guardan únicamente
en el dispositivo mediante un resumen no reversible. Para sincronización real
entre dispositivos debe conectarse a Firebase Authentication o a un backend
propio.

## Estructura

* [/iosApp](./iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./shared/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
    folder is the appropriate location.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :androidApp:assembleDebug`
- Web app:
  - Wasm target (faster, modern browsers): `./gradlew :webApp:wasmJsBrowserDevelopmentRun`
  - JS target (slower, supports older browsers): `./gradlew :webApp:jsBrowserDevelopmentRun`
- iOS app: open the [/iosApp](./iosApp) directory in Xcode and run it from there.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :shared:testAndroidHostTest`
- Web tests:
  - Wasm target: `./gradlew :shared:wasmJsTest`
  - JS target: `./gradlew :shared:jsTest`
- iOS tests: `./gradlew :shared:iosSimulatorArm64Test`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html),
[Compose Multiplatform](https://kotlinlang.org/compose-multiplatform/),
[Kotlin/Wasm](https://kotl.in/wasm/)…

We would appreciate your feedback on Compose/Web and Kotlin/Wasm in the public Slack channel [#compose-web](https://slack-chats.kotlinlang.org/c/compose-web).
If you face any issues, please report them on [YouTrack](https://youtrack.jetbrains.com/newIssue?project=CMP).
