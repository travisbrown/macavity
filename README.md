# macavity

[![Build status](https://img.shields.io/travis/travisbrown/macavity/master.svg)](http://travis-ci.org/travisbrown/macavity)
[![Coverage status](https://img.shields.io/codecov/c/github/travisbrown/macavity/master.svg)](https://codecov.io/github/travisbrown/macavity)
[![Maven Central](https://img.shields.io/maven-central/v/io.macavity/macavity-core_2.11.svg)](https://maven-badges.herokuapp.com/maven-central/io.macavity/macavity-finagle_2.11)

This project provides alternative implementations of things like [Cats][cats]'s
`StateT` that are specifically designed for use in situations where performance
is important. For example, we'd like to use `StateT` in [Finch][finch], but our
initial [experiments][finch-559] suggest that the overhead the version in Cats
would introduce would not be consistent with Finch's goals.

The `StateT` implementation provided by this project is mostly source-compatible
with Cats's, but it takes a different approach to providing stack safety, and it
uses methods in places where the Cats version uses `FunctionN` values. The
initial benchmarks suggest that this approach may get us closer to something
that would work for Finch (higher numbers are better):

```
Benchmark                        Mode  Cnt    Score   Error  Units
StateTBenchmark.traverseStateC  thrpt   40   61.313 ± 0.507  ops/s
StateTBenchmark.traverseStateM  thrpt   40  131.301 ± 1.728  ops/s
```

And allocation rates (lower is better):

```
Benchmark                                           Mode  Cnt         Score     Error   Units

StateTBenchmark.traverseStateC:gc.alloc.rate.norm  thrpt   20  19767449.565 ±  50.382    B/op
StateTBenchmark.traverseStateM:gc.alloc.rate.norm  thrpt   20  13126749.061 ±  27.103    B/op
```

## Status

This is currently an experimental project. It is not published anywhere, and may
never be. If you'd like to contribute, please see the [open issues][issues] or
get in touch on [Twitter][travisbrown]. 

## License

Licensed under the **[Apache License, Version 2.0][apache]** (the "License");
you may not use this software except in compliance with the License.

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

[apache]: http://www.apache.org/licenses/LICENSE-2.0
[cats]: https://github.com/typelevel/cats
[finch]: https://github.com/finagle/finch/
[finch-559]: https://github.com/finagle/finch/pull/559
[issues]: https://github.com/travisbrown/macavity/issues
[travisbrown]: https://twitter.com/travisbrown
