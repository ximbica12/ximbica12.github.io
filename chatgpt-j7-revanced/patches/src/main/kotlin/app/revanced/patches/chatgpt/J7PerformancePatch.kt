package app.revanced.patches.chatgpt

import app.revanced.patcher.patch.resourcePatch
import org.w3c.dom.Element

@Suppress("unused")
val j7PerformancePatch = resourcePatch(
    name = "J7 startup performance",
    description = "Cuts non-essential startup telemetry providers on low-memory Android 8 devices while keeping auth, notifications, files and voice intact.",
) {
    compatibleWith("com.openai.chatgpt"("1.2026.258"))

    apply {
        document("AndroidManifest.xml").use { doc ->
            val providers = doc.getElementsByTagName("provider")
            val providersToDisable = setOf(
                "com.datadog.android.rum.DdRumContentProvider",
                "io.sentry.ndk.SentryNdkPreloadProvider",
            )

            for (i in 0 until providers.length) {
                val provider = providers.item(i) as? Element ?: continue
                val providerName = provider.getAttributeNS(ANDROID_NS, "name")

                if (providerName in providersToDisable) {
                    provider.setAttributeNS(ANDROID_NS, "android:enabled", "false")
                }

                if (providerName == "androidx.startup.InitializationProvider") {
                    val children = provider.childNodes
                    for (j in children.length - 1 downTo 0) {
                        val child = children.item(j) as? Element ?: continue
                        if (child.tagName != "meta-data") continue
                        if (child.getAttributeNS(ANDROID_NS, "name") == "io.bitdrift.capture.ContextHolder") {
                            provider.removeChild(child)
                        }
                    }
                }
            }
        }
    }
}
