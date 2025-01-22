myname(bob).

!add_belief.

+!add_belief
    :   true
    <-  .wait(3000);
        +prova;
        .wait(1000);
        +prova(2);
        .wait(1000);
        -+prova(3).

+!start
    :   true
    <-  .wait(20000);
        .print("I'm sending the message");
        .broadcast(tell, myname(bob)).

+!say_hello(Agent)
    :   true
    <-  .send(Agent, tell, ciao).