package dev.structlayout

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import com.jetbrains.rd.ide.model.LayoutResult

const val STRUCT_LAYOUT_TOOL_WINDOW_ID = "Struct Layout"

@Service(Service.Level.PROJECT)
class StructLayoutViewService {
    var panel: StructLayoutPanel? = null

    // Activating the tool window creates the content, and with it the panel, so the update has to run
    // after activation rather than before it.
    fun show(project: Project, layout: LayoutResult) = activate(project) { panel?.show(layout) }

    fun showMessage(project: Project, message: String) = activate(project) { panel?.showMessage(message) }

    private fun activate(project: Project, update: () -> Unit) {
        val manager = ToolWindowManager.getInstance(project)
        val toolWindow = manager.getToolWindow(STRUCT_LAYOUT_TOOL_WINDOW_ID) ?: return
        toolWindow.activate(update, true, true)
    }
}

class StructLayoutToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = StructLayoutPanel()
        project.getService(StructLayoutViewService::class.java).panel = panel

        val content = ContentFactory.getInstance().createContent(panel, null, false)
        content.isCloseable = false
        toolWindow.contentManager.addContent(content)
    }
}
