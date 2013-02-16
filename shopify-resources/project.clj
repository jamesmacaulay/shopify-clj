(defproject shopify/shopify-resources "0.1.0-SNAPSHOT"
  :description "Functions for interacting with a shop's resources"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[clj-http "0.6.3"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.4.0"]]}
             :1.3 {:dependencies [[org.clojure/clojure "1.3.0"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.0-RC16"]]}}
  :aliases {"all" ["with-profile" "1.3:dev:1.5"]})
