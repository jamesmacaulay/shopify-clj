(ns shopify.resources.client
  "A custom middleware stack to make Shopify API requests with clj-http."
  (:use [shopify.util :only [as-namespaced-str
                             dashes->underscores
                             underscores->dashes]]
        [shopify.resources.names :only [member-keyword]])
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            clj-http.client
            clj-http.links
            clj-http.util))

(defn params->query-string
  "Builds Rails-style nested query strings from param maps."
  [data & [prefix]]
  (cond
    (sequential? data)
    (->> data
         (map (fn [item]
                (params->query-string
                  item
                  (str prefix "[]"))))
         (str/join \&))
    (map? data)
    (->> data
         (map (fn [[k v]]
                (params->query-string
                  v
                  (let [k (clj-http.util/url-encode (name k))]
                    (if prefix (str prefix \[ k \]) k)))))
         (str/join \&))
    data
    (->> (as-namespaced-str data)
         clj-http.util/url-encode
         (str prefix (when prefix \=)))
    :else
    prefix))

(defn wrap-query-params
  "Middleware which converts a `:query-params` map into a Rails-style nested `:query-string`."
  [client]
  (fn [{:keys [query-params] :as req}]
    (if query-params
      (client (-> req
                  (dissoc :query-params)
                  (assoc
                    :query-string
                    (params->query-string query-params))))
      (client req))))

