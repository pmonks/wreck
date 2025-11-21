;
; Copyright © 2025 Peter Monks
;
; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.
;
; SPDX-License-Identifier: MPL-2.0
;

(ns wreck.api
  "The public API of [`wreck`](https://github.com/pmonks/wreck).

  Notes:

  * This library does minimal argument checking, since the rules for regexes
    vary from platform to platform, and it is a first class requirement that
    callers be allowed to construct platform specific regexes if they wish.
  * As a result, all functions have the potential to throw platform-specific
    exceptions if the resulting regex is syntactically invalid. On the JVM,
    these will typically be instances of the `java.util.regex.PatternSyntaxException`
    class. On JavaScript, these will typically be a `js/SyntaxError`.
  * Platform specific behaviour is particularly notable for short / empty
    regexes, such as `#\"{}\"` (an error on the JVM, fine but
    nonsensical on JS) and `#\"{1}\"` (ironically fine but nonsensical on the
    JVM, but an error on JS).  🤡
  * Furthemore, JavaScript fundamentally doesn't support lossless round-tripping
    of `RegExp` objects to `String`s and back, something this library does
    extensively.  The library makes a best effort to correct JavaScript's
    problematic implementation, but because it's fundamentally lossy there are
    some cases that (on ClojureScript only) may change your regexes in
    unexpected (though _probably_ not semantically significant) ways.
  * Regex flags are supported to the best ability of the library, but please
    carefully review the [usage notes in README.md](https://github.com/pmonks/wreck?tab=readme-ov-file#regex-flags)
    for various caveats when flags are used.
  * None of these functions perform `String` escaping or quoting automatically.
    You can use [[esc]] or [[qot]] for this."
  (:require [clojure.string :as s]
            [wreck.impl     :as wi]
   #?(:cljs [clojure.set    :as set])
   #?(:cljs [goog.object])))

(def ^:private empty-regex #"")

;; FLAGS HANDLING

; Note: the set of embeddable flag characters on JavaScript was determined
; empirically, by running this code on node.js v25 with each flag character one
; at a time:
;
;   new RegExp().compile("(?<flagChar>:.*)");
;
; While (at the time of writing) the full set of official JavaScript regex flag
; characters is `digmsuvy`, only `ims` were found to be embeddable.  `u` and `v`
; were also separately found to be mutually exclusive (they cannot be used
; together).
;
; Note also that ClojureScript complicates matters by (attempting) to add
; support for JVM-style ungrouped embedded flags (e.g. `(?i).*`).  This is NOT
; natively supported by JavaScript however, and it appears that the
; ClojureScript logic that handles the conversion sometimes emits syntactically
; invalid JavaScript code - see https://ask.clojure.org/index.php/14717/possible-clojurescript-corner-regex-literal-compilation

#?(:clj
(def ^:private non-embeddable-flags (seq (filter identity (map (fn [[k v]] (when (nil? v) k)) wi/flag->embedded-char))))
:cljs
(def ^:private embeddable-flags #{\i \m \s}))

(defn has-non-embeddable-flags?
  "Does `re` have non-embeddable flags?

  Notes:

  * On the JVM, the only non-embeddable flags are the programmatic flags
    `LITERAL` and `CANON_EQ`.
  * On JavaScript, this is every flag _except_ `i`, `m`, and `s`."
  [re]
  (if-let [^String flgs (wi/raw-flags re)]
#?(:clj
    (boolean (seq (filter (complement zero?) (map #(bit-and flgs %) non-embeddable-flags))))
  :cljs
    (let [flag-set (-> flgs
                       seq
                       set)]
      (boolean (seq (set/difference flag-set embeddable-flags)))))
    false))  ; re did not contain any flags

(defn embed-flags
  "Embeds any programmatic or ungrouped flags found in `re`. It does this by
  removing all flags from `re` then wrapping it in a flag group containing those
  flags that are embeddable (non-embeddable flags are silently dropped - use
  [[has-non-embeddable-flags?]] if you need to check for this).  Returns `re` if
  `re` contains no flags.

  For example on the JVM, both `(Pattern/compile \"[abc]+\" Pattern/CASE_INSENSITIVE)`
  and `#\"(?i)[abc]+\"` would become `#\"(?i:[abc]+)\"`.

  Similarly, on ClojureScript `(doto (js/RegExp.) (.compile \"[abc]+\" \"i\"))`
  would become `#\"(?i:[abc]+)\"`.

  Note:

  * **[[fgrp]] is almost always a better choice than this function!**
    `embed-flags` is primarily intended for internal use by `wreck`, but may be
    useful in those rare cases where Clojure(Script) code receives a 3rd party
    regex, wishes to use it as part of composing a larger regex, doesn't
    know if it contains flags or not, and doesn't care that non-embeddable flags
    will be silently dropped.
  * ⚠️ On the JVM, ungrouped embedded flags in the middle of `re` will be moved
    to the beginning of the regex.  This may alter the semantics of the regex -
    for example `a(?i)b` will become `(?i:ab)`, which means that `a` will be
    matched case-insensitively by the result, which is _not_ the same as the
    original (which matches lower-case `a` only).  This is an unavoidable
    consequence of how the JVM regex engine reports flags.  If you really need
    to use embedded flag(s) midway through a regex, use [[fgrp]] to ensure
    proper scoping of the flag(s).
  * ⚠️ On the JVM, the programmatic flags `LITERAL` and `CANON_EQ` have no
    embeddable equivalent, and will be silently dropped by this function.
  * ⚠️ On JavaScript, only the flags `i`, `m`, and `s` can be embedded.  All
    other flags will be silently dropped by this function."
  [re]
  (if-let [rf (wi/raw-flags re)]  ; Check raw flags, in case there are non-embeddable flags on the JVM that need to be stripped (wi/flags ignores non-embeddable flags)
    (let [f #?(:clj  (wi/flags re)
               :cljs (s/join (set/intersection embeddable-flags (set (seq rf)))))]
      (wi/set-flags re f))
    (if (nil? re)
      empty-regex
      re)))


;; FUNDAMENTAL PRIMITIVES

(defn regex?
  "Is `x` a regex?

  Notes:

  * ClojureScript already has a `regexp?` predicate in `cljs.core`, but
    ClojureJVM doesn't.  See [this ask.clojure.org post](https://ask.clojure.org/index.php/1127/add-clojure-core-pattern-predicate)."
  [x]
  (wi/regex? x))  ; Ideally should eliminate this redundant call

(defn str'
  "Returns the `String` representation of `x`, with special handling for
  `RegExp` objects on ClojureScript in an attempt to correct JavaScript's
  **APPALLING** default stringification.

  Notes:

  * Embeds flags (as per [[embed-flags]])."
  [x]
  (when x
    (-> x
        embed-flags
        wi/raw-str)))

(defn ='
  "Equality for regexes, defined by having equal string representations and
  flags (including flags that cannot be embedded).

  Notes:

  * Functionally equivalent regexes (e.g. `#\"...\"` and `#\".{3}\"` are _not_
    considered `='`.
  * Some regexes may not be `='` initially due to differing flag sets, but after
    being run through [[embed-flags]] may become `='`, due to non-embeddable
    flags being silently dropped (see [[embed-flags]] for details)."
  ([_] true)
  ([re1 re2]
   (and (= (wi/raw-flags re1) (wi/raw-flags  re2))  ; Check flags first, because that's fast
        (= (wi/raw-str   re1) (wi/raw-str    re2))))
  ([re1 re2 & more]
   (if (=' re1 re2)  ; Naive recursion to 2-arg version of =' (which doesn't recurse further)
     (if (next more)
       (recur re2 (first more) (rest more))
       (=' re2 (first more)))  ; Naive recursion to 2-arg version of =' (which doesn't recurse further)
     false)))

(defn- distinct'
  "Similar to `clojure.core/distinct`, but non-lazy, and uses the [[str']]
  representation of each element when comparing for equality (so `0`, `\"0\"`,
  and `#\"0\"` would all be considered identical, for example).

  Notes:

  * Unlike `clojure.core/distinct`, returns `nil` if the result is empty.
  * Flags in each re are embedded, as per [[embed-flags]], before checking for
    equality, and the embedded version will be returned."
  [res]
  (when res
    (loop [[f & r] res
           result  []
           seen    #{}]
      (if-not f
        (seq result)
        (let [f-str      (str' f)
              new-f      (if (regex? f) (re-pattern f-str) f)
              new-result (if (contains? seen f-str) result (conj result new-f))]
          (recur r new-result (conj seen f-str)))))))

(defn empty?'
  "Is `re` `nil` or `(=' #\"\")`?

  Notes:

  * Takes flags (if any) into account."
  [re]
  (or (nil? re)
      (=' empty-regex re)))

(defn join
  "Returns a regex that is all of the `res` joined together. Each element in
  `res` can be a regex, a `String` or something that can be turned into a
  `String` (including numbers, etc.).  Returns an empty regex (`#\"\"`) if no
  `res` are provided, or they're all [[empty?']].

  Notes:

  * ⚠️ In ClojureScript be cautious about using numbers in these calls, since
    JavaScript's number handling is a 🤡show.  See the unit tests for examples."
  [& res]
  (if-let [res (seq (filter identity res))]
    (re-pattern (s/join (map str' res)))
    empty-regex))

(defn esc
  "Escapes `s` (a `String`) for use in a regex, returning a `String`.  Returns
  `nil` if `s` is `nil`.

  Notes:

  * unlike most other fns in this namespace, this one does _not_ support a regex
    as an input, nor return a regex as an output"
  [^String s]
  (when s
    (s/escape s {\< "\\<"
                 \( "\\("
                 \[ "\\["
                 \{ "\\{"
                 \\ "\\\\"
                 \^ "\\^"
                 \- "\\-"
                 \= "\\="
                 \$ "\\$"
                 \! "\\!"
                 \| "\\|"
                 \] "\\]"
                 \} "\\}"
                 \) "\\)"
                 \? "\\?"
                 \* "\\*"
                 \+ "\\+"
                 \. "\\."
                 \> "\\>"})))

(defn qot
  "Quotes `re`:

  * `\\Qre\\E`

  Returns an empty regex (`#\"\"`) if `re` is [[empty?']]."
  [re]
  (if (empty?' re)
    empty-regex
    (join "\\Q" re "\\E")))


;; CHARACTER CLASSES

(defn chcl
  "As for [[join]], but encloses the joined `res` into a character class:

  * `[res]`

  Returns an empty regex (`#\"\"`) if no `res` are provided, or they're all
  [[empty?']].

  Notes:

  * ⚠️ On ClojureScript nested character classes don't work as one might expect,
    even though they will compile just fine.  For example, this code matches as
    expected on ClojureJVM, but does not on ClojureScript (despite the regex
    compiling): `(re-matches #\"[[a-m][o-z]]+\" \"az\")`."
  [& res]
  (let [exp (apply join res)]
    (if (empty?' exp)
      empty-regex
      (join "[" exp "]"))))


;; GROUPS

(defn grp
  "As for [[join]], but encloses the joined `res` into a single non-capturing
  group:

  * `(?:res)`

  Returns an empty regex (`#\"\"`) if no `res` are provided, or they're all
  [[empty?']]."
  [& res]
  ; Here we optimise out an empty non-capturing group, which does NOT break code that indexes into capturing groups (since non-capturing groups never have an index!)
  (let [exp (apply join res)]
    (if (empty?' exp)
      empty-regex
      (join "(?:" exp ")"))))

(defn cg
  "As for [[grp]], but emits a capturing group:

  * `(res)`

  Returns an empty capturing group (`#\"()\"`) if no `res` are provided, or
  they're all [[empty?']]. It does this to ensure that capturing groups are
  preserved during composition, even if they're empty (since not doing so will
  break code that uses indexes to access matched group content)."
  [& res]
  ; Note: don't optimise empty capturing groups, because that will break code that indexes into capturing groups
  (join "(" (apply join res) ")"))

(defn ncg
  "As for [[grp]], but emits a named capturing group named `nm`:

  * `(?<nm>res)`

  Devolves to [[cg]] if `nm` is blank. Throws if `nm` is an invalid name for a
  named capturing group (alphanumeric only, must start with an alphabetical
  character, must be unique within the regex)."
  [^String nm & res]
  ; Note: don't optimise empty named capturing groups, because that will break code that indexes into capturing groups
  (if (s/blank? nm)
    (apply cg res)
    (join "(?<" nm ">" (apply join res) ")")))

(defn fgrp
  "As for [[grp]], but emits an embedded flag group with `flgs` (a `String`):

  * `(?flgs:res)`

  Devolves to [[grp]] if `flgs` is blank.  Throws if `flgs` contains an invalid
  flag character, including those that (ClojureScript only) cannot be embedded.

  Notes:

  * If you must use regex flags, **it is STRONGLY RECOMMENDED that you use this
    function!**  Programmatically set flags and ungrouped embedded flags (e.g.
    `(?i)`) have no explicit scope and so cannot be reliably used to compose
    larger regexes.  `wreck` makes a best effort to always convert such
    'unscoped' flags into their embedded (scoped) equivalents (using
    [[embed-flags]]) when composing larger regexes , but using `fgrp` voids
    potential footguns.
  * Removes any ungrouped embedded flags in `re` (e.g. `(?i)ab`), but does _not_
    add them to `flgs` if they aren't already there.
  * ⚠️ On the JVM, ungrouped embedded flags _in the middle of `re`_ (e.g.
    `a(?i)b`) will also be removed, which may alter the semantics of the regex.
  * ⚠️ On JavaScript, only the flags `i`, `m` and `s` can be embedded (this is a
    limitation of the JavaScript regex engine).  Other flags will result in a
    `js/SyntaxError` being thrown.
  * For the JVM, see the ['special constructs' section of the
    `java.util.regex.Pattern` JavaDoc](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/regex/Pattern.html#special)
    for the set of valid flag characters.
  * For JavaScript, see the [`RegExp` flags reference](https://www.w3schools.com/js/js_regexp_flags.asp)
    for the set of valid flag characters (while keeping in mind most of them
    can't be embedded)."
  [^String flgs & res]
  (let [exp (apply join res)]
    (if (empty?' exp)
      empty-regex
      (if (s/blank? flgs)
        (apply grp res)
        (wi/set-flags exp flgs)))))

(def ^:deprecated flags-grp
  "See [[fgrp]]"
  fgrp)


;; OPTIONAL

(defn opt
  "Returns a regex where `re` is optional:

  * `re?`

  Returns an empty regex (`#\"\"`) if `re` is [[empty?']]."
  [re]
  (if (empty?' re)
    empty-regex
    (join re "?")))

(defn opt-grp
  "[[grp]] then [[opt]]:

  * `(?:res)?`"
  [& res]
  (opt (apply grp res)))

(defn opt-cg
  "[[cg]] then [[opt]]:

  * `(res)?`"
  [& res]
  (opt (apply cg res)))

(defn opt-ncg
  "[[ncg]] then [[opt]]:

  * `(?<nm>res)?`"
  [^String nm & res]
  (opt (apply (partial ncg nm) res)))

(defn opt-fgrp
  "[[fgrp]] then [[opt]]:

  * `(?flgs:res)?`"
  [^String flgs & res]
  (opt (apply (partial fgrp flgs) res)))

(defn opt-chcl
  "[[chcl]] then [[opt]]:

  * `[res]?`"
  [& res]
  (opt (apply chcl res)))


;; ZERO OR MORE

(defn zom
  "Returns a regex where `re` will match zero or more times:

  * `re*`

  Returns an empty regex (`#\"\"`) if `re` is [[empty?']]."
  [re]
  (if (empty?' re)
    empty-regex
    (join re "*")))

(defn zom-grp
  "[[grp]] then [[zom]]:

  * `(?:res)*`"
  [& res]
  (zom (apply grp res)))

(defn zom-cg
  "[[cg]] then [[zom]]:

  * `(res)*`"
  [& res]
  (zom (apply cg res)))

(defn zom-ncg
  "[[ncg]] then [[zom]]:

  * `(?<nm>res)*`"
  [^String nm & res]
  (zom (apply (partial ncg nm) res)))

(defn zom-fgrp
  "[[fgrp]] then [[zom]]:

  * `(?flgs:res)*`"
  [^String flgs & res]
  (zom (apply (partial fgrp flgs) res)))

(defn zom-chcl
  "[[chcl]] then [[zom]]:

  * `[res]*`"
  [& res]
  (zom (apply chcl res)))


;; ONE OR MORE

(defn oom
  "Returns a regex where `re` will match one or more times:

  * `re+`

  Returns an empty regex (`#\"\"`) if `re` is [[empty?']]."
  [re]
  (if (empty?' re)
    empty-regex
    (join re "+")))

(defn oom-grp
  "[[grp]] then [[oom]]:

  * `(?:res)+`"
  [& res]
  (oom (apply grp res)))

(defn oom-cg
  "[[cg]] then [[oom]]:

  * `(res)+`"
  [& res]
  (oom (apply cg res)))

(defn oom-ncg
  "[[ncg]] then [[oom]]:

  * `(?<nm>res)+`"
  [^String nm & res]
  (oom (apply (partial ncg nm) res)))

(defn oom-fgrp
  "[[fgrp]] then [[oom]]:

  * `(?flgs:res)+`"
  [^String flgs & res]
  (oom (apply (partial fgrp flgs) res)))

(defn oom-chcl
  "[[chcl]] then [[oom]]:

  * `[res]+`"
  [& res]
  (oom (apply chcl res)))


;; N OR MORE

(defn nom
  "Returns a regex where `re` will match `n` or more times:

  * `re{n,}`

  Returns an empty regex (`#\"\"`) if `re` is [[empty?']]."
  [^Long n re]
  (if (empty?' re)
    empty-regex
    (join re "{" n ",}")))

(defn nom-grp
  "[[grp]] then [[nom]]:

  * `(?:res){n,}`"
  [^Long n & res]
  (nom n (apply grp res)))

(defn nom-cg
  "[[cg]] then [[nom]]:

  * `(res){n,}`"
  [^Long n & res]
  (nom n (apply cg res)))

(defn nom-ncg
  "[[ncg]] then [[nom]]:

  * `(?<nm>res){n,}`"
  [^String nm ^Long n & res]
  (nom n (apply (partial ncg nm) res)))

(defn nom-fgrp
  "[[fgrp]] then [[nom]]:

  * `(?flgs:res){n,}`"
  [^String flgs ^Long n & res]
  (nom n (apply (partial fgrp flgs) res)))

(defn nom-chcl
  "[[chcl]] then [[nom]]:

  * `[res]{n,}`"
  [^Long n & res]
  (nom n (apply chcl res)))


;; EXACTLY N

(defn exn
  "Returns a regex where `re` will match exactly `n` times:

  * `re{n}`

  Returns an empty regex (`#\"\"`) if `re` is [[empty?']]."
  [^Long n re]
  (if (empty?' re)
    empty-regex
    (join re "{" n "}")))

(defn exn-grp
  "[[grp]] then [[exn]]:

  * `(?:res){n}`"
  [^Long n & res]
  (exn n (apply grp res)))

(defn exn-cg
  "[[cg]] then [[exn]]:

  * `(res){n}`"
  [^Long n & res]
  (exn n (apply cg res)))

(defn exn-ncg
  "[[ncg]] then [[exn]]:

  * `(?<nm>res){n}`"
  [^String nm ^Long n & res]
  (exn n (apply (partial ncg nm) res)))

(defn exn-fgrp
  "[[fgrp]] then [[exn]]:

  * `(?flgs:res){n}`"
  [^String flgs ^Long n & res]
  (exn n (apply (partial fgrp flgs) res)))

(defn exn-chcl
  "[[chcl]] then [[exn]]:

  * `[res]{n}`"
  [^Long n & res]
  (exn n (apply chcl res)))


;; N TO M

(defn n2m
  "Returns a regex where `re` will match from `n` to `m` times:

  * `re{n,m}`

  Returns an empty regex (`#\"\"`) if `re` is [[empty?']]."
  [^Long n ^Long m re]
  (if (empty?' re)
    empty-regex
    (join re "{" n "," m "}")))

(defn n2m-grp
  "[[grp]] then [[n2m]]:

  * `(?:res){n,m}`"
  [^Long n ^Long m & res]
  (n2m n m (apply grp res)))

(defn n2m-cg
  "[[cg]] then [[n2m]]:

  * `(res){n,m}`"
  [^Long n ^Long m & res]
  (n2m n m (apply cg res)))

(defn n2m-ncg
  "[[ncg]] then [[n2m]]:

  * `(?<nm>res){n,m}`"
  [^String nm ^Long n ^Long m & res]
  (n2m n m (apply (partial ncg nm) res)))

(defn n2m-fgrp
  "[[fgrp]] then [[n2m]]:

  * `(?flgs:res){n,m}`"
  [^String flgs ^Long n ^Long m & res]
  (n2m n m (apply (partial fgrp flgs) res)))

(defn n2m-chcl
  "[[chcl]] then [[n2m]]:

  * `[res]{n,m}`"
  [^Long n ^Long m & res]
  (n2m n m (apply chcl res)))


;; ALTERNATION

(defn alt
  "Returns a regex that will match any one of `res`, via alternation:

  * `re|re|re|...`

  Returns an empty regex (`#\"\"`) if no `res` are provided, or they're all
  [[empty?']].

  Notes:

  * Duplicate elements in `res` will only appear once in the result. This
    equality comparison occurs _after_ each re is run through [[embed-flags]].
  * Does _not_ wrap the result in a group, which, [because alternation has the
    lowest precedence in regexes](https://pubs.opengroup.org/onlinepubs/9699919799/basedefs/V1_chap09.html#tag_09_04_08),
    runs the risk of behaving unexpectedly if the result is then combined with
    further regexes.
    tl;dr - one of the grouping variants should _almost always_ be preferred."
  [& res]
  (if-let [res (distinct' (filter identity res))]
    (apply join (interpose "|" res))
    empty-regex))

(defn alt-grp
  "[[alt]] then [[grp]]:

  * `(?:re|re|re|...)`"
  [& res]
  (grp (apply alt res)))

(defn alt-cg
  "[[alt]] then [[cg]]:

  * `(re|re|re|...)`"
  [& res]
  (cg (apply alt res)))

(defn alt-ncg
  "[[alt]] then [[ncg]]:

  * `(?<nm>re|re|re|...)`"
  [^String nm & res]
  (ncg nm (apply alt res)))

(defn alt-fgrp
  "[[alt]] then [[fgrp]]:

  * `(?flgs:re|re|re|...)`"
  [^String flgs & res]
  (fgrp flgs (apply alt res)))

; Note: no -chcl variant for alt, since that doesn't make sense


;; LOGICAL OPERATORS

(defn and'
  "Returns an 'and' regex that will match `a` and `b` in any order, and with the
  separator regex `s` (if provided) between them:

  * `asb|bsa`

  Returns an empty regex (`#\"\"`) if `a` and `b` are [[empty?']].

  Notes:

  * `a` and `b` must be distinct (must not match the same text) or else the
    resulting regex will be logically inconsistent (will not be an 'and')
  * May optimise the expression (via de-duplication in [[alt]]).
  * Does _not_ wrap the result in a group, which, [because alternation has the
    lowest precedence in regexes](https://pubs.opengroup.org/onlinepubs/9699919799/basedefs/V1_chap09.html#tag_09_04_08),
    runs the risk of behaving unexpectedly if the result is then combined with
    further regexes.
    tl;dr - one of the grouping variants should _almost always_ be preferred."
  ([a b] (and' a b nil))
  ([a b s]
   (if (and (empty?' a) (empty?' b))
     empty-regex
     (alt (join a s b) (join b s a)))))

(defn and-grp
  "[[and']] then [[grp]]:

  * `(?:asb|bsa)`

  Notes:

  * Unlike most other `-grp` fns, this one does _not_ accept any number of res.
  * May optimise the expression (via de-duplication in [[alt]])."
  ([a b] (and-grp a b nil))
  ([a b s]
   (grp (and' a b s))))

(defn and-cg
  "[[and']] then [[cg]]:

  * `(asb|bsa)`

  Notes:

  * Unlike most other `-cg` fns, this one does _not_ accept any number of res.
  * May optimise the expression (via de-duplication in [[alt]])."
  ([a b] (and-cg a b nil))
  ([a b s]
   (cg (and' a b s))))

(defn and-ncg
  "[[and']] then [[ncg]]:

  * `(?<nm>asb|bsa)`

  Notes:

  * Unlike most other `-ncg` fns, this one does _not_ accept any number of res.
  * May optimise the expression (via de-duplication in [[alt]])."
  ([^String nm a b] (and-ncg nm a b nil))
  ([^String nm a b s]
   (ncg nm (and' a b s))))

(defn and-fgrp
  "[[and']] then [[fgrp]]:

  * `(?flgs:asb|bsa)`

  Notes:

  * Unlike most other `-fgrp` fns, this one does _not_ accept any number of res.
  * May optimise the expression (via de-duplication in [[alt]])."
  ([^String flgs a b] (and-fgrp flgs a b nil))
  ([^String flgs a b s]
   (fgrp flgs (and' a b s))))

; Note: no -chcl variant for and, since that doesn't make sense

(defn or'
  "Returns an 'inclusive or' regex that will match `a` or `b`, or both, in any
  order, and with the separator regex `s` (if provided) between them:

  * `asb|bsa|a|b`

  Returns an empty regex (`#\"\"`) if `a` and `b` are [[empty?']].

  Notes:

  * `a` and `b` must be distinct (must not match the same text) or else the
    resulting regex will be logically inconsistent (will not be an 'or')
  * May optimise the expression (via de-duplication in [[alt]]).
  * Does _not_ wrap the result in a group, which, [because alternation has the
    lowest precedence in regexes](https://pubs.opengroup.org/onlinepubs/9699919799/basedefs/V1_chap09.html#tag_09_04_08),
    runs the risk of behaving unexpectedly if the result is then combined with
    further regexes.
    tl;dr - one of the grouping variants should _almost always_ be preferred."
  ([a b] (or' a b nil))
  ([a b s]
   (if (and (empty?' a) (empty?' b))
     empty-regex
     (alt (join a s b) (join b s a) a b))))

(defn or-grp
  "[[or']] then [[grp]]:

  * `(?:asb|bsa|a|b)`

  Notes:

  * Unlike most other `-grp` fns, this one does _not_ accept any number of res.
  * May optimise the expression (via de-duplication in [[alt]])."
  ([a b] (or-grp a b nil))
  ([a b s]
   (grp (or' a b s))))

(defn or-cg
  "[[or']] then [[cg]]:

  * `(asb|bsa|a|b)`

  Notes:

  * Unlike most other `-cg` fns, this one does _not_ accept any number of res.
  * May optimise the expression (via de-duplication in [[alt]])."
  ([a b] (or-cg a b nil))
  ([a b s]
   (cg (or' a b s))))

(defn or-ncg
  "[[or']] then [[ncg]]:

  * `(?<nm>asb|bsa|a|b)`

  Notes:

  * Unlike most other `-ncg` fns, this one does _not_ accept any number of res.
  * May optimise the expression (via de-duplication in [[alt]])."
  ([^String nm a b] (or-ncg nm a b nil))
  ([^String nm a b s]
   (ncg nm (or' a b s))))

(defn or-fgrp
  "[[or']] then [[fgrp]]:

  * `(?flgs:asb|bsa|a|b)`

  Notes:

  * Unlike most other `-flgs` fns, this one does _not_ accept any number of res.
  * May optimise the expression (via de-duplication in [[alt]])."
  ([^String flgs a b] (or-fgrp flgs a b nil))
  ([^String flgs a b s]
   (fgrp flgs (or' a b s))))

; Note: no -chcl variant for or, since that doesn't make sense

(defn xor'
  "Returns an 'exclusive or' regex that will match `a` or `b`, but _not_ both:

  * `a|b`

  Returns an empty regex (`#\"\"`) if `a` and `b` are [[empty?']].

  This is identical to [[alt]] called with 2 arguments, but is provided as a
  convenience for those who might be building up large logic based regexes and
  would prefer to use more easily understood logical operator names throughout.

  Notes:

  * May optimise the expression (via de-duplication in [[alt]]).
  * Does _not_ wrap the result in a group, which, [because alternation has the
    lowest precedence in regexes](https://pubs.opengroup.org/onlinepubs/9699919799/basedefs/V1_chap09.html#tag_09_04_08),
    runs the risk of behaving unexpectedly if the result is then combined with
    further regexes.
    tl;dr - one of the grouping variants should _almost always_ be preferred."
  [a b]
  (alt a b))

(defn xor-grp
  "[[xor']] then [[grp]]:

  * `(?:a|b)`

  Notes:

  * Unlike most other `-grp` fns, this one does _not_ accept any number of res.
  * May optimise the expression (via de-duplication in [[alt]])."
  [a b]
  (grp (xor' a b)))

(defn xor-cg
  "[[xor']] then [[cg]]:

  * `(a|b)`

  Notes:

  * Unlike most other `-cg` fns, this one does _not_ accept any number of res.
  * May optimise the expression (via de-duplication in [[alt]])."
  [a b]
  (cg (xor' a b)))

(defn xor-ncg
  "[[xor']] then [[ncg]]:

  * `(?<nm>a|b)`

  Notes:

  * Unlike most other `-ncg` fns, this one does _not_ accept any number of res.
  * May optimise the expression (via de-duplication in [[alt]])."
  [^String nm a b]
  (ncg nm (xor' a b)))

(defn xor-fgrp
  "[[xor']] then [[fgrp]]:

  * `(?flgs:a|b)`

  Notes:

  * Unlike most other `-fgrp` fns, this one does _not_ accept any number of res.
  * May optimise the expression (via de-duplication in [[alt]])."
  [^String flgs a b]
  (fgrp flgs (xor' a b)))

; Note: no -chcl variant for xor, since that doesn't make sense
