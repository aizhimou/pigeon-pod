<div align="center">
  <img src=".github/docs-assets/logo-with-brand.png" alt="pigeonpod" width="260" />
  <h2>Escucha YouTube y Bilibili. En cualquier lugar.</h2>
  <h3>Si el autoalojamiento no es lo tuyo, echa un vistazo a nuestros próximos servicios en línea:
    <a target="_blank" href="https://pigeonpod.cloud/?utm_source=github&utm_medium=repo&utm_campaign=readme&utm_content=cta">PigeonPod</a>
  </h3>
</div>

<div align="center">

[English](README.md) | [简体中文](README-ZH.md) | [Português](README-PT.md) | [日本語](README-JA.md) | [Deutsch](README-DE.md) | [Français](README-FR.md) | [한국어](README-KO.md)
</div>

> [!NOTE]
> La documentación detallada de uso se mantiene en inglés en la GitHub Wiki.
> Este README en español es una entrada ligera al proyecto y puede quedarse atrás respecto a la documentación oficial en inglés.

## ¿Qué es PigeonPod?

PigeonPod es un proyecto de autoalojamiento orientado a usuarios técnicos que convierte canales de YouTube, playlists de YouTube y contenido de Bilibili en feeds RSS para podcasts, con reglas configurables para sincronizar, descargar y gestionar episodios.

Está pensado especialmente para quienes:

- quieren desplegar y mantener su propio servicio
- quieren llevar contenido de YouTube o Bilibili a una app de podcasts
- necesitan controlar filtros, descargas automáticas, retención y almacenamiento

## Características Principales

- **🎯 Suscripción inteligente y vista previa**: Suscríbete a canales y playlists de YouTube o Bilibili en segundos.
- **📻 RSS seguro para cualquier cliente**: Genera feeds RSS estándar protegidos para cualquier app de podcasts.
- **🎦 Salida de audio/vídeo flexible**: Descarga en audio o vídeo con control de calidad y formato.
- **🤖 Sincronización automática e histórico**: Mantén tus suscripciones al día y recupera videos antiguos cuando lo necesites.
- **🍪 Soporte ampliado para cookies**: Usa cookies de YouTube y Bilibili para acceder mejor al contenido restringido.
- **🌍 Acceso de red con proxy**: Enruta el tráfico de YouTube API y yt-dlp mediante proxies personalizados.
- **🔗 Compartir episodios con un clic**: Comparte cualquier episodio con una página pública para reproducirlo sin iniciar sesión.
- **📦 Descargas por lotes rápidas**: Busca, selecciona y encola grandes catálogos históricos con eficiencia.
- **📊 Panel de descargas y acciones masivas**: Sigue el estado de las tareas y reintenta, cancela o elimina en bloque.
- **🔍 Filtros y retención por feed**: Controla el alcance de la sincronización con palabras clave, duración y límites por feed.
- **⏱ Descargas más inteligentes para episodios nuevos**: Retrasa la descarga automática para mejorar el procesamiento de videos recién publicados.
- **🎛 Feeds personalizables y reproductor integrado**: Personaliza títulos y portadas y reproduce episodios en la web.
- **🧩 Gestión y control de episodios**: Descarga, reintenta, cancela y elimina episodios con limpieza de archivos incluida.
- **🔓 Inicio de sesión automático en entornos de confianza**: Evita el login manual cuando PigeonPod está detrás de controles de acceso confiables.
- **📈 Información de uso de la API de YouTube**: Supervisa la cuota antes de que las sincronizaciones alcancen el límite.
- **🔄 Exportación de suscripciones en OPML**: Exporta tus suscripciones para migrarlas fácilmente entre clientes de podcast.
- **⬆️ Actualización de yt-dlp dentro de la app**: Actualiza yt-dlp sin salir de la aplicación.
- **🛠 Argumentos avanzados de yt-dlp**: Ajusta las descargas con argumentos personalizados de yt-dlp.
- **📚 Soporte de capítulos Podcasting 2.0**: Genera archivos de capítulos para una navegación más rica en el reproductor.
- **🌐 Interfaz multilingüe y responsiva**: Usa PigeonPod en ocho idiomas de interfaz y en todo tipo de dispositivos.

## Inicio rápido

La forma recomendada de desplegarlo es con Docker Compose:

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
      - SPRING_DATASOURCE_URL=jdbc:sqlite:/data/pigeon-pod.db
      # Opcional: desactiva la autenticación integrada solo si otra capa ya protege la interfaz web
      # - PIGEON_AUTH_ENABLED=false
    volumes:
      - data:/data

volumes:
  data:
```

Inicia el servicio:

```bash
docker compose up -d
```

Accede a la aplicación:

```text
http://localhost:8834
```

Credenciales por defecto:

- Usuario: `root`
- Contraseña: `Root@123`

> [!WARNING]
> `PIGEON_AUTH_ENABLED` tiene como valor predeterminado `true`. Cámbialo a `false` solo si otra capa de confianza ya protege la interfaz web, como un auth proxy, control de acceso en el proxy inverso, una VPN o una red privada.
>
> No expongas una instancia sin autenticación directamente a Internet.

## Documentación

La GitHub Wiki en inglés es la documentación de usuario oficial:

- [Wiki Home](https://github.com/aizhimou/PigeonPod/wiki)
- [Quick Start](https://github.com/aizhimou/PigeonPod/wiki/Quick-Start)
- [Installation](https://github.com/aizhimou/PigeonPod/wiki/Installation)
- [Configuration Overview](https://github.com/aizhimou/PigeonPod/wiki/Configuration-Overview)
- [Troubleshooting](https://github.com/aizhimou/PigeonPod/wiki/Troubleshooting)
- [Advanced Customization](https://github.com/aizhimou/PigeonPod/wiki/Advanced-Customization)

## Enlaces más útiles

- [README principal en inglés](README.md)
- [GitHub Wiki](https://github.com/aizhimou/PigeonPod/wiki)
- [Releases](https://github.com/aizhimou/PigeonPod/releases)
- [Issues](https://github.com/aizhimou/PigeonPod/issues)

## Notas adicionales

- El método recomendado de despliegue es Docker; ya no se recomienda ejecutar el JAR directamente.
- Si solo quieres evaluar rápidamente si el proyecto te sirve, este README y la Wiki en inglés son suficientes.
- Si necesitas personalización profunda, desarrollo o contexto de arquitectura, revisa `dev-docs/` en el repositorio.

---

<div align="center">
  <p>¡Hecho con ❤️ para los entusiastas de podcasts!</p>
  <p>⭐ Si te gusta PigeonPod, ¡dale una estrella en GitHub!</p>
</div>
