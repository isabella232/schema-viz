# Schema-viz

[Schema](https://github.com/plumatic/schema) visualization using Graphviz.

[![Clojars Project](http://clojars.org/metosin/schema-viz/latest-version.svg)](http://clojars.org/metosin(schema-viz))

## Prerequisites

Install [Graphviz](http://www.graphviz.org/).

## Usage

Public functions in `schema-viz.core`:
* `visualize-schemas` to display schemas from a namespace in a window.
* `save-schemas` - same as visualize-schemas, but saves the result into a file.

Both take an optional options-map to configure the rendering process.
See docs for details.

```clj
(require '[schema-viz.core :as svc])
(require '[schema.core :as s])

(s/defschema Country
  {:name (s/enum :FI :PO)})

(s/defschema Burger
  {:name s/Str
   (s/optional-key :description) s/Str
   :origin Country
   :price (s/constrained s/Int pos?)
   s/Keyword s/Any})

(s/defschema OrderLine
  {:burger Burger
   :amount s/Int})

(s/defschema Order
  {:lines [OrderLine]
   :delivery {:status s/Bool
              :address {:street s/Str
                        :zip s/Int
                        :country Country}}})

(svc/visualize-schemas)
```

Produces the following:

![Schema](dev-resources/schema.png)

## License

Copyright © 2015-2016 [Metosin Oy](http://www.metosin.fi)

Distributed under the Eclipse Public License, the same as Clojure.
