package app.revanced.patches.chatgpt

import app.revanced.patcher.patch.rawResourcePatch

@Suppress("unused")
val j7BundlePatch = rawResourcePatch(
    name = "GPT J7 Oreo pack",
    description = "Recommended Android 8 ARMv7 compatibility pack for ChatGPT 1.2026.258.",
) {
    compatibleWith("com.openai.chatgpt"("1.2026.258"))
    dependsOn(j7DownloadPatch, j7PerformancePatch)
    apply { }
}
