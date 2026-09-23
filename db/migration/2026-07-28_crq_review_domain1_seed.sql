-- Seeds 5 fully-mapped CRQs (CRQ_MASTER_TBL + CRQ_PLAN_TBL + CRQ_DETAIL_TBL +
-- CRQ_TASK_TBL + CRQ_STAGE_ASSIGN_TBL) in domain_id=1 (Embedded Support) /
-- sub_domain_id=1 (Site shifting), stage VALIDATE, status STARTED, so that
-- `call Get_CRQ_Review_Details(<super_admin_user_id>, 1, 1)` returns 5 rows
-- via its SUPER_ADMIN branch (CRQ_MASTER_TBL.domain_id/sub_domain_id filter,
-- current_stage='VALIDATE', current_status<>'DONE').
--
-- Run against Vegayan_CHM_36. Before this, domain_id=1/sub_domain_id=1 had
-- zero CRQs at any stage - existing CRQs (5, 6, 11, 12, 13, ...) live in
-- domain 1 / sub_domain 6 instead. Ids are auto-generated; crq_no/plan_no
-- continue the live sequence (max was CRQ000000888974 / PLAN000084 at the
-- time this was written).

START TRANSACTION;

-- CRQ 1: Site relocation, BTS shift ahead of tower dismantling
INSERT INTO CRQ_PLAN_TBL (plan_no, plan_type, source_system)
VALUES ('PLAN000085', 'Site Shifting', 'CMS');
SET @plan1 = LAST_INSERT_ID();

INSERT INTO CRQ_MASTER_TBL (crq_no, plan_id, current_stage, current_status, domain_id, sub_domain_id, remark)
VALUES ('CRQ000000888975', @plan1, 'VALIDATE', 'STARTED', 1, 1, 'Site relocation - BTS shift ahead of tower dismantling');
SET @crq1 = LAST_INSERT_ID();

INSERT INTO CRQ_DETAIL_TBL (
  crq_id, crq_no, plan_no, ascpy, asorg, asgrp, company_3, support_organization, support_group_name,
  categorization_tier_1, categorization_tier_2, categorization_tier_3,
  requested_start_date, requested_end_date, description, detailed_description, type_of_cr, change_impact
) VALUES (
  @crq1, 'CRQ000000888975', 'PLAN000085', 'Airtel', 'Network Operations', 'Embedded Support',
  'Bharti Airtel', 'Network Deployment', 'Site Shifting Team',
  'Network', 'Site Infrastructure', 'Site Relocation',
  '2026-08-05 09:00:00', '2026-08-05 18:00:00',
  'Site relocation - SITE-EMB-101 to SITE-EMB-104',
  'Relocate active BTS equipment from SITE-EMB-101 to newly built SITE-EMB-104 ahead of the scheduled dismantling of the source tower. Includes RF unit, baseband unit and antenna transfer.',
  'Standard', 'Medium'
);

INSERT INTO CRQ_TASK_TBL (
  crq_id, plan_id, task_id, task_sequence, external_state, ne_label, task_profile_type,
  assigned_group, vendor, activity_plan_start_date, activity_plan_end_date,
  work_area_territory, task_activity, location_code_m6, workflow, domain, subdomain
) VALUES (
  @crq1, @plan1, 'TASK000085', '1', 'OPEN', 'SITE-EMB-104', 'Site Migration',
  'Field Ops - Embedded', 'Ericsson', '2026-08-05 09:00:00', '2026-08-05 18:00:00',
  'North Zone', 'BTS relocation and commissioning', 'M6-EMB-104', 'Site Shifting', 'Embedded Support', 'Site shifting'
);

INSERT INTO CRQ_STAGE_ASSIGN_TBL (crq_id, stage, assign_olmid, assign_start_time, performed_by_olmid, actual_start_time)
VALUES (@crq1, 'VALIDATE', 'B0266821', '2026-07-28 09:00:00', 'B0266821', '2026-07-28 09:05:00');


-- CRQ 2: RF antenna realignment and site shift for interference resolution
INSERT INTO CRQ_PLAN_TBL (plan_no, plan_type, source_system)
VALUES ('PLAN000086', 'Site Shifting', 'CMS');
SET @plan2 = LAST_INSERT_ID();

INSERT INTO CRQ_MASTER_TBL (crq_no, plan_id, current_stage, current_status, domain_id, sub_domain_id, remark)
VALUES ('CRQ000000888976', @plan2, 'VALIDATE', 'STARTED', 1, 1, 'Antenna realignment and site shift for RF interference resolution');
SET @crq2 = LAST_INSERT_ID();

