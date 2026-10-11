# DINEX Aurora — interfaz compartida

El rediseño vive en `shared/src/commonMain/kotlin/com/example/dinex/`.
No se duplican pantallas para Android, iOS y web ni se cambia el esquema de datos.

## Dirección visual

Azul noche como base; turquesa para acciones e ingresos, violeta para ahorro/IA,
coral para egresos y ámbar para avisos. El color acompaña etiquetas e iconos:
no es la única forma de identificar un estado financiero.

- `DinexTheme.kt`: paleta, Material 3, tarjetas con degradados, tamaños y espaciado.
- `DinexVisualSystem.kt`: fondo aurora, entrada de tarjetas y encabezados.
- `DinexNavigationIcons.kt`: iconos vectoriales compartidos; no se estiran bitmaps.
- `NavigationComponents.kt`: navegación de cristal, barra lateral y burbuja de IA.
- `HomeScreen.kt`: saldo/ahorro, categorías, actividad, presupuesto y racha.
- `AssistantScreen.kt`: bienvenida, conversación, sugerencias, campo de escritura,
  envío, indicador de espera y resumen contextual lateral en escritorio.
- `InsightsScreen.kt` / `PlanScreen.kt`: presentación adaptada a columnas en web.
- `AuthScreens.kt` / `DinexLaunchScreen.kt`: acceso y presentación de marca.

## Movimiento

- Aurora periférica: ciclo suave de 14 segundos, sin mover el texto ni los controles.
- Entrada de tarjetas: 380 ms; transiciones entre pantallas: hasta 320 ms.
- Gráfico circular: revelado de 650 ms; barras y progreso: transición de valores.
- Burbuja IA: respiración visual lenta; puntos animados solo durante una respuesta.
- **Perfil → Reducir movimiento** detiene la aurora/respiración/puntos y elimina
  entradas, desplazamientos y transiciones animadas. Se conserva **Texto más grande**.

El acabado de cristal usa capas, transparencias y bordes reflectantes compartidos;
no depende de la API Liquid Glass de Apple ni de filtros de desenfoque costosos.

## Funciones conservadas

Navegación, registro, persistencia, permisos, importación/exportación y conexión
existente del asistente no se sustituyen por simulaciones visuales. El rediseño no
añade un nuevo proveedor de IA: la web conserva su respuesta local existente y
Android/iOS conservan su configuración de Gemini. No se promete sincronización
remota ni lectura de notificaciones de otras apps en iOS/web.
