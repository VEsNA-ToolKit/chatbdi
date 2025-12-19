// this agent manages the auction and identify the winner

+!start_auction( Id )   // this goal is created by the GUI of the agent
    <- .broadcast( tell, auction( Id ) ).


@pb1[atomic]
+place_bid( Id ,_ )     // receives bids and checks for new winner
   :  .findall( b(Amount,Agent), place_bid(Id,Amount)[source(Agent)],L) &
      .length(L,4)  // all 4 expected bids was received
   <- .max(L,b(Amount,Winner));
      .print("Winner is ",Winner," with ", Amount);
      show_winner(Id,Winner); // show it in the GUI
      .broadcast(tell, winner(Winner));
      .abolish(place_bid(Id,_)).
