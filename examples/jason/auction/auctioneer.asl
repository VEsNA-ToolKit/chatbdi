// this agent manages the auction and identify the winner

+!start_auction( Id )   // this goal is created by the GUI of the agent
    <- .broadcast( tell, auction( Id ) ).


@pb1[atomic]
+place_bid( Id ,_ )     // receives bids and checks for new winner
   :  .findall( b(Value,Agent), place_bid(Id,Value)[source(Agent)],L) &
      .length(L,4)  // all 4 expected bids was received
   <- .max(L,b(V,W));
      .print("Winner is ",W," with ", V);
      show_winner(N,W); // show it in the GUI
      .broadcast(tell, winner(W));
      .abolish(place_bid(Id,_)).
