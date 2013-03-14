(defproject shopify "0.1.1"
  :description "A library for interacting with the Shopify platform."
  :url "https://github.com/jamesmacaulay/shopify-clj"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [org.flatland/useful "0.9.4"]
                 [prismatic/plumbing "0.0.1"]
                 [digest "1.4.0"]
                 [clj-http "0.6.3"]
                 [com.cemerick/friend "0.1.3"]]
  :profiles {:dev {:dependencies [[ring-mock "0.1.3"]
                                  [org.clojure/clojure "1.4.0"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.0"]]}}
  :aliases {"all" ["with-profile" "dev:dev,1.5"]}
  :plugins [[lein-marginalia "0.7.1"]
            [codox "0.6.4"]]
  :codox {:output-dir "docs/codox"})