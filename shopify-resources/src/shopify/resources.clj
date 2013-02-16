(ns shopify.resources
  (:refer-clojure :exclude [comment])
  (:require [clojure.string :as str]
            [clojure.set :as set]
            clj-http.core
            clj-http.client
            clj-http.links
            clj-http.util))

(defn name-str
  [x]
  (if (keyword? x)
    (name x)
    (str x)))

(defn params->query-string
  "Builds Rails-style nested query strings from param maps"
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
    (->> (name-str data)
         clj-http.util/url-encode
         (str prefix (when prefix \=)))
    :else
    prefix))

(defn wrap-query-params
  "Middleware which converts a :query-params map into a Rails-style nested :query-string"
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
  [client]
  (fn [req]
    (let [req (assoc req
                :accept :json
                :as :json
                :content-type :json)]
      (if-let [uri (:uri req)]
        (client (assoc req :uri (str/replace uri #"(?<!\.json)$" ".json")))
        (client req)))))

(defn wrap-access-token
  "Middleware converting an :access-token option into the appropriate auth header"
  [client]
  (fn [req]
    (if-let [token (:access-token req)]
      (client (-> req
                  (dissoc :access-token)
                  (assoc-in [:headers "x-shopify-access-token"] token)))
      (client req))))

(defn wrap-ssl
  "Middleware defaulting requests to ssl"
  [client]
  (fn [req]
    (if-let [scheme (:scheme req)]
      (client req)
      (client (-> req
                  (assoc :scheme :https))))))

(defn wrap-shop
  "Middleware converting a :shop option into :server-name"
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
  
  :retry-on-throttle-errors - default:true, whether to retry throttled requests
  :throttle-retry-delay - default:60, how many seconds to wait between throttle retries
  :max-throttle-retries - default:12, how many times to retry before giving up and returning the error response
  
  In the response:
  
  :throttle-retry-count - number of retries (absent when 0)"
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

(def ^:private query-string-methods
  #{:get :head :delete})

(defn wrap-generic-params-key
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

(defn wrap-request
  [request]
  (-> request
      clj-http.client/wrap-request-timing
      wrap-retry-on-throttle-errors
      clj-http.client/wrap-lower-case-headers
      ; clj-http.client/wrap-query-params
      wrap-query-params
      ; clj-http.client/wrap-basic-auth
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
      wrap-generic-params-key
      clj-http.client/wrap-method
      ; clj-http.cookies/wrap-cookies
      clj-http.links/wrap-links
      ; clj-http.client/wrap-unknown-host
      wrap-json-format
      wrap-access-token
      wrap-shop
      wrap-ssl))

(def request
  (wrap-request clj-http.core/request))

(defn collection-name
  "Converts resource keywords to their plural forms, unless they're singleton resources."
  [resource]
  (let [resource (name resource)]
    (case resource
      ("country" "countries") "countries"
      ("shop" "shops") "shop"
      (str/replace-first resource #"s?$" "s"))))

(defn member-name
  "Converts resource keywords to their singular forms."
  [resource]
  (let [resource (name resource)]
    (case resource
      ("country" "countries") "country"
      (str/replace-first resource #"s?$" ""))))

(def path-params
  ^{:doc "Takes a route template like \"/admin/blogs/:blog_id/articles\" and returns a set of dynamic segments as keywords like #{:blog_id}"}
  (memoize (fn path-params [route]
             (->> (re-seq #"(?<=:)\w+" route)
                  (map keyword)
                  set))))

(defn pick-route
  "Pick the first satisfyable route template in a collection, given a collection of available keys"
  [routes params]
  (let [available-keys (->> params
                            (filter #(not (nil? (val %))))
                            (map first)
                            set)]
    (some #(let [unfilled-segments (set/difference (path-params %)
                                                   (set available-keys))]
             (when (empty? unfilled-segments) %))
          routes)))


(defn render-route
  "Given a route template and a map of params, return a partial request map"
  [route params]
  (let [dynamic-segments (path-params route)
        reducer (fn [req [k v :as param]]
                  (if (dynamic-segments k)
                    (assoc req
                      :uri (str/replace-first
                             (:uri req)
                             (re-pattern (str k "(?=$|\\/)"))
                             (clj-http.util/url-encode (name-str v))))
                    (assoc-in req [:params k] v)))]
    (reduce reducer
            {:uri route}
            params)))


(defn- collection-route
  ([resource]
    (collection-route resource "/admin"))
  ([resource prefix]
    (str prefix \/ (collection-name resource))))

(defn- collection-action-route
  ([resource]
    (collection-action-route resource "/admin"))
  ([resource prefix]
    (str (collection-route resource prefix) "/:action")))

(defn- member-route
  ([resource]
    (member-route resource "/admin"))
  ([resource prefix]
    (str (collection-route resource prefix) "/:id")))

(defn- member-action-route
  ([resource]
    (member-action-route resource "/admin"))
  ([resource prefix]
    (str (collection-route resource prefix) "/:id/:action")))

(defn- prefixed-collection-routes
  [resource prefix]
  (list (collection-action-route resource prefix)
        (collection-route resource prefix)))

(defn- prefixed-member-routes
  [resource prefix]
  (list (member-action-route resource prefix)
        (member-route resource prefix)))

(defn- shallow-collection-routes
  [resource]
  (prefixed-collection-routes resource "/admin"))

(defn- shallow-member-routes
  [resource]
  (prefixed-member-routes resource "/admin"))

(defn- prefixed-and-shallow-collection-routes
  [resource prefix]
  (concat (prefixed-collection-routes resource prefix)
          (shallow-collection-routes resource)))

(defn- prefixed-and-shallow-member-routes
  [resource prefix]
  (concat (prefixed-member-routes resource prefix)
          (shallow-member-routes resource)))

(defn- prefixed-and-shallow-routes
  [resource prefix]
  {:collection (prefixed-and-shallow-collection-routes resource prefix)
   :member (prefixed-and-shallow-member-routes resource prefix)})

(def resource-types
  {:articles
   {:routes
    {:collection (prefixed-collection-routes
                   :articles "/admin/blogs/:blog_id")}}
   :assets
   {:routes
    {:collection (prefixed-and-shallow-collection-routes
                   :assets "/admin/themes/:theme_id")
     :member (prefixed-and-shallow-collection-routes
               :assets "/admin/themes/:theme_id")}}
   :customers
   {:routes
    {:collection (prefixed-and-shallow-collection-routes
                   :customers "/admin/customer_groups/:customer_group_id")}}
   :events
   {:routes (prefixed-and-shallow-routes
              :events "/admin/:resource/:resource_id")}
   :fulfillments
   {:routes
    {:collection (prefixed-collection-routes
                   :fulfillments "/admin/orders/:order_id")}}
   :metafields
   {:routes (prefixed-and-shallow-routes
              :metafields "/admin/:resource/:resource_id")}
   :product_images
   {:routes
    {:collection (prefixed-collection-routes
                   :product_images "/admin/products/:product_id")}}
   :product_variants
   {:routes
    {:collection (prefixed-collection-routes
                   :product_variants "/admin/products/:product_id")}}
   :provinces
   {:routes
    {:collection (prefixed-collection-routes
                   :provinces "/admin/countries/:country_id")}}
   :shop
   {:routes
    {:collection (shallow-collection-routes :shop)
     :member (shallow-collection-routes :shop)}}
   :transactions
   {:routes
    {:collection (prefixed-collection-routes
                   :transactions "/admin/orders/:order_id")}}})

(defn routes-for-resource
  "Takes a resource-type (e.g. :products) and cardinality (:member or :collection) and returns a sequence of routes."
  [resource-type cardinality]
  (or (get-in resource-types [resource-type :routes cardinality])
      (if (= :member cardinality)
        (shallow-member-routes resource-type)
        (shallow-collection-routes resource-type))))

(defn endpoint
  [resource-type cardinality params]
  (-> (routes-for-resource resource-type cardinality)
      (pick-route params)
      (render-route params)))

(def scope-keys-for-resource
  (memoize (fn scope-keys-for-resource
             [resource-type]
             (let [routes (mapcat (partial routes-for-resource resource-type)
                                  [:collection :member])]
               (into #{} (mapcat path-params routes))))))

(defn transform-parent-resource-attrs
  [attrs]
  (let [renamed-attrs (set/rename-keys attrs {:owner_resource :resource
                                              :owner_id :resource_id})
        resource (:resource renamed-attrs)]
    (if (string? resource)
      (assoc renamed-attrs :resource (collection-name resource))
      renamed-attrs)))

(defmulti attrs-as-scope-params
  (fn [resource-type attrs] resource-type))

(defmethod attrs-as-scope-params :events
  [_ attrs] (transform-parent-resource-attrs attrs))

(defmethod attrs-as-scope-params :metafields
  [_ attrs] (transform-parent-resource-attrs attrs))

(defmethod attrs-as-scope-params :default
  [_ attrs]
  attrs)

(defn partition-keys
  "Takes a map and a predicate and returns two maps split by which keys satisfy the predicate"
  [m pred]
  (reduce (fn [result [k v]]
            (let [i (if (pred k) 0 1)]
              (assoc-in result [i k] v)))
          [{} {}]
          m))

(defn extract-scope-params
  "Takes a resource type-keyword and a map of member attributes, and returns a map of scope params and a map of the remaining attributes"
  [resource-type member-attrs]
  (partition-keys (attrs-as-scope-params resource-type member-attrs)
                  (scope-keys-for-resource resource-type)))

(defn attrs-to-params
  [resource-type attrs]
  (let [[scope-params attrs] (extract-scope-params resource-type attrs)
        root-key (-> resource-type member-name keyword)]
    (if (empty? attrs)
      scope-params
      (assoc scope-params root-key attrs))))

(defmulti create-request
  (fn [resource-type attrs] resource-type))
(declare update-request)
(defmethod create-request :assets
  [_ attrs]
  (update-request :assets attrs))
(defmethod create-request :default
  [resource-type attrs]
  (let [params (attrs-to-params resource-type attrs)]
    (assoc (endpoint resource-type :collection params)
      :method :post)))

(defmulti get-collection-request
  (fn [resource-type params] resource-type))
(defmethod get-collection-request :default
  [resource-type params]
  (assoc (endpoint resource-type :collection params)
    :method :get))

(defmulti get-member-request
  (fn [resource-type attrs] resource-type))
(defn default-get-member-request
  [resource-type attrs]
  (let [params (attrs-to-params resource-type attrs)]
    (assoc (endpoint resource-type :member params)
      :method :get)))
(defmethod get-member-request :assets
  [_ attrs]
  (let [pk-keys (if (nil? (:theme_id attrs))
                  #{:key}
                  #{:theme_id :key})]
    (default-get-member-request :assets (select-keys attrs pk-keys))))
(defmethod get-member-request :default
  [resource-type attrs]
  (default-get-member-request resource-type attrs))

(defmulti update-request
  (fn [resource-type attrs] resource-type))
(defmethod update-request :default
  [resource-type attrs]
  (let [params (attrs-to-params resource-type attrs)]
    (assoc (endpoint resource-type :member params)
      :method :put)))

(defmulti persisted?
  (fn [resource-type attrs] resource-type))
(defmethod persisted? :assets
  [resource-type attrs]
  (contains? attrs :key))
(defmethod persisted? :default
  [resource-type attrs]
  (contains? attrs :id))

(defmulti save-request
  (fn [resource-type attrs] resource-type))
(defmethod save-request :default
  [resource-type attrs]
  (if (persisted? resource-type attrs)
    (update-request resource-type attrs)
    (create-request resource-type attrs)))

(defmulti delete-request
  (fn [resource-type attrs] resource-type))
(defmethod delete-request :default
  [resource-type attrs]
  (let [params (attrs-to-params resource-type attrs)]
    (assoc (endpoint resource-type :member params)
      :method :delete)))

(defn count-request
  [resource-type params]
  (get-collection-request resource-type (assoc params :action :count)))
