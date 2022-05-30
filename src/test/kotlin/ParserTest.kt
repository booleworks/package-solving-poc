import com.booleworks.packagesolver.io.parse
import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test

internal class ParserTest {

    @Test
    fun testParser() {
        val problem = parse("src/test/resources/small-test.cudf")

        assertThat(problem.allPackages()).hasSize(9)
        assertThat(problem.allPackageNames()).hasSize(6)
        assertThat(problem.request.install).hasSize(1)
        assertThat(problem.request.remove).isEmpty()
    }
}
