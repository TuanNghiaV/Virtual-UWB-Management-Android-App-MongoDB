-- ==============================================================================
-- VirtualUWB Supabase / PostGIS Schema
-- ==============================================================================
-- IMPORTANT PROTOTYPE NOTES:
-- 1. App explicitly stores latitude and longitude as double precision.
-- 2. PostGIS Point uses ST_MakePoint(longitude, latitude).
-- 3. Geofence polygon uses longitude latitude order.
-- 4. This is a dev/prototype schema with open RLS policies.
-- ==============================================================================

-- ==========================================
-- PART A: Extensions
-- ==========================================
create extension if not exists postgis;
create extension if not exists pgcrypto;

-- ==========================================
-- PART B: Safe Drop Tables (Dev/MVP only)
-- ==========================================
drop table if exists geofence_events cascade;
drop table if exists position_logs cascade;
drop table if exists geofences cascade;
drop table if exists uwb_devices cascade;
drop table if exists floors cascade;

-- ==========================================
-- PART C: Bảng floors
-- ==========================================
create table floors (
    id uuid primary key default gen_random_uuid(),
    name text not null,
    description text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- Seed floors
insert into floors (name, description)
values ('Demo Floor', 'Default indoor demo area for VirtualUWB');

-- ==========================================
-- PART D: Bảng uwb_devices
-- ==========================================
create table uwb_devices (
    id uuid primary key default gen_random_uuid(),
    floor_id uuid references floors(id) on delete cascade,
    device_code text not null unique,
    name text not null,
    role text not null check (role in ('ANCHOR', 'TAG')),
    latitude double precision not null check (latitude between -90 and 90),
    longitude double precision not null check (longitude between -180 and 180),
    location geography(Point, 4326) generated always as (
        ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography
    ) stored,
    is_active boolean not null default true,
    updated_at timestamptz not null default now(),
    created_at timestamptz not null default now()
);

create index uwb_devices_floor_id_idx on uwb_devices(floor_id);
create index uwb_devices_role_idx on uwb_devices(role);
create index uwb_devices_location_idx on uwb_devices using gist(location);

-- Seed devices
do $$
declare
    v_floor_id uuid;
begin
    select id into v_floor_id from floors where name = 'Demo Floor' limit 1;

    insert into uwb_devices (floor_id, device_code, name, role, latitude, longitude)
    values 
    (v_floor_id, 'anchor-a1', 'Anchor A1', 'ANCHOR', 10.762672, 106.660122),
    (v_floor_id, 'anchor-a2', 'Anchor A2', 'ANCHOR', 10.762672, 106.660222),
    (v_floor_id, 'anchor-a3', 'Anchor A3', 'ANCHOR', 10.762572, 106.660122),
    (v_floor_id, 'anchor-a4', 'Anchor A4', 'ANCHOR', 10.762572, 106.660222),
    (v_floor_id, 'tag-t1', 'Tag T1', 'TAG', 10.762622, 106.660172),
    (v_floor_id, 'tag-t2', 'Tag T2', 'TAG', 10.762610, 106.660190);
end $$;

-- ==========================================
-- PART E: Bảng geofences
-- ==========================================
create table geofences (
    id uuid primary key default gen_random_uuid(),
    floor_id uuid references floors(id) on delete cascade,
    geofence_code text not null unique,
    name text not null,
    type text not null check (type in ('ROOM', 'SAFE_ZONE', 'RESTRICTED_ZONE')),
    area geometry(Polygon, 4326) not null,
    is_active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index geofences_floor_id_idx on geofences(floor_id);
create index geofences_type_idx on geofences(type);
create index geofences_area_idx on geofences using gist(area);

-- Seed geofences
do $$
declare
    v_floor_id uuid;
begin
    select id into v_floor_id from floors where name = 'Demo Floor' limit 1;

    insert into geofences (floor_id, geofence_code, name, type, area)
    values 
    (
        v_floor_id, 
        'restricted-zone-1', 
        'Restricted Zone', 
        'RESTRICTED_ZONE',
        ST_GeomFromText('POLYGON((106.660180 10.762640, 106.660220 10.762640, 106.660220 10.762600, 106.660180 10.762600, 106.660180 10.762640))', 4326)
    ),
    (
        v_floor_id, 
        'safe-zone-1', 
        'Safe Zone', 
        'SAFE_ZONE',
        ST_GeomFromText('POLYGON((106.660125 10.762665, 106.660165 10.762665, 106.660165 10.762625, 106.660125 10.762625, 106.660125 10.762665))', 4326)
    );
end $$;

-- ==========================================
-- PART F: Bảng position_logs
-- ==========================================
create table position_logs (
    id bigserial primary key,
    device_id uuid references uwb_devices(id) on delete cascade,
    floor_id uuid references floors(id) on delete cascade,
    latitude double precision not null check (latitude between -90 and 90),
    longitude double precision not null check (longitude between -180 and 180),
    location geography(Point, 4326) generated always as (
        ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography
    ) stored,
    source text not null default 'SIMULATION',
    created_at timestamptz not null default now()
);

create index position_logs_device_id_time_idx on position_logs(device_id, created_at desc);
create index position_logs_floor_id_time_idx on position_logs(floor_id, created_at desc);
create index position_logs_location_idx on position_logs using gist(location);

-- ==========================================
-- PART G: Bảng geofence_events
-- ==========================================
create table geofence_events (
    id bigserial primary key,
    device_id uuid references uwb_devices(id) on delete cascade,
    geofence_id uuid references geofences(id) on delete cascade,
    event_type text not null check (event_type in ('ENTER', 'EXIT', 'DWELL')),
    latitude double precision not null,
    longitude double precision not null,
    created_at timestamptz not null default now()
);

create index geofence_events_device_id_time_idx on geofence_events(device_id, created_at desc);
create index geofence_events_geofence_id_time_idx on geofence_events(geofence_id, created_at desc);
create index geofence_events_type_idx on geofence_events(event_type);

-- ==========================================
-- PART H: Helper SQL Functions
-- ==========================================

-- 1. get_devices_within_radius
create or replace function get_devices_within_radius(
  center_lat double precision,
  center_lon double precision,
  radius_meters double precision
)
returns setof uwb_devices
language sql
stable
as $$
  select *
  from uwb_devices
  where ST_DWithin(
    location,
    ST_SetSRID(ST_MakePoint(center_lon, center_lat), 4326)::geography,
    radius_meters
  )
  and is_active = true;
$$;

-- 2. get_tag_geofences
create or replace function get_tag_geofences(p_device_code text)
returns table (
  device_code text,
  device_name text,
  geofence_code text,
  geofence_name text,
  geofence_type text
)
language sql
stable
as $$
  select 
    d.device_code, 
    d.name as device_name, 
    g.geofence_code, 
    g.name as geofence_name, 
    g.type as geofence_type
  from uwb_devices d
  join geofences g on d.floor_id = g.floor_id
  where d.device_code = p_device_code
    and d.role = 'TAG'
    and ST_Contains(
      g.area,
      ST_SetSRID(ST_MakePoint(d.longitude, d.latitude), 4326)
    )
    and g.is_active = true
    and d.is_active = true;
$$;

-- ==========================================
-- PART I: Updated At Trigger
-- ==========================================
create or replace function set_updated_at()
returns trigger as $$
begin
  new.updated_at = now();
  return new;
end;
$$ language plpgsql;

create trigger trg_floors_updated_at
before update on floors
for each row execute function set_updated_at();

create trigger trg_uwb_devices_updated_at
before update on uwb_devices
for each row execute function set_updated_at();

create trigger trg_geofences_updated_at
before update on geofences
for each row execute function set_updated_at();

-- ==========================================
-- PART J: Row Level Security (RLS) - Prototype
-- ==========================================
alter table floors enable row level security;
alter table uwb_devices enable row level security;
alter table geofences enable row level security;
alter table position_logs enable row level security;
alter table geofence_events enable row level security;

-- Prototype policy only. Tighten before production.
create policy "Allow all for prototype" on floors
for all using (true) with check (true);

create policy "Allow all for prototype" on uwb_devices
for all using (true) with check (true);

create policy "Allow all for prototype" on geofences
for all using (true) with check (true);

create policy "Allow all for prototype" on position_logs
for all using (true) with check (true);

create policy "Allow all for prototype" on geofence_events
for all using (true) with check (true);
