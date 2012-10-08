(ns shopify.core
  (:require [clojure.string :as str])
  (:require digest)
  (:require clj-http.client))

(defn http-request [req] (clj-http.client/request (assoc req :throw-exceptions false)))

(defn user-auth-url
  "Take an options map and return the URL where the user can authorize the app."
  [app shop & {:keys [redirect]}]
  (str  "https://" shop "/admin/oauth/authorize"
        "?client_id=" (app :key)
        "&scope=" (->> (app :scope) (map name) (str/join ","))
        (when redirect (str "&redirect_url=" redirect))))

(defn verify-params
  "Uses your shared secret to verify that a signed map of query params is from Shopify."
  [app params]
  (let [secret (app :secret)
        signature (params :signature)
        params (dissoc params :signature)
        join-keypair (comp (partial str/join "=") (partial map name))
        sorted-param-string (->> params (map join-keypair) sort (str/join))]
    (= signature
      (digest/md5 (str secret sorted-param-string)))
    ))

(defn build-access-token-request
  "Take an options map and return a request map for a POST to Shopify to get a permanent token"
  [app shop code]
  { :method :post
    :url (str "https://" shop "/admin/oauth/access_token")
    :accept :json
    :as :json
    :form-params {
      :client_id (app :key)
      :client_secret (app :secret)
      :code code}})

(defn fetch-access-token
  "Takes an options map and fires off a blocking POST to Shopify which requests a permanent token."
  [options]
  (let [response (http-request (build-access-token-request options))]
    (get-in response [:body :access_token])))


(defn build-resource-request
  [{:keys [shop access-token] :as connection} method resource-path params]
  {
    :method method
    :url (str "https://" shop "/admin/" resource-path ".json")
    :headers {"X-Shopify-Access-Token" access-token}
    :accept :json
    :as :json
    :query-params params
    })

(defn perform-resource-request
  [& args]
  (http-request (apply build-resource-request args)))
