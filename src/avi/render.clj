(ns avi.render
  (:import [java.util Arrays])
  (:require [clojure.set :refer [map-invert]]
            [avi.editor :as e]
            [avi.buffer :as b]))

(def ^:private color->number
  {:black 0
   :red 1
   :green 2
   :yellow 3
   :blue 4
   :magenta 5
   :cyan 6
   :white 7})

(def ^:private number->color
  (map-invert color->number))

(defn make-attributes
  [color background]
  (byte (bit-or (bit-shift-left (color->number color) 3)
                (color->number background))))

(defn- foreground-color
  [character-attributes]
  (number->color (bit-and 7 (bit-shift-right character-attributes 3))))

(defn- background-color
  [character-attributes]
  (number->color (bit-and 7 character-attributes)))

(defn attr-description
  [character-attributes]
  (let [fg-keyword (foreground-color character-attributes)
        bg-keyword (background-color character-attributes)]
    (cond
      (= [:white :black] [fg-keyword bg-keyword])
      []

      (= :black bg-keyword)
      [fg-keyword]

      :else
      [fg-keyword :on bg-keyword])))

(defn- render-message-line
  [editor]
  (cond
    (and (:prompt editor) (:command-line editor))
    [(make-attributes :white :black) (str (:prompt editor) (:command-line editor))]

    (:message editor)
    (let [[foreground background text] (:message editor)]
      [(make-attributes foreground background) text])

    :else
    [(make-attributes :white :black) ""]))

(defn- render-line
  [editor i]
  (let [[height] (:size (:viewport editor))
        buffer (e/current-buffer editor)
        top (:viewport-top buffer)
        message-line (dec height)
        status-line (dec message-line)
        last-edit-line (dec status-line)
        buffer-line (+ i top)
        buffer-line-count (b/line-count buffer)]
    (cond
      (= message-line i)
      (render-message-line editor)

      (= status-line i)
      [(make-attributes :black :white) (or (:name buffer) "[No Name]")]

      (< buffer-line buffer-line-count)
      (let [white-on-black (make-attributes :white :black)
            red-on-black (make-attributes :red :black)
            line (b/line buffer buffer-line)
            attrs (byte-array (count line) white-on-black)]
        (doseq [j (range (count line))]
          (when (#{\( \)} (get line j))
            (aset-byte attrs j red-on-black)))
        [attrs line])

      :else
      [(make-attributes :blue :black) "~"])))

(defmulti ^:private cursor-position :mode)

(defmethod cursor-position :default
  [editor]
  (let [buffer (e/current-buffer editor)
        [buffer-cursor-i buffer-cursor-j] (:cursor buffer)
        viewport-top (:viewport-top buffer)]
    [(- buffer-cursor-i viewport-top) buffer-cursor-j]))

(defmethod cursor-position :command-line
  [editor]
  (let [[height] (:size (:viewport editor))]
    [(dec height) (inc (count (:command-line editor)))]))

(let [byte-array-class (Class/forName "[B")]
  (defn byte-array?
    [obj]
    (= byte-array-class (class obj))))

(defn render
  [editor]
  (let [[height width] (:size (:viewport editor))
        default-attrs (make-attributes :white :black)
        rendered-chars (char-array (* height width) \space)
        rendered-attrs (byte-array (* height width) default-attrs)]
    (doseq [i (range height)]
      (let [[attrs text] (render-line editor i)]
        (.getChars text 0 (min width (count text)) rendered-chars (* i width))
        (if (byte-array? attrs)
          (System/arraycopy attrs 0 rendered-attrs (* i width) (min width (count attrs)))
          (Arrays/fill rendered-attrs (* i width) (* (inc i) width) attrs))))
    {:width width
     :chars rendered-chars
     :attrs rendered-attrs
     :cursor (cursor-position editor)}))

(defn rendered
  [editor]
  (assoc editor :rendition (render editor)))

(defn wrap
  [responder]
  (fn [editor event]
    (-> editor
      (responder event)
      rendered)))
