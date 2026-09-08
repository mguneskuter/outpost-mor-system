package com.outpost

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

abstract class VerifyModuleGraphTask extends DefaultTask {
    @Internal
    Project rootProject

    @TaskAction
    void verifyModuleGraph() {
        ModuleGraphVerification.verify(rootProject)
    }
}
