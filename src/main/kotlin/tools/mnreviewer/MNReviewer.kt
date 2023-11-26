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
            Messages.showMessageDialog(NO_EDITOR_EXCEPTION,
                MESSAGE_TITLE, Messages.getInformationIcon())
            return
        }

        var modifiedContent = editor.document.text

        val resultOutputMessageBuilder = StringBuilder()
        var declarations = ArrayList<Declaration>()
        val numbersMap = mutableMapOf("" to true)

        val firstOpenCurlyBracketPosition = modifiedContent.indexOfFirst {it == '{'}

        modifiedContent = StringBuilder(modifiedContent).insert(firstOpenCurlyBracketPosition + 1,
            "\n\tval constants = object {\n\t}\n").toString()

        val secondOpenCurlyBracketPosition = modifiedContent.indexOf('{', firstOpenCurlyBracketPosition + 1)

        var numberMatcher = Regex(INTEGERS_PATTERN).toPattern().matcher(modifiedContent)
        if (!numberMatcher.find()) {
            Messages.showMessageDialog(CONGRATULATION,
                MESSAGE_TITLE, Messages.getInformationIcon())

            return
        }

        while (numberMatcher.find()) {
            val comments = getCurrentCommentsPositions(modifiedContent)
            declarations = getCurrentDeclarations(modifiedContent, declarations)

            val number: String = numberMatcher.group()
            val constInObjectName: String = CONSTANT_IN_OBJECT_TEMPLATE + number
            val constInMainName: String = CONSTANT_IN_MAIN_TEMPLATE + number
            val constantDeclaration: String = CONSTANT_DECLARATION_TEMPLATE.format(constInObjectName, "Int", number)

            var numberFirstIndex : Int = numberMatcher.start()
            var numberPostLastIndex : Int = numberMatcher.end()
            val numberRange = IntRange(numberFirstIndex, numberPostLastIndex)

            val preFirstCharacter: Char = modifiedContent[numberFirstIndex - 1]
            val postLastCharacter: Char = modifiedContent[numberPostLastIndex]

            if (!isMagicNumber(preFirstCharacter, postLastCharacter)
                || comments.stream().anyMatch { comment -> comment.surrounds(numberRange) }
                || declarations.stream().anyMatch { constant -> constant.surrounds(numberRange) }
            ) {
                continue
            }

            if (declarations.stream().noneMatch { constant -> constant.name == constInObjectName }) {
                modifiedContent = StringBuilder(modifiedContent).insert(secondOpenCurlyBracketPosition + 1,
                    "\n\t\t$constantDeclaration").toString()

                val constantDeclarationMatcher = Regex(constantDeclaration).find(modifiedContent)
                if (constantDeclarationMatcher != null) {
                    declarations.add(
                        Declaration(
                            constInObjectName,
                            number,
                            constantDeclarationMatcher.range
                        )
                    )
                }

                numberFirstIndex += constantDeclaration.length + 3
                numberPostLastIndex += constantDeclaration.length + 3
            }

            modifiedContent = StringBuilder(modifiedContent)
                .replace(numberFirstIndex, numberPostLastIndex, constInMainName).toString()

            for (constant: Declaration in declarations) {
                val matcher: Regex = Regex(CONSTANT_DECLARATION_PATTERN.format(constant.name))
                if (matcher.find(modifiedContent) != null) {
                    constant.range = matcher.find(modifiedContent)!!.range
                }
            }

            if (!numbersMap.containsKey(number)) {
                resultOutputMessageBuilder.append(number).append("; ")
                numbersMap[number] = true
            }

            numberMatcher = Regex(INTEGERS_PATTERN).toPattern().matcher(modifiedContent)
        }

        WriteCommandAction.runWriteCommandAction(e.project) {
            editor.document.setText(modifiedContent)
        }

        val resultOutputMessage = resultOutputMessageBuilder.insert(0, "Extracted: ").toString()
        Messages.showMessageDialog(resultOutputMessage, MESSAGE_TITLE, Messages.getInformationIcon())
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

        private const val CONSTANT_IN_OBJECT_TEMPLATE = "MAGIC_NUMBER_"
        private const val CONSTANT_IN_MAIN_TEMPLATE = "constants.MAGIC_NUMBER_"

        private const val CONSTANT_DECLARATION_PATTERN = "val %s : Int = \\d+"
        private const val CONSTANT_DECLARATION_TEMPLATE = "val %s : %s = %s"
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

    private fun getCurrentDeclarations(modifiedContent: String,
                                       declarations: ArrayList<Declaration>): ArrayList<Declaration> {

        val declarationMatcher = Regex(Companion.DECLARATION_PATTERN).toPattern().matcher(modifiedContent)

        while (declarationMatcher.find()) {
            val declarationParts = declarationMatcher.group().trim().split("=")
            declarations.add(Declaration(
                declarationParts[0],
                declarationParts[1],
                IntRange(declarationMatcher.start(), declarationMatcher.end()))
            )
        }

        return declarations
    }
}
