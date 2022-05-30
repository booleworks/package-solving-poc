import com.booleworks.packagesolver.data.RelOp
import com.booleworks.packagesolver.data.VersionedPackage
import com.booleworks.packagesolver.io.parse
import com.booleworks.packagesolver.solver.Criterion
import com.booleworks.packagesolver.solver.PackageSolver
import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test

internal class SolverTest {

    @Test
    fun testSolverNew() {
        val problem = parse("src/test/resources/small-test.cudf")
        val result = PackageSolver(problem).optimalSolution(Criterion.NEW)

        println(result)

        assertThat(result.install).contains(VersionedPackage("MusicPlus", RelOp.EQ, 2))
        assertThat(result.install).hasSize(2)
    }

    @Test
    fun testSolverRemove() {
        val problem = parse("src/test/resources/small-test.cudf")
        val result = PackageSolver(problem).optimalSolution(Criterion.REMOVED)

        assertThat(result.install).contains(VersionedPackage("MusicPlus", RelOp.EQ, 2))
        assertThat(result.remove).isEmpty()
    }

    @Test
    fun testSolverChanged() {
        val problem = parse("src/test/resources/small-test.cudf")
        val result = PackageSolver(problem).optimalSolution(Criterion.CHANGED)

        assertThat(result.install).contains(VersionedPackage("MusicPlus", RelOp.EQ, 2))
        assertThat(result.install.size + result.remove.size).isEqualTo(3)
    }

    @Test
    fun testSolverNotUptodate() {
        val problem = parse("src/test/resources/small-test.cudf")
        val result = PackageSolver(problem).optimalSolution(Criterion.NOTUPTODATE)

        assertThat(result.install).contains(VersionedPackage("MusicPlus", RelOp.EQ, 2))
    }

    @Test
    fun testSolverAllSolutions() {
        val problem = parse("src/test/resources/small-test.cudf")
        val result = PackageSolver(problem).allSolutions()

        assertThat(result).hasSize(12)
    }
}
