+auction( service, Description )[ source( Auctioneer ) ]  <- .broadcast( tell, bid( Description, math.random * 100 + 10 ) ).

{ include("$jacamo/templates/common-cartago.asl") }
{ include("$jacamo/templates/common-moise.asl") }
{ include("$moise/asl/org-obedient.asl") }
