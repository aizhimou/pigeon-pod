<div align="center">
  <img src="../frontend/src/assets/pigeonpod.svg" alt="pigeonpod" width="120" />
  <h1>PigeonPod</h1>
  <h2>Convierte tus canales favoritos de YouTube en canales de podcast de la manera más simple y elegante.</h2>
  <h3>Si el auto-hospedaje no es lo tuyo, echa un vistazo a nuestros próximos servicios en línea:
    <a target="_blank" href="https://pigeonpod.cloud/">PigeonPod</a>
  </h3>
</div>

<div align="center">
  
[![English README](https://img.shields.io/badge/README-English-blue)](../../README.md) [![中文 README](https://img.shields.io/badge/README-%E4%B8%AD%E6%96%87-red)](README-ZH.md) [![Português README](https://img.shields.io/badge/README-Português-green)](README-PT.md) [![日本語 README](https://img.shields.io/badge/README-日本語-blue)](README-JA.md) [![Deutsch README](https://img.shields.io/badge/README-Deutsch-yellow)](README-DE.md) [![Français README](https://img.shields.io/badge/README-Français-purple)](README-FR.md) [![한국어 README](https://img.shields.io/badge/README-한국어-pink)](README-KO.md)
</div>

## Capturas de Pantalla

![index-dark&light](../assets/screenshots/index-dark&light.png)
<div align="center">
  <p style="color: gray">Lista de canales</p>
</div>

![detail-dark&light](../assets/screenshots/detail-dark&light.png)
<div align="center">
  <p style="color: gray">Detalle del canal</p>
</div>

## Características Principales

- **🎯 Suscripción inteligente**: Añade y sincroniza canales o playlists de YouTube con un solo clic.
- **🤖 Sincronización Automática**: Verifica y sincroniza automáticamente el contenido más reciente con actualizaciones incrementales.
- **📻 Suscripción RSS para Podcasts**: Genera enlaces de suscripción RSS estándar, compatibles con cualquier cliente de podcasts.
- **🔍 Filtrado de Contenido**: Soporta filtrar por palabras clave en título y descripción (incluir/excluir) y por duración de episodios.
- **📊 Gestión de Episodios**: Visualiza, elimina y reintenta descargas fallidas de episodios.
- **🎦 Compatibilidad con video**: Descarga episodios en video con selección de resolución y codificación; reproducibles en clientes de podcast con soporte de video.
- **🎚 Control de calidad de audio**: Elige entre niveles 0–10 o conserva la pista original para equilibrar calidad y tamaño.
- **💽 Capítulos y metadatos**: Escribe automáticamente metadatos y marcadores de capítulo en los archivos para mejorar la experiencia de escucha.
- **✨ Escucha Sin Anuncios**: Elimina automáticamente anuncios de introducción y intermedios de los episodios.
- **🍪 Cookies Personalizadas**: Permite suscribirse a contenido con restricción de edad y contenido premium mediante la carga de cookies.
- **🌐 Soporte Multiidioma**: Soporte completo para interfaces en inglés, chino, español, portugués, japonés, francés, alemán y coreano.
- **📱 Interfaz Responsiva**: Experiencia excelente en cualquier dispositivo, en cualquier momento y lugar.

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
      - 'PIGEON_BASE_URL=https://pigeonpod.cloud' # configura tu dominio
      - 'PIGEON_AUDIO_FILE_PATH=/data/audio/' # configura la ruta de archivos de audio
      - 'PIGEON_COVER_FILE_PATH=/data/cover/' # Establezca la ruta del archivo de portada
      - 'SPRING_DATASOURCE_URL=jdbc:sqlite:/data/pigeon-pod.db' # configura la ruta de la base de datos
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
java -jar -DPIGEON_BASE_URL=http://localhost:8080 \  # configura tu dominio
           -DPIGEON_AUDIO_FILE_PATH=/path/to/your/audio/ \  # configura la ruta de archivos de audio
           -Dspring.datasource.url=jdbc:sqlite:/path/to/your/pigeon-pod.db \  # configura la ruta de la base de datos
           pigeon-pod-x.x.x.jar
```

4. Accede a la aplicación:
Abre tu navegador y visita `http://localhost:8080` con **usuario por defecto: `root` y contraseña por defecto: `Root@123`**

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
