(defproject shopify/shopify-friend "0.1.0-SNAPSHOT"
  :description "A friend workflow to authenticate with a Shopify shop using OAuth2"
  :url "https://github.com/jamesmacaulay/shopify-clj"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [com.cemerick/friend "0.1.2"]
                 [clj-http "0.5.6"]]
  :profiles {:dev {:dependencies [[ring-mock "0.1.3"]]}})
