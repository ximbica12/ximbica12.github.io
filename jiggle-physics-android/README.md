# Jiggle Physics Lab

Mini sandbox 3D para Android 8+ (minSdk 26) focado em física de mola/amortecimento em duas regiões macias de um mannequin adulto não explícito.

## Recursos
- Spring/damper em tempo real
- Arraste direto por toque
- Impulso pelo acelerômetro
- Rigidez, damping, massa e escala ajustáveis
- Botões Bounce, 0G, giro e reset
- Render WebGL leve dentro de WebView
- Fallback procedural caso o asset remoto falhe

## Asset 3D
O app tenta carregar:
- **Quaternius — Animated Woman**, CC0 / domínio público
- Mirror utilizado: `tech-leads-club/nj-mmo/client/public/models/npcs/Roxxy.glb`
- Licença registrada no mirror em `client/public/models/npcs/LICENSE.txt`
- Fonte original: https://quaternius.com/packs/animatedwoman.html

O corpo remoto é usado como base visual. A simulação macia é implementada separadamente como volumes procedurais sem detalhes explícitos.

## Build local
Requer JDK 17 + Android SDK.

```bash
gradle :app:assembleDebug
```

APK:
`app/build/outputs/apk/debug/app-debug.apk`
