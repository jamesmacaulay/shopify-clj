(defproject shopify "0.1.0-SNAPSHOT"
  :description "A set of libraries for interacting with the Shopify platform"
  :url "https://github.com/jamesmacaulay/shopify-clj"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[shopify/shopify-core "0.1.0-SNAPSHOT"]
                 [shopify/shopify-friend "0.1.0-SNAPSHOT"]
                 [shopify/shopify-resources "0.1.0-SNAPSHOT"]]
  :plugins [[lein-sub "0.2.4"]
            [lein-marginalia "0.7.1"]]
  :sub ["shopify-core"
        "shopify-friend"
        "shopify-resources"])