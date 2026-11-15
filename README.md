# iptv-android

App Android nativa (Kotlin + Jetpack Compose + Media3/ExoPlayer) para ver un servicio IPTV compatible con la API de **Xtream Codes** (`player_api.php`): TV en vivo, películas y series.

Nace como versión nativa de la web del mismo servicio, para lo que el navegador no puede hacer: reproducir audio **AC-3 / E-AC-3 / DTS** (con FFmpeg por software si el dispositivo no lo decodifica), MKV, y **elegir pistas de audio y subtítulos**.

## Qué hace

- **Cuentas:** servidor + usuario + contraseña, con nombre opcional. Varias cuentas por dispositivo, guardadas cifradas con el Android Keystore.
- **TV en vivo:** carpetas | canales | previsualizador en tres columnas. Ampliar el canal no lo recarga. Selector **HLS / TS** (si HLS falla pasa solo a TS), guía ahora/después, favoritos y ventana flotante (picture-in-picture).
- **Películas y series:** pósters por carpeta, ficha, temporadas y episodios. Incluye **Continuar** donde quedaste, episodios marcados como vistos y paso automático al siguiente.
- **Reproductor:** botones de **Audio** y **Subtítulos**; la elección se recuerda para los próximos videos.
- **Búsqueda global:** canales, películas y series a la vez.
- **Catálogo guardado en el dispositivo:** abre al instante y se renueva cada 6 horas (o con "Actualizar ahora" en Cuenta).
- Solo horizontal. Textos en español.

La app habla **directo con el panel**: no necesita servidor propio ni proxy.

## Requisitos para compilar

No hace falta Android Studio:

- **JDK 17**: en Windows, `winget install Microsoft.OpenJDK.17`.
- **Android SDK command-line tools**, con `platform-tools`, `platforms;android-35` y `build-tools;35.0.0` instalados mediante `sdkmanager`.
- Gradle no se instala: el proyecto trae su wrapper (`gradlew` / `gradlew.bat`).

## Configuración

Crea `local.properties` en la raíz. **No se sube a git**, porque lleva datos de cada instalación:

```properties
sdk.dir=C\:\\Android\\Sdk

# Servidor que aparece por defecto en el login (el usuario puede cambiarlo).
panel.url=http://servidor.ejemplo.com:8080

# true = versión de revendedor: el servidor queda fijo y no se muestra en el login.
panel.lock=false

# Marca (opcionales).
brand.name=Lytra TV
brand.color=#a78bfa
```

## Comandos

```bash
./gradlew assembleDebug        # APK en app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # tests unitarios (formato, lectura tolerante del panel, títulos)
./gradlew installDebug         # instala en el dispositivo conectado por adb
```

En Windows, usa `gradlew.bat` y define `JAVA_HOME` y `ANDROID_HOME` antes de ejecutarlo.

Para instalar sin cable, copia el `.apk` al teléfono y ábrelo, o arrástralo a la ventana de un emulador como MuMuPlayer.

## Estructura

```
app/src/main/java/app/iptv/player/
  data/     API Xtream, modelos tolerantes, sesión cifrada, catálogo (memoria + disco), favoritos, avance
  domain/   formato y mapeo puros (con tests)
  player/   PlaybackSession (Media3 + FFmpeg), VideoSurface (barra con audio/subtítulos), PlayerActivity
  ui/       AppShell (navegación), live/, vod/, search/, account/, login/, components/, theme/
```

## Licencias de terceros

- Fuentes **Geist**, **Geist Mono** e **Instrument Serif**: SIL Open Font License 1.1 (ver `licencias/`).
- Decodificador FFmpeg para Media3: `org.jellyfin.media3:media3-ffmpeg-decoder`.
