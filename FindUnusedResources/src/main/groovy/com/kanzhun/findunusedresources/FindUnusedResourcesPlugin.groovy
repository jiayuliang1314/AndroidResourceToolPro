package com.kanzhun.findunusedresources

//import com.github.better.restools.ResToolsConfiguration
//import com.github.better.restools.values.ValuesReplace
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * zhaoyu1
 */
class FindUnusedResourcesPlugin implements Plugin<Project> {

    static final String APP = "com.android.application"
    static final String LIBRARY = "com.android.library"

    @Override
    void apply(Project project) {
        println("FindUnusedResourcesPlugin 1 " + project.name)
        if (!(project.plugins.hasPlugin(APP) || project.plugins.hasPlugin(LIBRARY))) {
            throw new IllegalArgumentException(
                    'ResTools gradle plugin can only be applied to android projects.')
        }

        println("FindUnusedResourcesPlugin 2 " + project.name)

        // config
//        project.extensions.create('resConfig', ResToolsConfiguration.class)

        // === Create Task
        project.tasks.create(["name": "FindUnusedResourcesPlugin", "group": "resourceTools"]) {
            doLast {
                println("FindUnusedResourcesPlugin 3 " + project.name)
                if (!project.android) {
                    throw new IllegalStateException('Must apply \'com.android.application\' or \'com.android.library\' first!')
                }

//                if (project.resConfig == null) {       // check config
//                    throw new IllegalArgumentException(
//                            'ResTools gradle plugin "resConfig DSL" config can not be null.')
//                }

                // === System default
                String sourceFolder = project.android.sourceSets.main.java.srcDirs[0].getAbsolutePath()
                String resFolder = project.android.sourceSets.main.res.srcDirs[0].getAbsolutePath()
                String manifestFilePath = project.android.sourceSets.main.manifest.srcFile.getAbsolutePath()

//                long startTime = System.currentTimeMillis()     // startTime

                // === User settings
//                def config = project.resConfig
//                if (config.new_prefix == null || config.new_prefix.trim().length() == 0) {
//                    throw new IllegalArgumentException(
//                            'the [new_prefix] can not be null (必须配置新的前缀)')
//                }
//                if (config.srcFolderPath != null && config.srcFolderPath.trim().length() > 0) {
//                    sourceFolder = config.srcFolderPath
//                }
//                if (config.resFolderPath != null && config.resFolderPath.trim().length() > 0) {
//                    resFolder = config.resFolderPath
//                }
//                if (config.manifestFilePath != null && config.manifestFilePath.trim().length() > 0) {
//                    manifestFilePath = config.manifestFilePath
//                }

                // === print all settings

//                println(">>>>>> old_prefix: ${config.old_prefix}")
//                println(">>>>>> new_prefix: ${config.new_prefix}")
                println(">>>>>> srcFolder : ${sourceFolder}")
                println(">>>>>> resFolder : ${resFolder}")
                println(">>>>>> AndroidManifest.xml file path : ${manifestFilePath}")

                // === do work
                println "++++++++++++++++++++++ Start FindUnusedResourcesPlugin.."
                File file = new File(resFolder)
                String[] args = new String[4]
                args[0] = file.getParentFile().getAbsolutePath()
                args[1] = "1"//ACTION_PRINT_UNUSED
                args[2] = "2"//ACTION_DELETE
                args[3] = "noprompt"
                println "++++++++++++++++++++++ Start FindUnusedResourcesPlugin.." + args[0]
                FindUnusedResources.main(args)
            }
        }
    }
}