INSERT INTO CRQ_DETAIL_TBL (
  crq_id, crq_no, plan_no, ascpy, asorg, asgrp, company_3, support_organization, support_group_name,
  categorization_tier_1, categorization_tier_2, categorization_tier_3,
  requested_start_date, requested_end_date, description, detailed_description, type_of_cr, change_impact
) VALUES (
  @crq2, 'CRQ000000888976', 'PLAN000086', 'Airtel', 'Network Operations', 'Embedded Support',
  'Bharti Airtel', 'RF Engineering', 'Site Shifting Team',
  'Network', 'RF Optimization', 'Antenna Realignment',
  '2026-08-06 10:00:00', '2026-08-06 16:00:00',
  'Antenna realignment - SITE-EMB-212',
  'Shift antenna mounting position on SITE-EMB-212 and realign azimuth/tilt to resolve reported co-channel interference with adjacent site.',
  'Standard', 'Low'
);

INSERT INTO CRQ_TASK_TBL (
  crq_id, plan_id, task_id, task_sequence, external_state, ne_label, task_profile_type,
  assigned_group, vendor, activity_plan_start_date, activity_plan_end_date,
  work_area_territory, task_activity, location_code_m6, workflow, domain, subdomain
) VALUES (
  @crq2, @plan2, 'TASK000086', '1', 'OPEN', 'SITE-EMB-212', 'RF Optimization',
  'Field Ops - Embedded', 'Nokia', '2026-08-06 10:00:00', '2026-08-06 16:00:00',
  'North Zone', 'Antenna shift and realignment', 'M6-EMB-212', 'Site Shifting', 'Embedded Support', 'Site shifting'
);

INSERT INTO CRQ_STAGE_ASSIGN_TBL (crq_id, stage, assign_olmid, assign_start_time, performed_by_olmid, actual_start_time)
VALUES (@crq2, 'VALIDATE', 'B0321549', '2026-07-28 09:10:00', 'B0321549', '2026-07-28 09:12:00');


-- CRQ 3: DC power plant relocation between co-located sites
INSERT INTO CRQ_PLAN_TBL (plan_no, plan_type, source_system)
VALUES ('PLAN000087', 'Site Shifting', 'CMS');
SET @plan3 = LAST_INSERT_ID();

INSERT INTO CRQ_MASTER_TBL (crq_no, plan_id, current_stage, current_status, domain_id, sub_domain_id, remark)
VALUES ('CRQ000000888977', @plan3, 'VALIDATE', 'STARTED', 1, 1, 'DC power plant relocation between co-located sites');
SET @crq3 = LAST_INSERT_ID();

INSERT INTO CRQ_DETAIL_TBL (
  crq_id, crq_no, plan_no, ascpy, asorg, asgrp, company_3, support_organization, support_group_name,
  categorization_tier_1, categorization_tier_2, categorization_tier_3,
  requested_start_date, requested_end_date, description, detailed_description, type_of_cr, change_impact
) VALUES (
  @crq3, 'CRQ000000888977', 'PLAN000087', 'Airtel', 'Network Operations', 'Embedded Support',
  'Bharti Airtel', 'Power Infrastructure', 'Site Shifting Team',
  'Power', 'DC Plant', 'Equipment Relocation',
  '2026-08-07 08:00:00', '2026-08-07 20:00:00',
  'DC power plant relocation - SITE-EMB-305 to SITE-EMB-308',
  'Relocate DC rectifier plant and battery bank from SITE-EMB-305 to SITE-EMB-308 as part of site consolidation, with temporary UPS backup during the cutover window.',
  'Standard', 'High'
);

INSERT INTO CRQ_TASK_TBL (
  crq_id, plan_id, task_id, task_sequence, external_state, ne_label, task_profile_type,
  assigned_group, vendor, activity_plan_start_date, activity_plan_end_date,
  work_area_territory, task_activity, location_code_m6, workflow, domain, subdomain
) VALUES (
  @crq3, @plan3, 'TASK000087', '1', 'OPEN', 'SITE-EMB-308', 'Power Migration',
  'Field Ops - Embedded', 'Vertiv', '2026-08-07 08:00:00', '2026-08-07 20:00:00',
  'North Zone', 'DC plant relocation and commissioning', 'M6-EMB-308', 'Site Shifting', 'Embedded Support', 'Site shifting'
);

INSERT INTO CRQ_STAGE_ASSIGN_TBL (crq_id, stage, assign_olmid, assign_start_time, performed_by_olmid, actual_start_time)
VALUES (@crq3, 'VALIDATE', 'B0325609', '2026-07-28 09:15:00', 'B0325609', '2026-07-28 09:18:00');


-- CRQ 4: Active equipment migration ahead of tower decommission
INSERT INTO CRQ_PLAN_TBL (plan_no, plan_type, source_system)
VALUES ('PLAN000088', 'Site Shifting', 'CMS');
SET @plan4 = LAST_INSERT_ID();

INSERT INTO CRQ_MASTER_TBL (crq_no, plan_id, current_stage, current_status, domain_id, sub_domain_id, remark)
VALUES ('CRQ000000888978', @plan4, 'VALIDATE', 'STARTED', 1, 1, 'Active equipment migration ahead of tower decommission');
SET @crq4 = LAST_INSERT_ID();

