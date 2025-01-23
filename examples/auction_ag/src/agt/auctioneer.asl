!start. // initial goal

+!start : true
   <- .wait(4000);
      .print("Start the Auction!");
      .broadcast(tell, auction(service, flight_ticket(paris,athens,"15/12/2015")));
      .broadcast(tell, auction(service, flight_ticket(athens,paris,"18/12/2015"))).


+bid(Service, _)     // receives bids and checks for new winner
   :  .findall( b( Value, Agent ), bid( Service, Value )[ source( Agent ) ], BidsList ) & // L is a list of all bids, e.g.: [b(77.7,alice), b(91.7,giacomo), ...]
      .length( BidsList, 4 )  // all 4 expected bids was received, announce the winner
   <- .min( BidsList, b( Value, Winner) );
      .print( "Winner for ", Service, " is ", Winner, " with ", Value );
      .broadcast( tell, winner(Service, Winner) ).

{ include("$jacamo/templates/common-cartago.asl") }
{ include("$jacamo/templates/common-moise.asl") }
{ include("$moise/asl/org-obedient.asl") }
