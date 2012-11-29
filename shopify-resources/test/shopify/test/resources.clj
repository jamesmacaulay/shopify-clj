(ns shopify.test.resources
  (:use clojure.test
        shopify.resources))

(def default-connection
  {:shop "xerxes.myshopify.com"
   :access-token "e5ea7fb51ff27a20c3f622df66b9acdc"})

(deftest build-resource-request-test
  (testing "(build-resource-request connection method resource-path params) builds the appropriate authenticated request")
    (let [connection default-connection
          method :get
          resource-path "products"
          params {:limit 2}]
      (is (= {:method :get
              :url "https://xerxes.myshopify.com/admin/products.json"
              :headers {"X-Shopify-Access-Token" "e5ea7fb51ff27a20c3f622df66b9acdc"}
              :accept :json
              :as :json
              :query-params {:limit 2}}
             (build-resource-request connection method resource-path params)))))