# Voz, burbuja y notificaciones

## Activación en el teléfono

1. Abre Dinex, inicia sesión y entra en **Perfil → Voz y notificaciones**.
2. Concede micrófono. Activa **Oye Dinex** mientras Dinex está abierta.
3. Pulsa **Permitir burbuja flotante** y habilita **Mostrar sobre otras apps**.
4. Sal de Dinex con Inicio. Aparece una burbuja arrastrable **D**.
5. Di **Oye Dinex** y luego **Registra 20 soles en comida**, o toca la burbuja.
   El panel permite Hablar, Abrir Dinex, Minimizar y Desactivar voz y burbuja.
   Un comando válido se guarda en el perfil activo de SQLite. Al volver a la app
   se actualizan los datos; sin sesión no se registra dinero.
6. Para capturar transacciones, pulsa **2. Dar acceso** y activa
   **Dinex · Captura de movimientos**. Al volver, revisa el indicador de conexión.
   Si está autorizado pero desconectado, pulsa **Reconectar lector**.
7. Revisa e importa las sugerencias capturadas. Abrir o cerrar la lista no las
   elimina; solo se confirman después del guardado. La aplicación no mueve dinero.

### Ingresos y gastos

El mismo lector reconoce ambos sentidos. Por ejemplo, **«te yapearon S/ 20»**,
**«Ana te yapeó S/ 25»**, **«has recibido S/ 100»** y **«abono a tu cuenta»**
se proponen como ingresos; **«yapeaste»**, **«pagaste»** o **«transferencia enviada»**
se proponen como egresos. Necesita un monto visible en soles y un texto reconocible:
una alerta oculta o sin importe no puede registrarse de forma fiable.

Se conserva la fecha local de la notificación al confirmar. Si se reconecta el
servicio, también revisa las últimas notificaciones todavía visibles; no puede
recuperar las que ya fueron descartadas. Los textos ambiguos requieren revisión.

## Si Android/HyperOS deniega el acceso

En APK instaladas fuera de una tienda, Android puede bloquear permisos especiales.
Ve a **Ajustes → Aplicaciones → Dinex → menú ⋮ → Permitir ajustes restringidos**
si esa opción aparece. Luego vuelve a habilitar el acceso a notificaciones.
La ruta exacta depende del fabricante y versión; Dinex incluye acceso a Información
de la aplicación y al ajuste general como alternativas.

En Redmi/Xiaomi también revisa **Inicio automático**, las restricciones de batería
y los permisos de ventanas en segundo plano. La app ofrece un botón para abrir
esos ajustes, pero no cambia tus permisos ni desactiva protecciones sin tu acción.

## Qué puede y qué no puede hacer

- El manifiesto declara lector de notificaciones, servicio de micrófono y ventanas
  flotantes. Incluye consultas del servicio de reconocimiento y tipos de notificación
  silenciosa, alerta, conversación y continua.
- El lector no está limitado a una lista de bancos: analiza notificaciones visibles
  con frases de transacciones y montos en soles. Ignora la propia app, resúmenes
  de grupos, promociones comunes, OTP y pagos rechazados. No lee la bandeja de correo
  ni mensajes ocultos por Android, y puede necesitar revisión ante textos ambiguos.
- La voz es optativa y utiliza reconocimiento del sistema con una notificación
  permanente de micrófono. No es un asistente de hardware del sistema; puede requerir
  conexión a internet y consumir batería. No garantiza escucha permanente después
  de un cierre forzado, reinicio o bloqueo del servicio por el fabricante.
- No se reinicia automáticamente un servicio de micrófono desde el fondo. Debes
  abrir Dinex y activarlo de nuevo si Android lo detiene. Sin permiso de superposición
  se ofrece una notificación para abrir la pantalla de voz. Con pantalla bloqueada
  no se abre el panel flotante de comandos automáticamente.
- En iOS y web se mantiene la interfaz/voz disponible en primer plano. Las burbujas
  sobre otras apps y lectura de notificaciones ajenas son capacidades de Android.
- Desactiva desde el panel flotante, la notificación permanente o el interruptor
  en Dinex. Cerrar sesión también detiene la escucha.

## Referencias oficiales

- https://developer.android.com/reference/android/service/notification/NotificationListenerService
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- https://support.google.com/android/answer/12623953?hl=es

## Validación pendiente en hardware

Las compilaciones y pruebas automatizadas no sustituyen las pruebas en un Redmi
con su versión específica de HyperOS. No se ha conectado un teléfono físico durante
esta revisión. Comprueba permisos, reinicio, bloqueo de pantalla y consumo de batería
en el dispositivo final antes de publicar.
