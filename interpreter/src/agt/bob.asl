myname(bob).

!list_plans.

+!start
    :   true
    <-  .wait(20000);
        .print("I'm sending the message");
        .broadcast(tell, myname(bob)).

+!say_hello(Agent)
    :   true
    <-  .send(Agent, tell, ciao).

// +!list_plans
//     :   true
//     <-  .wait(2000);
//         interpreter.list_plans( Plans );
//         .concat( "The list of my plans I have available is ", Plans, PlanString );
//         .send( interpreter, tell, describe(PlanString)).