package tools.mnreviewer

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.command.*
import com.intellij.openapi.editor.*
import com.intellij.openapi.ui.*
import org.jetbrains.annotations.*

class MagicNumbersReview : AnAction() {

    override fun actionPerformed(e: @NotNull AnActionEvent) {
        val dataContext: DataContext = e.dataContext
        val editor: Editor? = CommonDataKeys.EDITOR.getData(dataContext)

        if (editor?.document == null) {
            Messages.showMessageDialog(Companion.NO_EDITOR_EXCEPTION,
                Companion.MESSAGE_TITLE, Messages.getInformationIcon())
            return
        }

        var modifiedContent = editor.document.text
        val firstOpenCurlyBracketPosition = editor.document.text.indexOfFirst {it == '{'}

        var numberMatcher = Regex(Companion.INTEGERS_PATTERN).toPattern().matcher(modifiedContent)
        if (!numberMatcher.find()) {
            Messages.showMessageDialog(Companion.CONGRATULATION,
                Companion.MESSAGE_TITLE, Messages.getInformationIcon())

            return
        }

        val resultOutputMessageBuilder = StringBuilder()

        while (numberMatcher.find()) {
            val comments = getCurrentCommentsPositions(modifiedContent)
            val declarations = getCurrentDeclarations(modifiedContent)

            val number: String = numberMatcher.group()
            val constantName: String = "MAGIC_NUMBER_" + number.replace('.', '_')
            val constantDeclaration: String = "val %s : %s = %s;".format(constantName, "Int", number)

            var numberStartPosition: Int = numberMatcher.start()
            var numberEndPosition: Int = numberMatcher.end()

            val preFirstCharacter: Char = modifiedContent[numberStartPosition - 1]
            val postLastCharacter: Char = modifiedContent[numberEndPosition]

            if (!isMagicNumber(preFirstCharacter, postLastCharacter)
                || comments.stream().anyMatch { comment -> comment.surrounds(IntRange(numberStartPosition, numberEndPosition)) }
                || declarations.stream().anyMatch { constant -> constant.surrounds(IntRange(numberStartPosition, numberEndPosition)) }
            ) {
                continue
            }

            if (declarations.stream().noneMatch { constant -> constant.name == constantName }) {
                modifiedContent = StringBuilder(modifiedContent).insert(firstOpenCurlyBracketPosition + 1, "\n\t$constantDeclaration").toString()

                val constantDeclarationMatcher = Regex(constantDeclaration).find(modifiedContent)
                if (constantDeclarationMatcher != null) {
                    declarations.add(
                        Declaration(
                            constantName,
                            number,
                            constantDeclarationMatcher.range
                        )
                    )
                }

                numberStartPosition += constantDeclaration.length + 2
                numberEndPosition += constantDeclaration.length + 2
            }

            modifiedContent = StringBuilder(modifiedContent).replace(numberStartPosition, numberEndPosition, constantName).toString()

            for (constant: Declaration in declarations) {
                val matcher: Regex = Regex("val %s : Int = (\\d)+".format(constant.name))
                if (matcher.find(modifiedContent) != null) {
                    constant.range = matcher.find(modifiedContent)!!.range
                }
            }

            if (resultOutputMessageBuilder.indexOf(number) == -1) {
                resultOutputMessageBuilder.append(number).append("; ")
            }

            numberMatcher = Regex(Companion.INTEGERS_PATTERN).toPattern().matcher(modifiedContent)
        }

        val finalModifiedContent = modifiedContent
        WriteCommandAction.runWriteCommandAction(e.project) { editor.document.setText(finalModifiedContent) }

        val resultOutputMessage = resultOutputMessageBuilder.insert(0, "Extracted: ").toString()
        Messages.showMessageDialog(resultOutputMessage, Companion.MESSAGE_TITLE, Messages.getInformationIcon())
    }

    class Comment(private var range: IntRange) {
        fun surrounds(range: IntRange): Boolean {
            return range.first >= this.range.first && range.last <= this.range.last
        }
    }

    class Declaration(val name: String, private val value: String, var range: IntRange) {
        fun surrounds(range: IntRange): Boolean {
            return range.first >= this.range.first && range.last <= this.range.last
        }
    }

    companion object {
        private const val MESSAGE_TITLE = "Message"
        private const val NO_EDITOR_EXCEPTION = "No editor document found!"
        private const val CONGRATULATION = "There's no MAGIC NUMBERS already"

        private const val COMMENT_LINE_PATTERN = "\\/\\/.*"
        private const val COMMENT_BLOCK_PATTERN = "\\/\\*[^\\/]*\\*\\/"

        private const val INTEGERS_PATTERN = "\\d+"
        private const val DECLARATION_PATTERN = "\\w+\\s*=\\s*\\d+"
    }

    private fun isMagicNumber(prefixCharacter: Char, suffixCharacter: Char): Boolean {
        return  !(prefixCharacter.isLetter()
                || suffixCharacter.isLetter()
                || prefixCharacter == '_'
                || suffixCharacter == '_'
                || (prefixCharacter == '"' && suffixCharacter == '"')
                || (prefixCharacter == '\'' && suffixCharacter == '\''))
    }

    private fun getCurrentCommentsPositions(modifiedContent: String) : ArrayList<Comment> {
        val comments = ArrayList<Comment>()

        val commentLineMatcher = Regex(COMMENT_LINE_PATTERN).toPattern().matcher(modifiedContent)
        while (commentLineMatcher.find()) {
            comments.add(Comment(IntRange(commentLineMatcher.start(), commentLineMatcher.end())))
        }

        val commentBlockMatcher = Regex(COMMENT_BLOCK_PATTERN).toPattern().matcher(modifiedContent)
        while (commentBlockMatcher.find()) {
            comments.add(Comment(IntRange(commentBlockMatcher.start(), commentBlockMatcher.end())))
        }

        return comments
    }

    private fun getCurrentDeclarations(modifiedContent: String) : ArrayList<Declaration> {
        val declarations = ArrayList<Declaration>()

        val declarationMatcher = Regex(Companion.DECLARATION_PATTERN).toPattern().matcher(modifiedContent)
        while (declarationMatcher.find()) {
            val declarationParts = declarationMatcher.group().trim().split("=")
            declarations.add(Declaration(declarationParts[0], declarationParts[1], IntRange(declarationMatcher.start(), declarationMatcher.end())))
        }

        return declarations
    }
}
