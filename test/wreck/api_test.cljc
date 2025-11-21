;
; Copyright © 2025 Peter Monks
;
; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.
;
; SPDX-License-Identifier: MPL-2.0
;

(ns wreck.api-test
  (:require [clojure.string   :as s]
   #?(:clj  [clojure.test     :refer [deftest testing is]]
      :cljs [cljs.test        :refer-macros [deftest testing is]])
   #?(:clj  [wreck.test-utils :refer [time-execution]]
      :cljs [wreck.test-utils :refer-macros [time-execution]])
            [wreck.api        :refer [has-non-embeddable-flags? embed-flags  regex?
                                      str' =' empty?' join esc qot
                                               grp     cg     ncg     fgrp     chcl
                                      opt  opt-grp opt-cg opt-ncg opt-fgrp opt-chcl
                                      zom  zom-grp zom-cg zom-ncg zom-fgrp zom-chcl
                                      oom  oom-grp oom-cg oom-ncg oom-fgrp oom-chcl
                                      nom  nom-grp nom-cg nom-ncg nom-fgrp nom-chcl
                                      exn  exn-grp exn-cg exn-ncg exn-fgrp exn-chcl
                                      n2m  n2m-grp n2m-cg n2m-ncg n2m-fgrp n2m-chcl
                                      alt  alt-grp alt-cg alt-ncg alt-fgrp
                                      and' and-grp and-cg and-ncg and-fgrp
                                      or'  or-grp  or-cg  or-ncg  or-fgrp
                                      xor' xor-grp xor-cg xor-ncg xor-fgrp]]))

; Important note: because of the way reader conditionals work, regexes in _ALL_
; branches _ALWAYS_ get compiled on _ALL_ hosts. This means that platform-
; specific regexes will cause compilation errors on the _OTHER_ platform.  To
; get around this, we use (re-pattern) to move regex compilation from read time
; to runtime (which only executes _after_ reader conditionals have been taken
; into account). This is a niche corner case, and only due to the Clojure reader
; pre-dating reader conditionals (by a lot).

#?(:cljs (enable-console-print!))

(deftest has-non-embeddable-flags?-tests
  (testing "Basic cases - nil, blank, not a regex etc."
    (is (false? (has-non-embeddable-flags? nil)))
    (is (false? (has-non-embeddable-flags? "")))
    (is (false? (has-non-embeddable-flags? " ")))
    (is (false? (has-non-embeddable-flags? 2)))
    (is (false? (has-non-embeddable-flags? 2.0)))
    (is (false? (has-non-embeddable-flags? true)))
    (is (false? (has-non-embeddable-flags? false))))
  (testing "Regexes without non-embeddable flags"
    (is (false? (has-non-embeddable-flags? #".*")))
#?(:clj  (is (false? (has-non-embeddable-flags? (java.util.regex.Pattern/compile "ab"))))
   :cljs (is (false? (has-non-embeddable-flags? (doto (js/RegExp.) (.compile "ab"))))))
    (is (false? (has-non-embeddable-flags? #"(?i:.*)")))
#?(:clj  (is (false? (has-non-embeddable-flags? (java.util.regex.Pattern/compile "ab" java.util.regex.Pattern/CASE_INSENSITIVE))))
   :cljs (is (false? (has-non-embeddable-flags? (doto (js/RegExp.) (.compile "ab" "i")))))))
  (testing "Regexes with non-embeddable flags"
#?(:clj  (is (true? (has-non-embeddable-flags? (java.util.regex.Pattern/compile "ab" java.util.regex.Pattern/CANON_EQ))))
   :cljs (is (true? (has-non-embeddable-flags? (doto (js/RegExp.) (.compile "ab" "y")))))))

  )

(deftest embed-flags-tests
  (testing "Basic cases - nil etc."
    (is (=' #""   (embed-flags nil)))
    (is (=' #""   (embed-flags #"")))
    (is (=' #".*" (embed-flags #".*"))))
  (testing "Ungrouped embedded flags (note: this is emulated by ClojureScript and NOT supported by native JavaScript!)"
    (is (=' #"(?i:ab)"   (embed-flags #"(?i)ab")))
    (is (=' #"(?ims:ab)" (embed-flags #"(?smi)ab"))))
#?(:clj
  (testing "JVM specific cases"
    (is (=' #"(?ims:ab)"                 (embed-flags (re-pattern "(?s)(?i)(?m)ab"))))
    (is (=' #"(?i:ab)"                   (embed-flags (re-pattern "a(?i)b"))))  ; ⚠️ footgun: this changes the semantics of the regex
    (is (=' #"(?i:ab)"                   (embed-flags (java.util.regex.Pattern/compile "ab" java.util.regex.Pattern/CASE_INSENSITIVE))))
    (is (=' #"ab"                        (embed-flags (java.util.regex.Pattern/compile "ab" java.util.regex.Pattern/CANON_EQ))))  ; ⚠️ footgun: non-embeddable flag is silently dropped
    (is (=' (re-pattern "(?Udimsux:ab)") (embed-flags (java.util.regex.Pattern/compile "ab" (+ java.util.regex.Pattern/UNIX_LINES
                                                                                               java.util.regex.Pattern/CASE_INSENSITIVE
                                                                                               java.util.regex.Pattern/COMMENTS
                                                                                               java.util.regex.Pattern/MULTILINE
                                                                                               java.util.regex.Pattern/DOTALL
                                                                                               java.util.regex.Pattern/UNICODE_CASE
                                                                                               java.util.regex.Pattern/UNICODE_CHARACTER_CLASS))))))
:cljs
  (testing "JavaScript specific cases"
    (is (=' #"(?i:ab)"               (embed-flags (doto (js/RegExp.) (.compile "ab" "i")))))
    (is (=' (re-pattern "(?ims:ab)") (embed-flags (doto (js/RegExp.) (.compile "ab" "msiydgv")))))     ; ⚠️ footgun: non-embeddable flags are silently dropped
    (is (=' (re-pattern "(?ims:ab)") (embed-flags (doto (js/RegExp.) (.compile "ab" "ydsiumg"))))))))  ; ⚠️ footgun: non-embeddable flags are silently dropped

(deftest regex?-tests
  (testing "Not regexes"
    (is (false? (regex? nil)))
    (is (false? (regex? "")))
    (is (false? (regex? \a)))
    (is (false? (regex? 0)))
    (is (false? (regex? 0.0)))
    (is (false? (regex? true)))
    (is (false? (regex? false))))
  (testing "Regexes"
    (is (true? (regex? #".+")))
#?(:clj  (is (true? (regex? (java.util.regex.Pattern/compile "ab"))))
   :cljs (is (true? (regex? (doto (js/RegExp.) (.compile "ab"))))))))

(deftest str'-tests
  (testing "Basic cases"
    (is (= " "                     (str' (re-pattern " "))))
    (is (= "foo"                   (str' (re-pattern "foo"))))
    (is (= "foobar"                (str' (re-pattern "foobar"))))
    (is (= "foo|bar"               (str' (re-pattern "foo|bar"))))
    (is (= "(foo|bar)"             (str' (re-pattern "(foo|bar)"))))
    (is (= "(?:foo|bar)"           (str' (re-pattern "(?:foo|bar)"))))
    (is (= "(?<groupName>foo|bar)" (str' (re-pattern "(?<groupName>foo|bar)")))))
  (testing "Messed up cases (due to the JavaScript RegExp class's idiotic stringification)"
    (is  (= "foo/bar"              (str' (re-pattern "foo/bar"))))
#?(:clj  (is (= "foo\\/bar"        (str' (re-pattern "foo\\/bar"))))   ; JVM is sane
   :cljs (is (= "foo/bar"          (str' (re-pattern "foo\\/bar")))))  ; JavaScript is 🤡🤡🤡
    (is  (= ""                     (str' (re-pattern ""))))
#?(:clj  (is (= "(?:)"             (str' (re-pattern "(?:)"))))        ; JVM is sane
   :cljs (is (= ""                 (str' (re-pattern "(?:)")))))       ; JavaScript is 🤡🤡🤡
    (is  (= "foo/bar/blah"         (str' (re-pattern "foo/bar/blah")))))
  (testing "Flags"
    (is (= "(?i:a+b)"              (str' #"(?i)a+b")))
    (is (= "(?ims:a+b)"            (str' #"(?sim)a+b")))) ; Test sorting of flags that are common to both JVM and JS
#?(:clj
  (testing "JVM specific cases"
    (is (= "(?im:ab)"            (str' (re-pattern "(?m)(?i)ab"))))                            ; JavaScript doesn't support separate embedded flags like this
    (is (= "(?Udimsux:a+b)"      (str' (re-pattern "(?xsiUmdu)a+b"))))                         ; Test sorting of flags
    (is (= "(?i:a+b)"            (str' (re-pattern "a+(?i)b"))))                               ; ⚠️ footgun: this changes the semantics of the regex
    (is (= "(?Udimsux:abcdefgh)" (str' (re-pattern "a(?x)b(?s)c(?i)d(?U)e(?m)f(?d)g(?u)h"))))  ; ⚠️ footgun: this changes the semantics of the regex
    (is (= "(?i:a+)"             (str' (java.util.regex.Pattern/compile "a+" java.util.regex.Pattern/CASE_INSENSITIVE))))
    (is (= "(?iu:a+)"            (str' (java.util.regex.Pattern/compile "a+" (+ java.util.regex.Pattern/CASE_INSENSITIVE java.util.regex.Pattern/UNICODE_CASE)))))
    (is (= "(?Udimsux:a+)"       (str' (java.util.regex.Pattern/compile "a+" (+ java.util.regex.Pattern/UNIX_LINES
                                                                                java.util.regex.Pattern/CASE_INSENSITIVE
                                                                                java.util.regex.Pattern/COMMENTS
                                                                                java.util.regex.Pattern/MULTILINE
                                                                                java.util.regex.Pattern/DOTALL
                                                                                java.util.regex.Pattern/UNICODE_CASE
                                                                                java.util.regex.Pattern/UNICODE_CHARACTER_CLASS))))))
:cljs
  (testing "JavaScript specific cases"
    (is (= "(?i:a+)"   (str' (doto (js/RegExp.) (.compile "a+" "i")))))
    (is (= "(?is:a+)"  (str' (doto (js/RegExp.) (.compile "a+" "si")))))
    (is (= "(?ims:a+)" (str' (doto (js/RegExp.) (.compile "a+" "mivgsdy")))))     ; ⚠️ footgun: this changes the semantics of the regex
    (is (= "(?ims:a+)" (str' (doto (js/RegExp.) (.compile "a+" "miugsdy"))))))))  ; ⚠️ footgun: this changes the semantics of the regex

; How fast we expect the str' performance tests to complete.
; Note: we have to hedge out bets a little bit as GitHub actions VMs are slow.
(def performance-threshold-ms 2000)

(deftest str'-performance-tests
  (testing "Performance of str' (simplistic)"
    (let [re #".*"]
      (is (< (:time (time-execution (run! (fn [_] (str' re)) (range 1000000))) performance-threshold-ms )))
    (let [re #"(?im:.*)"]
      (is (< (:time (time-execution (run! (fn [_] (str' re)) (range 1000000))) performance-threshold-ms )))))))

(deftest ='-tests
  (testing "Equal"
    (is (true?  (=' #""   #"")))
    (is (true?  (=' #".*" #".*"))))
  (testing "Not equal"
    (is (false? (=' #"" #" ")))
    (is (false? (=' #"..." #".{3}"))))
  (testing "Flags"
    (is (true?  (=' #"(?i:a+)"              (embed-flags #"(?i)a+"))))
    (is (true?  (=' (embed-flags #"(?i)a+") (embed-flags #"(?i)a+"))))
    (is (false? (=' #"a+"                   (embed-flags #"(?i)a+")))))
#?(:clj
  (testing "JVM specific cases"
    (is (true?  (=' #"(?i:a+)" (embed-flags (java.util.regex.Pattern/compile "a+" java.util.regex.Pattern/CASE_INSENSITIVE)))))
    (is (true?  (=' #"(?i:a+)" (embed-flags #"(?i)a+") (embed-flags (java.util.regex.Pattern/compile "a+" java.util.regex.Pattern/CASE_INSENSITIVE)))))
    (is (true?  (=' (embed-flags #"(?i)ab") (embed-flags #"a(?i)b"))))  ; ⚠️ footgun: prior to embedding the flags, these regexes have different semantics
    (is (false? (=' #"(?i)ab" #"a(?i)b")))
    (is (false? (=' #"a+"      (java.util.regex.Pattern/compile "a+" java.util.regex.Pattern/CASE_INSENSITIVE))))
    (is (false? (=' (java.util.regex.Pattern/compile "a+" java.util.regex.Pattern/CASE_INSENSITIVE)
                    (java.util.regex.Pattern/compile "a+" (+ java.util.regex.Pattern/CASE_INSENSITIVE java.util.regex.Pattern/LITERAL))))))   ; Ensure all flags are considered in equality , even if they can't be embedded ; Ensure all flags are considered, even if we normally drop them
:cljs
  (testing "JavaScript specific cases"
    (is (true?  (=' (doto (js/RegExp.) (.compile "a+" "i")) (doto (js/RegExp.) (.compile "a+" "i")))))
    (is (true?  (=' (embed-flags #"(?i)a+")                 (embed-flags (doto (js/RegExp.) (.compile "a+" "i"))))))
    (is (false? (=' (embed-flags #"(?i)a+")                 (doto (js/RegExp.) (.compile "a+" "i")))))  ; ⚠️ footgun: once flags are embedded, these are identical
    (is (false? (=' #"a+"                                   (doto (js/RegExp.) (.compile "a+" "i")))))
    (is (false? (=' (doto (js/RegExp.) (.compile "a+" "i")) (doto (js/RegExp.) (.compile "ab" "ig")))))))  ; Ensure all flags are considered in equality , even if they can't be embedded
  (testing "Variable arguments"
    (is (true?  (=' #"")))
    (is (true?  (=' #"" #"")))
    (is (true?  (=' #"" #"" #"")))
    (is (true?  (=' #"" #"" #"" #"")))
    (is (true?  (=' #"" #"" #"" #"" #"" #"" #"" #"")))
    (is (true?  (=' #".*" #".*" #".*" #".*" #".*" #".*" #".*" #".*")))
    (is (false? (=' #"." #"" #"" #"" #"" #"" #"" #"")))
    (is (false? (=' #"" #"" #"" #"" #"" #"" #"" #".")))))

(deftest empty?'-tests
  (testing "empty?'"
    (is (true?  (empty?' nil)))
    (is (true?  (empty?' #"")))
    (is (true?  (empty?' (embed-flags #""))))
    (is (false? (empty?' (re-pattern "(?i)"))))  ; This shouldn't need the call to re-pattern, except for https://ask.clojure.org/index.php/14717/possible-clojurescript-corner-regex-literal-compilation
    (is (false? (empty?' #"(?i:a)")))
    (is (false? (empty?' #"(?im:)")))
    (is (false? (empty?' (embed-flags (re-pattern "(?im)")))))  ; This shouldn't need the call to re-pattern, except for https://ask.clojure.org/index.php/14717/possible-clojurescript-corner-regex-literal-compilation
    (is (false? (empty?' #" ")))
    (is (false? (empty?' #"a")))
    (is (false? (empty?' #".*")))
    (is (false? (empty?' #"(?:abc)+"))))
#?(:clj
  (testing "JVM specific cases"
    (is (false? (empty?' (java.util.regex.Pattern/compile "" java.util.regex.Pattern/CASE_INSENSITIVE)))))
:cljs
  (testing "JavaScript specific cases"
    (is (false? (empty?' (doto (js/RegExp.) (.compile "" "i"))))))))

(deftest join-tests
  (testing "join - nil, empty or blank"
    (is (=' #""   (join)))
    (is (=' #""   (join nil)))
    (is (=' #""   (join nil nil)))
    (is (=' #""   (join #"")))
    (is (=' #""   (join #"" #"")))
    (is (=' #"a"  (join nil nil nil nil #"a" nil nil)))
    (is (=' #"ab" (join nil #"a" nil nil #"b" nil nil nil nil))))
  (testing "join - regex"
    (is (=' #".*" (join #".*" #"")))
    (is (=' #".*" (join #"" #".*")))
    (is (=' #".*" (join #"" #"" #".*"))))
  (testing "join - other types"
    (is (=' #".*"   (join ".*" "")))
    (is (=' #".*"   (join "" ".*")))
    (is (=' #".*"   (join "" "" ".*")))
    (is (=' #".*"   (join "" "" ".*")))
    (is (=' #"123"  (join 1 2 3)))
#?(:clj  (is (=' #"2.0a" (join 2.0 "a")))    ; JVM is sane
   :cljs (is (=' #"2a"   (join 2.0 "a")))))  ; JavaScript is 🤡🤡🤡
  (testing "join - mixed types"
    (is (=' #"(.*)"                        (join "(" #".*" ")")))
    (is (=' #"Apache(\s+Software)?License" (join "Apache" #"(\s+Software)?" "License"))))
  (testing "join - nested"
    (is (=' #"Apache(\s+Software)?License(\s+v2\.0)?" (join "Apache" #"(\s+Software)?" "License" (join "(" #"\s+" (esc "v2.0") ")?"))))))

(deftest esc-tests
  (testing "esc - nil, empty or blank"
    (is (nil?       (esc nil)))
    (is (s/blank?   (esc "")))
    (is (s/blank?   (esc " ")))
    (is (s/blank?   (esc "\n")))
    (is (s/blank?   (esc "\r\n  \t"))))
  (testing "esc"
    (is (=' #"foo"  (esc "foo")))
    (is (=' #"\.\*" (esc ".*")))))

(deftest qot-tests
  (testing "qot - nil, empty or blank"
    (is (=' #""      (qot nil)))
    (is (=' #""      (qot "")))
    (is (=' #"\Q \E" (qot " "))))
    ; Note: whitespace literals (such as \n, \r and \t) act strangely inside Clojure regex literals, so we don't test with them
  (testing "qot"
    (is (=' #"\Qfoo\E" (qot "foo")))
    (is (=' #"\Q2\E" (qot 2)))
#?(:clj  (is (=' #"\Q2.0\E" (qot 2.0)))
   :cljs (is (=' #"\Q2\E" (qot 2.0))))  ; JavaScript is 🤡🤡🤡
    (is (=' #"\Qtrue\E" (qot true)))
    (is (=' #"\Qfoo\E" (qot #"foo")))  ; Technically quoting regexes is a Bad Idea™, but we test a simple example just in case
    (is (=' #"\Q.*\E"  (qot ".*")))))

(deftest basic-grouping-tests
  (testing "grp"
    (is (=' #""                                         (grp)))
    (is (=' #""                                         (grp nil)))
    (is (=' #""                                         (grp #"")))
    (is (=' #"(?:.*)"                                   (grp #".*")))
    (is (=' #"(?:.*)"                                   (grp #"" #".*")))
    (is (=' #"(?:foo.*)"                                (grp #"foo" #".*")))
    (is (=' #"(?:Apache(\s+Software)?(\s+Licen[cs]e)?)" (grp "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?"))))
  (testing "cg"
    (is (=' #"()"                                     (cg)))
    (is (=' #"()"                                     (cg nil)))
    (is (=' #"()"                                     (cg #"")))
    (is (=' #"(.*)"                                   (cg #".*")))
    (is (=' #"(.*)"                                   (cg #"" #".*")))
    (is (=' #"(foo.*)"                                (cg #"foo" #".*")))
    (is (=' #"(Apache(\s+Software)?(\s+Licen[cs]e)?)" (cg "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?"))))
  (testing "ncg"
    (is (=' #"()"                                              (ncg nil)))
    (is (=' #"()"                                              (ncg "")))
    (is (=' #"()"                                              (ncg "  ")))
    (is (=' #"()"                                              (ncg "\n")))
    (is (=' #"()"                                              (ncg "\n   \r\n  \t ")))
    (is (=' #"()"                                              (ncg nil nil)))
    (is (=' #"()"                                              (ncg "" nil)))
    (is (=' #"(.*)"                                            (ncg nil #".*")))
    (is (=' #"(.*)"                                            (ncg "" #".*")))
    (is (=' #"(?<groupName>)"                                  (ncg "groupName" #"")))
    (is (=' #"(?<groupName>.*)"                                (ncg "groupName" #".*")))
    (is (=' #"(?<groupName>.*)"                                (ncg "groupName" #"" #".*")))
    (is (=' #"(?<groupName>foo.*)"                             (ncg "groupName" #"foo" #".*")))
    (is (=' #"(?<apache>Apache(\s+Software)?(\s+Licen[cs]e)?)" (ncg "apache" "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?"))))
  (testing "fgrp"
    (is (=' #""                                           (fgrp nil)))
    (is (=' #""                                           (fgrp "")))
    (is (=' #""                                           (fgrp " ")))
    (is (=' #""                                           (fgrp "\n")))
    (is (=' #""                                           (fgrp "\n   \r\n  \t ")))
    (is (=' #""                                           (fgrp nil nil)))
    (is (=' #""                                           (fgrp "" nil)))
    (is (=' #""                                           (fgrp " " nil)))
    (is (=' #""                                           (fgrp "\n" nil)))
    (is (=' #""                                           (fgrp "\n   \r\n  \t " nil)))
    (is (=' #""                                           (fgrp nil #"")))
    (is (=' #"(?:.*)"                                     (fgrp nil #".*")))         ; Conversion to non capturing group
    (is (=' #"(?:abcd)"                                   (fgrp nil #"ab" #"cd")))   ; Conversion to non capturing group
    (is (=' #"(?:foo.*)"                                  (fgrp ""  #"foo" #".*")))  ; Conversion to non capturing group
    (is (=' #""                                           (fgrp "  " #"")))
    (is (=' #""                                           (fgrp "i" #"")))
    (is (=' #""                                           (fgrp "mi" #"")))
    (is (=' #"(?i:.*)"                                    (fgrp "i" #".*")))
    (is (=' #"(?im:.*)"                                   (fgrp "mi" #".*")))
    (is (=' #"(?im:Apache(\s+Software)?(\s+Licen[cs]e)?)" (fgrp "mi" "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?")))
    (is (thrown? #?(:clj  java.util.regex.PatternSyntaxException
                    :cljs js/SyntaxError)                 (fgrp "42" #".*"))))
  (testing "chcl"
    (is (=' #""                     (chcl)))
    (is (=' #""                     (chcl nil)))
    (is (=' #""                     (chcl "")))
    (is (=' #""                     (chcl #"")))
    (is (=' #"[ ]"                  (chcl " ")))
    (is (=' #"[ ]"                  (chcl #" ")))
    (is (=' #"[  ]"                 (chcl "  ")))
    (is (=' #"[  ]"                 (chcl #"  ")))
    (is (=' #"[abc]"                (chcl "abc")))
    (is (=' #"[abc]"                (chcl #"abc")))
    (is (=' #"[abc]"                (chcl "a" "b" "c")))
    (is (=' #"[a-z]"                (chcl "a-z")))
    (is (=' #"[\.\-\\]"             (chcl (esc ".-\\"))))  ; This combination of fn calls is likely to be common, so test it explicitly
    (is (=' #"[\p{Punct}]"          (chcl "\\p{Punct}")))  ; Note: while JavaScript "supports" this regex, it doesn't work as one might expect because the 🤡tastic JS regex engine doesn't support POSIX character classes
    (is (=' #"[\p{Punct}]"          (chcl #"\p{Punct}")))  ; ditto
    (is (=' #"[\p{Alpha}\p{Digit}]" (chcl #"\p{Alpha}" #"\p{Digit}")))))  ; ditto


(deftest opt-variant-tests
  (testing "opt"
    (is (=' #""        (opt nil)))
    (is (=' #""        (opt #"")))
    (is (=' #"x?"      (opt #"x")))
    (is (=' #".*?"     (opt #".*")))
    (is (=' #"foo?"    (opt #"foo")))
    (is (=' #"Apache?" (opt "Apache"))))
  (testing "opt-grp"
    (is (=' #""                                          (opt-grp)))
    (is (=' #""                                          (opt-grp nil)))
    (is (=' #""                                          (opt-grp #"")))
    (is (=' #"(?:x)?"                                    (opt-grp #"x")))
    (is (=' #"(?:.*)?"                                   (opt-grp #".*")))
    (is (=' #"(?:foo)?"                                  (opt-grp #"foo")))
    (is (=' #"(?:Apache)?"                               (opt-grp "Apache")))
    (is (=' #"(?:Apache(\s+Software)?(\s+Licen[cs]e)?)?" (opt-grp "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?"))))
  (testing "opt-cg"
    (is (=' #"()?"                                     (opt-cg)))
    (is (=' #"()?"                                     (opt-cg nil)))
    (is (=' #"()?"                                     (opt-cg #"")))
    (is (=' #"(x)?"                                    (opt-cg #"x")))
    (is (=' #"(.*)?"                                   (opt-cg #".*")))
    (is (=' #"(foo)?"                                  (opt-cg #"foo")))
    (is (=' #"(Apache)?"                               (opt-cg "Apache")))
    (is (=' #"(Apache(\s+Software)?(\s+Licen[cs]e)?)?" (opt-cg "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?"))))
  (testing "opt-ncg"
    (is (=' #"()?"                                              (opt-ncg nil)))
    (is (=' #"()?"                                              (opt-ncg nil nil)))
    (is (=' #"(?<groupName>)?"                                  (opt-ncg "groupName" nil)))
    (is (=' #"(?<groupName>)?"                                  (opt-ncg "groupName" #"")))
    (is (=' #"(?<groupName>x)?"                                 (opt-ncg "groupName" #"x")))
    (is (=' #"(?<groupName>.*)?"                                (opt-ncg "groupName" #".*")))
    (is (=' #"(?<groupName>foo)?"                               (opt-ncg "groupName" #"foo")))
    (is (=' #"(?<apache>Apache)?"                               (opt-ncg "apache"    "Apache")))
    (is (=' #"(?<apache>Apache(\s+Software)?(\s+Licen[cs]e)?)?" (opt-ncg "apache"    "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?"))))
  (testing "opt-fgrp"
    (is (=' #""                                           (opt-fgrp nil)))
    (is (=' #""                                           (opt-fgrp nil nil)))
    (is (=' #""                                           (opt-fgrp "i" nil)))
    (is (=' #""                                           (opt-fgrp "i" #"")))
    (is (=' #"(?i:x)?"                                    (opt-fgrp "i" #"x")))
    (is (=' #"(?i:.*)?"                                   (opt-fgrp "i" #".*")))
    (is (=' #"(?i:foo)?"                                  (opt-fgrp "i" #"foo")))
    (is (=' #"(?i:Apache)?"                               (opt-fgrp "i" "Apache")))
    (is (=' #"(?i:Apache(\s+Software)?(\s+Licen[cs]e)?)?" (opt-fgrp "i" "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?"))))
  (testing "opt-chcl"
    (is (=' #""       (opt-chcl)))
    (is (=' #""       (opt-chcl nil)))
    (is (=' #""       (opt-chcl #"")))
    (is (=' #"[x]?"   (opt-chcl #"x")))
    (is (=' #"[a-z]?" (opt-chcl #"a-z")))
    (is (=' #"[abc]?" (opt-chcl "a" "b" "c")))
    (is (=' #"[abc]?" (opt-chcl #"a" #"b" #"c")))))

(deftest zom-variant-tests
  (testing "zom"
    (is (=' #""                                         (zom nil)))
    (is (=' #""                                         (zom #"")))
    (is (=' #"x*")                                      (zom #"x"))
    (is (thrown? #?(:clj  java.util.regex.PatternSyntaxException
                    :cljs js/SyntaxError)               (zom #".*")))
    (is (=' #"foo*"                                     (zom #"foo")))
    (is (=' #"Apache*"                                  (zom "Apache"))))
  (testing "zom-grp"
    (is (=' #""                                          (zom-grp)))
    (is (=' #""                                          (zom-grp nil)))
    (is (=' #""                                          (zom-grp #"")))
    (is (=' #"(?:x)*"                                    (zom-grp #"x")))
    (is (=' #"(?:.*)*"                                   (zom-grp #".*")))
    (is (=' #"(?:foo)*"                                  (zom-grp #"foo")))
    (is (=' #"(?:Apache)*"                               (zom-grp "Apache")))
    (is (=' #"(?:Apache(\s+Software)?(\s+Licen[cs]e)?)*" (zom-grp "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?"))))
  (testing "zom-cg"
    (is (=' #"()*"                                     (zom-cg)))
    (is (=' #"()*"                                     (zom-cg nil)))
    (is (=' #"()*"                                     (zom-cg #"")))
    (is (=' #"(x)*"                                    (zom-cg #"x")))
    (is (=' #"(.*)*"                                   (zom-cg #".*")))
    (is (=' #"(foo)*"                                  (zom-cg #"foo")))
    (is (=' #"(Apache)*"                               (zom-cg "Apache")))
    (is (=' #"(Apache(\s+Software)?(\s+Licen[cs]e)?)*" (zom-cg "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?"))))
  (testing "zom-ncg"
    (is (=' #"()*"                                              (zom-ncg nil)))
    (is (=' #"()*"                                              (zom-ncg nil nil)))
    (is (=' #"(?<groupName>)*"                                  (zom-ncg "groupName" nil)))
    (is (=' #"(?<groupName>)*"                                  (zom-ncg "groupName" #"")))
    (is (=' #"(?<groupName>x)*"                                 (zom-ncg "groupName" #"x")))
    (is (=' #"(?<groupName>.*)*"                                (zom-ncg "groupName" #".*")))
    (is (=' #"(?<groupName>foo)*"                               (zom-ncg "groupName" #"foo")))
    (is (=' #"(?<apache>Apache)*"                               (zom-ncg "apache"    "Apache")))
    (is (=' #"(?<apache>Apache(\s+Software)?(\s+Licen[cs]e)?)*" (zom-ncg "apache"    "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?"))))
  (testing "zom-fgrp"
    (is (=' #""                                           (zom-fgrp nil)))
    (is (=' #""                                           (zom-fgrp nil nil)))
    (is (=' #""                                           (zom-fgrp "i" nil)))
    (is (=' #""                                           (zom-fgrp "i" #"")))
    (is (=' #"(?i:x)*"                                    (zom-fgrp "i" #"x")))
    (is (=' #"(?i:.*)*"                                   (zom-fgrp "i" #".*")))
    (is (=' #"(?i:foo)*"                                  (zom-fgrp "i" #"foo")))
    (is (=' #"(?i:Apache)*"                               (zom-fgrp "i" "Apache")))
    (is (=' #"(?i:Apache(\s+Software)?(\s+Licen[cs]e)?)*" (zom-fgrp "i" "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?"))))
  (testing "zom-chcl"
    (is (=' #""       (zom-chcl)))
    (is (=' #""       (zom-chcl nil)))
    (is (=' #""       (zom-chcl #"")))
    (is (=' #"[x]*"   (zom-chcl #"x")))
    (is (=' #"[a-z]*" (zom-chcl #"a-z")))
    (is (=' #"[abc]*" (zom-chcl "a" "b" "c")))
    (is (=' #"[abc]*" (zom-chcl #"a" #"b" #"c")))))

(deftest oom-variant-tests
  (testing "oom"
    (is (=' #""                      (oom nil)))
    (is (=' #""                      (oom #"")))
    (is (=' #"x+")                   (oom #"x"))
#?(:clj  (is (=' #".*+"              (oom #".*")))   ; Valid (but nonsensical) regex on ClojureJVM
   :cljs (is (thrown? js/SyntaxError (oom #".*"))))  ; Invalid regex on ClojureScript
    (is (=' #"foo+"                  (oom #"foo")))
    (is (=' #"Apache+"               (oom "Apache"))))
  (testing "oom-grp"
    (is (=' #""                                          (oom-grp)))
    (is (=' #""                                          (oom-grp nil)))
    (is (=' #""                                          (oom-grp #"")))
    (is (=' #"(?:x)+"                                    (oom-grp #"x")))
    (is (=' #"(?:.*)+"                                   (oom-grp #".*")))
    (is (=' #"(?:foo)+"                                  (oom-grp #"foo")))
    (is (=' #"(?:Apache)+"                               (oom-grp "Apache")))
    (is (=' #"(?:Apache(\s+Software)?(\s+Licen[cs]e)?)+" (oom-grp "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?"))))
  (testing "oom-cg"
    (is (=' #"()+"                                     (oom-cg)))
    (is (=' #"()+"                                     (oom-cg nil)))
    (is (=' #"()+"                                     (oom-cg #"")))
    (is (=' #"(x)+"                                    (oom-cg #"x")))
    (is (=' #"(.*)+"                                   (oom-cg #".*")))
    (is (=' #"(foo)+"                                  (oom-cg #"foo")))
    (is (=' #"(Apache)+"                               (oom-cg "Apache")))
    (is (=' #"(Apache(\s+Software)?(\s+Licen[cs]e)?)+" (oom-cg "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?"))))
  (testing "oom-ncg"
    (is (=' #"()+"                                              (oom-ncg nil)))
    (is (=' #"()+"                                              (oom-ncg nil nil)))
    (is (=' #"(?<groupName>)+"                                  (oom-ncg "groupName" nil)))
    (is (=' #"(?<groupName>)+"                                  (oom-ncg "groupName" #"")))
    (is (=' #"(?<groupName>x)+"                                 (oom-ncg "groupName" #"x")))
    (is (=' #"(?<groupName>.*)+"                                (oom-ncg "groupName" #".*")))
    (is (=' #"(?<groupName>foo)+"                               (oom-ncg "groupName" #"foo")))
    (is (=' #"(?<apache>Apache)+"                               (oom-ncg "apache"    "Apache")))
    (is (=' #"(?<apache>Apache(\s+Software)?(\s+Licen[cs]e)?)+" (oom-ncg "apache"    "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?"))))
  (testing "oom-fgrp"
    (is (=' #""                                           (oom-fgrp nil)))
    (is (=' #""                                           (oom-fgrp nil nil)))
    (is (=' #""                                           (oom-fgrp "i" nil)))
    (is (=' #""                                           (oom-fgrp "i" #"")))
    (is (=' #"(?i:x)+"                                    (oom-fgrp "i" #"x")))
    (is (=' #"(?i:.*)+"                                   (oom-fgrp "i" #".*")))
    (is (=' #"(?i:foo)+"                                  (oom-fgrp "i" #"foo")))
    (is (=' #"(?i:Apache)+"                               (oom-fgrp "i" "Apache")))
    (is (=' #"(?i:Apache(\s+Software)?(\s+Licen[cs]e)?)+" (oom-fgrp "i" "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?"))))
  (testing "oom-chcl"
    (is (=' #""       (oom-chcl)))
    (is (=' #""       (oom-chcl nil)))
    (is (=' #""       (oom-chcl #"")))
    (is (=' #"[x]+"   (oom-chcl #"x")))
    (is (=' #"[a-z]+" (oom-chcl #"a-z")))
    (is (=' #"[abc]+" (oom-chcl "a" "b" "c")))
    (is (=' #"[abc]+" (oom-chcl #"a" #"b" #"c")))))

(deftest nom-variant-tests
  (testing "nom"
    (is (=' #""                      (nom nil nil)))
    (is (=' #""                      (nom nil #"")))
    (is (=' #""                      (nom 2 nil)))
    (is (=' #"x{5,}"                 (nom 5 #"x")))
#?(:clj  (is (=' #".*{3,}"           (nom 3 #".*")))   ; Valid (but nonsensical) regex on ClojureJVM
   :cljs (is (thrown? js/SyntaxError (nom 3 #".*"))))  ; Invalid regex on ClojureScript
    (is (=' #"foo{2,}"               (nom 2 #"foo")))  ; Note how this doesn't result in optionality being applied to the entirety of the input - that's what nom-grp etc. are for
    (is (=' #"Apache{17,}"           (nom 17 "Apache"))))
  (testing "nom-grp"
    (is (=' #""                                              (nom-grp nil nil)))
    (is (=' #""                                              (nom-grp 3 nil)))
    (is (=' #""                                              (nom-grp 246 #"")))
    (is (=' #"(?:x){0,}"                                     (nom-grp 0 #"x")))
#?(:clj  (is (thrown? java.util.regex.PatternSyntaxException (nom-grp nil #"x")))   ; Invalid regex on ClojureJVM
   :cljs (is (=' (re-pattern "(?:x){,}")                     (nom-grp nil #"x"))))  ; Valid (but nonsensical) regex on ClojureScript
    (is (=' #"(?:.*){7,}"                                    (nom-grp 7 #".*")))
    (is (=' #"(?:foo){42,}"                                  (nom-grp 42 #"foo")))
    (is (=' #"(?:Apache){12,}"                               (nom-grp 12 "Apache")))
    (is (=' #"(?:Apache(\s+Software)?(\s+Licen[cs]e)?){5,}"  (nom-grp 5 "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?"))))
  (testing "nom-cg"
#?(:clj  (is (thrown? java.util.regex.PatternSyntaxException (nom-cg nil nil)))   ; Invalid regex on ClojureJVM
   :cljs (is (=' (re-pattern "(){,}")                        (nom-cg nil nil))))  ; Valid (but nonsensical) regex on ClojureScript
    (is (=' #"(){3,}"                                        (nom-cg 3 #"")))
    (is (=' #"(x){4,}"                                       (nom-cg 4 #"x")))
#?(:clj  (is (thrown? java.util.regex.PatternSyntaxException (nom-cg nil #"x")))   ; Invalid regex on ClojureJVM
   :cljs (is (=' (re-pattern "(x){,}")                       (nom-cg nil #"x"))))  ; Valid (but nonsensical) regex on ClojureScript
    (is (=' #"(.*){5,}"                                      (nom-cg 5 #".*")))
    (is (=' #"(foo){6,}"                                     (nom-cg 6 #"foo")))
    (is (=' #"(Apache){7,}"                                  (nom-cg 7 "Apache")))
    (is (=' #"(Apache(\s+Software)?(\s+Licen[cs]e)?){8,}"    (nom-cg 8 "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?"))))
  (testing "nom-ncg"
#?(:clj  (is (thrown? java.util.regex.PatternSyntaxException       (nom-ncg nil nil)))       ; Invalid regex on ClojureJVM
   :cljs (is (=' (re-pattern "(){,}")                              (nom-ncg nil nil))))      ; Valid (but nonsensical) regex on ClojureScript
#?(:clj  (is (thrown? java.util.regex.PatternSyntaxException       (nom-ncg nil nil nil)))   ; Invalid regex on ClojureJVM
   :cljs (is (=' (re-pattern "(){,}")                              (nom-ncg nil nil nil))))  ; Valid (but nonsensical) regex on ClojureScript
    (is (=' #"(?<groupName>){7,}"                                  (nom-ncg "groupName" 7 nil)))
    (is (=' #"(?<groupName>){6,}"                                  (nom-ncg "groupName" 6 #"")))
    (is (=' #"(?<groupName>x){5,}"                                 (nom-ncg "groupName" 5 #"x")))
#?(:clj  (is (thrown? java.util.regex.PatternSyntaxException       (nom-ncg "groupName" nil #"x")))   ; Invalid regex on ClojureJVM
   :cljs (is (=' (re-pattern "(?<groupName>x){,}")                 (nom-ncg "groupName" nil #"x"))))  ; Valid (but nonsensical) regex on ClojureScript
    (is (=' #"(?<groupName>.*){4,}"                                (nom-ncg "groupName" 4 #".*")))
    (is (=' #"(?<groupName>foo){3,}"                               (nom-ncg "groupName" 3 #"foo")))
    (is (=' #"(?<apache>Apache){2,}"                               (nom-ncg "apache"    2 "Apache")))
    (is (=' #"(?<apache>Apache(\s+Software)?(\s+Licen[cs]e)?){1,}" (nom-ncg "apache"    1 "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?"))))
  (testing "nom-fgrp"
    (is (=' #""                                              (nom-fgrp nil nil)))
    (is (=' #""                                              (nom-fgrp nil nil nil)))
    (is (=' #""                                              (nom-fgrp "i" 2 nil)))
    (is (=' #""                                              (nom-fgrp "i" 5 #"")))
    (is (=' #"(?i:x){6,}"                                    (nom-fgrp "i" 6 #"x")))
#?(:clj  (is (thrown? java.util.regex.PatternSyntaxException (nom-fgrp "i" nil #"x")))   ; Invalid regex on ClojureJVM
   :cljs (is (=' (re-pattern "(?i:x){,}")                    (nom-fgrp "i" nil #"x"))))  ; Valid (but nonsensical) regex on ClojureScript
    (is (=' #"(?i:.*){2,}"                                   (nom-fgrp "i" 2 #".*")))
    (is (=' #"(?i:foo){7,}"                                  (nom-fgrp "i" 7 #"foo")))
    (is (=' #"(?i:Apache){8,}"                               (nom-fgrp "i" 8 "Apache")))
    (is (=' #"(?i:Apache(\s+Software)?(\s+Licen[cs]e)?){5,}" (nom-fgrp "i" 5 "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?"))))
  (testing "nom-chcl"
    (is (=' #""          (nom-chcl nil)))
    (is (=' #""          (nom-chcl nil nil)))
    (is (=' #""          (nom-chcl 4 #"")))
    (is (=' #"[x]{7,}"   (nom-chcl 7 #"x")))
    (is (=' #"[a-z]{2,}" (nom-chcl 2 #"a-z")))
    (is (=' #"[abc]{8,}" (nom-chcl 8 "a" "b" "c")))
    (is (=' #"[abc]{1,}" (nom-chcl 1 #"a" #"b" #"c")))))

(deftest exn-variant-tests
  (testing "exn"
    (is (=' #""                                              (exn nil nil)))
    (is (=' #""                                              (exn nil #"")))
    (is (=' #""                                              (exn 2 nil)))
    (is (=' #""                                              (exn 2 #"")))
    (is (=' #"x{5}"                                          (exn 5 #"x")))
#?(:clj  (is (thrown? java.util.regex.PatternSyntaxException (exn nil #"x")))   ; Invalid regex on ClojureJVM
   :cljs (is (=' (re-pattern "x{}")                          (exn nil #"x"))))  ; Valid (but nonsensical) regex on ClojureScript
#?(:clj  (is (=' #".*{3}"                                    (exn 3 #".*")))   ; Valid (but nonsensical) regex on ClojureJVM
   :cljs (is (thrown? js/SyntaxError                         (exn 3 #".*"))))  ; Invalid regex on ClojureScript
    (is (=' #"foo{2}"                                        (exn 2 #"foo")))  ; Note how this doesn't result in optionality being applied to the entirety of the input - that's what nom-grp etc. are for
    (is (=' #"Apache{17}"                                    (exn 17 "Apache"))))
  (testing "exn-grp"
    (is (=' #""                                              (exn-grp nil nil)))
    (is (=' #""                                              (exn-grp 3 nil)))
    (is (=' #""                                              (exn-grp 246 #"")))
    (is (=' #"(?:x){0}"                                      (exn-grp 0 #"x")))
#?(:clj  (is (thrown? java.util.regex.PatternSyntaxException (exn-grp nil #"x")))   ; Invalid regex on ClojureJVM
   :cljs (is (=' (re-pattern "(?:x){}")                      (exn-grp nil #"x"))))  ; Valid (but nonsensical) regex on ClojureScript
    (is (=' #"(?:.*){7}"                                     (exn-grp 7 #".*")))
    (is (=' #"(?:foo){42}"                                   (exn-grp 42 #"foo")))
    (is (=' #"(?:Apache){12}"                                (exn-grp 12 "Apache")))
    (is (=' #"(?:Apache(\s+Software)?(\s+Licen[cs]e)?){5}"   (exn-grp 5 "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?"))))
  (testing "exn-cg"
#?(:clj  (is (thrown? java.util.regex.PatternSyntaxException (exn-cg nil nil)))       ; Invalid regex on ClojureJVM
   :cljs (is (=' (re-pattern "(){}")                         (exn-cg nil nil))))      ; Valid (but nonsensical) regex on ClojureScript
    (is (=' #"(){3}"                                         (exn-cg 3 nil)))  ; Note: empty capturing groups are _not_ optimised out, since doing so could break code that indexes into the matched groups
    (is (=' #"(){3}"                                         (exn-cg 3 #"")))
    (is (=' #"(x){4}"                                        (exn-cg 4 #"x")))
#?(:clj  (is (thrown? java.util.regex.PatternSyntaxException (exn-cg nil #"x")))   ; Invalid regex on ClojureJVM
   :cljs (is (=' (re-pattern "(x){}")                        (exn-cg nil #"x"))))  ; Valid (but nonsensical) regex on ClojureScript
    (is (=' #"(.*){5}"                                       (exn-cg 5 #".*")))
    (is (=' #"(foo){6}"                                      (exn-cg 6 #"foo")))
    (is (=' #"(Apache){7}"                                   (exn-cg 7 "Apache")))
    (is (=' #"(Apache(\s+Software)?(\s+Licen[cs]e)?){8}"     (exn-cg 8 "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?"))))
  (testing "exn-ncg"
#?(:clj  (is (thrown? java.util.regex.PatternSyntaxException      (exn-ncg nil nil)))       ; Invalid regex on ClojureJVM
   :cljs (is (=' (re-pattern "(){}")                              (exn-ncg nil nil))))      ; Valid (but nonsensical) regex on ClojureScript
#?(:clj  (is (thrown? java.util.regex.PatternSyntaxException      (exn-ncg nil nil nil)))   ; Invalid regex on ClojureJVM
   :cljs (is (=' (re-pattern "(){}")                              (exn-ncg nil nil nil))))  ; Valid (but nonsensical) regex on ClojureScript
    (is (=' #"(?<groupName>){7}"                                  (exn-ncg "groupName" 7 nil)))
    (is (=' #"(?<groupName>){6}"                                  (exn-ncg "groupName" 6 #"")))
    (is (=' #"(?<groupName>x){5}"                                 (exn-ncg "groupName" 5 #"x")))
#?(:clj  (is (thrown? java.util.regex.PatternSyntaxException      (exn-ncg "groupName" nil #"x")))   ; Invalid regex on ClojureJVM
   :cljs (is (=' (re-pattern "(?<groupName>x){}")                 (exn-ncg "groupName" nil #"x"))))  ; Valid (but nonsensical) regex on ClojureScript
    (is (=' #"(?<groupName>.*){4}"                                (exn-ncg "groupName" 4 #".*")))
    (is (=' #"(?<groupName>foo){3}"                               (exn-ncg "groupName" 3 #"foo")))
    (is (=' #"(?<apache>Apache){2}"                               (exn-ncg "apache"    2 "Apache")))
    (is (=' #"(?<apache>Apache(\s+Software)?(\s+Licen[cs]e)?){1}" (exn-ncg "apache"    1 "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?"))))
  (testing "exn-fgrp"
    (is (=' #""                                              (exn-fgrp nil nil)))
    (is (=' #""                                              (exn-fgrp nil nil nil)))
    (is (=' #""                                              (exn-fgrp "i" 2 nil)))
    (is (=' #""                                              (exn-fgrp "i" 5 #"")))
    (is (=' #"(?i:x){6}"                                     (exn-fgrp "i" 6 #"x")))
#?(:clj  (is (thrown? java.util.regex.PatternSyntaxException (exn-fgrp "i" nil #"x")))   ; Invalid regex on ClojureJVM
   :cljs (is (=' (re-pattern "(?i:x){}")                     (exn-fgrp "i" nil #"x"))))  ; Valid (but nonsensical) regex on ClojureScript
    (is (=' #"(?i:.*){2}"                                    (exn-fgrp "i" 2 #".*")))
    (is (=' #"(?i:foo){7}"                                   (exn-fgrp "i" 7 #"foo")))
    (is (=' #"(?i:Apache){8}"                                (exn-fgrp "i" 8 "Apache")))
    (is (=' #"(?i:Apache(\s+Software)?(\s+Licen[cs]e)?){5}"  (exn-fgrp "i" 5 "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?"))))
  (testing "exn-chcl"
    (is (=' #""                                              (exn-chcl nil)))
    (is (=' #""                                              (exn-chcl nil nil)))
    (is (=' #""                                              (exn-chcl 4 nil)))
    (is (=' #""                                              (exn-chcl 4 #"")))
    (is (=' #"[x]{7}"                                        (exn-chcl 7 #"x")))
#?(:clj  (is (thrown? java.util.regex.PatternSyntaxException (exn-chcl nil #"x")))   ; Invalid regex on ClojureJVM
   :cljs (is (=' (re-pattern "[x]{}")                        (exn-chcl nil #"x"))))  ; Valid (but nonsensical) regex on ClojureScript
    (is (=' #"[a-z]{2}"                                      (exn-chcl 2 #"a-z")))
    (is (=' #"[abc]{8}"                                      (exn-chcl 8 "a" "b" "c")))
    (is (=' #"[abc]{1}"                                      (exn-chcl 1 #"a" #"b" #"c")))))

(deftest n2m-variant-tests
  (testing "n2m"
    (is (=' #""                                              (n2m nil nil nil)))
    (is (=' #""                                              (n2m nil nil #"")))
    (is (=' #""                                              (n2m 2 nil nil)))
    (is (=' #""                                              (n2m nil 4 nil)))
    (is (=' #""                                              (n2m 2 4 nil)))
    (is (=' #""                                              (n2m 2 4 #"")))
    (is (=' #"x{2,4}"                                        (n2m 2 4 #"x")))
    (is (=' #"x{2,}"                                         (n2m 2 nil #"x")))   ; This turns into nom
#?(:clj  (is (thrown? java.util.regex.PatternSyntaxException (n2m nil 7 #"x")))   ; Invalid regex on ClojureJVM
   :cljs (is (=' (re-pattern "x{,7}")                        (n2m nil 7 #"x"))))  ; Valid (but nonsensical) regex on ClojureScript
#?(:clj  (is (=' #".*{3,7}"                                  (n2m 3 7 #".*")))    ; Valid (but nonsensical) regex on ClojureJVM
   :cljs (is (thrown? js/SyntaxError                         (n2m 3 7 #".*"))))   ; Invalid regex on ClojureScript
    (is (=' #"foo{2,2}"                                      (n2m 2 2 #"foo")))   ; Note how this doesn't result in optionality being applied to the entirety of the input - that's what nom-grp etc. are for
    (is (=' #"Apache{17,21}"                                 (n2m 17 21 "Apache"))))
  (testing "n2m-grp"
    (is (=' #""                                               (n2m-grp nil nil nil)))
    (is (=' #""                                               (n2m-grp 3 100 nil)))
    (is (=' #""                                               (n2m-grp 246 250 #"")))
#?(:clj  (is (thrown? java.util.regex.PatternSyntaxException  (n2m-grp nil 3 #"x")))   ; Invalid regex on ClojureJVM
   :cljs (is (=' (re-pattern "(?:x){,3}")                     (n2m-grp nil 3 #"x"))))  ; Valid (but nonsensical) regex on ClojureScript (it doesn't do what you might think it does...)
    (is (=' #"(?:x){0,}"                                      (n2m-grp 0 nil #"x")))   ; This turns into nom-grp
    (is (=' #"(?:.*){7,8}"                                    (n2m-grp 7 8 #".*")))
    (is (=' #"(?:foo){42,69}"                                 (n2m-grp 42 69 #"foo")))
    (is (=' #"(?:Apache){12,13}"                              (n2m-grp 12 13 "Apache")))
    (is (=' #"(?:Apache(\s+Software)?(\s+Licen[cs]e)?){5,99}" (n2m-grp 5 99 "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?"))))
  (testing "n2m-cg"
#?(:clj  (is (thrown? java.util.regex.PatternSyntaxException (n2m-cg nil nil nil)))   ; Invalid regex on ClojureJVM
   :cljs (is (=' (re-pattern "(){,}")                        (n2m-cg nil nil nil))))  ; Valid (but nonsensical) regex on ClojureScript
    (is (=' #"(){3,4}"                                       (n2m-cg 3 4 #"")))
    (is (=' #"(x){4,5}"                                      (n2m-cg 4 5 #"x")))
    (is (=' #"(x){5,}"                                       (n2m-cg 5 nil #"x")))   ; This turns into nom-cg
#?(:clj  (is (thrown? java.util.regex.PatternSyntaxException (n2m-cg nil 3 #"x")))   ; Invalid regex on ClojureJVM
   :cljs (is (=' (re-pattern "(x){,3}")                      (n2m-cg nil 3 #"x"))))  ; Valid (but nonsensical) regex on ClojureScript (it doesn't do what you might think it does...)
    (is (=' #"(.*){5,6}"                                     (n2m-cg 5 6 #".*")))
    (is (=' #"(foo){6,7}"                                    (n2m-cg 6 7 #"foo")))
    (is (=' #"(Apache){7,8}"                                 (n2m-cg 7 8 "Apache")))
    (is (=' #"(Apache(\s+Software)?(\s+Licen[cs]e)?){8,9}"   (n2m-cg 8 9 "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?"))))
  (testing "n2m-ncg"
#?(:clj  (is (thrown? java.util.regex.PatternSyntaxException         (n2m-ncg nil nil nil)))       ; Invalid regex on ClojureJVM
   :cljs (is (=' (re-pattern "(){,}")                                (n2m-ncg nil nil nil))))      ; Valid (but nonsensical) regex on ClojureScript
#?(:clj  (is (thrown? java.util.regex.PatternSyntaxException         (n2m-ncg nil nil nil nil)))   ; Invalid regex on ClojureJVM
   :cljs (is (=' (re-pattern "(){,}")                                (n2m-ncg nil nil nil nil))))  ; Valid (but nonsensical) regex on ClojureScript
    (is (=' #"(?<groupName>){7,8}"                                   (n2m-ncg "groupName" 7  8 nil)))
    (is (=' #"(?<groupName>){6,9}"                                   (n2m-ncg "groupName" 6  9 #"")))
    (is (=' #"(?<groupName>x){5,10}"                                 (n2m-ncg "groupName" 5 10 #"x")))
    (is (=' #"(?<groupName>x){8,}"                                   (n2m-ncg "groupName" 8 nil #"x")))   ; This turns into nom-ncg
#?(:clj  (is (thrown? java.util.regex.PatternSyntaxException         (n2m-ncg "groupName" nil 3 #"x")))   ; Invalid regex on ClojureJVM
   :cljs (is (=' (re-pattern "(?<groupName>x){,3}")                  (n2m-ncg "groupName" nil 3 #"x"))))  ; Valid (but nonsensical) regex on ClojureScript (it doesn't do what you might think it does...)
    (is (=' #"(?<groupName>.*){4,11}"                                (n2m-ncg "groupName" 4 11 #".*")))
    (is (=' #"(?<groupName>foo){3,12}"                               (n2m-ncg "groupName" 3 12 #"foo")))
    (is (=' #"(?<apache>Apache){2,13}"                               (n2m-ncg "apache"    2 13 "Apache")))
    (is (=' #"(?<apache>Apache(\s+Software)?(\s+Licen[cs]e)?){1,14}" (n2m-ncg "apache"    1 14 "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?"))))
  (testing "n2m-fgrp"
    (is (=' #""                                                (n2m-fgrp nil nil nil)))
    (is (=' #""                                                (n2m-fgrp nil nil nil nil)))
    (is (=' #""                                                (n2m-fgrp "i" 2 4  nil)))
    (is (=' #""                                                (n2m-fgrp "i" 5 9  #"")))
    (is (=' #"(?i:x){6,7}"                                     (n2m-fgrp "i" 6 7  #"x")))
    (is (=' #"(?i:x){8,}"                                      (n2m-fgrp "i" 8 nil #"x")))   ; This turns into nom-fgrp
#?(:clj  (is (thrown? java.util.regex.PatternSyntaxException   (n2m-fgrp "i" nil 3 #"x")))   ; Invalid regex on ClojureJVM
   :cljs (is (=' (re-pattern "(?i:x){,3}")                     (n2m-fgrp "i" nil 3 #"x"))))  ; Valid (but nonsensical) regex on ClojureScript (it doesn't do what you might think it does...)
    (is (=' #"(?i:.*){2,99}"                                   (n2m-fgrp "i" 2 99 #".*")))
    (is (=' #"(?i:foo){7,13}"                                  (n2m-fgrp "i" 7 13 #"foo")))
    (is (=' #"(?i:Apache){8,8}"                                (n2m-fgrp "i" 8 8  "Apache")))
    (is (=' #"(?i:Apache(\s+Software)?(\s+Licen[cs]e)?){5,17}" (n2m-fgrp "i" 5 17 "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?"))))
  (testing "n2m-chcl"
    (is (=' #""                                              (n2m-chcl nil nil)))
    (is (=' #""                                              (n2m-chcl nil nil nil)))
    (is (=' #""                                              (n2m-chcl 4 4 #"")))
    (is (=' #"[x]{7,9}"                                      (n2m-chcl 7 9  #"x")))
    (is (=' #"[x]{8,}"                                       (n2m-chcl 8 nil #"x")))   ; This turns into nom-chcl
#?(:clj  (is (thrown? java.util.regex.PatternSyntaxException (n2m-chcl nil 3 #"x")))   ; Invalid regex on ClojureJVM
   :cljs (is (=' (re-pattern "[x]{,3}")                      (n2m-chcl nil 3 #"x"))))  ; Valid (but nonsensical) regex on ClojureScript (it doesn't do what you might think it does...)
    (is (=' #"[a-z]{2,99}"                                   (n2m-chcl 2 99 #"a-z")))
    (is (=' #"[abc]{8,13}"                                   (n2m-chcl 8 13 "a" "b" "c")))
    (is (=' #"[abc]{1,10}"                                   (n2m-chcl 1 10 #"a" #"b" #"c")))))

(deftest alt-variant-tests
  (testing "alt"
    (is (=' #""                    (alt nil)))
    (is (=' #""                    (alt nil nil)))
    (is (=' #""                    (alt #"")))
    (is (=' #"a"                   (alt #"a")))
    (is (=' #""                    (alt #"" #"")))
    (is (=' #"foo|bar"             (alt #"foo" #"bar")))
    (is (=' #"foo"                 (alt "foo" "foo")))   ; Deduplication
    (is (=' #"foo"                 (alt "foo" #"foo")))  ; Deduplication
    (is (=' #"0"                   (alt 0 "0" #"0")))    ; Deduplication
#?(:clj  (is (=' #"(?Uiu:ab)"      (alt #"(?Uiu:ab)" #"ab(?i)(?U)(?u)")))                               ; Deduplication with flags
   :cljs (is (=' #"(?ims:ab)"      (alt #"(?ims:ab)" (doto (js/RegExp.) (.compile "ab" "msiydgv"))))))  ; Deduplication with flags with ⚠️ footgun: non-embeddable flags are silently dropped
    (is (=' #"0|1|2|3|4|5|6|7|8|9" (apply alt (range 10)))))
  (testing "alt-grp"
    (is (=' #""                        (alt-grp nil)))
    (is (=' #""                        (alt-grp nil nil)))
    (is (=' #""                        (alt-grp #"")))  ; Optimisation of empty non-capturing groups
    (is (=' #"(?:a)"                   (alt-grp #"a")))
    (is (=' #""                        (alt-grp #"" #"")))  ; Optimisation of empty non-capturing groups
    (is (=' #"(?:foo|bar)"             (alt-grp #"foo" #"bar")))
    (is (=' #"(?:0|1|2|3|4|5|6|7|8|9)" (apply alt-grp (range 10))))
    (is (=' #"(?:0|1|2|3|4|5|6|7|8|9)" (apply alt-grp (concat (range 10) (map str (range 10)))))))  ; Deduplication of equivalent regexes
  (testing "alt-cg"
    (is (=' #"()"                    (alt-cg nil)))
    (is (=' #"()"                    (alt-cg nil nil)))
    (is (=' #"()"                    (alt-cg #"")))
    (is (=' #"()"                    (alt-cg #"" #"")))
    (is (=' #"(a)"                   (alt-cg #"a")))
    (is (=' #"(foo|bar)"             (alt-cg #"foo" #"bar")))
    (is (=' #"(0|1|2|3|4|5|6|7|8|9)" (apply alt-cg (range 10))))
    (is (=' #"(0|1|2|3|4|5|6|7|8|9)" (apply alt-cg (concat (range 10) (map str (range 10)))))))  ; Deduplication of equivalent regexes
  (testing "alt-ncg"
    (is (=' #"()"                              (alt-ncg nil nil)))
    (is (=' #"()"                              (alt-ncg nil nil)))
    (is (=' #"()"                              (alt-ncg nil nil nil)))
    (is (=' #"(?<groupName>)"                  (alt-ncg "groupName" nil)))
    (is (=' #"(?<groupName>)"                  (alt-ncg "groupName" #"")))
    (is (=' #"(?<groupName>)"                  (alt-ncg "groupName" #"" #"")))
    (is (=' #"(?<groupName>a)"                 (alt-ncg "groupName" #"a")))
    (is (=' #"(?<groupName>foo|bar)"           (alt-ncg "groupName" #"foo" #"bar")))
    (is (=' #"(?<numbers>0|1|2|3|4|5|6|7|8|9)" (apply (partial alt-ncg "numbers") (range 10))))
    (is (=' #"(?<numbers>0|1|2|3|4|5|6|7|8|9)" (apply (partial alt-ncg "numbers") (concat (range 10) (map str (range 10)))))))  ; Deduplication of equivalent regexes
  (testing "alt-fgrp"
    (is (=' #""                         (alt-fgrp nil)))
    (is (=' #""                         (alt-fgrp nil nil)))
    (is (=' #""                         (alt-fgrp "i" nil)))
    (is (=' #""                         (alt-fgrp "i" #"")))
    (is (=' #""                         (alt-fgrp "i" #"" #"")))
    (is (=' #"(?i:x)"                   (alt-fgrp "i" #"x")))
    (is (=' #"(?i:foo|bar)"             (alt-fgrp "i" #"foo" #"bar")))
    (is (=' #"(?i:0|1|2|3|4|5|6|7|8|9)" (apply (partial alt-fgrp "i") (range 10))))))

(deftest and-variant-tests
  (testing "and'"
    (is (=' #""            (and' nil nil)))
    (is (=' #""            (and' nil nil nil)))
    (is (=' #""            (and' nil nil #"\s+")))
    (is (=' #"a"           (and' #"a" nil)))
    (is (=' #"b"           (and' nil #"b")))
    (is (=' #"b"           (and' nil #"b" nil)))
    (is (=' #"a\s+|\s+a"   (and' #"a" nil #"\s+")))  ; Optimisation
    (is (=' #"\s+b|b\s+"   (and' nil #"b" #"\s+")))  ; Optimisation
    (is (=' #"a"           (and' #"a" #"")))
    (is (=' #"b"           (and' #"" #"b")))
    (is (=' #"aa"          (and' #"a" #"a")))  ; Optimisation
    (is (=' #"ab|ba"       (and' #"a" #"b")))
    (is (=' #"ab|ba"       (and' #"a" #"b" nil)))
    (is (=' #"a\s+b|b\s+a" (and' #"a" #"b" #"\s+"))))
  (testing "and-grp"
    (is (=' #""                (and-grp nil nil)))
    (is (=' #""                (and-grp nil nil nil)))
    (is (=' #"(?:a)"           (and-grp #"a" nil)))
    (is (=' #"(?:a)"           (and-grp #"a" #"")))  ; Optimisation
    (is (=' #"(?:b)"           (and-grp #"" #"b")))  ; Optimisation
    (is (=' #"(?:aa)"          (and-grp #"a" #"a")))  ; Optimisation
    (is (=' #"(?:ab|ba)"       (and-grp #"a" #"b")))
    (is (=' #"(?:ab|ba)"       (and-grp #"a" #"b" nil)))
    (is (=' #"(?:a\s+b|b\s+a)" (and-grp #"a" #"b" #"\s+"))))
  (testing "and-cg"
    (is (=' #"()"            (and-cg nil nil)))
    (is (=' #"()"            (and-cg nil nil nil)))
    (is (=' #"()"            (and-cg #""  nil)))
    (is (=' #"(a)"           (and-cg #"a" nil)))
    (is (=' #"(a)"           (and-cg #"a" #"")))  ; Optimisation
    (is (=' #"(b)"           (and-cg #"" #"b")))  ; Optimisation
    (is (=' #"(aa)"          (and-cg #"a" #"a")))  ; Optimisation
    (is (=' #"(ab|ba)"       (and-cg #"a" #"b")))
    (is (=' #"(ab|ba)"       (and-cg #"a" #"b" nil)))
    (is (=' #"(a\s+b|b\s+a)" (and-cg #"a" #"b" #"\s+"))))
  (testing "and-ncg"
    (is (=' #"()"                        (and-ncg nil nil nil)))
    (is (=' #"()"                        (and-ncg nil nil nil nil)))
    (is (=' #"(?<groupName>)"            (and-ncg "groupName" nil nil nil)))
    (is (=' #"(?<groupName>)"            (and-ncg "groupName" #"" nil)))
    (is (=' #"(?<groupName>a)"           (and-ncg "groupName" #"a" nil)))
    (is (=' #"(?<groupName>a)"           (and-ncg "groupName" #"a" #"")))  ; Optimisation
    (is (=' #"(?<groupName>b)"           (and-ncg "groupName" #"" #"b")))  ; Optimisation
    (is (=' #"(?<groupName>aa)"          (and-ncg "groupName" #"a" #"a")))  ; Optimisation
    (is (=' #"(?<groupName>ab|ba)"       (and-ncg "groupName" #"a" #"b")))
    (is (=' #"(?<groupName>ab|ba)"       (and-ncg "groupName" #"a" #"b" nil)))
    (is (=' #"(?<groupName>a\s+b|b\s+a)" (and-ncg "groupName" #"a" #"b" #"\s+"))))
  (testing "and-fgrp"
    (is (=' #""                 (and-fgrp nil nil nil)))
    (is (=' #""                 (and-fgrp nil nil nil nil)))
    (is (=' #""                 (and-fgrp "i" nil nil nil)))
    (is (=' #""                 (and-fgrp "i" #"" nil)))
    (is (=' #"(?i:a)"           (and-fgrp "i" #"a" nil)))
    (is (=' #"(?i:a)"           (and-fgrp "i" #"a" #"")))  ; Optimisation
    (is (=' #"(?i:b)"           (and-fgrp "i" #"" #"b")))  ; Optimisation
    (is (=' #"(?i:aa)"          (and-fgrp "i" #"a" #"a")))  ; Optimisation
    (is (=' #"(?i:ab|ba)"       (and-fgrp "i" #"a" #"b")))
    (is (=' #"(?i:ab|ba)"       (and-fgrp "i" #"a" #"b" nil)))
    (is (=' #"(?i:a\s+b|b\s+a)" (and-fgrp "i" #"a" #"b" #"\s+")))))

(deftest or-variant-tests
  (testing "or'"
    (is (=' #""                (or' nil nil)))
    (is (=' #""                (or' nil nil nil)))
    (is (=' #""                (or' nil nil #"\s+")))
    (is (=' #"a"               (or' #"a" nil)))
    (is (=' #"b"               (or' nil #"b")))
    (is (=' #"b"               (or' nil #"b" nil)))
    (is (=' #"a\s+|\s+a|a"     (or' #"a" nil #"\s+")))  ; Optimisation
    (is (=' #"\s+b|b\s+|b"     (or' nil #"b" #"\s+")))  ; Optimisation
    (is (=' #"a|"              (or' #"a" #"")))
    (is (=' #"b|"              (or' #"" #"b")))   ; Note how order is not what we might expect (but it is correct!)
    (is (=' #"aa|a"            (or' #"a" #"a")))  ; Optimisation
    (is (=' #"ab|ba|a|b"       (or' #"a" #"b")))
    (is (=' #"ab|ba|a|b"       (or' #"a" #"b" nil)))
    (is (=' #"a\s+b|b\s+a|a|b" (or' #"a" #"b" #"\s+"))))
  (testing "or-grp"
    (is (=' #""                    (or-grp nil nil)))
    (is (=' #""                    (or-grp nil nil nil)))
    (is (=' #"(?:a)"               (or-grp #"a" nil)))
    (is (=' #"(?:a|)"              (or-grp #"a" #"")))  ; Optimisation
    (is (=' #"(?:b|)"              (or-grp #"" #"b")))  ; Note how order is not what we might expect (but it is correct!)
    (is (=' #"(?:aa|a)"            (or-grp #"a" #"a")))  ; Optimisation
    (is (=' #"(?:ab|ba|a|b)"       (or-grp #"a" #"b")))
    (is (=' #"(?:ab|ba|a|b)"       (or-grp #"a" #"b" nil)))
    (is (=' #"(?:a\s+b|b\s+a|a|b)" (or-grp #"a" #"b" #"\s+"))))
  (testing "or-cg"
    (is (=' #"()"                (or-cg nil nil)))
    (is (=' #"()"                (or-cg nil nil nil)))
    (is (=' #"(a)"               (or-cg #"a" nil)))
    (is (=' #"(a|)"              (or-cg #"a" #"")))  ; Optimisation
    (is (=' #"(b|)"              (or-cg #"" #"b")))  ; Note how order is not what we might expect (but it is correct!)
    (is (=' #"(aa|a)"            (or-cg #"a" #"a")))  ; Optimisation
    (is (=' #"(ab|ba|a|b)"       (or-cg #"a" #"b")))
    (is (=' #"(ab|ba|a|b)"       (or-cg #"a" #"b" nil)))
    (is (=' #"(a\s+b|b\s+a|a|b)" (or-cg #"a" #"b" #"\s+"))))
  (testing "or-ncg"
    (is (=' #"()"                            (or-ncg nil nil nil)))
    (is (=' #"()"                            (or-ncg nil nil nil nil)))
    (is (=' #"(?<groupName>)"                (or-ncg "groupName" nil nil nil)))
    (is (=' #"(?<groupName>a)"               (or-ncg "groupName" #"a" nil)))
    (is (=' #"(?<groupName>a|)"              (or-ncg "groupName" #"a" #"")))  ; Optimisation
    (is (=' #"(?<groupName>b|)"              (or-ncg "groupName" #"" #"b")))  ; Note how order is not what we might expect (but it is correct!)
    (is (=' #"(?<groupName>aa|a)"            (or-ncg "groupName" #"a" #"a")))  ; Optimisation
    (is (=' #"(?<groupName>ab|ba|a|b)"       (or-ncg "groupName" #"a" #"b")))
    (is (=' #"(?<groupName>ab|ba|a|b)"       (or-ncg "groupName" #"a" #"b" nil)))
    (is (=' #"(?<groupName>a\s+b|b\s+a|a|b)" (or-ncg "groupName" #"a" #"b" #"\s+"))))
  (testing "or-fgrp"
    (is (=' #""                     (or-fgrp nil nil nil)))
    (is (=' #""                     (or-fgrp nil nil nil nil)))
    (is (=' #""                     (or-fgrp "i" nil nil nil)))
    (is (=' #""                     (or-fgrp "i" #""  nil)))
    (is (=' #"(?i:a)"               (or-fgrp "i" #"a" nil)))
    (is (=' #"(?i:a|)"              (or-fgrp "i" #"a" #"")))  ; Optimisation
    (is (=' #"(?i:b|)"              (or-fgrp "i" #"" #"b")))  ; Note how order is not what we might expect (but it is correct!)
    (is (=' #"(?i:aa|a)"            (or-fgrp "i" #"a" #"a")))  ; Optimisation
    (is (=' #"(?i:ab|ba|a|b)"       (or-fgrp "i" #"a" #"b")))
    (is (=' #"(?i:ab|ba|a|b)"       (or-fgrp "i" #"a" #"b" nil)))
    (is (=' #"(?i:a\s+b|b\s+a|a|b)" (or-fgrp "i" #"a" #"b" #"\s+")))))

(deftest xor-variant-tests
  (testing "xor'"
    (is (=' #""    (xor' nil nil)))
    (is (=' #"a"   (xor' #"a" nil)))
    (is (=' #"b"   (xor' nil #"b")))
    (is (=' #"a|"  (xor' #"a" #"")))
    (is (=' #"|b"  (xor' #"" #"b")))
    (is (=' #"a"   (xor' #"a" #"a")))  ; Deduplication
    (is (=' #"a|b" (xor' #"a" #"b"))))
  (testing "xor-grp"
    (is (=' #""        (xor-grp nil nil)))
    (is (=' #"(?:a)"   (xor-grp #"a" nil)))
    (is (=' #"(?:a|)"  (xor-grp #"a" #"")))
    (is (=' #"(?:|b)"  (xor-grp #"" #"b")))
    (is (=' #"(?:a)"   (xor-grp #"a" #"a")))  ; Deduplication
    (is (=' #"(?:a|b)" (xor-grp #"a" #"b"))))
  (testing "xor-cg"
    (is (=' #"()"    (xor-cg nil nil)))
    (is (=' #"(a)"   (xor-cg #"a" nil)))
    (is (=' #"(a|)"  (xor-cg #"a" #"")))
    (is (=' #"(|b)"  (xor-cg #"" #"b")))
    (is (=' #"(a)"   (xor-cg #"a" #"a")))  ; Deduplication
    (is (=' #"(a|b)" (xor-cg #"a" #"b"))))
  (testing "xor-ncg"
    (is (=' #"()"                (xor-ncg nil nil nil)))
    (is (=' #"(?<groupName>)"    (xor-ncg "groupName" nil nil)))
    (is (=' #"(?<groupName>)"    (xor-ncg "groupName" #"" nil)))
    (is (=' #"(?<groupName>a)"   (xor-ncg "groupName" #"a" nil)))
    (is (=' #"(?<groupName>a|)"  (xor-ncg "groupName" #"a" #"")))
    (is (=' #"(?<groupName>|b)"  (xor-ncg "groupName" #"" #"b")))
    (is (=' #"(?<groupName>a)"   (xor-ncg "groupName" #"a" #"a")))  ; Deduplication
    (is (=' #"(?<groupName>a|b)" (xor-ncg "groupName" #"a" #"b"))))
  (testing "xor-fgrp"
    (is (=' #""                  (xor-fgrp nil nil nil)))
    (is (=' #""                  (xor-fgrp "i" nil nil)))
    (is (=' #"(?i:a)"   (xor-fgrp "i" #"a" nil)))
    (is (=' #"(?i:a|)"  (xor-fgrp "i" #"a" #"")))
    (is (=' #"(?i:|b)"  (xor-fgrp "i" #"" #"b")))
    (is (=' #"(?i:a)"   (xor-fgrp "i" #"a" #"a")))  ; Deduplication
    (is (=' #"(?i:a|b)" (xor-fgrp "i" #"a" #"b")))))

(defn- matches?
  [re s]
  (boolean (re-matches re s)))

(defn- finds?
  [re s]
  (boolean (re-find re s)))

#?(:clj
(deftest composite-tests
  (let [ws      (chcl #"\p{Space}\p{IsWhitespace}")
        ows     (zom ws)
        mws     (oom ws)
        lorl-re (or-grp "Lesser" "Library" (alt-grp (join ows "/" ows) (join mws "or" mws)))
        ; The following regex ends up being ~800 characters long, and yet it's easy to reason about
        lgpl-re (join
                  #"(?<!\w)"
                  (fgrp "i"
                    (alt-ncg "lgpl"
                      "LGPL"
                      (join "GNU" mws lorl-re mws "GPL")
                      (join "GNU" mws lorl-re)
                      (join lorl-re mws "GPL")))
                  #"(?!\w)")]
    (testing "Matching tests"
      ; Matches
      (is (true?  (matches? lgpl-re "LGPL")))
      (is (true?  (matches? lgpl-re "GNU Lesser")))
      (is (true?  (matches? lgpl-re "GNU Library")))
      (is (true?  (matches? lgpl-re "gnu lesser or library")))
      (is (true?  (matches? lgpl-re "gnu lesser/library")))
      (is (true?  (matches? lgpl-re "GNU LIBRARY OR LESSER")))
      (is (true?  (matches? lgpl-re "GNU LIBRARY / LESSER")))
      (is (true?  (matches? lgpl-re "Lesser GPL")))
      (is (true?  (matches? lgpl-re "Library GPL")))
      (is (true?  (matches? lgpl-re "Lesser or Library GPL")))
      (is (true?  (matches? lgpl-re "lIBRARY oR lESSER gpl")))
      (is (true?  (matches? lgpl-re "GNU Lesser or Library GPL")))
      (is (true?  (matches? lgpl-re "GNU Lesser/ Library GPL")))
      (is (true?  (matches? lgpl-re "GNU Lesser /Library GPL")))
      ; Non matches
      (is (false? (matches? lgpl-re "L GPL")))
      (is (false? (matches? lgpl-re "GNU")))
      (is (false? (matches? lgpl-re "GPL")))
      (is (false? (matches? lgpl-re "Lesser")))
      (is (false? (matches? lgpl-re "Library")))
      (is (false? (matches? lgpl-re "or")))
      (is (false? (matches? lgpl-re "Lesser or Library")))
      (is (false? (matches? lgpl-re "Lesser/Library")))
      (is (false? (matches? lgpl-re "Library or Lesser")))
      (is (false? (matches? lgpl-re "Library / Lesser")))
      (is (false? (matches? lgpl-re "GPL Library or Lesser")))
      (is (false? (matches? lgpl-re "Library or Lesser GNU"))))
    (testing "Finding tests"
      ; Finds
      (is (true?  (finds? lgpl-re "some text LGPL or more text")))
      (is (true?  (finds? lgpl-re "some text GNU Lesser or more text")))
      (is (true?  (finds? lgpl-re "some text GNU Library or more text")))
      (is (true?  (finds? lgpl-re "some text gnu lesser or library or more text")))
      (is (true?  (finds? lgpl-re "some text gnu lesser/library or more text")))
      (is (true?  (finds? lgpl-re "some text GNU LIBRARY OR LESSER or more text")))
      (is (true?  (finds? lgpl-re "some text GNU LIBRARY / LESSER or more text")))
      (is (true?  (finds? lgpl-re "some text Lesser GPL or more text")))
      (is (true?  (finds? lgpl-re "some text Library GPL or more text")))
      (is (true?  (finds? lgpl-re "some text Lesser or Library GPL or more text")))
      (is (true?  (finds? lgpl-re "some text lIBRARY oR lESSER gpl or more text")))
      ; Finds, but tricky - the re finds a subset of the entire phrase
      (is (true?  (finds? lgpl-re "some text GNU LIBRARY OR LESSERor more text")))  ; finds "GNU LIBRARY"
      (is (true?  (finds? lgpl-re "some textLesser or Library GPL or more text")))  ; finds "Library GPL"
      ; Non finds due to concatenated leading or trailing text
      (is (false? (finds? lgpl-re "some textLGPL or more text")))
      (is (false? (finds? lgpl-re "some text LGPLor more text")))
      (is (false? (finds? lgpl-re "some textGNU Lesser or more text")))
      (is (false? (finds? lgpl-re "some text GNU Libraryor more text")))
      (is (false? (finds? lgpl-re "some textgnu lesser or library or more text")))
      (is (false? (finds? lgpl-re "some textLesser GPL or more text")))
      (is (false? (finds? lgpl-re "some text Library GPLor more text")))
      (is (false? (finds? lgpl-re "some text lIBRARY oR lESSER gplor more text")))
      ; Non finds
      (is (false? (finds? lgpl-re "some text GNU or more text")))
      (is (false? (finds? lgpl-re "some text GPL or more text")))
      (is (false? (finds? lgpl-re "some text Lesser or more text")))
      (is (false? (finds? lgpl-re "some text Library or more text")))
      (is (false? (finds? lgpl-re "some text or or more text")))
      (is (false? (finds? lgpl-re "some text Lesser or Library or more text")))
      (is (false? (finds? lgpl-re "some text Library or Lesser or more text")))
      (is (false? (finds? lgpl-re "some text GPL Library or Lesser or more text")))
      (is (false? (finds? lgpl-re "some text Library or Lesser GNU or more text")))))))