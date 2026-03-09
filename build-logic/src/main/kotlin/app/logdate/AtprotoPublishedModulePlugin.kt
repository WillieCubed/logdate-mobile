package app.logdate

import groovy.util.Node
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.credentials.PasswordCredentials
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.gradle.plugins.signing.SigningExtension

private const val DefaultAtprotoGroup = "studio.hypertext.atproto"
private const val DefaultAtprotoVersion = "0.1.0"
private const val RepoUrl = "https://github.com/TheHypertextStudio/logdate-android"
private const val OrgUrl = "https://thehypertext.studio"

class AtprotoPublishedModulePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
            pluginManager.apply("org.jetbrains.dokka")
            pluginManager.apply("maven-publish")
            pluginManager.apply("signing")

            group = propertyOrEnv("atproto.group", "ATPROTO_GROUP") ?: DefaultAtprotoGroup
            version = propertyOrEnv("atproto.version", "ATPROTO_VERSION") ?: DefaultAtprotoVersion

            val dokkaHtmlJar =
                tasks.register("dokkaHtmlJar", Jar::class.java) {
                    group = "documentation"
                    description = "Assembles a jar archive containing the Dokka HTML publication."
                    archiveClassifier.set("javadoc")
                    dependsOn(tasks.named("dokkaGeneratePublicationHtml"))
                    from(layout.buildDirectory.dir("dokka/html"))
                }

            val publishing = extensions.getByType(PublishingExtension::class.java)
            configureRepository(publishing)
            configurePublications(publishing, dokkaHtmlJar)

            configureSigning()
        }
    }

    private fun Project.configureRepository(publishing: PublishingExtension) {
        val publishUrl = propertyOrEnv("atproto.publish.url", "ATPROTO_PUBLISH_URL") ?: return
        val username = propertyOrEnv("atproto.publish.username", "ATPROTO_PUBLISH_USERNAME")
        val password = propertyOrEnv("atproto.publish.password", "ATPROTO_PUBLISH_PASSWORD")

        publishing.repositories.maven(
            object : Action<MavenArtifactRepository> {
                override fun execute(repository: MavenArtifactRepository) {
                    repository.name = "atproto"
                    repository.url = uri(publishUrl)
                    if (username != null && password != null) {
                        repository.credentials(
                            PasswordCredentials::class.java,
                            object : Action<PasswordCredentials> {
                                override fun execute(credentials: PasswordCredentials) {
                                    credentials.username = username
                                    credentials.password = password
                                }
                            },
                        )
                    }
                }
            },
        )
    }

    private fun Project.configurePublications(
        publishing: PublishingExtension,
        dokkaHtmlJar: org.gradle.api.tasks.TaskProvider<Jar>,
    ) {
        publishing.publications.withType(MavenPublication::class.java).configureEach(
            object : Action<MavenPublication> {
                override fun execute(publication: MavenPublication) {
                    publication.artifact(dokkaHtmlJar)
                    publication.pom.name.set(project.name)
                    publication.pom.description.set(project.description ?: "Kotlin Multiplatform AT Protocol module.")
                    publication.pom.url.set(RepoUrl)
                    publication.pom.inceptionYear.set("2026")
                    publication.pom.withXml(
                        object : Action<XmlProvider> {
                            override fun execute(xml: XmlProvider) {
                                val root = xml.asNode()
                                root.appendNodeIfMissing("organization").apply {
                                    appendNodeIfMissing("name").setValue("The Hypertext Studio")
                                    appendNodeIfMissing("url").setValue(OrgUrl)
                                }
                                root.appendNodeIfMissing("licenses").appendNodeIfMissing("license").apply {
                                    appendNodeIfMissing("name").setValue("Apache-2.0")
                                    appendNodeIfMissing("url").setValue("https://www.apache.org/licenses/LICENSE-2.0.txt")
                                }
                                root.appendNodeIfMissing("developers").appendNodeIfMissing("developer").apply {
                                    appendNodeIfMissing("id").setValue("thehypertextstudio")
                                    appendNodeIfMissing("name").setValue("The Hypertext Studio")
                                    appendNodeIfMissing("url").setValue(OrgUrl)
                                }
                                root.appendNodeIfMissing("scm").apply {
                                    appendNodeIfMissing("url").setValue(RepoUrl)
                                    appendNodeIfMissing("connection").setValue("scm:git:https://github.com/TheHypertextStudio/logdate-android.git")
                                    appendNodeIfMissing("developerConnection").setValue("scm:git:ssh://git@github.com/TheHypertextStudio/logdate-android.git")
                                }
                                root.appendNodeIfMissing("issueManagement").apply {
                                    appendNodeIfMissing("system").setValue("GitHub")
                                    appendNodeIfMissing("url").setValue("$RepoUrl/issues")
                                }
                            }
                        },
                    )
                }
            },
        )
    }

    private fun Project.configureSigning() {
        val signingKeyId = propertyOrEnv("signingKeyId", "SIGNING_KEY_ID")
        val signingKey = propertyOrEnv("signingKey", "SIGNING_KEY")
        val signingPassword = propertyOrEnv("signingPassword", "SIGNING_PASSWORD")

        if (signingKey != null && signingPassword != null) {
            val signing = extensions.getByType(SigningExtension::class.java)
            signing.useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
            val publishing = extensions.getByType(PublishingExtension::class.java)
            signing.sign(publishing.publications)
        }
    }

    private fun Project.propertyOrEnv(
        propertyName: String,
        envName: String,
    ): String? = providers.gradleProperty(propertyName).orNull ?: providers.environmentVariable(envName).orNull

    private fun Node.appendNodeIfMissing(name: String): Node =
        (get(name) as? List<*>)?.firstOrNull() as? Node ?: appendNode(name)
}
