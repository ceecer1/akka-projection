#!/bin/sh

cart=`date '+%Y%m%d-%H%M%S.%s'`-$RANDOM

grpcurl -d '{"cartId":"'$cart'", "itemId":"socks", "quantity":3}' -plaintext shopping-cart-service-svc.akka-grpc-projection.svc.cluster.local:80 shoppingcart.ShoppingCartService.AddItem > /dev/null

COUNT=1
while [ $COUNT -le 6 ] ;
do
  echo "Updating cart $cart, times $COUNT"
  GRPCURL="grpcurl -d '{\"cartId\":\"$cart\", \"itemId\":\"socks\", \"quantity\":$COUNT}' -plaintext shopping-cart-service-svc.akka-grpc-projection.svc.cluster.local:80 shoppingcart.ShoppingCartService.UpdateItem > /dev/null"
  echo $GRPCURL
  RESULT=$(eval "$GRPCURL")
  COUNT=$((COUNT+1));
  sleep 0.02
done

grpcurl -d '{"cartId":"'$cart'"}' -plaintext shopping-cart-service-svc.akka-grpc-projection.svc.cluster.local:80 shoppingcart.ShoppingCartService.Checkout
