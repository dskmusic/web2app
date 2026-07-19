# Manual — desplegar Web2App en GitHub con auto-actualización

Cada vez que quieras publicar una nueva versión, lanzas manualmente el workflow "Build & Release"
desde la pestaña Actions (o desde `xSync-repo_GitHub.bat`, que te lo pregunta al final). GitHub
Actions compila el APK, lo firma, sube `versionCode`/`versionName` y publica un Release con el
APK adjunto. La propia app comprueba ese Release al abrir (y desde Ajustes) y te ofrece
descargar e instalar la actualización.

Todo lo de **código** (`UpdateChecker.kt`, el manifest, el workflow `.yml`) ya está hecho.
Esto es lo que tienes que hacer tú, paso a paso.

## 1. Crear el repositorio en GitHub

1. Entra en tu cuenta de GitHub y crea un repositorio nuevo, por ejemplo `web2app`. Puede ser
   **público** (recomendado, así `raw.githubusercontent.com` funciona sin autenticación — es lo
   que usa la app para leer `version.json`).
2. Anota el nombre exacto que le pongas (usuario/repositorio), lo necesitas en el paso 3.

## 2. Subir el proyecto

Desde la carpeta del proyecto (`D:\Documentos\_Android\_AndroidStudioApps\web2app`):

```bash
git init
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin https://github.com/dskmusic/web2app.git
git push -u origin main
```

## 3. Generar un keystore de release

```bash
keytool -genkey -v -keystore web2app-release.keystore -alias web2app -keyalg RSA -keysize 2048 -validity 10000 -storetype JKS
```

`-storetype JKS` es importante: los JDK recientes generan `PKCS12` por defecto con una
codificación que la librería de firmado de Android Gradle Plugin no sabe leer (falla con
`KeytoolException: Tag number over 30 is not supported`). `JKS` evita ese problema.

Te pedirá una contraseña para el keystore y otra (puede ser la misma) para la clave. Guarda
ese archivo `.keystore` en un sitio seguro **fuera del repositorio** — nunca lo subas a GitHub.

## 4. Codificar el keystore en base64

En PowerShell:
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("web2app-release.keystore")) | Set-Clipboard
```
Esto copia el resultado directamente al portapapeles.

## 5. Crear los secretos en GitHub

En el repositorio: **Settings → Secrets and variables → Actions → New repository secret**.
Crea estos cinco, exactamente con estos nombres:

| Nombre | Valor |
|---|---|
| `KEYSTORE_BASE64` | El texto largo que copiaste en el paso 4 |
| `KEYSTORE_PASSWORD` | La contraseña del keystore |
| `KEY_ALIAS` | El alias que usaste (`web2app` en el ejemplo del paso 3) |
| `KEY_PASSWORD` | La contraseña de la clave |
| `PIXABAY_API_KEY` | Tu API key gratuita de [pixabay.com/api/docs](https://pixabay.com/api/docs/) (para el buscador de imágenes online) |

## 6. Dar permisos de escritura a las Actions

**Settings → Actions → General → Workflow permissions** → marca **"Read and write permissions"**
→ Guardar. Sin esto falla el commit automático del `versionCode` y la creación del Release.

## 7. Primera ejecución manual

Ve a la pestaña **Actions** → selecciona el workflow **"Build & Release"** → **Run workflow**.

Si todo va bien, en unos minutos tendrás:
- Un nuevo commit automático (sube `versionCode`/`versionName`).
- Un Release nuevo en la pestaña **Releases**, con el APK adjunto.
- Un archivo `version.json` en la raíz del repositorio.

## 8. Comprobar que la app lo detecta

Instala la app (la que compilaste tú mismo, con un `versionCode` menor al que acaba de publicar
el workflow) y ábrela. Debería aparecer el diálogo de actualización. Al pulsar "Descargar e
instalar", Android pedirá permiso para instalar aplicaciones de esta fuente la primera vez
(actívalo cuando lo pida) y luego se abrirá el instalador normal.

## A partir de aquí

Cada vez que quieras publicar una nueva versión: o bien ejecuta `xSync-repo_GitHub.bat` y
responde "S" a la pregunta final, o entra directamente en **Actions → Build & Release → Run
workflow**. El `versionCode` sube en 1 automáticamente en cada release, así que la app siempre
sabrá distinguir cuál es más reciente.
