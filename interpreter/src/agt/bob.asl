myname(bob).

+!start
    :   true
    <-  .wait(20000);
        .print("I'm sending the message");
        .broadcast(tell, myname(bob)).

+!say_hello(Agent)
    :   true
    <-  .send(Agent, tell, ciao).