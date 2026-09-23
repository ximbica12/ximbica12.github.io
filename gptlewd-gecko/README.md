# GPTLewd Gecko J7

Cliente Android single-site do ChatGPT usando GeckoView real, direcionado ao Galaxy J7 Prime / Android 8 / ARMv7.

## Arquitetura

- Android nativo
- GeckoView 156 stable
- uma única GeckoSession
- sem barra de navegador, abas ou UI de browser
- package próprio: `com.ximbica.gptlewd.gecko`
- minSdk 26
- ABI filtrada para `armeabi-v7a`
- cookies/storage ficam no perfil persistente do GeckoRuntime
- abre diretamente `https://chatgpt.com/`

## Ícone

O build gera os mipmaps a partir da imagem fornecida pelo usuário em
`gptlewd-web/icon-src/icon.b64`. Não redesenha a arte; apenas faz crop central quadrado e resize para as densidades Android.

## Próximas etapas

Depois de confirmar boot, login e Cloudflare no J7:
- WebExtension interna só para chatgpt.com
- tema GPTLewd completo
- uploads/downloads nativos
- permissões de câmera/microfone
- restauração de sessão mais avançada
