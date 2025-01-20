myname(bob).

!start.

+!start
    :   true
    <-  .wait(20000);
        .print("I'm sending the message");
        .broadcast(tell, myname(bob)).