(ns shop-launchpad.shopify
  (:use [shop-launchpad.config :only [config]]))

(def api-client
  {:url (:app-url config)
   :key (:shopify-api-key config)
   :secret (:shopify-api-secret config)
   :scope [:read_products :read_orders]})
