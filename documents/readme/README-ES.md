<div align="center">
  <img src="../documents/assets/logo-with-brand.png" alt="pigeonpod" width="120" />
  <h2>Escucha YouTube y Bilibili. En cualquier lugar.</h2>
  <h3>Si el auto-hospedaje no es lo tuyo, echa un vistazo a nuestros próximos servicios en línea:
    <a target="_blank" href="https://pigeonpod.cloud/?utm_source=github&utm_medium=repo&utm_campaign=readme&utm_content=cta">PigeonPod</a>
  </h3>
</div>

<div align="center">
  
[English](../../README.md) | [简体中文](README-ZH.md) | [Português](README-PT.md) | [日本語](README-JA.md) | [Deutsch](README-DE.md) | [Français](README-FR.md) | [한국어](README-KO.md)
</div>

## Capturas de Pantalla

![index-dark&light](../assets/screenshots/Snipaste_2025-11-23_23-17-24.png)
<div align="center">
  <p style="color: gray">Lista de canales</p>
</div>

![detail-dark&light](../assets/screenshots/Snipaste_2025-11-23_23-16-12.png)
<div align="center">
  <p style="color: gray">Detalle del canal</p>
</div>

## Características Principales

- **🎯 Suscripción inteligente y vista previa**: Pega cualquier URL de canal o playlist de YouTube o Bilibili, detecta automáticamente el tipo y permite previsualizar el feed y los episodios antes de suscribirte.
- **📻 RSS seguro para cualquier cliente**: Genera enlaces RSS estándar para canales y playlists, protegidos con API Key y compatibles con cualquier aplicación de podcasts.
- **🤖 Sincronización automática y histórico**: Sincroniza periódicamente las nuevas publicaciones en segundo plano, con número inicial de episodios configurable por feed y carga de episodios históricos con un solo clic.
- **🎦 Salida de audio/vídeo flexible**: Elige descargas solo audio (AAC) o vídeo, con niveles de calidad o selección de resolución/codificación, e incrusta automáticamente metadatos, capítulos y carátulas.
- **🍪 Soporte para contenido restringido**: Usa claves de YouTube Data API y cookies subidas para acceder de forma más fiable a contenido con restricción de edad o solo para miembros.
- **📦 Descarga por lotes de episodios históricos**: Diseñado específicamente para descargar historial con eficiencia, con búsqueda, paginación, selección por episodio o por página y envío en un clic.
- **📊 Panel de descargas y acciones masivas**: Panel en tiempo real para tareas Pendiente/Descargando/Completado/Fallido, con registro de errores y acciones masivas de cancelar/eliminar/reintentar con un clic.
- **🔍 Filtros y retención por suscripción**: Filtra episodios por palabras clave en título/descripción (incluir/excluir), duración mínima y define por feed el estado de sincronización y el número máximo de episodios a conservar.
- **⏱ Descarga automática diferida para episodios nuevos**: Configura ventanas de retraso por feed para mejorar la tasa de éxito de `--sponsorblock` en videos recién publicados.
- **📈 Información de uso de la API de YouTube**: Supervisa el uso de cuota y los límites de la API para planificar sincronizaciones y evitar interrupciones inesperadas.
- **🎛 Feeds personalizables y reproductor integrado**: Personaliza título y portada por feed y utiliza el reproductor web integrado para escuchar rápidamente audio o vídeo.
- **🔄 Exportación de suscripciones en OPML**: Exporta todas las suscripciones como un archivo OPML estándar para migrar fácilmente entre distintos clientes de podcast.
- **🧩 Gestión y control de episodios**: Lista de episodios con scroll infinito, descarga manual de episodios individuales, reintento, cancelación y eliminación que también gestionan los archivos localmente.
- **⬆️ Actualización de yt-dlp dentro de la app**: Actualiza con un clic el runtime integrado de yt-dlp para mantener al día la compatibilidad de extracción y descarga.
- **🛠 Argumentos avanzados de yt-dlp**: Añade argumentos personalizados de yt-dlp con sintaxis estándar para ajustar el comportamiento de descarga en casos avanzados.
- **🌐 Interfaz multilingüe y responsiva**: Interfaz completamente localizada (inglés, chino, español, portugués, japonés, francés, alemán y coreano) con diseño responsivo para escritorio y móviles.
- **📚 Soporte de capítulos Podcasting 2.0**: Genera archivos de capítulos `chapters.json` estándar para que más clientes de podcast puedan mostrar la navegación por capítulos.

## Despliegue

### Usando Docker Compose (Recomendado)

**Asegúrate de tener Docker y Docker Compose instalados en tu máquina.**

