package dev.structlayout

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.logger
import com.jetbrains.rd.platform.util.lifetime
import com.jetbrains.rd.util.reactive.adviseOnce
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rd.ide.model.LayoutRequest
import com.jetbrains.rd.ide.model.structLayoutModel

private val CPP_EXTENSIONS = setOf("h", "hpp", "hxx", "hh", "inl", "c", "cpp", "cxx", "cc")

class ShowStructLayoutAction : AnAction() {

    private val logger = logger<ShowStructLayoutAction>()

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = e.project != null &&
                e.getData(CommonDataKeys.EDITOR) != null &&
                file != null &&
                file.extension?.lowercase() in CPP_EXTENSIONS
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        val request = LayoutRequest(file.path, editor.caretModel.offset)
        val task = project.solution.structLayoutModel.getStructLayout.start(project.lifetime, request)

        task.result.adviseOnce(project.lifetime) { result ->
            val layout = try {
                result.unwrap()
            } catch (t: Throwable) {
                logger.warn("getStructLayout failed", t)
                null
            }

            invokeLater {
                val service = project.getService(StructLayoutViewService::class.java)
                if (layout == null) {
                    service.showMessage(project, "No layout for this position - not a class/struct/union, " +
                            "or the type is incomplete, dependent or template-parented.")
                } else {
                    service.show(project, layout)
                }
            }
        }
    }
}
