# TubeLite J7 — LibreTube base

Este branch abandona o protótipo de UI próprio e passa a usar o LibreTube como base real.

## Upstream fixado

- Repositório: libre-tube/LibreTube
- Commit: `69ca0ba5de14cbb391839ddb30fa4da35e8b2283`
- Base atual: LibreTube 32.1
- Licença: GPL-3.0-or-later
- minSdk upstream: 26 (Android 8.0)
- compile/target SDK upstream: 36

O workflow clona exatamente esse commit, aplica apenas nossos patches e compila o app.

## Patches TubeLite

- applicationId: `com.ximbica.tubelite`
- nome do app: `TubeLite J7`
- versão do fork: `0.4.0-libretube`
- mantém o namespace interno `com.github.libretube` para reduzir alterações e riscos
- desativa `largeHeap` para evitar o app pedir heap ampliado no J7 Prime

## Login

LibreTube possui contas Piped/LibreTube Sync próprias. Google OAuth não é nativo do LibreTube.

O OAuth Google do protótipo anterior não deve ser confundido com o login do LibreTube. O erro 403 visto no Google é causado pelo projeto OAuth em modo Teste quando a conta ainda não foi adicionada como usuário de teste.

A integração Google será um patch separado sobre esta base, sem reescrever a UI do LibreTube.

## Objetivo do branch

1. Compilar o LibreTube oficial para Android 8.
2. Validar a UI/player no J7 Prime.
3. Aplicar otimizações específicas para ARMv7/J7.
4. Integrar OAuth Google opcional preservando o sistema de conta do LibreTube.
