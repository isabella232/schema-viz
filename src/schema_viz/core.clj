(ns schema-viz.core
  (:require [clojure.string :as str]
            [schema.core :as s]
            [rhizome.viz :as viz]))

;;
;; Extract definitions
;;

(defrecord SchemaDefinition [name fields relations])

(defn extract-schema-var [x]
  (and (var? x) (s/schema-name @x) @x))

(defn schema-and-relations [x]
  (when-let [name (s/schema-name x)]
    (let [fields (for [[k v] x :let [v (if (or (sequential? v) (set? v)) (first v) v)]]
     [k v (s/schema-name v)])]
      (->SchemaDefinition
        name
        (->> fields (map butlast))
        (->> fields (keep last) set)))))

(defn extract-relations [{:keys [name relations]}]
  (map (fn [r] [name r]) relations))

(defn explain-key [key]
  (if (s/specific-key? key)
    (str
      (s/explicit-schema-key key)
      (if (s/optional-key? key) "?"))
    (s/explain key)))

(defn explain-value [value]
  (or (s/schema-name value) (s/explain value)))

;;
;; DOT
;;

(defn- dot-class [{:keys [name fields]}]
  (let [fields (for [[k v] fields] (str "+ " (explain-key k) " " (explain-value v)))]
    (str name " [label = \"{" name "|" (str/join "\\l" fields) "\\l}\"]")))

(defn- dot-relation [[from to]]
  (str from " -> " to " [dirType = \"forward\"]"))

(defn- dot-node [node data]
  (str node "[" (str/join ", " (map (fn [[k v]] (str (name k) "=" (pr-str v))) data)) "]"))

(defn- dot-package [definitions]
  (let [relations (mapcat extract-relations definitions)]
    (str/join
      "\n"
      (concat
        ["digraph {"
         "fontname = \"Bitstream Vera Sans\""
         "fontsize = 12"
         (dot-node "node" {:fontname "Bitstream Vera Sans"
                           :fontsize 12
                           :shape "record"
                           :style "filled"
                           :fillcolor "#ccffcc"
                           :color "#558855"})
         (dot-node "edge" {:arrowhead "diamond"})]
        (map dot-class definitions)
        (map dot-relation relations)
        ["}"]))))

(defn dot-ns [ns]
  (->> ns
       ns-publics
       vals
       (keep extract-schema-var)
       (map schema-and-relations)
       dot-package))

;;
;; View
;;

(defn view-ns
  ([] (view-ns *ns*))
  ([ns]
   (->> ns dot-ns viz/dot->image viz/view-image)))
