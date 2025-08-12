% test_constraints.pl
% Test suite for constraints.pl

:- initialization(run_tests, main).

run_tests :-
    format('Running Prolog constraint tests...~n', []),
    consult('prolog/constraints.pl'),
    ( test_valid_schedule -> format(' [OK] valid schedule passes~n', []) ; (format(' [FAIL] valid schedule should pass~n', []), halt(1)) ),
    ( test_overbooking_fails -> format(' [OK] overbooking schedule fails~n', []) ; (format(' [FAIL] overbooking should fail~n', []), halt(1)) ),
    ( test_dependency_violation_fails -> format(' [OK] dependency violation fails~n', []) ; (format(' [FAIL] dependency violation should fail~n', []), halt(1)) ),
    format('All Prolog tests passed.~n', []),
    halt(0).

setup_valid_state :-
    clear_facts,
    assert_resource('R1', 1),
    assert_resource('R2', 1),
    % tasks: T1 on R1 dur2 at slot0; T2 depends on T1 at slot2; T3 on R2 no deps at slot0
    assert_task(1, 0, 2, 'R1'),
    assert_task(2, 2, 1, 'R1'),
    assert_task(3, 0, 3, 'R2'),
    assert_dependency(2, 1).

setup_overbook_state :-
    clear_facts,
    assert_resource('R1', 1),
    % Two tasks overlap on R1 at time 0..1
    assert_task(1, 0, 2, 'R1'),
    assert_task(2, 1, 2, 'R1').

setup_dependency_violation_state :-
    clear_facts,
    assert_resource('R1', 2),
    % T2 depends on T1 but starts before T1 ends
    assert_task(1, 0, 3, 'R1'),
    assert_task(2, 1, 1, 'R1'),
    assert_dependency(2, 1).


% Tests

test_valid_schedule :-
    setup_valid_state,
    valid_schedule.

% Should fail due to capacity 1 and overlapping tasks
test_overbooking_fails :-
    setup_overbook_state,
    ( valid_schedule -> fail ; true ).

% Should fail due to dependency violation
test_dependency_violation_fails :-
    setup_dependency_violation_state,
    ( valid_schedule -> fail ; true ).