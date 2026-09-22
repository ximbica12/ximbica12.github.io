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
- version: 32.1-j7.4
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
