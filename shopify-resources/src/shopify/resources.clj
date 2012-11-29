(ns shopify.resources
  (:require [clj-http.client :as client]))

(defn build-request
  [{:keys [shop access-token] :as connection} method resource-path params]
  {:method method
   :url (str "https://" shop "/admin/" resource-path ".json")
   :headers {"X-Shopify-Access-Token" access-token}
   :accept :json
   :as :json
   :query-params params})

(defn request
  ([connection method resource-path]
    (client/request (build-request connection method resource-path {})))
  ([connection method resource-path params]
    (client/request (build-request connection method resource-path params))))
