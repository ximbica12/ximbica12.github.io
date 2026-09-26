# NexoTube TV — LG webOS 3.x

Versão independente do NexoTube para TVs LG webOS, com alvo inicial na **LG 43UJ6565-SB / webOS 3.9.3**.

## Correção da arquitetura

A versão 0.1-alpha usava o frontend `youtube.com/tv` através de uma base Homebrew. Isso foi removido por completo na 0.2.

**NexoTube TV 0.2 não abre, não incorpora e não autentica em `youtube.com/tv`.**
Ele também não usa o app ID `youtube.leanback.v4` e não compartilha intencionalmente sessão/cookies com o YouTube oficial da TV.

O frontend agora é próprio e os dados de catálogo/streams vêm de APIs Piped configuráveis. O Piped fornece endpoints públicos para trending, busca, streams, canais e feed de inscrições sem autenticação.

## Dados do usuário

Cada perfil NexoTube guarda localmente no armazenamento do aplicativo:

- inscrições;
- histórico;
- Assistir mais tarde;
- região;
- preferências.

Trocar de perfil troca esse conjunto de dados. Nenhuma conta do YouTube oficial é necessária para usar a 0.2.

## Recursos 0.2-alpha

- interface TV própria;
- Home com recomendações baseadas no histórico local, inscrições e região;
- busca;
- inscrições locais;
- Biblioteca com histórico e Assistir mais tarde;
- múltiplos perfis locais;
- player próprio usando HLS/progressive streams retornados pela API;
- fallback automático entre instâncias públicas;
- controle remoto LG: setas, OK, Voltar, play/pause e seek;
- app ID separado: `com.ximbica.nexotube.webos`;
- pode coexistir com o YouTube oficial;
- nenhuma navegação para `youtube.com/tv`.

## Ícone

O pacote usa **o mesmo launcher icon que a build Android atual do NexoTube herda**. O workflow baixa esse asset diretamente da fonte Android e o coloca no `.ipk`; o ícone provisório da 0.1 foi removido.

## Backend

A 0.2 usa uma lista com fallback entre instâncias públicas Piped. A instância pode ser trocada em Configurações.

Instância inicial:

`https://pipedapi.kavin.rocks`

Fallbacks:

- `https://pipedapi.leptons.xyz`
- `https://piped-api.garudalinux.org`

Instâncias públicas podem ficar indisponíveis; por isso o fallback é importante. Para máxima estabilidade futura, é recomendável hospedar uma instância própria.

## Build

O GitHub Actions usa o CLI oficial atual do webOS e gera diretamente:

`NexoTube-TV-webOS3-0.2.0-independent.ipk`

O CI também falha se encontrar no runtime:

- `youtube.com/tv`;
- `youtube.leanback.v4`;
- dependência do antigo wrapper Homebrew.

## Instalação

Com Developer Mode / webOS CLI:

```bash
ares-install -d tv NexoTube-TV-webOS3-0.2.0-independent.ipk
ares-launch -d tv com.ximbica.nexotube.webos
```

## Compatibilidade

A aplicação é escrita em JavaScript compatível com ES5 e o workflow executa `es-check` antes de empacotar, visando o Chromium 38 do webOS 3.x.

## Licença

NexoTube TV é distribuído sob GPL-3.0-or-later. O launcher icon utilizado segue a licença do projeto Android de origem.
