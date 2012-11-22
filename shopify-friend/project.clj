(defproject com.shopify/shopify-friend "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [com.cemerick/friend "0.1.2"]
                 [clj-http "0.5.6"]]
  :profiles {:dev {:dependencies [[ring-mock "0.1.3"]]}})
