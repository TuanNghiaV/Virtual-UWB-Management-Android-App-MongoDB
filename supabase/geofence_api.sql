create or replace function get_active_geofences()
returns table (
  id uuid,
  floor_id uuid,
  geofence_code text,
  name text,
  type text,
  vertices jsonb,
  is_active boolean,
  created_at timestamptz,
  updated_at timestamptz
)
language sql
stable
as $$
  select
    g.id,
    g.floor_id,
    g.geofence_code,
    g.name,
    g.type,
    ST_AsGeoJSON(g.area)::jsonb -> 'coordinates' -> 0 as vertices,
    g.is_active,
    g.created_at,
    g.updated_at
  from geofences g
  where g.is_active = true
  order by g.geofence_code;
$$;

create or replace function upsert_rectangle_geofence(
    p_geofence_code text,
    p_name text,
    p_type text,
    p_min_lat double precision,
    p_max_lat double precision,
    p_min_lon double precision,
    p_max_lon double precision
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

    if p_min_lat >= p_max_lat then
        raise exception 'p_min_lat must be less than p_max_lat';
    end if;

    if p_min_lon >= p_max_lon then
        raise exception 'p_min_lon must be less than p_max_lon';
    end if;

    insert into geofences (
        floor_id,
        geofence_code,
        name,
        type,
        area,
        is_active,
        updated_at
    )
    values (
        v_floor_id,
        p_geofence_code,
        p_name,
        p_type,
        ST_MakeEnvelope(
            p_min_lon,
            p_min_lat,
            p_max_lon,
            p_max_lat,
            4326
        ),
        true,
        now()
    )
    on conflict (geofence_code) do update set
        name = excluded.name,
        type = excluded.type,
        area = excluded.area,
        is_active = true,
        updated_at = now();
end $$;

create or replace function soft_delete_geofence(p_geofence_code text)
returns void
language plpgsql
as $$
begin
    update geofences 
    set is_active = false, 
        updated_at = now() 
    where geofence_code = p_geofence_code;
end $$;
