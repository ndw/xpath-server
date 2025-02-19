plugins {
    alias(libs.plugins.kotlin.jvm)
    id("com.github.gmazzo.buildconfig") version "5.5.0"
    application
}

repositories {
    mavenCentral()
    mavenLocal()
    maven { url = uri("https://maven.saxonica.com/maven") }
}

val saxonVersion = findProperty("saxonVersion")
val xpathServerVersion = findProperty("xpathServerVersion")

val distributionClasspath by configurations.creating {
  extendsFrom(configurations["runtimeClasspath"])
}

dependencies {
    testImplementation(libs.junit.jupiter)

    implementation("net.sf.saxon:Saxon-HE:${saxonVersion}")

    implementation(libs.restlet)
    implementation(libs.htmlparser)

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(11)
    }
}

application {
    mainClass = "com.nwalsh.xml.XPathServer"
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

buildConfig {
  className("XPathServerBuildConfig")
  packageName("com.nwalsh.xml.xpathserver")
  useKotlinOutput { internalVisibility = false } 

  buildConfigField("NAME", "XPath server")
  buildConfigField("VERSION", "${xpathServerVersion}")
}

fun distClasspath(): List<File> {
  val libs = mutableListOf<File>()
  configurations["distributionClasspath"].forEach {
    if (it.isFile() && !it.getName().startsWith("Saxon-EE")) {
      libs.add(it)
    }
  }
  return libs
}

tasks.jar {
  val libs = mutableListOf<String>()
  for (jar in distClasspath()) {
    libs.add("lib/${jar.getName()}")
  }

  archiveFileName.set("xpath-server-${xpathServerVersion}.jar")
  manifest {
    attributes("Main-Class" to "com.nwalsh.xml.xpathserver.Main",
               "Class-Path" to libs.joinToString(" "))
  }
}

val copyScripts = tasks.register<Copy>("copyScripts") {
  inputs.file(layout.projectDirectory.file("src/main/scripts/xpath-server.sh"))
  inputs.file(layout.projectDirectory.file("src/main/scripts/xpath-server.ps1"))
  outputs.file(layout.buildDirectory.file("stage/xpath-server.sh"))
  outputs.file(layout.buildDirectory.file("stage/xpath-server.ps1"))

  doFirst {
    // Never let different versions get co-staged
    delete(layout.buildDirectory.dir("stage"))
  }

  from(layout.projectDirectory.dir("src/main/scripts"))
  into(layout.buildDirectory.dir("stage"))
  include("xpath-server.sh")
  include("xpath-server.ps1")
  filter { line ->
    line.replace("@@VERSION@@", "${xpathServerVersion}")
  }
}

val copyElisp = tasks.register<Copy>("copyElisp") {
  dependsOn(copyScripts)
  inputs.dir(layout.projectDirectory.file("../elisp"))
  outputs.dir(layout.buildDirectory.file("stage/elisp"))

  from(layout.projectDirectory.file("../elisp"))
  into(layout.buildDirectory.file("stage/elisp"))
  filter { line ->
    line.replace("@@VERSION@@", "${xpathServerVersion}")
  }
}

tasks.register("stage-release") {
  inputs.files(copyScripts)
  inputs.files(copyElisp)
  dependsOn("jar")

  doLast {
    mkdir(layout.buildDirectory.dir("stage"))
    mkdir(layout.buildDirectory.dir("stage/lib"))
    copy {
      from(layout.buildDirectory.dir("libs"))
      into(layout.buildDirectory.dir("stage"))
    }
  }

  doLast {
    distClasspath().forEach { path ->
      copy {
        from(path)
        into(layout.buildDirectory.dir("stage/lib"))
        exclude("META-INF/**")
        exclude("com/**")
      }                      
    }
  }
}

tasks.register<Zip>("release") {
  dependsOn("stage-release")
  from(layout.buildDirectory.dir("stage"))
  into("xpath-server-${xpathServerVersion}/")
  archiveFileName = "xpath-server-${xpathServerVersion}.zip"
}
