(ns shopify.resources
  "Functions for making requests against a shop's authenticated API."
  (:use [shopify.resources.names :only [member-keyword
                                        collection-keyword]])
  (:require [shopify.resources.client :as client]
            [shopify.resources.routes :as routes]
            [flatland.useful.parallel :as parallel]
            [plumbing.core :as plumbing]
            clj-http.core))

(def request
  ^{:dynamic true
    :doc "Makes a request to the Shopify API, using `clj-http` with a custom middleware stack."}
  (client/wrap-request clj-http.core/request))

(defn- firstarg [x & _] x)

(defn- attrs-to-params
  "Takes a resource type and a map of member attributes. Returns a transformed map with all non-path params hoisted into their own map keyed by the singular form of the type keyword. E.g. `{:id 99, :page {:title \"foo\"}}`."
  [resource-type attrs]
  (let [[scope-params attrs] (routes/extract-path-params resource-type attrs)
        root-key (member-keyword resource-type)]
    (if (empty? attrs)
      scope-params
      (assoc scope-params root-key attrs))))

(defmulti create-request
  "Takes a keyword `resource-type` and a map of `attrs`, and returns a partial request map for creating a resource member."
  firstarg)
(declare update-request)
(defmethod create-request :assets
  [_ attrs]
  (update-request :assets attrs))
(defmethod create-request :default
  [resource-type attrs]
  (let [params (attrs-to-params resource-type attrs)]
    (assoc (routes/endpoint resource-type :collection params)
      :method :post)))

(defmulti get-list-request
  "Returns a partial request map to get a collection of the given resource type with the given params."
  firstarg)
(defmethod get-list-request :default
  [resource-type params]
  (assoc (routes/endpoint resource-type :collection params)
    :method :get))

(defmulti get-one-request
  "Returns a partial request map to get a member of the given resource with the given attributes."
  firstarg)
(defn- default-get-one-request
  [resource-type attrs]
  (let [params (attrs-to-params resource-type attrs)]
    (assoc (routes/endpoint resource-type :member params)
      :method :get)))
(defmethod get-one-request :assets
  [_ attrs]
  (let [pk-keys (if (nil? (:theme_id attrs))
                  #{:key}
                  #{:theme_id :key})]
    (default-get-one-request :assets (select-keys attrs pk-keys))))
(defmethod get-one-request :default
  [resource-type attrs]
  (default-get-one-request resource-type attrs))

(defmulti update-request
  "Returns a partial request map to update a member of the given resource with the given attributes."
  firstarg)
(defmethod update-request :default
  [resource-type attrs]
  (let [params (attrs-to-params resource-type attrs)]
    (assoc (routes/endpoint resource-type :member params)
      :method :put)))

(defmulti persisted?
  "Returns true if the given attributes appear to refer to a member which already exists on the server."
  firstarg)
(defmethod persisted? :assets
  [resource-type attrs]
  (contains? attrs :key))
(defmethod persisted? :default
  [resource-type attrs]
  (contains? attrs :id))

(def new?
  ^{:doc "Returns false if the given attributes appear to refer to a member which already exists on the server."}
  (complement persisted?))

(defmulti save-request
  "Delegates to `create-request` if the attributes are new, or `update-request` if they're persisted."
  firstarg)
(defmethod save-request :default
  [resource-type attrs]
  (if (persisted? resource-type attrs)
    (update-request resource-type attrs)
    (create-request resource-type attrs)))

(defmulti delete-request
  "Returns a partial request map to delete a resource member."
  firstarg)
(defmethod delete-request :default
  [resource-type attrs]
  (let [params (attrs-to-params resource-type attrs)]
    (assoc (routes/endpoint resource-type :member params)
      :method :delete)))

(defn get-count-request
  "Returns a partial request map to get the count of the given resource/params."
  [resource-type params]
  (get-list-request resource-type (assoc params :action :count)))

(defn- extract-collection
  "Takes a response map and returns the collection of the given type, if it is present."
  [response resource-type]
  (get-in response [:body (collection-keyword resource-type)]))

(defn- extract-member
  "Takes a response map and returns the member of the given type, if it is present."
  [response resource-type]
  (get-in response [:body (member-keyword resource-type)]))

(defmacro with-opts
  "A convenience macro to define the same base request options for any request to the Shopify API. `opts` would most often be an auth map, but it could include any default options for the request."
  [opts & exprs]
  `(binding [client/*base-request-opts* ~opts]
     ~@exprs))

(defn extract-kicker-args
  "Used by the kicker functions to parse argument lists. The most verbose form takes a resource type keyword, a map of either params or attributes, and a map of request options:
  
    (def auth {:shop \"foo.myshopify.com\"
               :access-token \"70bc2f19efa5129f202e661ac6fd38f3\"})
    (get-list :products {:limit 2} auth)

If you only provide one map, it's assumed to be the params/attributes, so you'll need to provide authentication with `with-opts`:

    (with-opts auth
      (get-list :products {:limit 2}))

If you don't give a keyword as the first argument, the function will look for the resource type under a `:shopify.resources/type` key in the params/attributes map. This key is present in all resource attribute maps returned by the library, so you can chain together reading and writing operations conveniently like so:

    (with-opts auth
      (-> (get-one :page {:id 99})
          (update-in [:title]
                     clojure.string/upper-case)
          (update-in [:body-html]
                     #(clojure.string/replace % \".\" \"!\"))
          save!))"
  [args]
  (let [[params request-opts] (filter map? args)
        params (or params {})
        resource-type (if (keyword? (first args))
                        (first args)
                        (:shopify.resources/type params))]
    [resource-type params request-opts]))

(defn- kicker-fn
  "Takes a request-building function and a value-extracting function, and returns a function which actually performs the request."
  [request-fn extract-fn]
  (fn [& args]
    (let [[resource-type params request-opts] (extract-kicker-args args)
          response (request (merge (request-fn resource-type params)
                                   request-opts))]
      (extract-fn response resource-type))))

(def get-list
  ^{:doc "Performs a GET request for a resource collection, returning a sequence of attribute maps. See `extract-kicker-args` for the available argument forms."}
  (kicker-fn get-list-request
             extract-collection))

(def get-one
  ^{:doc "Performs a GET request for a resource member, returning a map of attributes."}
  (kicker-fn get-one-request
             extract-member))

(def get-count
  ^{:doc "Performs a GET request for the count of a resource collection, returning a `java.lang.Long`."}
  (kicker-fn get-count-request
             (fn [response _] (get-in response [:body :count]))))

(defn get-shop
  "A convenience function to get the singleton shop resource."
  [& [request-opts]]
  (get-one :shop {} request-opts))

(def save!
  ^{:doc "Performs either a POST or a PUT to save the given attributes."}
  (kicker-fn save-request
             extract-member))

(def delete!
  ^{:doc "Performs a DELETE on the given resource."}
  (kicker-fn delete-request
             extract-member))

(defn get-all
  "Eagerly gets _all_ the resources described by the arguments in parallel. Returns a sequence which is a lazy concatenation of all pages."
  [& args]
  (let [[resource-type params request-opts] (extract-kicker-args args)
        resource-count (get-count resource-type
                                  params
                                  request-opts)
        page-size 250
        page-count (-> (/ resource-count page-size) Math/ceil int)
        page-numbers (range 1 (+ 1 page-count))
        get-page (fn [n]
                   (get-list resource-type
                             (assoc params
                               :limit page-size
                               :page n)
                             request-opts))
        pages (parallel/pcollect get-page
                                 page-numbers)]
    (plumbing/aconcat pages)))

