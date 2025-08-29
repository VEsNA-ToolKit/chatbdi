prova.
!start.

+auction(N)[source(S)]
	:	true
	<-	.send( S, tell, place_bid(N, math.random * 100 + 10 )).

+!prova : true <- .print( "ciao" ).

+!start
	:	true
	<-	.print( "ciao" );
		.wait( 5000 );
		!start.
