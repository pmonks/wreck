<img alt="wreck logo: a generated image of a shipwreck on a beach" align="right" width="25%" src="https://raw.githubusercontent.com/pmonks/wreck/dev/wreck-logo.png">

| | | |
|---:|:---:|:---:|
| [**release**](https://github.com/pmonks/wreck/tree/release) | [![CI](https://github.com/pmonks/wreck/actions/workflows/ci.yml/badge.svg?branch=release)](https://github.com/pmonks/wreck/actions?query=workflow%3ACI+branch%3Arelease) | [![Dependencies](https://github.com/pmonks/wreck/actions/workflows/dependencies.yml/badge.svg?branch=release)](https://github.com/pmonks/wreck/actions?query=workflow%3Adependencies+branch%3Arelease) |
| [**dev**](https://github.com/pmonks/wreck/tree/dev)  | [![CI](https://github.com/pmonks/wreck/actions/workflows/ci.yml/badge.svg?branch=dev)](https://github.com/pmonks/wreck/actions?query=workflow%3ACI+branch%3Adev) | [![Dependencies](https://github.com/pmonks/wreck/actions/workflows/dependencies.yml/badge.svg?branch=dev)](https://github.com/pmonks/wreck/actions?query=workflow%3Adependencies+branch%3Adev) |

[![Latest Version](https://img.shields.io/clojars/v/com.github.pmonks/wreck)](https://clojars.org/com.github.pmonks/wreck/) [![Open Issues](https://img.shields.io/github/issues/pmonks/wreck.svg)](https://github.com/pmonks/wreck/issues) [![License](https://img.shields.io/github/license/pmonks/wreck.svg)](https://github.com/pmonks/wreck/blob/release/LICENSE) [![Vulnerabilities](https://github.com/pmonks/wreck/actions/workflows/vulnerabilities.yml/badge.svg?branch=dev)](https://github.com/pmonks/wreck/actions?query=workflow%3Avulnerabilities+branch%3Adev)


# wreck - the "Whackadoodle Regular Expression Construction Kit"

A micro-library for Clojure(Script) that provides regular expression construction functions.  It has no dependencies, other than on Clojure(Script), and emits standard Clojure(Script) regular expression objects, so is fully compatible with Clojure(Script)'s built-in regular expression functions (it does not use any JVM-specific or JavaScript-specific regex syntax, though of course it can be used to construct platform-specific regexes if you wish).

The library is _not_ intended to provide an entirely new syntax for constructing regular expressions - knowledge of regular expression syntax is still necessary, and the library is intended to be used with Clojure(Script)'s regular expression literals as components of larger regular expressions (which might be built partly or entirely using the functions in this library).

It also pairs very nicely with one of my other micro-libraries, [`rencg`](https://github.com/pmonks/rencg).

#### Why?

I have other projects that perform complex text processing and in some cases have ended up writing very large regular expressions (as large as ~10KB), and maintaining huge regular expressions while keeping them syntactically and functionally correct using nothing but regular expression literals, is... ..."challenging".  As a result I'd started using some helper functions so that I could modularise those regular expressions and test and construct them in pieces, and before long I realised that these functions were independently useful, despite not being complex or novel.

## Installation

`wreck` is available as a Maven artifact from [Clojars](https://clojars.org/com.github.pmonks/wreck).

### Trying it Out

#### Clojure CLI

```shell
$ clj -Sdeps '{:deps {com.github.pmonks/wreck {:mvn/version "RELEASE"}}}'
```

#### Leiningen

```shell
$ lein try com.github.pmonks/wreck
```

#### deps-try

```shell
$ deps-try com.github.pmonks/wreck
```

### Demo

```clojure
(require '[wreck.api :as re])


;####TODO!!!!


```

## Usage

[API documentation is available here](https://pmonks.github.io/wreck/), or [here on cljdoc](https://cljdoc.org/d/com.github.pmonks/wreck/), and the [unit tests](https://github.com/pmonks/wreck/blob/release/test/wreck/api_test.clj) are also worth perusing to see worked examples.

## Contributor Information

[Contributing Guidelines](https://github.com/pmonks/wreck/blob/release/.github/CONTRIBUTING.md)

[Bug Tracker](https://github.com/pmonks/wreck/issues)

[Code of Conduct](https://github.com/pmonks/wreck/blob/release/.github/CODE_OF_CONDUCT.md)

### Developer Workflow

This project uses the [git-flow branching strategy](https://nvie.com/posts/a-successful-git-branching-model/), and the permanent branches are called `release` and `dev`.  Any changes to the `release` branch are considered a release and auto-deployed (JARs to Clojars, API docs to GitHub Pages, etc.).

For this reason, **all development must occur either in branch `dev`, or (preferably) in temporary branches off of `dev`.**  All PRs from forked repos must also be submitted against `dev`; the `release` branch is **only** updated from `dev` via PRs created by the core development team.  All other changes submitted to `release` will be rejected.

### Build Tasks

`wreck` uses [`tools.build`](https://clojure.org/guides/tools_build). You can get a list of available tasks by running:

```
clojure -A:deps -T:build help/doc
```

Of particular interest are:

* `clojure -T:build test` - run the unit tests
* `clojure -T:build lint` - run the linters (clj-kondo and eastwood)
* `clojure -T:build ci` - run the full CI suite (check for outdated dependencies, run the unit tests, run the linters)
* `clojure -T:build install` - build the JAR and install it locally (e.g. so you can test it with downstream code)

Please note that the `release` and `deploy` tasks are restricted to the core development team (and will not function if you run them yourself).

## License

Copyright © 2025 Peter Monks

Distributed under the [Mozilla Public License, version 2.0](https://www.mozilla.org/en-US/MPL/2.0/).

SPDX-License-Identifier: [`MPL-2.0`](https://spdx.org/licenses/MPL-2.0)
