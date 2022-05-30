package com.booleworks.packagesolver.data

import org.logicng.formulas.Formula
import org.logicng.formulas.FormulaFactory
import org.logicng.formulas.Variable
import java.util.*

const val PREFIX_INSTALLED = "i"
const val PREFIX_INSTALLED_GREATER = "ig"
const val PREFIX_INSTALLED_LESS = "il"
const val PREFIX_UNINSTALLED_GREATER = "ug"
const val PREFIX_UNINSTALLED_LESS = "ul"

fun vin(f: FormulaFactory, p: String, v: Int): Variable = f.variable("${PREFIX_INSTALLED}_${p}_${v}")
fun vig(f: FormulaFactory, p: String, v: Int): Variable = f.variable("${PREFIX_INSTALLED_GREATER}_${p}_$v")
fun vil(f: FormulaFactory, p: String, v: Int): Variable = f.variable("${PREFIX_INSTALLED_LESS}_${p}_$v")
fun vug(f: FormulaFactory, p: String, v: Int): Variable = f.variable("${PREFIX_UNINSTALLED_GREATER}_${p}_$v")
fun vul(f: FormulaFactory, p: String, v: Int): Variable = f.variable("${PREFIX_UNINSTALLED_LESS}_${p}_$v")

/**
 * An enum for relational operators =, !=, >=, >, <=, < between versions
 */
enum class RelOp { EQ, NE, GE, GT, LE, LT }

/**
 * Main data structure for a package upgrade problem description.  Consists of a list of all packages
 * and a request which packages should be installed and/or removed.
 */
data class ProblemDescription(val packageMap: Map<Pair<String, Int>, Package>, val request: Request) {
    constructor(packages: List<Package>, request: Request) : this(packages.associateBy { Pair(it.name, it.version) }, request)

    fun allPackages(): List<Package> = packageMap.values.toList()
    fun allPackageNames(): Set<String> = packageMap.values.map { it.name }.toSet()
    fun currentInstallation(): Map<String, VersionedPackage> = packageMap.values.filter { it.installed }.associate { Pair(it.name, it.toVersionedPackage()) }
    fun getAllVersions(packageName: String): SortedSet<Int> = packageMap.filter { it.key.first == packageName }.map { it.key.second }.toSortedSet()
}

/**
 * A single package has a name and a version.  It can depend on or conflict with other packages and can be
 * currently installed or not.
 */
data class Package(
    val name: String,
    val version: Int,
    val depends: PackageFormula = Constant(true),
    val conflicts: List<VersionedPackage> = listOf(),
    val installed: Boolean = false,
) {
    fun installed(f: FormulaFactory) = vin(f, name, version)
    fun installedGreater(f: FormulaFactory) = vig(f, name, version)
    fun installedGreaterPlusOne(f: FormulaFactory) = vig(f, name, version + 1)
    fun installedLess(f: FormulaFactory) = vil(f, name, version)
    fun installedLessMinusOne(f: FormulaFactory) = vil(f, name, version - 1)
    fun uninstalledGreater(f: FormulaFactory) = vug(f, name, version)
    fun uninstalledGreaterPlusOne(f: FormulaFactory) = vug(f, name, version + 1)
    fun uninstalledLess(f: FormulaFactory) = vul(f, name, version)
    fun uninstalledLessMinusOne(f: FormulaFactory) = vul(f, name, version - 1)

    fun toVersionedPackage() = VersionedPackage(name, RelOp.EQ, version)
}

/**
 * A request has a list of packages which should be installed and a list which packages should be removed.
 */
data class Request(
    val install: List<VersionedPackage> = listOf(),
    val remove: List<VersionedPackage> = listOf()
)

/**
 * A package formula consists of constants true/false, predicates between package versions, and
 * Boolean operators OR and AND.
 */
sealed interface PackageFormula {
    fun translateForDependency(f: FormulaFactory, p: Package, vs: Set<Int>): Formula
}

data class Constant(val value: Boolean) : PackageFormula {
    override fun translateForDependency(f: FormulaFactory, p: Package, vs: Set<Int>): Formula =
        if (value) f.verum() else vin(f, p.name, p.version).negate()
}

data class VersionedPackage(val name: String, val relOp: RelOp = RelOp.GE, val version: Int = 1) : PackageFormula {
    fun translateForConflict(f: FormulaFactory, p: Package): Formula = when (relOp) {
        RelOp.EQ -> f.or(vin(f, p.name, p.version).negate(), vin(f, name, version).negate())
        RelOp.NE -> f.and(
            f.or(vin(f, p.name, p.version).negate(), vul(f, name, version - 1)),
            f.or(vin(f, p.name, p.version).negate(), vug(f, name, version + 1))
        )
        RelOp.GE -> f.or(vin(f, p.name, p.version).negate(), vug(f, name, version))
        RelOp.GT -> f.or(vin(f, p.name, p.version).negate(), vug(f, name, version + 1))
        RelOp.LE -> f.or(vin(f, p.name, p.version).negate(), vul(f, name, version))
        RelOp.LT -> f.or(vin(f, p.name, p.version).negate(), vul(f, name, version - 1))
    }

    override fun translateForDependency(f: FormulaFactory, p: Package, vs: Set<Int>): Formula = when (relOp) {
        RelOp.EQ -> f.or(vin(f, p.name, p.version).negate(), vin(f, name, version))
        RelOp.NE -> {
            val vil = if (vs.contains(version - 1)) vil(f, name, version - 1) else f.falsum()
            val vig = if (vs.contains(version + 1)) vig(f, name, version + 1) else f.falsum()
            f.or(vin(f, p.name, p.version).negate(), vil, vig)
        }
        RelOp.GE -> f.or(vin(f, p.name, p.version).negate(), vig(f, name, version))
        RelOp.GT -> f.or(vin(f, p.name, p.version).negate(), if (vs.contains(version + 1)) vig(f, name, version + 1) else f.falsum())
        RelOp.LE -> f.or(vin(f, p.name, p.version).negate(), vil(f, name, version))
        RelOp.LT -> f.or(vin(f, p.name, p.version).negate(), if (vs.contains(version - 1)) vil(f, name, version - 1) else f.falsum())
    }

    override fun toString(): String {
        return "$name $relOp $version"
    }
}

data class Or(val operands: List<PackageFormula>) : PackageFormula {
    constructor(vararg ops: PackageFormula) : this(ops.toList())

    override fun translateForDependency(f: FormulaFactory, p: Package, vs: Set<Int>): Formula = f.or(operands.map { it.translateForDependency(f, p, vs) })
}

data class And(val operands: List<PackageFormula>) : PackageFormula {
    constructor(vararg ops: PackageFormula) : this(ops.toList())

    override fun translateForDependency(f: FormulaFactory, p: Package, vs: Set<Int>): Formula = f.and(operands.map { it.translateForDependency(f, p, vs) })
}
