<div align="center">
  <img src="../../documents/assets/logo-with-brand.png" alt="pigeonpod" width="260" />
  
  <h2>Ouça YouTube e Bilibili. Em qualquer lugar.</h2>
  <h3>Se auto-hospedagem não é sua praia, dê uma olhada em nossos próximos serviços online:
    <a target="_blank" href="https://pigeonpod.cloud/?utm_source=github&utm_medium=repo&utm_campaign=readme&utm_content=cta">PigeonPod</a>
  </h3>
</div>

<div align="center">
  
[English](../../README.md) | [简体中文](README-ZH.md) | [Español](README-ES.md) | [日本語](README-JA.md) | [Deutsch](README-DE.md) | [Français](README-FR.md) | [한국어](README-KO.md)
</div>

## Screenshots

![index-dark&light](documents/assets/screenshots/home-27-11-2025.png)
<div align="center">
  <p style="color: gray">Lista de canais</p>
</div>

![detail-dark&light](documents/assets/screenshots/feed-27-11-2025.png)
<div align="center">
  <p style="color: gray">Detalhes do canal</p>
</div>

## Funcionalidades Principais

- **🎯 Inscrição inteligente e pré-visualização**: Assine canais e playlists do YouTube ou Bilibili em segundos.
- **📻 RSS seguro para qualquer cliente**: Gere feeds RSS padrão protegidos para qualquer app de podcasts.
- **🎦 Saída de áudio/vídeo flexível**: Baixe em áudio ou vídeo com controle de qualidade e formato.
- **🤖 Sincronização automática e histórico**: Mantenha as assinaturas atualizadas e recupere vídeos antigos quando quiser.
- **🍪 Suporte ampliado a cookies**: Use cookies do YouTube e do Bilibili para acessar melhor conteúdos restritos.
- **🌍 Acesso de rede com proxy**: Direcione o tráfego da YouTube API e do yt-dlp por proxies personalizados.
- **🔗 Compartilhamento de episódios com um clique**: Compartilhe qualquer episódio em uma página pública para reprodução sem login.
- **📦 Downloads em lote rápidos**: Pesquise, selecione e enfileire grandes catálogos históricos com eficiência.
- **📊 Painel de downloads e ações em massa**: Acompanhe o status das tarefas e refaça, cancele ou exclua em lote.
- **🔍 Filtros e retenção por feed**: Controle o escopo da sincronização com palavras-chave, duração e limites por feed.
- **⏱ Downloads mais inteligentes para novos episódios**: Atrasar o download automático melhora o processamento de vídeos recém-publicados.
- **🎛 Feeds personalizáveis e player integrado**: Personalize títulos e capas e reproduza episódios na web.
- **🧩 Gestão e controle de episódios**: Baixe, refaça, cancele e exclua episódios com limpeza de arquivos incluída.
- **🔓 Login automático em ambientes confiáveis**: Pule o login manual quando o PigeonPod estiver atrás de controles de acesso confiáveis.
- **📈 Insights de uso da API do YouTube**: Monitore a cota antes que as sincronizações atinjam o limite.
- **🔄 Exportação de assinaturas em OPML**: Exporte assinaturas para migrar facilmente entre clientes de podcast.
- **⬆️ Atualização do yt-dlp no app**: Atualize o yt-dlp sem sair do aplicativo.
- **🛠 Argumentos avançados do yt-dlp**: Ajuste os downloads com argumentos personalizados do yt-dlp.
- **📚 Suporte a capítulos Podcasting 2.0**: Gere arquivos de capítulos para uma navegação mais rica no player.
- **🌐 Interface multilíngue e responsiva**: Use o PigeonPod em oito idiomas de interface e em qualquer dispositivo.

## Deploy

### Usando Docker Compose (Recomendado)

**Certifique-se de ter Docker e Docker Compose instalados na sua máquina.**

1. Use o arquivo de configuração docker-compose, modifique as variáveis de ambiente conforme suas necessidades:
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
      # Opcional: desative a autenticacao integrada apenas se outra camada ja proteger a interface web
      # - PIGEON_AUTH_ENABLED=false
    volumes:
      - data:/data

volumes:
  data:
