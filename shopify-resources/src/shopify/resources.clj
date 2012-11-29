(ns shopify.resources
  (:require [clj-http.client :as client]))

(defn build-resource-request
  [{:keys [shop access-token] :as connection} method resource-path params]
  {:method method
   :url (str "https://" shop "/admin/" resource-path ".json")
   :headers {"X-Shopify-Access-Token" access-token}
   :accept :json
   :as :json
   :query-params params})

(defn perform-resource-request
  [& args]
  (client/request (apply build-resource-request args)))
