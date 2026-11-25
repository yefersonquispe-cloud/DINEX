# Dinex YUPAY: servidor (Node-RED)

La app y la web de DINEX consultan `https://dinex.tecsup20.site/alcancia/api/` con la sesión de Firebase de la cuenta (`Authorization: Bearer …`). Node-RED comprueba la firma de Google y solo deja entrar a las cuentas autorizadas.

## Estado

- **Flujo de Node-RED:** publicado el 25/11/2026. Las cuentas autorizadas están en las variables de la pestaña: `YUPAY_CUENTAS` (correos separados por coma) o `YUPAY_UIDS` (UID de Firebase). Sin ninguno, no entra nadie.
- **Web:** se copian los archivos de `webApp/build/dist/js/productionExecutable` a la carpeta que sirve nginx. La web anterior usaba `api/monedas` y sumaba las monedas al saldo; esta no.
- El proxy de `/alcancia/` debe seguir apuntando a `/dinex/` y dejar pasar `Authorization`. Ver `NGINX_DINEX.md`.

## Comprobar

- `https://dinex.tecsup20.site/alcancia/api/estado` sin sesión responde **401**.
- `https://dinex.tecsup20.site/alcancia/` lleva a DINEX.
- Con una cuenta autorizada, Dinex YUPAY muestra las monedas; con otra dice "Esta alcancía no está vinculada a tu cuenta".
- Si dice "Verifica tu correo", entra con Google, verifica el correo o usa el UID.

## Respuestas

| Código | Significado |
|---|---|
| 401 `SESION` | Sin sesión o vencida |
| 403 `CUENTA` / `VERIFICA_CORREO` | Cuenta no autorizada / correo sin verificar |
| 200 `OFFLINE` (`ok:false`) | El ESP32 está apagado; la orden no se envió |
| 429 `ESPERA` | Más de una orden por segundo |
| 503 `CERTIFICADOS` | Aún no se leyeron los certificados de Google |
