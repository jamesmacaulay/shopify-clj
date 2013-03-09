(ns shop-launchpad.views
  (:require [net.cgrand.enlive-html :as html]))

(defn sized-image [url size]
  (clojure.string/replace url #"\/([^\/\.\?]+)([^\/\?]+)(\?.*)?$" (str "/$1_" size "$2$3")))

(html/deftemplate layout "shop_launchpad/templates/index.html"
  [{:keys [content]}]
    [:#content]  (html/content content))

(html/defsnippet shop "shop_launchpad/templates/index.html" [:.shop]
  [shop]
  [:.shop-image-link] (html/set-attr :href (:admin-url shop))
  [:.shop-image] (html/set-attr :src (-> (:product-image-urls shop)
                                         first
                                         (sized-image "large")))
  [:.orders-badge] (html/do-> (html/set-attr :href (str (:admin-url shop) "/orders"))
                       (html/content (str (:unfulfilled-orders-count shop))))
  [:.shop-name-link] (html/do-> (html/set-attr :href (:admin-url shop))
                                (html/content (:name shop))))

(html/defsnippet shop-row "shop_launchpad/templates/index.html" [:.shop-row]
  [shops]
  [:.shop-row] (html/content (map shop shops)))

(defn main
  [shops]
  (let [shop-rows (map shop-row (partition-all 3 shops))]
    (layout {:content shop-rows})))