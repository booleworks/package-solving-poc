package com.booleworks.packagesolver.translation

import com.booleworks.packagesolver.data.Package
import com.booleworks.packagesolver.data.ProblemDescription
import com.booleworks.packagesolver.data.Request
import com.booleworks.packagesolver.data.vin
import org.logicng.formulas.Formula
import org.logicng.formulas.FormulaFactory
import org.logicng.propositions.Proposition
import org.logicng.propositions.StandardProposition
import org.logicng.transformations.UnitPropagation

/**
 * Main class for translating a package upgrade problem into a set of Boolean formulas for LogicNG
 */
class ProblemTranslator(private val f: FormulaFactory) {

    companion object {
        private val PSEUDO_PACKAGE = Package("r", 1)
    }

    /**
     * Translates the given problem into a list of LogicNG propositions
     */
    fun translate(problem: ProblemDescription): Translation {
        return Translation(problem.allPackages().flatMap { translatePackage(it, problem) } + translateRequest(problem.request) + generateAMOs(problem))
    }

    internal fun translatePackage(pack: Package, problem: ProblemDescription): List<Proposition> {
        val versions = problem.getAllVersions(pack.name)
        val constraints = mutableListOf(generateIntervalVariables(pack, versions))
        constraints.add(generateConflictConstraint(pack))
        constraints.add(generateDependsConstraint(pack, versions))
        return constraints
    }

    internal fun generateConflictConstraint(pack: Package) =
        StandardProposition("Conflicts for ${pack.name} version ${pack.version}", f.and(pack.conflicts.map { it.translateForConflict(f, pack) }))

    internal fun generateDependsConstraint(pack: Package, versions: Set<Int>) =
        StandardProposition("Depends for ${pack.name} version ${pack.version}", pack.depends.translateForDependency(f, pack, versions))

    internal fun generateIntervalVariables(pack: Package, versions: Set<Int>): Proposition {
        val installed = pack.installed(f)
        val uninstalledGreater = pack.uninstalledGreater(f)
        val uninstalledLess = pack.uninstalledLess(f)
        val installedGreaterPlusOne = if (versions.contains(pack.version + 1)) pack.installedGreaterPlusOne(f) else f.falsum()
        val uninstalledGreaterPlusOne = pack.uninstalledGreaterPlusOne(f)
        val installedLessMinusOne = if (versions.contains(pack.version - 1)) pack.installedLessMinusOne(f) else f.falsum()
        val uninstalledLessMinusOne = pack.uninstalledLessMinusOne(f)
        val cnf = f.cnf(
            f.or(pack.installedGreater(f).negate(), installed, installedGreaterPlusOne),
            f.or(uninstalledGreater.negate(), installed.negate()),
            f.or(uninstalledGreater.negate(), uninstalledGreaterPlusOne),
            f.or(pack.installedLess(f).negate(), installed, installedLessMinusOne),
            f.or(uninstalledLess.negate(), installed.negate()),
            f.or(uninstalledLess.negate(), uninstalledLessMinusOne)
        )
        return StandardProposition("Interval variables for ${pack.name} version ${pack.version}", cnf)
    }

    internal fun translateRequest(request: Request): Proposition {
        val requestVar = vin(f, PSEUDO_PACKAGE.name, PSEUDO_PACKAGE.version)
        val depends = f.and(request.install.map { it.translateForDependency(f, PSEUDO_PACKAGE, setOf(it.version)) })
        val conflicts = f.and(request.remove.map { it.translateForConflict(f, PSEUDO_PACKAGE) })
        val formula = f.and(requestVar, depends, conflicts).transform(UnitPropagation()).substitute(requestVar, f.verum())
        return StandardProposition("Request", formula)
    }

    internal fun generateAMOs(problem: ProblemDescription): Proposition {
        val formulas = mutableListOf<Formula>()
        problem.allPackageNames().forEach { packageName ->
            val versions = problem.getAllVersions(packageName)
            if (versions.size > 1) {
                formulas.add(f.amo(versions.map { v -> vin(f, packageName, v) }))
            }
        }
        return StandardProposition("Each package has at most one installed version", f.and(formulas))
    }
}

data class Translation(val constraints: List<Proposition>)
