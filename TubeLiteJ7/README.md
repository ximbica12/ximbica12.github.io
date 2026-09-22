# TubeLite J7

Cliente Android independente e leve, voltado ao Samsung Galaxy J7 Prime (Android 8.x / armeabi-v7a).

## Objetivo

- interface de vídeo estilo cliente moderno, sem depender do APK oficial do YouTube;
- pesquisa e extração via NewPipe Extractor;
- reprodução com AndroidX Media3;
- preferência por streams progressivos MP4 até 720p para reduzir carga de CPU/GPU no J7 Prime;
- sem WebView como motor principal;
- sem SDK de anúncios;
- suporte a links youtube.com e youtu.be;
- detecção de ReVanced GmsCore/microG;
- interface escura e simples.

## microG / conta

O app detecta ReVanced GmsCore (microG), mas o modo atual é visitante/local. Um cliente criado do zero não recebe automaticamente a sessão da conta do YouTube só por ter microG instalado. Login completo exigiria OAuth próprio do app; este projeto não falsifica assinatura/pacote do app oficial.

## Build

O workflow **Build TubeLite J7** gera um APK debug instalável. O APK é Java/Android puro e compatível com armeabi-v7a; não depende de binários arm64.

## Base técnica

- minSdk 26 (Android 8.0)
- compile/target SDK 35
- Java 17 no build
- Android Gradle Plugin 8.10.1
- NewPipe Extractor v0.26.5
- Media3 1.11.1
- OkHttp 4.12.0
- Glide 4.16.0

## Licença

O projeto deve ser distribuído sob GPL-3.0-or-later por utilizar NewPipe Extractor (GPL).
