;
; Copyright © 2025 Peter Monks
;
; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.
;
; SPDX-License-Identifier: MPL-2.0
;

(ns wreck.test-utils
#?(:clj
   (:require [net.cgrand.macrovich :as macros])
 :cljs
   (:require-macros [net.cgrand.macrovich :as macros]
                    [wreck.test-utils :refer [time-execution]])))  ; Self reference required by macrovich

(defmacro time-execution
  "Times the execution of `body`, in ms. Result is a map with these keys:

  * `:result` the result of `body`
  * `:time` the time (in ms) `body` took to execute"
  [& body]
  (macros/case
:clj
    `(let [start#  (System/nanoTime)
           result# ~@body]
       {:result result#
        :time   (/ (double (- (System/nanoTime) start#)) 1000000.0)})
:cljs
    `(let [start#  (cljs.core/system-time)
           result# ~@body]
       {:result result#
        :time   (.toFixed (- (cljs.core/system-time) start#) 6)})))
