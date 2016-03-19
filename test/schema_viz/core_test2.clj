(ns schema-viz.core-test
  (:require [schema-viz.core :as svc]
            [schema.core :as s]))

(s/defschema Origin
  {:country (s/enum :FI :PO)})

(s/defschema Pizza
  {:id s/Str
   :name (s/maybe s/Str)
   (s/optional-key :description) s/Str
   :origin Origin
   :prize (s/constrained Long pos?)
   s/Keyword s/Any})

(s/defschema OrderLine
  {:pizza Pizza
   :person {:name s/Str
            :category (s/maybe (s/enum :bad :good))
            :origin (s/conditional
                      map? Origin
                      :else String)
            :facts (s/cond-pre
                     {:fact1 s/Str
                      :fact2 s/Bool}
                     [{:fact s/Any}])}})

(s/defschema Order
  {:lines [OrderLine]
   :either (s/either s/Bool s/Int)
   :both (s/both s/Int (s/pred pos? 'pos?))
   :delivery {:status s/Bool
              :beer [svc/Beer]}})

(comment

  (svc/visualize-schemas {:ns *ns*})

  (svc/save-schemas "schema.png"))
