% constraints.pl
% Prolog validation rules for schedules.
% This file defines hard constraints that a schedule must satisfy. It is
% intended to be consulted from Java via JPL or used standalone in SWI-Prolog.
%
% Facts expected:
%   task(TaskId, Slot, Duration, ResourceId).
%   resource(ResourceId, CapacityPerSlot).
%   depends(TaskId, DependsOnTaskId).
%   room_capacity(ResourceId, SeatCapacity).        % optional: room seat capacity
%   task_students(TaskId, StudentCount).            % optional: number of students in class
%
% Determinism annotations (informal): the core predicates below are meant to be
% used in a semi-declarative/validation style and succeed/fail (deterministic).

%!
% occupied_slots(+Slot, +Duration, -Slots) is det.
%  Produce an inclusive list of time slots occupied by a task that starts at
%  Slot and spans Duration units. E.g., occupied_slots(0,2,[0,1]).
occupied_slots(Slot, Duration, Slots) :-
    End is Slot + Duration,
    E1 is End - 1,
    numlist(Slot, E1, Slots).

%!
% uses_resource_at_slot(+ResourceId, +Time, -TaskId) is nondet.
%  True when TaskId uses ResourceId at time Time, based on task/4 facts.
uses_resource_at_slot(ResourceId, Time, TaskId) :-
    task(TaskId, Slot, Duration, ResourceId),
    End is Slot + Duration,
    Time >= Slot,
    Time < End.

%!
% no_overbookings is det.
%  Succeeds iff no resource exceeds its capacity in any time slot.
no_overbookings :-
    \+ (resource(R, Cap),
        uses_resource_at_slot(R, T, _),
        findall(TaskId, uses_resource_at_slot(R, T, TaskId), L),
        length(L, C),
        C > Cap).

%!
% no_overlap_same_resource_when_cap1 is det.
%  For resources with capacity 1, ensures no two different tasks overlap in the
%  same time slot.
no_overlap_same_resource_when_cap1 :-
    \+ (resource(R, 1),
        uses_resource_at_slot(R, T, A),
        uses_resource_at_slot(R, T, B),
        A \= B).

%!
% respect_dependencies is det.
%  All task dependencies must be respected: a task's start time must be at or
%  after the end time of each of its dependencies.
respect_dependencies :-
    \+ (
        depends(Task, Dep),
        task(Task, SlotT, _, _),
        task(Dep, SlotD, DurD, _),
        EndD is SlotD + DurD,
        SlotT < EndD
    ).

%!
% seats_sufficient is det.
%  If room_capacity and task_students facts are provided, ensure the assigned
%  room has enough seats for the class size. If no such facts exist, this
%  predicate succeeds (opt-in constraint).
seats_sufficient :-
    ( \+ room_capacity(_, _) -> true
    ; \+ task_students(_, _) -> true
    ; \+ (
          task(TaskId, _, _, R),
          task_students(TaskId, S),
          room_capacity(R, Seats),
          S > Seats
        )
    ).

%!
% valid_slots_and_durations is det.
%  Basic sanity checks on task definitions: Slot >= 0 and Duration > 0.
valid_slots_and_durations :-
    \+ (
        task(_, Slot, Duration, _),
        (Slot < 0; Duration =< 0)
    ).

%!
% valid_schedule is det.
%  Main validation predicate: succeeds only if all constraints hold.
valid_schedule :-
    valid_slots_and_durations,
    no_overbookings,
    no_overlap_same_resource_when_cap1,
    respect_dependencies,
    seats_sufficient.

% -- Utilities to assert tasks/resources from lists if needed (for JPL) --
% They allow building the knowledge base from Java.

%!
% assert_task(+TaskId, +Slot, +Duration, +ResourceId) is det.
assert_task(TaskId, Slot, Duration, ResourceId) :-
    assertz(task(TaskId, Slot, Duration, ResourceId)).

%!
% assert_resource(+ResourceId, +Capacity) is det.
assert_resource(ResourceId, Capacity) :-
    assertz(resource(ResourceId, Capacity)).

%!
% assert_room_capacity(+ResourceId, +SeatCapacity) is det.
assert_room_capacity(ResourceId, SeatCapacity) :-
    assertz(room_capacity(ResourceId, SeatCapacity)).

%!
% assert_task_students(+TaskId, +StudentCount) is det.
assert_task_students(TaskId, StudentCount) :-
    assertz(task_students(TaskId, StudentCount)).

%!
% assert_dependency(+TaskId, +DependsOnTaskId) is det.
assert_dependency(TaskId, DependsOnTaskId) :-
    assertz(depends(TaskId, DependsOnTaskId)).

%!
% clear_facts is det.
%  Remove all dynamic facts to reset the KB between runs/tests.
clear_facts :-
    retractall(task(_,_,_,_)),
    retractall(resource(_,_)),
    retractall(depends(_,_)),
    retractall(room_capacity(_,_)),
    retractall(task_students(_,_)).
