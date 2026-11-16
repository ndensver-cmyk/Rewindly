# Rewindly

Estadísticas musicales personales, historias animadas e informes — 100 % locales. App Android (Kotlin + Jetpack Compose + Room).

> Rewindly es un proyecto independiente. No está afiliado, patrocinado ni aprobado por Spotify. No usa logos ni marca de Spotify.

## Cómo entran los datos (sin poner en riesgo tu cuenta)

Rewindly **nunca** pide tu usuario, contraseña, cookies ni tokens de Spotify y no usa endpoints privados.

| Fuente | Tiempo de espera | Qué aporta | Precisión |
|---|---|---|---|
| **Registro en vivo** (acceso a notificaciones) | Inmediato | Lee la sesión multimedia pública que Spotify publica en Android (la misma que usa la pantalla de bloqueo). Mide el tiempo real escuchado, pausas y saltos. | Real |
| **Last.fm** (scrobbling oficial de Spotify) | Inmediato | Historial desde que conectaste Last.fm. Spotify › Ajustes › Aplicaciones › Last.fm. En Rewindly: tu usuario + API key gratuita. | Tiempo **estimado** |
| **Datos de la cuenta** (ZIP) | ~5 días | Último año: fecha, canción, artista, ms. | Real |
| **Historial extendido** (ZIP) | Hasta 30 días | Toda la vida de la cuenta, con álbum, saltos, shuffle, etc. | Real |

Recomendación: activa el registro en vivo hoy, conecta Last.fm para tener pasado reciente, y solicita **ambos** ZIP a la vez en spotify.com/account/privacy. Cuando lleguen, impórtalos: Rewindly deduplica entre fuentes (huella fecha+canción+artista+álbum+ms y ventana de ±3 min entre fuentes) y da prioridad al ZIP extendido > ZIP de cuenta > vivo > Last.fm. Los posibles duplicados se marcan como ambiguos, no se borran.

## Piloto automático (v1.1)
Configúralo una vez y Rewindly se alimenta solo:
- **Registro en vivo**: Android lo reactiva tras reiniciar.
- **Last.fm**: sincroniza cada 3 h con conexión y al abrir la app.
- **Detección del ZIP**: busca `my_spotify_data*.zip` y `Streaming_History*.json` en Descargas (con "acceso a archivos") o en una carpeta que elijas, cada hora y al abrir la app, y los importa sin pasos manuales. Solo abre archivos con nombre de exportación de Spotify.
- **Seguimiento de la solicitud**: marca "Ya lo pedí" y te avisa a los 5 días (datos de la cuenta) y a los 30 (historial extendido).
- **Informes**: semanal cada lunes y mensual cada día 1.
- También puedes tocar el ZIP en el correo y elegir "Abrir con Rewindly".

## Diseño
Monocromo y editorial: tema negro o blanco (o según el sistema), titulares en Instrument Serif, texto en Inter Tight y datos en JetBrains Mono (todas bajo licencia SIL Open Font License).

## Funciones
- Estadísticas: tiempo total, top canciones/artistas/álbumes, horas y días favoritos, sesiones (corte configurable, 30 min por defecto), rachas, repeticiones, saltos, descubrimiento, nostalgia, diversidad (entropía de Shannon). Cada dato indica si es real, estimado o no disponible.
- Personalidad musical (Repetidor, Explorador, Nostálgico, Nocturno, Especialista, Ecléctico, Maratonista, Incondicional) — es un juego, no un diagnóstico.
- Informes semanal, 15 días, mensual y personalizado; historias animadas con swipe; exportación PNG/PDF y compartir.
- Recordatorios con WorkManager; borrar todos los datos desde Ajustes.

## Privacidad y seguridad
- Todo se procesa en el dispositivo. La única conexión de red es opcional (Last.fm).
- No se guardan IP, nombre de usuario ni user agent de los archivos de Spotify. Se ignoran vídeos y podcasts.
- ZIP: límite de tamaño, de número de entradas y de ratio de compresión (protección zip-bomb), y rechazo de rutas sospechosas.
- Datos excluidos de copias de seguridad automáticas.

## Instalar
Descarga `Rewindly.apk` desde **Releases**, ábrelo en el teléfono y permite "instalar apps desconocidas". Android 8.0+.

## Compilar
```bash
./gradlew testDebugUnitTest assembleRelease
```
GitHub Actions compila en cada push; al crear un tag `v*` publica el APK en Releases.
