(ns avi.commands
 "Avi's command-line-mode commands.

  Functions implemented in this namespace can be called by name from the colon
  prompt."
 (:require [avi.documents]
           [avi.edit-context :as ec]
           [avi.editor :as e]
           [avi.layout.panes :as p]
           [avi.world :as w]
           [clojure.string :as string]
           [packthread.core :refer :all]))

(defn -NUMBER-
  "Special function which handles commands like `:42`"
  [editor n]
  (+> editor
    (in e/edit-context
        (ec/operate {:operator :move-point
                     :motion [:goto [(dec n) :first-non-blank]]}))))

(defn q
  [editor]
  (p/close-pane editor))

(defn w
  [editor]
  (let [{filename :avi.documents/name,
         :keys [:avi.documents/text]} (get-in editor (e/current-document-path editor))]
    (w/write-file w/*world* filename text)
    editor))

(def wq (comp q w))

(defn- split*
  [direction]
  (fn [{:keys [:avi.lenses/lenses] :as editor}]
    (-> editor
        (update :avi.lenses/lenses assoc (inc (reduce max -1 (keys lenses))) (e/current-lens editor))
        (p/split-pane (count lenses) direction))))
(def sp (split* :horizontal))
(def vsp (split* :vertical))

(defn e
  {:type-hints [:avi.mode.command-line/string]}
  [{:keys [:avi.documents/documents] :as editor} filename]
  (+> editor
    (let [document-n (inc (reduce max -1 (keys documents)))]
     (update :avi.documents/documents assoc document-n (avi.documents/load filename))
     (assoc-in (conj (e/current-lens-path editor) :avi.lenses/document) document-n))))
