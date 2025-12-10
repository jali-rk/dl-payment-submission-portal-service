-- Dummy Data for Payment Portals
-- This script inserts test portals for use with Postman testing
-- Run this script directly against your database or use a Liquibase SQL changeset

-- Clear existing test data (optional - comment out if you want to keep existing data)
-- DELETE FROM payment_portals WHERE name LIKE 'test-%';

-- Insert dummy payment portals for different months and years
INSERT INTO payment_portals (
    id, 
    created_at, 
    updated_at, 
    created_by_admin_id, 
    created_by, 
    updated_by, 
    name,
    display_name,
    is_published, 
    visibility, 
    portal_month, 
    portal_year
) VALUES 
-- Published portals for current year (2025)
(
    gen_random_uuid(),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    '11111111-1111-1111-1111-111111111111',
    'admin',
    'admin',
    'test-january-2025-physics',
    'January 2025 - Physics',
    true,
    'PUBLISHED',
    1,
    2025
),
(
    gen_random_uuid(),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    '11111111-1111-1111-1111-111111111111',
    'admin',
    'admin',
    'test-february-2025-chemistry',
    'February 2025 - Chemistry',
    true,
    'PUBLISHED',
    2,
    2025
),
(
    gen_random_uuid(),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    '11111111-1111-1111-1111-111111111111',
    'admin',
    'admin',
    'test-march-2025-biology',
    'March 2025 - Biology',
    true,
    'PUBLISHED',
    3,
    2025
),
(
    gen_random_uuid(),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    '22222222-2222-2222-2222-222222222222',
    'admin2',
    'admin2',
    'test-april-2025-mathematics',
    'April 2025 - Mathematics',
    true,
    'PUBLISHED',
    4,
    2025
),
(
    gen_random_uuid(),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    '22222222-2222-2222-2222-222222222222',
    'admin2',
    'admin2',
    'test-may-2025-english',
    'May 2025 - English',
    true,
    'PUBLISHED',
    5,
    2025
),

-- Hidden portals (not yet published)
(
    gen_random_uuid(),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    '11111111-1111-1111-1111-111111111111',
    'admin',
    'admin',
    'test-june-2025-history',
    'June 2025 - History',
    false,
    'HIDDEN',
    6,
    2025
),
(
    gen_random_uuid(),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    '11111111-1111-1111-1111-111111111111',
    'admin',
    'admin',
    'test-july-2025-geography',
    'July 2025 - Geography',
    false,
    'HIDDEN',
    7,
    2025
),

-- Published portals for December (current month)
(
    gen_random_uuid(),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    '33333333-3333-3333-3333-333333333333',
    'admin3',
    'admin3',
    'test-december-2025-general-payment',
    'December 2025 - General Payment',
    true,
    'PUBLISHED',
    12,
    2025
),
(
    gen_random_uuid(),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    '33333333-3333-3333-3333-333333333333',
    'admin3',
    'admin3',
    'test-december-2025-special-class',
    'December 2025 - Special Class',
    true,
    'PUBLISHED',
    12,
    2025
),

-- Portals for next year (2026)
(
    gen_random_uuid(),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    '11111111-1111-1111-1111-111111111111',
    'admin',
    'admin',
    'test-january-2026-advanced-physics',
    'January 2026 - Advanced Physics',
    false,
    'HIDDEN',
    1,
    2026
),
(
    gen_random_uuid(),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    '22222222-2222-2222-2222-222222222222',
    'admin2',
    'admin2',
    'test-february-2026-advanced-chemistry',
    'February 2026 - Advanced Chemistry',
    false,
    'HIDDEN',
    2,
    2026
),

-- Previous year portals (2024)
(
    gen_random_uuid(),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    '11111111-1111-1111-1111-111111111111',
    'admin',
    'admin',
    'test-november-2024-physics',
    'November 2024 - Physics',
    true,
    'PUBLISHED',
    11,
    2024
),
(
    gen_random_uuid(),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    '22222222-2222-2222-2222-222222222222',
    'admin2',
    'admin2',
    'test-december-2024-year-end',
    'December 2024 - Year End',
    true,
    'PUBLISHED',
    12,
    2024
);

-- Note: The admin UUIDs used above are test UUIDs
-- Replace them with actual admin IDs from your User Service if needed
