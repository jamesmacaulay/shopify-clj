(ns shop-launchpad.config)

(def ^:dynamic config
  {:app-url (or (System/getenv "APP_URL") "http://localhost:3000")
   :shopify-api-key (System/getenv "SHOPIFY_API_KEY")
   :shopify-api-secret (System/getenv "SHOPIFY_API_SECRET")})