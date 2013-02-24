(ns shopify.resources.client-test
  (:use clojure.test
        shopify.resources.client))

(def default-session
  {:shop "xerxes.myshopify.com"
   :access-token "e5ea7fb51ff27a20c3f622df66b9acdc"})

(deftest params->query-string-test
  (testing "(params->query-string params) produces a nested query string using square bracket syntax"
    (is (= "asset[key]=templates%2Findex.liquid"
           (params->query-string {:asset {:key "templates/index.liquid"}})))
    (is (= "nest[num]=123&nest[all][]=this&nest[all][][silly]=stuff&nest[all][]=here"
           (params->query-string {:nest {:all ["this" {:silly :stuff} "here"] :num 123}})))))

(deftest wrap-json-format-middleware-test
  (testing "(wrap-json-format clj-http-client) wraps the client to set appropriate :accept and :as keys, and append .json format to paths"
    (let [wrapped-identity (wrap-json-format identity)]
      (is (= {:query-params {:foo "bar"}
              :accept :json
              :as :json
              :content-type :json}
             (wrapped-identity {:query-params {:foo "bar"}})))
      (is (= {:uri "/admin/products.json"
              :accept :json
              :as :json
              :content-type :json}
             (wrapped-identity {:uri "/admin/products"})))
      (is (= {:uri "/admin/products.json"
              :accept :json
              :as :json
              :content-type :json}
             (wrapped-identity {:uri "/admin/products.json"}))))))

(deftest wrap-access-token-middleware-test
  (testing "(wrap-access-token clj-http-client) wraps the client to convert an :access-token key into the appropriate auth header"
    (let [wrapped-identity (wrap-access-token identity)]
      (is (= {:shop "xerxes.myshopify.com"
              :headers {"x-shopify-access-token" "e5ea7fb51ff27a20c3f622df66b9acdc"}}
             (wrapped-identity default-session)))
      (is (= {:shop "xerxes.myshopify.com"
              :headers {"foo" "bar"
                        "x-shopify-access-token" "e5ea7fb51ff27a20c3f622df66b9acdc"}}
             (wrapped-identity (-> default-session
                                   (assoc :headers {"foo" "bar"}))))))))

(deftest wrap-shop-middleware-test
  (testing "(wrap-shop clj-http-client) wraps the client to change :shop keys into :server-name keys"
    (let [wrapped-identity (wrap-shop identity)]
      (is (= {:server-name "xerxes.myshopify.com"
              :access-token "e5ea7fb51ff27a20c3f622df66b9acdc"}
             (wrapped-identity default-session))))))

(deftest wrap-ssl-middleware-test
  (testing "(wrap-ssl clj-http-client) wraps the client to default :scheme to :https"
    (let [wrapped-identity (wrap-ssl identity)]
      (is (= {:scheme :https
              :shop "xerxes.myshopify.com"
              :access-token "e5ea7fb51ff27a20c3f622df66b9acdc"}
             (wrapped-identity default-session)))
      (is (= {:scheme :http
              :shop "xerxes.myshopify.com"
              :access-token "e5ea7fb51ff27a20c3f622df66b9acdc"}
             (wrapped-identity (assoc default-session :scheme :http)))))))

(deftest wrap-retry-on-throttle-errors-middleware-test
  (let [throttled-response {:status 429 :headers {} :body ""}
        ok-response {:status 200 :headers {} :body ""}
        mock-responder (fn [failure-count]
                         (let [counter (atom failure-count)]
                           (fn [req]
                             (if (= 0 @counter)
                               ok-response
                               (do
                                 (swap! counter dec)
                                 throttled-response)))))]
    (testing "wrap-retry-on-throttle-errors middleware doesn't do anything when :retry-on-throttle-errors is false"
      (let [fail-once (wrap-retry-on-throttle-errors (mock-responder 1))]
        (is (= throttled-response
               (fail-once {:throttle-retry-delay 0.1
                           :retry-on-throttle-errors false})))))
    (testing "wrap-retry-on-throttle-errors middleware doesn't retry on successful response"
      (let [succeed (wrap-retry-on-throttle-errors (constantly ok-response))]
        (is (= ok-response
               (succeed {:throttle-retry-delay 0.1})))))
    (testing "wrap-retry-on-throttle-errors middleware tells you how many times it had to retry, if not zero"
      (let [fail-twice (wrap-retry-on-throttle-errors (mock-responder 2))]
        (is (= (assoc ok-response
                 :throttle-retry-count 2)
               (fail-twice {:throttle-retry-delay 0.1
                            :max-throttle-retries 2})))))
    (testing "wrap-retry-on-throttle-errors middleware returns the last failed response if it hits the max number of retries"
      (let [fail-5-times (wrap-retry-on-throttle-errors (mock-responder 5))]
        (is (= (assoc throttled-response
                 :throttle-retry-count 4)
               (fail-5-times {:throttle-retry-delay 0.1
                              :max-throttle-retries 4})))))))

(deftest wrap-generic-params-key-test
  (testing "wrap-generic-params-key is middleware which converts a :params key to either :query-params or :form-params depending on request method"
    (let [wrapped-identity (wrap-generic-params-key identity)]
      (is (= {:request-method :get
              :query-params {:foo "bar"}}
             (wrapped-identity {:request-method :get
                                :params {:foo "bar"}})))
      (is (= {:request-method :post
              :form-params {:foo "bar"}}
             (wrapped-identity {:request-method :post
                                :params {:foo "bar"}}))))))

