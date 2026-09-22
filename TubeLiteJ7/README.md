# TubeLite J7

Cliente Android independente e leve, voltado ao Samsung Galaxy J7 Prime (Android 8.x / armeabi-v7a).

## v0.2 — OAuth Google / multi-conta

- OAuth 2.0 oficial via Google Identity Services / AuthorizationClient;
- escopo mínimo `youtube.readonly`;
- seletor de conta do Google para adicionar/trocar contas;
- contas autorizadas ficam registradas localmente para troca rápida;
- o app readquire tokens de acesso silenciosamente quando a autorização continua válida;
- opção de desconectar/revogar a conta atual;
- confirmação da sessão consultando `channels.list?mine=true` na YouTube Data API;
- cliente OAuth Android registrado para `com.ximbica.tubelite` e para a assinatura release fixa do projeto.

O ID de cliente OAuth Android é um identificador público do aplicativo; ele não é senha nem client secret. A chave privada de assinatura (`.jks`) e suas senhas nunca devem entrar no repositório.

## Reprodução

- pesquisa e extração via NewPipe Extractor;
- reprodução com AndroidX Media3;
- preferência por streams progressivos MP4 até 720p para reduzir carga de CPU/GPU no J7 Prime;
- sem WebView como motor principal;
- sem SDK de anúncios;
- links youtube.com / youtu.be;
- interface escura e simples.

## microG / GmsCore

O app ainda detecta ReVanced GmsCore/microG, mas a autorização OAuth do app usa a API oficial de autorização do Google disponível pelo Google Play Services compatível. O TubeLite não falsifica assinatura nem pacote do YouTube oficial.

## Build

O workflow **Build TubeLite J7** compila o APK de teste no GitHub Actions. Para OAuth Android funcionar no APK distribuído, a build final precisa ser assinada com a keystore release cuja impressão SHA-1 foi cadastrada no Google Cloud.

## Base técnica

- minSdk 26 (Android 8.0)
- compileSdk 36 / targetSdk 35
- Java 17 no build
- Android Gradle Plugin 8.10.1
- Google Play services Auth 21.6.0
- NewPipe Extractor v0.26.5
- Media3 1.11.1
- OkHttp 4.12.0
- Glide 4.16.0

## Licença

O projeto deve ser distribuído sob GPL-3.0-or-later por utilizar NewPipe Extractor (GPL).
