package nu.studer.gradle.rocker

import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Unroll

@Unroll
class RockerFuncTest extends BaseFuncTest {

    void "can invoke rocker task derived from minimum configuration DSL"() {
        given:
        exampleTemplate()

        and:
        buildFile << """
plugins {
    id 'nu.studer.rocker'
}

repositories {
    jcenter()
}

rocker {
  foo {
    optimize = true
    templateDir = file('src/rocker')
    outputDir = file('src/generated/rocker')
  }
}
"""

        when:
        def result = runWithArguments('rockerFoo')

        then:
        fileExists('src/generated/rocker/Example.java')
        result.output.contains("Parsing 1 rocker template files")
        result.output.contains("Generated 1 rocker java source files")
        result.output.contains("Generated rocker configuration")
        result.task(':rockerFoo').outcome == TaskOutcome.SUCCESS
    }

    void "can invoke rocker task derived from configuration DSL with multiple items"() {
        given:
        template('src/rocker/main/Example.rocker.html')
        template('src/rocker/test/ExampleTest.rocker.html')

        and:
        buildFile << """
plugins {
    id 'nu.studer.rocker'
}

repositories {
    jcenter()
}

rocker {
  main {
    optimize = true
    templateDir = file('src/rocker/main')
    outputDir = file('src/generated/rocker/main')
  }
  integTest {
    optimize = true
    templateDir = file('src/rocker/test')
    outputDir = file('src/generated/rocker/test')
  }
}
"""

        when:
        def result = runWithArguments('rockerIntegTest')

        then:
        fileExists('src/generated/rocker/test/ExampleTest.java')
        result.task(':rockerIntegTest').outcome == TaskOutcome.SUCCESS

        !fileExists('src/generated/rocker/main/Example.java')
        !result.task(':rockerMain')
    }

    void "can compile Java source files generated by rocker as part of invoking Java compile task with the matching source set"() {
        given:
        exampleTemplate()

        and:
        buildFile << """
plugins {
    id 'nu.studer.rocker'
    id 'java'  // provides 'main' sourceSet
}

repositories {
    jcenter()
}

rocker {
  main {
    optimize = true
    templateDir = file('src/rocker')
    outputDir = file('src/generated/rocker')
  }
}
"""

        when:
        def result = runWithArguments('classes')

        then:
        fileExists('src/generated/rocker/Example.java')
        fileExists('build/classes/main/Example.class')
        result.task(':rockerMain').outcome == TaskOutcome.SUCCESS
        result.task(':classes').outcome == TaskOutcome.SUCCESS
    }

    void "can reconfigure the output dir"() {
        given:
        exampleTemplate()

        and:
        buildFile << """
plugins {
    id 'nu.studer.rocker'
    id 'java'
}

repositories {
    jcenter()
}

rocker {
  main {
    optimize = true
    templateDir = file('src/rocker')
    outputDir = file('src/generated/rocker')
  }
}

rocker.main.outputDir = file('src/generated/rocker/other')

afterEvaluate {
  SourceSetContainer sourceSets = project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets()
  SourceSet sourceSet = sourceSets.findByName('main')
  Set<File> dirs = sourceSet.getJava().getSrcDirs()
  dirs.eachWithIndex { dir, index ->
    println "\$dir---"
  }
}
"""

        when:
        def result = runWithArguments('classes')

        then:
        fileExists('src/generated/rocker/other/Example.java')
        fileExists('build/classes/main/Example.class')
        result.output.contains('dir/src/main/java---')
        result.output.contains('dir/src/generated/rocker/other---')
        !result.output.contains('dir/src/generated/rocker---')
        result.task(':rockerMain').outcome == TaskOutcome.SUCCESS
        result.task(':classes').outcome == TaskOutcome.SUCCESS
    }

    void "can set custom rocker version"() {
        given:
        exampleTemplate()

        and:
        buildFile << """
plugins {
    id 'nu.studer.rocker'
    id 'java'
}

repositories {
    jcenter()
}

rockerVersion = '0.15.0'

rocker {
  main {
    optimize = true
    templateDir = file('src/rocker')
    outputDir = file('src/generated/rocker')
  }
}
"""

        when:
        def result = runWithArguments('dependencies')

        then:
        result.output.contains('com.fizzed:rocker-compiler: -> 0.15.0')
        result.output.contains('com.fizzed:rocker-runtime: -> 0.15.0')
    }

