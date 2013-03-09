(ns shop-launchpad.routes
  (:use [compojure.core :only [defroutes GET ANY]]
        [compojure.route :only [not-found]]
        [ring.util.response :only [redirect]]
        [cemerick.friend :only [logout]]
        [shop-launchpad.session :only [shop-auths]]
        [shop-launchpad.data :only [shop-data]])
  (:require [shop-launchpad.views :as views]))
  
(defroutes app-routes
  (GET "/" request
    (apply str
           (views/main (map shop-data (shop-auths (:session request))))))
  (logout (ANY "/logout" request (redirect "/")))
  (not-found "Not Found"))
