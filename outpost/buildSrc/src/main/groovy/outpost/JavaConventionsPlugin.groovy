package com.outpost

import com.diffplug.gradle.spotless.SpotlessExtension
import net.ltgt.gradle.errorprone.ErrorProneOptions
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion

class JavaConventionsPlugin implements Plugin<Project> {
    private static final String JSPECIFY_ANNOTATIONS = 'org.jspecify:jspecify:1.0.1'
    private static final String NULLAWAY = 'com.uber.nullaway:nullaway:0.14.1'

    @Override
    void apply(Project project) {
        project.group = 'com.outpost'
        project.repositories.mavenCentral()
        project.pluginManager.apply(JavaPlugin)
        project.pluginManager.apply('checkstyle')
        project.pluginManager.apply('com.diffplug.spotless')
        project.pluginManager.apply('net.ltgt.errorprone')

        project.extensions.configure(JavaPluginExtension) { javaExtension ->
            javaExtension.toolchain.languageVersion = JavaLanguageVersion.of(21)
        }
        project.dependencies.add(
            'implementation',
            project.dependencies.platform('org.springframework.boot:spring-boot-dependencies:4.1.1')
        )
        // Tomcat 11.0.25 fixes CVE-2026-65182; remove once the Spring Boot BOM manages 11.0.25 or later.
        ['tomcat-embed-core', 'tomcat-embed-el', 'tomcat-embed-websocket'].each { tomcatModule ->
            project.dependencies.constraints.add('implementation', "org.apache.tomcat.embed:${tomcatModule}:11.0.25")
        }
        project.extensions.configure(SpotlessExtension) { spotlessExtension ->
            spotlessExtension.java {
                googleJavaFormat()
            }
        }
        project.extensions.configure(CheckstyleExtension) { checkstyleExtension ->
            checkstyleExtension.toolVersion = '12.1.0'
            checkstyleExtension.maxWarnings = 0
            checkstyleExtension.config = project.resources.text.fromArchiveEntry(
                project.configurations.checkstyle.filter { checkstyleArchive ->
                    checkstyleArchive.name.startsWith('checkstyle-')
                },
                'google_checks.xml'
            )
        }
        project.dependencies.add('compileOnly', JSPECIFY_ANNOTATIONS)
        project.dependencies.add('testCompileOnly', JSPECIFY_ANNOTATIONS)
        project.dependencies.add('errorprone', NULLAWAY)
        project.dependencies.add('errorprone', 'com.google.errorprone:error_prone_core:2.50.0')

        project.tasks.withType(JavaCompile).configureEach { task ->
            task.options.errorprone.with {
                error('NullAway')
                option('NullAway:OnlyNullMarked', true)
                error('RequireExplicitNullMarking')
            }
        }
    }
}
