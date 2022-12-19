plugins {
    kotlin("multiplatform") version "1.7.21"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev/")
    }
    maven {
        url = uri("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap/")
    }
    maven {
        url = uri("https://dl.google.com/dl/android/maven2")
    }
    maven {
        url = uri("https://jitpack.io")
    }
}




kotlin {
    jvm {

        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
        }
        withJava()
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
    js(BOTH) {
        browser {
            commonWebpackConfig {
                cssSupport.enabled = true
            }
        }
    }
    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")
    val nativeTarget = when {
        hostOs == "Mac OS X" -> macosX64("native")
        hostOs == "Linux" -> linuxX64("native")
        isMingwX64 -> mingwX64("native")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    
    sourceSets {
        val commonMain by getting
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {
            dependencies {
                //Log
                implementation( "log4j:log4j:1.2.17")
                implementation("org.slf4j:slf4j-api:1.7.28")
                implementation("org.slf4j:slf4j-log4j12:1.7.28")
                implementation("net.sourceforge.argparse4j:argparse4j:0.8.1")

                //Intellij core
                implementation("com.android.tools.external.com-intellij:intellij-core:30.0.4")

                // TODO: not local location
                implementation(files("/home/oleg/.konan/kotlin-native-prebuilt-linux-x86_64-1.7.21/konan/lib/kotlin-native-compiler-embeddable.jar"))
            }
        }
    }
}
