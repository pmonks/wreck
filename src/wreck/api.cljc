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
  "The public API of the library.  Note that all methods will throw if the
  resulting regular expression is syntactically invalid."
  (:require [clojure.string :as s]))


;; FUNDAMENTAL PRIMITIVES

(defn ='
  "Equality for regexes, defined by having equal `String` representations.  This
  means that _equivalent_ regexes (e.g. `#\"...\"` and `#\".{3}\"` will _not_ be
  considered equal.

  Note: this is only needed in ClojureJVM (ClojureScript correctly implements
  equality for regexes)."
  ([_]       true)
  ([re1 re2] (= (str re1) (str re2)))
  ([re1 re2 & more]
    (if (= (str re1) (str re2))
      (if (next more)
        (recur re2 (first more) (rest more))
        (= (str re2) (str (first more))))
      false)))

(defn join
  "Returns a regex that is the ` res` joined together. Each element in `res` can
  be a regex, `String` or something that can be turned into a `String`.  Returns
  `nil` if no `res` are provided"
  [& res]
  (let [res (seq (filter identity res))]
    (when res
      (re-pattern (s/join res)))))

(defn esc
  "Escapes `s` (a `String`) for use in a regex, returning a `String`.  Note that
  unlike most other fns in this namespace, this one does _not_ support a regex
  as an input."
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
                 \> "\\>"
                 })))

(defn qot
  "Quotes `s` (a `String`) for use in a regex, returning a regex.  Note that
  unlike most other fns in this namespace, this one does _not_ support a regex
  as an input."
  [s]
  (when s
    (join "\\Q" s "\\E")))


;; GROUPS

(defn grp
  "As for [join], but encloses the joined `res` into a single non-capturing
  group."
  [& res]
  (let [res (seq (filter identity res))]
    (when res
      (join "(?:" (apply join res) ")"))))

(defn cg
  "As for [grp], but uses a capturing group."
  [& res]
  (let [res (seq (filter identity res))]
    (when res
      (join "(" (apply join res) ")"))))

(defn ncg
  "As for [grp], but uses a named capturing group named `nm`.  Returns `nil` if
  `nm` is `nil` or blank. Throws if `nm` is an invalid name for a named capturing
  group (alphanumeric only, must start with an alphabetical character, must be
  unique within the regex)."
  [nm & res]
  (let [res (seq (filter identity res))]
    (when (and (not (s/blank? nm)) res)
      (join "(?<" nm ">" (apply join res) ")"))))


;; OPTIONAL

(defn opt
  "Returns a regex where `re` is optional. Does not perform any grouping."
  [re]
  (when re
    (join re "?")))

(defn opt-grp
  "[grp] then [opt]."
  [& res]
  (let [res (seq (filter identity res))]
    (when res
      (opt (apply grp res)))))

(defn opt-cg
  "[cg] then [opt]."
  [& res]
  (let [res (seq (filter identity res))]
    (when res
      (opt (apply cg res)))))

(defn opt-ncg
  "[ncg] then [opt]."
  [nm & res]
  (let [res (seq (filter identity res))]
    (when (and (not (s/blank? nm)) res)
      (opt (apply (partial ncg nm) res)))))


;; ZERO OR MORE

(defn zom
  "Returns a regex where `re` will match zero or more times. Does not perform
  any grouping."
  [re]
  (when re
    (join re "*")))

(defn zom-grp
  "[grp] then [zom]."
  [& res]
  (let [res (seq (filter identity res))]
    (when res
      (zom (apply grp res)))))

(defn zom-cg
  "[cg] then [zom]."
  [& res]
  (let [res (seq (filter identity res))]
    (when res
      (zom (apply cg res)))))

(defn zom-ncg
  "[ncg] then [zom]."
  [nm & res]
  (let [res (seq (filter identity res))]
    (when (and (not (s/blank? nm)) res)
      (zom (apply (partial ncg nm) res)))))


;; ONE OR MORE

(defn oom
  "Returns a regex where `re` will match one or more times. Does not perform any
  grouping."
  [re]
  (when re
    (join re "+")))

(defn oom-grp
  "[grp] then [oom]."
  [& res]
  (let [res (seq (filter identity res))]
    (when res
      (oom (apply grp res)))))

(defn oom-cg
  "[cg] then [oom]."
  [& res]
  (let [res (seq (filter identity res))]
    (when res
      (oom (apply cg res)))))

(defn oom-ncg
  "[ncg] then [oom]."
  [nm & res]
  (let [res (seq (filter identity res))]
    (when (and (not (s/blank? nm)) res)
      (oom (apply (partial ncg nm) res)))))


;; N OR MORE

