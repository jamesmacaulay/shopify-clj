(ns shopify.friend
  "Provides a workflow for [friend](https://github.com/cemerick/friend)."
  (:require [cemerick.friend :as friend]
            [clj-http.client :as client]))

(defn user-auth-url
  "Takes an api-client map, a shop domain, and a redirect path and returns
  the URL where the user can grant access the api-client."
  [api-client shop & [redirect-path]]
  (str "https://" shop "/admin/oauth/authorize"
       "?client_id=" (:key api-client)
       "&scope=" (->> (:scope api-client) (map name) (clojure.string/join ","))
       (when redirect-path
         (str "&redirect_uri=" (:url api-client) redirect-path))))

(defn build-access-token-request
  "Takes an api-client map, a myshopify domain, and a temporary code.
  Returns a request map for a POST to Shopify to get a permanent token"
  [api-client shop code]
  {:method :post
   :url    (str "https://" shop "/admin/oauth/access_token")
   :accept :json
   :as     :json
   :form-params {:client_id (api-client :key)
                 :client_secret (api-client :secret)
                 :code code}})

(defn fetch-access-token
  "Takes an api-client map, a myshopify domain, and a temporary access code.
  Fires off a POST to Shopify requesting a permanent token, which is
  then returned."
  [api-client shop code]
  (let [response (client/request (build-access-token-request api-client shop code))]
    (get-in response [:body :access_token])))

(defn handle-callback-request
  "Uses the temporary code from an auth callback to fetch a permanent
  access token from Shopify and return it in an auth map."
  [api-client request]
  (let [shop (get-in request [:query-params "shop"])
        code (get-in request [:query-params "code"])
        access-token (fetch-access-token api-client shop code)]
    (with-meta {:identity access-token
                :access-token access-token
                :shop shop}
               {:type ::friend/auth
                ::friend/workflow :shopify
                ::friend/redirect-on-auth? true})))

(defn normalize-shop-domain
  "Puts in the .myshopify.com if necessary."
  [domain]
  (cond
    (re-find #"\.myshopify\.com\z" domain)
      domain
    (re-find #"\.shopify\.com\z" domain)
      (clojure.string/replace domain #"\.shopify\.com\z" ".myshopify.com")
    :else (str domain ".myshopify.com")))

(defn handle-login-request
  "Returns a redirect response to the user auth URL for the shop
  specified in the query params."
  [api-client request callback-path]
  (let [shop (normalize-shop-domain (get-in request
                                            [:query-params "shop"]))]
    (ring.util.response/redirect
      (user-auth-url api-client shop callback-path))))

(defn callback-request-matcher
  "Returns whether or not the request is an auth callback, for a given
  callback path."
  [callback-path request]
  (and (= (request :uri) callback-path)
       (contains? (request :query-params) "code")
       (contains? (request :query-params) "shop")))

(defn login-request-matcher
  "Returns whether or not the request is a login, for a given login path."
  [login-path request]
  (and (= (request :uri) login-path)
       (contains? (request :query-params) "shop")))

(defn workflow
  "Takes a config map and returns a friend workflow. The only required
config key is `:api-client`. Optional keys are `:login-path` (defaults to
\"/auth/shopify\") and `:callback-path` (defaults to
\"/auth/shopify/callback\"). Here's a typical config:

    {:api-client
     {:url \"http://myapp.com\"
      :key \"01abfc750a0c942167651c40d088531d\"
      :secret \"dc2e817cb95adce7164db4767a13a53f\"
      :scope [:read_orders, :write_content]}
     :login-path \"/login\"}
"
  [{:keys [api-client] :as config}]
  (let [callback-path (get config :callback-path "/auth/shopify/callback")
        login-path (get config :login-path "/auth/shopify")
        callback-request? (partial callback-request-matcher callback-path)
        login-request? (partial login-request-matcher login-path)]
    (fn [request]
      (cond (callback-request? request)
            (handle-callback-request api-client request)
            
            (login-request? request)
            (handle-login-request api-client request callback-path)))))

(defn shopify-auth?
  "Tells you if a friend auth is from shopify"
  [auth-map]
  (-> auth-map meta ::friend/workflow (= :shopify)))

(defn shopify-auths
  "Takes a ring request and returns a sequence of the current Shopify auth maps."
  [request]
  (->> request
       friend/identity
       :authentications
       vals
       (filter shopify-auth?)))

(defn logged-in?
  "Tells you whether or not there's an active Shopify authentication for this request."
  [request]
  (not (empty? (shopify-auths request))))

(defn current-authentication
  "Returns the current friend authentication if it's from Shopify, otherwise the first shopify auth available"
  [request]
  (if-let [current-auth (friend/current-authentication request)]
    (if (shopify-auth? current-auth)
      current-auth
      (first (shopify-auths request)))))
