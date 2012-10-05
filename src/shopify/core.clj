(ns shopify.core
  (:require [clojure.string :as str])
  (:require digest)
  (:require [clj-http.client :as client]))

(defn user-auth-url
  "Take an options map and return the URL where the user can authorize the app."
  [{:keys [shop app redirect]}]
  (str  "https://" shop "/admin/oauth/authorize"
        "?client_id=" (app :key)
        "&scope=" (->> (app :scope) (map name) (str/join ","))
        (when redirect (str "&redirect_url=" redirect))))

(defn verify-params
  "Uses your shared secret to verify that a signed map of query params is from Shopify."
  [secret params]
  (let [signature (params :signature)
        params (dissoc params :signature)
        join-keypair (comp (partial str/join "=") (partial map name))
        sorted-param-string (->> params (map join-keypair) sort (str/join))]
    (= signature
      (digest/md5 (str secret sorted-param-string)))
    ))

(defn build-access-token-request
  "Take an options map and return a request map for a POST to Shopify to get a permanent token"
  [{:keys [shop app code]}]
  { :method :post
    :url (str "https://" shop "/admin/oauth/access_token")
    :as :json
    :form-params {
      :client_id (app :key)
      :client_secret (app :secret)
      :code code}})

(defn fetch-access-token
  "Takes an options map and fires off a blocking POST to Shopify which requests a permanent token."
  [options]
  (let [response (client/request (build-access-token-request options))]
    (get-in response [:body :access_token])))