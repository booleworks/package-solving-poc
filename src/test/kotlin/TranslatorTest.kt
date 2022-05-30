import com.booleworks.packagesolver.data.*
import com.booleworks.packagesolver.io.parse
import com.booleworks.packagesolver.translation.ProblemTranslator
import org.assertj.core.api.Assertions.assertThat
import org.logicng.datastructures.Tristate
import org.logicng.formulas.FormulaFactory
import org.logicng.solvers.MiniSat
import org.logicng.solvers.sat.MiniSatConfig
import kotlin.test.Test

internal class TranslatorTest {
    private val problem = parse("src/test/resources/small-test.cudf")
    private val f = FormulaFactory()
    private val translator = ProblemTranslator(f)

    @Test
    fun testTranslateProblem() {
        val translation = translator.translate(problem)
        val solver = MiniSat.miniSat(f, MiniSatConfig.builder().proofGeneration(true).build())
        solver.addPropositions(translation.constraints)
        val sat = solver.sat()
        val variables = problem.allPackages().map { vin(f, it.name, it.version) }
        val solutions = solver.enumerateAllModels(variables)

        assertThat(translation.constraints).hasSize(29)
        assertThat(sat).isEqualTo(Tristate.TRUE)
        assertThat(solutions).allMatch { it.positiveVariables().contains(f.variable("i_MusicPlus_2")) }
        assertThat(solutions).hasSize(12)
    }

    @Test
    fun testTranslatePackage() {
        val depends = And(Or(VersionedPackage("x", RelOp.LT, 3), VersionedPackage("y", RelOp.GE, 2)), VersionedPackage("z"))
        val conflicts = listOf(VersionedPackage("x", RelOp.EQ, 1), VersionedPackage("y", RelOp.LT, 3))
        val pack = Package("p", 2, depends, conflicts)
        val problem = ProblemDescription(listOf(pack), Request())
        val constraints = translator.translatePackage(pack, problem)

        assertThat(constraints).hasSize(3)
    }

    @Test
    fun testGenerateIntervalVariables() {
        val pack = Package("p", 2)
        val iv = translator.generateIntervalVariables(pack, setOf(1, 2))

        assertThat(iv.formula()).isEqualTo(
            f.parse("(~ig_p_2 | i_p_2) & (~ug_p_2 | ~i_p_2) & (~ug_p_2 | ug_p_3) & (~il_p_2 | i_p_2 | il_p_1) & (~ul_p_2 | ~i_p_2) & (~ul_p_2 | ul_p_1)")
        )
    }

    @Test
    fun testTranslateConflicts() {
        val conflicts = listOf(VersionedPackage("x", RelOp.EQ, 1), VersionedPackage("y", RelOp.LT, 3))
        val pack = Package("p", 2, conflicts = conflicts)
        val cc = translator.generateConflictConstraint(pack)

        assertThat(cc.formula()).isEqualTo(f.parse("(~i_p_2 | ~i_x_1) & (~i_p_2 | ul_y_2)"))
    }

    @Test
    fun testConflictTranslation() {
        val p = Package("p", 1)

        assertThat(VersionedPackage("x", RelOp.EQ, 2).translateForConflict(f, p)).isEqualTo(f.parse("~i_p_1 | ~i_x_2"))
        assertThat(VersionedPackage("x", RelOp.NE, 2).translateForConflict(f, p)).isEqualTo(f.parse("(~i_p_1 | ul_x_1) & (~i_p_1 | ug_x_3)"))
        assertThat(VersionedPackage("x", RelOp.GE, 2).translateForConflict(f, p)).isEqualTo(f.parse("~i_p_1 | ug_x_2"))
        assertThat(VersionedPackage("x", RelOp.GT, 2).translateForConflict(f, p)).isEqualTo(f.parse("~i_p_1 | ug_x_3"))
        assertThat(VersionedPackage("x", RelOp.LE, 2).translateForConflict(f, p)).isEqualTo(f.parse("~i_p_1 | ul_x_2"))
        assertThat(VersionedPackage("x", RelOp.LT, 2).translateForConflict(f, p)).isEqualTo(f.parse("~i_p_1 | ul_x_1"))
    }

    @Test
    fun testTranslateDepends() {
        val depends = And(Or(VersionedPackage("x", RelOp.LT, 3), VersionedPackage("y", RelOp.GE, 2)), VersionedPackage("z"))
        val pack = Package("p", 2, depends = depends)
        val cc = translator.generateDependsConstraint(pack, setOf(1, 2, 3))

        assertThat(cc.formula()).isEqualTo(f.parse("(~i_p_2 | il_x_2 | ig_y_2) & (~i_p_2 | ig_z_1)"))
    }

    @Test
    fun testDependsTranslation() {
        val p = Package("p", 1)

        assertThat(VersionedPackage("x", RelOp.EQ, 2).translateForDependency(f, p, setOf(1, 2))).isEqualTo(f.parse("~i_p_1 | i_x_2"))
        assertThat(VersionedPackage("x", RelOp.NE, 2).translateForDependency(f, p, setOf(1, 2))).isEqualTo(f.parse("~i_p_1 | il_x_1"))
        assertThat(VersionedPackage("x", RelOp.GE, 2).translateForDependency(f, p, setOf(1, 2))).isEqualTo(f.parse("~i_p_1 | ig_x_2"))
        assertThat(VersionedPackage("x", RelOp.GT, 2).translateForDependency(f, p, setOf(1, 2))).isEqualTo(f.parse("~i_p_1"))
        assertThat(VersionedPackage("x", RelOp.LE, 2).translateForDependency(f, p, setOf(1, 2))).isEqualTo(f.parse("~i_p_1 | il_x_2"))
        assertThat(VersionedPackage("x", RelOp.LT, 2).translateForDependency(f, p, setOf(1, 2))).isEqualTo(f.parse("~i_p_1 | il_x_1"))
    }

    @Test
    fun testTranslateRequest() {
        val install = listOf(VersionedPackage("x", RelOp.EQ, 1), VersionedPackage("y", RelOp.GE, 3))
        val remove = listOf(VersionedPackage("z", RelOp.EQ, 2))
        val req = translator.translateRequest(Request(install, remove))

        assertThat(req.formula()).isEqualTo(f.parse("i_x_1 & ig_y_3 & ~i_z_2"))
    }

    @Test
    fun testGenerateAmos() {
        val amos = translator.generateAMOs(problem)

        assertThat(amos.formula()).isEqualTo(
            f.parse("(i_SoundModule_1 + i_SoundModule_2 <= 1) & (i_SoundModuleAdvanced_1 + i_SoundModuleAdvanced_2 <= 1) & (i_Spotify_1 + i_Spotify_2 <= 1)")
        )
    }
}
