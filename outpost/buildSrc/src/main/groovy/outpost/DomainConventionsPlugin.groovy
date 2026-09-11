package com.outpost

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.attributes.Category

class DomainConventionsPlugin implements Plugin<Project> {
    private static final String DOMAIN_MARKER = 'outpost.domain-module'
    private static final String JSPECIFY_GROUP = 'org.jspecify'
    private static final String JSPECIFY_NAME = 'jspecify'
    private static final String SLF4J_GROUP = 'org.slf4j'
    private static final String SLF4J_API_NAME = 'slf4j-api'
    private static final List<String> PRODUCTION_CONFIGURATIONS = [
        'api', 'implementation', 'compileOnly', 'runtimeOnly', 'annotationProcessor'
    ]

    @Override
    void apply(Project project) {
        project.pluginManager.apply('outpost.java-conventions')
        project.extensions.extraProperties.set(DOMAIN_MARKER, true)
        project.gradle.projectsEvaluated {
            PRODUCTION_CONFIGURATIONS.each { configurationName ->
                validateDependencies(project, project.rootProject, configurationName)
            }
        }
    }

    private static void validateDependencies(Project project, Project rootProject, String configurationName) {
        def configuration = project.configurations.findByName(configurationName)
        def violations = configuration?.dependencies?.findAll { dependency ->
            if (dependency instanceof ProjectDependency) {
                return !isDomainProject(rootProject, dependency.path)
            }

            if (configurationName == 'implementation' && dependency instanceof ExternalModuleDependency) {
                if (dependency.group == SLF4J_GROUP && dependency.name == SLF4J_API_NAME) {
                    return false
                }
                def category = dependency.attributes.getAttribute(Category.CATEGORY_ATTRIBUTE)
                return category == null || ![
                    Category.REGULAR_PLATFORM,
                    Category.ENFORCED_PLATFORM
                ].contains(category.name)
            }
            if (configurationName == 'compileOnly') {
                return !(dependency.group == JSPECIFY_GROUP && dependency.name == JSPECIFY_NAME)
            }

            return true
        }
        fail(project, configurationName, violations ?: [])
    }

    private static boolean isDomainProject(Project rootProject, String projectPath) {
        def targetProject = rootProject.findProject(projectPath)
        return targetProject?.extensions?.extraProperties?.has(DOMAIN_MARKER)
    }

    private static void fail(Project project, String configurationName, Collection violations) {
        if (!violations.isEmpty()) {
            String dependencyNames = violations.collect { dependency ->
                dependency.group ? "${dependency.group}:${dependency.name}" : dependency.name
            }.join(', ')
            throw new IllegalStateException(
                "Domain project ${project.path} may only use domain project dependencies in " +
                    "${configurationName}; found: ${dependencyNames}"
            )
        }
    }
}
