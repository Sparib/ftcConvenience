package com.sparib.ftcConvenience

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.ui.Messages


class MainAction : AnAction() {
    private var startTemplate: Regex = Regex("package .+;\n\npublic class .+ \\{\n}\n")
    private var teleInner: String = """
            // Pre-init
            
            @Override
            public void runOpMode() {
                // Init
                
                waitForStart();
                
                // Pre-run
                
                while (opModeIsActive()) {
                    // TeleOp loop
                    
                }
            }""".trimIndent().prependIndent("    ")
    private var autoInner: String = """
            // Pre-init
            
            @Override
            public void runOpMode() {
                // Init
                
                waitForStart();
                
                // Pre-run
                
                if (opModeIsActive()) {
                    // Autonomous instructions
                    
                }
            }""".trimIndent().prependIndent("    ")

    override fun update(e: AnActionEvent) {
        e.presentation.isVisible = true
        val document: Document
        try {
            val requiredData = e.getRequiredData(CommonDataKeys.EDITOR)
            document = requiredData.document
        } catch (er: AssertionError) {
            e.presentation.isEnabled = false
            return
        }
        val file = FileDocumentManager.getInstance().getFile(document)
        val path = file?.path
        if (path == null) {
            e.presentation.isEnabled = false
            return
        }
        if (!startTemplate.matches(document.text)) {
            e.presentation.isEnabled = false
            return
        }
        e.presentation.isEnabled = path.endsWith(".java") && document.isWritable
    }

    override fun actionPerformed(e: AnActionEvent) {
        val document = e.getRequiredData(CommonDataKeys.EDITOR).document
        val file = FileDocumentManager.getInstance().getFile(document)
        val s = file?.path ?: return
        if (!s.endsWith(".java")) {
            return
        }

        val filename = s.split("/").last().replace(".java", "")
        val response = Messages.showYesNoDialog("What OpMode is this file?", "OpMode Choice", "TeleOp", "Autonomous", Messages.getQuestionIcon())
        val classType = if (response == 0) ClassType.TeleOp else ClassType.Autonomous
        val regex = Regex("public class $filename \\{")
        val newClassString = "\n@$classType(name=\"\",group=\"\")\npublic class $filename extends LinearOpMode {"
        val text = document.text
        val classPos = regex.find(text, 0)!!
        val newFile = StringBuilder(text.replaceRange(classPos.range, newClassString))
        val newClassRegex = Regex("public class $filename extends LinearOpMode \\{")
        newFile.insert(document.getLineStartOffset(2), "import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;\nimport com.qualcomm.robotcore.eventloop.opmode.$classType;\n")
        val newClassPos = newClassRegex.find(newFile, 0)!!
        newFile.insert(newClassPos.range.last + 1, "\n${if (classType == ClassType.TeleOp) teleInner else autoInner}")

        WriteCommandAction.runWriteCommandAction(e.project) { document.setText(newFile.toString()) }
    }

    private enum class ClassType {
        TeleOp, Autonomous
    }
}