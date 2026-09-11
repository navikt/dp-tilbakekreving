import de.undercouch.gradle.tasks.download.Download

plugins {
    id("common")
    `java-library`
    id("ch.acanda.gradle.fabrikt") version "1.40.0"
    id("de.undercouch.download") version "5.7.0"
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

val apiSpecFile = layout.buildDirectory.file("tmp/behandling-api.yaml")

val hentOpenAPI by tasks.register<Download>("hentOpenAPI") {
    src("https://raw.githubusercontent.com/navikt/dp-behandling/refs/heads/main/openapi/src/main/resources/behandling-api.yaml")
    dest(apiSpecFile)
    overwrite(true)
    group = "openapi"
    description = "Henter OpenAPI spesifikasjonen fra github og lagrer den lokalt"
}

fabrikt {
    generate("behandling") {
        apiFile = apiSpecFile
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
