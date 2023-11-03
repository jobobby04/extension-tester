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
	sourceCompatibility = JavaVersion.VERSION_11
}

buildConfig {
	buildConfigField("String", "VERSION", "\"$version\"")
}

dependencies {
	testImplementation(kotlin("test"))

	implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

	implementation("com.gitlab.shosetsuorg:kotlin-lib:3c204042a96efd691478820042b61625c0bc0f32")
	implementation(kotlin("stdlib"))
	implementation(kotlin("stdlib-jdk8"))
	implementation("org.jsoup:jsoup:1.15.3")
	implementation("com.squareup.okhttp3:okhttp:4.10.0")
	implementation("org.luaj:luaj-jse:3.0.1")

	implementation(kotlin("reflect"))
}

tasks.test {
	useJUnit()
}

application {
	mainClass.set("MainKt")
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

