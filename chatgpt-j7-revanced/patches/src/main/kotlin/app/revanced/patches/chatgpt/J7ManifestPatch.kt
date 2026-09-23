package app.revanced.patches.chatgpt

import app.revanced.patcher.patch.ResourcePatchContext
import app.revanced.patcher.patch.resourcePatch
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

internal const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

internal fun ResourcePatchContext.editManifest(block: (Document) -> Unit) {
    val manifestFile: File = this["AndroidManifest.xml"]
    val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
    val document = factory.newDocumentBuilder().parse(manifestFile)
    block(document)
    TransformerFactory.newInstance().newTransformer().apply {
        setOutputProperty(OutputKeys.INDENT, "no")
        setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
    }.transform(DOMSource(document), StreamResult(manifestFile))
}

@Suppress("unused")
val j7ManifestPatch = resourcePatch(
    name = "J7 Oreo storage compatibility",
    description = "Adds the legacy storage permissions that ChatGPT checks on Android 8/9 but does not declare in 1.2026.258.",
) {
    compatibleWith("com.openai.chatgpt"("1.2026.258"))

    execute {
        editManifest { doc ->
            val manifest = doc.documentElement
            val application = doc.getElementsByTagName("application").item(0)

            fun ensurePermission(name: String, maxSdk: Int) {
                val permissions = doc.getElementsByTagName("uses-permission")
                for (i in 0 until permissions.length) {
                    val node = permissions.item(i) as? Element ?: continue
                    if (node.getAttributeNS(ANDROID_NS, "name") == name) return
                }

                val permission = doc.createElement("uses-permission").apply {
                    setAttributeNS(ANDROID_NS, "android:name", name)
                    setAttributeNS(ANDROID_NS, "android:maxSdkVersion", maxSdk.toString())
                }
                manifest.insertBefore(permission, application)
            }

            ensurePermission("android.permission.READ_EXTERNAL_STORAGE", 32)
            ensurePermission("android.permission.WRITE_EXTERNAL_STORAGE", 28)
        }
    }
}
