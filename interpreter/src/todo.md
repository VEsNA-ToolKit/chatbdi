# KQML Interpreter todo

## Interpreter
 - [ ] It should consider erroneous translations handling them with something like regex (?) or trying again.
 - [ ] Literals and so on should maintain a proprietary: now they are all mixed, but they should be subdivided and considered depending on the interlocutor.

## Instrumentation
 - [ ] Add instrumentation to describe a plan choosing from the set of the agent;
 - [ ] Add instrumentation to get actual agent intention and situation;
 - [ ] The agent should update beliefs, plans and literals when they got new.

## Chat
 - [x] It should support @at for sending messages to single agent.
    In that case it should provide this information to agent.

## Internal Actions
 - [x] They should remove the source from the parameter in square brackets, otherwise new beliefs are mispelled.

## Test
 - [ ] Auction Test
 - [ ] Domestic Robot