(ns avi.spec-runner
  (:require [midje.sweet :refer :all])
  (:require [avi.test-helpers :refer :all]))

(def ^:private column-names
  '{content ?content
    after   ?after
    point   ?point})

(defmacro facts-about
  [description qualities & tests]
  `(tabular
     (facts ~description
       (editor :editing ~'?content :after ~'?after) => (point ~'?point))
     ~@(map column-names qualities)
     ~@tests))
