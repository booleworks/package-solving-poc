# A Proof of Concept Package Solver with LogicNG

This is a minimal proof of concept implementation of a package solver for the software package upgradability problem.  It supports a limited subset of the [Mancoosi CUDF Format](https://www.mancoosi.org/cudf/), namely

- packages
- versions
- dependencies
- conflicts

and can solve install/remove/upgrade requests with four different optimization strategies

1. minimize newly installed packages
2. minimize removal of currently installed packages
3. minimze changes wrt. the current installation (a combination of the latter two)
4. maximize the number of currently installed packages which are upgraded to their latest version

The problem encoding is taken from [PackUp: Tools for Package Upgradability Solving](https://content.iospress.com/articles/journal-on-satisfiability-boolean-modeling-and-computation/sat190090) and implemented with [LogicNG](https://github.com/logic-ng/LogicNG).

# Usage

See the example [input]() and the tests in [SolverTest](https://github.com/booleworks/package-solving-poc/blob/main/src/test/kotlin/SolverTest.kt) for a basic usage of the parser and solver.  A basic request consists of only two lines of code

```kotlin
val problem = parse("src/test/resources/small-test.cudf")
val result = PackageSolver(problem).optimalSolution(Criterion.NEW)
```

The first line parses the input file into the internal data structure, the second line computes an optimal solutions with the NEW strategy, i.e. minimizing newly installed packages.

# Disclaimer

This is a very small proof of concept implementation without any optimizations and only a limited set of functions.  Do not expect to be able to answer some real-life package problems like the Linux package system with it ;)
