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
- version: 32.1-j7.2
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
