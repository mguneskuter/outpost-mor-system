package com.outpost

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.junit.Assert.assertTrue

class JavaConventionsPluginTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder()

    @Test
    void nullMarkedPackageWithNullSafeCodeCompiles() {
        BuildResult result = buildFixture(
            packageInfo: PACKAGE_INFO,
            main: classWithBody('''
                    String greeting() {
                        return "hi";
                    }
                '''))

        assertTrue(result.output.contains('BUILD SUCCESSFUL'))
    }

    @Test
    void dereferencingNullableValueFailsWithNullAway() {
        BuildResult failure = buildAndFail(
            packageInfo: PACKAGE_INFO,
            main: classWithBody('''
                    String lowercase() {
                        String value = nullableValue();
                        return value.toLowerCase();
                    }
                    @Nullable String nullableValue() {
                        return null;
                    }
                '''))

        assertTrue(failure.output.contains('NullAway'))
    }

    @Test
    void passingNullableToNonNullParameterFailsWithNullAway() {
        BuildResult failure = buildAndFail(
            packageInfo: PACKAGE_INFO,
            main: classWithBody('''
                    boolean isBlank(String text) {
                        return text.isEmpty();
                    }
                    boolean check() {
                        return isBlank(nullableValue());
                    }
                    @Nullable String nullableValue() {
                        return null;
                    }
                '''))

        assertTrue(failure.output.contains('NullAway'))
    }

    @Test
    void returningNullFromNonNullMethodFailsWithNullAway() {
        BuildResult failure = buildAndFail(
            packageInfo: PACKAGE_INFO,
            main: classWithBody('''
                    String returnsNull() {
                        return null;
                    }
                '''))

        assertTrue(failure.output.contains('NullAway'))
    }

    @Test
    void unmarkedPackageFailsWithRequireExplicitNullMarking() {
        BuildResult failure = buildAndFail(
            packageInfo: null,
            main: classWithBody('''
                    String value() {
                        return "ok";
                    }
                '''))

        assertTrue(failure.output.contains('RequireExplicitNullMarking'))
    }

    @Test
    void nullableReturnHandledByCallerCompiles() {
        BuildResult result = buildFixture(
            packageInfo: PACKAGE_INFO,
            main: classWithBody('''
                    String handle() {
                        String value = nullableValue();
                        return value == null ? "default" : value;
                    }
                    @Nullable String nullableValue() {
                        return null;
                    }
                '''))

        assertTrue(result.output.contains('BUILD SUCCESSFUL'))
    }

    @Test
    void productionAndTestSourcesAreBothChecked() {
        BuildResult result = buildFixture(
            packageInfo: PACKAGE_INFO,
            main: classWithBody('''
                    String prod() {
                        return "prod";
                    }
                '''),
            test: testClassWithBody('''
                    @org.junit.Test
                    public void ok() {
                        Example example = new Example();
                        org.junit.Assert.assertNotNull(example.prod());
                    }
                '''))

        assertTrue(result.output.contains('BUILD SUCCESSFUL'))
    }

    @Test
    void testSourceViolationFailsTheBuild() {
        BuildResult failure = buildAndFail(
            packageInfo: PACKAGE_INFO,
            main: classWithBody('''
                    String prod() {
                        return "prod";
                    }
                '''),
            test: testClassWithBody('''
                    @org.junit.Test
                    public void dereferencesNullable() {
                        String value = resource();
                        value.toLowerCase();
                    }
                    @Nullable String resource() {
                        return null;
                    }
                '''))

        assertTrue(failure.output.contains('NullAway'))
    }

    private static final String PACKAGE_INFO = '''
        @NullMarked
        package com.outpost.fixture;

        import org.jspecify.annotations.NullMarked;
        '''

    private static final String NULLABLE_IMPORT = 'import org.jspecify.annotations.Nullable;\n'

    private String classWithBody(String body) {
        deindent('''
            package com.outpost.fixture;

        ''' + NULLABLE_IMPORT + '''
            class Example {
        ''' + body + '''
            }
        ''')
    }

    private String testClassWithBody(String body) {
        deindent('''
            package com.outpost.fixture;

        ''' + NULLABLE_IMPORT + '''
            public class ExampleTest {
        ''' + body + '''
            }
        ''')
    }

    private BuildResult buildFixture(Map fixture) {
        runFixture(fixture, false)
    }

    private BuildResult buildAndFail(Map fixture) {
        runFixture(fixture, true)
    }

    private BuildResult runFixture(Map fixture, boolean expectFailure) {
        File projectDirectory = createFixture(fixture)
        GradleRunner runner = GradleRunner.create()
            .withProjectDir(projectDirectory)
            .withArguments('test')
            .withPluginClasspath()
        expectFailure ? runner.buildAndFail() : runner.build()
    }

    private File createFixture(Map fixture) {
        File projectDirectory = temporaryFolder.newFolder()
        new File(projectDirectory, 'settings.gradle').text = "rootProject.name = 'fixture'\n"
        new File(projectDirectory, 'build.gradle').text = '''
            plugins {
                id 'outpost.java-conventions'
            }

            dependencies {
                testImplementation 'junit:junit:4.13.2'
            }
        '''

        File mainDir = new File(projectDirectory, 'src/main/java/com/outpost/fixture')
        mainDir.mkdirs()
        File packageInfo = new File(mainDir, 'package-info.java')
        if (fixture.packageInfo == null) {
            packageInfo.delete()
        } else {
            packageInfo.text = deindent(fixture.packageInfo)
        }
        new File(mainDir, 'Example.java').text = deindent(fixture.main)

        if (fixture.test) {
            File testDir = new File(projectDirectory, 'src/test/java/com/outpost/fixture')
            testDir.mkdirs()
            new File(testDir, 'package-info.java').text = deindent(fixture.packageInfo)
            new File(testDir, 'ExampleTest.java').text = deindent(fixture.test)
        }
        projectDirectory
    }

    private String deindent(String source) {
        source.stripIndent()
    }
}