(defn wrap-json-format
  "Middleware which forces JSON for everything, and appends `.json` to paths if it isn't already there."
  [client]
  (fn [req]
    (let [req (assoc req
                :accept :json
                :as :json
                :content-type :json)]
      (if-let [uri (:uri req)]
        (client (assoc req :uri (str/replace uri #"(?<!\.json)$" ".json")))
        (client req)))))

(defn prepare-oauth2-request
  [req]
  (-> req
      (dissoc :access-token)
      (assoc-in [:headers "x-shopify-access-token"]
                (:access-token req))))

(defn prepare-basic-auth-request
  [req]
  (-> req
      (dissoc :api-key :password)
      (assoc :basic-auth [(:api-key req) (:password req)])))

(defn wrap-auth
  "If `:access-token` is present in the request, converts it into the appropriate auth header. Otherwise it looks for `:api-key` and `:password` to use basic auth."
  [client]
  (fn [req]
    (cond
      (contains? req :access-token)
        (client (prepare-oauth2-request req))
      (and (contains? req :api-key)
           (contains? req :password))
        (client (prepare-basic-auth-request req))
      :else
      (client req))))

(defn wrap-ssl
  "Middleware defaulting requests to SSL."
  [client]
  (fn [req]
    (if-let [scheme (:scheme req)]
      (client req)
      (client (-> req
                  (assoc :scheme :https))))))

(defn wrap-shop
  "Middleware converting a `:shop` option into `:server-name`."
  [client]
  (fn [req]
    (if-let [shop (:shop req)]
      (client (-> req
                  (dissoc :shop)
                  (assoc :server-name shop)))
      (client req))))

(defn wrap-retry-on-throttle-errors
  "Middleware which retries a request if it's being throttled.

Request options:

* `:retry-on-throttle-errors`: whether to retry throttled requests (default `true`)
* `:throttle-retry-delay`: how many seconds to wait between throttle retries (default `60`)
* `:max-throttle-retries`: how many times to retry before giving up and returning the error response (default `12`)

In the response:

* `:throttle-retry-count`: number of retries (key is absent when there were no retries)"
  [client]
  (fn [req]
    (if (= false (:retry-on-throttle-errors req))
      (client req)
      (let [wait-seconds (:throttle-retry-delay req 60)
            max-retries (:max-throttle-retries req 12)]
        (loop [retries 0]
          (let [response (client req)]
            (if (or
                  (not= 429 (:status response))
                  (= retries max-retries))
              (if (= retries 0)
                response
                (assoc response :throttle-retry-count retries))
              (do
                (Thread/sleep (* wait-seconds 1000))
                (recur (+ 1 retries))))))))))

(defn convert-keywords
  [data convert-fn]
  (clojure.walk/postwalk #(if (keyword? %)
                            (keyword (convert-fn %))
                            %)
                         data))

(defn wrap-underscored-request-params
  [client]
  (fn [req]
    (let [all-params (select-keys req #{:query-params :form-params})
          all-underscored-params
          (into {}
                (map (fn [[k params]]
                       [k (convert-keywords params
                                            dashes->underscores)])
                all-params))]
      (client (merge req all-underscored-params)))))

(defn wrap-dasherized-response
  [client]
  (fn [req]
    (let [response (client req)]
      (assoc response
        :body (convert-keywords (:body response)
                                underscores->dashes)))))

(defn- assoc-type
  [k m]
  (if (map? m)
    (assoc m :shopify.resources/type (member-keyword k))
    m))

(defn- insert-types
  [[k v]]
  (cond
    (map? v) [k (assoc-type k v)]
    (sequential? v) [k (vec (map (partial assoc-type k) v))]
    :else [k v]))

(defn embed-resource-types
  [data]
  (walk/postwalk #(if (map? %)
                    (into {} (map insert-types %))
                    %)
                 data))

(defn wrap-embed-resource-types-in-response
  [client]
  (fn [req]
    (let [response (client req)]
      (assoc response :body (embed-resource-types (:body response))))))

(defn remove-namespaced-keys
  [data]
  (walk/postwalk #(if (map? %)
                    (into {} (remove (comp namespace key) %))
                    %)
                 data))

(defn wrap-remove-namespaced-keys-from-request-params
  [client]
  (fn [req]
    (let [all-params (select-keys req #{:query-params :form-params})
          all-stripped-params (remove-namespaced-keys all-params)]
      (client (merge req all-stripped-params)))))

(def ^:private query-string-methods
  #{:get :head :delete})

(defn wrap-generic-params-key
  "Middleware which renames `:params` to `:query-params` when method is GET, HEAD, or DELETE. For other methods, `:params` becomes `:form-params`."
  [client]
  (fn [req]
    (if-let [params (:params req)]
      (if (query-string-methods (keyword (:request-method req)))
        (client (-> req
                    (dissoc :params)
                    (assoc :query-params params)))
        (client (-> req
                    (dissoc :params)
                    (assoc :form-params params))))
      (client req))))

(defn attach-response-to-resource-metadata
  [response]
  (let [body-with-meta
        (walk/postwalk #(if (map? %)
                          (with-meta % {:shopify.resources/response response})
                          %)
                       (:body response))]
    (assoc response :body body-with-meta)))

(def wrap-attach-response-to-resource-metadata
  (partial comp attach-response-to-resource-metadata))

(def ^:dynamic *base-request-opts* nil)

(defn wrap-base-request-opts
  [client]
  (fn [req]
    (client (merge *base-request-opts* req))))

(defn wrap-request
  "Wraps a request function with an appropriate stack of middleware."
  [request]
  (-> request
      clj-http.client/wrap-request-timing
      wrap-retry-on-throttle-errors
      clj-http.client/wrap-lower-case-headers
      ; clj-http.client/wrap-query-params
      wrap-query-params
      clj-http.client/wrap-basic-auth
      ; clj-http.client/wrap-oauth
      ; clj-http.client/wrap-user-info
      ; clj-http.client/wrap-url
      ; clj-http.client/wrap-redirects
      clj-http.client/wrap-decompression
      clj-http.client/wrap-input-coercion
      ; put this before output-coercion, so additional charset
      ; headers can be used if desired
      clj-http.client/wrap-additional-header-parsing
      clj-http.client/wrap-output-coercion
      clj-http.client/wrap-exceptions
      clj-http.client/wrap-accept
      clj-http.client/wrap-accept-encoding
      clj-http.client/wrap-content-type
      clj-http.client/wrap-form-params
      ; clj-http.client/wrap-nested-params
      wrap-embed-resource-types-in-response
      wrap-remove-namespaced-keys-from-request-params
      wrap-dasherized-response
      wrap-underscored-request-params
      wrap-generic-params-key
      clj-http.client/wrap-method
      ; clj-http.cookies/wrap-cookies
      clj-http.links/wrap-links
      ; clj-http.client/wrap-unknown-host
      wrap-json-format
      wrap-auth
      wrap-shop
      wrap-ssl
      wrap-attach-response-to-resource-metadata
      wrap-base-request-opts))

