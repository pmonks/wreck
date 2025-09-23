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
            [wreck.api        :refer [has-non-embeddable-flags?
                                      embed-flags
                                      str' ='      empty?'
                                      join esc     qot
                                      grp  cg      ncg    flags-grp
                                      opt  opt-grp opt-cg opt-ncg
                                      zom  zom-grp zom-cg zom-ncg
                                      oom  oom-grp oom-cg oom-ncg
                                      nom  nom-grp nom-cg nom-ncg
                                      exn  exn-grp exn-cg exn-ncg
                                      n2m  n2m-grp n2m-cg n2m-ncg
                                      alt  alt-grp alt-cg alt-ncg
                                      and' and-grp and-cg and-ncg
                                      or'  or-grp  or-cg  or-ncg
                                      xor' xor-grp xor-cg xor-ncg]]))

; Important note: because of the way reader conditionals work, regexes in _ALL_
; branches _ALWAYS_ get compiled on _ALL_ hosts. This means that platform-
; specific regexes will cause compilation errors on the _OTHER_ platform.  To
; get around this, we use (re-pattern) to move regex compilation from read time
; to runtime (which will always take reader conditionals into account).
; This is a niche corner case, and only due to the Clojure reader pre-dating
; reader conditionals by a lot.

#?(:cljs (enable-console-print!))

(deftest embed-flags-tests
  (testing "Basic cases - nil etc."
    (is (nil?     (embed-flags nil)))
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
    (is (nil?     (join)))
    (is (nil?     (join nil)))
    (is (nil?     (join nil nil)))
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
    (is (nil?        (qot nil)))
    (is (=' #"\Q\E"  (qot "")))
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
    (is (nil?                                           (grp)))
    (is (nil?                                           (grp nil)))
    (is (=' #""                                         (grp #"")))
    (is (=' #"(?:.*)"                                   (grp #".*")))
    (is (=' #"(?:.*)"                                   (grp #"" #".*")))
    (is (=' #"(?:foo.*)"                                (grp #"foo" #".*")))
    (is (=' #"(?:Apache(\s+Software)?(\s+Licen[cs]e)?)" (grp "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?"))))
  (testing "cg"
    (is (nil?                                         (cg)))
    (is (nil?                                         (cg nil)))
    (is (=' #"()"                                     (cg #"")))
    (is (=' #"(.*)"                                   (cg #".*")))
    (is (=' #"(.*)"                                   (cg #"" #".*")))
    (is (=' #"(foo.*)"                                (cg #"foo" #".*")))
    (is (=' #"(Apache(\s+Software)?(\s+Licen[cs]e)?)" (cg "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?"))))
  (testing "ncg"
    (is (nil?                                                  (ncg nil)))
    (is (nil?                                                  (ncg "")))
    (is (nil?                                                  (ncg "  ")))
    (is (nil?                                                  (ncg "\n")))
    (is (nil?                                                  (ncg "\n   \r\n  \t ")))
    (is (nil?                                                  (ncg nil nil)))
    (is (nil?                                                  (ncg "" nil)))
    (is (=' #"(?<groupName>)"                                  (ncg "groupName" #"")))
    (is (=' #"(?<groupName>.*)"                                (ncg "groupName" #".*")))
    (is (=' #"(?<groupName>.*)"                                (ncg "groupName" #"" #".*")))
    (is (=' #"(?<groupName>foo.*)"                             (ncg "groupName" #"foo" #".*")))
    (is (=' #"(?<apache>Apache(\s+Software)?(\s+Licen[cs]e)?)" (ncg "apache" "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?"))))
  (testing "flags-grp"
    (is (nil?                                             (flags-grp nil)))
    (is (nil?                                             (flags-grp "")))
    (is (nil?                                             (flags-grp " ")))
    (is (nil?                                             (flags-grp "\n")))
    (is (nil?                                             (flags-grp "\n   \r\n  \t ")))
    (is (nil?                                             (flags-grp nil nil)))
    (is (nil?                                             (flags-grp "" nil)))
    (is (nil?                                             (flags-grp " " nil)))
    (is (nil?                                             (flags-grp "\n" nil)))
    (is (nil?                                             (flags-grp "\n   \r\n  \t " nil)))
    (is (nil?                                             (flags-grp nil #"")))
    (is (nil?                                             (flags-grp nil #".*")))
    (is (nil?                                             (flags-grp nil #"ab" #"cd")))
    (is (nil?                                             (flags-grp ""  #"foo" #".*")))
    (is (nil?                                             (flags-grp "  " #"")))
    (is (=' #"(?i:)"                                      (flags-grp "i" #"")))
    (is (=' #"(?i:)"                                      (flags-grp "i" #"")))
    (is (=' #"(?i:.*)"                                    (flags-grp "i" #".*")))
    (is (=' #"(?im:.*)"                                   (flags-grp "mi" #".*")))
    (is (=' #"(?im:)"                                     (flags-grp "mi" #"")))
    (is (=' #"(?im:Apache(\s+Software)?(\s+Licen[cs]e)?)" (flags-grp "mi" "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?")))
    (is (thrown? #?(:clj  java.util.regex.PatternSyntaxException
                    :cljs js/SyntaxError)                  (flags-grp "42" #".*")))))

(deftest opt-variant-tests
  (testing "opt"
    (is (nil?                                           (opt nil)))
    (is (thrown? #?(:clj  java.util.regex.PatternSyntaxException
                    :cljs js/SyntaxError)               (opt #"")))
    (is (=' #"x?"                                       (opt #"x")))
    (is (=' #".*?"                                      (opt #".*")))
    (is (=' #"foo?"                                     (opt #"foo")))
    (is (=' #"Apache?"                                  (opt "Apache"))))
  (testing "opt-grp"
    (is (nil?                                            (opt-grp)))
    (is (nil?                                            (opt-grp nil)))
    (is (thrown? #?(:clj  java.util.regex.PatternSyntaxException
                    :cljs js/SyntaxError)                (opt-grp #"")))  ; Throws because of optimisation of empty non-capturing groups
    (is (=' #"(?:x)?"                                    (opt-grp #"x")))
    (is (=' #"(?:.*)?"                                   (opt-grp #".*")))
    (is (=' #"(?:foo)?"                                  (opt-grp #"foo")))
    (is (=' #"(?:Apache)?"                               (opt-grp "Apache")))
    (is (=' #"(?:Apache(\s+Software)?(\s+Licen[cs]e)?)?" (opt-grp "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?"))))
  (testing "opt-cg"
    (is (nil?                                          (opt-cg)))
    (is (nil?                                          (opt-cg nil)))
    (is (=' #"()?"                                     (opt-cg #"")))
    (is (=' #"(x)?"                                    (opt-cg #"x")))
    (is (=' #"(.*)?"                                   (opt-cg #".*")))
    (is (=' #"(foo)?"                                  (opt-cg #"foo")))
    (is (=' #"(Apache)?"                               (opt-cg "Apache")))
    (is (=' #"(Apache(\s+Software)?(\s+Licen[cs]e)?)?" (opt-cg "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?"))))
  (testing "opt-ncg"
    (is (nil?                                                   (opt-ncg nil)))
    (is (nil?                                                   (opt-ncg nil nil)))
    (is (nil?                                                   (opt-ncg "groupName" nil)))
    (is (=' #"(?<groupName>)?"                                  (opt-ncg "groupName" #"")))
    (is (=' #"(?<groupName>x)?"                                 (opt-ncg "groupName" #"x")))
    (is (=' #"(?<groupName>.*)?"                                (opt-ncg "groupName" #".*")))
    (is (=' #"(?<groupName>foo)?"                               (opt-ncg "groupName" #"foo")))
    (is (=' #"(?<apache>Apache)?"                               (opt-ncg "apache"    "Apache")))
    (is (=' #"(?<apache>Apache(\s+Software)?(\s+Licen[cs]e)?)?" (opt-ncg "apache"    "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?")))))

(deftest zom-variant-tests
  (testing "zom"
    (is (nil?                                           (zom nil)))
    (is (thrown? #?(:clj  java.util.regex.PatternSyntaxException
                    :cljs js/SyntaxError)               (zom #"")))
    (is (=' #"x*")                                      (zom #"x"))
    (is (thrown? #?(:clj  java.util.regex.PatternSyntaxException
                    :cljs js/SyntaxError)               (zom #".*")))
    (is (=' #"foo*"                                     (zom #"foo")))
    (is (=' #"Apache*"                                  (zom "Apache"))))
  (testing "zom-grp"
    (is (nil?                                            (zom-grp)))
    (is (nil?                                            (zom-grp nil)))
    (is (thrown? #?(:clj  java.util.regex.PatternSyntaxException
                    :cljs js/SyntaxError)                (zom-grp #"")))  ; Throws because of optimisation of empty non-capturing groups
    (is (=' #"(?:x)*"                                    (zom-grp #"x")))
    (is (=' #"(?:.*)*"                                   (zom-grp #".*")))
    (is (=' #"(?:foo)*"                                  (zom-grp #"foo")))
    (is (=' #"(?:Apache)*"                               (zom-grp "Apache")))
    (is (=' #"(?:Apache(\s+Software)?(\s+Licen[cs]e)?)*" (zom-grp "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?"))))
  (testing "zom-cg"
    (is (nil?                                          (zom-cg)))
    (is (nil?                                          (zom-cg nil)))
    (is (=' #"()*"                                     (zom-cg #"")))
    (is (=' #"(x)*"                                    (zom-cg #"x")))
    (is (=' #"(.*)*"                                   (zom-cg #".*")))
    (is (=' #"(foo)*"                                  (zom-cg #"foo")))
    (is (=' #"(Apache)*"                               (zom-cg "Apache")))
    (is (=' #"(Apache(\s+Software)?(\s+Licen[cs]e)?)*" (zom-cg "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?"))))
  (testing "zom-ncg"
    (is (nil?                                                   (zom-ncg nil)))
    (is (nil?                                                   (zom-ncg nil nil)))
    (is (nil?                                                   (zom-ncg "groupName" nil)))
    (is (=' #"(?<groupName>)*"                                  (zom-ncg "groupName" #"")))
    (is (=' #"(?<groupName>x)*"                                 (zom-ncg "groupName" #"x")))
    (is (=' #"(?<groupName>.*)*"                                (zom-ncg "groupName" #".*")))
    (is (=' #"(?<groupName>foo)*"                               (zom-ncg "groupName" #"foo")))
    (is (=' #"(?<apache>Apache)*"                               (zom-ncg "apache"    "Apache")))
    (is (=' #"(?<apache>Apache(\s+Software)?(\s+Licen[cs]e)?)*" (zom-ncg "apache"    "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?")))))

(deftest oom-variant-tests
  (testing "oom"
    (is (nil?                                           (oom nil)))
    (is (thrown? #?(:clj  java.util.regex.PatternSyntaxException
                    :cljs js/SyntaxError)               (oom #"")))
    (is (=' #"x+")                                      (oom #"x"))
#?(:clj  (is (=' #".*+"                                 (oom #".*")))   ; Valid (but nonsensical) regex on ClojureJVM
   :cljs (is (thrown? js/SyntaxError                    (oom #".*"))))  ; Invalid regex on ClojureScript
    (is (=' #"foo+"                                     (oom #"foo")))
    (is (=' #"Apache+"                                  (oom "Apache"))))
  (testing "oom-grp"
    (is (nil?                                            (oom-grp)))
    (is (nil?                                            (oom-grp nil)))
    (is (thrown? #?(:clj  java.util.regex.PatternSyntaxException
                    :cljs js/SyntaxError)                (oom-grp #"")))  ; Throws because of optimisation of empty non-capturing groups
    (is (=' #"(?:x)+"                                    (oom-grp #"x")))
    (is (=' #"(?:.*)+"                                   (oom-grp #".*")))
    (is (=' #"(?:foo)+"                                  (oom-grp #"foo")))
    (is (=' #"(?:Apache)+"                               (oom-grp "Apache")))
    (is (=' #"(?:Apache(\s+Software)?(\s+Licen[cs]e)?)+" (oom-grp "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?"))))
  (testing "oom-cg"
    (is (nil?                                          (oom-cg)))
    (is (nil?                                          (oom-cg nil)))
    (is (=' #"()+"                                     (oom-cg #"")))
    (is (=' #"(x)+"                                    (oom-cg #"x")))
    (is (=' #"(.*)+"                                   (oom-cg #".*")))
    (is (=' #"(foo)+"                                  (oom-cg #"foo")))
    (is (=' #"(Apache)+"                               (oom-cg "Apache")))
    (is (=' #"(Apache(\s+Software)?(\s+Licen[cs]e)?)+" (oom-cg "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?"))))
  (testing "oom-ncg"
    (is (nil?                                                   (oom-ncg nil)))
    (is (nil?                                                   (oom-ncg nil nil)))
    (is (nil?                                                   (oom-ncg "groupName" nil)))
    (is (=' #"(?<groupName>)+"                                  (oom-ncg "groupName" #"")))
    (is (=' #"(?<groupName>x)+"                                 (oom-ncg "groupName" #"x")))
    (is (=' #"(?<groupName>.*)+"                                (oom-ncg "groupName" #".*")))
    (is (=' #"(?<groupName>foo)+"                               (oom-ncg "groupName" #"foo")))
    (is (=' #"(?<apache>Apache)+"                               (oom-ncg "apache"    "Apache")))
    (is (=' #"(?<apache>Apache(\s+Software)?(\s+Licen[cs]e)?)+" (oom-ncg "apache"    "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?")))))

(deftest nom-variant-tests
  (testing "nom"
    (is (nil?                        (nom nil nil)))
    (is (nil?                        (nom nil #"")))
    (is (nil?                        (nom 2 nil)))
    (is (=' #"x{5,}"                 (nom 5 #"x")))
#?(:clj  (is (=' #".*{3,}"           (nom 3 #".*")))   ; Valid (but nonsensical) regex on ClojureJVM
   :cljs (is (thrown? js/SyntaxError (nom 3 #".*"))))  ; Invalid regex on ClojureScript
    (is (=' #"foo{2,}"               (nom 2 #"foo")))  ; Note how this doesn't result in optionality being applied to the entirety of the input - that's what nom-grp etc. are for
    (is (=' #"Apache{17,}"           (nom 17 "Apache"))))
  (testing "nom-grp"
    (is (nil?                                               (nom-grp nil nil)))
    (is (nil?                                               (nom-grp 3 nil)))
#?(:clj  (is (=' #"{246,}"                                  (nom-grp 246 #"")))   ; Valid (but nonsensical) regex on ClojureJVM
   :cljs (is (thrown? js/SyntaxError                        (nom-grp 246 #""))))  ; Invalid regex on ClojureScript
    (is (=' #"(?:x){0,}"                                    (nom-grp 0 #"x")))
    (is (=' #"(?:.*){7,}"                                   (nom-grp 7 #".*")))
    (is (=' #"(?:foo){42,}"                                 (nom-grp 42 #"foo")))
    (is (=' #"(?:Apache){12,}"                              (nom-grp 12 "Apache")))
    (is (=' #"(?:Apache(\s+Software)?(\s+Licen[cs]e)?){5,}" (nom-grp 5 "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?"))))
  (testing "nom-cg"
    (is (nil?                                             (nom-cg nil nil)))
    (is (=' #"(){3,}"                                     (nom-cg 3 #"")))
    (is (=' #"(x){4,}"                                    (nom-cg 4 #"x")))
    (is (=' #"(.*){5,}"                                   (nom-cg 5 #".*")))
    (is (=' #"(foo){6,}"                                  (nom-cg 6 #"foo")))
    (is (=' #"(Apache){7,}"                               (nom-cg 7 "Apache")))
    (is (=' #"(Apache(\s+Software)?(\s+Licen[cs]e)?){8,}" (nom-cg 8 "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?"))))
  (testing "nom-ncg"
    (is (nil?                                                      (nom-ncg nil nil)))
    (is (nil?                                                      (nom-ncg nil nil nil)))
    (is (nil?                                                      (nom-ncg "groupName" 7 nil)))
    (is (=' #"(?<groupName>){6,}"                                  (nom-ncg "groupName" 6 #"")))
    (is (=' #"(?<groupName>x){5,}"                                 (nom-ncg "groupName" 5 #"x")))
    (is (=' #"(?<groupName>.*){4,}"                                (nom-ncg "groupName" 4 #".*")))
    (is (=' #"(?<groupName>foo){3,}"                               (nom-ncg "groupName" 3 #"foo")))
    (is (=' #"(?<apache>Apache){2,}"                               (nom-ncg "apache"    2 "Apache")))
    (is (=' #"(?<apache>Apache(\s+Software)?(\s+Licen[cs]e)?){1,}" (nom-ncg "apache"    1 "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?")))))

(deftest exn-variant-tests
  (testing "exn"
    (is (nil?                        (exn nil nil)))
    (is (nil?                        (exn nil #"")))
    (is (nil?                        (exn 2 nil)))
#?(:clj  (is (=' #"{2}"              (exn 2 #"")))   ; Valid (but nonsensical) regex on ClojureJVM
   :cljs (is (thrown? js/SyntaxError (exn 2 #""))))  ; Invalid regex on ClojureScript
    (is (=' #"x{5}"                  (exn 5 #"x")))
#?(:clj  (is (=' #".*{3}"            (exn 3 #".*")))   ; Valid (but nonsensical) regex on ClojureJVM
   :cljs (is (thrown? js/SyntaxError (exn 3 #".*"))))  ; Invalid regex on ClojureScript
    (is (=' #"foo{2}"                (exn 2 #"foo")))  ; Note how this doesn't result in optionality being applied to the entirety of the input - that's what nom-grp etc. are for
    (is (=' #"Apache{17}"            (exn 17 "Apache"))))
  (testing "exn-grp"
    (is (nil?                                              (exn-grp nil nil)))
    (is (nil?                                              (exn-grp 3 nil)))
#?(:clj  (is (=' #"{246}"                                  (exn-grp 246 #"")))   ; Valid (but nonsensical) regex on ClojureJVM
   :cljs (is (thrown? js/SyntaxError                       (exn-grp 246 #""))))  ; Invalid regex on ClojureScript
    (is (=' #"(?:x){0}"                                    (exn-grp 0 #"x")))
    (is (=' #"(?:.*){7}"                                   (exn-grp 7 #".*")))
    (is (=' #"(?:foo){42}"                                 (exn-grp 42 #"foo")))
    (is (=' #"(?:Apache){12}"                              (exn-grp 12 "Apache")))
    (is (=' #"(?:Apache(\s+Software)?(\s+Licen[cs]e)?){5}" (exn-grp 5 "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?"))))
  (testing "exn-cg"
    (is (nil?                                            (exn-cg nil nil)))
    (is (nil?                                            (exn-cg 3 nil)))
    (is (=' #"(){3}"                                     (exn-cg 3 #"")))  ; Note: empty capturing groups are _not_ optimised out, since doing so could break code that indexes into the matched groups
    (is (=' #"(x){4}"                                    (exn-cg 4 #"x")))
    (is (=' #"(.*){5}"                                   (exn-cg 5 #".*")))
    (is (=' #"(foo){6}"                                  (exn-cg 6 #"foo")))
    (is (=' #"(Apache){7}"                               (exn-cg 7 "Apache")))
    (is (=' #"(Apache(\s+Software)?(\s+Licen[cs]e)?){8}" (exn-cg 8 "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?"))))
  (testing "exn-ncg"
    (is (nil?                                                     (exn-ncg nil nil)))
    (is (nil?                                                     (exn-ncg nil nil nil)))
    (is (nil?                                                     (exn-ncg "groupName" 7 nil)))
    (is (=' #"(?<groupName>){6}"                                  (exn-ncg "groupName" 6 #"")))
    (is (=' #"(?<groupName>x){5}"                                 (exn-ncg "groupName" 5 #"x")))
    (is (=' #"(?<groupName>.*){4}"                                (exn-ncg "groupName" 4 #".*")))
    (is (=' #"(?<groupName>foo){3}"                               (exn-ncg "groupName" 3 #"foo")))
    (is (=' #"(?<apache>Apache){2}"                               (exn-ncg "apache"    2 "Apache")))
    (is (=' #"(?<apache>Apache(\s+Software)?(\s+Licen[cs]e)?){1}" (exn-ncg "apache"    1 "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?")))))

(deftest n2m-variant-tests
  (testing "n2m"
    (is (nil?                        (n2m nil nil nil)))
    (is (nil?                        (n2m nil nil #"")))
    (is (nil?                        (n2m 2 4 nil)))
#?(:clj  (is (=' #"{2,4}"            (n2m 2 4 #"")))   ; Valid (but nonsensical) regex on ClojureJVM
   :cljs (is (thrown? js/SyntaxError (n2m 2 4 #""))))  ; Invalid regex on ClojureScript
    (is (=' #"x{2,4}"                (n2m 2 4 #"x")))
#?(:clj  (is (=' #".*{3,7}"          (n2m 3 7 #".*")))   ; Valid (but nonsensical) regex on ClojureJVM
   :cljs (is (thrown? js/SyntaxError (n2m 3 7 #".*"))))  ; Invalid regex on ClojureScript
    (is (=' #"foo{2,2}"              (n2m 2 2 #"foo")))  ; Note how this doesn't result in optionality being applied to the entirety of the input - that's what nom-grp etc. are for
    (is (=' #"Apache{17,21}"         (n2m 17 21 "Apache"))))
  (testing "n2m-grp"
    (is (nil?                                                 (n2m-grp nil nil nil)))
    (is (nil?                                                 (n2m-grp 3 100 nil)))
#?(:clj  (is (=' #"{246,250}"                                 (n2m-grp 246 250 #"")))   ; Valid (but nonsensical) regex on ClojureJVM
   :cljs (is (thrown? js/SyntaxError                          (n2m-grp 246 250 #""))))  ; Invalid regex on ClojureScript
    (is (=' #"(?:x){0,3}"                                     (n2m-grp 0 3 #"x")))
    (is (=' #"(?:.*){7,8}"                                    (n2m-grp 7 8 #".*")))
    (is (=' #"(?:foo){42,69}"                                 (n2m-grp 42 69 #"foo")))
    (is (=' #"(?:Apache){12,13}"                              (n2m-grp 12 13 "Apache")))
    (is (=' #"(?:Apache(\s+Software)?(\s+Licen[cs]e)?){5,99}" (n2m-grp 5 99 "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?"))))
  (testing "n2m-cg"
    (is (nil?                                              (n2m-cg nil nil nil)))
    (is (=' #"(){3,4}"                                     (n2m-cg 3 4 #"")))
    (is (=' #"(x){4,5}"                                    (n2m-cg 4 5 #"x")))
    (is (=' #"(.*){5,6}"                                   (n2m-cg 5 6 #".*")))
    (is (=' #"(foo){6,7}"                                  (n2m-cg 6 7 #"foo")))
    (is (=' #"(Apache){7,8}"                               (n2m-cg 7 8 "Apache")))
    (is (=' #"(Apache(\s+Software)?(\s+Licen[cs]e)?){8,9}" (n2m-cg 8 9 "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?"))))
  (testing "n2m-ncg"
    (is (nil?                                                        (n2m-ncg nil nil nil)))
    (is (nil?                                                        (n2m-ncg nil nil nil nil)))
    (is (nil?                                                        (n2m-ncg "groupName" 7  8 nil)))
    (is (=' #"(?<groupName>){6,9}"                                   (n2m-ncg "groupName" 6  9 #"")))
    (is (=' #"(?<groupName>x){5,10}"                                 (n2m-ncg "groupName" 5 10 #"x")))
    (is (=' #"(?<groupName>.*){4,11}"                                (n2m-ncg "groupName" 4 11 #".*")))
    (is (=' #"(?<groupName>foo){3,12}"                               (n2m-ncg "groupName" 3 12 #"foo")))
    (is (=' #"(?<apache>Apache){2,13}"                               (n2m-ncg "apache"    2 13 "Apache")))
    (is (=' #"(?<apache>Apache(\s+Software)?(\s+Licen[cs]e)?){1,14}" (n2m-ncg "apache"    1 14 "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?")))))

(deftest alt-variant-tests
  (testing "alt"
    (is (nil?                      (alt nil)))
    (is (nil?                      (alt nil nil)))
    (is (=' #""                    (alt #"")))
    (is (=' #"a"                   (alt #"a")))
    (is (=' #""                    (alt #"" #"")))
    (is (=' #"foo|bar"             (alt #"foo" #"bar")))
    (is (=' #"foo"                 (alt "foo" "foo")))   ; Deduplication
    (is (=' #"foo"                 (alt "foo" #"foo")))  ; Deduplication
    (is (=' #"0"                   (alt 0 "0" #"0")))    ; Deduplication
    (is (=' #"0|1|2|3|4|5|6|7|8|9" (apply alt (range 10)))))
  (testing "alt-grp"
    (is (nil?                          (alt-grp nil)))
    (is (nil?                          (alt-grp nil nil)))
    (is (=' #""                        (alt-grp #"")))  ; Optimisation of empty non-capturing groups
    (is (=' #"(?:a)"                   (alt-grp #"a")))
    (is (=' #""                        (alt-grp #"" #"")))  ; Optimisation of empty non-capturing groups
    (is (=' #"(?:foo|bar)"             (alt-grp #"foo" #"bar")))
    (is (=' #"(?:0|1|2|3|4|5|6|7|8|9)" (apply alt-grp (range 10))))
    (is (=' #"(?:0|1|2|3|4|5|6|7|8|9)" (apply alt-grp (concat (range 10) (map str (range 10)))))))  ; Deduplication of equivalent regexes
  (testing "alt-cg"
    (is (nil?                        (alt-cg nil)))
    (is (nil?                        (alt-cg nil nil)))
    (is (=' #"()"                    (alt-cg #"")))
    (is (=' #"(a)"                   (alt-cg #"a")))
    (is (=' #"()"                    (alt-cg #"" #"")))  ; Nonsensical, but ensure we have well defined behaviour anyway
    (is (=' #"(foo|bar)"             (alt-cg #"foo" #"bar")))
    (is (=' #"(0|1|2|3|4|5|6|7|8|9)" (apply alt-cg (range 10))))
    (is (=' #"(0|1|2|3|4|5|6|7|8|9)" (apply alt-cg (concat (range 10) (map str (range 10)))))))  ; Deduplication of equivalent regexes
  (testing "alt-ncg"
    (is (nil?                                  (alt-ncg nil nil)))
    (is (nil?                                  (alt-ncg nil nil)))
    (is (nil?                                  (alt-ncg nil nil nil)))
    (is (nil?                                  (alt-ncg "groupName" nil)))
    (is (=' #"(?<groupName>)"                  (alt-ncg "groupName" #"")))
    (is (=' #"(?<groupName>a)"                 (alt-ncg "groupName" #"a")))
    (is (=' #"(?<groupName>)"                  (alt-ncg "groupName" #"" #"")))  ; Nonsensical, but ensure we have well defined behaviour anyway
    (is (=' #"(?<groupName>foo|bar)"           (alt-ncg "groupName" #"foo" #"bar")))
    (is (=' #"(?<numbers>0|1|2|3|4|5|6|7|8|9)" (apply (partial alt-ncg "numbers") (range 10))))
    (is (=' #"(?<numbers>0|1|2|3|4|5|6|7|8|9)" (apply (partial alt-ncg "numbers") (concat (range 10) (map str (range 10))))))))  ; Deduplication of equivalent regexes

(deftest and-variant-tests
  (testing "and'"
    (is (nil?              (and' nil nil)))
    (is (nil?              (and' nil nil nil)))
    (is (nil?              (and' nil nil #"\s+")))
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
    (is (nil?                  (and-grp nil nil)))
    (is (nil?                  (and-grp nil nil nil)))
    (is (=' #"(?:a)"           (and-grp #"a" nil)))
    (is (=' #"(?:a)"           (and-grp #"a" #"")))  ; Optimisation
    (is (=' #"(?:b)"           (and-grp #"" #"b")))  ; Optimisation
    (is (=' #"(?:aa)"          (and-grp #"a" #"a")))  ; Optimisation
    (is (=' #"(?:ab|ba)"       (and-grp #"a" #"b")))
    (is (=' #"(?:ab|ba)"       (and-grp #"a" #"b" nil)))
    (is (=' #"(?:a\s+b|b\s+a)" (and-grp #"a" #"b" #"\s+"))))
  (testing "and-cg"
    (is (nil?                (and-cg nil nil)))
    (is (nil?                (and-cg nil nil nil)))
    (is (=' #"(a)"           (and-cg #"a" nil)))
    (is (=' #"(a)"           (and-cg #"a" #"")))  ; Optimisation
    (is (=' #"(b)"           (and-cg #"" #"b")))  ; Optimisation
    (is (=' #"(aa)"          (and-cg #"a" #"a")))  ; Optimisation
    (is (=' #"(ab|ba)"       (and-cg #"a" #"b")))
    (is (=' #"(ab|ba)"       (and-cg #"a" #"b" nil)))
    (is (=' #"(a\s+b|b\s+a)" (and-cg #"a" #"b" #"\s+"))))
  (testing "and-ncg"
    (is (nil?                            (and-ncg nil nil nil)))
    (is (nil?                            (and-ncg nil nil nil nil)))
    (is (=' #"(?<groupName>a)"           (and-ncg "groupName" #"a" nil)))
    (is (=' #"(?<groupName>a)"           (and-ncg "groupName" #"a" #"")))  ; Optimisation
    (is (=' #"(?<groupName>b)"           (and-ncg "groupName" #"" #"b")))  ; Optimisation
    (is (=' #"(?<groupName>aa)"          (and-ncg "groupName" #"a" #"a")))  ; Optimisation
    (is (=' #"(?<groupName>ab|ba)"       (and-ncg "groupName" #"a" #"b")))
    (is (=' #"(?<groupName>ab|ba)"       (and-ncg "groupName" #"a" #"b" nil)))
    (is (=' #"(?<groupName>a\s+b|b\s+a)" (and-ncg "groupName" #"a" #"b" #"\s+")))))

(deftest or-variant-tests
  (testing "or'"
    (is (nil?                  (or' nil nil)))
    (is (nil?                  (or' nil nil nil)))
    (is (nil?                  (or' nil nil #"\s+")))
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
    (is (nil?                      (or-grp nil nil)))
    (is (nil?                      (or-grp nil nil nil)))
    (is (=' #"(?:a)"               (or-grp #"a" nil)))
    (is (=' #"(?:a|)"              (or-grp #"a" #"")))  ; Optimisation
    (is (=' #"(?:b|)"              (or-grp #"" #"b")))  ; Note how order is not what we might expect (but it is correct!)
    (is (=' #"(?:aa|a)"            (or-grp #"a" #"a")))  ; Optimisation
    (is (=' #"(?:ab|ba|a|b)"       (or-grp #"a" #"b")))
    (is (=' #"(?:ab|ba|a|b)"       (or-grp #"a" #"b" nil)))
    (is (=' #"(?:a\s+b|b\s+a|a|b)" (or-grp #"a" #"b" #"\s+"))))
  (testing "or-cg"
    (is (nil?                    (or-cg nil nil)))
    (is (nil?                    (or-cg nil nil nil)))
    (is (=' #"(a)"               (or-cg #"a" nil)))
    (is (=' #"(a|)"              (or-cg #"a" #"")))  ; Optimisation
    (is (=' #"(b|)"              (or-cg #"" #"b")))  ; Note how order is not what we might expect (but it is correct!)
    (is (=' #"(aa|a)"            (or-cg #"a" #"a")))  ; Optimisation
    (is (=' #"(ab|ba|a|b)"       (or-cg #"a" #"b")))
    (is (=' #"(ab|ba|a|b)"       (or-cg #"a" #"b" nil)))
    (is (=' #"(a\s+b|b\s+a|a|b)" (or-cg #"a" #"b" #"\s+"))))
  (testing "or-ncg"
    (is (nil?                                (or-ncg nil nil nil)))
    (is (nil?                                (or-ncg nil nil nil nil)))
    (is (nil?                                (or-ncg "groupName" nil nil nil)))
    (is (=' #"(?<groupName>a)"               (or-ncg "groupName" #"a" nil)))
    (is (=' #"(?<groupName>a|)"              (or-ncg "groupName" #"a" #"")))  ; Optimisation
    (is (=' #"(?<groupName>b|)"              (or-ncg "groupName" #"" #"b")))  ; Note how order is not what we might expect (but it is correct!)
    (is (=' #"(?<groupName>aa|a)"            (or-ncg "groupName" #"a" #"a")))  ; Optimisation
    (is (=' #"(?<groupName>ab|ba|a|b)"       (or-ncg "groupName" #"a" #"b")))
    (is (=' #"(?<groupName>ab|ba|a|b)"       (or-ncg "groupName" #"a" #"b" nil)))
    (is (=' #"(?<groupName>a\s+b|b\s+a|a|b)" (or-ncg "groupName" #"a" #"b" #"\s+")))))

(deftest xor-variant-tests
  (testing "xor'"
    (is (nil?      (xor' nil nil)))
    (is (=' #"a"   (xor' #"a" nil)))
    (is (=' #"b"   (xor' nil #"b")))
    (is (=' #"a|"  (xor' #"a" #"")))
    (is (=' #"|b"  (xor' #"" #"b")))
    (is (=' #"a"   (xor' #"a" #"a")))  ; Optimisation
    (is (=' #"a|b" (xor' #"a" #"b"))))
  (testing "xor-grp"
    (is (nil?          (xor-grp nil nil)))
    (is (=' #"(?:a)"   (xor-grp #"a" nil)))
    (is (=' #"(?:a|)"  (xor-grp #"a" #"")))
    (is (=' #"(?:|b)"  (xor-grp #"" #"b")))
    (is (=' #"(?:a)"   (xor-grp #"a" #"a")))  ; Optimisation
    (is (=' #"(?:a|b)" (xor-grp #"a" #"b"))))
  (testing "xor-cg"
    (is (nil?        (xor-cg nil nil)))
    (is (=' #"(a)"   (xor-cg #"a" nil)))
    (is (=' #"(a|)"  (xor-cg #"a" #"")))
    (is (=' #"(|b)"  (xor-cg #"" #"b")))
    (is (=' #"(a)"   (xor-cg #"a" #"a")))  ; Optimisation
    (is (=' #"(a|b)" (xor-cg #"a" #"b"))))
  (testing "xor-ncg"
    (is (nil?                    (xor-ncg nil nil nil)))
    (is (nil?                    (xor-ncg "groupName" nil nil)))
    (is (=' #"(?<groupName>a)"   (xor-ncg "groupName" #"a" nil)))
    (is (=' #"(?<groupName>a|)"  (xor-ncg "groupName" #"a" #"")))
    (is (=' #"(?<groupName>|b)"  (xor-ncg "groupName" #"" #"b")))
    (is (=' #"(?<groupName>a)"   (xor-ncg "groupName" #"a" #"a")))  ; Optimisation
    (is (=' #"(?<groupName>a|b)" (xor-ncg "groupName" #"a" #"b")))))

(defn- matches?
  [re s]
  (boolean (re-matches re s)))

(defn- finds?
  [re s]
  (boolean (re-find re s)))

#?(:clj
(deftest composite-tests
  ; The following regex ends up being ~300 characters long, partly because of the sheer number of times the words
  ; "Lesser" and "Library" appear in it (in order to implement the nested alt/or)
  (let [lorl-re (or-grp "Lesser" "Library" (alt-grp #"\s*/\s*" #"\s+or\s+"))
        lgpl-re (join
                  #"(?<!\w)"
                  (flags-grp "i"
                    (alt-ncg "lgpl"
                      "LGPL"
                      (join "GNU" #"\s+" lorl-re #"\s+" "GPL")
                      (join "GNU" #"\s+" lorl-re)
                      (join lorl-re #"\s+" "GPL")))
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