```

> [!WARNING]
> `PIGEON_AUTH_ENABLED` tem valor padrao `true`. Defina como `false` somente se outra camada confiavel ja proteger a interface web, como um auth proxy, controle de acesso no proxy reverso, VPN ou rede privada.
>
> Se voce desativar a autenticacao integrada, deve proteger o PigeonPod por outros meios. Nao exponha uma instancia com a autenticacao desativada diretamente a Internet publica.

2. Inicie o serviço:
```bash
docker-compose up -d
```

3. Acesse a aplicação:
Abra seu navegador e visite `http://localhost:8834` com **usuário padrão: `root` e senha padrão: `Root@123`**

### Executar com JAR

**Certifique-se de ter Java 17+ e yt-dlp instalados na sua máquina.**

1. Baixe o JAR da versão mais recente em [Releases](https://github.com/aizhimou/pigeon-pod/releases)

2. Crie o diretório de dados no mesmo diretório do arquivo JAR:
```bash
mkdir -p data
```

3. Execute a aplicação:
```bash
java -jar -Dspring.datasource.url=jdbc:sqlite:/path/to/your/pigeon-pod.db \  # configure o caminho do banco de dados
           pigeon-pod-x.x.x.jar
```

4. Acesse a aplicação:
Abra seu navegador e visite `http://localhost:8080` com **usuário padrão: `root` e senha padrão: `Root@123`**

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

## Documentação

- [Como obter a chave da API do YouTube](../how-to-get-youtube-api-key/how-to-get-youtube-api-key-en.md)
- [Como configurar cookies do YouTube](../youtube-cookie-setup/youtube-cookie-setup-en.md)
- [Como obter o ID do canal do YouTube](../how-to-get-youtube-channel-id/how-to-get-youtube-channel-id-en.md)

## Stack Tecnológico

### Backend
- **Java 17** - Linguagem principal
- **Spring Boot 3.5** - Framework da aplicação
- **MyBatis-Plus 3.5** - Framework ORM
- **Sa-Token** - Framework de autenticação
- **SQLite** - Banco de dados leve
- **Flyway** - Ferramenta de migração de banco de dados
- **YouTube Data API v3** - Recuperação de dados do YouTube
- **yt-dlp** - Ferramenta de download de vídeos
- **Rome** - Biblioteca de geração RSS

### Frontend
- **Javascript (ES2024)** - Linguagem principal
- **React 19** - Framework da aplicação
- **Vite 7** - Ferramenta de build
- **Mantine 8** - Biblioteca de componentes UI
- **i18next** - Suporte à internacionalização
- **Axios** - Cliente HTTP

## Guia de Desenvolvimento

### Requisitos do Ambiente
- Java 17+
- Node.js 22+
- Maven 3.9+
- SQLite
- yt-dlp

### Desenvolvimento Local

1. Clone o projeto:
```bash
git clone https://github.com/aizhimou/PigeonPod.git
cd PigeonPod
```

2. Configure o banco de dados:
```bash
# Crie o diretório de dados
mkdir -p data/audio

# O arquivo do banco de dados será criado automaticamente na primeira inicialização
```

3. Configure a API do YouTube:
   - Crie um projeto no [Google Cloud Console](https://console.cloud.google.com/)
   - Habilite a YouTube Data API v3
   - Crie uma chave da API
   - Configure a chave da API nas configurações do usuário

4. Inicie o backend:
```bash
cd backend
mvn spring-boot:run
```

5. Inicie o frontend (novo terminal):
```bash
cd frontend
npm install
npm run dev
```

6. Acesse a aplicação:
- Servidor de desenvolvimento frontend: `http://localhost:5173`
- API backend: `http://localhost:8080`

### Observações de Desenvolvimento
1. Certifique-se de que o yt-dlp esteja instalado e disponível na linha de comando
2. Configure corretamente a chave da API do YouTube
3. Garanta que o diretório de armazenamento de áudio tenha espaço em disco suficiente
4. Limpe regularmente arquivos de áudio antigos para economizar espaço

---

<div align="center">
  <p>Feito com ❤️ para os entusiastas de podcasts!</p>
  <p>⭐ Se você curte o PigeonPod, deixe uma estrela no GitHub!</p>
</div>
