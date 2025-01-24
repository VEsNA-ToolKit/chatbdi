// This is just a sample agent that adds and removes beliefs and has two simple plans

myname(bob).

!add_belief.

+!add_belief
    :   true
    <-  .wait(3000);
        +trial;
        .wait(1000);
        +trial(2);
        .wait(1000);
        -+trial(3).

+!start
    :   true
    <-  .wait(20000);
        .print("I'm sending the message");
        .broadcast(tell, myname(bob)).

+!say_hello(Agent)
    :   true
    <-  .send(Agent, tell, hello).