    void "participates in incremental build"() {
        given:
        exampleTemplate()

        and:
        buildFile << """
plugins {
    id 'nu.studer.rocker'
    id 'java'
}

repositories {
    jcenter()
}

rocker {
  main {
    optimize = true
    templateDir = file('src/rocker')
    outputDir = file('src/generated/rocker')
  }
}
"""

        when:
        def result = runWithArguments('rockerMain')

        then:
        fileExists('src/generated/rocker/Example.java')
        result.task(':rockerMain').outcome == TaskOutcome.SUCCESS

        when:
        result = runWithArguments('rockerMain')

        then:
        result.task(':rockerMain').outcome == TaskOutcome.UP_TO_DATE
    }

    void "detects when task is not uptodate anymore"() {
        given:
        template("${templateDirFirst}/Example1.rocker.html")
        template("${templateDirSecond}/Example2.rocker.html")

        when:
        rockerMainBuildFile(optimizeFirst, templateDirFirst, outputDirFirst)

        def result = runWithArguments('rockerMain')

        then:
        fileExists("${outputDirFirst}/Example1.java")
        result.task(':rockerMain').outcome == TaskOutcome.SUCCESS

        when:
        rockerMainBuildFile(optimizeSecond, templateDirSecond, outputDirSecond)

        result = runWithArguments('rockerMain')

        then:
        fileExists("${outputDirSecond}/Example2.java")
        result.task(':rockerMain').outcome == TaskOutcome.SUCCESS

        where:
        optimizeFirst | optimizeSecond | templateDirFirst | templateDirSecond | outputDirFirst          | outputDirSecond
        true          | false          | 'src/rocker'     | 'src/rocker'      | 'src/generated/rocker'  | 'src/generated/rocker'
        true          | true           | 'src/rocker1'    | 'src/rocker2'     | 'src/generated/rocker'  | 'src/generated/rocker'
        true          | true           | 'src/rocker'     | 'src/rocker'      | 'src/generated/rocker1' | 'src/generated/rocker2'
    }

    void "can clean sources generated by rocker as part of the clean life-cycle task"() {
        given:
        exampleTemplate()

        and:
        buildFile << """
plugins {
    id 'nu.studer.rocker'
    id 'java'
}

repositories {
    jcenter()
}

rocker {
  main {
    optimize = true
    templateDir = file('src/rocker')
    outputDir = file('src/generated/rocker')
  }
}
"""

        when:
        runWithArguments('rockerMain')

        then:
        fileExists('src/generated/rocker/Example.java')

        when:
        def result = runWithArguments('clean')

        then:
        !fileExists('src/generated/rocker/Example.java')
        result.task(':cleanRockerMain').outcome == TaskOutcome.SUCCESS
    }

    void "can customize java execution and handle execution result"() {
        given:
        exampleTemplate()

        and:
        buildFile << """
plugins {
    id 'nu.studer.rocker'
}

repositories {
    jcenter()
}

rocker {
  foo {
    optimize = true
    templateDir = file('src/rocker')
    outputDir = file('src/generated/rocker')
  }
}

def out = new ByteArrayOutputStream()
rockerFoo {
  javaExecSpec = { JavaExecSpec s ->
    s.standardOutput = out
    s.errorOutput = out
    s.ignoreExitValue = true
  }
  execResultHandler = { ExecResult r ->
    if (r.exitValue == 0) {
      println('Rocker template compilation succeeded')
    }
  }
}
"""

        when:
        def result = runWithArguments('rockerFoo')

        then:
        fileExists('src/generated/rocker/Example.java')
        result.output.contains("Rocker template compilation succeeded")
        result.task(':rockerFoo').outcome == TaskOutcome.SUCCESS
    }

    private Writer rockerMainBuildFile(boolean optimize, String templateDir, String outputDir) {
        buildFile.newWriter().withWriter { w ->
            w << """
plugins {
    id 'nu.studer.rocker'
    id 'java'
}

repositories {
    jcenter()
}

rocker {
  main {
    optimize = ${Boolean.toString(optimize)}
    templateDir = file('${templateDir}')
    outputDir = file('${outputDir}')
  }
}
"""
        }
    }

    private File exampleTemplate() {
        template('src/rocker/Example.rocker.html')
    }

    private void template(String fileName) {
        file(fileName) << """
@args (String message)
Hello @message!
"""
    }

    private boolean fileExists(String filePath) {
        def file = new File(workspaceDir, filePath)
        file.exists() && file.file
    }

}
