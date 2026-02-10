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

![index-dark&light](../assets/screenshots/Snipaste_2025-11-23_23-17-24.png)
<div align="center">
  <p style="color: gray">Lista de canais</p>
</div>

![detail-dark&light](../assets/screenshots/Snipaste_2025-11-23_23-16-12.png)
<div align="center">
  <p style="color: gray">Detalhes do canal</p>
</div>

## Funcionalidades Principais

- **🎯 Inscrição inteligente e pré-visualização**: Cole qualquer URL de canal ou playlist do YouTube, detecte automaticamente o tipo e visualize o feed e os episódios antes de se inscrever.
- **🤖 Sincronização automática e histórico**: Sincroniza periodicamente novos envios em segundo plano, com quantidade inicial de episódios configurável por feed e carregamento de episódios históricos com um clique.
- **⏱ Download automático com atraso para novos episódios**: Configure janelas de atraso por feed para aumentar a taxa de sucesso do `--sponsorblock` em vídeos recém-publicados.
- **📻 RSS seguro para qualquer cliente**: Gera links RSS padrão para canais e playlists, protegidos com API Key e compatíveis com qualquer aplicativo de podcasts.
- **🔄 Exportação de assinaturas em OPML**: Exporte todas as assinaturas como um arquivo OPML padrão para migrar facilmente entre diferentes clientes de podcast.
- **🔍 Filtros e retenção por feed**: Filtre episódios por palavras‑chave no título/descrição (incluir/excluir), duração mínima e defina por feed o estado de sincronização e o número máximo de episódios a manter.
- **📊 Painel de downloads e ações em massa**: Painel em tempo real para tarefas Pendente/Baixando/Concluído/Com falha, com logs de erro e ações em massa de cancelar/excluir/tentar novamente com um clique.
- **🧩 Gestão e controle de episódios**: Lista de episódios com scroll infinito, download manual de episódios individuais, nova tentativa, cancelamento e exclusão que também gerenciam os arquivos locais.
- **🎦 Saída de áudio/vídeo flexível**: Escolha entre downloads apenas de áudio (AAC) ou vídeo, com níveis de qualidade ou seleção de resolução/codificação, e incorporação automática de metadados, capítulos e capas.
- **📚 Suporte a capítulos Podcasting 2.0**: Gera arquivos de capítulos `chapters.json` no padrão para que mais clientes de podcast exibam navegação por capítulos.
- **🍪 Suporte a conteúdo restrito**: Use chaves da YouTube Data API e cookies enviados para acessar com mais confiabilidade conteúdo com restrição de idade e conteúdo exclusivo para membros.
- **🛠 Argumentos avançados do yt-dlp**: Adicione argumentos personalizados do yt-dlp com sintaxe padrão para ajustar com precisão o comportamento de download em casos avançados.
- **⬆️ Atualização do yt-dlp no app**: Atualize com um clique o runtime integrado do yt-dlp para manter a compatibilidade de extração e download sempre em dia.
- **🎛 Feeds personalizáveis e player integrado**: Personalize título e capa por feed e utilize o player web integrado para ouvir rapidamente áudio ou vídeo.
- **🌐 Interface multilíngue e responsiva**: Interface totalmente localizada (inglês, chinês, espanhol, português, japonês, francês, alemão e coreano) com layout responsivo para desktop e dispositivos móveis.

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
      - 'PIGEON_BASE_URL=https://pigeonpod.cloud' # configure para seu domínio. NOTA: Se você alterou este domínio durante o uso, seus links de assinatura anteriores se tornarão inválidos.
      - 'PIGEON_AUDIO_FILE_PATH=/data/audio/' # configure o caminho dos arquivos de áudio
      - 'PIGEON_VIDEO_FILE_PATH=/data/video/' # configure o caminho dos arquivos de vídeo
      - 'PIGEON_COVER_FILE_PATH=/data/cover/' # configure o caminho do arquivo de capa
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
java -jar -DPIGEON_BASE_URL=http://localhost:8080 \  # configure para seu domínio. NOTA: Se você alterou este domínio durante o uso, seus links de assinatura anteriores se tornarão inválidos.
           -DPIGEON_AUDIO_FILE_PATH=/path/to/your/audio/ \  # configure o caminho dos arquivos de áudio
           -PIGEON_VIDEO_FILE_PATH=/path/to/your/video/  \  # configure o caminho dos arquivos de vídeo
           -PIGEON_COVER_FILE_PATH=/path/to/your/cover/  \  # configure o caminho do arquivo de capa
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
