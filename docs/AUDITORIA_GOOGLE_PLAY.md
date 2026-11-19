# Auditoría técnica de DINEX para Google Play Console

Fecha: 19/11/2026 · Commit revisado: `e4a12ec` · Paquete: `com.yefersonquispe.dinex` · versión 1.6 (versionCode 7) · targetSdk 36.

Solo lectura: no se cambió ninguna función. Todo sale del código, del manifiesto final de la versión publicada
(`processReleaseMainManifest`), de Gradle, de `firestore.rules` y de `firebase.json`.
**Ninguna declaración se marcó en Play Console.**

> Rutas abreviadas: `shared/…` = `shared/src/commonMain/kotlin/com/example/dinex/…`,
> `android/…` = `shared/src/androidMain/kotlin/com/example/dinex/…`, `app/…` = `androidApp/src/main/…`.

---

## 1. Resumen de lo encontrado

| Tema | Estado real |
|---|---|
| Cuenta | Firebase Authentication por REST (correo + contraseña, y Google). La contraseña la guarda Firebase; DINEX no la guarda. |
| Datos en la nube | Cloud Firestore `users/{uid}`: nombre, correo, perfil financiero (movimientos con ubicación, metas, presupuestos, recordatorios, ahorro, racha), consentimiento y conteo de IA. `aiReports/{id}`: reportes de respuestas de la IA. |
| IA | Gemini (Google) en la nube con **clave dentro del APK** (el servidor seguro no está desplegado). Gemini Nano en el teléfono cuando el equipo lo admite. |
| Terceros | Google (Firebase, Gemini, inicio con Google, reconocimiento de voz, Geocoder, ML Kit en el teléfono), CoinGecko (precios), Open Food Facts (código de barras), OpenStreetMap (mapas). |
| Analítica / anuncios | **No hay**: ningún SDK de analítica, Crashlytics, publicidad ni rastreo. |
| Notificaciones push | **No hay** Firebase Cloud Messaging. Solo notificaciones locales (WorkManager). |
| Firebase Storage | **No se usa.** Fotos de perfil e ilustraciones quedan solo en el teléfono. |
| Transmisión | Solo HTTPS (se rechaza cualquier URL que no sea `https://`). |
| Almacenamiento local | SQLite privado de la app, **sin cifrado propio**; copia de seguridad de Android desactivada (`allowBackup="false"`). |
| Eliminación de cuenta | Dentro de la app y página web pública. Borra Firestore `users/{uid}`, la cuenta de Auth y los datos del equipo. **Quedan los reportes de IA.** |

---

## 2. Permisos de Android (manifiesto final de la versión publicada)

