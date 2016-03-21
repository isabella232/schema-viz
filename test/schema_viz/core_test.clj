(ns schema-viz.core-test
  (:require [schema-viz.core :as svc]
            [schema.core :as s]))

(s/defschema Country
  {:name (s/enum :fi :po)
   :neighbors [(s/recursive #'Country)]})

(s/recursive #'Country)

(s/defschema Burger
  {:name s/Str
   (s/optional-key :description) s/Str
   :origin (s/maybe Country)
   :price (s/constrained s/Int pos?)
   s/Keyword s/Any})

(s/defschema Order
  {:burger Burger
   :amount s/Int
   :delivery {:delivered s/Bool
              :address {:street s/Str
                        :zip s/Int
                        :country Country}
              :recipient {:name s/Str
                          :phone s/Str}}})

(comment

  (svc/visualize-schemas)

  (svc/save-schemas "schema.png"))
