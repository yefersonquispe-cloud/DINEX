# Revisión QA de Dinex

Fecha: 30 de septiembre de 2026.

## Correcciones aplicadas

- La activación de **Oye Dinex** ya no depende del permiso de notificaciones.
  El micrófono sigue siendo obligatorio; las notificaciones son opcionales.
- Se eliminó el reconocimiento forzado sin conexión, que fallaba en equipos
  Redmi/HyperOS sin el paquete local de español.
- El reinicio del reconocedor usa tareas independientes y ya no cancela otros
  eventos pendientes del servicio.
- Los accesos a ajustes del sistema tienen una ruta alternativa para evitar un
  cierre si el fabricante no implementa la pantalla detallada.
- La detección del lector de notificaciones usa la API oficial de AndroidX.
- Se corrigió el recurso del tema que usaba una propiedad de API 27 pese a que
  Dinex admite desde API 24.
- `App.kt` fue dividido por pantallas y responsabilidades. El archivo pasó de
  2,225 a 530 líneas.

## Verificaciones ejecutadas

- Compilación del APK Android.
- Pruebas unitarias Android del módulo compartido.
- Android Lint sin errores.
- Instalación limpia del APK ARM64 en emulador.
- Arranque en frío y navegación hasta **Voz y notificaciones**.
- Activación de **Oye Dinex** rechazando el permiso de notificaciones: el
  servicio continuó activo y no hubo cierre de la aplicación.
- Búsqueda de claves API incluidas, ejecución de comandos, carga dinámica de
  código, SMS, contactos, instalación de paquetes y permisos de almacenamiento
  total: no se encontraron patrones maliciosos.

## Restricciones de Android que no se pueden evadir

- HyperOS puede bloquear el lector de notificaciones para APK instalados fuera
  de Play Store. El usuario debe abrir **Información de la app → ⋮ → Permitir
  ajustes restringidos** y después conceder **Acceso a notificaciones**.
- Algunos fabricantes impiden abrir una actividad desde segundo plano. Dinex
  intenta abrir el comando y deja una notificación para continuar con un toque.
- Para mantener la escucha en Redmi se debe habilitar **Inicio automático** y
  excluir Dinex del ahorro agresivo de batería.

## Resultado de seguridad

No se encontró código malicioso ni permisos para SMS, contactos, instalación
de aplicaciones, acceso total a archivos o ejecución de comandos. Las únicas
conexiones externas del código son Gemini y Open Food Facts, usadas por el chat
y el reconocimiento de productos. No hay una clave real de Gemini versionada.
