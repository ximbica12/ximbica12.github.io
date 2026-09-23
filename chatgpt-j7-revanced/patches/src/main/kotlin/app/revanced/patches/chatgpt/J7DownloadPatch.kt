package app.revanced.patches.chatgpt

import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction10x
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction20t
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction22t
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val FILE_PREVIEW_ERROR = "File preview downloads are unsupported below Android Q"
private const val CODEX_REMOTE_ERROR = "Codex Remote downloads are unsupported below Android Q."

private data class FoundMethod(val classDef: ClassDef, val method: Method)

private fun BytecodePatchContext.findMethodContainingString(value: String): FoundMethod {
    for (classDef in classes) {
        for (method in classDef.methods) {
            val instructions = method.implementation?.instructions ?: continue
            val hit = instructions.any { instruction ->
                val ref = (instruction as? ReferenceInstruction)?.reference as? StringReference
                ref?.string == value
            }
            if (hit) return FoundMethod(classDef, method)
        }
    }
    error("Method containing string not found: $value")
}

private fun BytecodePatchContext.mutable(found: FoundMethod): MutableMethod {
    val immutable = found.method
    return proxy(found.classDef).mutableClass.methods.first { method ->
        method.name == immutable.name &&
            method.returnType == immutable.returnType &&
            method.parameterTypes == immutable.parameterTypes
    }
}

private fun MutableMethod.stringIndex(value: String): Int = implementation!!.instructions.indexOfFirst { instruction ->
    val ref = (instruction as? ReferenceInstruction)?.reference as? StringReference
    ref?.string == value
}

private fun MutableMethod.nearestOpcodeBefore(index: Int, opcode: Opcode, lookBack: Int = 48): Int? {
    if (index <= 0) return null
    val instructions = implementation!!.instructions
    val start = maxOf(0, index - lookBack)
    for (i in index - 1 downTo start) {
        if (instructions[i].opcode == opcode) return i
    }
    return null
}

@Suppress("unused")
val j7DownloadPatch = bytecodePatch(
    name = "J7 Android 8 downloads",
    description = "Enables the app's existing File Preview and Codex Remote download paths below Android Q and requests legacy storage permission on Oreo/Pie.",
) {
    compatibleWith("com.openai.chatgpt"("1.2026.258"))
    dependsOn(j7ManifestPatch)
    extendWith("extensions/chatgpt.rve")

    execute {
        mutable(findMethodContainingString(FILE_PREVIEW_ERROR)).apply {
            val errorIndex = stringIndex(FILE_PREVIEW_ERROR)
            require(errorIndex >= 0) { "File Preview Android-Q guard string was not found" }
            val guardIndex = nearestOpcodeBefore(errorIndex, Opcode.IF_LT)
                ?: error("File Preview Android-Q IF_LT guard was not found")
            implementation!!.replaceInstruction(guardIndex, BuilderInstruction10x(Opcode.NOP))
        }

        mutable(findMethodContainingString(CODEX_REMOTE_ERROR)).apply {
            val errorIndex = stringIndex(CODEX_REMOTE_ERROR)
            require(errorIndex >= 0) { "Codex Remote Android-Q guard string was not found" }
            val guardIndex = nearestOpcodeBefore(errorIndex, Opcode.IF_GE)
                ?: error("Codex Remote Android-Q IF_GE guard was not found")
            val guard = implementation!!.instructions[guardIndex] as? BuilderInstruction22t
                ?: error("Codex Remote guard had an unexpected instruction format")
            implementation!!.replaceInstruction(guardIndex, BuilderInstruction20t(Opcode.GOTO_16, guard.target))
        }

        val mainClass = classes.first { it.type == "Lcom/openai/chatgpt/MainActivity;" }
        val immutableOnCreate = mainClass.methods.first { method ->
            method.name == "onCreate" &&
                method.returnType == "V" &&
                method.parameterTypes == listOf("Landroid/os/Bundle;")
        }
        val onCreate = mutable(FoundMethod(mainClass, immutableOnCreate))
        val instructions = onCreate.implementation!!.instructions
        val returns = instructions.indices.filter { instructions[it].opcode == Opcode.RETURN_VOID }.asReversed()
        require(returns.isNotEmpty()) { "MainActivity.onCreate has no RETURN_VOID" }
        returns.forEach { index ->
            onCreate.addInstruction(
                index,
                "invoke-static {p0}, Lapp/revanced/extension/chatgpt/J7Compat;->onMainActivityCreated(Landroid/app/Activity;)V",
            )
        }
    }
}
