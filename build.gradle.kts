import org.eclipse.jgit.api.Git
import org.eclipse.jgit.errors.IncorrectObjectTypeException
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import java.io.FileNotFoundException
import java.net.URI
import java.time.ZoneOffset
import java.time.ZonedDateTime

plugins {
	kotlin("jvm") version "2.1.10"
	kotlin("plugin.serialization") version "2.1.10"
	id("com.github.gmazzo.buildconfig") version "5.5.0"
	application
    `maven-publish`
}

group = "app.shosetsu"

data class Tag(val id: ObjectId, val name: String, val commit: RevCommit, val dateTime: ZonedDateTime)
private val PersonIdent.dateTime get() = ZonedDateTime.ofInstant(whenAsInstant, this.zoneId ?: ZoneOffset.UTC)
version = System.getenv("CI_COMMIT_TAG") ?: Git.open(projectDir).use { git ->
	val tag = git.repository.refDatabase
		.getRefsByPrefix(Constants.R_TAGS)
		.map { RevWalk(git.repository).use { walk ->
			val rev = try {
				walk.parseTag(it.objectId)
			} catch (_: IncorrectObjectTypeException) {
				// Lightweight (unannotated) tag
				// Copy information from commit
				val target = walk.parseCommit(it.objectId)
				return@map Tag(it.objectId, Repository.shortenRefName(it.name), target, target.committerIdent.dateTime)
			}
			walk.parseBody(rev)
			walk.parseBody(rev.`object`)
			val target = walk.peel(rev)
			walk.parseBody(target)
			Tag(it.objectId, Repository.shortenRefName(rev.tagName), target as RevCommit, rev.taggerIdent.dateTime)
		} }
		.filter { RevWalk(git.repository).use { walk -> walk
			.isMergedInto(walk.parseCommit(it.commit.id), walk.parseCommit(git.repository.resolve("HEAD")))
		} }
		.maxByOrNull { it.dateTime }

	val name = tag?.name?.trimStart('v') ?: "0.0.0+nogit"
	val hasNewCommits = tag == null || RevWalk(git.repository).use { walk -> walk.isMergedInto(walk.parseCommit(git.repository.resolve("HEAD")), walk.parseCommit(tag.commit.id)) }
	val dirty = hasNewCommits || git.diff().call().isNotEmpty()

	val branchName = System.getenv("CI_DEFAULT_BRANCH") ?: git.repository.branch ?: "unknown"

	name + (if (branchName in setOf("main", "master", "development")) "" else "+$branchName") + (if (dirty) "-SNAPSHOT" else "")
}

repositories {
	mavenCentral()
	maven("https://gitlab.com/api/v4/projects/61884451/packages/maven")
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
	implementation("com.github.ajalt.clikt:clikt:5.0.0") // for CLI

	implementation("app.shosetsu.lib:kotlin-lib:0.0.0+nogit-SNAPSHOT")
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

val assembleJar by tasks.registering(Jar::class) {
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
		val namespaceId = env["CI_PROJECT_NAMESPACE_ID"]
		if (token != null && namespaceId != null) {
			val checkResult = try {
				URI.create("${env["CI_API_V4_URL"]}/groups/$namespaceId").toURL().openStream().use { String(it.readAllBytes()) }
			} catch (_: FileNotFoundException) {
				null
			}
			// If this is a group, we can publish to it
			// Otherwise, we assume it's a user namespace, which does not support Maven packages
			if (checkResult != null) {
				maven {
					url = uri("${env["CI_API_V4_URL"]}/groups/$namespaceId/-/packages/maven")
					name = "group"

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
}