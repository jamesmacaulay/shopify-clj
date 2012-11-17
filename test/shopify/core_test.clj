(ns shopify.core-test
  (:use clojure.test
        shopify.core))

(def default-app {
  :key "01abfc750a0c942167651c40d088531d"
  :secret "dc2e817cb95adce7164db4767a13a53f"
  :scope [:read_orders, :write_content]
  })

(def default-connection {
  :shop "xerxes.myshopify.com"
  :access-token "e5ea7fb51ff27a20c3f622df66b9acdc"
  })

(deftest user-auth-url-test
  (testing "(user-auth-url options) returns a user auth url"
    (is (=
      (str  "https://xerxes.myshopify.com"
            "/admin/oauth/authorize"
            "?client_id=01abfc750a0c942167651c40d088531d"
            "&scope=read_orders,write_content"
            "&redirect_uri=http://shopify-test.heroku.com/landing-page")
      (user-auth-url default-app "xerxes.myshopify.com"
        :redirect "http://shopify-test.heroku.com/landing-page"))))
  (testing "redirect param is optional"
    (is (=
      (str  "https://xerxes.myshopify.com"
            "/admin/oauth/authorize"
            "?client_id=01abfc750a0c942167651c40d088531d"
            "&scope=read_orders,write_content")
      (user-auth-url default-app "xerxes.myshopify.com")))))


(deftest verify-params-test
  (testing "valid params"
    (let [params {
            :shop       "xerxes.myshopify.com"
            :code       "78415eb05dd9fc31283063c71952303c"
            :timestamp  "1349400031"
            :signature  "1dfa3f92d9500dbccf3aa5671bb8e78c"}]
    (is (= true
      (verify-params default-app params)))))
  (testing "invalid params"
    (let [params {
            :shop       "another-shop.myshopify.com"
            :code       "78415eb05dd9fc31283063c71952303c"
            :timestamp  "1349400031"
            :signature  "1dfa3f92d9500dbccf3aa5671bb8e78c"}]
    (is (= false
      (verify-params default-app params))))))


(deftest build-access-token-request-test
  (testing "(build-access-token-request options) generates a request to fetch a permanent access token")
    (let [shop "xerxes.myshopify.com"
          code "ffe51d3e7d8297237588704eeddc6ab2"]
      (is (=
        { :method :post
          :url "https://xerxes.myshopify.com/admin/oauth/access_token"
          :accept :json
          :as :json
          :form-params {
            :client_id "01abfc750a0c942167651c40d088531d"
            :client_secret "dc2e817cb95adce7164db4767a13a53f"
            :code "ffe51d3e7d8297237588704eeddc6ab2"}}
        (build-access-token-request default-app shop code)))))

(deftest build-resource-request-test
  (testing "(build-resource-request connection method resource-path params) builds the appropriate authenticated request")
    (let [connection default-connection
          method :get
          resource-path "products"
          params {:limit 2}]
      (is (=
        { :method :get
          :url "https://xerxes.myshopify.com/admin/products.json"
          :headers
            {"X-Shopify-Access-Token" "e5ea7fb51ff27a20c3f622df66b9acdc"}
          :accept :json
          :as :json
          :query-params {:limit 2}}
        (build-resource-request connection method resource-path params)))))











