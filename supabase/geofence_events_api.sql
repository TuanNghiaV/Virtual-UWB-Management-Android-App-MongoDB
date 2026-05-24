create or replace function get_current_tag_geofences(
    p_device_code text
)
returns table (
    device_code text,
    geofence_code text,
    geofence_name text,
    geofence_type text
)
language sql
stable
as $$
    select
        d.device_code,
        g.geofence_code,
        g.name as geofence_name,
        g.type as geofence_type
    from uwb_devices d
    join geofences g on d.floor_id = g.floor_id
    where d.device_code = p_device_code
      and d.role = 'TAG'
      and d.is_active = true
      and g.is_active = true
      and ST_Contains(
          g.area,
          ST_SetSRID(ST_MakePoint(d.longitude, d.latitude), 4326)
      )
    order by g.geofence_code;
$$;

create or replace function evaluate_geofence_events_for_device(
    p_device_code text
)
returns integer
language plpgsql
as $$
declare
    v_device_id uuid;
    v_floor_id uuid;
    v_latitude double precision;
    v_longitude double precision;
    v_created_count integer := 0;
    r record;
    v_last_event text;
    v_currently_inside boolean;
begin
    select id, floor_id, latitude, longitude
    into v_device_id, v_floor_id, v_latitude, v_longitude
    from uwb_devices
    where device_code = p_device_code
      and role = 'TAG'
      and is_active = true
    limit 1;

    if v_device_id is null then
        raise exception 'Active TAG not found for code %', p_device_code;
    end if;

    for r in
        select id, geofence_code
        from geofences
        where floor_id = v_floor_id
          and is_active = true
    loop
        -- Get the most recent event for this device and geofence
        select event_type
        into v_last_event
        from geofence_events
        where device_id = v_device_id
          and geofence_id = r.id
        order by created_at desc, id desc
        limit 1;

        -- Check if current position is inside this geofence
        select ST_Contains(
            (select area from geofences where id = r.id),
            ST_SetSRID(ST_MakePoint(v_longitude, v_latitude), 4326)
        )
        into v_currently_inside;

        if v_currently_inside then
            -- Transition: Not in -> In
            if v_last_event is null or v_last_event = 'EXIT' then
                insert into geofence_events (
                    device_id,
                    geofence_id,
                    event_type,
                    latitude,
                    longitude
                ) values (
                    v_device_id,
                    r.id,
                    'ENTER',
                    v_latitude,
                    v_longitude
                );
                v_created_count := v_created_count + 1;
            end if;
        else
            -- Transition: In -> Not in
            if v_last_event = 'ENTER' or v_last_event = 'DWELL' then
                insert into geofence_events (
                    device_id,
                    geofence_id,
                    event_type,
                    latitude,
                    longitude
                ) values (
                    v_device_id,
                    r.id,
                    'EXIT',
                    v_latitude,
                    v_longitude
                );
                v_created_count := v_created_count + 1;
            end if;
        end if;

        v_last_event := null;
        v_currently_inside := false;
    end loop;

    return v_created_count;
end $$;

create or replace function get_recent_geofence_events(
    p_limit integer default 50
)
returns table (
    id bigint,
    device_code text,
    device_name text,
    geofence_code text,
    geofence_name text,
    geofence_type text,
    event_type text,
    latitude double precision,
    longitude double precision,
    created_at timestamptz
)
language sql
stable
as $$
    select
        e.id,
        d.device_code,
        d.name as device_name,
        g.geofence_code,
        g.name as geofence_name,
        g.type as geofence_type,
        e.event_type,
        e.latitude,
        e.longitude,
        e.created_at
    from geofence_events e
    join uwb_devices d on e.device_id = d.id
    join geofences g on e.geofence_id = g.id
    order by e.created_at desc, e.id desc
    limit p_limit;
$$;
