# Anime Jiggle Lab v2

Sandbox 3D para Android 8+ com personagem VRoid real, rig humanoide e física de busto aplicada diretamente aos ossos skinned do personagem.

## O que mudou em relação à v1

A v1 usava volumes procedurais separados do corpo. A v2 remove isso completamente.

- Personagem anime VRoid real empacotado dentro do APK
- Sem download de modelo em runtime
- Usa os ossos reais `J_Sec_L_Bust1/2` e `J_Sec_R_Bust1/2`
- Spring/damper independente em cada lado
- Arrastar e soltar diretamente no busto
- Tap/poke, botão Bounce e impulso pelo acelerômetro
- Física secundária VRM continua ativa
- Idle, Breathe e Dance procedurais no esqueleto
- Câmera orbital e pinch zoom
- Presets Natural, Soft, Jelly e Chaos
- Foco de câmera e expressão Smile
- Pixel ratio limitado para hardware Android antigo

## Asset 3D

Build usa `HairSample_Female.vrm`, do conjunto de modelos de exemplo VRoid.

Fonte:
`madjin/vrm-samples/vroid/beta/HairSample_Female.vrm`

A documentação oficial do VRoid lista **HairSample_Female** entre os sample models com licença **CC0**.

O workflow baixa o arquivo durante o build e o empacota como:
`app/src/main/assets/models/female.vrm`

## Engine

- Three.js 0.128
- @pixiv/three-vrm 0.6.11
- Android WebView com WebGL
- minSdk 26 / Android 8+

## Build

Requer JDK 17 + Android SDK.

```bash
gradle :app:assembleDebug
```

APK:
`app/build/outputs/apk/debug/app-debug.apk`
