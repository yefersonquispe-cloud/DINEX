# Importar y exportar movimientos

## Dónde está

Abre **Movimientos → Importar Excel/CSV** o **Movimientos → Exportar reporte**.
Es la misma interfaz compartida para Android, iOS y web. Android usa el selector
de documentos del sistema, iOS usa Archivos y la web usa selección/descarga local.
No se envía el archivo a un servidor ni a Gemini.

## Importación

- Formatos admitidos: `.xlsx`, `.csv`, `.tsv` y texto CSV `.txt`.
- Excel antiguo `.xls` y libros cifrados no se admiten: convertir a `.xlsx` o CSV.
- Hasta 20 MB y 20 000 movimientos por archivo; descompresión limitada a 40 MB.
- Busca una hoja con movimientos y reconoce encabezados comunes: Fecha,
  Descripción/Nombre/Concepto, Monto/Importe, Tipo, Categoría y Medio/Método.
  También reconoce columnas bancarias separadas de Cargo/Débito y Abono/Crédito.
- Conserva día, mes y año. Reconoce fechas ISO, día/mes/año y fechas seriales Excel;
  importes `1,234.56`, `1.234,56`, `20,50` y negativos. DINEX registra soles:
  no convierte divisas y no debe usarse para mezclar dólares con soles.
- Presenta vista previa, ingresos, egresos, filas rechazadas y advertencias antes
  de guardar. El botón **Importar N** confirma el registro.
- La opción de omitir repetidos está activada inicialmente. Compara fecha,
  importe, tipo, descripción y medio; puede desactivarse si hay pagos legítimos
  idénticos. No es una conciliación bancaria por ID de operación.
- Si falta el tipo, propone gasto y lo advierte. Si falta la fecha, propone hoy.
  Revisa estos casos antes de confirmar; no todos los bancos usan las mismas columnas.

Plantilla recomendada (CSV con punto y coma):

```csv
Fecha;Descripción;Categoría;Monto;Tipo;Medio;Ubicación
2026-09-01;Sueldo;Ingreso;1500.00;Ingreso;BCP;
2026-09-15;Almuerzo;Comida;20.50;Egreso;Yape;Lima
```

## Exportación

- **Excel (.xlsx):** libro real con hojas Movimientos y Resumen, montos numéricos,
  encabezado fijo, filtro y totales. Las descripciones se escriben como texto.
- **CSV:** UTF-8 con BOM, separador punto y coma y campos entrecomillados.
  Protege textos que parezcan fórmulas de Excel.
- **PDF:** reporte A4 paginado con fechas completas, ingresos, egresos y balance.
- Periodos: este mes, últimos tres meses calendario, este año, fechas elegidas
  o todo el historial. Un periodo sin movimientos no permite exportar.
- **Guardar** abre el destino del sistema en Android/iOS y descarga en web.
  Cancelar el selector no borra ni modifica los movimientos.

El reporte contiene ingresos y egresos, no es una copia de seguridad completa:
el acumulado de ahorros, recordatorios, preferencias y cuentas no se importan
ni exportan con este flujo. La aplicación aún guarda perfiles locales y no
sincroniza cuentas entre dispositivos.

## Compatibilidad con datos antiguos

Versiones anteriores guardaban únicamente el día. Se conserva la lectura de
esos registros, pero no puede reconstruirse su mes original: usan el mes/año
actual al leerlos. Los movimientos nuevos e importados sí guardan fecha completa.
El formato nuevo conserva saltos de línea, `%` y `|` en las descripciones.

## Archivos de implementación

- `shared/src/commonMain/kotlin/com/example/dinex/transfer/`: UI, CSV/XLSX/PDF,
  revisión, periodos y límites de seguridad.
- `shared/src/{androidMain,iosMain,jsMain,wasmJsMain}/kotlin/com/example/dinex/FileTransfer.*.kt`:
  selección y guardado por plataforma.
- `webApp/src/webMain/resources/dinex-files.js`: puente de archivos del navegador.
- `MovementTransferTest`, `WorkbookInteropTest`: pruebas de fechas, importes,
  hojas comprimidas, duplicados, archivos inválidos y exportación.

## Verificación

```sh
./gradlew :shared:testAndroidHostTest :shared:jsNodeTest :shared:wasmJsBrowserTest
./gradlew :androidApp:assembleDebug :webApp:jsBrowserDistribution :webApp:wasmJsBrowserDistribution
```

Las pruebas Android generan reportes con datos ficticios en
`shared/build/qa-transfer/` para abrirlos con Excel y un lector PDF independiente.
