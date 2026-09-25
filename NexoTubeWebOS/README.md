# NexoTube TV — LG webOS

Port experimental do NexoTube para TVs LG webOS, com foco inicial na **LG 43UJ6565-SB / webOS 3.9.3**.

## Arquitetura

Esta versão **não é o APK Android convertido**. Ela usa como engine de TV o projeto GPL-3.0
`webosbrew/youtube-webos`, mantendo o frontend de TV do YouTube para login, navegação e playback,
e adicionando a camada NexoTube por cima.

Isso é intencional: webOS TV 3.x usa Chromium 38 e não executa Android/Media3/NewPipe.

## Compatibilidade alvo

- LG webOS TV 3.x (Chromium 38) em diante
- 1920x1080 e 4K
- Magic Remote / controle LG
- Developer Mode ou Homebrew Channel
- sem root obrigatório

App ID: `com.ximbica.nexotube.webos`

O ID é diferente de `youtube.leanback.v4`, então o NexoTube TV pode coexistir com o YouTube oficial.

## 0.1-alpha

- frontend YouTube TV para conta, recomendações, inscrições e playback;
- adblock e SponsorBlock herdados da base;
- Shorts mantidos;
- miniaturas melhoradas por padrão;
- modo de animações reduzidas para aliviar webOS 3.x / Chromium 38;
- painel NexoTube acessível pelo botão verde;
- identidade visual NexoTube;
- assets próprios;
- build transpila para Chrome 38 por meio do browserslist upstream.

## Build

O GitHub Actions clona `webosbrew/youtube-webos`, aplica `patch_webos.py`, executa o build
e gera o arquivo `.ipk`.

## Instalação

### Developer Mode (recomendado para webOS 3.9.3 sem root)

1. Instale o app **Developer Mode** pela LG Content Store.
2. Entre com uma conta LG Developer.
3. Ative **Developer Mode** e **Key Server**.
4. No PC, registre a TV no webOS CLI/Device Manager.
5. Instale o `.ipk` gerado.

Exemplo:

```bash
ares-install -d tv ./com.ximbica.nexotube.webos_0.1.0_all.ipk
ares-launch -d tv com.ximbica.nexotube.webos
```

### Homebrew Channel

Se o Homebrew Channel já estiver instalado, o mesmo `.ipk` pode ser instalado como app de
desenvolvedor/homebrew. Um repositório Homebrew próprio será adicionado depois que a primeira
versão for validada fisicamente na TV.

## Observação sobre root

Para televisores LG 2017, RootMyTV é conhecido como corrigido a partir do webOS 3.9.2.
Portanto, **não dependa de root** nessa TV com webOS 3.9.3.

## Licença

GPL-3.0-only, acompanhando a base `webosbrew/youtube-webos`.
