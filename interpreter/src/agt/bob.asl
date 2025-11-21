// This is just a sample agent that adds and removes beliefs and has two simple plans

myname(bob).

hello.

!add_belief.

+!add_belief
    :   true
    <-  .wait(3000);
        .print( "Hello" );
        +trial;
        .wait(1000);
        +trial(2);
        .wait(1000);
        -+trial(3);
        .broadcast( tell, hello ).

+!start
    :   true
    <-  .wait(20000);
        .print("I'm sending the message");
        .broadcast(tell, myname(bob)).

+!kqml_received( Sender, tell, Belief, _ )
    <-  .concat( "I received ", Belief, Sentence );
        .send( Sender, tell, Sentence ).
