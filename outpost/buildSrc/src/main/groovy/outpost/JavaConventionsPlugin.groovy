package outpost

import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion

class JavaConventionsPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.repositories.mavenCentral()
        project.pluginManager.apply(JavaPlugin)
        project.pluginManager.apply('checkstyle')
        project.pluginManager.apply('com.diffplug.spotless')
        project.pluginManager.apply('net.ltgt.errorprone')

        project.extensions.configure(JavaPluginExtension) { javaExtension ->
            javaExtension.toolchain.languageVersion = JavaLanguageVersion.of(21)
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
        project.dependencies.add('errorprone', 'com.google.errorprone:error_prone_core:2.50.0')
    }
}
