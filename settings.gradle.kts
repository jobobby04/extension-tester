buildscript {
	repositories {
		mavenCentral()
	}
	dependencies {
		classpath("org.eclipse.jgit:org.eclipse.jgit:7.3.0.202506031305-r") // https://github.com/eclipse-jgit/jgit/tags
	}
}

rootProject.name = "tester"
