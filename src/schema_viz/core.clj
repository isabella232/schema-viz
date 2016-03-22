(ns schema-viz.core
  (:require [clojure.string :as str]
            [schema.core :as s]
            [schema-tools.walk :as stw]
            [schema-tools.core :as st]
            [rhizome.viz :as viz]))

;;
;; Definitions
;;

(defrecord SchemaDefinition [schema fields relations])

(defrecord SchemaReference [schema]
  s/Schema
  (spec [_]
    (s/spec schema))
  (explain [_]
    (s/schema-name schema))
  clojure.lang.IDeref
  (deref [this]
    this)
  stw/WalkableSchema
  (-walk [this inner outer]
    (outer (with-meta (->SchemaReference (inner (:schema this))) (meta this)))))

;;
;; Walkers
;;

(defn- deref? [x]
  (instance? clojure.lang.IDeref x))

(defn- get-name [x]
  (name (or (s/schema-name x) x)))

(defn- full-name [path]
  (->> path (map get-name) (map str/capitalize) (apply str) symbol))

(defn- plain-map? [x]
  (and (map? x) (and (not (record? x)))))

; supporting Clojure 1.7
(defn- -map-entry? [x]
  (instance? java.util.Map$Entry x))

(defn- named-subschemas [schema]
  (letfn [(-named-subschemas [path schema]
            (stw/walk
              (fn [x]
                (cond
                  (-map-entry? x) (let [[k v] x
                                        name (s/schema-name (st/schema-value v))]
                                    [k (-named-subschemas
                                         (if name [name]
                                                  (into path
                                                        [:$
                                                         (if (s/specific-key? k)
                                                           (s/explicit-schema-key k)
                                                           (gensym (pr-str k)))])) v)])
                  (s/schema-name x) (-named-subschemas [x] x)
                  :else (-named-subschemas path x)))
              (fn [x]
                (if (and (plain-map? x) (not (s/schema-name x)))
                  (with-meta x {:name (full-name path)
                                :ns (s/schema-ns (first path))
                                ::sub-schema? true})
                  x))
              schema))]
    (-named-subschemas [schema] schema)))

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

;; TODO: currently just looks for a first schema, support multiple schemas: s/cond-pre & friends
(defn- peek-schema [schema f]
  (let [peeked (atom nil)]
    (->> schema
         (stw/prewalk
           (fn [x]
             (let [naked (if (deref? x) @x x)]
               (if (and (plain-map? naked) (f naked))
                 (do (if-not @peeked (reset! peeked naked)) x)
                 x)))))
    @peeked))

;;
;; Models
;;

(defn- extract-schema-var [x]
  (and (var? x) (s/schema-name @x) @x))

(defn- schema-definition [schema]
  (when (s/schema-name schema)
    (let [fields (for [[k v] (peek-schema schema identity)
                       :let [peeked (peek-schema v s/schema-name)]]
                   [k v peeked])]
      (->SchemaDefinition
        schema
        (->> fields (map butlast))
        (->> fields (keep last) set)))))

(defn- extract-relations [{:keys [schema relations]}]
  (map (fn [r] [schema r]) relations))

(defn- explainable [explanation]
  (reify s/Schema (explain [_] explanation)))

(defn- safe-explain [schema]
  (try
    (s/explain
      ;; replace Schemas with ones producing cleaner explanation
      (stw/postwalk
        (fn [x]
          (if (instance? schema.core.Recursive x)
            (explainable (list 'recursive (s/explain (st/schema-value x))))
            x))
        schema))
    (catch Exception _ schema)))

(defn- explain-key [key]
  (if (s/specific-key? key)
    (str
      (s/explicit-schema-key key)
      (if (s/optional-key? key) "(?)"))
    (safe-explain key)))

(defn- explain-value [value]
  (str (or (s/schema-name value) (safe-explain value))))

(defn- schema-definitions [ns]
  (->> ns
       ns-publics
       vals
       (keep extract-schema-var)
       (map named-subschemas)
       with-sub-schemas-references
       collect-schemas
       (mapv schema-definition)))

;;
;; DOT
;;

(defn- wrap-quotes [x] (str "\"" x "\""))

(defn- wrap-escapes [x] (str/escape x {\> ">", \< "<", \" "\\\""}))

(defn- dot-relation [[from to]]
  (str (wrap-quotes (s/schema-name from)) " -> " (wrap-quotes (s/schema-name to))))

(defn- dot-node [node data]
  (str node " [" (str/join ", " (map (fn [[k v]] (str (name k) "=" (pr-str v))) data)) "]"))

(defn- dot-class [{:keys [fields?]} {:keys [schema fields]}]
  (let [{name :name sub-schema? ::sub-schema?} (meta schema)
        fields (for [[k v] fields] (str "+ " (explain-key k) " " (-> v explain-value wrap-escapes)))]
    (str (wrap-quotes name) " [label = \"{" name
         (if fields? (str "|" (str/join "\\l" fields))) "\\l}\""
         (if sub-schema? ", fillcolor=\"#e6caab\"") "]")))

(defn- dot-graph [data]
  (str "digraph {\n" (str/join "\n" (apply concat data)) "\n}"))

(defn- dot-package [options definitions]
  (let [relations (mapcat extract-relations definitions)]
    (dot-graph
      [[(dot-node "node" {:fontname "Bitstream Vera Sans"
                          :fontsize 12
                          :shape "record"
                          :style "filled"
                          :fillcolor "#fff0cd"
                          :color "#000000"})
        (dot-node "edge" {:arrowhead "diamond"})]
       (mapv (partial dot-class options) definitions)
       (mapv dot-relation relations)])))

;;
;; Visualization
;;

(def ^:private +defaults+ {:fields? true})

(defn- process-schemas
  [f options]
  (let [options (merge {:ns *ns*} +defaults+ options)
        ns (:ns options)]
    (when-not (= ns *ns*)
      (require ns))
    (->> ns
         schema-definitions
         (dot-package options)
         viz/dot->image
         f)))

;;
;; Public API
;;

(defn visualize-schemas
  "Displays a schema visualization in an window. Takes an optional
  options map:

  :ns           - namespace symbol to be visualized (default *ns*)
  :fields?      - boolean, wether to show schema fields (default true)"
  ([] (visualize-schemas {}))
  ([options] (process-schemas viz/view-image options)))

(defn save-schemas
  "Same as visualize-schemas, but saves the result into a file."
  ([file] (save-schemas file {}))
  ([file options] (process-schemas #(viz/save-image % file) options)))