(deftest wrap-underscored-request-params-test
  (testing "wrap-underscored-request-params turns `:foo-bar` param keywords into `:foo_bar`."
    (let [wrapped-identity (wrap-underscored-request-params identity)]
      (is (= {:query-params {:foo_bar "baz-buzz"
                             :keyword :as_a_value}}
             (wrapped-identity {:query-params {:foo-bar "baz-buzz"
                                               :keyword :as-a-value}}))))))

(deftest wrap-dasherized-response-test
  (testing "wrap-dasherized-response turns `:foo_bar` keywords in the response into `:foo-bar`."
    (let [wrapped-underscore-emitter
          (wrap-dasherized-response
            (fn [req]
              {:body {:foo_foo {:bar_id 99
                                :images [{:baz_id 101} {:baz_id 102}]}}}))]
      (is (= {:body {:foo-foo {:bar-id 99
                               :images [{:baz-id 101} {:baz-id 102}]}}}
             (wrapped-underscore-emitter {}))))))

(deftest embed-resource-types-test
  (testing "embed-resource-types inserts :shopify.resources/type values into all resource entities in a nested map"
    (is (= {:products [{:shopify.resources/type :product
                        :id 100
                        :variants [{:shopify.resources/type :variant
                                    :id 1000}
                                   {:shopify.resources/type :variant
                                    :id 1001}]
                        :images [{:shopify.resources/type :image
                                  :id 2000}]}]}
           (embed-resource-types
             {:products [{:id 100
                         :variants [{:id 1000} {:id 1001}]
                         :images [{:id 2000}]}]})))))

(deftest wrap-embed-resource-types-in-response-test
  (testing "wrap-embed-resource-types-in-response inserts a :shopify.resources/type value into all resource entities in the response"
    (let [wrapped-identity
          (wrap-embed-resource-types-in-response identity)]
      (is (= {:body {:page {:shopify.resources/type :page
                            :id 99}}}
             (wrapped-identity {:body {:page {:id 99}}}))))))

(deftest remove-namespaced-keys-test
  (testing "remove-namespaced-keys removes all namespaced map entries from the given data structure"
    (is (= {:product {:id 100
                      :variants [{:id 1000} {:id 1001}]
                      :images [{:id 2000}]}}
           (remove-namespaced-keys
             {:product {:shopify.resources/type :product
                        :id 100
                        :variants [{:shopify.resources/type :variant
                                    :id 1000}
                                   {:shopify.resources/type :variant
                                    :id 1001}]
                        :images [{:shopify.resources/type :image
                                  :id 2000}]}})))))

(deftest wrap-remove-namespaced-keys-from-request-params-test
  (testing "wrap-remove-namespaced-keys-from-request-params removed namespaced map entries from query-params and form-params"
    (let [wrapped-identity
          (wrap-remove-namespaced-keys-from-request-params identity)]
      (is (= {:query-params {:asset {:key "snippets/foo.liquid"}}
              :uri "/admin/assets"}
             (wrapped-identity
               {:query-params {:asset {:shopify.resources/type :asset
                                       :key "snippets/foo.liquid"}}
                :uri "/admin/assets"})))
      (is (= {:form-params {:page {:title "foo"}}
              :uri "/admin/pages/99"}
             (wrapped-identity
               {:form-params {:page {:shopify.resources/type :page
                                     :title "foo"}}
                :uri "/admin/pages/99"}))))))

(deftest wrap-all-middleware-test
  (testing "(wrap-request clj-http-client) wraps the client fn in the appropriate middleware for Shopify resource requests, transforming the requests and responses correctly"
    (let [raw-page-count-response {:status 200
                                   :headers {"x-ua-compatible" "IE=Edge,chrome=1"
                                             "server" "nginx"
                                             "x-runtime" "0.044928"
                                             "content-encoding" "gzip"
                                             "p3p" "CP=\"NOI DSP COR NID ADMa OPTa OUR NOR\""
                                             "content-type" "application/json; charset=utf-8"
                                             "x-request-id" "362bb5e86ffa2fc3fd11df72a8ac33d6"
                                             "date" "Sat, 05 Jan 2013 05:10:40 GMT"
                                             "http_x_shopify_shop_api_call_limit" "2/500"
                                             "cache-control" "max-age=0, private, must-revalidate"
                                             "vary" "Accept-Encoding"
                                             "status" "200 OK"
                                             "transfer-encoding" "chunked"
                                             "etag" "\"a18b769e721d2462b4d3057ffe7ac0c0\""
                                             "x-shopify-shop-api-call-limit" "2/500"
                                             "connection" "close"}
                                   :body (byte-array (map byte [31, -117, 8, 0, 0, 0, 0, 0, 0, 3, -85, 86, 74, -50, 47, -51, 43, 81, -78, 50, -86, 5, 0, 106, 49, -10, -15, 11, 0, 0, 0]))}
          request (-> default-session
                      (assoc
                        :method :get
                        :uri "/admin/pages/count"
                        :params {:since-id 108828309}))
          wrapped-assertions (wrap-request
                               (fn [req]
                                 (is (= {:scheme :https
                                         :request-method :get
                                         :content-type :json
                                         :as :json
                                         :uri "/admin/pages/count.json"
                                         :query-string "since_id=108828309"
                                         :server-name "xerxes.myshopify.com"
                                         :headers
                                         {"accept-encoding" "gzip, deflate"
                                          "accept" "application/json"
                                          "content-type" "application/json"
                                          "x-shopify-access-token" "e5ea7fb51ff27a20c3f622df66b9acdc"}}
                                        req))
                                 raw-page-count-response))
          response (wrapped-assertions request)]
      (is (= {:count 2}
             (:body response)))
      (is (= (:headers raw-page-count-response)
             (:headers response))))))

