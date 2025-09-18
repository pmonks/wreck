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
    exceptions if the resulting regex is syntactically invalid.
    * On the JVM, these will typically be instances of the
      `java.util.regex.PatternSyntaxException` class.
    * On JavaScript, these will typically be a `js/SyntaxError`.
  * Platform specific behaviour is particularly notable for short / empty
    regexes, such as `#\"{}\"` (an error on the JVM, fine but
    nonsensical on JS) and `#\"{1}\"` (ironically, fine but nonsensical on the
    JVM, but an error on JS).  🤡
  * Furthemore, JavaScript fundamentally doesn't support lossless round-tripping
    of `RegExp` objects to `String`s and back, something this library relies
    upon and does extensively.  The library makes a best effort to correct
    JavaScript's problematic implementation, but because it's fundamentally
    lossy there are some cases that (on ClojureScript only) may change your
    regexes in unexpected (though _probably_ not semantically significant) ways.
  * Regex flags (which aren't natively supported by Clojure's regex literals, so
    may be uncommon) are supported to the best ability of the library, but
    please carefully review the usage notes in the
    [README.md](https://github.com/pmonks/wreck?tab=readme-ov-file#wreck---the-whacky-regular-expression-construction-kit)
    for various caveats, especially in ClojureScript."
  (:require [clojure.string :as s]
   #?(:cljs [goog.object])))


;; FUNDAMENTAL PRIMITIVES

#?(:cljs
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
(defn- js-re-str
  "`clojure.core/str` but with better support for JavaScript's appalling RegExp
  class.

  Note:

  * Silently drops any flags in the regex."
  [o]
  (when o
    (if (not= js/RegExp (type o))
      (str o)
      (let [src (goog.object/get o "source")]  ; Remove leading and trailing "/" (inserted by JavaScript's idiotic RegExp class)
        (-> src
            (s/replace "(?:)" "")              ; Remove redundant capturing groups (inserted by JavaScript's idiotic RegExp class when a regex is blank)
            (s/replace "\\/"  "/")))))))       ; Remove redundant escapings of "/" (inserted by JavaScript's idiotic RegExp class)

(defn raw-flags
  "Returns the raw, platform specific flags in `re`. On the JVM this is an
  `int`, on JavaScript this is a `String`.  If `re` has no flags, or `re` is not
  a regex, returns `nil`.

  ⚠️ Because this function has platform specific behaviour, it is _strongly_
  recommended that callers use [[flags]] instead (that function is _not_
  platform specific, at least in its contract).  The one reasonable exception to
  this guideline is on the JVM, in the narrow case where a caller needs to check
  for a non-embeddable flag (as of JVM 25, `LITERAL` and `CANON_EQ`) - in that
  case [[flags]] throws, which may be a hindrance.

  Notes:

  * the JVM considers _some but not all_ embedded flags as flags. See the unit
    tests for details."
  [re]
#?(:clj
  (when (= (type re) java.util.regex.Pattern)
    (let [f (.flags ^java.util.regex.Pattern re)]
      (if (= f 0)
        nil
        f)))
   :cljs
  (when (= (type re) js/RegExp)
    (let [f (goog.object/get re "flags")]  ; Note: JavaScript always returns flags in sorted order, regardless of their order at RegExp construction time
      (if (= f "")
        nil
        f)))))

(defn has-flags?
  "Does `re` have any flags?

  Notes:

  * returns `false` if `re` is not a regex
  * the JVM considers _some but not all_ embedded flags as flags. See the unit
    tests for details."
  [re]
  (boolean (raw-flags re)))

#?(:clj
(def ^:private flag->embedded-char {
  java.util.regex.Pattern/UNIX_LINES              \d     ; 1
  java.util.regex.Pattern/CASE_INSENSITIVE        \i     ; 2
  java.util.regex.Pattern/COMMENTS                \x     ; 4
  java.util.regex.Pattern/MULTILINE               \m     ; 8
  java.util.regex.Pattern/LITERAL                 nil    ; 16   ; No embedded equivalent
  java.util.regex.Pattern/DOTALL                  \s     ; 32
  java.util.regex.Pattern/UNICODE_CASE            \u     ; 64
  java.util.regex.Pattern/CANON_EQ                nil    ; 128  ; No embedded equivalent
  java.util.regex.Pattern/UNICODE_CHARACTER_CLASS \U}))  ; 256

(defn flags
  "Returns the flags for `re` as a set of characters, or `nil` if `re` doesn't
  have any or is not a regex.

  Notes:

  * on the JVM, flags that don't have an embedded equivalent (as of JVM 25,
    `LITERAL` and `CANON_EQ`) will cause an `ex-info` to be thrown. If you
    specifically need to handle these flags, [[raw-flags]] may be useful but it
    should only be used as a last resort as its behaviour is platform specific.
  * the JVM considers _some but not all_ embedded flags as flags. See the unit
    tests for details."
  [re]
  (when-let [flgs (raw-flags re)]
#?(:cljs
      ; JavaScript flags are a string, so we can just seq it directly
    (set (seq flgs))
   :clj
    ; JVM flags are a bit set (int), so we have to manually determine the characters
    (let [chs (filter identity
                      (map (fn [[bit ch]]
                             (when-not (zero? (bit-and flgs bit))
                               (or ch (throw (ex-info "regex has flag with no embedded equivalent"
                                                      (merge {:regex         re
                                                              :flags         flgs
                                                              :problem-flag  bit}
                                                             (case (int bit)
                                                               16  {:problem-flag-name "LITERAL"}
                                                               128 {:problem-flag-name "CANON_EQ"}
                                                               {:problem-flag-name "unknown"})))))))
                           flag->embedded-char))]
      (set chs)))))

