package com.booleworks.packagesolver.solver

import com.booleworks.packagesolver.data.*
import com.booleworks.packagesolver.translation.ProblemTranslator
import org.logicng.datastructures.Assignment
import org.logicng.datastructures.Tristate
import org.logicng.formulas.FormulaFactory
import org.logicng.formulas.Variable
import org.logicng.solvers.MiniSat
import org.logicng.solvers.SATSolver
import org.logicng.solvers.functions.OptimizationFunction

private const val PREFIX_HELPER_CHANGED = "s"
private const val PREFIX_HELPER_NOTUPTODATE = "t"
private const val PREFIX_HELPER_SOFTCLAUSE = "sc"

/**
 * The result of solving a package upgrade problem. A single result consists of
 * - a list of packages which have to be newly installed
 * - a list of packages which have to be removed
 * - a list of packages which have to upgraded
 */
data class Result(val install: List<VersionedPackage>, val remove: List<VersionedPackage>, val upgrade: List<VersionedPackage>)

/**
 * The optimization criteria for the package solver
 * - REMOVED reduces the number of installed packages which are removed
 * - NEW reduces the number of packages which are newly installed
 * - CHANGED reduces the number of changed packages wrt. to the current installation (install/remove)
 * - NOTUPTODATE reduces the number of installed packages which are not at their maximum available version
 */
enum class Criterion { REMOVED, NEW, CHANGED, NOTUPTODATE }

/**
 * The package solver for package upgrade problems.
 */
class PackageSolver(private val problem: ProblemDescription) {

    private val f = FormulaFactory()
    private val installed = problem.currentInstallation()
    private val solver = initSolver()
    private val solverState = solver.saveState()
    private val allVars = problem.allPackages().map { vin(solver.factory(), it.name, it.version) }

    /**
     * Computes an optimal solution for the given criterion.  If there are more than one optimal
     * solution, an arbitrary one is returned.
     */
    fun optimalSolution(criterion: Criterion): Result {
        solver.loadState(solverState)
        return createResult(computeOptimum(criterion))
    }

    /**
     * Computes all solutions for the package upgrade problem.
     */
    fun allSolutions(): List<Result> {
        return solver.enumerateAllModels(allVars).map { createResult(it) }
    }

    private fun computeOptimum(criterion: Criterion): Assignment {
        val optimum = when (criterion) {
            Criterion.REMOVED -> computeRemoveCriterion()
            Criterion.NEW -> computeNewCriterion()
            Criterion.CHANGED -> computeChangedCriterion()
            Criterion.NOTUPTODATE -> computeNotUptodateCriterion()
        }
        return optimum
    }

    private fun computeRemoveCriterion(): Assignment {
        val optimizationVars = problem.allPackageNames().filter { installed.keys.contains(it) }.map { vig(solver.factory(), it, 1) }
        return solver.execute(OptimizationFunction.builder().literals(optimizationVars).additionalVariables(allVars).maximize().build())
    }

    private fun computeNewCriterion(): Assignment {
        val optimizationVars = problem.allPackageNames().filter { !installed.keys.contains(it) }.map { vug(solver.factory(), it, 1) }
        return solver.execute(OptimizationFunction.builder().literals(optimizationVars).additionalVariables(allVars).maximize().build())
    }

    private fun computeChangedCriterion(): Assignment {
        val optimizationVars = mutableSetOf<Variable>()
        problem.allPackages().forEach { p ->
            f.variable("${PREFIX_HELPER_CHANGED}_${p.name}").let { helper ->
                solver.add(f.or(helper.negate(), f.literal(vin(f, p.name, p.version).name(), p.installed)))
                optimizationVars.add(helper)
            }
        }
        return solver.execute(OptimizationFunction.builder().literals(optimizationVars).additionalVariables(allVars).maximize().build())
    }

    private fun computeNotUptodateCriterion(): Assignment {
        val helperVars = mutableSetOf<Variable>()
        problem.allPackages().forEach { p ->
            f.variable("${PREFIX_HELPER_NOTUPTODATE}_${p.name}").let { helper ->
                solver.add(f.or(vin(f, p.name, p.version).negate(), helper))
                helperVars.add(helper)
            }
        }
        val optimizationVars = mutableSetOf<Variable>()
        helperVars.forEach {
            val packageName = it.name().split("_")[1]
            val maxVersion = problem.getAllVersions(packageName).last()
            f.variable("${PREFIX_HELPER_SOFTCLAUSE}_${packageName}").let { helper ->
                solver.add(f.equivalence(helper, f.or(it.negate(), vin(f, packageName, maxVersion))))
                optimizationVars.add(helper)
            }
        }
        return solver.execute(OptimizationFunction.builder().literals(optimizationVars).additionalVariables(allVars).maximize().build())
    }

    private fun createResult(solution: Assignment): Result {
        val newInstallation = solution.positiveVariables()
            .filter { it.name().startsWith("${PREFIX_INSTALLED}_") }
            .map { it.name().split("_").let { t -> VersionedPackage(t[1], RelOp.EQ, t[2].toInt()) } }
        val install = mutableSetOf<VersionedPackage>()
        val remove = HashSet(installed.values)
        val update = mutableSetOf<VersionedPackage>()
        newInstallation.forEach {
            installed[it.name].let { currentPackage ->
                remove.remove(it)
                when (currentPackage) {
                    null -> install.add(it)
                    else -> if (currentPackage.version != it.version) {
                        update.add(it)
                        remove.remove(currentPackage)
                    }
                }
            }
        }
        return Result(install.sortedBy { it.name }, remove.map { it }.sortedBy { it.name }, update.sortedBy { it.name })
    }

    private fun initSolver(): SATSolver {
        val solver = MiniSat.miniSat(f)
        solver.addPropositions(ProblemTranslator(f).translate(problem).constraints)
        return solver.takeUnless { it.sat() == Tristate.FALSE } ?: throw IllegalArgumentException("Original problem was not satisfiable")
    }
}

