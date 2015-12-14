(defproject metosin/schema-viz "0.1.0"
  :description "Schema visualization using graphviz"
  :url "https://github.com/metosin/schema-viz"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"}
  :dependencies [[prismatic/schema "1.0.4"]
                 [rhizome "0.2.5"]]
  :plugins [[funcool/codeina "0.3.0"]]

  :codeina {:target "doc"
            :src-uri "http://github.com/metosin/schema-viz/blob/master/"
            :src-uri-prefix "#L"}

  :profiles {:dev {:plugins [[jonase/eastwood "0.2.2"]]
                   :dependencies [[criterium "0.4.3"]
                                  [org.clojure/clojure "1.7.0"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0-RC3"]]}}
  :aliases {"all" ["with-profile" "dev:dev,1.8"]
            "test-clj" ["all" "do" ["test"] ["check"]]})
