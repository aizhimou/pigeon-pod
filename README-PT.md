<div align="center">
  <img src=".github/docs-assets/logo-with-brand.png" alt="pigeonpod" width="260" />
  <h2>Ouça YouTube e Bilibili. Em qualquer lugar.</h2>
  <h3>Se auto-hospedagem não é sua praia, dê uma olhada em nossos próximos serviços online:
    <a target="_blank" href="https://pigeonpod.cloud/?utm_source=github&utm_medium=repo&utm_campaign=readme&utm_content=cta">PigeonPod</a>
  </h3>
</div>

<div align="center">

[English](README.md) | [简体中文](README-ZH.md) | [Español](README-ES.md) | [日本語](README-JA.md) | [Deutsch](README-DE.md) | [Français](README-FR.md) | [한국어](README-KO.md)
</div>

> [!NOTE]
> A documentação detalhada de uso é mantida em inglês na GitHub Wiki.
> Este README em português é uma entrada leve para o projeto e pode ficar atrás da documentação oficial em inglês.

## O que é o PigeonPod?

O PigeonPod é um projeto self-hosted para usuários técnicos que transforma canais e playlists do YouTube, além de conteúdos do Bilibili, em feeds RSS de podcast com regras configuráveis de sincronização, download e gerenciamento.

Ele é mais indicado para quem:

- quer implantar e manter o próprio serviço
- quer levar conteúdo do YouTube ou do Bilibili para um app de podcast
- precisa controlar filtros, downloads automáticos, retenção e armazenamento

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
- **🔔 Resumos e alertas de downloads com falha**: Receba resumos por e-mail ou webhook quando as tentativas automáticas se esgotarem.
- **🔍 Filtros e retenção por feed**: Controle o escopo da sincronização com palavras-chave, duração e limites por feed.
- **⏱ Downloads mais inteligentes para novos episódios**: Atrasar o download automático melhora o processamento de vídeos recém-publicados.
- **🎛 Feeds personalizáveis e player integrado**: Personalize títulos e capas e reproduza episódios na web.
- **🧩 Gestão e controle de episódios**: Baixe, refaça, cancele e exclua episódios com limpeza de arquivos incluída.
- **🔓 Login automático em ambientes confiáveis**: Pule o login manual quando o PigeonPod estiver atrás de controles de acesso confiáveis.
- **📈 Insights de uso da API do YouTube**: Monitore a cota antes que as sincronizações atinjam o limite.
- **🔄 Exportação de assinaturas em OPML**: Exporte assinaturas para migrar facilmente entre clientes de podcast.
- **⬆️ Gerenciamento de yt-dlp no app**: Gerencie runtimes, alterne a versão ativa e atualize o yt-dlp sem sair do aplicativo.
- **🛠 Argumentos avançados do yt-dlp**: Ajuste os downloads com argumentos personalizados do yt-dlp.
- **📚 Suporte a capítulos Podcasting 2.0**: Gere arquivos de capítulos para uma navegação mais rica no player.
- **🌐 Interface multilíngue e responsiva**: Use o PigeonPod em oito idiomas de interface e em qualquer dispositivo.

## Início rápido

A forma recomendada de deploy é com Docker Compose:

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
      # Opcional: desative a autenticação integrada apenas se outra camada já proteger a interface web
      # - PIGEON_AUTH_ENABLED=false
    volumes:
      - data:/data

volumes:
  data:
```

Inicie o serviço:

```bash
docker compose up -d
```

Acesse:

```text
http://localhost:8834
```

Credenciais padrão:

- Usuário: `root`
- Senha: `Root@123`

> [!WARNING]
> `PIGEON_AUTH_ENABLED` tem valor padrão `true`. Defina como `false` somente se outra camada confiável já proteger a interface web, como um auth proxy, controle de acesso no proxy reverso, VPN ou rede privada.
>
> Não exponha uma instância com a autenticação desativada diretamente à Internet pública.

## Documentação

A GitHub Wiki em inglês é a documentação oficial para usuários:

- [Wiki Home](https://github.com/aizhimou/PigeonPod/wiki)
- [Quick Start](https://github.com/aizhimou/PigeonPod/wiki/Quick-Start)
- [Installation](https://github.com/aizhimou/PigeonPod/wiki/Installation)
- [Configuration Overview](https://github.com/aizhimou/PigeonPod/wiki/Configuration-Overview)
- [Troubleshooting](https://github.com/aizhimou/PigeonPod/wiki/Troubleshooting)
- [Advanced Customization](https://github.com/aizhimou/PigeonPod/wiki/Advanced-Customization)

## Links mais úteis

- [README principal em inglês](README.md)
- [GitHub Wiki](https://github.com/aizhimou/PigeonPod/wiki)
- [Releases](https://github.com/aizhimou/PigeonPod/releases)
- [Issues](https://github.com/aizhimou/PigeonPod/issues)

## Observações

- O método recomendado de deploy é Docker; executar o JAR diretamente não é mais recomendado.
- Se você só quer avaliar rapidamente se o projeto atende ao seu caso, este README e a Wiki em inglês bastam.
- Para customização profunda, desenvolvimento ou contexto de arquitetura, consulte `dev-docs/` no repositório.

---

<div align="center">
  <p>Feito com ❤️ para os entusiastas de podcasts!</p>
  <p>⭐ Se você curte o PigeonPod, deixe uma estrela no GitHub!</p>
</div>
