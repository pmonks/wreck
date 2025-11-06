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

  * Apart from passing through `nil`, this library does minimal argument
    checking, since the rules for regexes vary from platform to platform, and it
    is a first class requirement that callers be allowed to construct platform
    specific regexes if they wish.
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
  (if-let [flgs (wi/raw-flags re)]
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

  * **[[flags-grp]] is almost always a better choice than this function!**
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
    to use embedded flag(s) midway through a regex, use [[flags-grp]] to ensure
    proper scoping of the flag(s).
  * ⚠️ On the JVM, the programmatic flags `LITERAL` and `CANON_EQ` have no
    embeddable equivalent, and will be silently dropped by this function.
  * ⚠️ On JavaScript, only the flags `i`, `m`, and `s` can be embedded.  All
    other flags will be silently dropped by this function."
  [re]
  (if-let [rf (wi/raw-flags re)]  ; Check raw flags, in case we have to strip some
    (let [f #?(:clj  (wi/flags re)
               :cljs (s/join (set/intersection embeddable-flags (set (seq rf)))))]
      (wi/set-flags re f))
    re))


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
    equality, though the result contains the original re and may ."
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
      (=' #"" re)))

(defn join
  "Returns a regex that is all of the `res` joined together. Each element in
  `res` can be a regex, a `String` or something that can be turned into a
  `String` (including numbers, etc.).  Ignores `nil` values in `res`, and
  returns `nil` when no `res` are provided or they're all `nil`.

  Notes:

  * ⚠️ In ClojureScript be cautious about using numbers in these calls, since
    JavaScript's number handling is a 🤡show.  See the unit tests for examples."
  [& res]
  (when-let [res (seq (filter identity res))]
    (re-pattern (s/join (map str' res)))))

(defn esc
  "Escapes `s` (a `String`) for use in a regex, returning a `String`.

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

  * `\\Qre\\E`"
  [re]
  (when re
    (join "\\Q" re "\\E")))


;; CHARACTER CLASSES

(defn chcl
  "As for [[join]], but encloses the joined `res` into a character class:

  * `[res]`

  Notes:

  * ⚠️ On ClojureScript nested character classes don't work as one might expect,
    even though they will compile just fine.  For example, this code matches as
    expected on ClojureJVM, but does not on ClojureScript:
    `(re-matches #\"[a[b[c]]]+\" \"abc\")`.  As a result it's worth being
    particularly careful when composing character classes programmatically, to
    avoid accidentally nesting them."
  [& res]
  (when-let [res (seq (filter identity res))]
    (let [exp (apply join res)]
      (if (empty?' exp)
        #""
        (join "[" exp "]")))))


;; GROUPS

(defn grp
  "As for [[join]], but encloses the joined `res` into a single non-capturing
  group:

  * `(?:res)`"
  [& res]
  (when-let [res (seq (filter identity res))]
    ; Here we optimise out an empty non-capturing group
    (let [exp (apply join res)]
      (if (empty?' exp)
        #""
        (join "(?:" exp ")")))))

(defn cg
  "As for [[grp]], but uses a capturing group:

  * `(res)`"
  [& res]
  (when-let [res (seq (filter identity res))]
    (join "(" (apply join res) ")")))  ; Note: don't optimise empty capturing groups, because that will throw out code that indexes into capturing groups

(defn ncg
  "As for [[grp]], but uses a named capturing group named `nm`:

  * `(?<nm>res)`

  Returns `nil` if `nm` is `nil` or blank. Throws if `nm` is an invalid name for
  a named capturing group (alphanumeric only, must start with an alphabetical
  character, must be unique within the regex)."
  [nm & res]
  (when-not (s/blank? nm)
    (when-let [res (seq (filter identity res))]
      (join "(?<" nm ">" (apply join res) ")"))))  ; Note: don't optimise empty named capturing groups, because that will throw out code that indexes into capturing groups

(defn flags-grp
  "As for [[grp]], but prefixes the group with `flgs` (a `String`):

  * `(?flgs:res)`

  Returns `nil` if `flgs` is `nil` or empty.  Throws if `flgs` contains an
  invalid flag character, including those that (ClojureScript only) cannot be
  embedded.

  Notes:

  * If you must use regex flags, **it is STRONGLY RECOMMENDED that you use this
    function!**  Programmatically set flags and ungrouped embedded flags (e.g.
    `(?i)`) have no explicit scope and so cannot be reliably used to compose
    larger regexes.  `wreck` makes a best effort to always convert such
    'unscoped' flags into their embedded (scoped) equivalents (using
    [[embed-flags]]) when composing larger regexes , but using `flags-grp`
    explicitly in the first place is easier to reason about and avoids potential
    footguns.
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
  [flgs & res]
  (when-not (s/blank? flgs)
    (when-let [res (seq (filter identity res))]
      (wi/set-flags (apply join res) flgs))))


;; OPTIONAL

(defn opt
  "Returns a regex where `re` is optional:

  * `re?`"
  [re]
  (when re
    (join re "?")))

(defn opt-grp
  "[[grp]] then [[opt]]:

  * `(?:res)?`"
  [& res]
  (when-let [res (seq (filter identity res))]
    (opt (apply grp res))))

(defn opt-cg
  "[[cg]] then [[opt]]:

  * `(res)?`"
  [& res]
  (when-let [res (seq (filter identity res))]
    (opt (apply cg res))))

(defn opt-ncg
  "[[ncg]] then [[opt]]:

  * `(?<nm>res)?`"
  [nm & res]
  (when-not (s/blank? nm)
    (when-let [res (seq (filter identity res))]
      (opt (apply (partial ncg nm) res)))))


;; ZERO OR MORE

(defn zom
  "Returns a regex where `re` will match zero or more times:

  * `re*`"
  [re]
  (when re
    (join re "*")))

(defn zom-grp
  "[[grp]] then [[zom]]:

  * `(?:res)*`"
  [& res]
  (when-let [res (seq (filter identity res))]
    (zom (apply grp res))))

(defn zom-cg
  "[[cg]] then [[zom]]:

  * `(res)*`"
  [& res]
  (when-let [res (seq (filter identity res))]
    (zom (apply cg res))))

(defn zom-ncg
  "[[ncg]] then [[zom]]:

  * `(?<nm>res)*`"
  [nm & res]
  (when-not (s/blank? nm)
    (when-let [res (seq (filter identity res))]
      (zom (apply (partial ncg nm) res)))))


;; ONE OR MORE

(defn oom
  "Returns a regex where `re` will match one or more times:

  * `re+`"
  [re]
  (when re
    (join re "+")))

(defn oom-grp
  "[[grp]] then [[oom]]:

  * `(?:res)+`"
  [& res]
  (when-let [res (seq (filter identity res))]
    (oom (apply grp res))))

(defn oom-cg
  "[[cg]] then [[oom]]:

  * `(res)+`"
  [& res]
  (when-let [res (seq (filter identity res))]
    (oom (apply cg res))))

(defn oom-ncg
  "[[ncg]] then [[oom]]:

  * `(?<nm>res)+`"
  [nm & res]
  (when-not (s/blank? nm)
    (when-let [res (seq (filter identity res))]
      (oom (apply (partial ncg nm) res)))))


;; N OR MORE

(defn nom
  "Returns a regex where `re` will match `n` or more times:

  * `re{n,}`"
  [n re]
  (when (and n re)
    (join re "{" n ",}")))

(defn nom-grp
  "[[grp]] then [[nom]]:

  * `(?:res){n,}`"
  [n & res]
  (when n
    (when-let [res (seq (filter identity res))]
      (nom n (apply grp res)))))

(defn nom-cg
  "[[cg]] then [[nom]]:

  * `(res){n,}`"
  [n & res]
  (when n
    (when-let [res (seq (filter identity res))]
      (nom n (apply cg res)))))

(defn nom-ncg
  "[[ncg]] then [[nom]]:

  * `(?<nm>res){n,}`"
  [nm n & res]
  (when (and (not (s/blank? nm)) n)
    (when-let [res (seq (filter identity res))]
      (nom n (apply (partial ncg nm) res)))))


;; EXACTLY N

(defn exn
  "Returns a regex where `re` will match exactly `n` times:

  * `re{n}`"
  [n re]
  (when (and n re)
    (join re "{" n "}")))

(defn exn-grp
  "[[grp]] then [[exn]]:

  * `(?:res){n}`"
  [n & res]
  (when n
    (when-let [res (seq (filter identity res))]
      (exn n (apply grp res)))))

(defn exn-cg
  "[[cg]] then [[exn]]:

  * `(res){n}`"
  [n & res]
  (when n
    (when-let [res (seq (filter identity res))]
      (exn n (apply cg res)))))

(defn exn-ncg
  "[[ncg]] then [[exn]]:

  * `(?<nm>res){n}`"
  [nm n & res]
  (when (and (not (s/blank? nm)) n)
    (when-let [res (seq (filter identity res))]
      (exn n (apply (partial ncg nm) res)))))


;; N TO M

(defn n2m
  "Returns a regex where `re` will match from `n` to `m` times:

  * `re{n,m}`"
  [n m re]
  (when (and n m re)
    (join re "{" n "," m "}")))

(defn n2m-grp
  "[[grp]] then [[n2m]]:

  * `(?:res){n,m}`"
  [n m & res]
  (when (and n m)
    (when-let [res (seq (filter identity res))]
      (n2m n m (apply grp res)))))

(defn n2m-cg
  "[[cg]] then [[n2m]]:

  * `(res){n,m}`"
  [n m & res]
  (when (and n m)
    (when-let [res (seq (filter identity res))]
      (n2m n m (apply cg res)))))

(defn n2m-ncg
  "[[ncg]] then [[n2m]]:

  * `(?<nm>res){n,m}`"
  [nm n m & res]
  (when (and (not (s/blank? nm)) n m)
    (when-let [res (seq (filter identity res))]
      (n2m n m (apply (partial ncg nm) res)))))


;; ALTERNATION

(defn alt
  "Returns a regex that will match any one of `res`, via alternation:

  * `re|re|re|...`

  Notes:

  * Duplicate elements in `res` will only appear once in the result. This
    equality comparison occurs _after_ each re is run through [[embed-flags]].
  * Does _not_ wrap the result in a group, which, [because alternation has the
    lowest precedence in regexes](https://pubs.opengroup.org/onlinepubs/9699919799/basedefs/V1_chap09.html#tag_09_04_08),
    runs the risk of behaving unexpectedly if the result is then combined with
    further regexes.
    tl;dr - one of the grouping variants should _almost always_ be preferred."
  [& res]
  (when-let [res (distinct' (filter identity res))]
    (apply join (interpose "|" res))))

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
  [nm & res]
  (ncg nm (apply alt res)))


;; LOGICAL OPERATORS

(defn and'
  "Returns an 'and' regex that will match `a` and `b` in any order, and with the
  separator regex `s` (if provided) between them:

  * `asb|bsa`

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
   (when-not (and (empty?' a) (empty?' b))
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
  ([nm a b] (and-ncg nm a b nil))
  ([nm a b s]
   (ncg nm (and' a b s))))

(defn or'
  "Returns an 'inclusive or' regex that will match `a` or `b`, or both, in any
  order, and with the separator regex `s` (if provided) between them:

  * `asb|bsa|a|b`

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
   (when-not (and (empty?' a) (empty?' b))
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
  ([nm a b] (or-ncg nm a b nil))
  ([nm a b s]
   (ncg nm (or' a b s))))

(defn xor'
  "Returns an 'exclusive or' regex that will match `a` or `b`, but _not_ both:

  * `a|b`

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
  [nm a b]
  (ncg nm (xor' a b)))
