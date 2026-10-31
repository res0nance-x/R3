plugins {
	kotlin("jvm") version "2.4.0"
}

group = "R3"
version = "1.0"

repositories {
	mavenCentral()
}

dependencies {
	testImplementation(kotlin("test"))
}

sourceSets {
	main {
		java.srcDirs("src/main/kotlin")
		kotlin.srcDirs("src/main/kotlin")
	}
}

tasks.withType<Jar> {
	duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

kotlin {
	jvmToolchain(25)
}


tasks.test {
	useJUnitPlatform()
}

tasks.register<JavaExec>("runHub") {
	group = "application"
	description = "Runs the FileMessageHub screenshot edge-filter demo."
	classpath = sourceSets["test"].runtimeClasspath
	mainClass.set("r3.io.FileMessageHubTest")
	standardInput = System.`in`
}