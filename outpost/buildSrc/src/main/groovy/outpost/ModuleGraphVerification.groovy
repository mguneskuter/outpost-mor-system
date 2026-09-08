package com.outpost

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency

class ModuleGraphVerification {
    static void verify(Project rootProject) {
        Set<String> allProjectPaths = rootProject.allprojects
            .collect { it.path }
            .findAll { it != ':' } as Set
        Set<String> structuralProjects = allProjectPaths.findAll { projectPath ->
            !ModuleGraphSpec.PROJECT_PATHS.contains(projectPath) &&
                !rootProject.project(projectPath).buildFile.exists() &&
                ModuleGraphSpec.PROJECT_PATHS.any { expectedPath -> expectedPath.startsWith("${projectPath}:") }
        }
        Set<String> actualProjects = allProjectPaths - structuralProjects
        Set<String> missingProjects = ModuleGraphSpec.PROJECT_PATHS - actualProjects
        Set<String> unexpectedProjects = actualProjects - ModuleGraphSpec.PROJECT_PATHS
        List<String> errors = []

        missingProjects.sort().each { projectPath ->
            errors << "Missing required project: ${projectPath}"
        }
        unexpectedProjects.sort().each { projectPath ->
            errors << "Unexpected project: ${projectPath}"
        }

        Map<String, Set<String>> actualDependencies = [:].withDefault { [] as Set }
        rootProject.allprojects.findAll { it.path != ':' }.each { project ->
            project.configurations.each { configuration ->
                configuration.dependencies.findAll { it instanceof ProjectDependency }.each { dependency ->
                    actualDependencies[project.path] << dependency.path
                }
            }
        }

        Set<String> actualEdges = actualDependencies.collectMany { projectPath, dependencies ->
            dependencies.collect { dependencyPath -> ModuleGraphSpec.edge(projectPath, dependencyPath) }
        } as Set
        Set<String> expectedEdges = ModuleGraphSpec.expectedEdges()

        (expectedEdges - actualEdges).sort().each { edge ->
            errors << "Missing required dependency edge: ${edge}"
        }
        (actualEdges - expectedEdges).sort().each { edge ->
            errors << "Unexpected dependency edge: ${edge}"
        }

        if (actualDependencies[':platform-sanity:static-data-model']) {
            errors << 'static-data-model must not have project dependencies: ' +
                actualDependencies[':platform-sanity:static-data-model'].sort().join(', ')
        }

        ModuleGraphSpec.DOMAIN_PROJECT_PATHS.each { domainPath ->
            actualDependencies[domainPath].each { dependencyPath ->
                if (ModuleGraphSpec.DEPLOYABLE_PROJECT_PATHS.contains(dependencyPath)) {
                    errors << "Domain module depends on deployable: ${ModuleGraphSpec.edge(domainPath, dependencyPath)}"
                }
                if (domainPath != ':payment' && dependencyPath == ':payment') {
                    errors << "Domain module other than payment depends on payment: ${ModuleGraphSpec.edge(domainPath, dependencyPath)}"
                }
            }
        }

        ModuleGraphSpec.DEPLOYABLE_PROJECT_PATHS.each { deployablePath ->
            actualDependencies[deployablePath].each { dependencyPath ->
                if (ModuleGraphSpec.DEPLOYABLE_PROJECT_PATHS.contains(dependencyPath)) {
                    errors << "Deployable depends on deployable: ${ModuleGraphSpec.edge(deployablePath, dependencyPath)}"
                }
            }
        }

        List<String> cycle = findCycle(actualDependencies)
        if (cycle) {
            errors << "Dependency cycle detected: ${cycle.join(' -> ')}"
        }

        if (errors) {
            throw new GradleException('Module graph verification failed:\n' + errors.join('\n'))
        }
    }

    private static List<String> findCycle(Map<String, Set<String>> dependencies) {
        Map<String, Integer> states = [:].withDefault { 0 }
        List<String> path = []

        for (String projectPath : dependencies.keySet().sort()) {
            List<String> cycle = findCycleFrom(projectPath, dependencies, states, path)
            if (cycle) {
                return cycle
            }
        }
        return []
    }

    private static List<String> findCycleFrom(
        String projectPath,
        Map<String, Set<String>> dependencies,
        Map<String, Integer> states,
        List<String> path
    ) {
        if (states[projectPath] == 1) {
            int cycleStart = path.indexOf(projectPath)
            return path[cycleStart..-1] + projectPath
        }
        if (states[projectPath] == 2) {
            return []
        }

        states[projectPath] = 1
        path << projectPath
        for (String dependencyPath : (dependencies[projectPath] ?: []).sort()) {
            List<String> cycle = findCycleFrom(dependencyPath, dependencies, states, path)
            if (cycle) {
                return cycle
            }
        }
        path.remove(path.size() - 1)
        states[projectPath] = 2
        return []
    }
}
