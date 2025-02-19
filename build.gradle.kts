import org.eclipse.jgit.api.Git
import org.eclipse.jgit.errors.IncorrectObjectTypeException
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import java.time.ZoneOffset
import java.time.ZonedDateTime

plugins {
	kotlin("jvm") version "2.2.0"
	kotlin("plugin.serialization") version "2.2.0"
	id("com.github.gmazzo.buildconfig") version "5.6.7"
	application
    `maven-publish`
}

group = "app.shosetsu"
description = "Extension tester for shosetsu"

data class Tag(val id: ObjectId, val name: String, val commit: RevCommit, val dateTime: ZonedDateTime)
private val PersonIdent.dateTime get() = ZonedDateTime.ofInstant(whenAsInstant, this.zoneId ?: ZoneOffset.UTC)
// Compute the version by either using the TAG that started the CI build, or...
version = System.getenv("CI_COMMIT_TAG")?.trimStart('v') ?: Git.open(projectDir).use { git ->
	val tag = git.repository.refDatabase
		.getRefsByPrefix(Constants.R_TAGS) // ...picking the latest tag...
		.map { RevWalk(git.repository).use { walk ->
			// Extract the tag information
			val rev = try {
				walk.parseTag(it.objectId)
			} catch (_: IncorrectObjectTypeException) {
				// Lightweight (unannotated) tag
				// Copy information from commit
				val target = walk.parseCommit(it.objectId)
				return@map Tag(it.objectId, Repository.shortenRefName(it.name), target, target.committerIdent.dateTime)
			}
			// Full (annotated) tag
			// Parse the tag and use its' information
			walk.parseBody(rev)
			walk.parseBody(rev.`object`)
			val target = walk.peel(rev)
			walk.parseBody(target)
			Tag(it.objectId, Repository.shortenRefName(rev.tagName), target as RevCommit, rev.taggerIdent.dateTime)
		} }
		.filter { RevWalk(git.repository).use { walk -> walk // ...that is part of the current branch...
			.isMergedInto(walk.parseCommit(it.commit.id), walk.parseCommit(git.repository.resolve("HEAD")))
		} }
		.maxByOrNull { it.dateTime }

	val name = tag?.name?.trimStart('v') ?: "0.0.0+nogit" // ...and taking its name (or a placeholder)

	// If there are new changes since the version, append -SNAPSHOT to prevent collisions
	val hasNewCommits = tag == null || git.log().not(tag.commit.id).add(git.repository.resolve("HEAD")).call().toList().isNotEmpty()
	val dirty = hasNewCommits || git.diff().call().isNotEmpty()

	// If the branch is not a main development branch (=a feature branch), also append the branch name
	val branchName = System.getenv("CI_COMMIT_BRANCH") ?: git.repository.branch ?: "unknown"

	name + (if (branchName in setOf("main", "master", "development")) "" else "+$branchName") + (if (dirty) "-SNAPSHOT" else "")
}

repositories {
	exclusiveContent {
		forRepository {
			maven("https://gitlab.com/api/v4/groups/12585416/-/packages/maven")
		}
		filter {
			includeGroupAndSubgroups("app.shosetsu")
		}
	}
	mavenCentral()
}

kotlin {
	jvmToolchain(21)
}

buildConfig {
	buildConfigField("VERSION", version.toString())
}

val ktlint by configurations.registering

dependencies {
	implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
	implementation("com.github.ajalt.clikt:clikt:5.0.0") // for CLI

	implementation("app.shosetsu.lib:kotlin-lib:1.4.1")
	implementation(kotlin("stdlib"))
	implementation(kotlin("stdlib-jdk8"))
	implementation("org.jsoup:jsoup:1.21.1")
	implementation("com.squareup.okhttp3:okhttp:4.12.0")
	implementation("org.luaj:luaj-jse:3.0.1")

	ktlint("com.pinterest.ktlint:ktlint-cli:1.8.0")
	ktlint("io.github.tobi2k:ktlint-gitlab-reporter:2.0.1")

	testImplementation(kotlin("test"))
}

tasks.test {
	useJUnit()
}

application {
	mainClass.set("app.shosetsu.tester.MainKt")
}

val assembleJar by tasks.registering(Jar::class) {
	archiveFileName = "extension-tester.jar"
	group = "build"

	duplicatesStrategy = DuplicatesStrategy.FAIL

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
		configurations.runtimeClasspath.get()
			.filter { it.name.endsWith("jar") }
			.map { zipTree(it).matching {
				exclude("META-INF/**")
			} }
	)
}

publishing {
	publications {
		create<MavenPublication>("maven") {
			groupId = project.group.toString()
			artifactId = "extension-tester"
			version = project.version.toString()

			from(components["kotlin"])
			artifact(assembleJar) {
				classifier = "all"
			}
		}
	}
	repositories {
		val env = System.getenv()
		val token = env["CI_JOB_TOKEN"]
		if (token != null) {
			maven {
				url = uri("${env["CI_API_V4_URL"]}/projects/${env["CI_PROJECT_ID"]}/packages/maven")
				name = "project"

				credentials(HttpHeaderCredentials::class) {
					name = "Job-Token"
					value = token
				}
				authentication {
					create<HttpHeaderAuthentication>("header")
				}
			}
		}
	}
}

val deploy by tasks.registering {
	group = "publishing"
	description = "Performs the tasks necessary to deploy the library"
	dependsOn(tasks.publish)
}

val outputDir = project.layout.buildDirectory.dir("reports/ktlint/")
val inputFiles = fileTree("src") { include("**/*.kt") }
val editorconfig = rootProject.file(".editorconfig").absolutePath

tasks {
	val ktlintRun by registering(JavaExec::class) {
		group = "verification"
		inputs.files(inputFiles)
		outputs.dir(outputDir)
		mainClass = "com.pinterest.ktlint.Main"
		classpath(ktlint)
		args = listOf("--editorconfig=$editorconfig", "src/**/*.kt", "--reporter=plain?group_by_file", "--reporter=gitlab,output=${outputDir.get().asFile.absolutePath}/ktlint.json")
		jvmArgs = listOf("--add-opens", "java.base/java.lang=ALL-UNNAMED")
	}

	val ktlintFormat by registering(JavaExec::class) {
		group = "verification"
		inputs.files(inputFiles)
		outputs.dir(outputDir)
		mainClass = "com.pinterest.ktlint.Main"
		classpath(ktlint)
		args = listOf("--editorconfig=$editorconfig", "-F", "src/**/*.kt")
		jvmArgs = listOf("--add-opens", "java.base/java.lang=ALL-UNNAMED")
	}

	val lint by registering {
		group = "verification"
		dependsOn(ktlintRun)
	}

	check { dependsOn(lint) }
	compileKotlin { dependsOn(ktlintFormat) }
}
