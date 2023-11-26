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
        var declarations = ArrayList<Declaration>()
        val numbersMap = mutableMapOf("" to true)

        while (numberMatcher.find()) {
            val comments = getCurrentCommentsPositions(modifiedContent)
            declarations = getCurrentDeclarations(modifiedContent, declarations)

            val number: String = numberMatcher.group()
            val currentConstName: String = Companion.CONSTANT_TEMPLATE + number
            val constantDeclaration: String = "val %s : %s = %s".format(currentConstName, "Int", number)

            var numberFirstIndex : Int = numberMatcher.start()
            var numberPostLastIndex : Int = numberMatcher.end()
            val numberRange : IntRange = IntRange(numberFirstIndex, numberPostLastIndex)

            val preFirstCharacter: Char = modifiedContent[numberFirstIndex - 1]
            val postLastCharacter: Char = modifiedContent[numberPostLastIndex]

            if (!isMagicNumber(preFirstCharacter, postLastCharacter)
                || comments.stream().anyMatch { comment -> comment.surrounds(numberRange) }
                || declarations.stream().anyMatch { constant -> constant.surrounds(numberRange) }
            ) {
                continue
            }

            if (declarations.stream().noneMatch { constant -> constant.name == currentConstName }) {
                modifiedContent = StringBuilder(modifiedContent).insert(firstOpenCurlyBracketPosition + 1,
                    "\n\t$constantDeclaration").toString()

                val constantDeclarationMatcher = Regex(constantDeclaration).find(modifiedContent)
                if (constantDeclarationMatcher != null) {
                    declarations.add(
                        Declaration(
                            currentConstName,
                            number,
                            constantDeclarationMatcher.range
                        )
                    )
                }

                numberFirstIndex += constantDeclaration.length + 2
                numberPostLastIndex += constantDeclaration.length + 2
            }

            modifiedContent = StringBuilder(modifiedContent).replace(numberFirstIndex, numberPostLastIndex, currentConstName).toString()

            for (constant: Declaration in declarations) {
                val matcher: Regex = Regex("val %s : Int = \\d+".format(constant.name))
                if (matcher.find(modifiedContent) != null) {
                    constant.range = matcher.find(modifiedContent)!!.range
                }
            }

            if (!numbersMap.containsKey(number)) {
                resultOutputMessageBuilder.append(number).append("; ")
                numbersMap[number] = true
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

        private const val CONSTANT_TEMPLATE = "MAGIC_NUMBER_"
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

    private fun getCurrentDeclarations(modifiedContent: String, declarations: ArrayList<Declaration>) : ArrayList<Declaration> {
        val declarationMatcher = Regex(Companion.DECLARATION_PATTERN).toPattern().matcher(modifiedContent)
        while (declarationMatcher.find()) {
            val declarationParts = declarationMatcher.group().trim().split("=")
            declarations.add(Declaration(declarationParts[0], declarationParts[1], IntRange(declarationMatcher.start(), declarationMatcher.end())))
        }

        return declarations
    }
}