(defn nom
  "Returns a regex where `re` will match `n` or more times. Does not perform any
  grouping."
  [n re]
  (when (and n re)
    (join re "{" n ",}")))

(defn nom-grp
  "[grp] then [nom]."
  [n & res]
  (let [res (seq (filter identity res))]
    (when (and n res)
      (nom n (apply grp res)))))

(defn nom-cg
  "[cg] then [nom]."
  [n & res]
  (let [res (seq (filter identity res))]
    (when (and n res)
      (nom n (apply cg res)))))

(defn nom-ncg
  "[ncg] then [nom]."
  [nm n & res]
  (let [res (seq (filter identity res))]
    (when (and (not (s/blank? nm)) n res)
      (nom n (apply (partial ncg nm) res)))))


;; EXACTLY N

(defn exn
  "Returns a regex where `re` will match exactly `n` times. Does not perform any
  grouping."
  [n re]
  (when (and n re)
    (join re "{" n "}")))

(defn exn-grp
  "[grp] then [exn]."
  [n & res]
  (let [res (seq (filter identity res))]
    (when (and n res)
      (exn n (apply grp res)))))

(defn exn-cg
  "[cg] then [exn]."
  [n & res]
  (let [res (seq (filter identity res))]
    (when (and n res)
      (exn n (apply cg res)))))

(defn exn-ncg
  "[ncg] then [exn]."
  [nm n & res]
  (let [res (seq (filter identity res))]
    (when (and (not (s/blank? nm)) n res)
      (exn n (apply (partial ncg nm) res)))))


;; N TO M

(defn n2m
  "Returns a regex where `re` will match from `n` to `m` times. Does not perform
  any grouping."
  [n m re]
  (when (and n m re)
    (join re "{" n "," m "}")))

(defn n2m-grp
  "[grp] then [n2m]."
  [n m & res]
  (let [res (seq (filter identity res))]
    (when (and n m res)
      (n2m n m (apply grp res)))))

(defn n2m-cg
  "[cg] then [n2m]."
  [n m & res]
  (let [res (seq (filter identity res))]
    (when (and n m res)
      (n2m n m (apply cg res)))))

(defn n2m-ncg
  "[ncg] then [n2m]."
  [nm n m & res]
  (let [res (seq (filter identity res))]
    (when (and (not (s/blank? nm)) n m res)
      (n2m n m (apply (partial ncg nm) res)))))


;; ALTERNATIVES

(defn alt
  "Returns a regex that will match any one of `res`, via alternation. Does not
  perform any grouping of the elements in `res` - for that use [alt-grp]."
  [& res]
  (let [res (seq (filter identity res))]
    (when res
     (apply join (interpose "|" (filter identity res))))))

(defn alt-grp
  "[grp] on each element in `res`, then [alt]."
  [& res]
  (let [res (seq (filter identity res))]
    (when res
      (apply alt (map #(join "(?:" % ")") (filter identity res))))))

; Note: capturing group versions don't make sense for alt


;; INCLUSIVE OR

(defn and'
  "Returns an 'and' regex that will match `a` and `b` in any order, and with the
  `s`eparator regex (if provided) between them.  This is implemented as
  `ASB|BSA`, which means that A and B must be distinct (must not match each
  other).  Does not perform any grouping, ether on `a`, `b`, or `s` - for that
  use [and-grp]."
  ([a b] (and' a b nil))
  ([a b s]
   (when (and a b)
     (alt (join a s b)
          (join b s a)))))

(defn and-grp
  "[grp] around `a`, `b` and `s`, then [and']."
  ([a b] (and-grp a b nil))
  ([a b s]
   (when (and a b)
     (alt (grp a s b)
          (grp b s a)))))

; Note: capturing group versions don't make much sense for 'and' - a more
; typical pattern would be to wrap the entire expression in a (single) capturing
; group

(defn or'
  "Returns an 'inclusive or' regex that will match `a` or `b`, or both, in any
  order, and with the `s`eparator regex (if provided) between them.  This is
  implemented as `ASB|BSA|A|B`, which means that A and B must be distinct (must
  not match each other).  Does not perform any grouping, either on `a`, `b`, or
  `s` - for that use [or-grp]."
  ([a b] (or' a b nil))
  ([a b s]
   (when (and a b)
     (alt (join a s b)
          (join b s a)
          a
          b))))

(defn or-grp
  "[grp] around `a`, `b` and `s`, then [or']."
  ([a b] (or-grp a b nil))
  ([a b s]
   (when (and a b)
     (alt (grp a s b)
          (grp b s a)
          (grp a)
          (grp b)))))

; Note: capturing group versions don't make much sense for 'or' - a more
; typical pattern would be to wrap the entire expression in a (single) capturing
; group
