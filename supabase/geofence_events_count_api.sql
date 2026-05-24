create or replace function count_geofence_events()
returns integer
language sql
stable
as $$
    select count(*)::integer
    from geofence_events;
$$;
