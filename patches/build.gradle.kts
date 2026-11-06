group = "com.themuhammad.deepseekmod"

patches {
    about {
        name = "DeepSeek Mod Patches"
        description = "Native Android feature patches for the official DeepSeek application."
        source = "https://github.com/themuhammad-personal/DeepSeek-Mod-APK"
        author = "themuhammad-personal"
        contact = "GitHub Issues"
        website = "https://github.com/themuhammad-personal/DeepSeek-Mod-APK"
        license = "All rights reserved"
    }
}

val patchListGeneratorClasspath = configurations.create("patchListGeneratorClasspath")

dependencies {
    compileOnly(libs.gson)
    patchListGeneratorClasspath(libs.gson)
}

tasks {
    register<JavaExec>("generatePatchesList") {
        description = "Generate the Morphe patch list."
        dependsOn(build)
        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("util.PatchListGeneratorKt")
    }

    publish {
        dependsOn("generatePatchesList")
    }
}
