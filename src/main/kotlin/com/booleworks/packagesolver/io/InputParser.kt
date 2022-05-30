package com.booleworks.packagesolver.io

import com.booleworks.packagesolver.data.*
import java.io.BufferedReader
import java.io.File

private val BLANK = "\\s+".toRegex()
private const val COMMA = ","
private const val PACKAGE = "package: "
private const val VERSION = "version: "
private const val CONFLICTS = "conflicts: "
private const val INSTALLED = "installed: "
private const val DEPENDS = "depends: "
private const val AND = ","
private const val OR = "|"
private const val TRUE = "true!"
private const val FALSE = "false!"

private const val REQUEST = "request: "
private const val INSTALL = "install: "
private const val REMOVE = "remove: "

/**
 * Parses a minimal subset of the <a href="https://www.mancoosi.org/cudf/">CUDF format</a>
 * in a very proof-of-concept manner.  No proper error handling is implemented.
 */
fun parse(fileName: String): ProblemDescription {
    val reader = File(fileName).bufferedReader()
    val packages = mutableListOf<Package>()
    var request: Request? = null
    while (reader.ready()) {
        val line = reader.readLine()
        if (line.startsWith(PACKAGE)) {
            packages += parsePackage(reader, line)
        } else if (line.startsWith(REQUEST)) {
            request = parseRequest(reader)
        }
    }
    if (request == null) {
        throw IllegalStateException("No request in file")
    }
    return ProblemDescription(packages, request)
}

private fun parsePackage(reader: BufferedReader, line: String): Package {
    val pc = PackageContent(line.split(PACKAGE)[1])
    while (reader.ready()) {
        val currentLine = reader.readLine()
        if (currentLine.isBlank()) {
            return Package(pc.name, pc.version, pc.depends, pc.conflicts, pc.installed)
        }
        if (currentLine.startsWith(VERSION)) {
            pc.version = currentLine.split(VERSION)[1].toInt()
        } else if (currentLine.startsWith(CONFLICTS)) {
            pc.conflicts = parseVPkgList(currentLine.split(CONFLICTS)[1])
        } else if (currentLine.startsWith(INSTALLED)) {
            pc.installed = currentLine.split(INSTALLED)[1].toBoolean()
        } else if (currentLine.startsWith(DEPENDS)) {
            pc.depends = parsePackageFormula(currentLine.split(DEPENDS)[1])
        }
    }
    throw IllegalStateException("Should never happen")
}

private fun parseRequest(reader: BufferedReader): Request {
    val rc = RequestContent()
    while (reader.ready()) {
        val currentLine = reader.readLine()
        if (currentLine.isBlank()) {
            return Request(rc.install, rc.remove)
        }
        if (currentLine.startsWith(INSTALL)) {
            rc.install = parseVPkgList(currentLine.split(INSTALL)[1])
        } else if (currentLine.startsWith(REMOVE)) {
            rc.remove = parseVPkgList(currentLine.split(REMOVE)[1])
        }
    }
    return Request(rc.install, rc.remove)
}

private fun parseVPkgList(string: String): List<VersionedPackage> = string.split(COMMA).map { parseVPkg(it.trim()) }

private fun parseVPkg(string: String): VersionedPackage {
    val tokens = string.split(BLANK)
    return if (tokens.size == 1) VersionedPackage(tokens[0]) else VersionedPackage(tokens[0], parseRelOp(tokens[1]), tokens[2].toInt())
}

private fun parseRelOp(string: String): RelOp = when (string) {
    "=" -> RelOp.EQ
    "!=" -> RelOp.NE
    "<" -> RelOp.LT
    "<=" -> RelOp.LE
    ">" -> RelOp.GT
    ">=" -> RelOp.GE
    else -> throw IllegalStateException("Unknown relop: $string")
}

private fun parsePackageFormula(string: String): PackageFormula {
    val clauses = string.split(AND).map { parseClause(it.trim()) }
    return if (clauses.size == 1) clauses[0] else And(clauses)
}

private fun parseClause(string: String): PackageFormula {
    val atoms = string.split(OR).map { parseAtom(it.trim()) }
    return if (atoms.size == 1) atoms[0] else Or(atoms)
}

private fun parseAtom(string: String): PackageFormula = when (string.trim()) {
    TRUE -> Constant(true)
    FALSE -> Constant(false)
    else -> parseVPkg(string)
}

private data class PackageContent(
    val name: String,
    var version: Int = -1,
    var depends: PackageFormula = Constant(true),
    var conflicts: List<VersionedPackage> = listOf(),
    var installed: Boolean = false,
)

private data class RequestContent(
    var install: List<VersionedPackage> = listOf(),
    var remove: List<VersionedPackage> = listOf()
)
