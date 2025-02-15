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
  (:require [clojure.string :as s]
            [clojure.test   :refer [deftest testing is]]
            [wreck.api      :refer [=' join esc qot
                                    grp  cg      ncg
                                    opt  opt-grp opt-cg opt-ncg
                                    zom  zom-grp zom-cg zom-ncg
                                    oom  oom-grp oom-cg oom-ncg
                                    nom  nom-grp nom-cg nom-ncg
                                    exn  exn-grp exn-cg exn-ncg
                                    n2m  n2m-grp n2m-cg n2m-ncg
                                    alt  alt-grp
                                    and' and-grp
                                    or'  or-grp]]))

(deftest equality-tests
  (testing "Equal"
    (is (true?  (=' #""   #"")))
    (is (true?  (=' #".*" #".*"))))
  (testing "Not equal"
    (is (false? (=' #"" #" ")))
    (is (false? (=' #"..." #".{3}"))))
  (testing "Variable arguments"
    (is (true?  (=' #"")))
    (is (true?  (=' #"" #"")))
    (is (true?  (=' #"" #"" #"")))
    (is (true?  (=' #"" #"" #"" #"")))
    (is (true?  (=' #"" #"" #"" #"" #"" #"" #"" #"")))
    (is (false? (=' #"." #"" #"" #"" #"" #"" #"" #"")))
    (is (false? (=' #"" #"" #"" #"" #"" #"" #"" #".")))))

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
  (testing "join - string"
    (is (=' #".*" (join ".*" "")))
    (is (=' #".*" (join "" ".*")))
    (is (=' #".*" (join "" "" ".*"))))
  (testing "join - mixed"
    (is (=' #"(.*)"                        (join "(" #".*" ")")))
    (is (=' #"Apache(\s+Software)?License" (join "Apache" #"(\s+Software)?" "License")))))

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
    ; Note: whitespace literals (such as \n, \r and \t) act strangely in Clojure regex literals
  (testing "qot"
    (is (=' #"\Qfoo\E" (qot "foo")))
    (is (=' #"\Qfoo\E" (qot #"foo")))  ; Technically quoting regexes is a Bad Idea™, but we test a simple example just in case
    (is (=' #"\Q.*\E"  (qot ".*")))))

(deftest basic-grouping-tests
  (testing "grp"
    (is (nil?                                           (grp)))
    (is (=' #"(?:.*)"                                   (grp #".*")))
    (is (=' #"(?:.*)"                                   (grp #"" #".*")))
    (is (=' #"(?:foo.*)"                                (grp #"foo" #".*")))
    (is (=' #"(?:Apache(\s+Software)?(\s+Licen[cs]e)?)" (grp "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?"))))
  (testing "cg"
    (is (nil?                                         (cg)))
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
    (is (=' #"(?<groupName>.*)"                                (ncg "groupName" #".*")))
    (is (=' #"(?<groupName>.*)"                                (ncg "groupName" #"" #".*")))
    (is (=' #"(?<groupName>foo.*)"                             (ncg "groupName" #"foo" #".*")))
    (is (=' #"(?<apache>Apache(\s+Software)?(\s+Licen[cs]e)?)" (ncg "apache" "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?")))))

(deftest opt-variant-tests
  (testing "opt"
    (is (nil?                                           (opt nil)))
    (is (thrown? java.util.regex.PatternSyntaxException (opt #"")))
    (is (=' #"x?"                                       (opt #"x")))
    (is (=' #".*?"                                      (opt #".*")))
    (is (=' #"foo?"                                     (opt #"foo")))  ; Note how this doesn't result in optionality being applied to the entirety of the input - that's what opt-grp etc. are for
    (is (=' #"Apache?"                                  (opt "Apache"))))
  (testing "opt-grp"
    (is (nil?                                            (opt-grp)))
    (is (nil?                                            (opt-grp nil)))
    (is (=' #"(?:)?"                                     (opt-grp #"")))
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
    (is (thrown? java.util.regex.PatternSyntaxException (zom #"")))
    (is (=' #"x*")                                      (zom #"x"))
    (is (thrown? java.util.regex.PatternSyntaxException (zom #".*")))
    (is (=' #"foo*"                                     (zom #"foo")))  ; Note how this doesn't result in optionality being applied to the entirety of the input - that's what zom-grp etc. are for
    (is (=' #"Apache*"                                  (zom "Apache"))))
  (testing "zom-grp"
    (is (nil?                                            (zom-grp)))
    (is (nil?                                            (zom-grp nil)))
    (is (=' #"(?:)*"                                     (zom-grp #"")))
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
    (is (thrown? java.util.regex.PatternSyntaxException (oom #"")))
    (is (=' #"x+")                                      (oom #"x"))
;    (is (thrown? java.util.regex.PatternSyntaxException (oom #".*")))  ; Note: valid (but nonsensical) on ClojureJVM, but _not_ valid on ClojureScript, so we don't test this
    (is (=' #"foo+"                                     (oom #"foo")))  ; Note how this doesn't result in optionality being applied to the entirety of the input - that's what oom-grp etc. are for
    (is (=' #"Apache+"                                  (oom "Apache"))))
  (testing "oom-grp"
    (is (nil?                                            (oom-grp)))
    (is (nil?                                            (oom-grp nil)))
    (is (=' #"(?:)+"                                     (oom-grp #"")))
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
    (is (nil?                                           (nom nil nil)))
    (is (nil?                                           (nom nil #"")))
    (is (nil?                                           (nom 2 nil)))
    (is (=' #"x{5,}"                                    (nom 5 #"x")))
;    (is (thrown? java.util.regex.PatternSyntaxException (nom 3 #".*")))  ; Note: valid (but nonsensical) on ClojureJVM, but _not_ valid on ClojureScript, so we don't test this
    (is (=' #"foo{2,}"                                  (nom 2 #"foo")))  ; Note how this doesn't result in optionality being applied to the entirety of the input - that's what nom-grp etc. are for
    (is (=' #"Apache{17,}"                              (nom 17 "Apache"))))
  (testing "nom-grp"
    (is (nil?                                               (nom-grp nil nil)))
    (is (nil?                                               (nom-grp 3 nil)))
    (is (=' #"(?:){246,}"                                   (nom-grp 246 #""))))
    (is (=' #"(?:x){0,}"                                    (nom-grp 0 #"x")))
    (is (=' #"(?:.*){7,}"                                   (nom-grp 7 #".*")))
    (is (=' #"(?:foo){42,}"                                 (nom-grp 42 #"foo")))
    (is (=' #"(?:Apache){12,}"                              (nom-grp 12 "Apache")))
    (is (=' #"(?:Apache(\s+Software)?(\s+Licen[cs]e)?){5,}" (nom-grp 5 "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?")))
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
    (is (nil?                                          (exn nil nil)))
    (is (nil?                                          (exn nil #"")))
    (is (nil?                                          (exn 2 nil)))
    (is (=' #"x{5}"                                    (exn 5 #"x")))
;    (is (thrown? java.util.regex.PatternSyntaxException (exn 3 #".*")))  ; Note: valid (but nonsensical) on ClojureJVM, but _not_ valid on ClojureScript, so we don't test this
    (is (=' #"foo{2}"                                  (exn 2 #"foo")))  ; Note how this doesn't result in optionality being applied to the entirety of the input - that's what nom-grp etc. are for
    (is (=' #"Apache{17}"                              (exn 17 "Apache"))))
  (testing "exn-grp"
    (is (nil?                                              (exn-grp nil nil)))
    (is (nil?                                              (exn-grp 3 nil)))
    (is (=' #"(?:){246}"                                   (exn-grp 246 #""))))
    (is (=' #"(?:x){0}"                                    (exn-grp 0 #"x")))
    (is (=' #"(?:.*){7}"                                   (exn-grp 7 #".*")))
    (is (=' #"(?:foo){42}"                                 (exn-grp 42 #"foo")))
    (is (=' #"(?:Apache){12}"                              (exn-grp 12 "Apache")))
    (is (=' #"(?:Apache(\s+Software)?(\s+Licen[cs]e)?){5}" (exn-grp 5 "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?")))
  (testing "exn-cg"
    (is (nil?                                            (exn-cg nil nil)))
    (is (=' #"(){3}"                                     (exn-cg 3 #"")))
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
    (is (nil?                                           (n2m nil nil nil)))
    (is (nil?                                           (n2m nil nil #"")))
    (is (nil?                                           (n2m 2 4 nil)))
    (is (=' #"x{2,4}"                                   (n2m 2 4 #"x")))
;    (is (thrown? java.util.regex.PatternSyntaxException (n2m 3 7 #".*")))  ; Note: valid (but nonsensical) on ClojureJVM, but _not_ valid on ClojureScript, so we don't test this
    (is (=' #"foo{2,2}"                                 (n2m 2 2 #"foo")))  ; Note how this doesn't result in optionality being applied to the entirety of the input - that's what nom-grp etc. are for
    (is (=' #"Apache{17,21}"                            (n2m 17 21 "Apache"))))
  (testing "n2m-grp"
    (is (nil?                                                 (n2m-grp nil nil nil)))
    (is (nil?                                                 (n2m-grp 3 100 nil)))
    (is (=' #"(?:){246,250}"                                  (n2m-grp 246 250 #""))))
    (is (=' #"(?:x){0,3}"                                     (n2m-grp 0 3 #"x")))
    (is (=' #"(?:.*){7,8}"                                    (n2m-grp 7 8 #".*")))
    (is (=' #"(?:foo){42,69}"                                 (n2m-grp 42 69 #"foo")))
    (is (=' #"(?:Apache){12,13}"                              (n2m-grp 12 13 "Apache")))
    (is (=' #"(?:Apache(\s+Software)?(\s+Licen[cs]e)?){5,99}" (n2m-grp 5 99 "Apache" #"(\s+Software)?" #"(\s+Licen[cs]e)?")))
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
    (is (=' #"|"                   (alt #"" #"")))
    (is (=' #"foo|bar"             (alt #"foo" #"bar")))
    (is (=' #"0|1|2|3|4|5|6|7|8|9" (apply alt (range 10)))))
  (testing "alt-grp"
    (is (nil?                                                              (alt-grp nil)))
    (is (nil?                                                              (alt-grp nil nil)))
    (is (=' #"(?:)"                                                        (alt-grp #"")))
    (is (=' #"(?:)|(?:)"                                                   (alt-grp #"" #"")))
    (is (=' #"(?:foo)|(?:bar)"                                             (alt-grp #"foo" #"bar")))
    (is (=' #"(?:0)|(?:1)|(?:2)|(?:3)|(?:4)|(?:5)|(?:6)|(?:7)|(?:8)|(?:9)" (apply alt-grp (range 10))))))

(deftest and'-variant-tests
  (testing "and'"
    (is (nil?              (and' nil nil)))
    (is (nil?              (and' nil nil nil)))
    (is (nil?              (and' #"a" nil)))
    (is (=' #"ab|ba"       (and' #"a" #"b")))
    (is (=' #"ab|ba"       (and' #"a" #"b" nil)))
    (is (=' #"a\s+b|b\s+a" (and' #"a" #"b" #"\s+"))))
  (testing "and-grp"
    (is (nil?                                      (and-grp nil nil)))
    (is (nil?                                      (and-grp nil nil nil)))
    (is (nil?                                      (and-grp #"a" nil)))
    (is (=' #"(?:a)(?:b)|(?:b)(?:a)"               (and-grp #"a" #"b")))
    (is (=' #"(?:a)(?:b)|(?:b)(?:a)"               (and-grp #"a" #"b" nil)))
    (is (=' #"(?:a)(?:\s+)(?:b)|(?:b)(?:\s+)(?:a)" (and-grp #"a" #"b" #"\s+")))))

(deftest or'-variant-tests
  (testing "or'"
    (is (nil?                  (or' nil nil)))
    (is (nil?                  (or' nil nil nil)))
    (is (nil?                  (or' #"a" nil)))
    (is (=' #"ab|ba|a|b"       (or' #"a" #"b")))
    (is (=' #"ab|ba|a|b"       (or' #"a" #"b" nil)))
    (is (=' #"a\s+b|b\s+a|a|b" (or' #"a" #"b" #"\s+"))))
  (testing "or-grp"
    (is (nil?                                                  (or-grp nil nil)))
    (is (nil?                                                  (or-grp nil nil nil)))
    (is (nil?                                                  (or-grp #"a" nil)))
    (is (=' #"(?:a)(?:b)|(?:b)(?:a)|(?:a)|(?:b)"               (or-grp #"a" #"b")))
    (is (=' #"(?:a)(?:b)|(?:b)(?:a)|(?:a)|(?:b)"               (or-grp #"a" #"b" nil)))
    (is (=' #"(?:a)(?:\s+)(?:b)|(?:b)(?:\s+)(?:a)|(?:a)|(?:b)" (or-grp #"a" #"b" #"\s+")))))

(defn- matches?
  [re s]
  (boolean (re-matches re s)))

(defn- finds?
  [re s]
  (boolean (re-find re s)))

(deftest composite-tests
  ; The following regex ends up being ~450 characters long, partly because of the sheer number of times the words
  ; "Lesser" and "Library" appear in it (in order to implement the nested "or"s)
  (let [lorl (grp (or' #"Lesser" #"Library" #"\s+or\s+"))
        re   (join #"(?iuU)(?<!\w)"
                   (ncg "lgpl"
                     (or-grp
                       (join #"GNU\s+" lorl)
                       (join lorl #"\s+GPL")))
                   #"(?!\w)")]
    (testing "Matching tests"
      ; Matches
      (is (true?  (matches? re "GNU Lesser")))
      (is (true?  (matches? re "GNU Library")))
      (is (true?  (matches? re "gnu lesser or library")))
      (is (true?  (matches? re "GNU LIBRARY OR LESSER")))
      (is (true?  (matches? re "Lesser GPL")))
      (is (true?  (matches? re "Library GPL")))
      (is (true?  (matches? re "Lesser or Library GPL")))
      (is (true?  (matches? re "lIBRARY oR lESSER gpl")))
      ; Non matches
      (is (false? (matches? re "GNU")))
      (is (false? (matches? re "GPL")))
      (is (false? (matches? re "Lesser")))
      (is (false? (matches? re "Library")))
      (is (false? (matches? re "or")))
      (is (false? (matches? re "Lesser or Library")))
      (is (false? (matches? re "Library or Lesser")))
      (is (false? (matches? re "GPL Library or Lesser")))
      (is (false? (matches? re "Library or Lesser GNU"))))
    (testing "Finding tests"
      ; Finds
      (is (true?  (finds? re "some text GNU Lesser or more text")))
      (is (true?  (finds? re "some text GNU Library or more text")))
      (is (true?  (finds? re "some text gnu lesser or library or more text")))
      (is (true?  (finds? re "some text GNU LIBRARY OR LESSER or more text")))
      (is (true?  (finds? re "some text Lesser GPL or more text")))
      (is (true?  (finds? re "some text Library GPL or more text")))
      (is (true?  (finds? re "some text Lesser or Library GPL or more text")))
      (is (true?  (finds? re "some text lIBRARY oR lESSER gpl or more text")))
      ; Finds, but tricky - the re finds a subset of the entire phrase
      (is (true?  (finds? re "some text GNU LIBRARY OR LESSERor more text")))  ; finds "GNU LIBRARY"
      (is (true?  (finds? re "some textLesser or Library GPL or more text")))  ; finds "Library GPL"
      ; Non finds due to concatenated leading or trailing text
      (is (false? (finds? re "some textGNU Lesser or more text")))
      (is (false? (finds? re "some text GNU Libraryor more text")))
      (is (false? (finds? re "some textgnu lesser or library or more text")))
      (is (false? (finds? re "some textLesser GPL or more text")))
      (is (false? (finds? re "some text Library GPLor more text")))
      (is (false? (finds? re "some text lIBRARY oR lESSER gplor more text")))
      ; Non finds
      (is (false? (finds? re "some text GNU or more text")))
      (is (false? (finds? re "some text GPL or more text")))
      (is (false? (finds? re "some text Lesser or more text")))
      (is (false? (finds? re "some text Library or more text")))
      (is (false? (finds? re "some text or or more text")))
      (is (false? (finds? re "some text Lesser or Library or more text")))
      (is (false? (finds? re "some text Library or Lesser or more text")))
      (is (false? (finds? re "some text GPL Library or Lesser or more text")))
      (is (false? (finds? re "some text Library or Lesser GNU or more text"))))))
