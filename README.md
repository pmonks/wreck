<img alt="wreck logo: a generated image of a shipwreck on a beach" align="right" width="25%" src="https://raw.githubusercontent.com/pmonks/wreck/dev/wreck-logo.png">

| | | |
|---:|:---:|:---:|
| [**release**](https://github.com/pmonks/wreck/tree/release) | [![CI](https://github.com/pmonks/wreck/actions/workflows/ci.yml/badge.svg?branch=release)](https://github.com/pmonks/wreck/actions?query=workflow%3ACI+branch%3Arelease) | [![Dependencies](https://github.com/pmonks/wreck/actions/workflows/dependencies.yml/badge.svg?branch=release)](https://github.com/pmonks/wreck/actions?query=workflow%3Adependencies+branch%3Arelease) |
| [**dev**](https://github.com/pmonks/wreck/tree/dev)  | [![CI](https://github.com/pmonks/wreck/actions/workflows/ci.yml/badge.svg?branch=dev)](https://github.com/pmonks/wreck/actions?query=workflow%3ACI+branch%3Adev) | [![Dependencies](https://github.com/pmonks/wreck/actions/workflows/dependencies.yml/badge.svg?branch=dev)](https://github.com/pmonks/wreck/actions?query=workflow%3Adependencies+branch%3Adev) |

[![Latest Version](https://img.shields.io/clojars/v/com.github.pmonks/wreck)](https://clojars.org/com.github.pmonks/wreck/) [![Open Issues](https://img.shields.io/github/issues/pmonks/wreck.svg)](https://github.com/pmonks/wreck/issues) [![License](https://img.shields.io/github/license/pmonks/wreck.svg)](https://github.com/pmonks/wreck/blob/release/LICENSE) [![Vulnerabilities](https://github.com/pmonks/wreck/actions/workflows/vulnerabilities.yml/badge.svg?branch=dev)](https://github.com/pmonks/wreck/actions?query=workflow%3Avulnerabilities+branch%3Adev)


# wreck - the "Whacky Regular Expression Construction Kit"

> [!WARNING]  
> Prior to v1.0 the library will be undergoing extensive development based on its use elsewhere.  APIs and behaviour may change in backwards incompatible ways without warning.  Feedback during this period is very welcome however, either here in the form of [issues](https://github.com/pmonks/wreck/issues), or on [the Clojure Discord server](https://discord.gg/discljord).

A micro-library for Clojure(Script) that provides a selection of regular expression construction functions.  It has no dependencies, other than on Clojure, and emits standard Clojure regular expression objects, so is fully compatible with Clojure's built-in regular expression functions (it does not use any JVM-specific or JavaScript-specific regex syntax itself, though is compatible with platform-specific regular expressions, if you're using those).

The library is _not_ intended to provide a comprehensive functional alternative for constructing regular expressions - knowledge of regular expression syntax remains necessary.  Instead it is intended to assist in constructing syntactically valid large regular expressions by composing smaller regular expressions together in well-defined ways.

It also pairs very nicely with [`rencg`](https://github.com/pmonks/rencg) - that library adds first class support for named capturing groups to Clojure (albeit the JVM flavour only).

#### Why?

I have other projects that perform complex text processing and in some cases have ended up writing very large regular expressions (as large as ~10KB), and writing and maintaining huge regular expressions while keeping them syntactically and functionally correct using regular expression literals, is... ..."challenging".  As a result I'd written some helper functions that let me modularise those regular expressions, and test and construct them in pieces, and before long I realised that these functions were independently useful, despite not being complex or novel.  Hence this library.

## Installation

`wreck` is available as a Maven artifact from [Clojars](https://clojars.org/com.github.pmonks/wreck).

## Usage

[API documentation is available here](https://pmonks.github.io/wreck/), or [here on cljdoc](https://cljdoc.org/d/com.github.pmonks/wreck/), and the [unit tests](https://github.com/pmonks/wreck/blob/dev/test/wreck/api_test.clj) are also worth perusing to see worked examples.

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

(re/join "[" #"\p{Punct}" #"\p{Space}" "]+")  ; join also supports strings, allowing
                                              ; syntactically invalid fragments to be used to
                                              ; build up a valid expression
;=> #"[\p{Punct}\p{Space}]+"

; Because equality isn't defined for regexes in Clojure
(re/=' #"ab" (re/join #"a" #"b"))
;=> true


;; Groups

(re/grp #"a" #"b")
;=> #"(?:ab)"  ; Default group is non-capturing

(re/cg #"a" #"b")
;=> #"(ab)"  ; But we can also do capturing groups

(re/ncg "ab" #"a" #"b")
;=> #"(?<ab>ab)"  ; And named capturing groups (much more useful, especially with rencg!)

(re/grp "a" "b" "c" "d" "e" "f" "g" "h" "i" "j" "k" "l" "m" "n" "o" "p" "q" "r" "s" "t" "u" "v" "w" "x" "y" "z")
;=> #"(?:abcdefghijklmnopqrstuvwxyz)"  ; Group functions are variadic, including most of the
                                       ; variants shown next. They also (like join) support
                                       ; both regexes and strings.


;; Cardinality

(re/zom #"foo")  ; zom = zero or more
;=> #"foo*"  ; Probably not what we want, so...

(re/zom-grp #"foo")
;=> #"(?:foo)*"  ; That's more like it!

(re/oom-grp #"foo")  ; oom = one or more
;=> #"(?:foo)+"

(re/exn-grp 2 #"foo")  ; exn = exactly n
;=> #"(?:foo){2}"

(re/nom-grp 4 #"foo")  ; nom = n or more
;=> #"(?:foo){4,}"

(re/n2m-grp 12 17 #"foo")  ; n2m = n to m
;=> #"(?:foo){12,17}"

; There are -cg and -ncg variants of all of these fns as well, all variadic


;; Alternation

(re/alt #"foo" #"bar")
;=> #"foo|bar"

(re/alt-grp #"foo" #"bar")
;=> #"(?:foo|bar)"

; There are -cg and -ncg variants of this fn as well, all variadic


;; Logical operators

(re/and' #"foo" #"bar")
;=> #"foobar|barfoo"

(re/and-grp #"foo" #"bar")
;=> #"(?:foobar|barfoo)"

(re/or' #"foo" #"bar")
;=> #"foobar|barfoo|foo|bar"

(re/or-grp #"foo" #"bar")
;=> #"(?:foobar|barfoo|foo|bar)"

(re/or-grp #"foo" #"bar" #"\s+")  ; Logical operators also support separators
;=> #"(?:foo\s+bar|bar\s+foo|foo|bar)"

(re/xor' #"foo" #"bar")  ; The same as alt, but provided for ease of comprehension in lengthy
                         ; regex composition expressions that use the logical operators
;=> #"foo|bar"

(re/xor-grp #"foo" #"bar")
;=> #"(?:foo|bar)"

; There are -cg and -ncg variants of all of these fns as well, but note that unlike the other
; variants, none of the logical operator grouping variants are variadic


;; A more complex example that composes a longer regex from just a few easy-to-read statements
;; (from the unit tests)

(def lorl-re (re/or-grp "Lesser" "Library" #"\s+or\s+"))  ; "Lesser" or "Library", but in any
                                                          ; order, or either word by itself,
                                                          ; with the word "or" as a separator
;=> #"(?:Lesser\s+or\s+Library|Library\s+or\s+Lesser|Lesser|Library)"

(def lgpl-re (re/join #"(?iuU)(?<!\w)"                                ; Prefix fragment
                      (re/alt-ncg "lgpl"                              ; Alternations, ncg'ed
                        "LGPL"                                        ; LGPL literal (string)
                        (re/join "GNU" #"\s+" lorl-re)                ; GNU <lorl regex>
                        (re/join lorl-re #"\s+" "GPL")                ; <lorl regex> GPL
                        (re/join "GNU" #"\s+" lorl-re #"\s+" "GPL"))  ; GNU <lorl> GPL
                      #"(?!\w)"))                                     ; Suffix fragment
;=> #"(?iuU)(?<!\w)(?<lgpl>GNU\s+(?:Lesser\s+or\s+Library|Library\s+or\s+Lesser|Lesser|
;=> Library)|(?:Lesser\s+or\s+Library|Library\s+or\s+Lesser|Lesser|Library)\s+GPL|GNU\s+
;=> (?:Lesser\s+or\s+Library|Library\s+or\s+Lesser|Lesser|Library)\s+GPL)(?!\w)"

; Which would you rather maintain?  😉
```

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