| Permiso | ¿Lo declara DINEX o una librería? | ¿Se usa? | Para qué | Evidencia |
|---|---|---|---|---|
| `INTERNET` | DINEX | Sí | Firebase, Gemini, precios, mapas | `app/AndroidManifest.xml:4` |
| `RECORD_AUDIO` | DINEX | Sí, con permiso en tiempo de ejecución | Comandos de voz y "Oye Dinex" | `app/AndroidManifest.xml:5`; pedido en `android/DeviceAutomation.android.kt:161,239` |
| `POST_NOTIFICATIONS` | DINEX | Sí | Alertas de presupuesto, recordatorio de ahorro, notificación de "Oye Dinex" | `app/AndroidManifest.xml:6`; pedido en `android/DeviceAutomation.android.kt:124,248` |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE` | DINEX | Sí | Servicio "Oye Dinex" (tipo micrófono) con notificación permanente | `app/AndroidManifest.xml:7-8,54`; `app/kotlin/.../DinexHotwordService.kt:87` |
| `SYSTEM_ALERT_WINDOW` | DINEX | Sí, opcional | Burbuja flotante con la mascota | `app/AndroidManifest.xml:9`; `app/kotlin/.../DinexVoiceOverlay.kt:73` |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | DINEX | Sí, opcional, **solo con la app abierta** | Lugar de cada gasto y mapa de calor | `app/AndroidManifest.xml:10-11`; `android/DeviceAutomation.android.kt:269,330` |
| `BIND_NOTIFICATION_LISTENER_SERVICE` (servicio) | DINEX | Sí, opcional | Leer avisos de pago (Yape, Plin, bancos) | `app/AndroidManifest.xml:59-60`; `app/kotlin/.../DinexNotificationListenerService.kt:22` |
| `ACCESS_NETWORK_STATE` | Librería (WorkManager / Google) | Sí | Ver si hay internet ("IA en línea") | Manifiesto final |
| `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK` | Librería (WorkManager) | Sí | Reprogramar recordatorios locales tras reiniciar | Manifiesto final |
| `com.google.android.apps.aicore.service.BIND_SERVICE` | Librería (ML Kit GenAI) | Sí | Gemini Nano en el teléfono | Manifiesto final |
| `CAMERA` | — | **No se declara** | La foto de la boleta se toma con la app de cámara del sistema (`TakePicture`) | `android/ReceiptScanner.android.kt:94` |
| `READ_SMS`, `READ_CONTACTS`, almacenamiento, `ACCESS_BACKGROUND_LOCATION`, `QUERY_ALL_PACKAGES`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | — | **No existen** | — | Manifiesto final |

---

## 3. Funciones financieras

| Función | ¿Existe? | Detalle |
|---|---|---|
| Registrar ingresos y gastos | Sí | Manual, por voz, por foto de boleta o desde avisos de pago leídos en el teléfono (con confirmación). |
| Presupuestos | Sí | Límite diario y por categoría, con alertas. |
| Ahorros y metas | Sí | Metas de ahorro, racha y medallas. **El dinero no se mueve**: solo se registra. |
| Consultar movimientos | Sí | Historial, filtros, análisis, reportes PDF, Excel y CSV. |
| Consultas con IA | Sí | Dinex IA (Gemini) y "Oye Dinex". Orientación, no asesoría profesional. |
| Precios de mercado | Sí, informativo | Dólar y criptomonedas desde CoinGecko; no se puede comprar ni vender. |
| Conexión con cuentas bancarias | **No** | No usa APIs bancarias, Open Banking ni credenciales de bancos. Leer notificaciones no es una conexión con el banco. |
| Transferencias o pagos | **No** | No mueve dinero, no procesa pagos. |
| Préstamos, créditos, seguros, inversiones | **No** | No existen. |

---

## 4. Inteligencia artificial

| Punto | Estado | Evidencia |
|---|---|---|
| Qué recibe Gemini (chat) | Pregunta + últimos 6 mensajes + resumen financiero: totales de ingresos y gastos, saldo, gasto de hoy y límite, racha, ahorro, gasto por categoría, totales por periodo y **los 8 últimos movimientos (fecha, título, monto, categoría, medio de pago)**. **No** recibe nombre, correo, ubicación ni identificadores. | `shared/presentation/AssistantViewModel.kt:124` |
| Qué recibe Gemini (boletas) | Foto de la boleta + texto leído en el teléfono + código de barras. No se guarda la imagen. | `android/ReceiptScanner.android.kt:88` |
| Gemini Nano | Si el teléfono lo admite, el chat se responde **en el teléfono** sin salir a internet. | `android/GeminiAssistant.android.kt:150` |
| Clave de la API | **Dentro del APK** (`meta-data` del manifiesto) porque `DINEX_AI_PROXY_URL` no está configurada. Se puede extraer. | `app/AndroidManifest.xml:32`; `android/GeminiClient.android.kt:29` |
| Genera contenido con IA | Sí: respuestas de texto (chat) y lectura de boletas. | — |
| Reportar respuestas | Sí: botón **Reportar** en cada respuesta (ofensiva, peligrosa, incorrecta u otra) → Firestore `aiReports`, solo creación. | `shared/ui/AssistantScreen.kt:335`; `firestore.rules:55` |
| Bloquear contenido | No existe un bloqueo por usuario; el chat no tiene contenido de otras personas. | — |
| Moderación | Instrucciones del sistema (no inventar datos, no pedir claves) y los filtros de seguridad propios de Gemini. **No hay** un filtro adicional propio sobre las respuestas. | `android/GeminiAssistant.android.kt:23` |
| Límite de uso | 3 consultas al día, controladas **en el teléfono** y en Firestore (no hay servidor desplegado). | `shared/presentation/DinexViewModel.kt:54` |

---

## 5. Eliminación de cuenta

| Punto | Estado | Evidencia |
|---|---|---|
| Dentro de la app | Sí: Perfil → Eliminar mi cuenta | `shared/ui/NavigationComponents.kt:582` |
| Borra Firestore y Auth | Sí: borra `users/{uid}` y luego la cuenta de Firebase Auth | `shared/data/DinexCloud.kt:196`; `shared/presentation/DinexViewModel.kt:344` |
| Borra datos del teléfono | Sí: perfil financiero, foto, consentimiento y conteo de IA | `shared/data/DinexRepository.kt:105` |
| Qué queda | 1) Reportes de IA en `aiReports` (uid, texto reportado, pregunta). 2) Avisos de pago capturados y estado de "Oye Dinex" en preferencias locales. 3) Copias locales en **otros** equipos donde la cuenta siga abierta. 4) Sesión de Google en el teléfono (de Google, no de DINEX). | — |
| Página web | Sí: https://dinex-63ed4.web.app/eliminar-cuenta.html (publicada) | `webApp/src/webMain/resources/eliminar-cuenta.html` |

---

## 6. Servicios externos

| Servicio | Qué recibe | Dónde | Evidencia |
|---|---|---|---|
| Firebase Authentication (Google) | Nombre visible, correo, contraseña (la administra Firebase), token de Google | `identitytoolkit` / `securetoken.googleapis.com` | `shared/data/DinexCloud.kt:89,96` |
| Cloud Firestore (Google) | Perfil financiero completo (incluye ubicación de gastos), consentimiento, conteo de IA, reportes de IA | `firestore.googleapis.com` | `shared/data/DinexCloud.kt:146,180` |
| Firebase Storage | No se usa | — | — |
| Gemini API (Google) | Ver sección 4 | `generativelanguage.googleapis.com` | `android/GeminiClient.android.kt:65` |
| Inicio de sesión con Google (Credential Manager) | Cuenta de Google elegida | Servicios de Google Play | `android/GoogleIdentity.android.kt:12` |
| Reconocimiento de voz de Android (Google) | Audio de los comandos de voz; DINEX recibe solo el texto | Servicio de voz del sistema | `app/kotlin/.../DinexHotwordService.kt:467` |
| Geocoder de Android | Coordenadas del gasto para obtener el nombre de la calle | Servicio del sistema | `android/DeviceAutomation.android.kt:421` |
| ML Kit (texto, etiquetas, código de barras) | Foto de la boleta, **procesada en el teléfono** | Local | `shared/build.gradle.kts:108-110` |
| Open Food Facts | Solo el número del código de barras | `world.openfoodfacts.org` | `android/ReceiptScanner.android.kt:291` |
| CoinGecko | Solo qué monedas consultar (sin datos personales) | `api.coingecko.com` | `shared/data/MarketData.kt:35` |
| OpenStreetMap | Pedidos de mosaicos del mapa (revelan la zona mostrada) | `tile.openstreetmap.org` | `android/SpendingMap.android.kt:163` |
| Analítica, anuncios, Crashlytics, FCM | No existen | — | Gradle sin esos SDK |

---

## 7. Tabla para Play Console

| 1. Declaración de Google Play | 2. Qué hace actualmente DINEX | 3. Evidencia en el código | 4. Archivo y ubicación | 5. Opción sugerida en Play Console | 6. Falta confirmar |
|---|---|---|---|---|---|
| **Seguridad de los datos → ¿Recopila o comparte datos?** | Sí recopila y envía datos fuera del teléfono (Firebase, Gemini). | Subida del perfil y de reportes | `shared/data/DinexCloud.kt:146,180` | **Sí, recopila datos.** | — |
| Datos personales → Nombre | Nombre visible de la cuenta | Campo `name` en Firestore | `shared/data/DinexCloud.kt:146`; `firestore.rules:9` | Recopilado · obligatorio · **Funcionalidad de la app, Administración de la cuenta** · no compartido | — |
| Datos personales → Dirección de correo | Correo de la cuenta | Campo `email`; Firebase Auth | `shared/data/DinexCloud.kt:89,146` | Recopilado · obligatorio · Administración de la cuenta · no compartido | — |
| Datos personales → IDs de usuario | UID de Firebase | `users/{uid}`, `aiReports.uid` | `firestore.rules:9,55` | Recopilado · obligatorio · Administración de la cuenta | — |
| Datos personales → Teléfono, dirección, otros | No se recopilan | — | — | **No** | — |
| Información financiera → Otra información financiera | Movimientos, ingresos, gastos, ahorro, metas, presupuestos, recordatorios | Perfil empaquetado | `shared/data/DinexSerialization.kt:128`; `shared/data/DinexRepository.kt:160` | Recopilado · obligatorio · Funcionalidad de la app · **procesado por Google (Gemini) como proveedor** | Decidir si el envío a Gemini se declara como "compartir": Play no lo considera compartir si Google actúa como proveedor de servicios. |
| Información financiera → Historial de compras / crédito / pagos | No (no conecta bancos ni procesa pagos) | — | — | **No** | — |
| Ubicación → Aproximada y precisa | Coordenadas y calle de cada gasto, sincronizadas en Firestore; solo con la app abierta | Lectura de GPS y datos del movimiento | `android/DeviceAutomation.android.kt:330`; `shared/data/DinexSerialization.kt:132` | Recopilado · **opcional** · Funcionalidad de la app | — |
| Fotos y videos → Fotos | Foto de la boleta enviada a Gemini; no se guarda | Envío de la imagen | `android/ReceiptScanner.android.kt:88` | Recopilado · opcional · **procesamiento efímero** · Funcionalidad de la app | Solo aplica si la clave de Gemini o el servidor están activos; si no, todo se procesa en el teléfono. |
| Audio → Grabaciones de voz | Comandos de voz y "Oye Dinex" procesados por el reconocimiento de voz de Android (Google); DINEX no guarda audio | `SpeechRecognizer` | `app/kotlin/.../DinexHotwordService.kt:467` | Sugerido (conservador): Recopilado · opcional · **efímero** · Funcionalidad | Confirmar con la guía de Play si el reconocimiento del sistema cuenta como recopilación de la app. |
| Mensajes → Otros mensajes en la app | Lee avisos de pago; el texto queda en el teléfono; solo el monto se vuelve movimiento | Lector de notificaciones | `app/kotlin/.../DinexNotificationListenerService.kt:22` | **No** (no sale del teléfono); el movimiento resultante ya está en "Otra información financiera" | — |
| Actividad en la app → Otro contenido generado por el usuario | Preguntas a Dinex IA (a Gemini) y reportes de respuestas (Firestore) | Chat y reportes | `shared/presentation/AssistantViewModel.kt:124`; `shared/data/DinexCloud.kt:180` | Recopilado · opcional · Funcionalidad, seguridad y cumplimiento | — |
| Información del dispositivo / IDs / rendimiento / diagnósticos | No se envían (el modelo del teléfono solo se muestra en pantalla) | `nanoDevice` solo visible | `android/GeminiAssistant.android.kt:81` | **No** | — |
| ¿Datos cifrados en tránsito? | Sí: solo HTTPS | `require(url.startsWith("https://"))` | `android/CloudHttp.android.kt:9` | **Sí** | — |
| ¿El usuario puede pedir eliminar sus datos? | Sí, en la app y en la web | Botón y página | `shared/ui/NavigationComponents.kt:582`; `eliminar-cuenta.html` | **Sí** | — |
| **Acceso a la app** | Todo requiere cuenta | Pantalla de entrar o crear cuenta | `shared/ui/AuthScreens.kt:56` | **"Todas las funciones o algunas están restringidas"** y dar una cuenta de prueba | Crear una cuenta de prueba (correo y contraseña) para el equipo de revisión. |
| **Anuncios** | No tiene anuncios ni SDK de anuncios | Gradle sin SDK de anuncios | `shared/build.gradle.kts:104-126` | **No, mi app no contiene anuncios** | — |
| **Clasificación del contenido** | App de utilidad/finanzas; sin violencia, sexo ni apuestas; chat con IA que genera texto; no hay contenido entre usuarios | — | — | Categoría **Utilidad / Productividad**. Responder **Sí** a contenido generado por IA o interacción, si el cuestionario lo pregunta; **No** a compras digitales, apuestas o compartir ubicación con otros usuarios | Revisar el texto exacto de cada pregunta del cuestionario IARC. |
| **Público objetivo** | Jóvenes de Perú. **Los Términos permiten menores de 14 años con autorización de sus padres** | Cláusula de edad | `shared/ui/LegalScreen.kt:80` | Recomendado: **18 años o más** (evita la política de Familias en una app que maneja datos financieros) | **Decidir la edad mínima** y actualizar los Términos para que coincidan. |
| **Aplicaciones financieras** | Gestión de finanzas personales: presupuestos y registro; sin bancos, préstamos, pagos ni inversiones | Sección 3 | — | Marcar que la app **no** ofrece préstamos, banca, pagos, cripto ni inversiones; describirla como herramienta de presupuesto o gestión personal | Ver las categorías exactas del formulario "Funciones financieras". |
| **Permisos sensibles → Servicio en primer plano (micrófono)** | "Oye Dinex" opcional, con notificación permanente, se pausa tras 15 minutos sin uso | Servicio tipo micrófono | `app/AndroidManifest.xml:54`; `app/kotlin/.../DinexHotwordService.kt:87,561` | Declarar **"micrófono"**, con el texto de `docs/GOOGLE_PLAY.md` y un **video** | Grabar el video mostrando cómo se activa y se desactiva. |
| Permisos sensibles → Acceso a notificaciones | Lee solo avisos de pago; ignora chats; opcional, con explicación previa | Lector de notificaciones | `app/kotlin/.../DinexNotificationListenerService.kt:22,27` | No tiene formulario propio; describirlo en la ficha y en la política | Confirmar si Play lo pide en "Permisos y API sensibles". |
| Permisos sensibles → Ubicación | Solo con la app abierta | Sin ubicación en segundo plano | `android/DeviceAutomation.android.kt:330` | **No** requiere declaración de ubicación en segundo plano | — |
| Permisos sensibles → Burbuja (`SYSTEM_ALERT_WINDOW`) | Opcional | Burbuja | `app/kotlin/.../DinexVoiceOverlay.kt:73` | No es un permiso restringido de Play | — |
| **Contenido generado por IA** | Chat con Gemini y lectura de boletas; botón Reportar | Botón y colección `aiReports` | `shared/ui/AssistantScreen.kt:335`; `firestore.rules:55` | Indicar que la app **usa IA generativa** y **permite reportar** respuestas | Definir quién revisa `aiReports` y cada cuánto. |
| **Eliminación de cuentas** | Dentro de la app y en la web | Sección 5 | `shared/data/DinexCloud.kt:196` | URL: `https://dinex-63ed4.web.app/eliminar-cuenta.html` | Decidir qué pasa con `aiReports` al eliminar la cuenta (hoy se conservan). |
| **Política de privacidad** | Publicada en la web y dentro de la app | Página pública | `webApp/src/webMain/resources/privacidad.html` | URL: `https://dinex-63ed4.web.app/privacidad.html` | Completar lo que falta (ver sección 8). |

