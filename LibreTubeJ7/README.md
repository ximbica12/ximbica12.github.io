# TubeLite J7 — LibreTube base

This branch builds a J7-focused derivative from the official LibreTube source.

## Upstream
- Project: libre-tube/LibreTube
- Pinned release: v32.1
- License: GPL-3.0-or-later

## J7 changes
- applicationId: com.ximbica.tubelite
- app name: TubeLite J7
- minSdk remains 26 (Android 8.0)
- ARM ABI restricted to armeabi-v7a
- version: 32.1-j7.7
- release build generated from pinned upstream source
- patched source archive is uploaded together with the APK for GPL compliance

## Accounts

LibreTube's native account system uses Piped accounts. It does not require Google OAuth verification.
Google/YouTube OAuth can be layered onto this base separately for importing/syncing data from a Google account.

## Build

The GitHub Actions workflow clones the pinned upstream tag, applies `LibreTubeJ7/apply_j7_patch.py`, builds the unsigned release APK, and uploads both the APK and the patched source tree.


## Importação direta do YouTube

A versão J7.2 adiciona uma rota sem CSV em **Configurações > Importar/Exportar > Importar direto do YouTube**.

O fluxo usa OAuth 2.0 somente leitura (`youtube.readonly`) e importa:
- inscrições;
- playlists criadas pelo usuário;
- vídeos marcados como gostei.

Histórico e Assistir mais tarde não são expostos pela YouTube Data API e continuam dependendo de exportação/Takeout.

Enquanto o projeto OAuth estiver em modo de teste, somente contas adicionadas como usuários de teste no Google Auth Platform conseguem autorizar. Para uso público, o app precisa cumprir a verificação OAuth aplicável.


## J7.3 — Perfis e Home personalizada

- perfis locais múltiplos;
- cada perfil usa um banco Room separado, isolando inscrições, playlists, histórico, progresso e Watch Later;
- conta Google importada é associada automaticamente a um perfil;
- ao importar uma segunda conta Google, o app reutiliza o perfil correspondente ou cria um novo;
- perfil ativo aparece como subtítulo da barra superior e pode ser trocado pelo novo botão de perfil;
- Home prioriza **Suas inscrições**, **Continuar assistindo**, **Assistir mais tarde**, playlists e **Discovery para você**;
- Discovery é calculado localmente a partir dos vídeos recentes do perfil e dos vídeos relacionados retornados pelo extrator, sem depender do feed Home privado do YouTube;
- **Assistir mais tarde** é uma playlist local especial por perfil e ganhou ação rápida no menu de cada vídeo;
- Trending continua disponível, mas deixa de ser o foco principal da Home.

### Limites da API do YouTube

O feed Home personalizado do YouTube não é exposto atualmente pela YouTube Data API.
As coleções oficiais Watch History e Watch Later deixaram de ser recuperáveis pela API em 2016, portanto o TubeLite mantém histórico e Watch Later localmente por perfil.


## J7.4 — Correção PiP / System UI no Android 8

O LibreTube entra automaticamente em Picture-in-Picture ao sair para a tela inicial enquanto um vídeo está tocando. Em Android 8.0/8.1 essa transição pode ser problemática em aparelhos antigos/low-memory.

Nesta build:
- PiP é desativado no app em API 26/27 (Android 8.0/8.1);
- o botão de PiP fica oculto nesses sistemas;
- ao apertar Home no Android 8/8.1, a reprodução de vídeo é pausada;
- o track de vídeo é desativado enquanto o app está fora da tela, liberando decoder/surface;
- ao voltar ao app, o track de vídeo é reativado pelo fluxo normal do LibreTube;
- Android 9+ mantém o PiP normal.

O modo de áudio em segundo plano continua disponível quando o usuário o escolhe explicitamente.


## J7.7 — NexoTube

- nome visível alterado de TubeLite J7 para **NexoTube**; o applicationId continua `com.ximbica.tubelite` para preservar atualizações e OAuth;
- correção do seletor PT-BR no Android 8: `pt-rBR` não é mais convertido em `pt_BR` e interpretado incorretamente;
- traduções ausentes em PT-BR são preenchidas com o recurso português antes de qualquer fallback para inglês;
- Home Discovery mistura histórico recente, feed de inscrições e conteúdo regional da região escolhida;
- Shorts usa histórico, relacionados, inscrições, região e embaralhamento, com fallback para vídeos curtos de até 3 minutos;
- avatares de inscrições agora são circulares; cards de vídeo/playlist/Shorts têm cantos mais arredondados;
- cor de destaque padrão passa de azul para vermelho na primeira migração, permanecendo configurável em Aparência;
- o marcador interno do Watch Later não aparece mais na descrição da playlist; builds antigas são migradas;
- importação Google reaproveita playlists antigas e remove apenas duplicatas vazias;
- player ganhou **Gostei** e **Não gostei** usando a YouTube Data API `videos.rate` e autorização `youtube.force-ssl` sob demanda;
- legendas: quando não existe faixa portuguesa, NexoTube tenta criar uma faixa **Português (automático)** a partir da URL de caption/timedtext já extraída pelo NewPipe e a seleciona automaticamente quando o idioma do app é português.

### Limitações conhecidas

A YouTube Data API oficial só permite baixar uma faixa de legenda quando o usuário autenticado tem permissão para editar o vídeo. Por isso a tradução automática de vídeos públicos usa a URL de legenda obtida pelo extrator/player do YouTube; é um caminho best-effort e pode precisar de manutenção se o YouTube mudar esse endpoint.

Para registrar Gostei/Não gostei na conta, o projeto OAuth também precisa autorizar o escopo `https://www.googleapis.com/auth/youtube.force-ssl`. O importador da biblioteca continua solicitando apenas `youtube.readonly`.
