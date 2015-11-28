(ns avi.mode.normal
  (:require [packthread.core :refer :all]
            [avi.beep :as beep]
            [avi.brackets :as brackets]
            [avi.buffer :as b]
            [avi.editor :as e]
            [avi.eventmap :as em]
            [avi.mode command-line insert]
            [avi.pervasive :refer :all]
            [avi.search]))

(defn- scroll
  [editor update-fn]
  (+> editor
    (in e/current-buffer
        (b/scroll update-fn))))

(def motions
  '[["0"    :exclusive [:goto [:current 0]]]
    ["^"    :exclusive [:goto [:current :first-non-blank]]]
    ["$"    :inclusive [:goto [:current :end-of-line]]]
    ["f<.>" :inclusive [:goto [:current [:to-next ?char]]]]
    ["gg"   :linewise  [:goto [(?line 0) :first-non-blank]]]
    ["h"    :exclusive [:goto [:current :left]]]
    ["j"    :linewise  [:goto [:down :last-explicit]]]
    ["k"    :linewise  [:goto [:up :last-explicit]]]
    ["l"    :exclusive [:goto [:current :right]]]
    ["t<.>" :inclusive [:goto [:current [:before-next ?char]]]]
    ["F<.>" :exclusive [:goto [:current [:to-previous ?char]]]]
    ["G"    :linewise  [:goto [(?line :last) :first-non-blank]]]
    ["H"    :linewise  [:goto [[:viewport-top (?line 0)] :first-non-blank]]]
    ["L"    :linewise  [:goto [[:viewport-bottom (?line 0)] :first-non-blank]]]
    ["M"    :linewise  [:goto [:viewport-middle :first-non-blank]]]])

(defn variable?
  [a]
  (and (symbol? a)
       (= (get (name a) 0) \?)))

(defn bindings
  [editor [_ keyname]]
  {'?char (get keyname 0)
   '?line (some-> (:count editor) dec)})

(def count-variables
  #{'?line})

(defn uses-count?
  [pattern]
  (->> pattern
    flatten
    (filter count-variables)
    seq))

(defn substitute
  [a bindings]
  (cond
    (variable? a)
    (bindings a)

    (and (list? a) (variable? (first a)))
    (if-let [value (bindings (first a))]
      value
      (second a))

    (coll? a)
    (into (empty a) (map #(substitute % bindings) a))

    :else
    a))

(defn motion-handler
  [f kind pattern]
  (with-meta
    (fn+> [editor event]
      (let [motion (substitute pattern (bindings editor event))]
        (in e/current-buffer
            (f motion kind))))
    (if (uses-count? pattern)
      {:no-repeat true})))

(defn motion-handlers
  [prefix f]
  (->> motions
    (map (fn [[ks kind pattern]]
           [(str prefix ks) (motion-handler f kind pattern)]))
    (into {})))

(def wrap-normal-mode
  (em/eventmap
    (merge
      (motion-handlers "" b/move-point)
      (motion-handlers "d" b/delete)
      {"dd" ^:no-repeat (fn+> [editor _]
                          (let [repeat-count (:count editor)]
                            (in e/current-buffer
                                b/start-transaction
                                (n-times (or repeat-count 1) b/delete-current-line)
                                b/commit)))

       "u" (fn+> [editor _]
             (in e/current-buffer
               b/undo))

       "x" ^:no-repeat (fn+> [editor _]
                         (let [repeat-count (:count editor)]
                           (in e/current-buffer
                               b/start-transaction
                               (as-> buffer
                                 (reduce
                                   (fn [buffer n]
                                     (b/delete-char-under-point buffer))
                                   buffer
                                   (range (or repeat-count 1))))
                               b/commit)))
       "D" (fn+> [editor _]
             (in e/current-buffer
               (b/delete [:goto [:current :end-of-line]] :inclusive)))

       "J" ^:no-repeat (fn+> [editor _]
                         (let [{[i j] :point, lines :lines} (e/current-buffer editor)
                               n (or (:count editor) 1)
                               new-line (reduce
                                          #(str %1 " " %2)
                                          (subvec lines i (+ i n 1)))
                               new-lines (splice lines i (+ i n 1) [new-line])]
                           (in e/current-buffer
                               b/start-transaction
                               (assoc :lines new-lines)
                               b/commit)))

       "<C-D>" (fn+> [editor _]
                 (let [buffer (e/current-buffer editor)]
                   (if (b/on-last-line? buffer)
                     beep/beep
                     (in e/current-buffer
                       (b/move-and-scroll-half-page :down)))))

       "<C-E>" (fn+> [editor _]
                 (scroll inc))

       "<C-R>" (fn+> [editor _]
                 (in e/current-buffer
                   b/redo))

       "<C-U>" (fn+> [editor _]
                 (let [buffer (e/current-buffer editor)
                       [i] (:point buffer)]
                   (if (zero? i)
                     beep/beep
                     (in e/current-buffer
                       (b/move-and-scroll-half-page :up)))))

       "<C-Y>" (fn+> [editor _]
                 (scroll dec))})))

(defn- update-count
  [editor digit]
  (let [old-count (or (:count editor) 0)
        new-count (+ (* 10 old-count) digit)]
    (assoc editor :count new-count)))

(defn- wrap-collect-repeat-count
  [responder]
  (fn+> [editor [event-type event-data :as event]]
    (if (seq (:pending-events editor))
      (responder event)
      (cond
        (= event [:keystroke "0"])
        (if (:count editor)
          (update-count 0)
          (responder event))

        (and (= 1 (count event-data))
             (Character/isDigit (get event-data 0)))
        (update-count (Integer/parseInt event-data))

        :else
        (responder event)))))

(def responder
  (-> beep/beep-responder
      wrap-normal-mode
      avi.search/wrap-normal-search-commands
      avi.mode.command-line/wrap-enter-command-line-mode
      avi.mode.insert/wrap-enter-insert-mode
      brackets/wrap-go-to-matching-bracket
      wrap-collect-repeat-count))

(def wrap-mode (e/mode-middleware :normal responder))
