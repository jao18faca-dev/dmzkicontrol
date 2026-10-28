plugins {
    java
    idea
    id("net.minecraftforge.gradle") version "6.0.30"
    id("org.parchmentmc.librarian.forgegradle") version "1.+"
}

fun prop(name: String): String = property(name) as String

group = "com.dmzkicontrol"
version = prop("mod_version")

base {
    archivesName.set("DragonMineZ-Evolution")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

repositories {
    maven("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/") // GeckoLib + mclib
    maven("https://maven.blamejared.com/")                             // TerraBlender
    maven("https://maven.theillusivec4.top/")                          // Curios
    flatDir { dirs("libs") }                                            // jar do DragonMineZ
    mavenCentral()
}

minecraft {
    // Mesmos mappings do DMZ (wiki: Add-on Getting Started)
    mappings("parchment", "2023.09.03-1.20.1")
    runs {
        configureEach {
            workingDirectory(project.file("run"))
            property("forge.logging.console.level", "info")
            // O DMZ usa mixins: deixa o ambiente de dev remapear o refmap dele
            property("mixin.env.remapRefMap", "true")
            property("mixin.env.refMapRemappingFile", "${projectDir}/build/createSrgToMcp/output.srg")
            mods {
                create("dmzkicontrol") {
                    source(sourceSets.main.get())
                }
            }
        }
        create("client")
        create("server") { args("--nogui") }
    }
}

dependencies {
    minecraft("net.minecraftforge:forge:1.20.1-${prop("forge_version")}")

    // Dependencias obrigatorias do DMZ (precisam estar no classpath de dev)
    implementation(fg.deobf("software.bernie.geckolib:geckolib-forge-1.20.1:4.8.3"))
    implementation("com.eliotlash.mclib:mclib:20")
    implementation(fg.deobf("com.github.glitchfiend:TerraBlender-forge:1.20.1-3.0.1.10"))
    compileOnly(fg.deobf("top.theillusivec4.curios:curios-forge:5.14.1+1.20.1:api"))
    runtimeOnly(fg.deobf("top.theillusivec4.curios:curios-forge:5.14.1+1.20.1"))

    // DragonMineZ: libs/dragonminez-<dmz_version>.jar (o jar SEM "-slim")
    implementation(fg.deobf("local:dragonminez:${prop("dmz_version")}"))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("META-INF/mods.toml") { expand(props) }
}

tasks.named<Jar>("jar") {
    manifest {
        attributes(
            "Specification-Title" to "dmzkicontrol",
            "Implementation-Title" to "DMZ Ki Control",
            "Implementation-Version" to project.version
        )
    }
    finalizedBy("reobfJar")
}
