(ns schema-viz.core
  (:require [clojure.string :as str]
            [schema.core :as s]
            [schema-tools.walk :as stw]
            [rhizome.viz :as viz]))

;;
;; Definitions
;;

(defrecord SchemaDefinition [name fields relations])

(defrecord SchemaReference [schema]
  s/Schema
  (spec [_]
    (s/spec schema))
  (explain [_]
    (s/schema-name schema))
  stw/WalkableSchema
  (-walk [this inner outer]
    (outer (with-meta (->SchemaReference (inner (:schema this))) (meta this)))))

;;
;; Walkers
;;

(defn- full-name [path]
  (->> path (map name) (map str/capitalize) (apply str) symbol))

(defn- plain-map? [x]
  (and (map? x) (and (not (record? x)))))

(defn- with-named-subschemas [schemas]
  (let [path (atom [])]
    (stw/prewalk
      (fn [x]
        (cond
          (s/schema-name x) (and (reset! path [(s/schema-name x)]) x)
          (plain-map? x) (with-meta x {:name (full-name @path)})
          (map-entry? x) (and (swap! path (fn [[k]] [k (first x)])) x)
          :else x))
      schemas)))

(defn- with-sub-schemas-references [schemas]
  (->> schemas
       (stw/postwalk
         (fn [x]
           (if (s/schema-name x)
             (->SchemaReference x)
             x)))
       (mapv :schema)))

(defn- collect-schemas [schemas]
  (let [name->schema (atom {})]
    (stw/prewalk
      (fn [schema]
        (when-let [name (s/schema-name schema)]
          (swap!
            name->schema update-in [name]
            (fn [x] (conj (or x #{}) schema))))
        schema)
      schemas)
    ;; TODO: handle duplicate names here
    (->> @name->schema vals (map first))))

;; TODO: dummy implementation, just looks for a first schema
(defn- peek-schema [schema]
  (let [peeked (atom nil)]
    (->> schema
         (stw/prewalk
           (fn [x]
             (if (and (plain-map? x) (s/schema-name x))
               (do (if-not @peeked (reset! peeked x)) x)
               x))))
    @peeked))

;;
;; Models
;;

(defn- extract-schema-var [x]
  (and (var? x) (s/schema-name @x) @x))

(defn- schema-definition [x]
  (when-let [name (s/schema-name x)]
    (let [fields (for [[k v] x :let [peeked (peek-schema v)]]
                   [k v (s/schema-name peeked)])]
      (->SchemaDefinition
        name
        (->> fields (map butlast))
        (->> fields (keep last) set)))))

(defn- extract-relations [{:keys [name relations]}]
  (map (fn [r] [name r]) relations))

(defn- safe-explain [x]
  (try
    (s/explain x)
    (catch Exception _ x)))

(defn- explain-key [key]
  (if (s/specific-key? key)
    (str
      (s/explicit-schema-key key)
      (if (s/optional-key? key) "?"))
    (safe-explain key)))

(defn- explain-value [value]
  (str (or (s/schema-name value) (safe-explain value))))

(defn- schema-definitions [ns]
  (->> ns
       ns-publics
       vals
       (keep extract-schema-var)
       with-named-subschemas
       with-sub-schemas-references
       collect-schemas
       (mapv schema-definition)))

;;
;; DOT
;;

(defn wrap-quotes [x] (str "\"" x "\""))

(defn wrap-escapes [x] (str/escape x {\> ">", \< "<", \" "\\\""}))

(defn- dot-class [{:keys [name fields]}]
  (let [fields (for [[k v] fields] (str "+ " (explain-key k) " " (-> v explain-value wrap-escapes)))]
    (str (wrap-quotes name) " [label = \"{" name "|" (str/join "\\l" fields) "\\l}\"]")))

(defn- dot-relation [[from to]]
  (str (wrap-quotes from) " -> " (wrap-quotes to) " [dirType = \"forward\"]"))

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

;;
;; Visualization
;;

(defn visualize-schemas
  ([]
   (visualize-schemas *ns*))
  ([ns]
   (->> ns
        schema-definitions
        dot-package
        viz/dot->image
        viz/view-image)))