INSERT INTO CRQ_DETAIL_TBL (
  crq_id, crq_no, plan_no, ascpy, asorg, asgrp, company_3, support_organization, support_group_name,
  categorization_tier_1, categorization_tier_2, categorization_tier_3,
  requested_start_date, requested_end_date, description, detailed_description, type_of_cr, change_impact
) VALUES (
  @crq4, 'CRQ000000888978', 'PLAN000088', 'Airtel', 'Network Operations', 'Embedded Support',
  'Bharti Airtel', 'Network Deployment', 'Site Shifting Team',
  'Network', 'Site Infrastructure', 'Equipment Migration',
  '2026-08-08 09:00:00', '2026-08-08 19:00:00',
  'Node shifting - SITE-EMB-410 ahead of tower decommission',
  'Migrate active radio and transmission equipment off SITE-EMB-410 to the standby SITE-EMB-411 tower ahead of the planned decommission of SITE-EMB-410.',
  'Standard', 'High'
);

INSERT INTO CRQ_TASK_TBL (
  crq_id, plan_id, task_id, task_sequence, external_state, ne_label, task_profile_type,
  assigned_group, vendor, activity_plan_start_date, activity_plan_end_date,
  work_area_territory, task_activity, location_code_m6, workflow, domain, subdomain
) VALUES (
  @crq4, @plan4, 'TASK000088', '1', 'OPEN', 'SITE-EMB-411', 'Site Migration',
  'Field Ops - Embedded', 'Huawei', '2026-08-08 09:00:00', '2026-08-08 19:00:00',
  'North Zone', 'Node shift and commissioning', 'M6-EMB-411', 'Site Shifting', 'Embedded Support', 'Site shifting'
);

INSERT INTO CRQ_STAGE_ASSIGN_TBL (crq_id, stage, assign_olmid, assign_start_time, performed_by_olmid, actual_start_time)
VALUES (@crq4, 'VALIDATE', 'A1KQODMV', '2026-07-28 09:20:00', 'A1KQODMV', '2026-07-28 09:22:00');


-- CRQ 5: Site consolidation - merge two co-located sites
INSERT INTO CRQ_PLAN_TBL (plan_no, plan_type, source_system)
VALUES ('PLAN000089', 'Site Shifting', 'CMS');
SET @plan5 = LAST_INSERT_ID();

INSERT INTO CRQ_MASTER_TBL (crq_no, plan_id, current_stage, current_status, domain_id, sub_domain_id, remark)
VALUES ('CRQ000000888979', @plan5, 'VALIDATE', 'STARTED', 1, 1, 'Site consolidation - merge two co-located sites');
SET @crq5 = LAST_INSERT_ID();

INSERT INTO CRQ_DETAIL_TBL (
  crq_id, crq_no, plan_no, ascpy, asorg, asgrp, company_3, support_organization, support_group_name,
  categorization_tier_1, categorization_tier_2, categorization_tier_3,
  requested_start_date, requested_end_date, description, detailed_description, type_of_cr, change_impact
) VALUES (
  @crq5, 'CRQ000000888979', 'PLAN000089', 'Airtel', 'Network Operations', 'Embedded Support',
  'Bharti Airtel', 'Network Deployment', 'Site Shifting Team',
  'Network', 'Site Infrastructure', 'Site Consolidation',
  '2026-08-09 09:00:00', '2026-08-09 21:00:00',
  'Site consolidation - SITE-EMB-520 and SITE-EMB-521 merge',
  'Consolidate two co-located sites (SITE-EMB-520, SITE-EMB-521) sharing the same tower onto a single shared cabinet and antenna structure to reduce site footprint.',
  'Standard', 'Medium'
);

INSERT INTO CRQ_TASK_TBL (
  crq_id, plan_id, task_id, task_sequence, external_state, ne_label, task_profile_type,
  assigned_group, vendor, activity_plan_start_date, activity_plan_end_date,
  work_area_territory, task_activity, location_code_m6, workflow, domain, subdomain
) VALUES (
  @crq5, @plan5, 'TASK000089', '1', 'OPEN', 'SITE-EMB-520', 'Site Migration',
  'Field Ops - Embedded', 'Ericsson', '2026-08-09 09:00:00', '2026-08-09 21:00:00',
  'North Zone', 'Site consolidation and commissioning', 'M6-EMB-520', 'Site Shifting', 'Embedded Support', 'Site shifting'
);

INSERT INTO CRQ_STAGE_ASSIGN_TBL (crq_id, stage, assign_olmid, assign_start_time, performed_by_olmid, actual_start_time)
VALUES (@crq5, 'VALIDATE', 'A1YPVTS4', '2026-07-28 09:25:00', 'A1YPVTS4', '2026-07-28 09:28:00');

COMMIT;