---

## 8. Problemas encontrados y cambios necesarios

| # | Problema | Riesgo | Cambio recomendado |
|---|---|---|---|
| 1 | **Clave de Gemini dentro del APK** (y de la web) | Alta: cualquiera puede extraerla y usarla; el límite de 3 consultas se puede saltar | Desplegar `functions/` (plan Blaze), poner `DINEX_AI_PROXY_URL` y **revocar la clave actual** |
| 2 | La **política de privacidad** dice que el chat usa Gemini Nano en el teléfono, pero no dice que, si no hay Nano, **el resumen financiero va a Gemini en la nube** | Alta: declaración inexacta | Agregarlo y subir `DinexLegal.VERSION` (pedirá aceptar de nuevo) |
| 3 | La política no menciona Open Food Facts, CoinGecko, el reconocimiento de voz de Google ni el Geocoder | Media | Agregar estos servicios en "Servicios de terceros" |
| 4 | **Términos aceptan menores de 14 años** con autorización de los padres | Alta: activa la política de Familias en Play | Definir edad mínima (recomendado 18) y alinear Términos, ficha y "Público objetivo" |
| 5 | `CONTACT_EMAIL` está vacío | Media: Play pide un correo de contacto; los derechos ARCO necesitan un canal | Definir un correo de soporte y ponerlo en la app y en la ficha |
| 6 | Los **reportes de IA no se borran** al eliminar la cuenta | Media | Borrarlos al eliminar la cuenta o declarar en la política que se conservan por seguridad (ya lo dice la página web) |
| 7 | Avisos de pago capturados y preferencias de voz quedan en el teléfono tras eliminar la cuenta | Baja | Limpiar `dinex_notification_capture` y `dinex_hotword` al eliminar o cerrar sesión |
| 8 | Base de datos local **sin cifrado propio** (sí está aislada por Android) | Baja | Opcional: cifrar con Android Keystore |
| 9 | Moderación solo por instrucciones y filtros de Gemini | Baja | Opcional: activar `safetySettings` explícitos y revisar `aiReports` con frecuencia |
| 10 | El límite diario de IA se controla en el teléfono | Media | Se resuelve con el punto 1 |
| 11 | Falta cuenta de prueba para revisión y video del servicio de micrófono | Bloquea la revisión | Crear la cuenta y grabar el video |
