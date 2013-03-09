(ns shop-launchpad.data
  (:require [shopify.resources :as shop]
            [clojure.walk :as walk]))

(defn get-unfulfilled-orders-count []
  (shop/get-count :orders {:fulfillment-status :unfulfilled}))

(defn get-product-image-urls []
  (->> (shop/get-list :products {:fields "images"})
       (mapcat :images)
       (map :src)
       (take 5)))

(defn shop-data [auth]
  (shop/with-opts auth
    (let [shop (future-call shop/get-shop)
          orders-count (future-call get-unfulfilled-orders-count)
          images (future-call get-product-image-urls)]
      (assoc (select-keys @shop [:id :name :myshopify-domain])
        :admin-url (str "https://" (:myshopify-domain @shop) "/admin")
        :unfulfilled-orders-count @orders-count
        :product-image-urls @images))))
