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

/** Enforces shared settings layout, typography and functional row controls. */
object SettingsUiContract {
    private val layouts = setOf("Column", "Row", "MaterialContainer")
    private val rows =
        setOf(
            "SettingsRow",
            "SettingsChoiceItem",
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

            val calls = PsiTreeUtil.findChildrenOfType(file, KtCallExpression::class.java)
            val grouping = calls
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
            val ungroupedCards = calls
                .filter { name(it) in setOf("Card", "ElevatedCard", "OutlinedCard", "MaterialCard") }
                .filter { card ->
                    PsiTreeUtil.findChildrenOfType(card, KtCallExpression::class.java)
                        .count { row ->
                            name(row) in rows && generateSequence(row.parent) { it.parent }
                                .takeWhile { it != card }
                                .none { it is KtCallExpression && name(it) == "SettingsSection" }
                        } > 1
                }
                .map { card -> source.take(card.textOffset).count { it == '\n' } + 1 }
            val settingsImplementation = file.packageFqName.asString().split('.').contains("settings") ||
                calls.any { name(it) == "SettingsScaffold" || name(it) == "SettingsSection" }
            val duplicatedRows = if (settingsImplementation) calls.filter { name(it) in setOf("ListItem", "Switch", "RadioButton") } else emptyList()
            val typographyOverrides = calls.filter { name(it) == "SettingsRow" }.flatMap { row ->
                row.valueArguments.filter { it.getArgumentName()?.asName?.asString() == "supportingContent" }
                    .mapNotNull { it.getArgumentExpression() }
                    .flatMap { PsiTreeUtil.findChildrenOfType(it, KtCallExpression::class.java) }
                    .filter { call -> name(call) == "Text" && call.valueArguments.any {
                        it.getArgumentName()?.asName?.asString() == "style"
                    } }
            }
            return (grouping + ungroupedCards + (duplicatedRows + typographyOverrides).map { call ->
                source.take(call.textOffset).count { it == '\n' } + 1
            }).distinct().sorted()
        } finally {
            Disposer.dispose(disposable)
        }
    }
}
