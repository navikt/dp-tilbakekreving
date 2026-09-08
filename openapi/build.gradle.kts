plugins {
    id("ch.acanda.gradle.fabrikt") version "1.40.0"
    id("common")
    `java-library`
}

tasks {
    compileKotlin {
        dependsOn("fabriktGenerateBehandling")
    }
}

tasks.named("runKtlintCheckOverMainSourceSet").configure {
    dependsOn("fabriktGenerateBehandling")
}

tasks.named("runKtlintFormatOverMainSourceSet").configure {
    dependsOn("fabriktGenerateBehandling")
}

sourceSets {
    main {
        java {
            setSrcDirs(listOf("src/main/kotlin", "${layout.buildDirectory.get()}/generated/src/main/kotlin"))
        }
    }
}

ktlint {
    filter {
        exclude { element -> element.file.path.contains("generated") }
    }
}

dependencies {
    implementation(libs.bundles.jackson)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.jackson)
}

fabrikt {
    generate("behandling") {
        apiFile = file("$projectDir/src/main/resources/behandling-api.yaml")
        basePackage = "no.nav.dagpenger.tilbakekreving.behandling.api"
        skip = false
        quarkusReflectionConfig = disabled
        typeOverrides {
            datetime = LocalDateTime
        }
        model {
            generate = enabled
            validationLibrary = NoValidation
            extensibleEnums = disabled
            sealedInterfacesForOneOf = enabled
            ignoreUnknownProperties = disabled
            nonNullMapValues = enabled
            serializationLibrary = Jackson
            suffix = "DTO"
        }
        client {
            generate = enabled
            target = Ktor
            suspendModifier = enabled
        }
    }
}
