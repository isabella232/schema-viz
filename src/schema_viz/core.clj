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
    (let [fields (for [[k v] x] [k v (s/schema-name v)])]
      (->SchemaDefinition
        name
        (->> fields (remove last) (map butlast))
        (->> fields (keep last))))))

(defn extract-relations [{:keys [name relations]}]
  (map (fn [r] [name r]) relations))

;;
;; DOT
;;

(defn- dot-node [{:keys [name fields]}]
  (let [fields (for [[k v] fields] (str "+ " k (if (s/optional-key? k) "?") " : " (s/explain v)))]
    (str name " [label = \"{" name "|" (str/join "\\l" fields) "\\l}\"]")))

(defn- dot-relation [[from to]]
  (str from " -> " to " [dirType = \"forward\"]"))

(defn- dot-package [definitions]
  (let [relations (mapcat extract-relations definitions)]
    (str/join
      "\n"
      (concat
        ["digraph {"
         "fontname = \"Bitstream Vera Sans\""
         "fontsize = 12"
         "node [fontname = \"Bitstream Vera Sans\" fontsize = 12 shape = \"record\"]"
         "edge [arrowhead = \"diamond\"]"]
        (map dot-node definitions)
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

(defn view-ns [ns]
  (->> ns dot-ns viz/dot->image viz/view-image))

(s/defschema Country {:name s/Str
                      :code (s/enum :FI :PO)})

(s/defschema Address {:street s/Str
                      :country Country})

(s/defschema User {:id s/Int
                   :name s/Str
                   (s/optional-key :address) Address})

(view-ns *ns*)
