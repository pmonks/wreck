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
    checking, since the rules for regular expressions vary from platform to
    platform, and it is a first class requirement that callers be allowed to
    construct platform specific regular expressions if they wish.
  * As a result, all functions have the potential to throw platform-specific
    exceptions if the resulting regular expression is syntactically invalid.
  * On the JVM, these will typically be instances of the
    `java.util.regex.PatternSyntaxException` class.
  * On JavaScript, these will typically be a `js/SyntaxError`.
  * Platform specific behaviour is particularly notable for short / empty
    regular expressions, such as `#\"{}\"` (an error on the JVM, fine but
    nonsensical on JS) and `#\"{1}\"` (ironically, fine but nonsensical on the
    JVM, but an error on JS).  🤡
  * Furthemore, JavaScript fundamentally doesn't support lossless round-tripping
    of `RegExp` objects to `String`s and back, something this library relies
    upon and does extensively.  The library makes a best effort to correct
    JavaScript's problematic implementation, but because it's fundamentally
    lossy there are some cases that (on ClojureScript only) may change your
    regexes in unexpected (though not semantically significant) ways."
  (:require [clojure.string :as s]
   #?(:cljs [goog.object])))


;; FUNDAMENTAL PRIMITIVES

; We have to do this chicanery because regexes and strings don't round-trip in JavaScript  🙄
; This awful code is a best effort to handle this lunacy.
;
; Some examples of how much of a 🤡show JavaScript regexes are:
;
;   (str (re-pattern #""))                    =>  "/(?:)/"
;   (str (re-pattern "foo"))                  =>  "/foo/"
;   (str (re-pattern "foo/bar"))              =>  "/foo\\/bar/"
;   (str (re-pattern "foo\\/bar"))            =>  "/foo\\/bar/"
;   (str (re-pattern (str (re-pattern ""))))  =>  "/\\/(?:)\\//"

#?(
:clj (def ^{:doc
  "Returns the `String` representation of `o`, with special handling for
  `RegExp` objects on ClojureScript in an attempt to correct JavaScript's
  **APPALLING** default stringification."
  :arglists '([o])}
  str' str)

:cljs (defn str'
  "Returns the `String` representation of `o`, with special handling for
  `RegExp` objects on ClojureScript in an attempt to correct JavaScript's
  **APPALLING** default stringification."
  [o]
  (if (not= (type o) js/RegExp)
    (str o)
    (let [s (goog.object/get o "source")]  ; Remove leading and trailing "/" and any flags
      (-> s
          (s/replace "(?:)" "")            ; Remove redundant capturing groups (inserted by JavaScript's idiotic RegExp class when a regex is blank)
          (s/replace "\\/"  "/")))))       ; Remove redundant escapings of "/" (inserted by JavaScript's idiotic RegExp class)
)

(defn ='
  "Equality for regexes, defined by having equal `String` representations.  This
  means that _equivalent_ regexes (e.g. `#\"...\"` and `#\".{3}\"` will _not_ be
  considered equal.

  Notes:

  * Some JavaScript runtimes that ClojureScript runs on correctly implement
    equality for regexes, but the JVM does not."
  ([_]       true)
  ([re1 re2] (= (str' re1) (str' re2)))
  ([re1 re2 & more]
    (if (= (str' re1) (str' re2))
      (if (next more)
        (recur re2 (first more) (rest more))
        (= (str' re2) (str' (first more))))
      false)))

(defn empty?'
  "Is `re` `nil` or `(=' #\"\")`?"
  [re]
  (or (nil? re)
      (=' #"" re)))

(defn join
  "Returns a regex that is all of the `res` joined together. Each element in
  `res` can be a regex, a `String` or something that can be turned into a
  `String` (including numbers, etc.).  Returns `nil` when no `res` are provided,
  or they're all `nil`.

  Notes:

  * In ClojureScript be cautious about using numbers in these calls, since
    JavaScript's number handling is a 🤡show.  See [this unit test](https://github.com/pmonks/wreck/blob/dev/test/wreck/api_test.cljc#L93)
    for a worked example of the types of problems that can occur."
  [& res]
  (when-let [res (seq (filter identity res))]
    (re-pattern (s/join (map str' res)))))

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
        (let [f-str      (str' f)
              new-result (if (contains? seen f-str) result (conj result f))]
          (recur r new-result (conj seen f-str)))))))


;; GROUPS

(defn grp
  "As for [[join]], but encloses the joined `res` into a single non-capturing
  group."
  [& res]
  (when-let [res (seq (filter identity res))]
    ; Here we optimise out an empty non-capturing group
    (let [exp (apply join res)]
      (if (empty?' exp)
        #""
        (join "(?:" exp ")")))))

(defn cg
  "As for [[grp]], but uses a capturing group."
  [& res]
  (when-let [res (seq (filter identity res))]
    (join "(" (apply join res) ")")))  ; Note: don't optimise empty capturing groups, because that will throw out code that indexes into capturing groups

(defn ncg
  "As for [[grp]], but uses a named capturing group named `nm`.  Returns `nil` if
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
  "[[grp]] then [[opt]]."
  [& res]
  (when-let [res (seq (filter identity res))]
    (opt (apply grp res))))

(defn opt-cg
  "[[cg]] then [[opt]]."
  [& res]
  (when-let [res (seq (filter identity res))]
    (opt (apply cg res))))

(defn opt-ncg
  "[[ncg]] then [[opt]]."
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
  "[[grp]] then [[zom]]."
  [& res]
  (when-let [res (seq (filter identity res))]
    (zom (apply grp res))))

(defn zom-cg
  "[[cg]] then [[zom]]."
  [& res]
  (when-let [res (seq (filter identity res))]
    (zom (apply cg res))))

(defn zom-ncg
  "[[ncg]] then [[zom]]."
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
  "[[grp]] then [[oom]]."
  [& res]
  (when-let [res (seq (filter identity res))]
    (oom (apply grp res))))

(defn oom-cg
  "[[cg]] then [[oom]]."
  [& res]
  (when-let [res (seq (filter identity res))]
    (oom (apply cg res))))

(defn oom-ncg
  "[[ncg]] then [[oom]]."
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
  "[[grp]] then [[nom]]."
  [n & res]
  (when n
    (when-let [res (seq (filter identity res))]
      (nom n (apply grp res)))))

(defn nom-cg
  "[[cg]] then [[nom]]."
  [n & res]
  (when n
    (when-let [res (seq (filter identity res))]
      (nom n (apply cg res)))))

(defn nom-ncg
  "[[ncg]] then [[nom]]."
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
  "[[grp]] then [[exn]]."
  [n & res]
  (when n
    (when-let [res (seq (filter identity res))]
      (exn n (apply grp res)))))

(defn exn-cg
  "[[cg]] then [[exn]]."
  [n & res]
  (when n
    (when-let [res (seq (filter identity res))]
      (exn n (apply cg res)))))

(defn exn-ncg
  "[[ncg]] then [[exn]]."
  [nm n & res]
  (when (and (not (s/blank? nm)) n)
    (when-let [res (seq (filter identity res))]
      (exn n (apply (partial ncg nm) res)))))


;; N TO M

(defn n2m
  "Returns a regex where `re` will match from `n` to `m` times."
  [n m re]
  (when (and n m re)
    (join re "{" n "," m "}")))

(defn n2m-grp
  "[[grp]] then [[n2m]]."
  [n m & res]
  (when (and n m)
    (when-let [res (seq (filter identity res))]
      (n2m n m (apply grp res)))))

(defn n2m-cg
  "[[cg]] then [[n2m]]."
  [n m & res]
  (when (and n m)
    (when-let [res (seq (filter identity res))]
      (n2m n m (apply cg res)))))

(defn n2m-ncg
  "[[ncg]] then [[n2m]]."
  [nm n m & res]
  (when (and (not (s/blank? nm)) n m)
    (when-let [res (seq (filter identity res))]
      (n2m n m (apply (partial ncg nm) res)))))


;; ALTERNATION

(defn alt
  "Returns a regex that will match any one of `res`, via alternation.

  Notes:

  * Duplicate elements in `res` will only appear once in the result.
  * Does _not_ wrap the result in a group, which, [because alternation has the
    lowest precedence in regexes](https://pubs.opengroup.org/onlinepubs/9699919799/basedefs/V1_chap09.html#tag_09_04_08),
    runs the risk of behaving unexpectedly if the result is then combined with
    further regexes.
    tl;dr - one of the grouping variants should _almost always_ be preferred."
  [& res]
  (when-let [res (distinct' (filter identity res))]
    (apply join (interpose "|" res))))

(defn alt-grp
  "[[alt]] then [[grp]]."
  [& res]
   (grp (apply alt res)))

(defn alt-cg
  "[[alt]] then [[cg]]."
  [& res]
   (cg (apply alt res)))

(defn alt-ncg
  "[[alt]] then [[ncg]]."
  [nm & res]
  (ncg nm (apply alt res)))


;; LOGICAL OPERATORS

(defn and'
  "Returns an 'and' regex that will match `a` and `b` in any order, and with the
  `s`eparator regex (if provided) between them.  This is implemented as
  `ASB|BSA`, which means that A and B must be distinct (must not match the same
  text).

  Notes:

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
  "[[and']] then [[grp]].

  Notes:

  * Unlike most other `-grp` fns, this one does _not_ accept any number of res.
  * May optimise the expression (via de-duplication in [[alt]])."
  ([a b] (and-grp a b nil))
  ([a b s]
   (grp (and' a b s))))

(defn and-cg
  "[[and']] then [[cg]].

  Notes:

  * Unlike most other `-grp` fns, this one does _not_ accept any number of res.
  * May optimise the expression (via de-duplication in [[alt]])."
  ([a b] (and-cg a b nil))
  ([a b s]
   (cg (and' a b s))))

(defn and-ncg
  "[[and']] then [[ncg]].

  Notes:

  * Unlike most other `-grp` fns, this one does _not_ accept any number of res.
  * May optimise the expression (via de-duplication in [[alt]])."
  ([nm a b] (and-ncg nm a b nil))
  ([nm a b s]
   (ncg nm (and' a b s))))

(defn or'
  "Returns an 'inclusive or' regex that will match `a` or `b`, or both, in any
  order, and with the `s`eparator regex (if provided) between them.  This is
  implemented as `ASB|BSA|A|B`, which means that A and B must be distinct (must
  not match the same text).

  Notes:

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
  "[[or']] then [[grp]].

  Notes:

  * Unlike most other `-grp` fns, this one does _not_ accept any number of res.
  * May optimise the expression (via de-duplication in [[alt]])."
  ([a b] (or-grp a b nil))
  ([a b s]
   (grp (or' a b s))))

(defn or-cg
  "[[or']] then [[cg]].

  Notes:

  * Unlike most other `-grp` fns, this one does _not_ accept any number of res.
  * May optimise the expression (via de-duplication in [[alt]])."
  ([a b] (or-cg a b nil))
  ([a b s]
   (cg (or' a b s))))

(defn or-ncg
  "[[or']] then [[ncg]].

  Notes:

  * Unlike most other `-grp` fns, this one does _not_ accept any number of res.
  * May optimise the expression (via de-duplication in [[alt]])."
  ([nm a b] (or-ncg nm a b nil))
  ([nm a b s]
   (ncg nm (or' a b s))))

(defn xor'
  "Returns an 'exclusive or' regex that will match `a` or `b`, but _not_ both.
  This is identical to [[alt]] called with 2 arguments, and is provided as a
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
  "[[xor']] then [[grp]].

  Notes:

  * Unlike most other `-grp` fns, this one does _not_ accept any number of res.
  * May optimise the expression (via de-duplication in [[alt]])."
  [a b]
  (grp (xor' a b)))

(defn xor-cg
  "[[xor']] then [[cg]].

  Notes:

  * Unlike most other `-grp` fns, this one does _not_ accept any number of res.
  * May optimise the expression (via de-duplication in [[alt]])."
  [a b]
  (cg (xor' a b)))

(defn xor-ncg
  "[[xor']] then [[ncg]].

  Notes:

  * Unlike most other `-grp` fns, this one does _not_ accept any number of res.
  * May optimise the expression (via de-duplication in [[alt]])."
  [nm a b]
  (ncg nm (xor' a b)))
