;
; Copyright © 2025 Peter Monks
;
; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.
;
; SPDX-License-Identifier: MPL-2.0
;

(ns wreck.impl
  "Internal implementation details of wreck.  This is not part of the public API
  of wreck and is subject to change without notice."
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
(defn raw-str
  "`clojure.core/str` but with better support for JavaScript's appalling RegExp
  class.

  Note:

  * Ignores all flags in the regex."
  [o]
  (when o
#?(:clj
     (str o)  ; No special handling needed on the JVM
:cljs
    (if (not= js/RegExp (type o))
      (str o)
      (let [src (goog.object/get o "source")]  ; Remove leading and trailing "/" (inserted by JavaScript's idiotic RegExp class)
        (-> src
            (s/replace "(?:)" "")              ; Remove redundant non capturing groups (inserted by JavaScript's idiotic RegExp class when a regex is blank)
            (s/replace "\\/"  "/")))))))       ; Remove redundant escapings of "/" (inserted by JavaScript's idiotic RegExp class)

(defn raw-flags
  "Returns the raw, platform specific flags in `re`. On the JVM this is an
  `int`, on JavaScript this is a `String`.  If `re` has no flags, or `re` is not
  a regex, returns `nil`."
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

#?(:clj
(def flag->embedded-char {
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
  "Returns the flags for `re` as a `String`, or `nil` if `re` doesn't have any
  or is not a regex.

  Notes:

  * on the JVM, flags that don't have an embedded equivalent (as of JVM 25,
    `LITERAL` and `CANON_EQ`) will be silently dropped.  Use
    [[has-non-embeddable-flags?]] if you need to check for this."
  [re]
  (when-let [flgs (raw-flags re)]
#?(:cljs
      ; JavaScript flags are a string in sorted order, so we can just return it directly
    flgs
   :clj
    ; JVM flags are a bit set (int), so we have to manually determine the characters
    (when-let [chs (seq (filter identity
                                (map (fn [[bit ch]] (when-not (zero? (bit-and flgs bit)) ch))
                                     flag->embedded-char)))]
      (s/join (sort chs))))))

(defn set-flags
  "Sets the flags on `re` to `flgs` (a `String`, or `nil` to strip all flags),
  returning a new regex.  All existing flags in `re` are replaced.  Returns
  `nil` if `re` is `nil`.  Throws if invalid flag characters are provided."
  [re flgs]
  (when re
    ; Strip any ungrouped embedded flags
    (let [raw-re #?(:clj  (s/replace (str re)     #"\(\?[Udimsux]+\)+" "")
                    :cljs (s/replace (raw-str re) #"^\(\?[Udimsux]+\)" ""))]
      (if-not (s/blank? flgs)
        ; * We sort the flags before joining, to ensure consistent flag strings (important for equality)
        (re-pattern (str "(?" (s/join (sort (distinct (seq flgs)))) ":" raw-re ")"))
        (re-pattern raw-re)))))

