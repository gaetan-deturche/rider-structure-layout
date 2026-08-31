package dev.structlayout

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.jetbrains.rd.platform.util.lifetime
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rd.ide.model.structLayoutModel

// Listens for layouts pushed by the backend's Alt+Enter action, so that entry point lands in the tool
// window instead of a popup. The editor-menu action calls the backend and shows the result itself; this
// covers the opposite direction.
class StructLayoutSubscriber : ProjectActivity {

    override suspend fun execute(project: Project) {
        project.solution.structLayoutModel.showLayout.advise(project.lifetime) { layout ->
            invokeLater {
                project.getService(StructLayoutViewService::class.java).show(project, layout)
            }
        }
    }
}