1. Utiliza el archivo de configuración docker-compose, modifica las variables de entorno según tus necesidades:
```yml
version: '3.9'
services:
  pigeon-pod:
    image: 'ghcr.io/aizhimou/pigeon-pod:latest' 
    restart: unless-stopped
    container_name: pigeon-pod
    ports:
      - '8834:8080'
    environment:
      - SPRING_DATASOURCE_URL=jdbc:sqlite:/data/pigeon-pod.db # set to your database path
    volumes:
      - data:/data

volumes:
  data:
```

2. Inicia el servicio:
```bash
docker-compose up -d
```

3. Accede a la aplicación:
Abre tu navegador y visita `http://localhost:8834` con **usuario por defecto: `root` y contraseña por defecto: `Root@123`**

### Ejecutar con JAR

**Asegúrate de tener Java 17+ y yt-dlp instalados en tu máquina.**

1. Descarga el JAR de la versión más reciente desde [Releases](https://github.com/aizhimou/pigeon-pod/releases)

2. Crea el directorio de datos en el mismo directorio que el archivo JAR:
```bash
mkdir -p data
```

3. Ejecuta la aplicación:
```bash
java -jar -Dspring.datasource.url=jdbc:sqlite:/path/to/your/pigeon-pod.db \  # configura la ruta de la base de datos
           pigeon-pod-x.x.x.jar
```

4. Accede a la aplicación:
Abre tu navegador y visita `http://localhost:8080` con **usuario por defecto: `root` y contraseña por defecto: `Root@123`**

## Storage Configuration

- PigeonPod supports `LOCAL` and `S3` storage modes.
- You can only enable one mode at a time.
- S3 mode supports MinIO, Cloudflare R2, AWS S3, and other S3-compatible services.
- Switching storage mode does not migrate historical media automatically. You must migrate files manually.

### Storage Quick Comparison

| Mode | Pros | Cons |
| --- | --- | --- |
| `LOCAL` | Easy setup, no external dependency | Uses local disk, harder to scale |
| `S3` | Better scalability, suitable for cloud deployment | Requires object storage setup and credentials |

## Documentación

- [Cómo obtener la clave API de YouTube](../how-to-get-youtube-api-key/how-to-get-youtube-api-key-en.md)
- [Cómo configurar las cookies de YouTube](../youtube-cookie-setup/youtube-cookie-setup-en.md)
- [Cómo obtener el ID del canal de YouTube](../how-to-get-youtube-channel-id/how-to-get-youtube-channel-id-en.md)

## Stack Tecnológico

### Backend
- **Java 17** - Lenguaje principal
- **Spring Boot 3.5** - Framework de aplicación
- **MyBatis-Plus 3.5** - Framework ORM
- **Sa-Token** - Framework de autenticación
- **SQLite** - Base de datos ligera
- **Flyway** - Herramienta de migración de base de datos
- **YouTube Data API v3** - Recuperación de datos de YouTube
- **yt-dlp** - Herramienta de descarga de videos
- **Rome** - Biblioteca de generación RSS

### Frontend
- **Javascript (ES2024)** - Lenguaje principal
- **React 19** - Framework de aplicación
- **Vite 7** - Herramienta de construcción
- **Mantine 8** - Biblioteca de componentes UI
- **i18next** - Soporte de internacionalización
- **Axios** - Cliente HTTP

## Guía de Desarrollo

### Requisitos del Entorno
- Java 17+
- Node.js 22+
- Maven 3.9+
- SQLite
- yt-dlp

### Desarrollo Local

1. Clona el proyecto:
```bash
git clone https://github.com/aizhimou/PigeonPod.git
cd PigeonPod
```

2. Configura la base de datos:
```bash
# Crea el directorio de datos
mkdir -p data/audio

# El archivo de base de datos se creará automáticamente en el primer inicio
```

3. Configura la API de YouTube:
   - Crea un proyecto en [Google Cloud Console](https://console.cloud.google.com/)
   - Habilita YouTube Data API v3
   - Crea una clave API
   - Configura la clave API en la configuración de usuario

4. Inicia el backend:
```bash
cd backend
mvn spring-boot:run
```

5. Inicia el frontend (nueva terminal):
```bash
cd frontend
npm install
npm run dev
```

6. Accede a la aplicación:
- Servidor de desarrollo frontend: `http://localhost:5173`
- API backend: `http://localhost:8080`

### Notas de Desarrollo
1. Asegúrate de que yt-dlp esté instalado y disponible en la línea de comandos
2. Configura la clave API de YouTube correctamente
3. Asegúrate de que el directorio de almacenamiento de audio tenga suficiente espacio en disco
4. Limpia regularmente los archivos de audio antiguos para ahorrar espacio

---

<div align="center">
  <p>¡Hecho con ❤️ para los entusiastas de podcasts!</p>
  <p>⭐ Si te gusta PigeonPod, ¡dale una estrella en GitHub!</p>
</div>
