# DINEX en Google Play

Guía para completar Play Console con lo que la app hace de verdad (revisado en el código el 19/11/2026).

## 1. Datos de la app

| Campo | Valor |
|---|---|
| Nombre del paquete | `com.yefersonquispe.dinex` (cambiado antes de la primera publicación; Play Console lo marcó como disponible) |
| Versión | 1.6 (versionCode 7) |
| minSdk / targetSdk | 26 / 36 |
| Política de privacidad (URL pública) | `https://dinex.tecsup20.site/privacidad.html` (sale de `webApp/src/webMain/resources/privacidad.html`; requiere publicar Hosting) |
| Eliminación de cuenta (URL pública) | `https://dinex.tecsup20.site/eliminar-cuenta.html` |
| Eliminación dentro de la app | Perfil → Eliminar mi cuenta (borra Firestore y la cuenta de Firebase Auth) |

## 2. Seguridad de los datos (formulario "Data safety")

Todo viaja cifrado (HTTPS). La persona puede pedir que se borren sus datos (eliminar cuenta). Nada se vende ni se usa para publicidad.

| Tipo de dato (categoría de Play) | ¿Se recopila? | ¿Se comparte? | Obligatorio | Para qué |
|---|---|---|---|---|
| Información personal → Nombre | Sí | No | Sí | Cuenta |
| Información personal → Correo electrónico | Sí | No | Sí | Cuenta, inicio de sesión |
| Información financiera → Otra información financiera (movimientos, metas, presupuestos, recordatorios) | Sí | No* | Sí | Funcionalidad de la app |
| Ubicación → Ubicación precisa | Sí | No | Opcional | Lugar de cada gasto (mapa de calor), solo con la app abierta |
| Fotos y videos → Fotos (comprobantes) | Sí, solo al escanear | No* | Opcional | Leer el total de la boleta |
| Audio → Grabaciones de voz | Se procesan, no se guardan | No* | Opcional | Comandos de voz y "Oye Dinex" |
| Mensajes → Otros mensajes en la app (avisos de pago leídos de notificaciones) | Sí, solo avisos de pago | No | Opcional | Registro automático de Yape, Plin y bancos |
| Actividad en la app → Otras acciones (conteo diario de Dinex IA, reportes de respuestas de IA) | Sí | No | Sí | Límite de uso y seguridad de la IA |

\* Proveedores de servicio que procesan datos en nombre de Dinex (Play no lo cuenta como "compartir"):
Google Firebase (Auth y Firestore), Google Gemini (resumen financiero de la persona al preguntar a Dinex IA
en la nube, e imagen de la boleta), y el reconocimiento de voz de Google del teléfono.

## 3. Permisos y declaraciones

| Permiso | Uso | Qué declarar |
|---|---|---|
| `RECORD_AUDIO` | Comandos de voz y "Oye Dinex" | Se pide al usarlo; explicado en la pantalla de Registro automático |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE` | Servicio "Oye Dinex" con notificación permanente | **Declaración de servicio en primer plano** (texto abajo) y un video corto |
| `SYSTEM_ALERT_WINDOW` | Burbuja flotante con la mascota | No es permiso restringido de Play; se pide desde la app con explicación |
| `ACCESS_FINE/COARSE_LOCATION` | Lugar del gasto, solo con la app abierta | No hay ubicación en segundo plano |
| `POST_NOTIFICATIONS` | Alertas de presupuesto y recordatorios | — |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Leer avisos de pago | Divulgación destacada antes de activarlo (ya existe). Ahora ignora chats y conversaciones |

**Texto sugerido para la declaración del servicio de micrófono:**

> Oye Dinex es un asistente de voz opcional que la persona activa expresamente desde la app. Mientras está activo se
> muestra una notificación permanente con el botón "Desactivar" y una burbuja flotante. El servicio usa el micrófono para
> reconocer la frase "Oye Dinex" y los comandos de registro de gastos e ingresos o consultas de saldo. La escucha manos
> libres se pausa automáticamente tras 15 minutos sin uso; cada comando tiene un tiempo máximo de 12 segundos. El audio no
> se guarda. Si la persona no lo activa, el servicio nunca se inicia.

## 4. Contenido generado por IA

- Dinex IA (Gemini) responde preguntas sobre las finanzas de la propia persona.
- Cada respuesta tiene el botón **Reportar** (ofensiva, peligrosa, incorrecta u otra). El reporte guarda el texto exacto
  de la respuesta, la pregunta, el motor y la fecha en `aiReports/{id}`; nadie puede leerlos desde la app.
- Revisión: Firebase Console → Firestore → colección `aiReports`.

## 5. Firma y exportación

- **Play App Signing:** aceptar. Falta crear la clave de subida (upload key) al generar el primer AAB.
- **Leyes de exportación de EE. UU.:** Dinex solo usa el cifrado estándar del sistema (HTTPS). No tiene cifrado propio.

## 6. Pendiente de configurar a mano

1. Publicar Hosting para que existan las URL de privacidad y de eliminación de cuenta.
2. Desplegar las reglas de Firestore (colección `aiReports`).
3. Servidor seguro de Dinex IA (carpeta `functions/`): necesita el plan Blaze. Ver la sección 7.
4. Cliente OAuth de Android en Google Cloud para `com.yefersonquispe.dinex` con el SHA-1 de depuración y el de Play.
5. Correo de contacto (`DinexLegal.CONTACT_EMAIL`) para la ficha de Play y los derechos ARCO.
6. La política de privacidad dice que Dinex IA usa Gemini Nano en el teléfono; conviene agregar que, si Nano no está
   disponible, se envía el resumen financiero a Gemini en la nube. Cambiarla obliga a subir `DinexLegal.VERSION`.

## 7. Servidor seguro de Gemini (para sacar la clave del APK)

```bash
cd functions && npm install
npx -y firebase-tools@15.35.0 functions:secrets:set GEMINI_API_KEY --project dinex-63ed4
npx -y firebase-tools@15.35.0 deploy --only functions,firestore:rules --project dinex-63ed4
```

Luego agregar a `local.properties` la URL que muestre el despliegue:

```
DINEX_AI_PROXY_URL=https://us-central1-dinex-63ed4.cloudfunctions.net/dinexAi
```

Con esa línea, el APK y la web **ya no incluyen** la clave de Gemini, el límite de 3 consultas lo controla el servidor y
cada pedido exige la sesión de Firebase. Después hay que revocar la clave anterior en Google Cloud, porque estuvo en APKs.
