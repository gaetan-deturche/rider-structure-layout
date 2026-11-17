package dev.structlayout

import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.updateSettings.impl.PluginUpdateSourceService
import com.intellij.openapi.updateSettings.impl.UpdateSettings

// Must match the stored host string exactly - no trailing slash.
const val UPDATE_REPOSITORY =
    "https://raw.githubusercontent.com/gaetan-deturche/rider-structure-layout/master/updatePlugins.xml"

private const val PLUGIN_ID = "dev.structlayout"

// Rider updates a plugin only from the source it was installed from, and a copy installed from disk has
// none. Registering the repository is not enough on its own: the plugin's update source has to name it.
// Doing both at startup means a fresh install needs no manual setup.
//
// Both APIs live in impl packages (treat as internal), so each step is isolated: a change in either must
// not stop the plugin from loading.
class StructLayoutUpdateSource : AppLifecycleListener {

    override fun appStarted() {
        val log = logger<StructLayoutUpdateSource>()

        runCatching {
            val hosts = UpdateSettings.getInstance().storedPluginHosts
            // never remove or reorder what the user already has
            if (UPDATE_REPOSITORY !in hosts) hosts.add(UPDATE_REPOSITORY)
        }.onFailure { log.warn("could not register the update repository", it) }

        runCatching {
            if (PluginUpdateSourceService.isFunctionalitySupported()) {
                val sources = PluginUpdateSourceService.getInstance()
                val descriptor = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID))
                // only when missing: a source the user picked themselves stays untouched
                if (descriptor != null && sources.isMissingPluginUpdateSource(descriptor)) {
                    sources.setPluginUpdateSourceId(
                        descriptor.pluginId,
                        sources.createCustomRepositoryPluginUpdateSourceId(UPDATE_REPOSITORY))
                }
            }
        }.onFailure { log.warn("could not set the plugin update source", it) }
    }
}
