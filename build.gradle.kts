buildscript {
    repositories {
        gradleScriptKotlin()
    }

    dependencies {
        classpath(kotlinModule("gradle-plugin"))
    }
}

plugins {
    application
}

apply {
    plugin("kotlin")
    plugin("idea")
}

application {
    mainClassName = "info.modoff.spoofvotingserver.SpoofVotingServerKt"
}

repositories {
    gradleScriptKotlin()
}

dependencies {
    compile(kotlinModule("stdlib"))
    compile(kotlinModule("reflect"))
    compile("com.fasterxml.jackson.module:jackson-module-kotlin:2.8.7")
    compile("net.sourceforge.argparse4j:argparse4j:0.7.0")
}
