# GPTLewd Web J7

Cliente Android leve para `chatgpt.com`, feito especificamente para Android 8.0/8.1 e aparelhos ARMv7 como o Galaxy J7 Prime.

## Objetivos
- minSdk 26
- sem bibliotecas nativas: usa Android System WebView
- tema adulto / neon aplicado via CSS
- ícone próprio
- upload de arquivos
- downloads via Android DownloadManager
- cookies e DOM storage persistentes
- layout compacto para telas 360x640 / ~240 dpi
- animações pesadas reduzidas

## Login
O wrapper mantém `chatgpt.com` e hosts de autenticação dentro do mesmo WebView para preservar cookies. O User-Agent é ajustado para Chrome Android. Provedores de identidade podem aplicar regras próprias a navegadores incorporados; email/login web tende a ser o primeiro fluxo a testar.

A OpenAI recomenda Chrome ou Brave para o fluxo de login no app Android oficial. Este projeto não tenta falsificar assinatura, package ou credenciais do app oficial.

## Build
O workflow `Build GPTLewd Web APK` gera um APK debug instalável automaticamente em pushes na branch `gptlewd-web`.

O projeto é independente e não contém código proprietário do APK oficial do ChatGPT.
