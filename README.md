<img alt="wreck logo: a generated image of a shipwreck on a beach" align="right" width="25%" src="https://raw.githubusercontent.com/pmonks/wreck/dev/wreck-logo.png">

# wreck - the "Whacky Regular Expression Construction Kit"

[![CI](https://github.com/pmonks/wreck/actions/workflows/ci.yml/badge.svg?branch=dev)](https://github.com/pmonks/wreck/actions?query=workflow%3ACI+branch%3Adev)
[![Dependencies](https://github.com/pmonks/wreck/actions/workflows/dependencies.yml/badge.svg?branch=dev)](https://github.com/pmonks/wreck/actions?query=workflow%3Adependencies+branch%3Adev)
[![Vulnerabilities](https://github.com/pmonks/wreck/actions/workflows/vulnerabilities.yml/badge.svg?branch=dev)](https://pmonks.github.io/wreck/nvd/dependency-check-report.html)
<br/>
[![Latest Version](https://img.shields.io/clojars/v/com.github.pmonks/wreck)](https://clojars.org/com.github.pmonks/wreck/)
[![Open Issues](https://img.shields.io/github/issues/pmonks/wreck.svg)](https://github.com/pmonks/wreck/issues)
[![License](https://img.shields.io/github/license/pmonks/wreck.svg)](https://github.com/pmonks/wreck/blob/release/LICENSE) 
![Maintained](https://badges.ws/badge/?label=maintained&value=yes,+at+author's+discretion)

A micro-library for Clojure(Script) that provides a selection of regular expression (regex) functions, mostly focused on ease of composition.  It has no dependencies, other than on Clojure, and emits standard Clojure regex objects, so is fully compatible with Clojure's built-in regex functions ([`re-matches`](https://clojuredocs.org/clojure.core/re-matches), [`re-find`](https://clojuredocs.org/clojure.core/re-find), [`re-seq`](https://clojuredocs.org/clojure.core/re-seq), etc.).  It also doesn't make use of any JVM-specific or JavaScript-specific regex syntax, though it is fully compatible with platform-specific regex syntax, if you're using that.

The library is _not_ intended to provide a comprehensive functional alternative for constructing regexes - knowledge of regex syntax remains necessary.  Instead it is intended to assist in constructing syntactically valid large regexes by composing smaller regexes together in well-defined ways, which mostly involves adding support for regex concatenation, and making groups and alternation easier to use.

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

Regex flags are a thorny corner case with regexes, in that they're both highly platform specific, and (in their usual usage) don't compose properly because of their global nature (and regex composition is the entire point of `wreck`).  As a result `wreck` makes the opinionated design choice to automatically and aggressively convert all flags it finds to embedded flag groups (e.g. `#"(?i)[abc]+"`, `/[abc]+/i`, and the programmatic JVM equivalent all get turned into `#"(?i:[abc]+)"`), as this construct scopes the effect of the flag(s) and is therefore easier to reason about during regex composition.

When constructing regexes from scratch, **it is strongly recommended that you use the [`fgrp`](https://pmonks.github.io/wreck/wreck.api.html#var-fgrp) function**, and don't programmatically set flags or use other embedded flag regex syntax.  This function directly creates an embedded flag group, avoiding any guesswork about `wreck`'s automatic embedding logic, or the scope of the flag(s).

> [!IMPORTANT]  
> The JVM has 2 flags that can only be set globally (but cannot be embedded), and JavaScript has 5.  `wreck` very deliberately does not provide functionality to set global flags on regexes, because of the difficulties they create during regex composition.  If you have a use case that cannot be satisfied any way except with one of these non-embeddable flags, you can fallback on interop to set them (Clojure itself does not provide such a mechanism either).  Such flags must be applied at the very end of regex composition, after all `wreck` functions have been applied (since `wreck` functions aggressively remove such flags).

> [!WARNING]  
> [ClojureScript appears to have a JS code generation bug](https://ask.clojure.org/index.php/14717/possible-clojurescript-corner-regex-literal-compilation) in the logic that emulates support for JVM-style (`(?i)`) embedded flags (JavaScript does _not_ natively support embedded flags of this form).  This bug manifests as a JavaScript syntax error at runtime (the JavaScript code generated by ClojureScript is syntactically invalid).  Be aware if you happen to be constructing regexes using JVM-style embedded flags (and better yet, use the [`fgrp`](https://pmonks.github.io/wreck/wreck.api.html#var-fgrp) function instead).

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

(re/join #"a" #"b")  ; join is the fundamental operation provided by wreck
;=> #"ab"

(re/join "[" #"\p{Punct}" #"\p{Space}" "]+")  ; join supports strings and other data types,
                                              ; allowing syntactically invalid fragments to be
                                              ; used to build up a valid expression
;=> #"[\p{Punct}\p{Space}]+"

; Because equality isn't defined for regexes in Clojure
(re/=' #"ab" (re/join #"a" #"b"))
;=> true

; Escaping and quoting
(re/esc ".*")
;=> "\\.\\*"  ; Note: a String - most other fns return regexes

(re/qot ".*")
;=> #"\Q.*\E"


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

(re/fgrp "i" #"[abc]+")
;=> #"(?i:[abc]+)"  ; Flag groups, which apply a regex flag in a scoped manner

(re/chcl #"\p{Punct}" #"\p{Space}")
;=> #"[\p{Punct}\p{Space}]"  ; While not a "group" per se, character classes are conceptually
                             ; somewhat similar


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

; There are -cg, -ncg, -fgrp, and -chcl variants of all of these fns as well, and all are
; variadic


;; Alternation

(re/alt #"foo" #"bar")  ; Be careful using this fn as alternation has the lowest precedence in
;=> #"foo|bar"          ; regexes

(re/alt-grp #"foo" #"bar")
;=> #"(?:foo|bar)"      ; Using a group eliminates any ambiguity about how this will compose

; There are -cg, -ncg, and -fgrp variants of this fn as well, and all are variadic


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


; There are -cg, -ncg, and -fgrp variants of all of these fns as well, but note that unlike
; the other variants, none of the logical operator grouping variants are variadic


;; A more complex JVM-specific example that composes a longer regex from just a few easy-to-
;; read statements (from the unit tests)

; Whitespace - ASCII & Unicode (likly redundant, though Unicode categories can be surprising)
(def ws  (re/chcl #"\p{Space}\p{IsWhitespace}"))
;=> #"[\p{Space}\p{IsWhitespace}]"
(def ows (re/zom ws))
;=> #"[\p{Space}\p{IsWhitespace}]*"
(def mws (re/oom ws))
;=> #"[\p{Space}\p{IsWhitespace}]+"

; "Lesser" or "Library" but in any order or either word by itself, with either a forward
; slash or the word "or" as a separator between them
(def lorl-re (re/or-grp "Lesser" "Library" (re/xor-grp (re/join ows "/" ows)
                                                       (re/join mws "or" mws))))
;=> #"(?:Lesser(?:[\p{Space}\p{IsWhitespace}]*/[\p{Space}\p{IsWhitespace}]*|[\p{Space}
;=>   \p{IsWhitespace}]+or[\p{Space}\p{IsWhitespace}]+)Library|Library(?:[\p{Space}
;=>   \p{IsWhitespace}]*/[\p{Space}\p{IsWhitespace}]*|[\p{Space}\p{IsWhitespace}]+or
;=>   [\p{Space}\p{IsWhitespace}]+)Lesser|Lesser|Library)"

; A regex that matches various common formulations of the name of the GNU LGPL license
(def lgpl-re (re/join
               (re/-lb #"\w")                             ; No word character before
               (re/fgrp "i"                               ; Flags (in a group)
                 (re/alt-ncg "lgpl"                       ; Alternations, in NCG called "lgpl"
                   "LGPL"                                 ; LGPL literal
                   (re/join "GNU" mws lorl-re mws "GPL")  ; GNU <lorl regex> GPL
                   (re/join "GNU" mws lorl-re)            ; GNU <lorl regex>
                   (re/join lorl-re mws "GPL")))          ; <lorl regex> GPL
               (re/-la #"\w")))                           ; No word character after
;=> #"(?<!\w)(?i:(?<lgpl>LGPL|GNU[\p{Space}\p{IsWhitespace}]+(?:Lesser(?:[\p{Space}
;=>   \p{IsWhitespace}]*/[\p{Space}\p{IsWhitespace}]*|[\p{Space}\p{IsWhitespace}]+or[\p{Space}
;=>   \p{IsWhitespace}]+)Library|Library(?:[\p{Space}\p{IsWhitespace}]*/[\p{Space}
;=>   \p{IsWhitespace}]*|[\p{Space}\p{IsWhitespace}]+or[\p{Space}\p{IsWhitespace}]+)Lesser|
;=>   Lesser|Library)[\p{Space}\p{IsWhitespace}]+GPL|GNU[\p{Space}\p{IsWhitespace}]+(?:Lesser
;=>   (?:[\p{Space}\p{IsWhitespace}]*/[\p{Space}\p{IsWhitespace}]*|[\p{Space}
;=>   \p{IsWhitespace}]+or[\p{Space}\p{IsWhitespace}]+)Library|Library(?:[\p{Space}
;=>   \p{IsWhitespace}]*/[\p{Space}\p{IsWhitespace}]*|[\p{Space}\p{IsWhitespace}]+or[\p{Space}
;=>   \p{IsWhitespace}]+)Lesser|Lesser|Library)|(?:Lesser(?:[\p{Space}\p{IsWhitespace}]*/
;=>   [\p{Space}\p{IsWhitespace}]*|[\p{Space}\p{IsWhitespace}]+or[\p{Space}\p{IsWhitespace}]+)
;=>   Library|Library(?:[\p{Space}\p{IsWhitespace}]*/[\p{Space}\p{IsWhitespace}]*|[\p{Space}
;=>   \p{IsWhitespace}]+or[\p{Space}\p{IsWhitespace}]+)Lesser|Lesser|Library)[\p{Space}
;=>   \p{IsWhitespace}]+GPL))(?!\w)"

; Which form would you rather maintain?  😉
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
