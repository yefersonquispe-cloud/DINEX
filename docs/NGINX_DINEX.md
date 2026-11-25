# Nginx para Dinex

Configura `dinex.tecsup20.site` con los archivos de `webApp/build/dist/js/productionExecutable`. El certificado TLS lo instala el administrador del servidor. Conserva el proxy actual de `/alcancia/` hacia Node-RED: debe tener prioridad sobre la ruta SPA.

```nginx
map $uri $dinex_cache {
    default "";
    ~*\.(html|js|mjs)$ "no-cache";
    ~*[.-][0-9a-f]{8,}\.wasm$ "public, max-age=31536000, immutable";
}
server {
    listen 80;
    server_name dinex.tecsup20.site;
    return 301 https://$host$request_uri;
}
server {
    listen 443 ssl http2;
    server_name dinex.tecsup20.site;
    root /srv/dinex/web;
    index index.html;
    ssl_certificate /etc/letsencrypt/live/dinex.tecsup20.site/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/dinex.tecsup20.site/privkey.pem;

    include mime.types;
    types { application/wasm wasm; }
    gzip on;
    gzip_types application/javascript text/javascript text/css text/html application/json application/wasm image/svg+xml;
    gzip_min_length 1024;

    add_header Content-Security-Policy "default-src 'self'; script-src 'self' 'wasm-unsafe-eval' https://accounts.google.com/gsi/client; style-src 'self' 'unsafe-inline' https://accounts.google.com/gsi/style; img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self' https://accounts.google.com/gsi/ https://identitytoolkit.googleapis.com https://securetoken.googleapis.com https://firestore.googleapis.com https://generativelanguage.googleapis.com https://api.coingecko.com https://*.cloudfunctions.net https://*.a.run.app; frame-src https://accounts.google.com/gsi/; worker-src 'self' blob:; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'; upgrade-insecure-requests" always;
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-Frame-Options "DENY" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;
    add_header Permissions-Policy "camera=(self), microphone=(self), geolocation=(self), payment=(), usb=()" always;
    add_header Cross-Origin-Opener-Policy "same-origin-allow-popups" always;

    add_header Cache-Control $dinex_cache always;

    # Mantén aquí la configuración actual del proxy de /alcancia/.
    # Su location debe ganar antes del fallback de la SPA.
    location ^~ /alcancia/ {
        # Node-RED atiende /dinex. La sesión viaja en Authorization.
        proxy_pass http://127.0.0.1:1880/dinex/;
        proxy_set_header Authorization $http_authorization;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location = /privacidad.html { try_files $uri =404; }
    location = /eliminar-cuenta.html { try_files $uri =404; }
    location ~* "[.-][0-9a-f]{8,}\.wasm$" { try_files $uri =404; }
    location ~* \.(html|js|mjs)$ { try_files $uri =404; }
    location / { try_files $uri $uri/ /index.html; }
}
```

Comprueba la sintaxis con `nginx -t` y luego recarga el servicio.
