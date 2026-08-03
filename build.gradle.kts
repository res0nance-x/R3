plugins {
	kotlin("jvm") version "2.4.0"
}

group = "org.example"
version = "1.0-SNAPSHOT"

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