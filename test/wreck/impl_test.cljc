;
; Copyright © 2025 Peter Monks
;
; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.
;
; SPDX-License-Identifier: MPL-2.0
;

(ns wreck.impl-test
  (:require [wreck.api    :refer [=']]
   #?(:clj  [clojure.test :refer [deftest testing is]]
      :cljs [cljs.test    :refer-macros [deftest testing is]])
            [wreck.impl   :refer [regex? raw-flags flags set-flags]]))

#?(:cljs (enable-console-print!))

; Important note: because of the way reader conditionals work, regexes in _ALL_
; branches _ALWAYS_ get compiled on _ALL_ hosts. This means that platform-
; specific regexes will cause compilation errors on the _OTHER_ platform.  To
; get around this, we use (re-pattern) to move regex compilation from read time
; to runtime (which will always take reader conditionals into account).
; This is a niche corner case, and only due to the Clojure reader pre-dating
; reader conditionals by a lot.

; Because of raw-flags' platform-specific behaviour, it's easier to just check for a result
(defn has-flags?
  [re]
  (boolean (raw-flags re)))

(deftest has-flags?-tests
  (testing "Basic cases - nil, not a regex object, etc."
    (is (false? (has-flags? nil)))
    (is (false? (has-flags? "")))
    (is (false? (has-flags? 0))))
  (testing "Regexes without flags"
    (is (false? (has-flags? #".*")))
#?(:clj  (is (false? (has-flags? (java.util.regex.Pattern/compile "ab"))))
   :cljs (is (false? (has-flags? (doto (js/RegExp.) (.compile "ab"))))))
#?(:clj  (is (false? (has-flags? (java.util.regex.Pattern/compile ".+")))))
#?(:clj  (is (false? (has-flags? #"(?i:a)")))))  ; embedded flag in a non-capturing group - JVM doesn't count this as a "flag" for some inconsistent reason
  (testing "Regexes with programmatic flags"
#?(:clj  (is (true? (has-flags? (java.util.regex.Pattern/compile "ab" java.util.regex.Pattern/CASE_INSENSITIVE))))
   :cljs (is (true? (has-flags? (doto (js/RegExp.) (.compile "ab" "i")))))))
#?(:clj
  (testing "JVM specific regexes with embedded flags"
    (is (true?  (has-flags? #"(?i)ab")))          ; Inconvenient, but ok I guess...
    (is (false? (has-flags? #"(?-i)ab")))         ; Ok I guess...
    (is (true?  (has-flags? #"a(?i)b")))          ; wat
    (is (false? (has-flags? #"(?i)a(?-i)b"))))))  ; watman?!?

(deftest flags-tests
  (testing "Basic cases - nil, not a regex object, etc."
    (is (nil? (flags nil)))
    (is (nil? (flags "")))
    (is (nil? (flags 0))))
  (testing "Regexes without flags"
    (is (nil? (flags #".*")))
    (is (nil? (flags #"(?im:a)"))))  ; Embedded flags in a non-capturing group don't "count" as a flag
  (testing "Flags"
    (is (= "i"  (flags #"(?i)ab")))
    (is (= "im" (flags #"(?mi)ab"))))  ; Test sorting
#?(:clj
  (testing "JVM specific cases"
    (is (nil?        (flags (java.util.regex.Pattern/compile "ab"))))
    (is (= "i"       (flags (re-pattern "a(?i)b"))))  ; embedded flag partway through a regex
    (is (= "i"       (flags (java.util.regex.Pattern/compile "ab" java.util.regex.Pattern/CASE_INSENSITIVE))))
    (is (= "iu"      (flags (java.util.regex.Pattern/compile "ab" (+ java.util.regex.Pattern/CASE_INSENSITIVE  java.util.regex.Pattern/UNICODE_CASE)))))
    (is (= "Udimsux" (flags (java.util.regex.Pattern/compile "a+" (+ java.util.regex.Pattern/UNIX_LINES
                                                                     java.util.regex.Pattern/CASE_INSENSITIVE
                                                                     java.util.regex.Pattern/COMMENTS
                                                                     java.util.regex.Pattern/MULTILINE
                                                                     java.util.regex.Pattern/DOTALL
                                                                     java.util.regex.Pattern/UNICODE_CASE
                                                                     java.util.regex.Pattern/UNICODE_CHARACTER_CLASS)))))
    (is (nil?        (flags (java.util.regex.Pattern/compile "ab" java.util.regex.Pattern/LITERAL))))
    (is (nil?        (flags (java.util.regex.Pattern/compile "ab" java.util.regex.Pattern/CANON_EQ))))
    (is (nil?        (flags (java.util.regex.Pattern/compile "ab" (+ java.util.regex.Pattern/LITERAL java.util.regex.Pattern/CANON_EQ)))))
    (is (= "i"       (flags (java.util.regex.Pattern/compile "ab" (+ java.util.regex.Pattern/CASE_INSENSITIVE java.util.regex.Pattern/LITERAL)))))
    (is (= "Udimsux" (flags (java.util.regex.Pattern/compile "a+" (+ java.util.regex.Pattern/UNIX_LINES
                                                                     java.util.regex.Pattern/CASE_INSENSITIVE
                                                                     java.util.regex.Pattern/COMMENTS
                                                                     java.util.regex.Pattern/MULTILINE
                                                                     java.util.regex.Pattern/LITERAL
                                                                     java.util.regex.Pattern/DOTALL
                                                                     java.util.regex.Pattern/UNICODE_CASE
                                                                     java.util.regex.Pattern/CANON_EQ
                                                                     java.util.regex.Pattern/UNICODE_CHARACTER_CLASS)))))) ; ⚠️ footgun: flags with no embedded alternative are silently dropped
:cljs
  (testing "JavaScript specific cases"
    (is (nil?        (flags (doto (js/RegExp.) (.compile "ab")))))
    (is (= "i"       (flags (doto (js/RegExp.) (.compile "ab" "i")))))
    (is (= "im"      (flags (doto (js/RegExp.) (.compile "ab" "mi")))))
    (is (= "dgimsuy" (flags (doto (js/RegExp.) (.compile ".*" "msigduy")))))     ; Ensure flag sorting
    (is (= "dgimsvy" (flags (doto (js/RegExp.) (.compile ".*" "gmvsidy"))))))))  ; Ensure flag sorting

(deftest set-flags-tests
  (testing "Basic cases - nil etc."
    (is (nil?           (set-flags nil   nil)))
    (is (nil?           (set-flags nil   "i")))
    (is (=' #".*"       (set-flags #".*" nil)))
    (is (=' #"(?i:.*)"  (set-flags #".*" "i")))
    (is (=' #"(?im:.*)" (set-flags #".*" "mi"))))
#?(:clj
  (testing "JVM specific cases"
    (is (=' (re-pattern "(?Udimsux:.*)") (set-flags #".*" "xdsiUmu"))))  ; Ensure flag sorting
:cljs
  (testing "JavaScript specific cases"
    (is (=' (re-pattern "(?i:.*)") (set-flags #".*" "i")))
    (is (=' (re-pattern "(?m:.*)") (set-flags #".*" "m")))
    (is (=' (re-pattern "(?s:.*)") (set-flags #".*" "s")))
    (is (=' (re-pattern "(?ims:.*)") (set-flags #".*" "smi")))    ; Ensure flag sorting
    (is (thrown? js/SyntaxError (set-flags #".*" "d")))           ; Ensure non-embeddable flags pass through to the JS regex engine, which then throws
    (is (thrown? js/SyntaxError (set-flags #".*" "g")))           ; Ensure non-embeddable flags pass through to the JS regex engine, which then throws
    (is (thrown? js/SyntaxError (set-flags #".*" "u")))           ; Ensure non-embeddable flags pass through to the JS regex engine, which then throws
    (is (thrown? js/SyntaxError (set-flags #".*" "v")))           ; Ensure non-embeddable flags pass through to the JS regex engine, which then throws
    (is (thrown? js/SyntaxError (set-flags #".*" "y")))           ; Ensure non-embeddable flags pass through to the JS regex engine, which then throws
    (is (thrown? js/SyntaxError (set-flags #".*" "usgiydm")))     ; Ensure non-embeddable flags pass through to the JS regex engine, which then throws
    (is (thrown? js/SyntaxError (set-flags #".*" "vsgiydm"))))))  ; Ensure non-embeddable flags pass through to the JS regex engine, which then throws
