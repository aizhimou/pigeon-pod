<div align="center">
  <img src="../../frontend/src/assets/pigeonpod.svg" alt="pigeonpod" width="120" />
  <h1>PigeonPod</h1>
  <h2>Transforme seus canais favoritos do YouTube em canais de podcast da forma mais simples e elegante.</h2>
  <h3>Se auto-hospedagem não é sua praia, dê uma olhada em nossos próximos serviços online:
    <a target="_blank" href="https://pigeonpod.cloud/">PigeonPod</a>
  </h3>
</div>

<div align="center">
  
[![English README](https://img.shields.io/badge/README-English-blue)](../../README.md) [![中文 README](https://img.shields.io/badge/README-%E4%B8%AD%E6%96%87-red)](README-ZH.md) [![Español README](https://img.shields.io/badge/README-Español-orange)](README-ES.md) [![日本語 README](https://img.shields.io/badge/README-日本語-blue)](README-JA.md) [![Deutsch README](https://img.shields.io/badge/README-Deutsch-yellow)](README-DE.md) [![Français README](https://img.shields.io/badge/README-Français-purple)](README-FR.md) [![한국어 README](https://img.shields.io/badge/README-한국어-pink)](README-KO.md)
</div>

## Screenshots

![index-dark&light](../assets/screenshots/index-dark&light.png)
<div align="center">
  <p style="color: gray">Lista de canais</p>
</div>

![detail-dark&light](../assets/screenshots/detail-dark&light.png)
<div align="center">
  <p style="color: gray">Detalhes do canal</p>
</div>

## Funcionalidades Principais

- **🎯 Inscrição inteligente**: Adicione e sincronize canais ou playlists do YouTube com um clique.
- **🤖 Sincronização Automática**: Verifica e sincroniza automaticamente o conteúdo mais recente com atualizações incrementais.
- **📻 Inscrição RSS para Podcasts**: Gera links de inscrição RSS padrão, compatíveis com qualquer cliente de podcast.
- **🔍 Filtragem de Conteúdo**: Suporta filtrar por palavras-chave no título e na descrição (incluir/excluir) e por duração dos episódios.
- **📊 Gerenciamento de Episódios**: Visualize, delete e tente novamente downloads de episódios que falharam.
- **🎦 Suporte a vídeo**: Baixe episódios em vídeo com seleção de resolução e codificação; reproduzíveis em clientes de podcast com suporte a vídeo.
- **🎚 Controle de qualidade de áudio**: Escolha entre níveis 0–10 ou mantenha a faixa original para equilibrar fidelidade e tamanho.
- **💽 Capítulos e metadados**: Grava automaticamente metadados e marcadores de capítulos nos arquivos para melhorar a experiência de audição.
- **✨ Escuta Sem Anúncios**: Remove automaticamente anúncios de introdução e meio dos episódios.
- **🍪 Cookies Personalizados**: Suporte para inscrição em conteúdo com restrição de idade e conteúdo de membros através do upload de cookies.
- **🌐 Suporte Multi-idioma**: Suporte completo para interfaces em inglês, chinês, espanhol, português, japonês, francês, alemão e coreano.
- **📱 Interface Responsiva**: Experiência excelente em qualquer dispositivo, a qualquer hora, em qualquer lugar.

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
      - 'PIGEON_BASE_URL=https://pigeonpod.cloud' # configure para seu domínio
      - 'PIGEON_AUDIO_FILE_PATH=/data/audio/' # configure o caminho dos arquivos de áudio
      - 'PIGEON_COVER_FILE_PATH=/data/cover/' # set to your cover file path
      - 'SPRING_DATASOURCE_URL=jdbc:sqlite:/data/pigeon-pod.db' # configure o caminho do banco de dados
    volumes:
      - data:/data

volumes:
  data:
```

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
java -jar -DPIGEON_BASE_URL=http://localhost:8080 \  # configure para seu domínio
           -DPIGEON_AUDIO_FILE_PATH=/path/to/your/audio/ \  # configure o caminho dos arquivos de áudio
           -Dspring.datasource.url=jdbc:sqlite:/path/to/your/pigeon-pod.db \  # configure o caminho do banco de dados
           pigeon-pod-x.x.x.jar
```

4. Acesse a aplicação:
Abra seu navegador e visite `http://localhost:8080` com **usuário padrão: `root` e senha padrão: `Root@123`**

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
