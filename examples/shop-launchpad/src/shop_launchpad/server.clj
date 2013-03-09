(ns shop-launchpad.server
  (:use [ring.middleware.resource :only [wrap-resource]]
        [ring.middleware.reload-modified :only [wrap-reload-modified]]
        [cemerick.friend :only [authenticate]]
        [shop-launchpad.routes :only [app-routes]]
        [shop-launchpad.shopify :only [api-client]])
  (:require compojure.handler
            shopify.friend))

(defn wrap-auth
  [handler]
  (let [shopify-auth (shopify.friend/workflow {:api-client api-client})]
    (authenticate handler
                  {:allow-anon? true
                   :workflows [shopify-auth]})))

(defn wrap-reload-views
  [handler]
  (fn [request]
    (require 'shop-launchpad.views :reload)
    (handler request)))

(def app
  (-> app-routes
      wrap-auth
      (wrap-resource "public")
      compojure.handler/site
      (wrap-reload-modified ["src"])
      wrap-reload-views))
