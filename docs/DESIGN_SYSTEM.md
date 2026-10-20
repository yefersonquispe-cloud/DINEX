# DINEX — diseño verde y blanco, claro y oscuro

El rediseño vive en `shared/src/commonMain/kotlin/com/example/dinex/`.
No se duplican pantallas para Android, iOS y web ni se cambia el esquema de datos.

## Dirección visual

En modo claro: blanco cálido como fondo, tarjetas blancas y verde profundo en acciones,
navegación activa y la tarjeta de saldo. En modo oscuro: fondo verde pizarra,
tarjetas verde bosque y textos marfil con acentos salvia. La tarjeta de saldo
conserva un verde profundo y cifras blancas en ambos modos. El ahorro usa superficies verdes.
Los gráficos mantienen colores secundarios
para distinguir categorías: coral, violeta, ámbar y azul. El color acompaña etiquetas e iconos:
no es la única forma de identificar un estado financiero.

- `DinexTheme.kt` / `DinexCatalog.kt`: dos paletas Material 3, tarjetas, tamaños y espaciado.
- `DinexVisualSystem.kt`: fondo aurora, entrada de tarjetas y encabezados.
- `DinexNavigationIcons.kt`: iconos vectoriales compartidos; no se estiran bitmaps.
- `NavigationComponents.kt`: dock compacto superpuesto junto al borde inferior; las listas
  reservan espacio solo al final del desplazamiento para no tapar el último elemento.
  Incluye barra lateral en escritorio, perfil de pantalla completa y burbuja de Dinex IA.
- `HomeScreen.kt`: saldo/ahorro, categorías, actividad, presupuesto y racha.
- `AssistantScreen.kt`: bienvenida, conversación, sugerencias, campo de escritura,
  envío, indicador de espera y resumen contextual lateral en escritorio.
- `InsightsScreen.kt` / `PlanScreen.kt`: presentación adaptada a columnas en web.
  Las categorías reciben límites sugeridos según ingresos del mes e historial previo;
  un solo botón junto al presupuesto diario abre el editor flotante. Sin edición,
  las cifras siguen siendo automáticas; los cambios manuales permanecen guardados.
- `AuthScreens.kt` / `DinexLaunchScreen.kt`: acceso y presentación de marca.

## Movimiento

- Aurora periférica: ciclo suave de 14 segundos, sin mover el texto ni los controles.
- Entrada de tarjetas: 380 ms; transiciones entre pantallas: hasta 320 ms.
- Gráfico circular: revelado de 650 ms; barras y progreso: transición de valores.
- Burbuja IA: respiración visual lenta; puntos animados solo durante una respuesta.
- **Perfil → Reducir movimiento** detiene la aurora/respiración/puntos y elimina
  entradas, desplazamientos y transiciones animadas. Se conserva **Texto más grande**.
- **Perfil → Apariencia** cambia entre claro y oscuro y recuerda la elección en
  el almacenamiento local de cada plataforma, también después de cerrar sesión.

La navegación usa capas adaptativas, transparencias suaves y bordes verdes compartidos;
no depende de la API Liquid Glass de Apple ni de filtros de desenfoque costosos.

## Funciones conservadas

Navegación, registro, persistencia, permisos, importación/exportación y conexión
existente del asistente no se sustituyen por simulaciones visuales. El rediseño no
añade un nuevo proveedor de IA: la web conserva su respuesta local existente y
Android/iOS conservan su configuración de Gemini. No se promete sincronización
remota ni lectura de notificaciones de otras apps en iOS/web.
