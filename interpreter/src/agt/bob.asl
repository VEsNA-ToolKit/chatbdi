myname(bob).

!start.

+!start
    :   true
    <-  .wait(5000);
        .broadcast(tell, myname(bob)).