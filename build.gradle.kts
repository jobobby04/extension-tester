plugins {
	kotlin("jvm") version "1.9.20"
	id("com.github.gmazzo.buildconfig") version "4.1.2"
	application
}

group = "com.github.doomsdayrs.lib"
version = "1.1.2"

repositories {
	mavenCentral()
	maven("https://jitpack.io")
}

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(11)
	}
}

buildConfig {
	buildConfigField("String", "VERSION", "\"$version\"")
}

dependencies {
	testImplementation(kotlin("test"))

	implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
	implementation("com.github.ajalt.clikt:clikt:4.2.2") // for CLI
	implementation("org.slf4j:slf4j-simple:2.0.13")
	implementation("io.github.oshai:kotlin-logging-jvm:6.0.9")

	implementation("com.gitlab.shosetsuorg:kotlin-lib:11a3569bc32a47d8026901925152cfec1fdf6b5c")
	implementation(kotlin("stdlib"))
	implementation(kotlin("stdlib-jdk8"))
	implementation("org.jsoup:jsoup:1.16.2")
	implementation("com.squareup.okhttp3:okhttp:4.12.0")
	implementation("org.luaj:luaj-jse:3.0.1")

	implementation(kotlin("reflect"))
}

tasks.test {
	useJUnit()
}

application {
	mainClass.set("app.shosetsu.tester.MainKt")
}

tasks.register<Jar>("assembleJar") {
	val programVersion = archiveVersion.get()
	archiveVersion.set("")


	duplicatesStrategy = DuplicatesStrategy.INCLUDE

	manifest {
		attributes(
			"Main-Class" to application.mainClass,
			"Implementation-Title" to "Gradle",
			"Implementation-Version" to programVersion
		)
	}

	from(sourceSets.main.get().output)
	dependsOn(configurations.runtimeClasspath)
	from(
		configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
	)
}

