(ns avi.buffer.t-lines
  (:require [avi.buffer.lines :as lines]
            [clojure.string :as string]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [com.gfredericks.test.chuck.generators :as gen']
            [com.gfredericks.test.chuck.properties :as prop']
            [midje.sweet :refer :all]
            [midje.checking.core :as checking]
            [schema.core :as s]))

(s/set-fn-validation! true)

(facts "about buffer contents"
  (fact "we can retrieve buffer contents' initial text"
    (lines/content "Hello, World!") => ["Hello, World!"]
    (lines/content "Line 1\nLine 2") => ["Line 1" "Line 2"]
    (lines/content "Line 1\nLine 3\n") => ["Line 1" "Line 3"]
    (lines/content "Line 1\n\nLine 3") => ["Line 1" "" "Line 3"])
  (fact "we always have at least one line"
    (lines/content "") => [""])
  (fact "if the last character is a newline, it does not make an extra line"
    (lines/content "\n") => [""]
    (lines/content "\n\n") => ["" ""]
    (lines/content "\nfoo") => ["" "foo"]
    (lines/content "foo\n") => ["foo"]))

(facts "about replacing contents"
  (fact "replace can insert at beginning of buffer"
    (lines/replace (lines/content "Hello!") [0 0] [0 0] "xyz") => ["xyzHello!"])
  (fact "replace can insert within a line"
    (lines/replace (lines/content "Hello!") [0 2] [0 2] "//") => ["He//llo!"])
  (fact "replace can insert at the end of a line"
    (lines/replace (lines/content "Hello!") [0 6] [0 6] "//") => ["Hello!//"])
  (fact "replace works with start and end reversed"
    (lines/replace (lines/content "Hello!") [0 5] [0 2] "//") => ["He//!"]))

(def text-generator
  (gen/fmap (partial string/join "\n") (gen/vector gen/string-ascii)))

(def content-generator
  (gen/fmap lines/content text-generator))

(defn location-generator
  [lines]
  (gen'/for [line (gen/choose 0 (dec (count lines)))
             column (gen/choose 0 (count (get lines line)))]
    [line column]))

(defn start-end-location-generator
  [lines]
  (gen/fmap sort (gen/vector (location-generator lines) 2)))

(defspec join-before-and-after-invariant 25
  (prop'/for-all [content content-generator
                  location (location-generator content)]
    (= (lines/join (lines/before content location) (lines/after content location))
       content)))

(defspec replace-before-after-replacement-invariant 25
  (prop'/for-all [content content-generator
                  [start end] (start-end-location-generator content)
                  replacement text-generator]
    (= (str (string/join "\n" (lines/before content start))
            replacement
            (string/join "\n" (lines/after content end)))
       (string/join "\n" (lines/replace content start end replacement)))))