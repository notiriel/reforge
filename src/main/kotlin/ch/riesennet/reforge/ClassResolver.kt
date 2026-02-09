package ch.riesennet.reforge

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AllClassesSearch

/**
 * Matches classes in a project against glob-style patterns.
 *
 * Supported wildcards:
 * - `*` matches any characters within a single package/class name segment
 * - `**` matches any number of package segments
 */
object ClassResolver {

    private fun isExactPattern(pattern: String): Boolean {
        return !pattern.contains('*')
    }

    /**
     * Converts a glob pattern to a regex pattern.
     *
     * @param pattern The glob pattern (e.g., "com.example.**.*Entity")
     * @return A regex pattern that matches qualified class names
     */
    fun patternToRegex(pattern: String): Regex {
        val regexPattern = buildString {
            append("^")
            var i = 0
            while (i < pattern.length) {
                when {
                    pattern.startsWith("**", i) -> {
                        if (i + 2 < pattern.length && pattern[i + 2] == '.') {
                            append("(.*\\.)?")
                            i += 3  // Skip ** and the following .
                        } else {
                            append(".*")
                            i += 2
                        }
                    }
                    pattern[i] == '*' -> {
                        append("[^.]*")
                        i++
                    }
                    pattern[i] == '.' -> {
                        append("\\.")
                        i++
                    }
                    else -> {
                        append(Regex.escape(pattern[i].toString()))
                        i++
                    }
                }
            }
            append("$")
        }
        return Regex(regexPattern)
    }

    /**
     * Finds all classes in the project matching the given pattern.
     *
     * For exact class names (no wildcards), uses JavaPsiFacade.findClass() which is
     * more reliable. For glob patterns, falls back to AllClassesSearch.
     *
     * @param project The IntelliJ project
     * @param pattern The glob pattern to match
     * @return List of matching PsiClass instances
     */
    fun findMatchingClasses(project: Project, pattern: String): List<PsiClass> {
        return ReadAction.compute<List<PsiClass>, Exception> {
            if (isExactPattern(pattern)) {
                findExactClass(project, pattern)
            } else {
                findByGlobPattern(project, pattern)
            }
        }
    }

    private fun findExactClass(project: Project, qualifiedName: String): List<PsiClass> {
        val scope = GlobalSearchScope.projectScope(project)
        val psiClass = JavaPsiFacade.getInstance(project).findClass(qualifiedName, scope)
        return if (psiClass != null) listOf(psiClass) else emptyList()
    }

    private fun findByGlobPattern(project: Project, pattern: String): List<PsiClass> {
        val regex = patternToRegex(pattern)
        val scope = GlobalSearchScope.projectScope(project)
        val matches = mutableListOf<PsiClass>()

        AllClassesSearch.search(scope, project).forEach { psiClass ->
            val qualifiedName = psiClass.qualifiedName
            if (qualifiedName != null && regex.matches(qualifiedName)) {
                matches.add(psiClass)
            }
        }

        return matches
    }

    /**
     * Checks if a qualified class name matches the given pattern.
     */
    fun matches(pattern: String, qualifiedName: String): Boolean {
        return patternToRegex(pattern).matches(qualifiedName)
    }
}
