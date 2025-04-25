
{ include("$jacamoJar/templates/common-cartago.asl") }

last_order_id(1). // initial belief

+!orderProduct(beer,5) : true
  <-  .send(supermarket, achieve, order(beer,5)).

// plan to achieve the goal "order" for agent Ag
+!order(Product,Qtd)[source(Ag)] : true
  <- ?last_order_id(N);
     OrderId = N + 1;
     -+last_order_id(OrderId);
     deliver(Product,Qtd);
     .send(Ag, tell, delivered(Product,Qtd,OrderId)).
