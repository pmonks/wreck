<img alt="wreck logo: a generated image of a shipwreck on a beach" align="right" width="25%" src="https://raw.githubusercontent.com/pmonks/wreck/dev/wreck-logo.png">

| | | |
|---:|:---:|:---:|
| [**release**](https://github.com/pmonks/wreck/tree/release) | [![CI](https://github.com/pmonks/wreck/actions/workflows/ci.yml/badge.svg?branch=release)](https://github.com/pmonks/wreck/actions?query=workflow%3ACI+branch%3Arelease) | [![Dependencies](https://github.com/pmonks/wreck/actions/workflows/dependencies.yml/badge.svg?branch=release)](https://github.com/pmonks/wreck/actions?query=workflow%3Adependencies+branch%3Arelease) |
| [**dev**](https://github.com/pmonks/wreck/tree/dev)  | [![CI](https://github.com/pmonks/wreck/actions/workflows/ci.yml/badge.svg?branch=dev)](https://github.com/pmonks/wreck/actions?query=workflow%3ACI+branch%3Adev) | [![Dependencies](https://github.com/pmonks/wreck/actions/workflows/dependencies.yml/badge.svg?branch=dev)](https://github.com/pmonks/wreck/actions?query=workflow%3Adependencies+branch%3Adev) |

[![Latest Version](https://img.shields.io/clojars/v/com.github.pmonks/wreck)](https://clojars.org/com.github.pmonks/wreck/) [![Open Issues](https://img.shields.io/github/issues/pmonks/wreck.svg)](https://github.com/pmonks/wreck/issues) [![License](https://img.shields.io/github/license/pmonks/wreck.svg)](https://github.com/pmonks/wreck/blob/release/LICENSE) [![Vulnerabilities](https://github.com/pmonks/wreck/actions/workflows/vulnerabilities.yml/badge.svg?branch=dev)](https://github.com/pmonks/wreck/actions?query=workflow%3Avulnerabilities+branch%3Adev)


# wreck - the "Whacky Regular Expression Construction Kit"

A micro-library for Clojure(Script) that provides a selection of regular expression construction functions.  It has no dependencies, other than on Clojure(Script), and emits standard Clojure(Script) regular expression objects, so is fully compatible with Clojure(Script)'s built-in regular expression functions (it does not use any JVM-specific or JavaScript-specific regex syntax, though it can be used with platform-specific regular expression fragments to produce platform-specific regular expressions, if that's what you want).

The library is _not_ intended to provide a comprehensive functional alternative for constructing regular expressions - knowledge of regular expression syntax and literals remains necessary.  The library is instead intended to assist in constructing syntactically valid Clojure(Script) regular expressions by combining smaller regular expressions fragments.

It also pairs very nicely with [`rencg`](https://github.com/pmonks/rencg).

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

;; Basics

(re/esc ".*")
;=> "\\.\\*"  ; Note: a String - most other fns return regexes

(re/qot ".*")
;=> #"\Q.*\E"

(re/join #"a" #"b")
;=> #"ab"

(re/join "[" #"\p{Punct}" #"\p{Space}" "]+")  ; join also supports strings, allowing syntactically invalid fragments to be used to build up a valid expression
;=> #"[\p{Punct}\p{Space}]+"

(re/grp #"a" #"b")
;=> #"(?:ab)"  ; Default group is non-capturing

(re/cg #"a" #"b")
;=> #"(ab)"  ; But we can also do capturing

(re/ncg "ab" #"a" #"b")
;=> #"(?<ab>ab)"  ; And named capturing (much more useful, with rencg!)

; Because ClojureJVM doesn't implement equality for regexes, even though
; ClojureScript does...  🙄
(re/=' #"ab" (re/join #"ab"))
;=> true


;; Cardinality

(re/zom #"foo")
;=> #"foo*"  ; Probably not what we want, so...

(re/zom-grp #"foo")
;=> #"(?:foo)*"  ; That's more like it!

(re/oom-grp #"foo")
;=> #"(?:foo)+"

(re/exn-grp 2 #"foo")
;=> #"(?:foo){2}"

(re/nom-grp 4 #"foo")
;=> #"(?:foo){4,}"

(re/n2m-grp 12 17 #"foo")
;=> #"(?:foo){12,17}"


;; Alternation

(re/alt #"foo" #"bar")
;=> #"foo|bar"

(re/alt-grp #"foo" #"bar")  ; In case the alternates are themselves complex regexes
;=> #"(?:foo)|(?:bar)"


;; Logical operations

(re/and' #"foo" #"bar")
;=> #"foobar|barfoo"

(re/and-grp #"foo" #"bar")
;=> #"(?:foo)(?:bar)|(?:bar)(?:foo)"

(re/or' #"foo" #"bar")
;=> #"foobar|barfoo|foo|bar"

(re/or-grp #"foo" #"bar")
;=> #"(?:foo)(?:bar)|(?:bar)(?:foo)|(?:foo)|(?:bar)"

(re/or-grp #"foo" #"bar" #"\s+")
;=> #"(?:foo)(?:\s+)(?:bar)|(?:bar)(?:\s+)(?:foo)|(?:foo)|(?:bar)"


;; Complex example that composes a medium sized regex from just a few
;; easy-to-read statements (taken from the unit tests)

(def lorl-re (re/grp (re/or' #"Lesser" #"Library" #"\s+or\s+")))  ; "Lesser or Library", but in any order, or either word by itself
;=> #"(?:Lesser\s+or\s+Library|Library\s+or\s+Lesser|Lesser|Library)"

(def lgpl-re (re/join #"(?iuU)(?<!\w)"                   ; Prefix fragment
                      (re/ncg "lgpl"                     ; Define a named capture group called "lgpl"
                        (re/or-grp                       ; Outer 'or' (with elements grouped)
                          (re/join #"GNU\s+" lorl-re)    ; GNU <Lesser or library regex>
                          (re/join lorl-re #"\s+GPL")))  ; <Lesser or library regex> or GPL
                      #"(?!\w)"))                        ; Suffix fragment
;=> #"(?iuU)(?<!\w)(?<lgpl>(?:GNU\s+(?:Lesser\s+or\s+Library|Library\s+or\s+Lesser|Lesser|Library))(?:(?:Lesser\s+or\s+Library|Library\s+or\s+Lesser|Lesser|Library)\s+GPL)|(?:(?:Lesser\s+or\s+Library|Library\s+or\s+Lesser|Lesser|Library)\s+GPL)(?:GNU\s+(?:Lesser\s+or\s+Library|Library\s+or\s+Lesser|Lesser|Library))|(?:GNU\s+(?:Lesser\s+or\s+Library|Library\s+or\s+Lesser|Lesser|Library))|(?:(?:Lesser\s+or\s+Library|Library\s+or\s+Lesser|Lesser|Library)\s+GPL))(?!\w)"

; Which would you rather maintain?  😉
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
