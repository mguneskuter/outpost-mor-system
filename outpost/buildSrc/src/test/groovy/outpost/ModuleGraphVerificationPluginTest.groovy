package com.outpost

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.BuildResult
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.junit.Assert.assertTrue

class ModuleGraphVerificationPluginTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder()

    @Test
    void intendedGraphPasses() {
        BuildResult result = runFixture(ModuleGraphSpec.PROJECT_DEPENDENCIES)

        assertTrue(result.output.contains('BUILD SUCCESSFUL'))
    }

    @Test
    void missingEdgeFailsAndNamesTheEdge() {
        Map<String, Set<String>> dependencies = fixtureDependencies()
        dependencies[':common-iso'].remove(':platform-sanity:static-data-model')

        BuildResult failure = buildAndFail(dependencies)

        assertTrue(failure.output.contains(
            'Missing required dependency edge: :common-iso -> :platform-sanity:static-data-model'
        ))
    }

    @Test
    void unexpectedEdgeFailsAndNamesTheEdge() {
        Map<String, Set<String>> dependencies = fixtureDependencies()
        dependencies[':common-iso'] << ':tax'

        BuildResult failure = buildAndFail(dependencies)

        assertTrue(failure.output.contains(
            'Unexpected dependency edge: :common-iso -> :tax'
        ))
    }

    @Test
    void domainToDeployableEdgeFails() {
        Map<String, Set<String>> dependencies = fixtureDependencies()
        dependencies[':common-iso'] << ':outpost-api'

        BuildResult failure = buildAndFail(dependencies)

        assertTrue(failure.output.contains(
            'Domain module depends on deployable: :common-iso -> :outpost-api'
        ))
    }

    @Test
    void deployableToDeployableEdgeFails() {
        Map<String, Set<String>> dependencies = fixtureDependencies()
        dependencies[':outpost-api'] << ':outpost-worker'

        BuildResult failure = buildAndFail(dependencies)

        assertTrue(failure.output.contains(
            'Deployable depends on deployable: :outpost-api -> :outpost-worker'
        ))
    }

    @Test
    void cycleFailsAndPrintsTheCycle() {
        Map<String, Set<String>> dependencies = fixtureDependencies()
        dependencies[':platform-sanity:static-data-model'] << ':common-iso'

        BuildResult failure = buildAndFail(dependencies)

        assertTrue(failure.output.contains(
            'Dependency cycle detected: :common-iso -> :platform-sanity:static-data-model -> :common-iso'
        ))
    }

    @Test
    void missingProjectFailsAndNamesTheProject() {
        BuildResult failure = buildFixtureWithoutProject(':seed-job')

        assertTrue(failure.output.contains('Missing required project: :seed-job'))
    }

    @Test
    void unexpectedProjectFailsAndNamesTheProject() {
        Map<String, Set<String>> dependencies = fixtureDependencies()
        BuildResult result = buildAndFailWithAdditionalProject(dependencies, ':unexpected')

        assertTrue(result.output.contains('Unexpected project: :unexpected'))
    }

    private Map<String, Set<String>> fixtureDependencies() {
        ModuleGraphSpec.PROJECT_DEPENDENCIES.collectEntries { projectPath, dependencies ->
            [(projectPath): new LinkedHashSet<String>(dependencies)]
        }
    }

    private BuildResult runFixture(Map<String, Set<String>> dependencies) {
        runFixtureWithAdditionalProject(dependencies, null)
    }

    private BuildResult runFixtureWithAdditionalProject(
        Map<String, Set<String>> dependencies,
        String additionalProject
    ) {
        File projectDirectory = createFixture(dependencies, additionalProject, ModuleGraphSpec.PROJECT_PATHS)
        GradleRunner.create()
            .withProjectDir(projectDirectory)
            .withArguments('verifyModuleGraph')
            .withPluginClasspath()
            .build()
    }

    private BuildResult buildAndFail(Map<String, Set<String>> dependencies) {
        buildAndFailWithAdditionalProject(dependencies, null)
    }

    private BuildResult buildAndFailWithAdditionalProject(
        Map<String, Set<String>> dependencies,
        String additionalProject
    ) {
        File projectDirectory = createFixture(
            dependencies,
            additionalProject,
            ModuleGraphSpec.PROJECT_PATHS
        )
        GradleRunner.create()
            .withProjectDir(projectDirectory)
            .withArguments('verifyModuleGraph')
            .withPluginClasspath()
            .buildAndFail()
    }

    private BuildResult buildFixtureWithoutProject(String projectToRemove) {
        Set<String> projectPaths = new LinkedHashSet<String>(ModuleGraphSpec.PROJECT_PATHS)
        projectPaths.remove(projectToRemove)
        File projectDirectory = createFixture(
            fixtureDependencies(),
            null,
            projectPaths
        )
        GradleRunner.create()
            .withProjectDir(projectDirectory)
            .withArguments('verifyModuleGraph')
            .withPluginClasspath()
            .buildAndFail()
    }

    private File createFixture(
        Map<String, Set<String>> dependencies,
        String additionalProject,
        Set<String> projectPaths
    ) {
        File projectDirectory = temporaryFolder.newFolder()
        Set<String> includedProjects = new LinkedHashSet<String>(projectPaths)
        if (additionalProject) {
            includedProjects << additionalProject
        }
        String includes = includedProjects.collect { "'${it}'" }.join(',\n    ')
        new File(projectDirectory, 'settings.gradle').text = """
rootProject.name = 'fixture'
include(
    ${includes}
)
"""
        new File(projectDirectory, 'build.gradle').text = """
plugins {
    id 'outpost.module-graph-verification'
}
"""

        includedProjects.each { projectPath ->
            File moduleDirectory = new File(
                projectDirectory,
                projectPath.substring(1).replace(':', File.separator)
            )
            moduleDirectory.mkdirs()
            Set<String> moduleDependencies = dependencies[projectPath] ?: []
            String dependencyLines = moduleDependencies.collect { dependencyPath ->
                "    graph project('${dependencyPath}')"
            }.join('\n')
            new File(moduleDirectory, 'build.gradle').text = """
configurations {
    graph
}
dependencies {
${dependencyLines}
}
"""
        }
        projectDirectory
    }
}
