;
; Copyright © 2025 Peter Monks
;
; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.
;
; SPDX-License-Identifier: MPL-2.0
;

#_{:clj-kondo/ignore [:unresolved-namespace]}
(defn set-opts
  [opts]
  (assoc opts
         :lib          'com.github.pmonks/wreck
         :version      (pbr/calculate-version 0 1)
         :prod-branch  "release"
         :write-pom    true
         :validate-pom true
         :pom          {:description      "A micro-library for Clojure(Script) that provides regular expression construction functions."
                        :url              "https://github.com/pmonks/wreck"
                        :licenses         [:license   {:name "MPL-2.0" :url "https://www.mozilla.org/en-US/MPL/2.0/"}]
                        :developers       [:developer {:id "pmonks" :name "Peter Monks" :email "pmonks+wreck@gmail.com"}]
                        :scm              {:url                  "https://github.com/pmonks/wreck"
                                           :connection           "scm:git:git://github.com/pmonks/wreck.git"
                                           :developer-connection "scm:git:ssh://git@github.com/pmonks/wreck.git"
                                           :tag                  (tc/git-tag-or-hash)}
                        :issue-management {:system "github" :url "https://github.com/pmonks/wreck/issues"}}
         :codox        {:metadata         {:doc/format :markdown}}))
