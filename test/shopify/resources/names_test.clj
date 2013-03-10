(ns shopify.resources.names-test
  (:use clojure.test
        [shopify.resources.names :as names]))

(deftest member-name-test
  (testing "(member-name resource) chops off a trailing \"s\""
    (is (= "product" (member-name :products))))
  (testing "(member-name resource) converts dashes to underscores"
    (is (= "product_image" (member-name :product-images))))
  (testing "(member-name resource) leaves singulars alone"
    (is (= "product" (member-name :product))))
  (testing "(member-name resource) does :countries"
    (is (= "country" (member-name :countries)))
    (is (= "country" (member-name :country)))))

(deftest collection-name-test
  (testing "(collection-name resource) adds a trailing \"s\""
    (is (= "products" (collection-name :product))))
  (testing "(collection-name resource) converts dashes to underscores"
    (is (= "product_images" (collection-name :product-image))))
  (testing "(collection-name resource) leaves plurals alone"
    (is (= "products" (collection-name :products))))
  (testing "(collection-name resource) does :countries"
    (is (= "countries" (collection-name :country)))
    (is (= "countries" (collection-name :countries)))))
