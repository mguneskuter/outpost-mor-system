package com.outpost

import org.gradle.api.Plugin
import org.gradle.api.Project

class ModuleGraphVerificationPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        if (project != project.rootProject) {
            throw new IllegalStateException('outpost.module-graph-verification must be applied to the root project')
        }

        def verifyTask = project.tasks.register('verifyModuleGraph', VerifyModuleGraphTask) {
            rootProject = project
        }
        project.tasks.register('check') {
            dependsOn verifyTask
        }
        project.tasks.register('build') {
            dependsOn project.tasks.named('check')
        }

        project.gradle.projectsEvaluated {
            def moduleProjects = project.subprojects.findAll {
                ModuleGraphSpec.PROJECT_PATHS.contains(it.path)
            }
            project.tasks.named('check').configure {
                dependsOn moduleProjects.collect { it.tasks.named('check') }
            }
            project.tasks.named('build').configure {
                dependsOn moduleProjects.collect { it.tasks.named('build') }
            }
        }
    }
}
