(defproject shop-launchpad "0.1.0-SNAPSHOT"
  :description "A simple Shopify app which lets you authenticate with multiple shops."
  :url "http://example.com/FIXME"
  :dependencies [[org.clojure/clojure "1.5.0"]
                 [ring/ring-core "1.1.8"]
                 [ring/ring-jetty-adapter "1.1.8"]
                 [compojure "1.1.5"]
                 [shopify "0.2.0-SNAPSHOT"]
                 [enlive "1.1.1"]]
  :plugins [[lein-ring "0.8.2"]]
  :ring {:handler shop-launchpad.server/app}
  :profiles
  {:dev {:dependencies [[ring-mock "0.1.3"]
                        [ring-reload-modified "0.1.1"]]}})
