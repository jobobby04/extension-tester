plugins {
	kotlin("jvm") version "2.0.20"
	kotlin("plugin.serialization") version "2.0.20"
	id("com.github.gmazzo.buildconfig") version "5.5.0"
	application
}

group = "com.github.doomsdayrs.lib"
version = "1.1.2"

repositories {
	mavenCentral()
	maven("https://jitpack.io")
}

kotlin {
	jvmToolchain(11)
}

buildConfig {
	buildConfigField("VERSION", version.toString())
}

dependencies {
	testImplementation(kotlin("test"))

	implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
	implementation("com.github.ajalt.clikt:clikt:5.0.0") // for CLI

	implementation("com.gitlab.jobobby04:kotlin-lib:fe2f95a2a5")
	implementation(kotlin("stdlib"))
	implementation(kotlin("stdlib-jdk8"))
	implementation("org.jsoup:jsoup:1.18.1")
	implementation("com.squareup.okhttp3:okhttp:4.12.0")
	implementation("org.luaj:luaj-jse:3.0.1")
}

tasks.test {
	useJUnit()
}

application {
	mainClass.set("app.shosetsu.tester.MainKt")
}

tasks.register<Jar>("assembleJar") {
	archiveFileName = "${project.name}.jar"
	group = "build"

	duplicatesStrategy = DuplicatesStrategy.INCLUDE

	manifest {
		attributes(
			"Main-Class" to application.mainClass,
			"Implementation-Title" to "Gradle",
			"Implementation-Version" to project.version.toString()
		)
	}

	from(sourceSets.main.get().output)
	dependsOn(configurations.runtimeClasspath)
	from(
		configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
	)
}

