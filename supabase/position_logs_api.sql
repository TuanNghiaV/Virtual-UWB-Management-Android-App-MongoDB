create or replace function insert_position_log_by_device_code(
    p_device_code text,
    p_latitude double precision,
    p_longitude double precision,
    p_source text default 'SIMULATION'
) returns void
language plpgsql
as $$
declare
    v_device_id uuid;
    v_floor_id uuid;
begin
    select id, floor_id
    into v_device_id, v_floor_id
    from uwb_devices
    where device_code = p_device_code
      and is_active = true
    limit 1;

    if v_device_id is null then
        raise exception 'Active device not found for code %', p_device_code;
    end if;

    insert into position_logs (
        device_id,
        floor_id,
        latitude,
        longitude,
        source
    )
    values (
        v_device_id,
        v_floor_id,
        p_latitude,
        p_longitude,
        p_source
    );
end $$;

create or replace function get_position_logs_by_device_code(
    p_device_code text,
    p_limit integer default 100
)
returns table (
    id bigint,
    device_code text,
    latitude double precision,
    longitude double precision,
    source text,
    created_at timestamptz
)
language sql
stable
as $$
    select
        p.id,
        d.device_code,
        p.latitude,
        p.longitude,
        p.source,
        p.created_at
    from position_logs p
    join uwb_devices d on p.device_id = d.id
    where d.device_code = p_device_code
    order by p.created_at desc
    limit p_limit;
$$;
