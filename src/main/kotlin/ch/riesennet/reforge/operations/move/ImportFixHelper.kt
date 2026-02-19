package ch.riesennet.reforge.operations.move

/**
 * Pure-logic helper for import fix operations after class moves.
 * No IntelliJ PSI dependencies — fully unit-testable.
 */
object ImportFixHelper {

    /**
     * Given moved classes, infer additional *Impl entries for MapStruct mappers.
     * @param movedClasses oldFqn → newFqn for all successfully moved classes
     * @param mapperFqns set of NEW FQNs identified as @Mapper interfaces
     * @return additional entries to merge into movedClasses (oldImpl → newImpl)
     */
    fun inferMapperImplMoves(
        movedClasses: Map<String, String>,
        mapperFqns: Set<String>
    ): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for ((oldFqn, newFqn) in movedClasses) {
            if (newFqn !in mapperFqns) continue
            val oldImpl = "${oldFqn}Impl"
            if (oldImpl in movedClasses) continue
            result[oldImpl] = "${newFqn}Impl"
        }
        return result
    }

    /**
     * Build a package rename map from moved classes.
     * Groups by old package → set of distinct new packages.
     */
    fun buildPackageRenameMap(
        movedClasses: Map<String, String>
    ): Map<String, Set<String>> {
        val result = mutableMapOf<String, MutableSet<String>>()
        for ((oldFqn, newFqn) in movedClasses) {
            val oldPkg = oldFqn.substringBeforeLast('.', "")
            val newPkg = newFqn.substringBeforeLast('.', "")
            if (oldPkg.isNotEmpty() && newPkg.isNotEmpty()) {
                result.getOrPut(oldPkg) { mutableSetOf() }.add(newPkg)
            }
        }
        return result
    }

    /**
     * Given an unresolvable import FQN and a package rename map,
     * return candidate new FQNs. Empty if no package match.
     */
    fun resolveViaPackageRename(
        importFqn: String,
        packageRenameMap: Map<String, Set<String>>
    ): Set<String> {
        val dotIndex = importFqn.lastIndexOf('.')
        if (dotIndex < 0) return emptySet()
        val importPkg = importFqn.substring(0, dotIndex)
        val simpleName = importFqn.substring(dotIndex + 1)
        val newPackages = packageRenameMap[importPkg] ?: return emptySet()
        return newPackages.map { "$it.$simpleName" }.toSet()
    }
}
