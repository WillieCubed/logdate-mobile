package app.logdate

import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.legacy.pipeline.createProjectEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtPsiFactory

/** Detects grouping that turns several settings rows into one MaterialContainer child. */
object SettingsUiContract {
    private val layouts = setOf("Column", "Row", "MaterialContainer")
    private val rows =
        setOf(
            "SimpleSettingsItem",
            "SettingsNavigationItem",
            "ToggleSettingsItem",
            "LinkedToggleSettingsItem",
            "ExportDataItem",
            "ImportBackupItem",
        )

    fun inspect(source: String): List<Int> {
        val disposable = Disposer.newDisposable()
        try {
            val environment =
                createProjectEnvironment(
                    CompilerConfiguration(),
                    disposable,
                    EnvironmentConfigFiles.JVM_CONFIG_FILES,
                    MessageCollector.NONE,
                )
            val file = KtPsiFactory(environment.project).createFile(source)
            val aliases =
                file.importDirectives.mapNotNull { directive ->
                    directive.aliasName?.let { it to directive.importedFqName!!.shortName().asString() }
                }.toMap()
            fun name(call: KtCallExpression): String? =
                call.calleeExpression?.text?.substringAfterLast('.')?.let { aliases[it] ?: it }

            return PsiTreeUtil.findChildrenOfType(file, KtCallExpression::class.java)
                .filter { name(it) == "SettingsSection" }
                .flatMap { section ->
                    section.valueArguments.mapNotNull { it.getArgumentExpression() as? KtLambdaExpression }
                        .flatMap { it.bodyExpression?.statements.orEmpty() }
                        .filterIsInstance<KtCallExpression>()
                        .filter { name(it) in layouts }
                        .filter { wrapper ->
                            PsiTreeUtil.findChildrenOfType(wrapper, KtCallExpression::class.java)
                                .count { name(it) in rows } > 1
                        }
                        .map { wrapper -> source.take(wrapper.textOffset).count { it == '\n' } + 1 }
                }
        } finally {
            Disposer.dispose(disposable)
        }
    }
}
