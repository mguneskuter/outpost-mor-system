-- Sources: https://www.vatcomply.com/ (European Commission TEDB-derived EU
-- and UK VAT) and https://www.salestaxzip.com/ (US state general
-- sales-tax rates), extracted 2026-09-11.
-- Rates are general jurisdiction rates only. Product taxability, local US
-- taxes, and historical effective dates are not represented. Re-running
-- this file fails with a unique-key violation rather than replacing an
-- existing rate, so a changed rate is never silently overwritten.
INSERT INTO tax_rate (
    tax_rate_id, country_id, country_subdivision_id, rate
)
VALUES
-- EU/UK country-level rows: ISO alpha-2 code in the comment.
(1, 1, NULL, 0.2000),   -- AT Austria
(2, 2, NULL, 0.2100),   -- BE Belgium
(3, 3, NULL, 0.2000),   -- BG Bulgaria
(4, 4, NULL, 0.1900),   -- CY Cyprus
(5, 5, NULL, 0.2100),   -- CZ Czechia
(6, 6, NULL, 0.1900),   -- DE Germany
(7, 7, NULL, 0.2500),   -- DK Denmark
(8, 8, NULL, 0.2400),   -- EE Estonia
(9, 9, NULL, 0.2100),   -- ES Spain
(10, 10, NULL, 0.2550), -- FI Finland
(11, 11, NULL, 0.2000), -- FR France
(12, 12, NULL, 0.2000), -- GB United Kingdom
(13, 13, NULL, 0.2400), -- GR Greece
(14, 14, NULL, 0.2500), -- HR Croatia
(15, 15, NULL, 0.2700), -- HU Hungary
(16, 16, NULL, 0.2300), -- IE Ireland
(17, 17, NULL, 0.2200), -- IT Italy
(18, 18, NULL, 0.2100), -- LT Lithuania
(19, 19, NULL, 0.1700), -- LU Luxembourg
(20, 20, NULL, 0.2100), -- LV Latvia
(21, 21, NULL, 0.1800), -- MT Malta
(22, 22, NULL, 0.2100), -- NL Netherlands
(23, 23, NULL, 0.2300), -- PL Poland
(24, 24, NULL, 0.2300), -- PT Portugal
(25, 25, NULL, 0.2100), -- RO Romania
(26, 26, NULL, 0.2500), -- SE Sweden
(27, 27, NULL, 0.2200), -- SI Slovenia
(28, 28, NULL, 0.2300), -- SK Slovakia
-- US subdivision rows: ISO 3166-2 code in the comment.
(1001, 29, 1, 0.0000),  -- US-AK Alaska
(1002, 29, 2, 0.0400),  -- US-AL Alabama
(1003, 29, 3, 0.0650),  -- US-AR Arkansas
(1004, 29, 4, 0.0560),  -- US-AZ Arizona
(1005, 29, 5, 0.0725),  -- US-CA California
(1006, 29, 6, 0.0290),  -- US-CO Colorado
(1007, 29, 7, 0.0635),  -- US-CT Connecticut
(1008, 29, 8, 0.0575),  -- US-DC District of Columbia
(1009, 29, 9, 0.0000),  -- US-DE Delaware
(1010, 29, 10, 0.0600), -- US-FL Florida
(1011, 29, 11, 0.0400), -- US-GA Georgia
(1012, 29, 12, 0.0400), -- US-HI Hawaii
(1013, 29, 13, 0.0600), -- US-IA Iowa
(1014, 29, 14, 0.0600), -- US-ID Idaho
(1015, 29, 15, 0.0625), -- US-IL Illinois
(1016, 29, 16, 0.0700), -- US-IN Indiana
(1017, 29, 17, 0.0650), -- US-KS Kansas
(1018, 29, 18, 0.0600), -- US-KY Kentucky
(1019, 29, 19, 0.0445), -- US-LA Louisiana
(1020, 29, 20, 0.0625), -- US-MA Massachusetts
(1021, 29, 21, 0.0600), -- US-MD Maryland
(1022, 29, 22, 0.0550), -- US-ME Maine
(1023, 29, 23, 0.0600), -- US-MI Michigan
(1024, 29, 24, 0.0688), -- US-MN Minnesota
(1025, 29, 25, 0.0423), -- US-MO Missouri
(1026, 29, 26, 0.0700), -- US-MS Mississippi
(1027, 29, 27, 0.0000), -- US-MT Montana
(1028, 29, 28, 0.0475), -- US-NC North Carolina
(1029, 29, 29, 0.0500), -- US-ND North Dakota
(1030, 29, 30, 0.0550), -- US-NE Nebraska
(1031, 29, 31, 0.0000), -- US-NH New Hampshire
(1032, 29, 32, 0.0663), -- US-NJ New Jersey
(1033, 29, 33, 0.0513), -- US-NM New Mexico
(1034, 29, 34, 0.0685), -- US-NV Nevada
(1035, 29, 35, 0.0400), -- US-NY New York
(1036, 29, 36, 0.0575), -- US-OH Ohio
(1037, 29, 37, 0.0450), -- US-OK Oklahoma
(1038, 29, 38, 0.0000), -- US-OR Oregon
(1039, 29, 39, 0.0600), -- US-PA Pennsylvania
(1040, 29, 40, 0.0700), -- US-RI Rhode Island
(1041, 29, 41, 0.0600), -- US-SC South Carolina
(1042, 29, 42, 0.0420), -- US-SD South Dakota
(1043, 29, 43, 0.0700), -- US-TN Tennessee
(1044, 29, 44, 0.0625), -- US-TX Texas
(1045, 29, 45, 0.0485), -- US-UT Utah
(1046, 29, 46, 0.0430), -- US-VA Virginia
(1047, 29, 47, 0.0600), -- US-VT Vermont
(1048, 29, 48, 0.0650), -- US-WA Washington
(1049, 29, 49, 0.0500), -- US-WI Wisconsin
(1050, 29, 50, 0.0600), -- US-WV West Virginia
(1051, 29, 51, 0.0400); -- US-WY Wyoming

SELECT setval('tax_rate_seq', (SELECT max(tax_rate_id) FROM tax_rate), TRUE);
