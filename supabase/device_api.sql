create or replace function upsert_uwb_device_for_demo_floor(
    p_device_code text,
    p_name text,
    p_role text,
    p_latitude double precision,
    p_longitude double precision,
    p_is_active boolean default true
) returns void
language plpgsql
as $$
declare
    v_floor_id uuid;
begin
    select id into v_floor_id
    from floors
    where name = 'Demo Floor'
    limit 1;

    if v_floor_id is null then
        raise exception 'Demo Floor not found';
    end if;

    if p_role not in ('ANCHOR', 'TAG') then
        raise exception 'Invalid role: %', p_role;
    end if;

    if p_latitude < -90 or p_latitude > 90 then
        raise exception 'Invalid latitude: %', p_latitude;
    end if;

    if p_longitude < -180 or p_longitude > 180 then
        raise exception 'Invalid longitude: %', p_longitude;
    end if;

    insert into uwb_devices (
        floor_id,
        device_code,
        name,
        role,
        latitude,
        longitude,
        is_active,
        updated_at
    )
    values (
        v_floor_id,
        p_device_code,
        p_name,
        p_role,
        p_latitude,
        p_longitude,
        p_is_active,
        now()
    )
    on conflict (device_code) do update set
        floor_id = excluded.floor_id,
        name = excluded.name,
        role = excluded.role,
        latitude = excluded.latitude,
        longitude = excluded.longitude,
        is_active = excluded.is_active,
        updated_at = now();
end $$;

create or replace function soft_delete_uwb_device(
    p_device_code text
) returns void
language plpgsql
as $$
begin
    update uwb_devices
    set is_active = false,
        updated_at = now()
    where device_code = p_device_code;
end $$;

-- Optional cleanup for existing rows with null floor_id:
-- update uwb_devices
-- set floor_id = (select id from floors where name = 'Demo Floor' limit 1)
-- where floor_id is null;
