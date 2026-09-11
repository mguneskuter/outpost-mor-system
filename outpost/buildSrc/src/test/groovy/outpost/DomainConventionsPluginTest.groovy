package com.outpost

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.junit.Assert.assertTrue

class DomainConventionsPluginTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder()

    @Test
    void slf4jApiImplementationDependencySucceedsDomainConfiguration() {
        File projectDirectory = temporaryFolder.newFolder()
        new File(projectDirectory, 'settings.gradle').text = "rootProject.name = 'fixture'\n"
        new File(projectDirectory, 'build.gradle').text = '''
            plugins {
                id 'outpost.domain-conventions'
            }

            dependencies {
                implementation 'org.slf4j:slf4j-api'
            }
        '''

        BuildResult result = GradleRunner.create()
            .withProjectDir(projectDirectory)
            .withArguments('help')
            .withPluginClasspath()
            .build()

        assertTrue(result.output.contains('BUILD SUCCESSFUL'))
    }

    @Test
    void arbitraryProductionDependencyFailsDomainConfiguration() {
        File projectDirectory = temporaryFolder.newFolder()
        new File(projectDirectory, 'settings.gradle').text = "rootProject.name = 'fixture'\n"
        new File(projectDirectory, 'build.gradle').text = '''
            plugins {
                id 'outpost.domain-conventions'
            }

            dependencies {
                implementation 'commons-io:commons-io:2.16.1'
            }
        '''

        BuildResult failure = GradleRunner.create()
            .withProjectDir(projectDirectory)
            .withArguments('help')
            .withPluginClasspath()
            .buildAndFail()

        assertTrue(failure.output.contains('may only use domain project dependencies'))
    }

    @Test
    void frameworkCompileOnlyDependencyFailsDomainConfiguration() {
        File projectDirectory = temporaryFolder.newFolder()
        new File(projectDirectory, 'settings.gradle').text = "rootProject.name = 'fixture'\n"
        new File(projectDirectory, 'build.gradle').text = '''
            plugins {
                id 'outpost.domain-conventions'
            }

            dependencies {
                compileOnly 'org.springframework:spring-context:6.2.0'
            }
        '''

        BuildResult failure = GradleRunner.create()
            .withProjectDir(projectDirectory)
            .withArguments('help')
            .withPluginClasspath()
            .buildAndFail()

        assertTrue(failure.output.contains('may only use domain project dependencies'))
    }
}
