same_structure(Term1, Term2) :-
    Term1 =.. [F|Args1],
    Term2 =.. [F|Args2],
    same_args(Args1, Args2).

same_args([], []).
same_args([A1|R1], [A2|R2]) :-
    ( var(A1) ->
        var(A2)
    ; compound(A1), compound(A2) ->
        A1 =.. [F|Args1],
        A2 =.. [F|Args2],
        same_args(Args1, Args2)
    ; atomic(A1), atomic(A2) ->
        A1 == A2
    ; fail  % tipi incompatibili
    ),
    same_args(R1, R2).
