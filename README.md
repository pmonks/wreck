<img alt="wreck logo: a generated image of a shipwreck on a beach" align="right" width="25%" src="https://raw.githubusercontent.com/pmonks/wreck/dev/wreck-logo.png">

# wreck - the "Whacky Regular Expression Construction Kit"

[![CI](https://github.com/pmonks/wreck/actions/workflows/ci.yml/badge.svg?branch=dev)](https://github.com/pmonks/wreck/actions?query=workflow%3ACI+branch%3Adev)
[![Dependencies](https://github.com/pmonks/wreck/actions/workflows/dependencies.yml/badge.svg?branch=dev)](https://github.com/pmonks/wreck/actions?query=workflow%3Adependencies+branch%3Adev)
[![Vulnerabilities](https://github.com/pmonks/wreck/actions/workflows/vulnerabilities.yml/badge.svg?branch=dev)](https://github.com/pmonks/wreck/actions?query=workflow%3Avulnerabilities+branch%3Adev)
<br/>
[![Latest Version](https://img.shields.io/clojars/v/com.github.pmonks/wreck)](https://clojars.org/com.github.pmonks/wreck/)
[![Open Issues](https://img.shields.io/github/issues/pmonks/wreck.svg)](https://github.com/pmonks/wreck/issues)
[![License](https://img.shields.io/github/license/pmonks/wreck.svg)](https://github.com/pmonks/wreck/blob/release/LICENSE) 
![Maintained](https://badges.ws/badge/?label=maintained&value=yes,+at+author's+discretion)

A micro-library for Clojure(Script) that provides a selection of regular expression (regex) functions, mostly focused on ease of composition.  It has no dependencies, other than on Clojure, and emits standard Clojure regex objects, so is fully compatible with Clojure's built-in regex functions ([`re-matches`](https://clojuredocs.org/clojure.core/re-matches), [`re-find`](https://clojuredocs.org/clojure.core/re-find), [`re-seq`](https://clojuredocs.org/clojure.core/re-seq), etc.).  It also doesn't make use of any JVM-specific or JavaScript-specific regex syntax, though it is fully compatible with platform-specific regexes, if you're using those.

The library is _not_ intended to provide a comprehensive functional alternative for constructing regexes - knowledge of regex syntax remains necessary.  Instead it is intended to assist in constructing syntactically valid large regexes by composing smaller regexes together in well-defined ways.

It also pairs very nicely with [`rencg`](https://github.com/pmonks/rencg) - that library adds first class support for named capturing groups to Clojure (albeit the JVM flavour only).

#### Why?

I have other projects that perform complex text processing and in some cases have ended up writing very large regexes (as large as ~10KB), and writing and maintaining huge regexes while keeping them syntactically and functionally correct using Clojure regex literals, is... ..."challenging".  As a result I'd written some helper functions that let me modularise those regexes, and test and construct them in pieces, and before long I realised that these functions were independently useful, despite not being complex or novel.  Hence this library.

## Installation

`wreck` is available as a Maven artifact from [Clojars](https://clojars.org/com.github.pmonks/wreck).

## Usage

[API documentation is available here](https://pmonks.github.io/wreck/wreck.api.html), or [here on cljdoc](https://cljdoc.org/d/com.github.pmonks/wreck/), and the [unit tests](https://github.com/pmonks/wreck/blob/dev/test/wreck/api_test.clj) are also worth perusing to see worked examples.  I'm also active on [the Clojure Discord server](https://discord.gg/discljord) if you'd like to chat.

> [!WARNING]  
> JavaScript's `RegExp` class fundamentally doesn't support lossless round-tripping of `RegExp` objects to `String`s and back, something this library relies upon and does extensively.  The library makes a best effort to correct JavaScript's problematic implementation, but because it's fundamentally lossy there are some cases that (on ClojureScript only) may change your regexes in unexpected (though _probably_ not semantically significant) ways.  [See the unit tests for specific examples](https://github.com/pmonks/wreck/blob/dev/test/wreck/api_test.cljc).

> [!IMPORTANT]  
> `wreck` is primarily intended to be used to construct long-lived regex objects once (e.g. at load time), and YMMV if you're constructing large regexes dynamically.  This is because it repeatedly round trips regex objects to `String`s and back during the construction process, since Clojure regex objects don't natively support concatenation.  This can generate a substantial number of shortlived objects on the heap, which can have garbage collection implications (though generational garbage collectors, such as the JVM's, tend to handle this case well).

### Regex flags

Regex flags are a thorny corner case with regexes, in that they're both highly platform specific, and (in their usual usage) don't compose properly because of their global nature (and regex composition is the entire point of `wreck`).  As a result `wreck` makes the opinionated design choice to only natively support grouped embedded flags (e.g. `(?i:[abc]+)`), as these provide scoping of the flag(s) to a well-defined portion of a regex (i.e. the group).  This avoids most of the problems of global flags, with the downside that the JVM has 2 flags that can't be embedded, and JavaScript has 5.

In general, and for the most commonly used flags (case insensitivity `(?i)`, multiline `(?m)`, and singleline / dotall `(?s)`), you should always use the [`flags-grp`](https://pmonks.github.io/wreck/wreck.api.html#var-flags-grp) function to clearly delineate where in a given regex the flag applies.  This ensures if that regex is then composed into a larger regex, the flag's effects won't "leak" into the other fragments that larger regex was composed from.

Be especially cognizant of the risks involved in using 3rd party regexes (e.g. returned from other libraries) when composing a larger regex - they may contain flags that can't easily be seen at the REPL (since by default neither the ClojureJVM nor ClojureScript REPLs emits flags when a regex value is printed).  The [`embed-flags`](https://pmonks.github.io/wreck/wreck.api.html#var-embed-flags) function can be used to automatically convert any flags in a regex to the embedded grouped equivalent (i.e. as if `flags-grp` had been called explicitly), but be aware that this will silently drop flags that cannot be embedded.  You may use the [`has-non-embeddable-flags?`](https://pmonks.github.io/wreck/wreck.api.html#var-has-non-embeddable-flags.3F) function to check for the presence of non-embeddable flags in such regexes, if that's important.

`wreck` very consciously does not provide functions for setting programmatic flags on regexes (since this is strongly discouraged), and because Clojure regex literals don't support setting them either, users will be required to resort to interop in the unlikely event that a programmatic flag is the only possible solution for a use case.  Interop logic in Clojure tends to stand out, and that may help communicate the risks of such code.

> [!WARNING]  
> [ClojureScript appears to have a JS code generation bug](https://ask.clojure.org/index.php/14717/possible-clojurescript-corner-regex-literal-compilation) in the logic that emulates support for JVM-style (`(?i)`) embedded flags (JavaScript does _not_ natively support embedded flags of this form).  This bug manifests as a JavaScript syntax error at runtime (the JavaScript code generated by ClojureScript is syntactically invalid).  Be aware if you happen to be constructing regexes using JVM-style embedded flags (and better yet, use the [`flags-grp`](https://pmonks.github.io/wreck/wreck.api.html#var-flags-grp) function instead).

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

(re/join "[" #"\p{Punct}" #"\p{Space}" "]+")  ; join also supports strings (and other data
                                              ; types), allowing syntactically invalid
                                              ; fragments to be used to build up a valid
                                              ; expression
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

(re/grp "a" "b" "c" "d" "e" "f" "g" "h" "i" "j" "k" "l" "m" "n" "o" "p" "q" "r" "s" "t" "u" "v" "w" "x" "y" "z" 0 1 2 3 4 5 6 7 8 9)
;=> #"(?:abcdefghijklmnopqrstuvwxyz0123456789)"  ; Group functions are variadic, including
                                                 ; most of the variants shown next. They also
                                                 ; (like join) support regexes, strings, and
                                                 ; other data types


;; Cardinality

(re/opt #"foo")  ; opt = optional (i.e. zero or one)
;=> #"foo?"  ; Probably not what we want, so...

(re/opt-grp #"foo")
;=> #"(?:foo)?"  ; That's more like it!

(re/zom-grp #"foo")  ; zom = zero or more
;=> #"(?:foo)*"

(re/oom-grp #"foo")  ; oom = one or more
;=> #"(?:foo)+"

(re/exn-grp 2 #"foo")  ; exn = exactly n
;=> #"(?:foo){2}"

(re/nom-grp 4 #"foo")  ; nom = n or more
;=> #"(?:foo){4,}"

(re/n2m-grp 12 17 #"foo")  ; n2m = n to m
;=> #"(?:foo){12,17}"

; There are -cg and -ncg variants of all of these fns as well, and all are variadic


;; Alternation

(re/alt #"foo" #"bar")  ; Be careful using this fn as alternation has the lowest
;=> #"foo|bar"          ; precedence in regexes

(re/alt-grp #"foo" #"bar")
;=> #"(?:foo|bar)"

; There are -cg and -ncg variants of this fn as well, and all are variadic


;; Logical operators

(re/and-grp #"foo" #"bar")
;=> #"(?:foobar|barfoo)"

(re/or-grp #"foo" #"bar")
;=> #"(?:foobar|barfoo|foo|bar)"

(re/or-grp #"foo" #"bar" #"\s+")  ; Logical operators also support separators
;=> #"(?:foo\s+bar|bar\s+foo|foo|bar)"

(re/xor-grp #"foo" #"bar")  ; The same as alt, but provided for ease of comprehension in
;=> #"(?:foo|bar)"          ; lengthy regex composition expressions that use the logical
                            ; operators


; There are -cg and -ncg variants of all of these fns as well, but note that unlike the other
; variants, none of the logical operator grouping variants are variadic


;; A more complex example that composes a longer regex from just a few easy-to-read statements
;; (from the unit tests)

; "Lesser" or "Library", but in any order, or either word by itself, with either a forward
; slash or the word "or" as a separator
(def lorl-re (re/or-grp "Lesser" "Library" (re/alt-grp #"\s*/\s*" #"\s+or\s+")))
;=> #"(?:Lesser(?:\s*/\s*|\s+or\s+)Library|Library(?:\s*/\s*|\s+or\s+)Lesser|Lesser|Library)"

(def lgpl-re (re/join
               #"(?<!\w)"                                       ; No word character before
               (re/flags-grp "i"                                ; Flags (in a group)
                 (re/alt-ncg "lgpl"                             ; Alternations, in a NCG
                   "LGPL"                                       ; LGPL literal (string)
                   (re/join "GNU" #"\s+" lorl-re #"\s+" "GPL")  ; GNU <lorl regex> GPL
                   (re/join "GNU" #"\s+" lorl-re)               ; GNU <lorl regex>
                   (re/join lorl-re #"\s+" "GPL")))             ; <lorl regex> GPL
               #"(?!\w)"))                                      ; No word character after
;=> #"(?<!\w)(?i:(?<lgpl>LGPL|GNU\s+(?:Lesser(?:\s*/\s*|\s+or\s+)Library|Library(?:\s*/\s*|
;=>   \s+or\s+)Lesser|Lesser|Library)\s+GPL|GNU\s+(?:Lesser(?:\s*/\s*|\s+or\s+)Library|Library
;=>   (?:\s*/\s*|\s+or\s+)Lesser|Lesser|Library)|(?:Lesser(?:\s*/\s*|\s+or\s+)Library|Library
;=>   (?:\s*/\s*|\s+or\s+)Lesser|Lesser|Library)\s+GPL))(?!\w)"

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
