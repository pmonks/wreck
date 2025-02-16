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
  "The public API of `wreck`.

  Notes:

  * Apart from passing through `nil`, this library does minimal argument
    checking, since the rules for regular expressions vary from platform to
    platform, and it is a first class requirement that callers be allowed to
    construct platform specific regular expressions if they wish.
  * As a result, all functions have the potential to throw platform-specific
    exceptions if the resulting regular expression is syntactically invalid.
  * On the JVM, these will typically be instances of the
    `java.util.regex.PatternSyntaxException` class.
  * On JavaScript, these will typically be a `SyntaxError`s.
  * Platform specific behaviour is particularly notable for short / empty
    regular expressions, such as `#\"{}\"` (error on JVM, fine but nonsencial on
    JS) and `#\"{1}\"` (fine but nonsensical on JVM, but error on JS)."
  (:require [clojure.string :as s]))


;; FUNDAMENTAL PRIMITIVES

(defn ='
  "Equality for regexes, defined by having equal `String` representations.  This
  means that _equivalent_ regexes (e.g. `#\"...\"` and `#\".{3}\"` will _not_ be
  considered equal.

  Notes: this is only needed in ClojureJVM (ClojureScript correctly implements
  equality for regexes)."
  ([_]       true)
  ([re1 re2] (= (str re1) (str re2)))
  ([re1 re2 & more]
    (if (= (str re1) (str re2))
      (if (next more)
        (recur re2 (first more) (rest more))
        (= (str re2) (str (first more))))
      false)))

(defn empty?'
  "Is `re` `nil` or `(=' #\"\")`?"
  [re]
  (or (nil? re)
      (=' #"" re)))

(defn join
  "Returns a regex that is all of the ` res` joined together. Each element in
  `res` can be a regex, a `String` or something that can be turned into a
  `String` (including numbers, etc.).  Returns `nil` when no `res` are provided,
  or they're all `nil`."
  [& res]
  (let [res (seq (filter identity res))]
    (when res
      (re-pattern (s/join res)))))

(defn esc
  "Escapes `s` (a `String`) for use in a regex, returning a `String`.  Note that
  unlike most other fns in this namespace, this one does _not_ support a regex
  as an input, nor return a regex as an output."
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
  "Quotes `s` (a `String`) for use in a regex, returning a regex.  Note that
  unlike most other fns in this namespace, this one does _not_ support a regex
  as an input."
  [s]
  (when s
    (join "\\Q" s "\\E")))


;; Internal implementation details

(defn- distinct'
  "Similar to `clojure.core/distinct`, but non-lazy, and uses the regex
  representation of each element (e.g. `0`, `\"0\"`, and `#\"0\"` would all be
  considered identical).

  Notes: unlike `clojure.core/distinct`, returns `nil` if the result is empty."
  [res]
  (when res
    (loop [[f & r] res
           result  []
           seen    #{}]
      (if-not f
        (seq result)
        (let [f-str      (str f)
              new-result (if (contains? seen f-str) result (conj result f))]
          (recur r new-result (conj seen f-str)))))))


;; GROUPS

(defn grp
  "As for [join], but encloses the joined `res` into a single non-capturing
  group."
  [& res]
  (let [res (seq (filter identity res))]
    (when res
      ; Here we optimise out an empty non-capturing group
      (let [exp (apply join res)]
        (if (empty?' exp)
          #""
          (join "(?:" exp ")"))))))

(defn cg
  "As for [grp], but uses a capturing group."
  [& res]
  (let [res (seq (filter identity res))]
    (when res
      (join "(" (apply join res) ")"))))  ; Note: don't optimise empty capturing groups, because that will throw out code that indexes into capturing groups

(defn ncg
  "As for [grp], but uses a named capturing group named `nm`.  Returns `nil` if
  `nm` is `nil` or blank. Throws if `nm` is an invalid name for a named capturing
  group (alphanumeric only, must start with an alphabetical character, must be
  unique within the regex)."
  [nm & res]
  (when-not (s/blank? nm)
    (when-let [res (seq (filter identity res))]
      (join "(?<" nm ">" (apply join res) ")"))))  ; Note: don't optimise empty named capturing groups, because that will throw out code that indexes into capturing groups


;; OPTIONAL

(defn opt
  "Returns a regex where `re` is optional."
  [re]
  (when re
    (join re "?")))

(defn opt-grp
  "[grp] then [opt]."
  [& res]
  (when-let [res (seq (filter identity res))]
    (opt (apply grp res))))

(defn opt-cg
  "[cg] then [opt]."
  [& res]
  (when-let [res (seq (filter identity res))]
    (opt (apply cg res))))

(defn opt-ncg
  "[ncg] then [opt]."
  [nm & res]
  (when-not (s/blank? nm)
    (when-let [res (seq (filter identity res))]
      (opt (apply (partial ncg nm) res)))))


;; ZERO OR MORE

(defn zom
  "Returns a regex where `re` will match zero or more times."
  [re]
  (when re
    (join re "*")))

(defn zom-grp
  "[grp] then [zom]."
  [& res]
  (when-let [res (seq (filter identity res))]
    (zom (apply grp res))))

(defn zom-cg
  "[cg] then [zom]."
  [& res]
  (when-let [res (seq (filter identity res))]
    (zom (apply cg res))))

(defn zom-ncg
  "[ncg] then [zom]."
  [nm & res]
  (when-not (s/blank? nm)
    (when-let [res (seq (filter identity res))]
      (zom (apply (partial ncg nm) res)))))


;; ONE OR MORE

(defn oom
  "Returns a regex where `re` will match one or more times."
  [re]
  (when re
    (join re "+")))

(defn oom-grp
  "[grp] then [oom]."
  [& res]
  (when-let [res (seq (filter identity res))]
    (oom (apply grp res))))

(defn oom-cg
  "[cg] then [oom]."
  [& res]
  (when-let [res (seq (filter identity res))]
    (oom (apply cg res))))

(defn oom-ncg
  "[ncg] then [oom]."
  [nm & res]
  (when-not (s/blank? nm)
    (when-let [res (seq (filter identity res))]
      (oom (apply (partial ncg nm) res)))))


;; N OR MORE

(defn nom
  "Returns a regex where `re` will match `n` or more times."
  [n re]
  (when (and n re)
    (join re "{" n ",}")))

(defn nom-grp
  "[grp] then [nom]."
  [n & res]
  (when n
    (when-let [res (seq (filter identity res))]
      (nom n (apply grp res)))))

(defn nom-cg
  "[cg] then [nom]."
  [n & res]
  (when n
    (when-let [res (seq (filter identity res))]
      (nom n (apply cg res)))))

(defn nom-ncg
  "[ncg] then [nom]."
  [nm n & res]
  (when (and (not (s/blank? nm)) n)
    (when-let [res (seq (filter identity res))]
      (nom n (apply (partial ncg nm) res)))))


;; EXACTLY N

(defn exn
  "Returns a regex where `re` will match exactly `n` times."
  [n re]
  (when (and n re)
    (join re "{" n "}")))

(defn exn-grp
  "[grp] then [exn]."
  [n & res]
  (when n
    (when-let [res (seq (filter identity res))]
      (exn n (apply grp res)))))

(defn exn-cg
  "[cg] then [exn]."
  [n & res]
  (when n
    (when-let [res (seq (filter identity res))]
      (exn n (apply cg res)))))

(defn exn-ncg
  "[ncg] then [exn]."
  [nm n & res]
  (when (and (not (s/blank? nm)) n)
    (when-let [res (seq (filter identity res))]
      (exn n (apply (partial ncg nm) res)))))


;; N TO M

(defn n2m
  "Returns a regex where `re` will match from `n` to `m` times."
  [n m re]
  (when (and n m)
    (join re "{" n "," m "}")))

(defn n2m-grp
  "[grp] then [n2m]."
  [n m & res]
  (when (and n m)
    (when-let [res (seq (filter identity res))]
      (n2m n m (apply grp res)))))

(defn n2m-cg
  "[cg] then [n2m]."
  [n m & res]
  (when (and n m)
    (when-let [res (seq (filter identity res))]
      (n2m n m (apply cg res)))))

(defn n2m-ncg
  "[ncg] then [n2m]."
  [nm n m & res]
  (when (and (not (s/blank? nm)) n m)
    (when-let [res (seq (filter identity res))]
      (n2m n m (apply (partial ncg nm) res)))))


;; ALTERNATION

(defn alt
  "Returns a regex that will match any one of `res`, via alternation.

  Notes:

  * Duplicate elements in `res` will only appear once in the result."
  [& res]
  (when-let [res (distinct' (filter identity res))]
    (apply join (interpose "|" res))))

(defn alt-grp
  "[grp] on each element in `res`, then [alt]."
  [& res]
  (when-let [res (distinct' (filter identity res))]
    (apply alt (map #(join "(?:" % ")") res))))

; Note: capturing group versions don't make much sense for alt, and so are not
; provided.  A more typical pattern would be to wrap an entire alt'/alt-grp
; expression in a (single) capturing group.  If you do in fact need this, please
; don't hesitate to raise an issue: https://github.com/pmonks/wreck/issues/new?template=Feature_request.md


;; LOGICAL OPERATORS

(defn and'
  "Returns an 'and' regex that will match `a` and `b` in any order, and with the
  `s`eparator regex (if provided) between them.  This is implemented as
  `ASB|BSA`, which means that A and B must be distinct (must not match the same
  text).

  Notes:

  * May optimise the expression (via de-duplication in [alt])."
  ([a b] (and' a b nil))
  ([a b s]
    (alt (join a s b) (join b s a))))

(defn and-grp
  "As for [and'], but each element in the alternation is grouped with [grp].

  Notes:

  * Unlike most other `-grp` fns, this one does _not_ accept any number of res.
  * May optimise the expression (via de-duplication in [alt])."
  ([a b] (and-grp a b nil))
  ([a b s]
    (alt (grp a s b) (grp b s a))))

; Note: capturing group versions don't make much sense for and', and so are not
; provided.  A more typical pattern would be to wrap an entire and'/and-grp
; expression in a (single) capturing group.  If you do in fact need this, please
; don't hesitate to raise an issue: https://github.com/pmonks/wreck/issues/new?template=Feature_request.md

(defn or'
  "Returns an 'inclusive or' regex that will match `a` or `b`, or both, in any
  order, and with the `s`eparator regex (if provided) between them.  This is
  implemented as `ASB|BSA|A|B`, which means that A and B must be distinct (must
  not match the same text).

  Notes:

  * May optimise the expression (via de-duplication in [alt])."
  ([a b] (or' a b nil))
  ([a b s]
    (alt (join a s b) (join b s a) a b)))

(defn or-grp
  "As for [or'], but each element in the alternation is grouped with [grp].

  Notes:

  * Unlike most other `-grp` fns, this one does _not_ accept any number of res.
  * May optimise the expression (via de-duplication in [alt])."
  ([a b] (or-grp a b nil))
  ([a b s]
    (alt (grp a s b) (grp b s a) (grp a) (grp b))))

; Note: capturing group versions don't make much sense for or', and so are not
; provided.  A more typical pattern would be to wrap an entire or'/or-grp
; expression in a (single) capturing group.  If you do in fact need this, please
; don't hesitate to raise an issue: https://github.com/pmonks/wreck/issues/new?template=Feature_request.md

(defn xor'
  "Returns an 'exclusive or' regex that will match `a` or `b`, but _not_ both.
  This is identical to [alt] called with 2 arguments, and is provided as a
  convenience for those who might be building up large logic based regexes and
  would prefer to use more easily understood logical operator names throughout.

  Notes:

  * May optimise the expression (via de-duplication in [alt])."
  [a b]
  (alt a b))

(defn xor-grp
  "As for [xor'], but each element in the alternation is grouped with [grp].

  Notes:

  * Unlike most other `-grp` fns, this one does _not_ accept any number of res.
  * May optimise the expression (via de-duplication in [alt])."
  [a b]
  (alt (grp a) (grp b)))

; Note: capturing group versions don't make much sense for xor', and so are not
; provided.  A more typical pattern would be to wrap an entire xor'/xor-grp
; expression in a (single) capturing group.  If you do in fact need this, please
; don't hesitate to raise an issue: https://github.com/pmonks/wreck/issues/new?template=Feature_request.md