(defn set-flags
  "Sets the flags on `re` to `flgs` (a set of flag characters, such as those
  returned by [[flags]] - may also be `nil` to strip all flags), returning a new
  regex.  All existing flags in `re` are replaced.  Returns `nil` if `re` is
  `nil`.

  ⚠️ Because this function has platform specific behaviour, its use is
  discouraged.

  On the JVM, it's instead recommended to manually place flags in a non-
  capturing group that wraps the relevant regex (or fragment) since that gives
  explicit control over how multiple regexes with different flag sets compose
  together.  For example:

    `#\"(?i:[abc]+)\"`

  On JavaScript there's no choice - JavaScript's regex engine doesn't support
  embedded flags and they always apply globally.  It is therefore recommended to
  keep flags out of regex fragments used for composition entirely, and only
  apply flags (if needed) globally to the final, fully composed regex.

  Note:

  * Throws if `flgs` contains invalid flag characters.
  * On the JVM, all programmatic AND embedded flags in the regex will be
    removed, except embedded flags that appear in a non-capturing group (those
    will be retained, since the JVM doesn't consider them to be 'flags').
  * On the JVM, the flags will be set via a non-capturing group at the start of
    the regex that encloses the entire thing.  This ensures that regexes with
    flags can be safely combined with other regexes with different flags, with
    correct scoping of each regex's flags.  It also means that flags do _not_
    round-trip between [[flags]] and [[set-flags]] (unlike on JavaScript).
  * On JavaScript, the flags will be set programmatically (i.e. globally for the
    entire regex), since JavaScript's regex engine doesn't support embedded
    flags of any kind (and therefore flags can't be scoped to subsets of a
    regex).  This is obviously a problem if you're trying to compose regexes
    that have mutually exclusive flags."
  [re flgs]
  (when re
    ; Notes:
    ; * We sort the flags before joining, to ensure consistent flag strings (important for equality)
#?(:clj
    (let [raw-re (s/replace (str re) #"\(\?[Udimsux]+\)" "")]  ; Strip any embedded flags
      (if-let [flgs (seq flgs)]
        (re-pattern (str "(?" (s/join (sort flgs)) ":" raw-re ")"))
        (re-pattern raw-re)))
   :cljs
    (let [raw-re (js-re-str re)]
      (if-let [flgs (seq flgs)]
        (doto (js/RegExp.)
              (.compile raw-re (s/join (sort flgs))))
        (re-pattern raw-re))))))

#?(:clj
(defn embed-flags
  "Embeds any flags found in `re` at the start of `re` in a non-capturing group
  (to ensure scoping), returning a new regex.  Returns `re` if `re` contains no
  flags or is `nil`.

  For example `#\"(?i)[abc]+\"` would become `#\"(?i:[abc]+)\"`.

  Note:

  * This function is only available on the JVM. JavaScript's regex engine does
    not support embedded flags.
  * Embedded flags in the middle of `re` will be moved to the beginning of the
    regex.  This may alter the semantics of the regex - for example `a(?i)b`
    will become `(?i:ab)`, which means that `a` will be matched case-
    insensitively by the result, which is _not_ the same as the original (which
    matches lower-case `a` only). This is an unavoidable consequence of how the
    JVM regex engine reports embedded flags (arguably it shouldn't report them
    as flags at all...).  If you really need to use an embedded flag midway
    through a regex, put the flag in a non-capturing group e.g. `a(?i:b) as this
    usage will be preserved."
  [re]
  (if-let [flgs (flags re)]
    (set-flags re flgs)
    re)))

(defn str'
  "Returns the `String` representation of `o`, with special handling for
  `RegExp` objects on ClojureScript in an attempt to correct JavaScript's
  **APPALLING** default stringification.

  Notes:

  * On the JVM will embed all flags (as per [[embed-flags]]).
  * On JavaScript this will silently drop flags.  You may use [[flags]] and
    [[set-flags]] in combination to preserve flags if needed, but note that
    JavaScript only supports global flags - unlike the JVM there is no way to
    scope flags to subsets of a regex."
  [o]
  (when o
#?(:clj
    (-> o
        embed-flags
        str)
  :cljs
      (js-re-str o))))

(defn ='
  "Equality for regexes, defined by having equal `String` representations (as
  per [[str']]) and flags (as per [[flags]]).  This means that _equivalent_
  regexes (e.g. `#\"...\"` and `#\".{3}\"` will _not_ be considered equal."
  ([_] true)
  ([re1 re2]
#?(:clj
   (= (str' re1) (str' re2))  ; On the JVM str' embeds flags, so we don't need a separate condition to handle them
:cljs
   (and (= (str' re1)      (str' re2))
        (= (raw-flags re1) (raw-flags re2)))))
  ([re1 re2 & more]
   (if (=' re1 re2)
     (if (next more)
       (recur re2 (first more) (rest more))
       (=' re2 (first more)))
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
  "Quotes `re` (anything that can be accepted by [[join]]), returning a regex."
  [re]
  (when re
    (join "\\Q" re "\\E")))

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


#?(:clj
(defn flags-grp
  "As for [[grp], but prefixes the group with `flgs` (a set of regex flag
  characters, such as those returned by [[flags]]).  See ['special constructs'
  in the `java.util.regex.Pattern` JavaDoc](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/regex/Pattern.html#special)
  for the set of valid flag characters.

  Notes:

  * This function is only available on the JVM. JavaScript's regex engine does
    not support embedded flags."
  [flgs & res]
  (if-not (seq flgs)
    (apply grp res)  ; Default to grp if no flags provided
    (when-let [res (seq (filter identity res))]
      ; Here we optimise out an empty non-capturing group
      (let [exp (apply join res)]
        (if (empty?' exp)
          #""
          (join "(?" (s/join (sort flgs)) ":" exp ")")))))))

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

  * Unlike most other `-cg` fns, this one does _not_ accept any number of res.
  * May optimise the expression (via de-duplication in [[alt]])."
  ([a b] (and-cg a b nil))
  ([a b s]
   (cg (and' a b s))))

(defn and-ncg
  "[[and']] then [[ncg]].

  Notes:

  * Unlike most other `-ncg` fns, this one does _not_ accept any number of res.
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

  * Unlike most other `-cg` fns, this one does _not_ accept any number of res.
  * May optimise the expression (via de-duplication in [[alt]])."
  ([a b] (or-cg a b nil))
  ([a b s]
   (cg (or' a b s))))

(defn or-ncg
  "[[or']] then [[ncg]].

  Notes:

  * Unlike most other `-ncg` fns, this one does _not_ accept any number of res.
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

  * Unlike most other `-cg` fns, this one does _not_ accept any number of res.
  * May optimise the expression (via de-duplication in [[alt]])."
  [a b]
  (cg (xor' a b)))

(defn xor-ncg
  "[[xor']] then [[ncg]].

  Notes:

  * Unlike most other `-ncg` fns, this one does _not_ accept any number of res.
  * May optimise the expression (via de-duplication in [[alt]])."
  [nm a b]
  (ncg nm (xor' a b)))
